<template>
  <view v-if="readonlyMode" class="readonly-wrapper">
    <SlPage class="app-soft-bg" :custom-class="[themeClass, fontClass].join(' ')">
      <SlNavBar :title="docType === 'terms' ? t('consent.termsTitle') : t('consent.privacyTitle')" show-back @back="closeDoc" :safe-top="topSafeHeight" />
      <scroll-view scroll-y class="doc-scroll-page">
        <view class="doc-page">
          <view class="page-hero">
            <text class="hero-title">{{ docType === 'terms' ? t('consent.termsTitle') : t('consent.privacyTitle') }}</text>
            <text class="hero-subtitle">{{ t('consent.desc') }}</text>
          </view>
          <view class="doc-card app-card-soft app-surface">
            <view v-for="section in docSections" :key="section.title">
              <text class="doc-section">{{ section.title }}</text>
              <text class="doc-body">{{ section.body }}</text>
            </view>
          </view>
        </view>
      </scroll-view>
    </SlPage>
  </view>

  <view v-else class="consent-page app-gradient-bg" :class="[themeClass, fontClass]">
    <view class="top-safe-spacer" :style="{ height: topSafeHeight + 'px' }"></view>

    <!-- App logo / brand header -->
    <view class="brand-header">
      <view class="brand-logo-wrap">
        <text class="brand-logo-text">CL</text>
      </view>
      <text class="brand-name">CareerLoop</text>
      <text class="brand-slogan">{{ t('consent.slogan') }}</text>
    </view>

    <!-- Main consent card -->
    <view class="consent-card app-surface">
      <text class="consent-title">{{ t('consent.title') }}</text>
      <text class="consent-desc">{{ t('consent.desc') }}</text>

      <!-- Agreements list -->
      <view class="menu-card app-card-soft">
        <view class="menu-item" @click="openDoc('privacy')" hover-class="press-fb" hover-stay-time="120">
          <view class="app-icon-tile app-icon-tile--warning menu-icon-wrap"><text class="menu-icon ri-lock-line"></text></view>
          <view class="menu-text-wrap">
            <text class="menu-text">{{ t('consent.privacyTitle') }}</text>
            <text class="menu-meta">{{ t('consent.privacyMeta') }}</text>
          </view>
          <text class="menu-arrow">›</text>
        </view>
        <view class="menu-item" @click="openDoc('terms')" hover-class="press-fb" hover-stay-time="120">
          <view class="app-icon-tile app-icon-tile--cyan menu-icon-wrap"><text class="menu-icon ri-clipboard-line"></text></view>
          <view class="menu-text-wrap">
            <text class="menu-text">{{ t('consent.termsTitle') }}</text>
            <text class="menu-meta">{{ t('consent.termsMeta') }}</text>
          </view>
          <text class="menu-arrow">›</text>
        </view>
      </view>

      <!-- Age confirmation (14+) -->
      <view class="check-row" @click="ageChecked = !ageChecked" hover-class="press-fb" hover-stay-time="120">
        <view class="checkbox" :class="{ checked: ageChecked }">
          <text v-if="ageChecked" class="check-mark ri-check-line"></text>
        </view>
        <text class="check-label">{{ t('consent.ageLabel') }}</text>
      </view>

      <!-- Agreement checkbox -->
      <view class="check-row" @click="termsChecked = !termsChecked" hover-class="press-fb" hover-stay-time="120">
        <view class="checkbox" :class="{ checked: termsChecked }">
          <text v-if="termsChecked" class="check-mark ri-check-line"></text>
        </view>
        <view class="check-label-wrap">
          <text class="check-label">{{ t('consent.agreeLabel') }} </text>
          <text class="check-link" @click.stop="openDoc('terms')">{{ t('consent.agreeTerms') }}</text>
          <text class="check-label"> {{ t('consent.agreeAnd') }} </text>
          <text class="check-link" @click.stop="openDoc('privacy')">{{ t('consent.agreePrivacy') }}</text>
        </view>
      </view>

      <!-- Action buttons -->
      <view class="btn-agree" :class="{ 'btn-disabled': !canAgree }" @click="onAgree" hover-class="press-fb" hover-stay-time="120">
        <text class="btn-agree-text">{{ t('consent.agreeBtn') }}</text>
      </view>
      <view class="btn-disagree" @click="onDisagree" hover-class="press-fb" hover-stay-time="120">
        <text class="btn-disagree-text">{{ t('consent.disagreeBtn') }}</text>
      </view>

      <text class="consent-footnote">
        {{ t('consent.footnote') }}
      </text>
    </view>

    <!-- Document viewer modal -->
    <view class="doc-modal-mask" v-if="showDoc" @tap="closeDoc">
      <view class="doc-modal" @tap.stop hover-class="press-fb" hover-stay-time="120">
        <view class="doc-modal-header">
          <text class="doc-modal-title">{{ docType === 'terms' ? t('consent.termsTitle') : t('consent.privacyTitle') }}</text>
          <view class="doc-close-btn" @click="closeDoc" hover-class="press-fb" hover-stay-time="120"><text class="doc-close-icon ri-close-line"></text></view>
        </view>
        <scroll-view scroll-y class="doc-scroll">
          <view>
            <view v-for="section in docSections" :key="section.title">
              <text class="doc-section">{{ section.title }}</text>
              <text class="doc-body">{{ section.body }}</text>
            </view>
          </view>
        </scroll-view>
        <view class="doc-modal-footer">
          <view class="doc-btn-close" @click="closeDoc" hover-class="press-fb" hover-stay-time="120"><text class="doc-btn-close-text">{{ t('consent.docCloseBtn') }}</text></view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useI18n } from '@/locales';
