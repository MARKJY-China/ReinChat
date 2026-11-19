import { spawn } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

const electron = process.env.DSH_NODE_EXECUTABLE || 'H:/DeepSeekHarness/DeepSeek Harness.exe';
const bin = process.env.DSH_CLI_BIN || 'H:/DeepSeekHarness/resources/app.asar/dsh/node_modules/@deepseek-ai/dsh/lib/bin.js';
const home = path.join(os.tmpdir(), 'reinchat-dsh-poc-home-' + Date.now());
const cwd = path.join(os.tmpdir(), 'reinchat-dsh-poc-work-' + Date.now());
fs.mkdirSync(home, { recursive: true });
fs.mkdirSync(cwd, { recursive: true });

const child = spawn(electron, [bin, '--profile', 'sdk'], {
  cwd,
  env: {
    ...process.env,
    ELECTRON_RUN_AS_NODE: '1',
    DSH_HOME: home,
    DEEPSEEK_API_KEY: process.env.DEEPSEEK_API_KEY || 'dummy-key',
  },
  stdio: ['pipe', 'pipe', 'pipe'],
});
child.stderr.on('data', (chunk) => process.stderr.write('[dsh] ' + chunk));
let nextId = 1;
const pending = new Map();
let buffer = '';
child.stdout.on('data', (chunk) => {
  buffer += chunk.toString('utf8');
  let cut;
  while ((cut = buffer.indexOf('\n')) >= 0) {
    const line = buffer.slice(0, cut).trim();
    buffer = buffer.slice(cut + 1);
    if (!line) continue;
    let message;
    try { message = JSON.parse(line); } catch { console.log('[bad-frame]', line.slice(0, 200)); continue; }
    if (message.id && pending.has(message.id)) {
      const item = pending.get(message.id);
      pending.delete(message.id);
      clearTimeout(item.timer);
      item.resolve(message);
    } else if (message.method) {
      console.log('[notify]', message.method, JSON.stringify(message.params).slice(0, 300));
      if (message.id) {
        child.stdin.write(JSON.stringify({ jsonrpc: '2.0', id: message.id, result: { outcome: 'rejected' } }) + '\n');
      }
    }
  }
});
function request(method, params) {
  const id = nextId++;
  const frame = { jsonrpc: '2.0', id, method, params };
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => { pending.delete(id); reject(new Error('timeout ' + method)); }, 30000);
    pending.set(id, { resolve, reject, timer });
    child.stdin.write(JSON.stringify(frame) + '\n');
  });
}
const wait = (ms) => new Promise((r) => setTimeout(r, ms));
try {
  const init = await request('initialize', {
    cwd,
    provider: 'deepseek-official',
    model: 'deepseek-chat',
    approvalRequests: false,
  });
  console.log('INIT', JSON.stringify(init));
  const result = await request('session/query', {
    sessionId: 'reinchat-poc-' + Date.now(),
    contentBlocks: [{ type: 'text', text: 'Say hello in one word.' }],
  });
  console.log('QUERY', JSON.stringify(result));
  await wait(8000);
  await request('shutdown', {});
} catch (error) {
  console.error('POC_ERROR', error.message);
} finally {
  try { child.stdin.end(); } catch {}
  await wait(500);
  try { child.kill(); } catch {}
}
