<template>
  <button class="sl-button" :class="[`sl-button--${variant}`]" :disabled="disabled" @click="emit('click')">
    <slot>{{ text }}</slot>
  </button>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    text?: string;
    variant?: 'primary' | 'secondary' | 'ghost' | 'danger';
    disabled?: boolean;
  }>(),
  {
    text: '',
    variant: 'primary',
    disabled: false,
  },
);

const emit = defineEmits<{
  (e: 'click'): void;
}>();
</script>

<style scoped>
.sl-button {
  border: 1px solid transparent;
  border-radius: var(--btn-radius, 14px);
  height: 40px;
  line-height: 40px;
  font-size: 14px;
  font-weight: 600;
  padding: 0 16px;
  box-sizing: border-box;
  font-family: inherit;
}

.sl-button--primary {
  background: var(--primary-color, #cd6a43);
  color: #fff;
  box-shadow: var(--shadow-card, 0 4px 16px rgba(0,0,0,0.14), 0 2px 6px rgba(0,0,0,0.08));
}

.sl-button--secondary {
  background: var(--surface-2, #fafaf9);
  color: var(--text-primary, #1c1917);
  border-color: var(--border-color, #d8c1b8);
}

.sl-button--ghost {
  background: transparent;
  color: var(--primary-color, #cd6a43);
  border-color: var(--primary-mid, #d47f5d);
}

.sl-button--danger {
  background: var(--danger-color, #ef4444);
  color: #fff;
}

.sl-button[disabled] {
  opacity: 0.6;
}
</style>
