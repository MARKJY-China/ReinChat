<template>
  <article class="turn" :class="message.role">
    <div class="turn-head">
      <span class="who">{{ message.role === 'user' ? '你' : (message.model || 'ReinChat') }}</span>
      <time>{{ formatTime(message.createdAt) }}</time>
    </div>
    <div class="process" v-if="segments.length">
      <template v-for="(segment, index) in segments" :key="index">
        <div v-if="segment.type === 'reasoning'" class="thinking" :class="{ open: isOpen(index) }">
          <button class="thinking-head" :aria-expanded="isOpen(index) ? 'true' : 'false'" @click="toggle(index)">
            <svg class="chev" viewBox="0 0 12 12" aria-hidden="true"><path d="M4 2.2 7.8 6 4 9.8"/></svg>
            <span>{{ index === 0 ? '思考过程' : '继续思考' }}</span>
            <span class="thinking-size">{{ segment.text.length }} 字</span>
          </button>
          <transition name="think">
            <div v-show="isOpen(index)" class="thinking-body"><pre>{{ segment.text }}</pre></div>
          </transition>
        </div>
        <div v-else class="tool-card" :class="segment.status">
          <span class="tool-dot"></span>
          <span>{{ segment.label }}</span>
          <span class="tool-state">{{ segment.status === 'running' ? '进行中' : (segment.status === 'failed' ? '失败' : '完成') }}</span>
          <details v-if="segment.detail"><summary>结果</summary><pre>{{ segment.detail }}</pre></details>
        </div>
      </template>
    </div>
    <details class="thinking" v-else-if="message.reasoning">
      <summary>思考过程</summary>
      <pre>{{ message.reasoning }}</pre>
    </details>
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
  data() { return { copied: false, openState: {} }; },
  computed: {
    html() { return renderMarkdown(this.message.content); },
    segments() {
      if (this.message.segments && this.message.segments.length) return this.message.segments;
      return [];
    },
  },
  methods: {
    isOpen(index) {
      if (Object.prototype.hasOwnProperty.call(this.openState, index)) return this.openState[index];
      return !!this.message.streaming;
    },
    toggle(index) { this.$set(this.openState, index, !this.isOpen(index)); },
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
