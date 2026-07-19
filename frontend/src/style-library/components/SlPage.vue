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
  --paper: #0f172a;
  --paper-soft: #1e293b;
  --paper-deep: #334155;
  --card-bg: #1e293b;
  --ink: #f8fafc;
  --ink-secondary: #cbd5e1;
  --ink-tertiary: #94a3b8;
  --text-primary: #f8fafc;
  --text-secondary: #cbd5e1;
  --text-tertiary: #94a3b8;
  --surface-1: #1e293b;
  --surface-2: #0f172a;
  --surface-3: #334155;
  --border-color: #475569;
  background-color: var(--bg-color, #0f172a);
  color: #e2e8f0;
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
