<template>
  <footer class="composer-shell">
    <div class="composer">
      <div class="composer-tools">
        <UiDropdown :value="providerId" :options="providerOptions" placeholder="选择服务商" :disabled="sending || level < 2" @input="$emit('update:providerId', $event)" />
        <UiDropdown :value="model" :options="modelOptions" placeholder="选择模型" :disabled="sending || !modelOptions.length" @input="$emit('update:model', $event)" />
        <UiDropdown :value="reasoningEffort" :options="reasoningOptions" placeholder="思考：自动" :disabled="sending || level < 2" @input="$emit('update:reasoningEffort', $event)" />
        <button class="search-toggle" :class="{ on: search }" :disabled="sending || level < 2" @click="$emit('update:search', !search)"><span class="search-dot"></span>联网</button>
      </div>
      <textarea :value="draft" rows="2" placeholder="给 ReinChat 发送消息…" @input="$emit('update:draft', $event.target.value)" @keydown.enter.exact.prevent="$emit('send')"></textarea>
      <div class="composer-bottom">
        <div class="composer-hint">{{ level < 2 ? 'LV2 才能使用服务端 Agent' : '通过 ReinChat Agent · ' + (model || '未选择模型') }}</div>
        <button class="primary send" :disabled="sending || !draft.trim() || level < 2 || !providerId" @click="$emit('send')">{{ sending ? '生成中' : '发送' }}</button>
      </div>
    </div>
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
