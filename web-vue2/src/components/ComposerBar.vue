<template>
  <footer class="composer-dock">
    <div class="composer">
      <textarea :value="draft" rows="1" placeholder="输入消息…" @input="$emit('update:draft', $event.target.value)" @keydown.enter.exact.prevent="$emit('send')"></textarea>
      <div class="composer-foot">
        <div class="composer-settings">
          <UiDropdown :value="providerId" :options="providerOptions" placeholder="服务商" :disabled="sending || level < 2" @input="$emit('update:providerId', $event)" />
          <UiDropdown :value="model" :options="modelOptions" placeholder="模型" :disabled="sending || !modelOptions.length" @input="$emit('update:model', $event)" />
          <UiDropdown :value="reasoningEffort" :options="reasoningOptions" placeholder="思考" :disabled="sending || level < 2" @input="$emit('update:reasoningEffort', $event)" />
          <button class="chip" :class="{ on: search }" :disabled="sending || level < 2" @click="$emit('update:search', !search)">联网</button>
        </div>
        <button class="send-btn" :disabled="sending || !draft.trim() || level < 2 || !providerId" @click="$emit('send')" :title="sending ? '生成中' : '发送'">
          <span v-if="!sending">↑</span><span v-else class="spinner"></span>
        </button>
      </div>
    </div>
    <p class="composer-hint">{{ level < 2 ? 'LV2 可用服务端 Agent' : 'Enter 发送 · Shift + Enter 换行' }}</p>
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
};
</script>
