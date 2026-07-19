<template>
  <SlPage class="app-soft-bg" :custom-class="['legacy-bridge', themeClass, fontClass].join(' ')" :style="{ paddingTop: topSafeHeight + 'px' }">
    <text class="bridge-title">{{ t('interview.bridgeTitle') }}</text>
    <text class="bridge-copy">{{ t('interview.bridgeCopy') }}</text>
  </SlPage>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useI18n } from '@/locales';
import SlPage from '@/style-library/components/SlPage.vue';
import { getMpSafeAreaMetrics } from '@/utils/safeArea';
import { useTheme } from '@/utils/theme';

const { t } = useI18n();
const { themeClass, fontClass, refresh: refreshTheme } = useTheme();
const topSafeHeight = ref(52);

onMounted(() => {
  refreshTheme();
  topSafeHeight.value = getMpSafeAreaMetrics().contentTop;
  setTimeout(() => {
    uni.redirectTo({ url: '/pages/interview/start' });
  }, 80);
});
</script>

<style scoped>
.bridge-title {
  display: block;
  font-size: 22px;
  font-weight: 800;
  color: var(--text-primary, #0f172a);
}

.bridge-copy {
  display: block;
  margin-top: 8px;
  font-size: 14px;
  line-height: 1.5;
  color: var(--text-secondary, #64748b);
}

/* Competition visual system: quiet paper surface while the bridge redirects. */
.bridge-title {
  font-family: "Songti SC", STSong, serif;
  font-weight: 600;
  letter-spacing: 0.06em;
  color: #2c2b29;
}

.bridge-copy {
  color: #5a5956;
}

.is-dark .bridge-title {
  color: #faf9f6;
}

.is-dark .bridge-copy {
  color: #c6c4be;
}
</style>
