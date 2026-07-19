<template>
  <view class="sl-navbar-wrapper" :style="wrapperStyle">
    <!-- Placeholder to keep document flow intact while nav is fixed -->
    <view class="sl-navbar__placeholder" :style="wrapperStyle"></view>
    
    <!-- Actual fixed nav bar -->
    <view class="sl-navbar" :style="navbarStyle">
      <view class="sl-navbar__inner" :style="innerStyle">
        <view class="sl-navbar__left" @click="emit('back')">
          <slot name="left">
            <text v-if="showBack" class="sl-navbar__back">‹</text>
          </slot>
        </view>
        <view class="sl-navbar__center" :style="centerStyle">
          <slot name="title">
            <text class="sl-navbar__title">{{ title }}</text>
          </slot>
        </view>
        <view class="sl-navbar__right">
          <slot name="right" />
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { getMpSafeAreaMetrics } from '@/utils/safeArea';

const props = withDefaults(
  defineProps<{
    title?: string;
    safeTop?: number;
    rightAvoidWidth?: number;
    height?: number;
    showBack?: boolean;
  }>(),
  {
    title: '',
    safeTop: 0,
    height: 44,
    showBack: false,
  },
);

const emit = defineEmits<{
  (e: 'back'): void;
}>();

const wrapperStyle = computed(() => ({
  height: `${props.safeTop + props.height}px`,
}));

const navbarStyle = computed(() => ({
  paddingTop: `${props.safeTop}px`,
}));

const autoRightAvoidWidth = ref(0);
const effectiveRightAvoidWidth = computed(() => props.rightAvoidWidth ?? autoRightAvoidWidth.value);

const innerStyle = computed(() => ({
  height: `${props.height}px`,
  paddingRight: effectiveRightAvoidWidth.value > 0 ? `${Math.max(effectiveRightAvoidWidth.value, 16)}px` : '16px',
}));

const centerStyle = computed(() => {
  const sideInset = Math.max(effectiveRightAvoidWidth.value || 0, 64);
  return {
    left: `${sideInset}px`,
    right: `${sideInset}px`,
  };
});

onMounted(() => {
  autoRightAvoidWidth.value = getMpSafeAreaMetrics().rightAvoidWidth;
});
</script>

<style scoped>
.sl-navbar-wrapper {
  width: 100%;
}

.sl-navbar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 900;
  background: var(--surface-1, #ffffff);
  box-sizing: border-box;
}

.sl-navbar__inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  box-sizing: border-box;
  position: relative;
}

.sl-navbar__left {
  width: var(--nav-back-width, 64px);
  display: flex;
  align-items: center;
  position: relative;
  z-index: 2;
}

.sl-navbar__right {
  width: var(--nav-back-width, 64px);
  display: flex;
  align-items: center;
  justify-content: flex-end;
  position: relative;
  z-index: 2;
}

.sl-navbar__center {
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
  z-index: 1;
  pointer-events: none;
}

.sl-navbar__title {
  font-size: var(--font-section, 17px);
  font-weight: 700;
  color: var(--text-primary, #1c1917);
  pointer-events: auto;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sl-navbar__back {
  font-size: 26px;
  line-height: 1;
  color: var(--text-primary, #1c1917);
  font-weight: 500;
}
</style>
