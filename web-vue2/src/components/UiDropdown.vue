<template>
  <div class="dd" :class="{ open }">
    <button type="button" class="dd-trigger" :disabled="disabled" @click.stop="open = !open">{{ label }}<span class="chev">⌄</span></button>
    <div v-if="open" class="dd-menu" @click.stop>
      <button v-for="option in options" :key="option.value" class="dd-item" :class="{ active: option.value === value }" @click="choose(option)">{{ option.label }}</button>
      <p class="dd-empty" v-if="!options.length">没有选项</p>
    </div>
  </div>
</template>

<script>
export default {
  name: 'UiDropdown',
  props: {
    value: [String, Number], options: { type: Array, default: () => [] },
    placeholder: { type: String, default: '请选择' }, disabled: Boolean,
  },
  data() { return { open: false }; },
  computed: { label() { return this.options.find((o) => o.value === this.value)?.label || this.placeholder; } },
  methods: { choose(option) { this.$emit('input', option.value); this.open = false; } },
};
</script>
