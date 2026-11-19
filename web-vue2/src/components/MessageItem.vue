<template>
  <article class="turn" :class="message.role">
    <div class="turn-head" v-if="message.role === 'assistant'">
      <span class="who">{{ message.model || 'ReinChat' }}</span>
    </div>
    <div class="message-pdf" v-if="message.pdfName"><span class="pdf-icon">PDF</span><span>{{ message.pdfName }}</span></div>
    <div class="message-images" v-if="message.images && message.images.length">
      <img v-for="(img, index) in message.images" :key="index" :src="img.url || ('/api/v1/files/' + img.id)" alt="附件图片" @click="previewIndex = index">
    </div>

    <div class="process" v-if="showLiveProcess">
      <SegmentTimeline :segments="segments" :now="now" :streaming="message.streaming" />
    </div>

    <div class="process" v-else-if="segments.length && !message.streaming && !hasTools">
      <SegmentTimeline :segments="segments" :now="now" :streaming="false" />
    </div>

    <details class="work-log" v-else-if="hasFinishedWork && hasTools && !message.streaming">
      <summary>
        <svg class="chev" viewBox="0 0 12 12" aria-hidden="true"><path d="M4 2.2 7.8 6 4 9.8"/></svg>
        <span>已完成工作</span>
        <span class="work-duration" v-if="workDuration">{{ workDuration }}</span>
      </summary>
      <div class="work-body">
        <SegmentTimeline :segments="segments" :now="now" :streaming="false" />
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

    <details class="thinking" v-else-if="!message.streaming && message.reasoning">
      <summary><svg class="chev" viewBox="0 0 12 12" aria-hidden="true"><path d="M4 2.2 7.8 6 4 9.8"/></svg>思考过程</summary>
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
      <button v-if="message.role === 'user' && message.content" @click="copyMessage">{{ copied ? '已复制' : '复制' }}</button>
      <button v-if="message.role === 'assistant' && message.content" @click="copyMessage">{{ copied ? '已复制' : '复制' }}</button>
      <button @click="$emit('rewind')">回退</button>
    </div>
    <time class="turn-time" v-if="message.role === 'user'">{{ formatTime(message.createdAt) }}</time>
    <ImagePreview v-if="previewIndex >= 0" :images="message.images.map((img) => img.url || ('/api/v1/files/' + img.id))" :index.sync="previewIndex" @close="previewIndex = -1" />
  </article>
</template>

<script>
import { renderMarkdown } from '../lib/markdown';
import ImagePreview from './ImagePreview.vue';
import SegmentTimeline from './SegmentTimeline.vue';
export default {
  name: 'MessageItem',
  components: { ImagePreview, SegmentTimeline },
  props: { message: { type: Object, required: true }, streaming: Boolean, streamPhase: { type: String, default: '' } },
  data() { return { copied: false, now: Date.now(), timer: null, previewIndex: -1 }; },
  computed: {
    html() { return renderMarkdown(this.message.content, this.sources); },
    segments() { return (this.message.segments || []).slice(); },
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
    showLiveProcess() {
      return this.message.streaming && !this.message.content && (this.segments.length > 0 || !!this.message.reasoning);
    },
    hasFinishedWork() {
      return this.segments.length > 0 || !!this.message.reasoning;
    },
    hasTools() {
      return this.segments.some((segment) => segment.type === 'tool');
    },
    workDuration() {
      const starts = this.segments.map((s) => Number(s.startedAt) || 0).filter(Boolean);
      const ends = this.segments.map((s) => Number(s.endedAt) || 0).filter(Boolean);
      if (!starts.length) return '';
      const start = Math.min(...starts);
      const end = ends.length ? Math.max(...ends) : (Number(this.message.createdAt) || start);
      const seconds = Math.max(0, (end - start) / 1000);
      return seconds ? `用时 ${seconds.toFixed(1)}s` : '';
    },
  },
  mounted() { this.startClock(); },
  beforeDestroy() { this.stopClock(); },
  watch: {
    streaming(value) { if (value) this.startClock(); else this.stopClock(); },
    'message.content'() { if (!this.message.streaming) this.stopClock(); },
  },
  methods: {
    startClock() {
      if (this.timer) return;
      this.timer = setInterval(() => { this.now = Date.now(); }, 100);
    },
    stopClock() { if (this.timer) { clearInterval(this.timer); this.timer = null; } this.now = Date.now(); },
    host(url) { try { return new URL(url).hostname.replace(/^www\./, ''); } catch (e) { return String(url || ''); } },
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
