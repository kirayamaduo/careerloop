<template>
  <SlPage class="app-soft-bg" :custom-class="['resume-template-page', themeClass, fontClass].join(' ')">
    <SlNavBar show-back @back="goBack" :safe-top="topSafe" />

    <!-- Header -->
    <view class="page-header">
      <view class="header-text">
        <text class="page-title">{{ t('resumeTemplate.pageTitle') }}</text>
        <text class="page-subtitle">{{ t('resumeTemplate.pageSubtitle') }}</text>
      </view>
    </view>

    <!-- Progress bar -->
    <view class="progress-track">
      <view class="progress-fill" :style="{ width: progressPct + '%' }"></view>
    </view>
    <text class="progress-label">{{ t('resumeTemplate.stepLabel', { step: currentStep, label: stepLabels[currentStep - 1] }) }}</text>

    <!-- ===== Step 1: Basic Info ===== -->
    <view v-show="currentStep === 1" class="step-body">
      <view class="field-group">
        <text class="field-label">{{ t('resumeTemplate.fieldName') }} <text class="req">*</text></text>
        <input class="field-input ui-input" v-model="form.name" placeholder="e.g. Zhang Wei" />
      </view>
      <view class="field-group">
        <text class="field-label">{{ t('resumeTemplate.fieldPhone') }}</text>
        <input class="field-input ui-input" v-model="form.phone" type="number" placeholder="e.g. 138 0000 0000" />
      </view>
      <view class="field-group">
        <text class="field-label">{{ t('resumeTemplate.fieldEmail') }}</text>
        <input class="field-input ui-input" v-model="form.email" placeholder="your@email.com" />
      </view>
      <view class="field-group">
        <text class="field-label">{{ t('resumeTemplate.fieldTargetRole') }} <text class="req">*</text></text>
        <input class="field-input ui-input" v-model="form.targetRole" placeholder="e.g. Frontend Developer" />
      </view>
      <view class="field-group">
        <text class="field-label">{{ t('resumeTemplate.fieldCity') }}</text>
        <input class="field-input ui-input" v-model="form.city" placeholder="e.g. Beijing / Shanghai / Remote" />
      </view>
    </view>

    <!-- ===== Step 2: Education + Skills ===== -->
    <view v-show="currentStep === 2" class="step-body">
      <view class="field-group">
        <text class="field-label">{{ t('resumeTemplate.fieldUniversity') }}</text>
        <input class="field-input ui-input" v-model="form.university" placeholder="e.g. Tsinghua University" />
      </view>
      <view class="field-group">
        <text class="field-label">{{ t('resumeTemplate.fieldMajor') }}</text>
        <input class="field-input ui-input" v-model="form.major" placeholder="e.g. Computer Science" />
      </view>
      <view class="field-group">
        <text class="field-label">{{ t('resumeTemplate.fieldDegree') }}</text>
        <picker mode="selector" :range="degreeOptions" @change="onDegreeChange">
          <view class="field-picker ui-list-item" :class="{ 'picker-filled': form.degree }">
            <text class="picker-text" :class="{ 'picker-text-filled': form.degree }">
              {{ form.degree || t('resumeTemplate.tapToSelect') }}
            </text>
            <text class="picker-arrow">›</text>
          </view>
        </picker>
      </view>
      <view class="field-group">
        <text class="field-label">{{ t('resumeTemplate.fieldGradYear') }}</text>
        <picker mode="date" fields="year" @change="onYearChange">
          <view class="field-picker ui-list-item" :class="{ 'picker-filled': form.graduationYear }">
            <text class="picker-text" :class="{ 'picker-text-filled': form.graduationYear }">
              {{ form.graduationYear || t('resumeTemplate.tapToSelect') }}
            </text>
            <text class="picker-arrow">›</text>
          </view>
        </picker>
      </view>
      <view class="field-group">
        <text class="field-label">{{ t('resumeTemplate.fieldSkills') }}</text>
        <input class="field-input ui-input" v-model="form.skills" placeholder="e.g. Vue3, Spring Boot, Python, SQL" />
      </view>
    </view>

    <!-- ===== Step 3: Experience ===== -->
    <view v-show="currentStep === 3" class="step-body">
      <view class="tip-card app-surface">
        <text class="tip-icon ri-lightbulb-line"></text>
        <text class="tip-text">{{ t('resumeTemplate.experienceTip') }}</text>
      </view>
      <view class="field-group">
        <view class="field-label-row">
          <text class="field-label">{{ t('resumeTemplate.fieldExperience') }}</text>
          <text class="char-count">{{ form.experience.length }} / 800</text>
        </view>
        <textarea
          class="field-textarea ui-input"
          v-model="form.experience"
          placeholder="例如：参与 Vue3 + Spring Boot 电商项目，负责商品列表和结算模块，通过优化接口调用将结算耗时降低 40%。"
          maxlength="800"
          @input="(e: any) => form.experience = e.detail.value"
        />
      </view>
    </view>

    <!-- Bottom navigation -->
    <view class="bottom-bar">
      <view
        v-if="currentStep > 1"
        class="btn-back"
        @click="currentStep--"
      >
        <text class="btn-back-text">{{ t('resumeTemplate.backBtn') }}</text>
      </view>
      <view
        v-if="currentStep < 3"
        class="btn-next"
        :class="{ 'btn-disabled': !stepValid }"
        @click="nextStep"
      >
        <text class="btn-next-text">{{ t('resumeTemplate.nextBtn') }}</text>
      </view>
      <view
        v-if="currentStep === 3"
        class="btn-generate"
        :class="{ 'btn-disabled': submitting }"
        @click="handleGenerate"
      >
        <text class="btn-generate-text">{{ submitting ? t('resumeTemplate.generating') : t('resumeTemplate.generateBtn') }}</text>
      </view>
    </view>
  </SlPage>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import { useI18n } from '@/locales';
