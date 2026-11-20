<template>
  <article class="turn" :class="message.role">
    <div class="turn-head" v-if="message.role === 'assistant'">
      <span class="who">{{ message.model || 'ReinChat' }}</span>
    </div>

    <div class="message-pdf" v-if="message.pdfName"><span class="pdf-icon">PDF</span><span>{{ message.pdfName }}</span></div>
    <div class="message-images" v-if="message.images && message.images.length">
      <img
        v-for="(img, index) in message.images"
        :key="index"
        :src="img.url || ('/api/v1/files/' + img.id)"
        alt="附件图片"
        @click="previewIndex = index"
      >
    </div>

    <!-- Streaming: the whole process stays in chronological order. -->
    <template v-if="message.streaming">
      <div class="process" v-if="segments.length">
        <SegmentTimeline :segments="segments" :now="now" streaming />
      </div>
      <details class="thinking" v-else-if="message.reasoning">
        <summary><svg class="chev" viewBox="0 0 12 12" aria-hidden="true"><path d="M4 2.2 7.8 6 4 9.8"/></svg>思考过程</summary>
        <pre>{{ message.reasoning }}</pre>
      </details>
      <div class="stream-line">
        <span></span>{{ streamPhase === 'streaming' ? 'AI 正在输出' : '请求服务端中' }}
        <button class="stream-stop" @click="$emit('stop')">停止</button>
      </div>
    </template>

    <!-- Finished with tool work: fold the whole history away. -->
    <details class="work-log" v-else-if="hasWork">
      <summary>
        <svg class="chev" viewBox="0 0 12 12" aria-hidden="true"><path d="M4 2.2 7.8 6 4 9.8"/></svg>
        <span>已完成工作</span>
        <span class="work-stats">{{ workStats }}</span>
      </summary>
      <div class="work-body">
        <SegmentTimeline :segments="work" :now="now" />
        <div class="work-sources" v-if="sources.length">
          <div class="work-sources-title">搜索来源</div>
          <ol class="source-list">
            <li v-for="source in sources" :key="source.url">
              <span class="source-index">{{ source.index }}</span>
              <span class="source-body">
                <a :href="source.url" target="_blank" rel="noopener noreferrer" :title="source.title || source.url">{{ source.title || host(source.url) }}</a>
                <span class="source-host">{{ host(source.url) }}</span>
              </span>
            </li>
          </ol>
        </div>
      </div>
    </details>

    <!-- Finished without tools: only the thinking blocks. -->
    <div class="process" v-else-if="segments.length">
      <SegmentTimeline :segments="segments" :now="now" />
    </div>
    <details class="thinking" v-else-if="message.reasoning">
      <summary><svg class="chev" viewBox="0 0 12 12" aria-hidden="true"><path d="M4 2.2 7.8 6 4 9.8"/></svg>思考过程</summary>
      <pre>{{ message.reasoning }}</pre>
    </details>

    <div class="prose" v-if="answer" v-html="renderAnswer()"></div>

    <div class="turn-error" v-if="message.error">
      <strong>生成失败</strong>
      <div class="error-links">
        <details><summary>查看详情</summary><pre>{{ message.error }}</pre></details>
        <button class="copy-log" @click="copyLog">{{ logCopied ? '已复制日志' : '复制完整日志' }}</button>
      </div>
    </div>

    <div class="turn-actions">
      <button v-if="message.role === 'user'" @click="$emit('edit', message)">编辑</button>
      <button v-if="message.role === 'assistant'" @click="$emit('retry', message)">重试</button>
      <button v-if="answer" @click="copyMessage">{{ copied ? '已复制' : '复制' }}</button>
      <button @click="$emit('rewind')">回退</button>
    </div>

    <time class="turn-time" v-if="message.role === 'user'">{{ formatTime(message.createdAt) }}</time>

    <ImagePreview
      v-if="previewIndex >= 0"
      :images="message.images.map((img) => img.url || ('/api/v1/files/' + img.id))"
      :index.sync="previewIndex"
      @close="previewIndex = -1"
    />
  </article>
