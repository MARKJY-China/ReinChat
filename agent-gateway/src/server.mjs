import http from 'node:http';
import path from 'node:path';
import { randomUUID } from 'node:crypto';
import { DshRuntime } from './runtime.mjs';

const PORT = Number(process.env.PORT || 18091);
const HOST = process.env.HOST || '127.0.0.1';
const WORKSPACE_ROOT = process.env.REINCHAT_WORKSPACE_ROOT || path.join(process.cwd(), 'workspaces');

function json(res, status, value) {
  const raw = JSON.stringify(value);
  res.writeHead(status, { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(raw) });
  res.end(raw);
}

async function readBody(req) {
  let raw = '';
  for await (const chunk of req) {
    raw += chunk;
    if (raw.length > 1_000_000) throw new Error('body too large');
  }
  return raw ? JSON.parse(raw) : {};
}

const server = http.createServer(async (req, res) => {
  if (req.method === 'GET' && req.url === '/healthz') return json(res, 200, { status: 'ok' });
  if (req.method !== 'POST' || req.url !== '/v1/run') return json(res, 404, { error: 'not found' });
  let body;
  try { body = await readBody(req); } catch (e) { return json(res, 400, { error: e.message }); }
  const text = String(body.text || '').trim();
  if (!text) return json(res, 400, { error: 'text is required' });
  if (!body.provider || !body.model) return json(res, 400, { error: 'provider and model are required' });

  const sessionId = body.sessionId || ('reinchat-' + randomUUID());
  const workspace = path.join(WORKSPACE_ROOT, sessionId.replace(/[^a-zA-Z0-9._-]/g, '_'));
  const runtime = new DshRuntime({ cwd: workspace, apiKey: body.apiKey });
  let closed = false;
  const cleanup = async () => {
    if (closed) return;
    closed = true;
    await runtime.close();
  };
  res.on('close', cleanup);

  res.writeHead(200, {
    'Content-Type': 'text/event-stream; charset=utf-8',
    'Cache-Control': 'no-cache, no-transform',
    'Connection': 'keep-alive',
    'X-Accel-Buffering': 'no',
  });
  const send = (event, data) => {
    if (!closed) res.write(`event: ${event}\ndata: ${JSON.stringify(data)}\n\n`);
  };
  send('session', { sessionId, workspace });

  const listener = (message) => {
    if (message.params?.sessionId && message.params.sessionId !== sessionId) return;
    send('dsh', message);
  };
  runtime.notificationListeners.push(listener);

  try {
    const init = await runtime.initialize({
      provider: body.provider,
      model: body.model,
      reasoningEffort: body.reasoningEffort,
    });
    send('ready', init);
    const prompted = await runtime.prompt(sessionId, text);
    send('prompt', prompted);
    await runtime.waitForIdle(sessionId, Number(body.timeoutMs || 300000));
    send('done', { sessionId });
  } catch (error) {
    send('error', { message: error.message, stderr: runtime.stderr.slice(-1000) });
  } finally {
    await cleanup();
    res.end();
  }
});

server.listen(PORT, HOST, () => {
  console.log(`reinchat-agent-gateway http://${HOST}:${PORT}`);
});
