<template>
  <footer class="composer-dock">
    <div class="composer">
      <textarea :value="draft" rows="1" placeholder="输入消息…" @input="$emit('update:draft', $event.target.value)" @keydown.enter.exact.prevent="$emit('send')"></textarea>
      <div class="composer-foot">
        <div class="composer-settings">
          <UiDropdown :value="providerId" :options="providerOptions" placeholder="服务商" :disabled="sending || level < 2" :open="openDropdown === 'provider'" @toggle="toggleDropdown('provider')" @input="$emit('update:providerId', $event)" @select="openDropdown = ''" />
          <UiDropdown :value="model" :options="modelOptions" placeholder="模型" :disabled="sending || !modelOptions.length" :open="openDropdown === 'model'" @toggle="toggleDropdown('model')" @input="$emit('update:model', $event)" @select="openDropdown = ''" />
          <UiDropdown :value="reasoningEffort" :options="reasoningOptions" placeholder="思考" :disabled="sending || level < 2" :open="openDropdown === 'reasoning'" @toggle="toggleDropdown('reasoning')" @input="$emit('update:reasoningEffort', $event)" @select="openDropdown = ''" />
          <button class="chip" :class="{ on: search }" :disabled="sending || level < 2" @click="$emit('update:search', !search)">联网</button>
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
  },
  data() { return { openDropdown: '' }; },
  watch: {
    sending(value) { if (value) this.openDropdown = ''; },
  },
  mounted() { document.addEventListener('click', this.closeDropdown); },
  beforeDestroy() { document.removeEventListener('click', this.closeDropdown); },
  methods: {
    toggleDropdown(key) { this.openDropdown = this.openDropdown === key ? '' : key; },
    closeDropdown() { this.openDropdown = ''; },
  },
};
</script>
