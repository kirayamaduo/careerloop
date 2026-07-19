<template>
  <view v-if="visible" class="sl-modal">
    <view class="sl-modal__mask" @click="onMaskClick" />
    <view class="sl-modal__panel">
      <view class="sl-modal__header" v-if="title">
        <text class="sl-modal__title">{{ title }}</text>
      </view>
      <view class="sl-modal__body">
        <slot />
      </view>
      <view class="sl-modal__footer" v-if="$slots.footer">
        <slot name="footer" />
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
const props = withDefaults(
  defineProps<{
    visible: boolean;
    title?: string;
    maskClosable?: boolean;
  }>(),
  {
    title: '',
    maskClosable: true,
  },
);

const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void;
  (e: 'close'): void;
}>();

const onMaskClick = () => {
  if (!props.maskClosable) return;
  emit('update:visible', false);
  emit('close');
};
</script>

<style scoped>
.sl-modal {
  position: fixed;
  inset: 0;
  z-index: 999;
  display: flex;
  align-items: center;
  justify-content: center;
}

.sl-modal__mask {
  position: absolute;
  inset: 0;
  background: rgba(28, 25, 23, 0.45);
}

.sl-modal__panel {
  position: relative;
  width: 82%;
  max-width: 640rpx;
  background: var(--card-bg, #ffffff);
  border-radius: var(--radius-md, 16px);
  padding: 16px;
}

.sl-modal__header {
  margin-bottom: 10px;
}

.sl-modal__title {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-primary, #1c1917);
}

.sl-modal__body {
  font-size: 14px;
  color: var(--text-secondary, #78716c);
}

.sl-modal__footer {
  margin-top: 14px;
}
</style>
