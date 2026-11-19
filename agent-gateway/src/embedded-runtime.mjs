import {
  AgentLoop,
  AgentRegistry,
  Context,
  LlmAdapter,
  LlmRuntime,
  SessionId,
  SessionProjectionRegistry,
  SessionStore,
  SystemPrompt,
  ToolCallId,
  ToolRuntime,
  createUserMessage,
} from '../vendor/dsh-core.mjs';

class OpenAiAdapter extends LlmAdapter {
  constructor({ baseUrl, apiKey }) {
    super();
    this.baseUrl = baseUrl.replace(/\/+$/, '');
    this.apiKey = apiKey;
  }
  resolveModel(provider, model) {
    return Promise.resolve({
      provider,
      id: model,
      name: model,
      reasoning: {
        efforts: ['minimal', 'low', 'medium', 'high', 'xhigh', 'max'].map((id) => ({ id, name: id })),
      },
    });
  }
  async *stream(options) {
    const messages = [];
    if (options.system) messages.push({ role: 'system', content: options.system });
    for (const message of options.messages) {
      if (message.role === 'user') {
        const text = (message.content || []).filter((b) => b.type === 'text').map((b) => b.text).join('');
        if (text) messages.push({ role: 'user', content: text });
      } else if (message.role === 'assistant') {
        const text = (message.content || []).filter((b) => b.type === 'text').map((b) => b.text).join('');
        if (text) messages.push({ role: 'assistant', content: text });
      } else if (message.role === 'tool') {
        const text = (message.content || []).filter((b) => b.type === 'text').map((b) => b.text).join('');
        messages.push({ role: 'tool', tool_call_id: message.toolCallId, content: text });
      }
    }
    const response = await fetch(this.baseUrl + '/chat/completions', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(this.apiKey ? { Authorization: 'Bearer ' + this.apiKey } : {}),
      },
      body: JSON.stringify({
        model: options.model,
        messages,
        stream: true,
        stream_options: { include_usage: true },
        ...(options.temperature !== undefined ? { temperature: options.temperature } : {}),
        ...(options.maxTokens !== undefined ? { max_tokens: options.maxTokens } : {}),
        ...(options.reasoningEffort ? { reasoning_effort: options.reasoningEffort } : {}),
      }),
      signal: options.signal,
    });
    if (!response.ok || !response.body) throw new Error('upstream HTTP ' + response.status);
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    let index = -1;
    let kind = 'text';
    let text = '';
    const close = () => {
      if (index < 0) return null;
      const closingIndex = index;
      const block = { type: kind, text };
      index = -1;
      text = '';
      return { type: 'block-end', index: closingIndex, block };
    };
    while (true) {
      const part = await reader.read();
      if (part.done) break;
      buffer += decoder.decode(part.value, { stream: true });
      let cut;
      while ((cut = buffer.indexOf('\n')) >= 0) {
        const line = buffer.slice(0, cut).trim();
        buffer = buffer.slice(cut + 1);
        if (!line.startsWith('data:')) continue;
        const data = line.slice(5).trim();
        if (!data || data === '[DONE]') continue;
        const payload = JSON.parse(data);
        const choice = payload.choices?.[0];
        const delta = choice?.delta || {};
        const reasoning = delta.reasoning_content || delta.reasoning || '';
        if (reasoning) {
          if (index < 0 || kind !== 'reasoning') {
            const end = close();
            if (end) yield end;
            index = 0;
            kind = 'reasoning';
            yield { type: 'block-start', index, blockType: 'reasoning' };
          }
          text += reasoning;
          yield { type: 'reasoning-delta', index, text: reasoning };
        }
        if (delta.content) {
          if (index < 0 || kind !== 'text') {
            const end = close();
            if (end) yield end;
            index = 0;
            kind = 'text';
            yield { type: 'block-start', index, blockType: 'text' };
          }
          text += delta.content;
          yield { type: 'text-delta', index, text: delta.content };
        }
        if (payload.usage) {
          yield { type: 'usage', usage: {
            inputTokens: payload.usage.prompt_tokens || 0,
            outputTokens: payload.usage.completion_tokens || 0,
            totalTokens: payload.usage.total_tokens,
          } };
        }
        if (choice?.finish_reason) {
          const end = close();
          if (end) yield end;
          yield { type: 'finish', reason: { kind: choice.finish_reason === 'length' ? 'max-tokens' : choice.finish_reason === 'tool_calls' ? 'tool-calls' : 'stop' } };
        }
      }
    }
    const end = close();
    if (end) yield end;
  }
}

export async function createEmbeddedRuntime({ baseUrl, apiKey, model, systemPrompt, workspace }) {
  const ctx = new Context();
  await ctx.plugin(LlmRuntime);
  await ctx.plugin(SessionStore);
  await ctx.plugin(SessionProjectionRegistry);
  await ctx.plugin(SystemPrompt, { personaPrefix: systemPrompt || 'You are ReinChat, a helpful chat assistant.' });
  await ctx.plugin(ToolRuntime);
  await ctx.plugin(AgentRegistry);
  await ctx.plugin(AgentLoop, { agents: [] });
  ctx.llm.registerAdapter(['openai'], new OpenAiAdapter({ baseUrl, apiKey }));
  return {
    async createSession(sessionId) {
      const handle = await ctx.agents.create({
        sessionId: SessionId(sessionId || ('reinchat-' + crypto.randomUUID())),
        agentOptions: { provider: 'openai', model },
      });
      const agent = handle.agent;
      return {
        id: String(agent.session.id),
        async send(text, ui) {
          const offStream = ctx.on('agent/assistant-stream', (payload) => {
            if (payload.agent === agent) ui?.onFrame?.(payload.frame);
          });
          const offEvent = ctx.on('session/event', (session, event) => {
            if (session === agent.session) ui?.onEvent?.(event);
          });
          try {
            agent.followup(createUserMessage({ content: [{ type: 'text', text }], source: { kind: 'user' } }));
            await agent.whenIdle();
          } finally {
            offStream?.();
            offEvent?.();
          }
        },
        cancel: () => agent.cancel(),
        async dispose() { await handle.dispose(); },
      };
    },
    async dispose() { await ctx.fiber.dispose(); },
  };
}
