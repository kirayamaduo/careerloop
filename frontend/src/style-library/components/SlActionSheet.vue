<template>
  <view v-if="visible" class="sl-sheet">
    <view class="sl-sheet__mask" @click="onClose" />
    <view class="sl-sheet__panel">
      <view v-if="title" class="sl-sheet__title-wrap">
        <text class="sl-sheet__title">{{ title }}</text>
      </view>
      <view
        v-for="(item, idx) in normalizedOptions"
        :key="item.value || idx"
        class="sl-sheet__item"
        :class="{ 'sl-sheet__item--active': item.value === selectedValue }"
        @click="onSelect(idx, item)"
      >
        <view class="sl-sheet__icon" v-if="item.icon">
          <text :class="item.icon"></text>
        </view>
        <view class="sl-sheet__copy">
          <text class="sl-sheet__label">{{ item.label }}</text>
          <text class="sl-sheet__subtitle" v-if="item.subtitle">{{ item.subtitle }}</text>
        </view>
        <text class="sl-sheet__check ri-check-line" v-if="item.value === selectedValue"></text>
      </view>
      <view class="sl-sheet__cancel" @click="onClose">
        <text>{{ cancelText || '取消' }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue';

export interface SlActionSheetOption {
  label: string;
  value: string;
  subtitle?: string;
  icon?: string;
}

const props = defineProps<{
  visible: boolean;
  title?: string;
  options: Array<string | SlActionSheetOption>;
  selectedValue?: string;
  cancelText?: string;
}>();

const normalizedOptions = computed<SlActionSheetOption[]>(() =>
  props.options.map((item) => typeof item === 'string'
    ? { label: item, value: item }
    : item)
);

const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void;
  (e: 'select', payload: { index: number; value: string; option: SlActionSheetOption }): void;
}>();

const onClose = () => emit('update:visible', false);

const onSelect = (index: number, option: SlActionSheetOption) => {
  emit('select', { index, value: option.value, option });
  emit('update:visible', false);
};
</script>

<style scoped>
.sl-sheet {
  position: fixed;
  inset: 0;
  z-index: 999;
}

.sl-sheet__mask {
  position: absolute;
  inset: 0;
  background: rgba(28, 25, 23, 0.35);
}

.sl-sheet__panel {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  background: var(--surface-2, #fafaf9);
  border-top-left-radius: var(--radius-lg, 20px);
  border-top-right-radius: var(--radius-lg, 20px);
  padding: 12px 12px calc(12px + env(safe-area-inset-bottom, 0px));
}

.sl-sheet__title-wrap {
  text-align: center;
  padding: 8px 0 10px;
}

.sl-sheet__title {
  font-size: 13px;
  color: var(--text-secondary, #78716c);
}

.sl-sheet__item,
.sl-sheet__cancel {
  background: var(--surface-1, #ffffff);
  min-height: 48px;
  border-radius: var(--radius-sm, 12px);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  color: var(--text-primary, #1c1917);
  box-sizing: border-box;
}

.sl-sheet__item {
  justify-content: flex-start;
  gap: 10px;
  padding: 12px 14px;
  border: 1px solid transparent;
}

.sl-sheet__item--active {
  border-color: var(--primary-color, #cd6a43);
  background: var(--primary-soft, #fcf5f2);
}

.sl-sheet__icon {
  width: 34px;
  height: 34px;
  border-radius: 12px;
  background: var(--surface-2, #fafaf9);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--primary-color, #cd6a43);
  flex-shrink: 0;
}

.sl-sheet__copy {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.sl-sheet__label {
  font-size: 15px;
  line-height: 1.25;
  font-weight: 600;
  color: var(--text-primary, #1c1917);
}

.sl-sheet__subtitle {
  font-size: 12px;
  line-height: 1.35;
  color: var(--text-secondary, #78716c);
}

.sl-sheet__check {
  color: var(--primary-color, #cd6a43);
  font-size: 18px;
  flex-shrink: 0;
}

.sl-sheet__item + .sl-sheet__item {
  margin-top: 8px;
}

.sl-sheet__cancel {
  margin-top: 10px;
  font-weight: 600;
}

.is-dark .sl-sheet__panel {
  background: #1c1917;
}

.is-dark .sl-sheet__item,
.is-dark .sl-sheet__cancel {
  background: #292524;
  color: #fafaf9;
}

.is-dark .sl-sheet__item--active {
  background: rgba(205, 106, 67, 0.18);
  border-color: rgba(232, 186, 168, 0.4);
}

.is-dark .sl-sheet__icon {
  background: #1c1917;
}

.is-dark .sl-sheet__label {
  color: #fafaf9;
}

.is-dark .sl-sheet__subtitle {
  color: #a8a29e;
}
</style>