import { onShow } from '@dcloudio/uni-app';
import { getMpSafeAreaMetrics } from '@/utils/safeArea';
import { generateResumeFromTemplateApi } from '@/api/resume';
import { useTheme } from '@/utils/theme';
import { requireAuth } from '@/utils/auth';
import SlPage from '@/style-library/components/SlPage.vue';
import SlNavBar from '@/style-library/components/SlNavBar.vue';

const { t } = useI18n();
const { themeClass, fontClass, refresh: refreshTheme } = useTheme();
const topSafe = ref(getMpSafeAreaMetrics().topSafeHeight);
const submitting = ref(false);
const currentStep = ref(1);
const stepLabels = computed(() => [t('resumeTemplate.step1'), t('resumeTemplate.step2'), t('resumeTemplate.step3')]);
const degreeOptions = ['Associate', 'Bachelor', 'Master', 'Doctorate'];

const form = ref({
  name: '', phone: '', email: '', targetRole: '', city: '',
  university: '', major: '', degree: '', graduationYear: '', skills: '', experience: '',
});

const progressPct = computed(() => (currentStep.value / 3) * 100);
refreshTheme();

onShow(() => {
  refreshTheme();
  uni.setNavigationBarTitle({ title: t('resumeTemplate.pageTitle') });
});

const stepValid = computed(() => {
  if (currentStep.value === 1) return !!form.value.name.trim() && !!form.value.targetRole.trim();
  return true;
});

const nextStep = () => {
  if (!requireAuth({ message: '登录后才能创建并保存你的 AI 简历。' })) return;
  if (!stepValid.value) {
    uni.showToast({ title: t('resumeTemplate.requiredError'), icon: 'none' });
    return;
  }
  currentStep.value++;
};

const goBack = () => {
  uni.navigateBack();
};

const onDegreeChange = (e: any) => { form.value.degree = degreeOptions[e.detail.value]; };
const onYearChange = (e: any) => { form.value.graduationYear = e.detail.value; };

const handleGenerate = async () => {
  if (submitting.value) return;
  if (!requireAuth({ message: '登录后才能生成并保存你的 AI 简历。' })) return;
  if (!form.value.name.trim() || !form.value.targetRole.trim()) {
    uni.showToast({ title: t('resumeTemplate.requiredError'), icon: 'none' });
    currentStep.value = 1;
    return;
  }
  const userId = Number(uni.getStorageSync('userId'));
  if (!userId || isNaN(userId) || userId <= 0) {
    uni.showToast({ title: t('resumeTemplate.loginRequired'), icon: 'none' });
    return;
  }
  submitting.value = true;
  uni.showLoading({ title: t('resumeTemplate.aiWriting'), mask: true });
  try {
    await generateResumeFromTemplateApi({ userId, ...form.value });
    uni.hideLoading();
    uni.showToast({ title: t('resumeTemplate.created'), icon: 'success' });
    setTimeout(() => uni.navigateBack(), 1200);
  } catch (e: any) {
    uni.hideLoading();
    uni.showToast({ title: e?.message || t('resumeTemplate.generationFailed'), icon: 'none' });
  } finally {
    submitting.value = false;
  }
};
</script>

<style scoped>

/* ── Header ── */
.page-header {
  padding: 0 20px 16px;
}

