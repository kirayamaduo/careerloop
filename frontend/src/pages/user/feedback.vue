<template>
  <SlPage class="app-soft-bg" :custom-class="[themeClass, fontClass, 'fb-page'].join(' ')">
    <!-- Nav bar -->
    <SlNavBar :title="t('feedback.title')" show-back @back="goBack" :safe-top="topSafeHeight" />

    <scroll-view class="content" scroll-y>
      <view class="hero-block">
        <view class="hero-icon"><text class="ri-chat-3-line"></text></view>
        <text class="hero-title">{{ t('feedback.heroTitle') }}</text>
        <text class="hero-desc">{{ t('feedback.heroDesc') }}</text>
      </view>

      <!-- Category picker -->
      <text class="section-label">{{ t('feedback.categoryLabel') }}</text>
      <view class="category-row">
        <view
          v-for="cat in CATEGORIES"
          :key="cat.value"
          class="cat-chip ui-list-item"
          :class="{ 'cat-active': form.category === cat.value }"
          @click="form.category = cat.value"
         hover-class="press-fb" hover-stay-time="120">
          <text class="cat-emoji" :class="cat.emoji"></text>
          <text class="cat-label">{{ cat.label }}</text>
        </view>
      </view>

      <!-- Content textarea -->
      <text class="section-label">{{ t('feedback.contentLabel') }} <text class="req">*</text></text>
      <textarea
        class="fb-textarea ui-input"
        v-model="form.content"
        :placeholder="t('feedback.contentPlaceholder')"
        placeholder-class="ph"
        maxlength="2000"
        auto-height
      />
      <text class="char-count">{{ form.content.length }} / 2000</text>

      <!-- Contact (optional) -->
      <text class="section-label">{{ t('feedback.contactLabel') }}</text>
      <input
        class="fb-input ui-input"
        v-model="form.contact"
        :placeholder="t('feedback.contactPlaceholder')"
        placeholder-class="ph"
      />

      <button class="btn-submit" :disabled="submitting || !form.content.trim()" @click="doSubmit">
        <text v-if="submitting">{{ t('feedback.submitting') }}</text>
        <text v-else>{{ t('feedback.submit') }}</text>
      </button>

      <view class="bottom-pad"></view>
    </scroll-view>
  </SlPage>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useI18n } from '@/locales';
import { onShow } from '@dcloudio/uni-app';
import { getMpSafeAreaMetrics } from '@/utils/safeArea';
import { submitFeedbackApi, type FeedbackCategory } from '@/api/feedback';
import { useTheme } from '@/utils/theme';
import { requireAuth } from '@/utils/auth';
import SlPage from '@/style-library/components/SlPage.vue';
import SlNavBar from '@/style-library/components/SlNavBar.vue';

const { t } = useI18n();
const { themeClass, fontClass, refresh: refreshTheme } = useTheme();
const topSafeHeight = ref(44);
const submitting = ref(false);

const CATEGORIES = computed<{ value: FeedbackCategory; emoji: string; label: string }[]>(() => [
  { value: 'FUNCTION_BUG',    emoji: 'ri-bug-line', label: 'Bug' },
  { value: 'SUGGESTION',      emoji: 'ri-lightbulb-line', label: t('feedback.catSuggestion') },
  { value: 'CONTENT_REPORT',  emoji: 'ri-flag-line', label: t('feedback.catReport') },
  { value: 'OTHER',           emoji: 'ri-chat-3-line', label: t('feedback.catOther') },
]);

const form = ref({
  category: 'SUGGESTION' as FeedbackCategory,
  content: '',
  contact: '',
});

const goBack = () => uni.navigateBack();

const doSubmit = async () => {
  if (!requireAuth({ message: '登录后提交反馈，便于我们跟进处理结果。' })) return;
  const text = form.value.content.trim();
  if (!text) {
    uni.showToast({ title: t('feedback.contentRequired'), icon: 'none' });
    return;
  }
  submitting.value = true;
  try {
    await submitFeedbackApi({
      category: form.value.category,
      content: text,
      contact: form.value.contact.trim() || undefined,
    });
    uni.showToast({ title: t('feedback.submitSuccess'), icon: 'success' });
    setTimeout(() => uni.navigateBack(), 1500);
  } catch (e: any) {
    uni.showToast({ title: e?.message || t('common.failed'), icon: 'none' });
  } finally {
    submitting.value = false;
  }
};

onMounted(() => {
  refreshTheme();
  topSafeHeight.value = getMpSafeAreaMetrics().topSafeHeight;
});

onShow(() => {
  refreshTheme();
});
</script>

<style scoped>
:global(.fb-page) {
  display: flex !important;
  flex-direction: column;
}

