<template>
  <view class="sl-section-title">
    <text class="sl-section-title__text">{{ title }}</text>
    <text class="sl-section-title__action" v-if="actionText" @click="emit('action')">{{ actionText }}</text>
  </view>
</template>

<script setup lang="ts">
defineProps<{
  title: string;
  actionText?: string;
}>();

const emit = defineEmits<{
  (e: 'action'): void;
}>();
</script>

<style scoped>
.sl-section-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.sl-section-title__text {
  font-size: var(--font-title, 18px);
  font-weight: 700;
  color: var(--text-primary, #1c1917);
  letter-spacing: -0.3px;
}

.sl-section-title__action {
  font-size: 13px;
  color: var(--primary-color, #cd6a43);
  font-weight: 500;
}
</style>
