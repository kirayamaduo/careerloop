<template>
  <view class="sl-page" :class="[{ 'is-dark': dark }, customClass]" :style="pageStyle">
    <slot />
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue';

const props = defineProps<{
  dark?: boolean;
  customClass?: string;
  safeBottom?: boolean;
  gutter?: string;
}>();

const pageStyle = computed(() => ({
  paddingLeft: props.gutter || undefined,
  paddingRight: props.gutter || undefined,
  paddingBottom: props.safeBottom ? 'env(safe-area-inset-bottom, 0px)' : undefined,
}));
</script>

<style scoped>
.sl-page {
  min-height: 100vh;
  background-color: var(--paper, #faf9f6);
  color: var(--ink, #2c2b29);
  font-family: var(--font-sans, "Noto Sans SC", "PingFang SC", "Microsoft YaHei", sans-serif);
  box-sizing: border-box;
}

.sl-page.is-dark {
  --paper: #1c1917;
  --paper-soft: #292524;
  --paper-deep: #44403c;
  --card-bg: #292524;
  --ink: #fafaf9;
  --ink-secondary: #d6d3d1;
  --ink-tertiary: #a8a29e;
  --text-primary: #fafaf9;
  --text-secondary: #d6d3d1;
  --text-tertiary: #a8a29e;
  --surface-1: #292524;
  --surface-2: #1c1917;
  --surface-3: #44403c;
  --border-color: #57534e;
  background-color: var(--bg-color, #1c1917);
  color: #e7e5e4;
}

.sl-page.resume-template-page {
  padding-bottom: calc(100px + env(safe-area-inset-bottom, 0px));
}

.sl-page.chat-container {
  height: 100vh;
  display: flex;
  flex-direction: column;
}

.sl-page.legacy-bridge {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 24px;
  text-align: center;
}

.sl-page.market-page {
  padding: 0 var(--page-gutter, 20px) 24px;
}

.sl-page.history-page {
  min-height: 100vh;
}

.sl-page.webview-container {
  width: 100vw;
  min-height: 100vh;
}

.sl-page.competition-report-page {
  padding: 0 20px;
  padding-bottom: calc(100px + env(safe-area-inset-bottom, 0px));
}
</style>
