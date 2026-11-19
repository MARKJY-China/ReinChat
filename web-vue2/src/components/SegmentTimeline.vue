<template>
  <div class="segment-timeline">
    <template v-for="(segment, index) in segments" :key="index">
      <div v-if="segment.type === 'reasoning'" class="thinking" :class="{ open: isOpen(index) }">
        <button class="thinking-head" :aria-expanded="isOpen(index) ? 'true' : 'false'" @click="toggle(index)">
          <svg class="chev" viewBox="0 0 12 12" aria-hidden="true"><path d="M4 2.2 7.8 6 4 9.8"/></svg>
          <span>{{ segment.label || (index === 0 ? '思考过程' : '继续思考') }}</span>
          <span class="thinking-elapsed">{{ elapsed(segment) }}</span>
          <span class="thinking-size">{{ segment.text.length }} 字</span>
        </button>
        <transition name="think">
          <div v-show="isOpen(index)" class="thinking-body"><pre>{{ segment.text }}</pre></div>
        </transition>
      </div>
      <div v-else class="tool-card" :class="segment.status">
        <svg class="tool-icon" viewBox="0 0 16 16" aria-hidden="true">
          <template v-if="segment.name === 'fetch_url' || segment.name === 'web_fetch'">
            <circle cx="8" cy="8" r="5.6"/><path d="M2.4 8h11.2M8 2.4c2.5 2.9 2.5 8.3 0 11.2M8 2.4c-2.5 2.9-2.5 8.3 0 11.2"/>
          </template>
          <template v-else>
            <circle cx="7" cy="7" r="4.2"/><path d="m10.2 10.2 3.1 3.1"/>
          </template>
        </svg>
        <span class="tool-name">{{ segment.label || segment.name || '工具' }}</span>
        <span class="tool-query" v-if="segment.query">「{{ segment.query }}」</span>
        <a class="tool-url" v-if="segment.url" :href="segment.url" target="_blank" rel="noopener noreferrer" :title="segment.url" @click.stop>{{ host(segment.url) }}</a>
        <span class="tool-state">{{ toolState(segment.status) }}</span>
        <span class="tool-elapsed">{{ elapsed(segment) }}</span>
        <span class="tool-summary" v-if="segment.summary">{{ segment.summary }}</span>
        <details class="tool-detail" v-if="segment.detail"><summary><svg class="chev" viewBox="0 0 12 12" aria-hidden="true"><path d="M4 2.2 7.8 6 4 9.8"/></svg>结果</summary><pre>{{ segment.detail }}</pre></details>
      </div>
    </template>
  </div>
</template>

<script>
export default {
  name: 'SegmentTimeline',
  props: {
    segments: { type: Array, default: () => [] },
    now: { type: Number, default: 0 },
    streaming: Boolean,
  },
  data() { return { openState: {} }; },
  methods: {
    isOpen(index) {
      if (Object.prototype.hasOwnProperty.call(this.openState, index)) return this.openState[index];
      return !!this.streaming;
    },
    toggle(index) { this.$set(this.openState, index, !this.isOpen(index)); },
    elapsed(segment) {
      const start = Number(segment.startedAt) || 0;
      if (!start) return '';
      const end = Number(segment.endedAt) || (this.streaming ? this.now : Date.now());
      const seconds = Math.max(0, (end - start) / 1000);
      return seconds.toFixed(1) + 's';
    },
    toolState(status) {
      if (status === 'running') return '进行中';
      if (status === 'failed') return '失败';
      return '完成';
    },
    host(url) { try { return new URL(url).hostname.replace(/^www\./, ''); } catch (e) { return String(url || ''); } },
  },
};
</script>
