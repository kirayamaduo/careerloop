<template>
  <view class="sl-search-bar">
    <view class="sl-search-bar__icon"><text class="ri-search-line"></text></view>
    <input
      class="sl-search-bar__input"
      :value="modelValue"
      :placeholder="placeholder"
      @input="onInput"
      @confirm="emit('confirm')"
    />
    <view v-if="modelValue" class="sl-search-bar__clear" @click="clear">
      <text>×</text>
    </view>
  </view>
</template>

<script setup lang="ts">
const props = withDefaults(
  defineProps<{
    modelValue?: string;
    placeholder?: string;
  }>(),
  {
    modelValue: '',
    placeholder: 'Search...'
  },
);

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void;
  (e: 'confirm'): void;
}>();

const onInput = (e: any) => emit('update:modelValue', e?.detail?.value ?? '');
const clear = () => emit('update:modelValue', '');
</script>

<style scoped>
.sl-search-bar {
  flex: 1;
  height: 38px;
  display: flex;
  align-items: center;
  background: var(--surface-1, #ffffff);
  border-radius: 19px;
  padding: 0 12px;
  border: 1px solid var(--border-color, #d8c1b8);
  box-sizing: border-box;
}

.sl-search-bar__icon {
  margin-right: 6px;
  font-size: 13px;
}

.sl-search-bar__input {
  flex: 1;
  height: 38px;
  font-size: 14px;
  color: var(--text-primary, #1c1917);
}

.sl-search-bar__clear {
  width: 20px;
  text-align: center;
  font-size: 18px;
  color: var(--text-tertiary, #8e8e93);
}
</style>
