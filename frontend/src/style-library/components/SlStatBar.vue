<template>
  <view class="sl-stat-bar">
    <view v-for="(item, idx) in items" :key="idx" class="sl-stat-bar__item">
      <text class="sl-stat-bar__value">{{ item.value }}</text>
      <text class="sl-stat-bar__label">{{ item.label }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
defineProps<{
  items: Array<{ label: string; value: string | number }>;
}>();
</script>

<style scoped>
.sl-stat-bar {
  display: flex;
  background: var(--surface-1, #ffffff);
  border-radius: var(--btn-radius, 14px);
  border: 1px solid var(--border-color, #d8c1b8);
  overflow: hidden;
}

.sl-stat-bar__item {
  flex: 1;
  min-height: 60px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
}

.sl-stat-bar__item + .sl-stat-bar__item {
  border-left: 1px solid var(--border-color, #d8c1b8);
}

.sl-stat-bar__value {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-primary, #1c1917);
}

.sl-stat-bar__label {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-secondary, #78716c);
}
</style>
