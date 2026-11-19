<template>
  <article class="turn" :class="message.role">
    <div class="turn-head">
      <span class="who">{{ message.role === 'user' ? '你' : (message.model || 'ReinChat') }}</span>
      <time>{{ formatTime(message.createdAt) }}</time>
    </div>
    <div class="message-pdf" v-if="message.pdfName"><span class="pdf-icon">PDF</span><span>{{ message.pdfName }}</span></div>
    <div class="message-images" v-if="message.images && message.images.length">
      <img v-for="(img, index) in message.images" :key="index" :src="img.url || ('/api/v1/files/' + img.id)" alt="附件图片" @click="previewIndex = index">
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
          <span class="tool-name">{{ segment.label || segment.name || '工具' }}</span>
          <span class="tool-query" v-if="segment.query">“{{ segment.query }}”</span>
          <a class="tool-url" v-if="segment.url" :href="segment.url" target="_blank" rel="noopener noreferrer" :title="segment.url" @click.stop>{{ host(segment.url) }}</a>
          <span class="tool-state">{{ toolState(segment.status) }}</span>
          <span class="tool-summary" v-if="segment.summary">{{ segment.summary }}</span>
          <details class="tool-detail" v-if="segment.detail"><summary>结果</summary><pre>{{ segment.detail }}</pre></details>
        </div>
      </template>
    </div>
    <details class="thinking" v-else-if="message.reasoning">
      <summary>思考过程</summary>
      <pre>{{ message.reasoning }}</pre>
    </details>
    <div class="prose" v-html="html"></div>
    <details class="sources" v-if="sources.length">
      <summary><span>参考来源</span><span class="source-count">{{ sources.length }}</span></summary>
      <ol class="source-list">
        <li v-for="source in sources" :key="source.url">
          <span class="source-index">{{ source.index }}</span>
          <span class="source-body">
            <a :href="source.url" target="_blank" rel="noopener noreferrer" :title="source.title || source.url">{{ source.title || host(source.url) }}</a>
            <span class="source-host">{{ host(source.url) }}</span>
          </span>
        </li>
      </ol>
    </details>
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
    <ImagePreview v-if="previewIndex >= 0" :images="message.images.map((img) => img.url || ('/api/v1/files/' + img.id))" :index.sync="previewIndex" @close="previewIndex = -1" />
  </article>
</template>

<script>
import { renderMarkdown } from '../lib/markdown';
import ImagePreview from './ImagePreview.vue';
export default {
  name: 'MessageItem',
  components: { ImagePreview },
  props: { message: { type: Object, required: true }, streaming: Boolean, streamPhase: { type: String, default: '' } },
  data() { return { copied: false, openState: {}, previewIndex: -1 }; },
  computed: {
    html() { return renderMarkdown(this.message.content, this.sources); },
    segments() {
      if (this.message.segments && this.message.segments.length) return this.message.segments;
      return [];
    },
    sources() {
      const raw = Array.isArray(this.message.sources) ? this.message.sources : [];
      const seen = new Set();
      const list = [];
      raw.forEach((source, position) => {
        const url = String((source && source.url) || '').trim();
        if (!/^https?:\/\//i.test(url) || seen.has(url)) return;
        seen.add(url);
        const parsed = Number(source.index);
        const index = Number.isFinite(parsed) && parsed > 0 ? Math.trunc(parsed) : position + 1;
        list.push({ index, title: String(source.title || url), url });
      });
      return list.sort((a, b) => a.index - b.index);
    },
  },
  methods: {
    host(url) {
      try { return new URL(url).hostname.replace(/^www\./, ''); } catch (e) { return String(url || ''); }
    },
    toolState(status) {
      if (status === 'running') return '进行中';
      if (status === 'failed') return '失败';
      return '完成';
    },
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
