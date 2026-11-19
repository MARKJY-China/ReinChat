<template>
  <article class="msg" :class="message.role">
    <div class="msg-avatar" v-if="message.role === 'assistant'"><img :src="'icon-192.png'" alt=""></div>
    <div class="msg-content">
      <div class="msg-role">{{ message.role === 'user' ? '你' : (message.model || 'ReinChat') }}<span>{{ formatTime(message.createdAt) }}</span></div>
      <details class="reasoning" v-if="message.reasoning">
        <summary>思考过程</summary>
        <pre>{{ message.reasoning }}</pre>
      </details>
      <div class="tool-stack" v-if="message.toolCalls && message.toolCalls.length">
        <div v-for="tool in message.toolCalls" :key="tool.callId || tool.name" class="tool-card" :class="tool.status">
          <span class="tool-pulse"></span><span class="tool-name">{{ tool.name }}</span>
          <span class="tool-status">{{ tool.status === 'running' ? '运行中' : (tool.status === 'failed' ? '失败' : '完成') }}</span>
          <details v-if="tool.detail" class="tool-detail"><summary>结果</summary><pre>{{ tool.detail }}</pre></details>
        </div>
      </div>
      <div class="msg-body markdown-body" v-html="html"></div>
      <div class="msg-stream" v-if="message.streaming"><span class="stream-dot"></span>{{ message.content ? '正在生成…' : '正在思考…' }}</div>
      <div class="msg-error" v-if="message.error">{{ message.error }}</div>
      <div class="msg-tools" v-if="!streaming">
        <button v-if="message.role === 'user'" @click="$emit('edit', message)">编辑</button>
        <button v-if="message.role === 'assistant'" @click="$emit('retry', message)">重试</button>
        <button @click="$emit('rewind')">回退到此处</button>
      </div>
    </div>
  </article>
</template>

<script>
import { renderMarkdown } from '../lib/markdown';
export default {
  name: 'MessageItem',
  props: { message: { type: Object, required: true }, streaming: Boolean },
  computed: {
    html() { return renderMarkdown(this.message.content); },
  },
  methods: {
    formatTime(ms) {
      if (!ms) return '';
      return new Date(ms).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
    },
  },
};
</script>