</template>

<script>
import { renderMarkdown } from '../lib/markdown';
import { splitAnswer, answerText } from '../lib/timeline';
import ImagePreview from './ImagePreview.vue';
import SegmentTimeline from './SegmentTimeline.vue';

export default {
  name: 'MessageItem',
  components: { ImagePreview, SegmentTimeline },
  props: {
    message: { type: Object, required: true },
    streaming: Boolean,
    streamPhase: { type: String, default: '' },
  },
  data() {
    return { copied: false, logCopied: false, now: Date.now(), timer: null, previewIndex: -1 };
  },
  computed: {
    segments() { return (this.message.segments || []).slice(); },
    split() { return splitAnswer(this.segments); },
    work() { return this.split.work; },
    hasWork() { return this.work.length > 0; },
    answer() { return answerText(this.segments, this.message.content); },
    workStats() {
      const tools = this.work.filter((segment) => segment.type === 'tool').length;
      const chars = this.work
        .filter((segment) => segment.type === 'reasoning')
        .reduce((total, segment) => total + (segment.text || '').length, 0);
      const parts = [];
      if (tools) parts.push(`${tools} 项工具`);
      if (chars) parts.push(`${chars} 字`);
      return parts.join(' · ');
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
  mounted() { if (this.message.streaming) this.startClock(); },
  beforeDestroy() { this.stopClock(); },
  watch: {
    streaming(value) { if (value) this.startClock(); else this.stopClock(); },
  },
  methods: {
    renderAnswer() { return renderMarkdown(this.answer, this.sources); },
    startClock() {
      if (this.timer) return;
      this.timer = setInterval(() => { this.now = Date.now(); }, 100);
    },
    stopClock() {
      if (this.timer) { clearInterval(this.timer); this.timer = null; }
      this.now = Date.now();
    },
    host(url) { try { return new URL(url).hostname.replace(/^www\./, ''); } catch (e) { return String(url || ''); } },
    formatTime(ms) {
      if (!ms) return '';
      return new Date(ms).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
    },
    buildLog() {
      const lines = [];
      lines.push('# ReinChat 生成失败日志');
      lines.push('时间: ' + new Date(this.message.createdAt || Date.now()).toISOString());
      lines.push('模型: ' + (this.message.model || '-'));
      lines.push('状态: ' + (this.message.status || '-'));
      lines.push('');
      lines.push('## 错误');
      lines.push(this.message.error || '');
      if (this.segments.length) {
        lines.push('');
        lines.push('## 过程');
        this.segments.forEach((segment) => {
          if (segment.type === 'tool') {
            lines.push(`- [tool] ${segment.name || ''} ${segment.status || ''} ${segment.query || segment.url || ''} ${segment.summary || ''}`);
          } else if (segment.type === 'text') {
            lines.push('- [text] ' + String(segment.text || '').slice(0, 400));
          } else {
            lines.push('- [reasoning] ' + String(segment.text || '').slice(0, 400));
          }
        });
      }
      if (this.sources.length) {
        lines.push('');
        lines.push('## 来源');
        this.sources.forEach((source) => lines.push(`[${source.index}] ${source.title} ${source.url}`));
      }
      if (this.answer) {
        lines.push('');
        lines.push('## 已输出正文');
        lines.push(this.answer);
      }
      return lines.join('\n');
    },
    async writeClipboard(text) {
      if (navigator.clipboard) return navigator.clipboard.writeText(text);
      const area = document.createElement('textarea');
      area.value = text;
      document.body.appendChild(area);
      area.select();
      document.execCommand('copy');
      area.remove();
    },
    async copyLog() {
      try {
        await this.writeClipboard(this.buildLog());
        this.logCopied = true;
        setTimeout(() => { this.logCopied = false; }, 1600);
      } catch (e) { /* ignore */ }
    },
    async copyMessage() {
      try {
        await this.writeClipboard(this.answer || '');
        this.copied = true;
        setTimeout(() => { this.copied = false; }, 1400);
      } catch (e) { /* ignore */ }
    },
  },
};
</script>
