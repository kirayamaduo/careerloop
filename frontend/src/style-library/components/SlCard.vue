<template>
  <view class="sl-card" @click="emit('click')">
    <slot />
  </view>
</template>

<script setup lang="ts">
const emit = defineEmits<{
  (e: 'click'): void;
}>();
</script>

<style scoped>
.sl-card {
  background: var(--card-bg, #ffffff);
  border: 1px solid var(--border-color, #d8c1b8);
  border-radius: var(--radius-md, 16px);
  box-shadow: var(--shadow-sm, 0 4px 16px rgba(0,0,0,0.12), 0 2px 6px rgba(0,0,0,0.08));
  box-sizing: border-box;
}
</style>