.header-text { flex: 1; }
.page-title { display: block; font-size: 20px; font-weight: 800; color: var(--text-primary, #1c1917); letter-spacing: -0.3px; }
.page-subtitle { display: block; font-size: 12px; color: var(--text-secondary, #78716c); margin-top: 2px; }

/* ── Progress ── */
.progress-track {
  height: 4px;
  background: var(--surface-3, #f5f5f4);
  border-radius: 2px;
  margin: 0 20px;
}
.progress-fill {
  height: 4px;
  background: linear-gradient(90deg, #cd6a43, #dd987d);
  border-radius: 2px;
  transition: width 0.35s ease;
}
.progress-label {
  display: block;
  font-size: 11px;
  font-weight: 600;
  color: #cd6a43;
  margin: 6px 20px 16px;
  letter-spacing: 0.03em;
}

/* ── Step body ── */
.step-body {
  padding: 0 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

/* ── Fields ── */
.field-group { display: flex; flex-direction: column; gap: 6px; }

.field-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary, #78716c);
  padding-left: 2px;
}
.req { color: #ef4444; }

.field-label-row {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}
.char-count { font-size: 11px; color: var(--text-tertiary, #8e8e93); }

.field-input {
  height: 48px;
  background: var(--surface-1, #ffffff);
  border: 1.5px solid #e7e5e4;
  border-radius: var(--radius-sm, 12px);
  padding: 0 14px;
  font-size: 15px;
  color: var(--text-primary, #1c1917);
  box-sizing: border-box;
  width: 100%;
}
.field-input:focus { border-color: #cd6a43; }

.field-picker {
  height: 48px;
  background: var(--surface-1, #ffffff);
  border: 1.5px solid #e7e5e4;
  border-radius: var(--radius-sm, 12px);
  padding: 0 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-sizing: border-box;
}
.picker-filled { border-color: #f3dbd2; }
.picker-text { font-size: 15px; color: var(--text-tertiary, #8e8e93); flex: 1; }
.picker-text-filled { color: var(--text-primary, #1c1917); }
.picker-arrow { font-size: 18px; color: #c7c7cc; }

.field-textarea {
  background: var(--surface-1, #ffffff);
  border: 1.5px solid #e7e5e4;
  border-radius: var(--radius-sm, 12px);
  padding: 12px 14px;
  font-size: 15px;
  color: var(--text-primary, #1c1917);
  line-height: 1.55;
  min-height: 160px;
  width: 100%;
  box-sizing: border-box;
}

/* ── Tip card ── */
.tip-card {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  background: var(--primary-soft, #fcf5f2);
  border: 1px solid #f1d6cc;
  border-radius: var(--radius-sm, 12px);
  padding: 12px 14px;
}
.tip-icon { font-size: 18px; flex-shrink: 0; margin-top: 1px; }
.tip-text { font-size: 12.5px; color: #a24d2b; line-height: 1.5; flex: 1; }

/* ── Bottom navigation ── */
.bottom-bar {
  position: fixed;
  left: 0; right: 0; bottom: 0;
  padding: 12px 20px calc(12px + env(safe-area-inset-bottom, 0px));
  background: rgba(248, 243, 241, 0.95);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-top: 0.5px solid rgba(60, 60, 67, 0.12);
  display: flex;
  gap: 10px;
  z-index: 10;
}

.btn-back {
  height: 52px;
  border-radius: var(--btn-radius, 14px);
  border: 1.5px solid #e7e5e4;
  background: var(--surface-1, #ffffff);
  display: flex; align-items: center; justify-content: center;
  padding: 0 20px;
}
.btn-back:active { background: var(--surface-3, #f5f5f4); }
.btn-back-text { font-size: 15px; color: var(--text-secondary, #78716c); font-weight: 600; }

.btn-next {
  flex: 1;
  height: 52px;
  border-radius: var(--btn-radius, 14px);
  background: var(--primary-color, #cd6a43);
  display: flex; align-items: center; justify-content: center;
  box-shadow: var(--shadow-card);
}
.btn-next:active { background: #c25c33; }
.btn-next-text { font-size: 16px; color: #ffffff; font-weight: 700; }

.btn-generate {
  flex: 1;
  height: 52px;
  border-radius: var(--btn-radius, 14px);
  background: linear-gradient(135deg, #cd6a43 0%, #d27855 100%);
  display: flex; align-items: center; justify-content: center;
  box-shadow: var(--shadow-card);
}
.btn-generate:active { opacity: 0.88; }
.btn-generate-text { font-size: 16px; color: #ffffff; font-weight: 700; }

.btn-disabled {
  background: var(--surface-3, #f5f5f4) !important;
  box-shadow: none !important;
}
.btn-disabled .btn-next-text,
.btn-disabled .btn-generate-text { color: #a8a29e !important; }

/* ── Dark mode ── */
.is-dark .page-title { color: #fafaf9; }
.is-dark .progress-track { background: #292524; }
.is-dark .field-label { color: #d6d3d1; }
.is-dark .field-input,
.is-dark .field-picker,
.is-dark .field-textarea {
  background: #292524;
  border-color: var(--text-secondary, #78716c);
  color: #fafaf9;
}
.is-dark .picker-text-filled { color: #fafaf9; }
.is-dark .tip-card { background: rgba(205, 106, 67, 0.12); border-color: #a24d2b; }
.is-dark .tip-text { color: #e8baa8; }
.is-dark .bottom-bar { background: rgba(28, 25, 23, 0.95); border-color: var(--text-secondary, #78716c); }
.is-dark .btn-back { background: #292524; border-color: var(--text-secondary, #78716c); }
.is-dark .btn-back-text { color: var(--text-tertiary, #8e8e93); }

/* Website-aligned warm-paper form */
.page-header {
  padding: 2px 20px 18px;
}

.page-title {
  color: #2c2b29;
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
  font-size: 21px;
  font-weight: 600;
  letter-spacing: 0.06em;
}

.page-subtitle {
  margin-top: 4px;
  color: #5a5956;
  line-height: 1.65;
}

.progress-track {
  height: 3px;
  border-radius: 1px;
  background: #e0dfdb;
}

.progress-fill {
  height: 3px;
  border-radius: 1px;
  background: #c23b22;
}

.progress-label {
  color: #c23b22;
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
  font-weight: 600;
  letter-spacing: 0.05em;
}

.step-body {
  gap: 16px;
}

.field-group {
  gap: 7px;
}

.field-label {
  color: #5a5956;
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.req {
  color: #c23b22;
}

.char-count {
  color: #8b8a86;
}

.field-input,
.field-picker,
.field-textarea {
  border: 1px solid #d4d2cc;
  border-radius: 6px;
  background: #faf9f6;
  color: #2c2b29;
  box-shadow: none;
}

.field-input,
.field-picker {
  min-height: 48px;
}

.field-input:focus,
.picker-filled {
  border-color: #ac6448;
}

.picker-text {
  color: #b0afab;
}

.picker-text-filled {
  color: #2c2b29;
}

.picker-arrow {
  color: #8b8a86;
}

.field-textarea {
  line-height: 1.7;
}

.tip-card {
  padding: 12px 13px;
  border: 1px solid rgba(172, 100, 72, 0.28);
  border-left: 2px solid #ac6448;
  border-radius: 0 6px 6px 0;
  background: #f5f5f0;
}

.tip-icon {
  color: #ac6448;
}

.tip-text {
  color: #5a5956;
  line-height: 1.65;
}

.bottom-bar {
  padding-top: 10px;
  border-top: 1px solid #e0dfdb;
  background: rgba(250, 249, 246, 0.97);
  box-shadow: none;
}

.btn-back,
.btn-next,
.btn-generate {
  height: 50px;
  border-radius: 6px;
  box-shadow: none;
}

.btn-back {
  border: 1px solid #d4d2cc;
  background: #faf9f6;
}

.btn-back:active {
  background: #efeee9;
}

.btn-back-text {
  color: #5a5956;
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.btn-next,
.btn-generate {
  border: 1px solid #c23b22;
  background: #c23b22;
}

.btn-next:active,
.btn-generate:active {
  background: #a9321d;
  opacity: 1;
}

.btn-next-text,
.btn-generate-text {
  color: #faf9f6;
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
  font-weight: 600;
  letter-spacing: 0.06em;
}

.btn-disabled {
  border-color: #e0dfdb !important;
  background: #efeee9 !important;
}

.btn-disabled .btn-next-text,
.btn-disabled .btn-generate-text {
  color: #a4a29c !important;
}

.is-dark .page-title {
  color: #f5f5f0;
}

.is-dark .page-subtitle,
.is-dark .field-label {
  color: #c4c2bc;
}

.is-dark .field-input,
.is-dark .field-picker,
.is-dark .field-textarea,
.is-dark .btn-back {
  border-color: #575650;
  background: #292926;
  color: #f5f5f0;
}

.is-dark .picker-text-filled {
  color: #f5f5f0;
}

.is-dark .tip-card {
  border-color: rgba(219, 170, 151, 0.38);
  border-left-color: #d3a18d;
  background: #292926;
}

.is-dark .tip-text {
  color: #c4c2bc;
}

.is-dark .bottom-bar {
  border-color: #45443f;
  background: rgba(31, 31, 29, 0.97);
}

.is-dark .btn-next,
.is-dark .btn-generate {
  border-color: #c23b22;
  background: #c23b22;
}
</style>
