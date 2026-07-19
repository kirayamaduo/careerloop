<template>
  <view class="sl-segment">
    <view
      v-for="(item, idx) in options"
      :key="idx"
      class="sl-segment__item"
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
.sl-segment {
  display: flex;
  padding: 3px;
  background: var(--surface-3, #f5f5f4);
  border-radius: var(--radius-sm, 12px);
}

.sl-segment__item {
  flex: 1;
  min-height: 32px;
  border-radius: 8px;
  display: flex;
  justify-content: center;
  align-items: center;
  font-size: 13px;
  color: var(--text-secondary, #78716c);
}

.sl-segment__item.is-active {
  background: var(--surface-1, #ffffff);
  color: var(--text-primary, #1c1917);
  font-weight: 600;
}
</style>