import { isGuest, isRealUser, LOGIN_PAGE } from '@/utils/auth';
import { shouldForceOnboarding } from '@/utils/onboardingGate';
import {
  consumeConsentReturnUrl,
  recordCurrentConsentOnServer,
  storeCurrentConsent,
} from '@/utils/consent';
import { useTheme } from '@/utils/theme';
import { getMpSafeAreaMetrics } from '@/utils/safeArea';
import SlPage from '@/style-library/components/SlPage.vue';
import SlNavBar from '@/style-library/components/SlNavBar.vue';

const ageChecked = ref(false);
const termsChecked = ref(false);
const showDoc = ref(false);
const docType = ref<'privacy' | 'terms'>('privacy');
const readonlyMode = ref(false);
const { t } = useI18n();
const { themeClass, fontClass, refresh: refreshTheme } = useTheme();
const topSafeHeight = ref(52);

const canAgree = computed(() => ageChecked.value && termsChecked.value);
const docSections = computed(() => {
  const prefix = docType.value === 'terms' ? 'legal.terms' : 'legal.privacy';
  const count = docType.value === 'terms' ? 9 : 8;
  return Array.from({ length: count }, (_, index) => {
    const n = index + 1;
    return {
      title: t(`${prefix}.s${n}Title`),
      body: t(`${prefix}.s${n}Body`),
    };
  });
});

const openDoc = (type: 'privacy' | 'terms') => {
  docType.value = type;
  showDoc.value = true;
};

onMounted(() => {
  refreshTheme();
  topSafeHeight.value = getMpSafeAreaMetrics().topSafeHeight;
  const pages = getCurrentPages();
  const current = pages[pages.length - 1];
  const viewParam = (current as any)?.options?.view as string | undefined;
  readonlyMode.value = (current as any)?.options?.readonly === '1';
  if (viewParam === 'privacy' || viewParam === 'terms') {
    openDoc(viewParam);
  }
});

const closeDoc = () => {
  showDoc.value = false;
  if (readonlyMode.value) {
    uni.navigateBack();
  }
};

