import { createEmbeddedRuntime } from './embedded-runtime.mjs';

const runtime = await createEmbeddedRuntime({
  baseUrl: process.env.OPENAI_BASE_URL || 'https://api.deepseek.com/v1',
  apiKey: process.env.OPENAI_API_KEY || process.env.DEEPSEEK_API_KEY || 'dummy-key',
  model: process.env.OPENAI_MODEL || 'deepseek-chat',
  systemPrompt: 'You are ReinChat, a concise chat assistant.',
  workspace: process.cwd(),
});
const session = await runtime.createSession('reinchat-poc-' + Date.now());
try {
  await session.send('Say hello in one word.', {
    onFrame(frame) {
      if (frame?.chunk?.type === 'text-delta') process.stdout.write(frame.chunk.text);
      if (frame?.chunk?.type === 'reasoning-delta') process.stderr.write('[reason]' + frame.chunk.text);
    },
    onEvent(event) {
      if (event.type === 'turn/end') console.error('\nTURN_END', JSON.stringify(event.data?.reason));
    },
  });
} catch (error) {
  console.error('\nEMBEDDED_ERROR', error.message);
} finally {
  await session.dispose();
  await runtime.dispose();
}
