<template>
  <div class="dd" :class="{ open }">
    <button type="button" class="dd-trigger" :disabled="disabled" @click.stop="open = !open">
      <span class="dd-label">{{ label }}</span><span class="dd-chevron">⌄</span>
    </button>
    <transition name="drop">
      <div v-if="open" class="dd-menu" @click.stop>
        <button v-for="option in options" :key="option.value" class="dd-item" @click="choose(option)">
          <span class="dd-item-main">{{ option.label }}</span>
          <span v-if="option.value === value" class="dd-check">✓</span>
        </button>
        <p v-if="!options.length" class="empty-small">没有选项</p>
      </div>
    </transition>
  </div>
</template>

<script>
export default {
  name: 'UiDropdown',
  props: {
    value: [String, Number],
    options: { type: Array, default: () => [] },
    placeholder: { type: String, default: '请选择' },
    disabled: Boolean,
  },
  data() { return { open: false }; },
  computed: {
    label() { return this.options.find((o) => o.value === this.value)?.label || this.placeholder; },
  },
  methods: {
    choose(option) { this.$emit('input', option.value); this.open = false; },
  },
};
</script>
