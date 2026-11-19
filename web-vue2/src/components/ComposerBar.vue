<template>
  <footer class="composer-dock">
    <div class="composer">
      <div class="pdf-chip" v-if="pdfName"><span class="pdf-icon">PDF</span><span class="pdf-name">{{ pdfName }}</span><button class="pdf-remove" @click="$emit('remove-pdf')">×</button></div>
      <div class="image-strip" v-if="imagePreviews && imagePreviews.length">
        <span v-for="(img, index) in imagePreviews" :key="index" class="image-thumb"><img :src="img.url" :alt="img.name"><button @click="$emit('remove-image', index)">×</button></span>
      </div>
      <textarea :value="draft" rows="1" placeholder="输入消息，或粘贴 PDF / 图片…" @input="$emit('update:draft', $event.target.value)" @keydown.enter.exact.prevent="$emit('send')" @paste="onPaste"></textarea>
      <div class="composer-foot">
        <div class="composer-settings">
          <UiDropdown :value="providerId" :options="providerOptions" placeholder="服务商" :disabled="sending || level < 2" :open="openDropdown === 'provider'" @toggle="toggleDropdown('provider')" @input="$emit('update:providerId', $event)" @select="openDropdown = ''" />
          <UiDropdown :value="model" :options="modelOptions" placeholder="模型" :disabled="sending || !modelOptions.length" :open="openDropdown === 'model'" @toggle="toggleDropdown('model')" @input="$emit('update:model', $event)" @select="openDropdown = ''" />
          <UiDropdown :value="reasoningEffort" :options="reasoningOptions" placeholder="思考" :disabled="sending || level < 2" :open="openDropdown === 'reasoning'" @toggle="toggleDropdown('reasoning')" @input="$emit('update:reasoningEffort', $event)" @select="openDropdown = ''" />
          <button class="chip" :class="{ on: search }" :disabled="sending || level < 2" @click="$emit('update:search', !search)">联网</button>
          <label class="chip pdf-trigger" :class="{ loading: pdfBusy }" :title="pdfBusy ? '解析 PDF 中…' : '附加 PDF'">
            {{ pdfBusy ? '解析中' : 'PDF' }}
            <input type="file" accept="application/pdf,.pdf" :disabled="pdfBusy || sending" @change="$emit('attach-pdf', $event.target.files[0]); $event.target.value = ''">
          </label>
        </div>
        <button class="send-btn" :class="{ stop: sending }" :disabled="!sending && (!draft.trim() || !providerId)" @click="sending ? $emit('stop') : $emit('send')" :title="sending ? '停止生成' : '发送'">
          <span v-if="!sending">↑</span><span v-else class="stop-square"></span>
        </button>
      </div>
    </div>
    <p class="composer-hint">{{ level < 2 ? '旧版 Chat 内核 · Enter 发送' : 'Enter 发送 · Shift + Enter 换行' }}</p>
  </footer>
</template>

<script>
import UiDropdown from './UiDropdown.vue';
export default {
  name: 'ComposerBar',
  components: { UiDropdown },
  props: {
    providerId: String, model: String, reasoningEffort: String, search: Boolean,
    draft: String, sending: Boolean, level: Number,
    providerOptions: Array, modelOptions: Array, reasoningOptions: Array,
    pdfName: { type: String, default: '' }, pdfBusy: Boolean,
    imagePreviews: { type: Array, default: () => [] },
  },
  data() { return { openDropdown: '' }; },
  watch: {
    sending(value) { if (value) this.openDropdown = ''; },
  },
  mounted() { document.addEventListener('click', this.closeDropdown); },
  beforeDestroy() { document.removeEventListener('click', this.closeDropdown); },
  methods: {
    onPaste(event) {
      const files = event.clipboardData && event.clipboardData.files;
      if (!files || !files.length) return;
      event.preventDefault();
      this.$emit('paste-files', Array.from(files));
    },
    toggleDropdown(key) { this.openDropdown = this.openDropdown === key ? '' : key; },
    closeDropdown() { this.openDropdown = ''; },
  },
};
</script>