/* ---- Scroll content ---- */
.content { flex: 1; padding: 0 var(--page-gutter, 20px); box-sizing: border-box; width: 100%; }

/* ---- Hero ---- */
.hero-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 28px 0 22px;
  text-align: center;
}
.hero-icon { font-size: 44px; margin-bottom: 12px; }
.hero-title { font-size: 20px; font-weight: 800; color: var(--text-primary, #1c1917); margin-bottom: 8px; }
.hero-desc { font-size: 13px; color: var(--text-secondary, #78716c); line-height: 1.6; max-width: 280px; }

/* ---- Labels ---- */
.section-label {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary, #78716c);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  margin: 18px 0 8px;
}
.req { color: var(--danger-color, #ef4444); }

/* ---- Category chips ---- */
.category-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.cat-chip {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 8px 14px;
  border-radius: 20px;
  transition: all 0.15s;
}
.cat-chip:active { opacity: 0.75; }
.cat-active {
  background: var(--primary-soft, #fcf5f2);
  border-color: var(--primary-color, #cd6a43);
}
.cat-emoji { font-size: 15px; }
.cat-label { font-size: 13px; font-weight: 600; color: var(--text-secondary, #78716c); }
.cat-active .cat-label { color: var(--primary-color, #cd6a43); }

/* ---- Textarea ---- */
.fb-textarea {
  width: 100%;
  min-height: 120px;
  padding: 14px;
  border-radius: var(--btn-radius, 14px);
  font-size: 15px;
  color: var(--text-primary, #1c1917);
  line-height: 1.55;
  box-sizing: border-box;
}
.char-count {
  display: block;
  text-align: right;
  font-size: 12px;
  color: var(--text-tertiary, #8e8e93);
  margin-top: 4px;
}

/* ---- Input ---- */
.fb-input {
  width: 100%;
  height: 46px;
  padding: 0 14px;
  border-radius: var(--radius-sm, 12px);
  font-size: 15px;
  color: var(--text-primary, #1c1917);
  box-sizing: border-box;
}

.ph { color: var(--text-tertiary, #8e8e93); }

/* ---- Submit button ---- */
.btn-submit {
  width: 100%;
  height: 50px;
  background: var(--primary-color, #cd6a43);
  color: #ffffff;
  border-radius: var(--btn-radius, 14px);
  font-size: 16px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 24px;
  border: none;
}
.btn-submit[disabled] { background: #e8baa8; }

.bottom-pad { height: 40px; }

/* ---- Dark mode ---- */
.is-dark .hero-title { color: #fafaf9; }
.is-dark .fb-textarea,
.is-dark .fb-input { background: #292524; border-color: #44403c; color: #fafaf9; }
.is-dark .cat-chip { background: #292524; border-color: #44403c; }
.is-dark .cat-label { color: #d6d3d1; }

/* Competition visual system ------------------------------------------------ */
.hero-block {
  border-bottom: 1px solid #edece8;
}

.hero-icon {
  color: #c23b22;
}

.hero-title,
.section-label {
  color: #2c2b29;
  font-family: "Songti SC", STSong, serif;
  font-weight: 600;
  letter-spacing: 0.05em;
}

.hero-desc {
  color: #5a5956;
}

.cat-chip {
  background: #faf9f6;
  border: 1px solid #e0dfdb;
  border-radius: 4px;
}

.cat-active {
  background: rgba(172, 100, 72, 0.07);
  border-color: #ac6448;
}

.cat-label {
  color: #5a5956;
}

.cat-active .cat-label {
  color: #ac6448;
}

.fb-textarea,
.fb-input {
  color: #2c2b29;
  background: #ffffff;
  border: 1px solid #d8d7d2;
  border-radius: 6px;
}

.req {
  color: #c23b22;
}

.btn-submit {
  color: #faf9f6;
  background: #c23b22;
  border-radius: 6px;
  font-family: "Songti SC", STSong, serif;
  font-weight: 600;
  letter-spacing: 0.05em;
}

.btn-submit[disabled] {
  color: #8b8a86;
  background: #efeee9;
  border: 1px solid #e0dfdb;
}

.is-dark .hero-title,
.is-dark .section-label {
  color: #faf9f6;
}

.is-dark .hero-desc {
  color: #c6c4be;
}

.is-dark .cat-chip,
.is-dark .fb-textarea,
.is-dark .fb-input {
  color: #faf9f6;
  background: #242320;
  border-color: #494844;
}

.is-dark .cat-chip.cat-active {
  border-color: #e5beaf !important;
  background: rgba(172, 100, 72, 0.3) !important;
}

.is-dark .cat-chip.cat-active .cat-label {
  color: #fafaf9 !important;
}

.is-dark .btn-submit[disabled] {
  border-color: #57534e;
  background: #44403c;
  color: #a8a29e;
}
</style>
