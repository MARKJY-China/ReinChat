import { spawn } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ELECTRON = process.env.DSH_NODE_EXECUTABLE || 'H:/DeepSeekHarness/DeepSeek Harness.exe';
const CLI_BIN = process.env.DSH_CLI_BIN || 'H:/DeepSeekHarness/resources/app.asar/dsh/node_modules/@deepseek-ai/dsh/lib/bin.js';
const PATCH = process.env.REINCHAT_DSH_PATCH ||
  path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'profiles', 'reinchat.patch.yml');

function lineFrames(stream) {
  let buffer = '';
  const listeners = [];
  stream.on('data', (chunk) => {
    buffer += chunk.toString('utf8');
    let cut;
    while ((cut = buffer.indexOf('\n')) >= 0) {
      const line = buffer.slice(0, cut).trim();
      buffer = buffer.slice(cut + 1);
      if (line) listeners.forEach((listener) => listener(line));
    }
  });
  return (listener) => listeners.push(listener);
}

/**
 * One isolated DSH runtime. A production gateway should keep one per
 * conversation (or per user), not one per HTTP request.
 */
export class DshRuntime {
  constructor({ cwd, apiKey }) {
    this.cwd = cwd;
    this.apiKey = apiKey;
    this.nextId = 1;
    this.pending = new Map();
    this.notifications = [];
    this.stderr = '';
    this.closed = false;
    this.home = path.join(os.tmpdir(), 'reinchat-dsh-home-' + Date.now() + '-' + Math.random().toString(16).slice(2));
    fs.mkdirSync(this.home, { recursive: true });
    fs.mkdirSync(cwd, { recursive: true });
    this.child = spawn(ELECTRON, [CLI_BIN, '--profile', 'sdk', '--patch', PATCH], {
      cwd,
      env: {
        ...process.env,
        ELECTRON_RUN_AS_NODE: '1',
        DSH_HOME: this.home,
        DEEPSEEK_API_KEY: apiKey || process.env.DEEPSEEK_API_KEY || '',
        REINCHAT_WORKSPACE_ROOT: cwd,
      },
      stdio: ['pipe', 'pipe', 'pipe'],
    });
    this.child.stderr.on('data', (chunk) => { this.stderr += chunk.toString('utf8').slice(-4000); });
    lineFrames(this.child.stdout)((line) => this.onLine(line));
  }

  onLine(line) {
    let message;
    try { message = JSON.parse(line); } catch { return; }
    if (message.id !== undefined && message.method) {
      // Server-to-client request. POC denies approvals and cancels questions.
      const result = message.method === 'user-question/ask'
        ? { cancelled: true }
        : { outcome: 'rejected' };
      this.write({ jsonrpc: '2.0', id: message.id, result });
      return;
    }
    if (message.id !== undefined && this.pending.has(message.id)) {
      const pending = this.pending.get(message.id);
      this.pending.delete(message.id);
      clearTimeout(pending.timer);
      pending.resolve(message);
      return;
    }
    if (message.method) {
      this.notifications.push(message);
      for (const listener of this.notificationListeners) listener(message);
    }
  }

  get notificationListeners() {
    if (!this._notificationListeners) this._notificationListeners = [];
    return this._notificationListeners;
  }

  write(frame) {
    if (!this.closed) this.child.stdin.write(JSON.stringify(frame) + '\n');
  }

  request(method, params, timeoutMs = 60_000) {
    const id = this.nextId++;
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pending.delete(id);
        reject(new Error('DSH timeout: ' + method));
      }, timeoutMs);
      this.pending.set(id, { resolve, reject, timer });
      this.write({ jsonrpc: '2.0', id, method, params });
    });
  }

  async initialize({ provider, model, reasoningEffort }) {
    const response = await this.request('initialize', {
      cwd: this.cwd,
      provider,
      model,
      reasoningEffort: reasoningEffort || undefined,
      approvalRequests: true,
      capabilities: { userQuestions: true },
    }, 30_000);
    if (response.error) throw new Error(response.error.message || 'DSH initialize failed');
    return response.result;
  }

  async prompt(sessionId, text) {
    const response = await this.request('session/query', {
      sessionId,
      contentBlocks: [{ type: 'text', text }],
    });
    if (response.error) throw new Error(response.error.message || 'DSH prompt failed');
    return response.result;
  }

  async waitForIdle(sessionId, timeoutMs = 600_000) {
    return new Promise((resolve, reject) => {
      let sawRun = false;
      const timer = setTimeout(() => reject(new Error('DSH turn timeout')), timeoutMs);
      const listener = (message) => {
        if (message.method !== 'session.status' || message.params?.sessionId !== sessionId) return;
        if (message.params.status === 'running') sawRun = true;
        if (message.params.status === 'idle' && sawRun) {
          clearTimeout(timer);
          this.notificationListeners.splice(this.notificationListeners.indexOf(listener), 1);
          resolve();
        }
      };
      this.notificationListeners.push(listener);
    });
  }

  async close() {
    if (this.closed) return;
    this.closed = true;
    for (const pending of this.pending.values()) {
      clearTimeout(pending.timer);
      pending.reject(new Error('DSH runtime closed'));
    }
    this.pending.clear();
    try { this.child.stdin.end(); } catch { /* ignore */ }
    await new Promise((resolve) => {
      const timer = setTimeout(() => {
        try { this.child.kill(); } catch { /* ignore */ }
        resolve();
      }, 3000);
      this.child.once('exit', () => { clearTimeout(timer); resolve(); });
    });
    try { fs.rmSync(this.home, { recursive: true, force: true }); } catch { /* ignore */ }
  }
}
