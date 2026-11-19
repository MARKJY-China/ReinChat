import http from 'node:http';
import { randomUUID } from 'node:crypto';
import { createEmbeddedRuntime } from './embedded-runtime.mjs';

const PORT = Number(process.env.PORT || 18092);
const HOST = process.env.HOST || '127.0.0.1';

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
  if (req.method === 'GET' && req.url === '/healthz') return json(res, 200, { status: 'ok', engine: 'embedded-dsh-core' });
  if (req.method !== 'POST' || req.url !== '/v1/run') return json(res, 404, { error: 'not found' });
  let body;
  try { body = await readBody(req); } catch (e) { return json(res, 400, { error: e.message }); }
  const text = String(body.text || '').trim();
  if (!text) return json(res, 400, { error: 'text is required' });
  if (!body.baseUrl || !body.model) return json(res, 400, { error: 'baseUrl and model are required' });

  res.writeHead(200, {
    'Content-Type': 'text/event-stream; charset=utf-8',
    'Cache-Control': 'no-cache, no-transform',
    'Connection': 'keep-alive',
    'X-Accel-Buffering': 'no',
  });
  const send = (event, data) => {
    if (!res.destroyed) res.write(`event: ${event}\ndata: ${JSON.stringify(data)}\n\n`);
  };
  let runtime;
  let session;
  try {
    runtime = await createEmbeddedRuntime({
      baseUrl: body.baseUrl,
      apiKey: body.apiKey || process.env.DEEPSEEK_API_KEY || '',
      model: body.model,
      systemPrompt: body.systemPrompt || 'You are ReinChat, a concise chat assistant. Use the user\'s language.',
    });
    session = await runtime.createSession(body.sessionId || ('reinchat-' + randomUUID()));
    send('session', { sessionId: session.id });
    await session.send(text, {
      onFrame(frame) { send('frame', frame); },
      onEvent(event) { send('event', event); },
    });
    send('done', { sessionId: session.id });
  } catch (error) {
    send('error', { message: error.message });
  } finally {
    try { await session?.dispose(); } catch { /* ignore */ }
    try { await runtime?.dispose(); } catch { /* ignore */ }
    res.end();
  }
});

server.listen(PORT, HOST, () => console.log(`reinchat-embedded-gateway http://${HOST}:${PORT}`));
