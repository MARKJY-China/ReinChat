<template>
  <article class="turn" :class="message.role">
    <div class="turn-head">
      <span class="who">{{ message.role === 'user' ? '你' : (message.model || 'ReinChat') }}</span>
      <time>{{ formatTime(message.createdAt) }}</time>
    </div>
    <details class="thinking" v-if="message.reasoning">
      <summary>思考过程</summary>
      <pre>{{ message.reasoning }}</pre>
    </details>
    <div class="tool-stack" v-if="message.toolCalls && message.toolCalls.length">
      <div v-for="tool in message.toolCalls" :key="tool.callId || tool.name" class="tool-card" :class="tool.status">
        <span class="tool-dot"></span><span>{{ tool.name }}</span>
        <span class="tool-state">{{ tool.status === 'running' ? '运行中' : (tool.status === 'failed' ? '失败' : '完成') }}</span>
        <details v-if="tool.detail"><summary>结果</summary><pre>{{ tool.detail }}</pre></details>
      </div>
    </div>
    <div class="prose" v-html="html"></div>
    <div class="stream-line" v-if="message.streaming"><span></span>{{ streamPhase === 'streaming' ? 'AI 正在输出' : '请求服务端中' }}<button class="stream-stop" @click="$emit('stop')">停止</button></div>
    <div class="turn-error" v-if="message.error">
      <strong>生成失败</strong>
      <details><summary>查看详情</summary><pre>{{ message.error }}</pre></details>
    </div>
    <div class="turn-actions" v-if="!message.streaming">
      <button v-if="message.role === 'user'" @click="$emit('edit', message)">编辑</button>
      <button v-if="message.role === 'assistant'" @click="$emit('retry', message)">重试</button>
      <button v-if="message.content" @click="copyMessage">{{ copied ? '已复制' : '复制' }}</button>
      <button @click="$emit('rewind')">回退</button>
    </div>
  </article>
</template>

<script>
import { renderMarkdown } from '../lib/markdown';
export default {
  name: 'MessageItem',
  props: { message: { type: Object, required: true }, streaming: Boolean, streamPhase: { type: String, default: '' } },
  data() { return { copied: false }; },
  computed: { html() { return renderMarkdown(this.message.content); } },
  methods: {
    formatTime(ms) {
      if (!ms) return '';
      return new Date(ms).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
    },
    async copyMessage() {
      const text = this.message.content || '';
      try {
        if (navigator.clipboard) await navigator.clipboard.writeText(text);
        else {
          const area = document.createElement('textarea');
          area.value = text; document.body.appendChild(area); area.select();
          document.execCommand('copy'); area.remove();
        }
        this.copied = true;
        setTimeout(() => { this.copied = false; }, 1400);
      } catch (e) { /* ignore */ }
    },
  },
};
</script>
