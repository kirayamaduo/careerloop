<template>
  <view class="sl-scroll-topbar" :style="barStyle">
    <view class="sl-scroll-topbar__inner" :style="innerStyle">
      <slot name="left" />
      <view class="sl-scroll-topbar__center">
        <slot>
          <text class="sl-scroll-topbar__title">{{ title }}</text>
        </slot>
      </view>
      <view class="sl-scroll-topbar__right">
        <slot name="right" />
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, type CSSProperties } from 'vue';

const props = withDefaults(
  defineProps<{
    title?: string;
    opacity?: number;
    safeTop?: number;
    rightAvoidWidth?: number;
    height?: number;
  }>(),
  {
    title: '',
    opacity: 0,
    safeTop: 0,
    rightAvoidWidth: 16,
    height: 44,
  },
);

const visibleOpacity = computed(() => Math.max(0, Math.min(1, props.opacity)));

const barStyle = computed<CSSProperties>(() => ({
  paddingTop: `${props.safeTop}px`,
  opacity: `${visibleOpacity.value}`,
  pointerEvents: visibleOpacity.value > 0.08 ? 'auto' : 'none',
}));

const innerStyle = computed<CSSProperties>(() => ({
  height: `${props.height}px`,
  paddingRight: props.rightAvoidWidth > 0 ? `${Math.max(props.rightAvoidWidth, 16)}px` : '16px',
}));
</script>

<style scoped>
.sl-scroll-topbar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 880;
  background: var(--topbar-bg, rgba(255, 255, 255, 0.94));
  border-bottom: 1px solid var(--topbar-border, rgba(231, 229, 228, 0.86));
  box-sizing: border-box;
  transition: opacity 0.12s ease;
}

.sl-scroll-topbar__inner {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-left: 16px;
  box-sizing: border-box;
}

.sl-scroll-topbar__center {
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
  pointer-events: none;
}

.sl-scroll-topbar__right {
  min-width: var(--nav-back-width, 64px);
  display: flex;
  align-items: center;
  justify-content: flex-end;
  position: relative;
  z-index: 2;
}

.sl-scroll-topbar__title {
  font-size: var(--font-section, 17px);
  font-weight: 700;
  color: var(--text-primary, #1c1917);
}

</style>