const onAgree = async () => {
  if (!canAgree.value) {
    uni.showToast({ title: t('consent.checkBoth'), icon: 'none' });
    return;
  }
  storeCurrentConsent();

  const resumeAfterConsent = () => {
    const returnUrl = consumeConsentReturnUrl();
    if (returnUrl) {
      uni.reLaunch({ url: returnUrl });
      return;
    }
    uni.switchTab({ url: '/pages/home/index' });
  };

  if (isRealUser()) {
    recordCurrentConsentOnServer().catch(() => {
      // A temporary network failure must not trap the user. Login retries
      // the audit write after the next authenticated session.
    });
    const forceOnboarding = await shouldForceOnboarding();
    if (!isRealUser()) {
      // Keep the saved deep link. A successful re-login will consume it.
      uni.reLaunch({ url: LOGIN_PAGE });
      return;
    }
    if (forceOnboarding) {
      uni.reLaunch({ url: '/pages/onboarding/index' });
      return;
    }
    resumeAfterConsent();
  } else if (isGuest()) {
    resumeAfterConsent();
  } else {
    uni.reLaunch({ url: LOGIN_PAGE });
  }
};

const onDisagree = () => {
  uni.showModal({
    title: t('consent.exitTitle'),
    content: t('consent.exitContent'),
    confirmText: t('consent.exitConfirm'),
    cancelText: t('consent.exitCancel'),
    success: (res) => {
      if (res.confirm) {
        // #ifdef MP-WEIXIN
        uni.exitMiniProgram({});
        // #endif
        // #ifdef H5
        window.history.back();
        // #endif
      }
    }
  });
};
</script>

<style scoped>
.consent-page {
  min-height: 100vh;
  background: var(--gradient-primary);
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 0 16px 40px;
  box-sizing: border-box;
}

.top-safe-spacer {
  width: 100%;
  flex-shrink: 0;
}

.brand-header {
  padding-top: 24px;
  padding-bottom: 32px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.brand-logo-wrap {
  width: 64px;
  height: 64px;
  background: rgba(255, 255, 255, 0.15);
  border: 2px solid rgba(255, 255, 255, 0.35);
  border-radius: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 4px;
}

.brand-logo-text {
  font-size: 24px;
  font-weight: 900;
  color: #ffffff;
  letter-spacing: -1px;
}

.brand-name {
  font-size: 22px;
  font-weight: 800;
  color: #ffffff;
  letter-spacing: 0.3px;
}

.brand-slogan {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.72);
}

.consent-card {
  width: 100%;
  max-width: 480px;
  border-radius: var(--radius-xl, 24px);
  padding: 28px var(--page-gutter, 20px) var(--space-2xl, 24px);
  box-sizing: border-box;
  box-shadow: var(--shadow-card);
}

