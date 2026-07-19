<template>
  <view class="sl-tabs">
    <view
      v-for="(item, idx) in options"
      :key="idx"
      class="sl-tabs__item"
      :class="{ 'is-active': idx === modelValue }"
      @click="emit('update:modelValue', idx)"
    >
      <text>{{ item }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
defineProps<{
  modelValue: number;
  options: string[];
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', value: number): void;
}>();
</script>

<style scoped>
.sl-tabs {
  display: flex;
  gap: 16px;
  border-bottom: 1px solid var(--border-color, #d8c1b8);
}

.sl-tabs__item {
  padding: 10px 0;
  color: var(--text-secondary, #78716c);
  font-size: 14px;
  position: relative;
}

.sl-tabs__item.is-active {
  color: var(--primary-hover, #c25c33);
  font-weight: 600;
}

.sl-tabs__item.is-active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -1px;
  height: 2px;
  background: var(--primary-color, #cd6a43);
  border-radius: 2px;
}
</style>
