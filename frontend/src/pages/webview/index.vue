<template>
  <SlPage class="app-soft-bg" :custom-class="['webview-container', themeClass, fontClass].join(' ')">
    <SlNavBar :title="title" show-back @back="goBack" :safe-top="topSafe" />
    <web-view v-if="url && shouldUseWebview" :src="url" @error="handleError"></web-view>
    <view class="fallback" v-if="showFallback || !shouldUseWebview">
      <view class="fallback-card app-card-soft app-surface">
        <text class="fb-icon ri-link"></text>
        <text class="fb-text">{{ fallbackTitle }}</text>
        <text class="fb-desc">{{ fallbackDesc }}</text>
        <text class="fb-source">{{ sourceHint }}</text>
        <text class="fb-url">{{ url }}</text>
        <button class="btn-copy" @click="copyUrl">{{ t('webview.copyBtn') }}</button>
      </view>
    </view>
    <view class="webview-helper app-surface" v-if="url && shouldUseWebview">
      <view class="helper-copy">
        <text class="helper-source">{{ sourceHint }}</text>
        <text class="helper-tip">{{ openHint }}</text>
      </view>
      <button class="helper-copy-btn" @click="copyUrl">复制链接</button>
    </view>
  </SlPage>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue';
import { useI18n } from '@/locales';
import { onLoad } from '@dcloudio/uni-app';
import { getMpSafeAreaMetrics } from '@/utils/safeArea';
import { useTheme } from '@/utils/theme';
import SlPage from '@/style-library/components/SlPage.vue';
import SlNavBar from '@/style-library/components/SlNavBar.vue';

const { t } = useI18n();
const { themeClass, fontClass, refresh: refreshTheme } = useTheme();
const topSafe = ref(44);
const url = ref('');
const title = ref('');
const mode = ref<'webview' | 'copy'>('webview');
const showFallback = ref(false);

const goBack = () => uni.navigateBack();

onMounted(() => {
  refreshTheme();
  topSafe.value = getMpSafeAreaMetrics().topSafeHeight;
});

onLoad((options: any) => {
  if (options.url) {
    url.value = decodeURIComponent(options.url);
  }
  if (options.title) {
    title.value = decodeURIComponent(options.title);
    uni.setNavigationBarTitle({ title: title.value });
  }
  if (options.mode === 'copy') {
    mode.value = 'copy';
  }
});

const handleError = () => {
  showFallback.value = true;
};

const host = computed(() => {
  const match = url.value.match(/^https?:\/\/([^/]+)/i);
  return match?.[1]?.replace(/^www\./i, '') || '外部资源';
});

const sourceHint = computed(() => `来源：${host.value}`);

const shouldUseWebview = computed(() => mode.value !== 'copy' && !showFallback.value);

const fallbackTitle = computed(() =>
  shouldUseWebview.value ? t('webview.fallbackText') : t('webview.externalTitle')
);

const fallbackDesc = computed(() =>
  shouldUseWebview.value ? t('webview.fallbackDesc') : t('webview.externalDesc')
);

const openHint = computed(() => {
  const h = host.value.toLowerCase();
  if (/bilibili|b23\.tv/.test(h)) return 'B 站内容若无法内嵌，请复制后在浏览器或 B 站打开';
  if (/zhihu/.test(h)) return '知乎内容可能需要在浏览器或知乎 App 中查看';
  if (/weixin|qq\.com|mp\.weixin/.test(h)) return '公众号内容受微信域名限制，必要时复制链接打开';
  if (/pdf|report|edu|gov/.test(h)) return '就业报告类资源建议复制链接后在浏览器打开';
  return '如页面空白，请复制链接到浏览器打开';
});

const copyUrl = () => {
  uni.setClipboardData({
    data: url.value,
    success: () => {
      uni.showToast({ title: t('webview.copied'), icon: 'success' });
    }
  });
};
</script>

<style scoped>
.fallback {
  padding: 40px 20px;
  height: 100%;
  box-sizing: border-box;
}

.fallback-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 30px 20px;
  border-radius: var(--radius-lg, 20px);
}

.fb-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.fb-text {
  font-size: 16px;
  color: #1e293b;
  margin-bottom: 8px;
  text-align: center;
  font-weight: 900;
}

.fb-desc {
  font-size: 13px;
  line-height: 1.55;
  color: var(--text-secondary, #64748b);
  margin-bottom: 12px;
  text-align: center;
}

.fb-source {
  font-size: 13px;
  color: var(--primary-color, #2563eb);
  margin-bottom: 18px;
}

.fb-url {
  font-size: 13px;
  color: #64748b;
  margin-bottom: 32px;
  text-align: center;
  word-break: break-all;
  background: #f1f5f9;
  padding: 12px;
  border-radius: 8px;
  width: 100%;
}

.btn-copy {
  background: var(--primary-color, #2563eb);
  color: #ffffff;
  border-radius: var(--btn-radius, 14px);
  font-size: 15px;
  font-weight: 600;
  width: 100%;
  height: 48px;
  line-height: 48px;
  border: none;
}

.webview-helper {
  position: fixed;
  left: 12px;
  right: 12px;
  bottom: calc(env(safe-area-inset-bottom, 0px) + 12px);
  z-index: 99;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 12px;
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.16);
}

.helper-copy {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.helper-source {
  font-size: 12px;
  line-height: 1.25;
  font-weight: 800;
  color: var(--text-primary, #0f172a);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.helper-tip {
  font-size: 11px;
  line-height: 1.35;
  color: var(--text-secondary, #64748b);
}

.helper-copy-btn {
  flex-shrink: 0;
  width: 78px;
  height: 34px;
  line-height: 34px;
  padding: 0;
  border: none;
  border-radius: 10px;
  background: var(--primary-color, #2563eb);
  color: #ffffff;
  font-size: 13px;
  font-weight: 800;
}

.is-dark .fb-text { color: #f8fafc; }
.is-dark .fb-desc { color: #94a3b8; }
.is-dark .fb-url { background: #1e293b; color: #cbd5e1; }
.is-dark .webview-helper { background: #1e293b; border-color: #334155; }
.is-dark .helper-source { color: #f8fafc; }
.is-dark .helper-tip { color: #94a3b8; }

/* Competition visual system ------------------------------------------------ */
.fallback-card,
.webview-helper {
  background: #ffffff;
  border: 1px solid #e0dfdb;
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(44, 43, 41, 0.06);
}

.fb-icon {
  color: #3f51b5;
}

.fb-text,
.helper-source {
  color: #2c2b29;
  font-family: "Songti SC", STSong, serif;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.fb-desc,
.helper-tip {
  color: #5a5956;
}

.fb-source {
  color: #c23b22;
}

.fb-url {
  color: #5a5956;
  background: #f5f5f0;
  border: 1px solid #e0dfdb;
  border-radius: 4px;
}

.btn-copy,
.helper-copy-btn {
  color: #faf9f6;
  background: #c23b22;
  border-radius: 6px;
}

.is-dark .fallback-card,
.is-dark .webview-helper {
  background: #242320;
  border-color: #494844;
}

.is-dark .fb-text,
.is-dark .helper-source {
  color: #faf9f6;
}
</style>