.consent-title {
  display: block;
  font-size: 20px;
  font-weight: 800;
  color: var(--text-primary, #1c1917);
  margin-bottom: 10px;
}

.consent-desc {
  display: block;
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-secondary, #78716c);
  margin-bottom: 20px;
}

/* Agreement list */
.menu-card {
  border-radius: var(--radius-md, 16px);
  overflow: hidden; margin-bottom: 20px;
}

.menu-item {
  display: flex; align-items: center; padding: 12px 12px;
  position: relative;
}

.menu-item:not(:last-child)::after {
  content: ''; position: absolute;
  bottom: 0; left: 52px; right: 0;
  height: 1px; background: var(--border-color, #e7e5e4);
}

.menu-icon-wrap {
  margin-right: 12px;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}

.menu-icon { font-size: 17px; margin: 0; display: block; }

.menu-text-wrap { flex: 1; display: flex; flex-direction: column; min-width: 0; }

.menu-text {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-primary, #1c1917);
}

.menu-meta {
  font-size: 12px;
  color: var(--text-secondary, #78716c);
  margin-top: 2px;
}

.menu-arrow { font-size: 20px; color: var(--text-tertiary, #8e8e93); font-weight: 300; flex-shrink: 0; }

/* Checkboxes */
.check-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 14px;
}

.checkbox {
  width: 22px;
  height: 22px;
  min-width: 22px;
  border-radius: 7px;
  border: 2px solid #d6d3d1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #ffffff;
  margin-top: 1px;
  transition: all 0.2s;
}

.checkbox.checked {
  background: var(--primary-color, #cd6a43);
  border-color: var(--primary-color, #cd6a43);
}

.check-mark {
  font-size: 13px;
  color: #ffffff;
  font-weight: 800;
}

.check-label {
  font-size: 13px;
  color: var(--text-secondary, #78716c);
  line-height: 1.55;
  flex: 1;
}

.check-label-wrap {
  flex: 1;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  row-gap: 1px;
  column-gap: 2px;
}

.check-highlight {
  color: var(--primary-hover, #c25c33);
  font-weight: 700;
}

.check-link {
  color: var(--primary-color, #cd6a43);
  font-weight: 600;
  font-size: 13px;
}

/* Buttons */
.btn-agree {
  width: 100%;
  height: 52px;
  background: var(--gradient-primary);
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 8px;
  margin-bottom: 10px;
  box-shadow: var(--shadow-sm);
  transition: opacity 0.2s;
}

.btn-agree.btn-disabled {
  background: #a8a29e;
  box-shadow: none;
  pointer-events: none;
}

.btn-agree-text {
  color: #ffffff;
  font-size: 16px;
  font-weight: 700;
}

.btn-disagree {
  width: 100%;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.btn-disagree-text {
  font-size: 13px;
  color: var(--text-tertiary, #8e8e93);
}

.consent-footnote {
  display: block;
  font-size: 11px;
  color: var(--text-tertiary, #8e8e93);
  line-height: 1.5;
  text-align: center;
  margin-top: 14px;
}

/* Document modal */
.doc-modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(28, 25, 23, 0.6);
  display: flex;
  align-items: flex-end;
  z-index: 9999;
}

.doc-modal {
  width: 100%;
  height: 80vh;
  background: var(--surface-1, #ffffff);
  border-radius: 28px 28px 0 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.doc-modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 20px 16px;
  border-bottom: 1px solid var(--surface-3, #f5f5f4);
}

.doc-modal-title {
  font-size: 17px;
  font-weight: 700;
  color: var(--text-primary, #1c1917);
}

.doc-close-btn {
  width: 32px;
  height: 32px;
  background: var(--surface-3, #f5f5f4);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.doc-close-icon {
  font-size: 14px;
  color: var(--text-secondary, #78716c);
  font-weight: 700;
}

.doc-scroll {
  flex: 1;
  padding: 0 20px;
  overflow-y: auto;
}

.doc-section {
  display: block;
  font-size: 15px;
  font-weight: 800;
  color: var(--text-primary, #1c1917);
  margin-top: 24px;
  margin-bottom: 8px;
}

.doc-card > view:first-child > .doc-section {
  margin-top: 0;
}

.doc-body {
  display: block;
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-secondary, #78716c);
}

.is-dark .menu-card { background: #292524; box-shadow: none; border-color: #44403c; }
.is-dark .menu-text { color: #fafaf9; }
.is-dark .menu-meta { color: var(--text-tertiary, #8e8e93); }
.is-dark .menu-item:not(:last-child)::after { background: #44403c; }

.doc-modal-footer {
  padding: 16px 20px 24px;
  border-top: 1px solid var(--surface-3, #f5f5f4);
}

.doc-btn-close {
  width: 100%;
  height: 50px;
  background: var(--primary-color, #cd6a43);
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.doc-btn-close-text {
  color: #ffffff;
  font-size: 15px;
  font-weight: 700;
}
.readonly-wrapper {
  height: 100vh;
  overflow: hidden;
}
:global(.readonly-wrapper .sl-page) {
  display: flex !important;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
}
.doc-scroll-page {
  flex: 1;
  width: 100%;
  height: 0;
}
.doc-page {
  padding: 0 var(--page-gutter, 20px) 40px;
  box-sizing: border-box;
}

.page-hero { margin-bottom: 20px; padding: 0 2px; }

.hero-title {
  font-size: var(--font-hero, 28px); font-weight: 800;
  color: var(--text-primary, #1c1917); letter-spacing: -0.5px;
  display: block; margin-bottom: 6px;
}

.hero-subtitle {
  display: block; font-size: 14px; line-height: var(--line-height-body, 1.5);
  color: var(--text-secondary, #78716c);
}

.doc-card {
  padding: 24px 20px;
}

.is-dark .hero-title { color: #fafaf9; }
.is-dark .doc-card { background: #292524; box-shadow: none; border: 1px solid #44403c; }
.is-dark .doc-section { color: #fafaf9; }

/* Competition visual system ------------------------------------------------ */
.consent-page {
  background: #faf9f6;
}

.brand-header {
  padding-top: 20px;
  padding-bottom: 24px;
}

.brand-logo-wrap {
  width: 58px;
  height: 58px;
  color: #c23b22;
  background: #f5f5f0;
  border: 1px solid #e0dfdb;
  border-radius: 6px;
}

.brand-logo-text {
  color: #c23b22;
  font-family: "Songti SC", STSong, serif;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.brand-name,
.consent-title,
.hero-title,
.doc-modal-title,
.doc-section {
  color: #2c2b29;
  font-family: "Songti SC", STSong, serif;
  font-weight: 600;
  letter-spacing: 0.06em;
}

.brand-slogan,
.consent-desc,
.menu-meta,
.check-label,
.doc-body,
.hero-subtitle {
  color: #5a5956;
}

.consent-card,
.menu-card,
.doc-card {
  background: #ffffff;
  border: 1px solid #e0dfdb;
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(44, 43, 41, 0.05);
}

.menu-icon-wrap {
  color: #ac6448;
  background: #f5f5f0;
  border: 1px solid #e0dfdb;
  border-radius: 4px;
}

.menu-text {
  color: #2c2b29;
  font-weight: 600;
}

.menu-item:not(:last-child)::after {
  background: #edece8;
}

.checkbox {
  background: #ffffff;
  border: 1px solid #b9b7b1;
  border-radius: 3px;
}

.checkbox.checked {
  background: #ac6448;
  border-color: #ac6448;
}

.check-link {
  color: #ac6448;
}

.btn-agree,
.doc-btn-close {
  background: #c23b22;
  border-radius: 6px;
  box-shadow: none;
}

.btn-agree.btn-disabled {
  color: #8b8a86;
  background: #efeee9;
  border: 1px solid #e0dfdb;
}

.btn-agree.btn-disabled .btn-agree-text {
  color: #8b8a86;
}

.doc-modal-mask {
  background: rgba(44, 43, 41, 0.42);
}

.doc-modal {
  background: #faf9f6;
  border-radius: 8px 8px 0 0;
}

.doc-modal-header,
.doc-modal-footer {
  border-color: #e0dfdb;
}

.doc-close-btn {
  background: #efeee9;
  border-radius: 4px;
}

.doc-card {
  box-shadow: none;
}

.consent-page.is-dark {
  background: #1c1b19;
}

.consent-page.is-dark .brand-name,
.consent-page.is-dark .consent-title,
.is-dark .hero-title,
.is-dark .doc-modal-title,
.is-dark .doc-section {
  color: #faf9f6;
}

.consent-page.is-dark .brand-slogan,
.consent-page.is-dark .consent-desc,
.consent-page.is-dark .menu-meta,
.consent-page.is-dark .check-label,
.is-dark .doc-body,
.is-dark .hero-subtitle {
  color: #c6c4be;
}

.consent-page.is-dark .brand-logo-wrap,
.consent-page.is-dark .consent-card,
.consent-page.is-dark .menu-card,
.is-dark .doc-card,
.is-dark .doc-modal {
  background: #242320;
  border-color: #494844;
}

.consent-page.is-dark .menu-text {
  color: #faf9f6;
}

.consent-page.is-dark .checkbox {
  background: #1c1b19;
  border-color: #75736d;
}

.consent-page.is-dark .doc-close-btn {
  background: #33332f;
}

.consent-page.is-dark .doc-close-icon {
  color: #faf9f6;
}

.consent-page.is-dark .btn-agree.btn-disabled .btn-agree-text {
  color: #a8a29e;
}
</style>
