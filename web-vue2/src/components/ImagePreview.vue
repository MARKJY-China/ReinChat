<template>
  <transition name="lightbox">
    <div v-if="index >= 0" class="lightbox" @click.self="$emit('close')">
      <button class="lightbox-close" @click="$emit('close')">×</button>
      <button v-if="images.length > 1" class="lightbox-nav prev" @click.stop="move(-1)">‹</button>
      <img class="lightbox-image" :src="images[index]" alt="图片预览" @click.stop>
      <button v-if="images.length > 1" class="lightbox-nav next" @click.stop="move(1)">›</button>
      <span v-if="images.length > 1" class="lightbox-count">{{ index + 1 }} / {{ images.length }}</span>
    </div>
  </transition>
</template>

<script>
export default {
  name: 'ImagePreview',
  props: { images: { type: Array, default: () => [] }, index: { type: Number, default: -1 } },
  mounted() { document.addEventListener('keydown', this.onKey); },
  beforeDestroy() { document.removeEventListener('keydown', this.onKey); },
  methods: {
    move(delta) {
      const next = (this.index + delta + this.images.length) % this.images.length;
      this.$emit('update:index', next);
    },
    onKey(event) {
      if (this.index < 0) return;
      if (event.key === 'Escape') this.$emit('close');
      if (event.key === 'ArrowLeft') this.move(-1);
      if (event.key === 'ArrowRight') this.move(1);
    },
  },
};
</script>
