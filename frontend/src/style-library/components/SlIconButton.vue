<template>
  <view class="sl-icon-button" @click="emit('click')">
    <slot name="icon" />
    <text class="sl-icon-button__label">{{ label }}</text>
  </view>
</template>

<script setup lang="ts">
defineProps<{
  label: string;
}>();

const emit = defineEmits<{
  (e: 'click'): void;
}>();
</script>

<style scoped>
.sl-icon-button {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 0;
}

.sl-icon-button__label {
  font-size: 13px;
  font-weight: 700;
  color: var(--text-primary, #1c1917);
  letter-spacing: 0.02em;
  text-align: center;
  line-height: 1.25;
  min-height: 32px;
  display: -webkit-box;
  line-clamp: 2;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
