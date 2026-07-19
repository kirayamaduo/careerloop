<template>
  <view class="sl-input-wrap">
    <text v-if="label" class="sl-input__label">{{ label }}</text>
    <input
      class="sl-input"
      :value="modelValue"
      :placeholder="placeholder"
      :password="password"
      @input="onInput"
      @confirm="emit('confirm')"
    />
  </view>
</template>

<script setup lang="ts">
const props = withDefaults(
  defineProps<{
    modelValue?: string;
    label?: string;
    placeholder?: string;
    password?: boolean;
  }>(),
  {
    modelValue: '',
    label: '',
    placeholder: '',
    password: false,
  },
);

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void;
  (e: 'confirm'): void;
}>();

const onInput = (e: any) => {
  emit('update:modelValue', e?.detail?.value ?? '');
};
</script>

<style scoped>
.sl-input-wrap {
  width: 100%;
}

.sl-input__label {
  display: block;
  margin-bottom: 8px;
  font-size: 12px;
  color: var(--text-secondary, #78716c);
}

.sl-input {
  width: 100%;
  height: 40px;
  border-radius: var(--radius-sm, 12px);
  background: var(--surface-1, #ffffff);
  border: 1px solid var(--border-color, #d8c1b8);
  padding: 0 12px;
  font-size: 14px;
  color: var(--text-primary, #1c1917);
  box-sizing: border-box;
}
</style>
