<template>
  <view class="assessment-container app-soft-bg" :class="[themeClass, fontClass]">
    <SlNavBar :title="t('assessment.pageTitle')" show-back @back="goBack" :safe-top="topSafeHeight" :right-avoid-width="rightAvoidWidth" />

    <view class="assessment-content">
      <view class="page-summary">
        <text class="summary-title">{{ t('assessment.summaryTitle') }}</text>
        <text class="summary-text">{{ t('assessment.summaryText') }}</text>
      </view>

      <view class="flow-bar">
        <view class="flow-pill app-surface">
          <text class="flow-step">{{ t('assessment.step1') }}</text>
          <text class="flow-desc">{{ t('assessment.step1Desc') }}</text>
        </view>
      </view>

      <view class="status-card app-card-gradient">
        <view class="card-header">
          <text class="card-title">{{ t('assessment.aptitudeTitle') }}</text>
          <text class="card-subtitle">{{ t('assessment.aptitudeSubtitle') }}</text>
        </view>
        <view class="card-body">
          <view class="progress-info">
            <text class="progress-text">{{ t('assessment.completedCount', { n: completedCount }) }}</text>
            <text class="progress-label">{{ t('assessment.availableCount', { n: totalCount }) }}</text>
          </view>
          <view class="radar-placeholder">
            <text class="radar-icon ri-radar-line"></text>
          </view>
        </view>
      </view>

      <view class="section-title">{{ t('assessment.featured') }}</view>

      <view class="skeleton-list" v-if="loading">
        <view class="skel-card app-card-soft" v-for="i in 2" :key="i">
          <view class="skel-square"></view>
          <view class="skel-lines">
            <view class="skel-line skel-w70"></view>
            <view class="skel-line skel-w40"></view>
          </view>
        </view>
      </view>

      <view class="assessment-list" v-else-if="scales.length > 0">
        <view
          class="assessment-card app-card-soft"
          v-for="s in scales"
          :key="s.scaleId"
          @click="startQuiz(s)"
        >
          <view class="card-left">
            <view class="app-icon-tile icon-box" :class="scaleIconTone(s.title)">
              <text class="icon-glyph" :class="scaleIconClass(s.title)"></text>
            </view>
            <view class="card-info">
              <text class="a-title">{{ s.title }}</text>
              <text class="a-desc">{{ s.description }}</text>
              <view class="tags">
                <text class="tag tag-time">{{ t('assessment.minEst', { n: estimateMinutes(s.questionCount) }) }}</text>
                <text class="tag tag-blue">{{ t('assessment.questionCount', { n: s.questionCount }) }}</text>
                <text class="tag tag-done" v-if="completedScales.has(s.scaleId)">{{ t('assessment.doneBadge') }}</text>
              </view>
            </view>
          </view>
          <view class="card-right">
            <view class="btn-start">{{ completedScales.has(s.scaleId) ? t('assessment.retakeBtn') : t('assessment.startBtn') }}</view>
          </view>
        </view>
      </view>

      <view class="empty-state app-empty app-surface" v-else>
        <view class="empty-icon-shell">
          <text class="empty-icon-mark ri-folder-open-line"></text>
        </view>
        <text class="empty-text">{{ loadError ? t('assessment.loadFail') : t('assessment.noAssessments') }}</text>
        <text class="empty-desc">{{ loadError || t('assessment.noAssessmentsDesc') }}</text>
        <button class="btn-retry" v-if="loadError" @click="loadAll">{{ t('common.retry') }}</button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useI18n } from '@/locales';
import { onShow } from '@dcloudio/uni-app';
import { getMpSafeAreaMetrics } from '@/utils/safeArea';
import { useTheme } from '@/utils/theme';
import { isRealUser, requireAuth } from '@/utils/auth';
import SlNavBar from '@/style-library/components/SlNavBar.vue';
import {
  getAssessmentScalesApi,
  getMyAssessmentRecordsApi,
  type AssessmentScale,
} from '@/api/assessment';

const { t } = useI18n();
const { themeClass, fontClass, refresh: refreshTheme } = useTheme();
const topSafeHeight = ref(52);
const rightAvoidWidth = ref(20);
const loading = ref(true);
const scales = ref<AssessmentScale[]>([]);
const completedScales = ref<Set<number>>(new Set());
const loadError = ref('');

const totalCount = computed(() => scales.value.length);
const completedCount = computed(() => completedScales.value.size);

const scaleIconTone = (title: string) => {
  const lowerTitle = title.toLowerCase();
  if (lowerTitle.includes('mbti')) return 'app-icon-tile--cyan';
  if (lowerTitle.includes('holland')) return 'app-icon-tile--violet';
  return 'app-icon-tile--candy';
};

const scaleIconClass = (title: string) => {
  const lowerTitle = title.toLowerCase();
  if (lowerTitle.includes('mbti')) return 'ri-brain-line';
  if (lowerTitle.includes('holland')) return 'ri-compass-3-line';
  return 'ri-file-list-3-line';
};

const estimateMinutes = (questionCount?: number) => {
  // ~12 sec / question, rounded to nearest minute, floor at 1.
  const n = questionCount ?? 0;
  return Math.max(1, Math.round((n * 12) / 60));
};

const startQuiz = (s: AssessmentScale) => {
  if (!requireAuth({ message: '登录后开始测评，答题进度和结果会保存到你的成长档案。' })) return;
  uni.navigateTo({
    url: `/pages/assessment/quiz?scaleId=${s.scaleId}&title=${encodeURIComponent(s.title)}`,
  });
};

const goBack = () => {
  const pages = getCurrentPages();
  if (pages.length <= 1) {
    // 从注册/onboarding 用 reLaunch 进入时页面栈只有当前页，无法 navigateBack，
    // 直接跳到首页 tab 让用户可以离开。
    uni.switchTab({ url: '/pages/home/index' });
    return;
  }
  uni.navigateBack({ delta: 1 });
};

const loadAll = async () => {
  loading.value = true;
  loadError.value = '';
  try {
    const [scaleList, records] = await Promise.all([
      getAssessmentScalesApi(),
      // Public preview can browse the available scales, but never calls the
      // account-only records endpoint without a real session.
      isRealUser() ? getMyAssessmentRecordsApi().catch(() => []) : Promise.resolve([]),
    ]);
    scales.value = Array.isArray(scaleList) ? scaleList : [];
    const safeRecords = Array.isArray(records) ? records : [];
    completedScales.value = new Set(safeRecords.map((r) => r.scaleId));
  } catch (e: any) {
    loadError.value = e?.message || t('assessment.loadFail');
    uni.showToast({ title: loadError.value, icon: 'none' });
    scales.value = [];
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  refreshTheme();
  const safeMetrics = getMpSafeAreaMetrics();
  topSafeHeight.value = safeMetrics.topSafeHeight;
  rightAvoidWidth.value = safeMetrics.rightAvoidWidth;
});

// Re-fetch on focus so a freshly completed quiz shows its "Done" pill.
onShow(() => {
  refreshTheme();
  loadAll();
});
</script>

<style scoped>
.status-bar-spacer {
  width: 100%;
  flex-shrink: 0;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
  min-height: 44px;
  padding: 0 2px;
  box-sizing: border-box;
}

.page-summary {
  margin-bottom: 16px;
}

.summary-title {
  display: block;
  font-size: 28px;
  line-height: 1.12;
  font-weight: 800;
  color: var(--text-primary, #1c1917);
}

.summary-text {
  display: block;
  margin-top: 8px;
  font-size: 14px;
  line-height: 1.5;
  color: var(--text-secondary, #78716c);
}

.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  color: var(--text-primary, #1c1917);
  width: var(--nav-back-width, 64px);
}

.back-icon {
  font-size: 26px;
  font-weight: 500;
  line-height: 1;
}

.back-text {
  display: none;
}

.page-title {
  font-size: var(--font-section, 17px);
  font-weight: 700;
  color: var(--text-primary, #1c1917);
  letter-spacing: -0.3px;
  flex: 1;
  text-align: center;
}

.header-action { width: 64px; }

.flow-bar {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 16px;
}

.flow-pill {
  flex: 1;
  border-radius: var(--radius-md, 16px);
  padding: 12px 14px;
}

.flow-step {
  display: block;
  font-size: 11px;
  font-weight: 700;
  color: var(--primary-color, #cd6a43);
  letter-spacing: 0.06em;
  margin-bottom: 4px;
}

.flow-desc { font-size: 13px; color: var(--text-secondary, #78716c); line-height: 1.45; }

.assessment-container {
  min-height: 100vh;
  font-family: -apple-system, BlinkMacSystemFont, "SF Pro Text", "Helvetica Neue", sans-serif;
  box-sizing: border-box;
}

.assessment-content {
  padding: 24px var(--page-gutter, 20px) 60px;
  box-sizing: border-box;
}

.status-card {
  border-radius: var(--radius-xl, 24px);
  padding: 28px 24px;
  color: white;
  margin-bottom: 32px;
  position: relative;
  overflow: hidden;
}

.card-header { margin-bottom: 24px; }

.card-title {
  font-size: 24px;
  font-weight: 700;
  letter-spacing: -0.5px;
  display: block;
  margin-bottom: 6px;
}

.card-subtitle { font-size: 14px; font-weight: 400; opacity: 0.85; }

.card-body { display: flex; justify-content: space-between; align-items: center; }

.progress-info { display: flex; flex-direction: column; }

.progress-text { font-size: 20px; font-weight: 600; letter-spacing: -0.5px; margin-bottom: 4px; }

.progress-label { font-size: 13px; opacity: 0.8; }

.radar-placeholder {
  width: 64px;
  height: 64px;
  border-radius: 32px;
  background: rgba(255, 255, 255, 0.15);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  display: flex;
  justify-content: center;
  align-items: center;
  border: 1px solid rgba(255, 255, 255, 0.3);
}

.radar-icon {
  width: 34px;
  height: 34px;
  border-radius: 17px;
  background: rgba(255, 255, 255, 0.9);
  color: var(--primary-color, #cd6a43);
  font-size: 18px;
  font-weight: 800;
  line-height: 34px;
  text-align: center;
}

.section-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary, #1c1917);
  letter-spacing: -0.5px;
  margin-top: 12px;
  margin-bottom: 16px;
  padding-left: 4px;
}

.assessment-list { display: flex; flex-direction: column; gap: 16px; }

.assessment-card {
  border-radius: var(--radius-lg, 20px);
  padding: 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  transition: transform 0.2s ease;
}

.assessment-card:active { transform: scale(0.98); }

.card-left { display: flex; align-items: center; flex: 1; }

.icon-box {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-xl, 24px);
  display: flex;
  justify-content: center;
  align-items: center;
  font-size: 24px;
  margin-right: 16px;
  flex-shrink: 0;
}

.icon-glyph {
  font-size: 17px;
  font-weight: 800;
  letter-spacing: -0.2px;
}

.mbti-icon { background-color: #faf1ed; }
.holland-icon { background-color: #f8ece7; }

.card-info { display: flex; flex-direction: column; }

.a-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary, #1c1917);
  margin-bottom: 6px;
  letter-spacing: -0.3px;
}

.a-desc { font-size: 13px; color: var(--text-tertiary, #8e8e93); margin-bottom: 8px; }

.tags { display: flex; gap: 8px; }

.tag {
  font-size: 11px;
  font-weight: 500;
  color: var(--text-secondary, #78716c);
  background-color: var(--surface-3, #f5f5f4);
  padding: 4px 8px;
  border-radius: 8px;
}

.tag-time { color: var(--text-secondary, #78716c); }

.tag-blue { color: var(--primary-color, #cd6a43); background-color: var(--primary-soft, #fcf5f2); }

.card-right { margin-left: 12px; }

.btn-start {
  background-color: var(--primary-soft, #fcf5f2);
  color: var(--primary-color, #cd6a43);
  font-size: 13px;
  font-weight: 700;
  border-radius: 999px;
  padding: 0 16px;
  height: 32px;
  line-height: 32px;
  border: 1px solid #f7e8e2;
  display: flex; align-items: center; justify-content: center;
  letter-spacing: 0.02em;
}

.assessment-card:active .btn-start { background-color: #f7e8e2; }

.tag-done { color: var(--success-color, #735a28); background: var(--success-soft, #f5efe3); }

/* Skeleton placeholders shown during initial load. */
.skeleton-list { display: flex; flex-direction: column; gap: 16px; }
.skel-card {
  border-radius: var(--radius-lg, 20px);
  padding: 20px;
  display: flex; align-items: center; gap: 16px;
}
.skel-square {
  width: 48px; height: 48px; border-radius: var(--radius-xl, 24px);
  background: linear-gradient(90deg, #f6f1ef 0%, #fcf9f7 50%, #f6f1ef 100%);
  background-size: 200% 100%;
  animation: skel-shimmer 1.4s infinite;
  flex-shrink: 0;
}
.skel-lines { flex: 1; display: flex; flex-direction: column; gap: 8px; }
.skel-line {
  height: 12px; border-radius: 6px;
  background: linear-gradient(90deg, #f6f1ef 0%, #fcf9f7 50%, #f6f1ef 100%);
  background-size: 200% 100%;
  animation: skel-shimmer 1.4s infinite;
}
.skel-w40 { width: 40%; }
.skel-w70 { width: 70%; }
@keyframes skel-shimmer {
  0%   { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.empty-state {
  text-align: center;
  padding: 60px 20px;
  border-radius: var(--radius-lg, 20px);
}
.empty-icon-shell {
  width: 56px;
  height: 56px;
  border-radius: 20px;
  background: var(--candy-soft, #fdf2f8);
  color: var(--candy, #ec4899);
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 14px;
}
.empty-icon-mark {
  font-size: 20px;
  font-weight: 800;
}
.empty-text { font-size: 16px; font-weight: 700; color: var(--text-secondary, #78716c); display: block; margin-bottom: 8px; }
.empty-desc { font-size: 13px; color: var(--text-tertiary, #8e8e93); line-height: 1.5; }
.btn-retry { margin-top: 18px; background: var(--primary-color, #cd6a43); color: #fff; font-size: 14px; font-weight: 600; border-radius: var(--radius-sm, 12px); height: 40px; line-height: 40px; border: none; width: 120px; }

.is-dark { background-color: var(--text-primary, #1c1917); }

.is-dark .page-title,
.is-dark .summary-title,
.is-dark .section-title,
.is-dark .a-title { color: #fafaf9; }

.is-dark .flow-pill,
.is-dark .assessment-card { background-color: #292524; }

.is-dark .a-desc,
.is-dark .summary-text,
.is-dark .flow-desc,
.is-dark .tag { color: var(--text-tertiary, #8e8e93); }

/* ================================================================
 *  MP-WEIXIN parity overrides — HARDCODED values, no CSS vars.
 * ================================================================ */
/* #ifdef MP-WEIXIN */

.status-card {
  overflow: hidden;
  box-shadow: none;
  filter: none;
}

.assessment-card {
  overflow: visible;
  border: 1.5px solid #d3b8ad;
  box-shadow: var(--shadow-card);
}

.radar-placeholder {
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
  background: rgba(255,255,255,0.30);
}

/* MP + dark: overrides above are light-first */
.assessment-container.is-dark .assessment-card {
  background-color: #292524;
  border-color: #44403c;
}

.assessment-container.is-dark .skel-card {
  background: #292524;
  border-color: #44403c;
}

.assessment-container.is-dark .skel-square,
.assessment-container.is-dark .skel-line {
  background: linear-gradient(90deg, #292524 0%, #44403c 50%, #292524 100%);
  background-size: 200% 100%;
}

.assessment-container.is-dark .empty-state {
  background: #292524;
  border-color: #44403c;
}

.assessment-container.is-dark .btn-start {
  background-color: rgba(205, 106, 67, 0.2);
  border-color: rgba(205, 106, 67, 0.35);
  color: #e8baa8;
}

.assessment-container.is-dark .tag {
  background-color: rgba(68, 64, 60, 0.8);
  color: #d6d3d1;
}

.assessment-container.is-dark .tag-blue {
  background-color: rgba(205, 106, 67, 0.2);
  color: #e8baa8;
}

.assessment-container.is-dark .radar-placeholder {
  background: rgba(205, 106, 67, 0.16);
  border-color: rgba(232, 186, 168, 0.36);
}

.assessment-container.is-dark .radar-icon {
  background: rgba(205, 106, 67, 0.28);
  color: #f1d6cc;
}

.assessment-container.is-dark .mbti-icon {
  background-color: rgba(218, 144, 114, 0.2);
  color: #efcfc3;
}

.assessment-container.is-dark .holland-icon {
  background-color: rgba(219, 149, 121, 0.2);
  color: #f3dbd2;
}

.assessment-container.is-dark .icon-box.app-icon-tile--candy {
  background-color: rgba(236, 72, 153, 0.18);
  color: #f9a8d4;
}

/* #endif */

/* ── CareerLoop editorial skin ─────────────────────────────────────────── */
.assessment-container {
  background: #faf9f6;
  color: #2c2b29;
  font-family: "Noto Sans SC", "PingFang SC", "Microsoft YaHei", sans-serif;
}

.assessment-content {
  padding: 22px 20px 64px;
}

.page-summary {
  margin-bottom: 18px;
}

.summary-title,
.card-title,
.section-title,
.a-title {
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
  font-weight: 600;
  letter-spacing: 0.05em;
}

.summary-title {
  color: #2c2b29;
  font-size: 27px;
  line-height: 1.35;
}

.summary-text,
.flow-desc,
.a-desc {
  color: #5a5956;
  line-height: 1.7;
}

.flow-pill {
  padding: 12px 14px;
  border: 1px solid #e0dfdb;
  border-radius: 6px;
  background: #f5f5f0;
  box-shadow: none;
}

.flow-step {
  color: #c23b22;
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
  font-weight: 600;
  letter-spacing: 0.08em;
}

.status-card {
  padding: 24px 22px;
  margin-bottom: 30px;
  border: 1px solid #e0dfdb;
  border-top: 1px solid var(--border-color, #e0dfdb);
  border-radius: 8px;
  background: #f5f5f0;
  color: #2c2b29;
  box-shadow: 0 8px 24px rgba(44, 43, 41, 0.06);
}

.card-header {
  margin-bottom: 20px;
}

.card-title {
  font-size: 21px;
  letter-spacing: 0.05em;
}

.card-subtitle,
.progress-label {
  color: #8b8a86;
  opacity: 1;
}

.progress-text {
  color: #ac6448;
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
  font-weight: 600;
  letter-spacing: 0.03em;
}

.radar-placeholder {
  width: 58px;
  height: 58px;
  border: 1px solid #d8d6cf;
  border-radius: 6px;
  background: #faf9f6;
}

.radar-icon {
  width: 36px;
  height: 36px;
  border-radius: 4px;
  background: #efeee9;
  color: #ac6448;
  line-height: 36px;
}

.section-title {
  margin: 10px 0 14px;
  padding: 0 0 10px;
  border-bottom: 1px solid #edece8;
  color: #2c2b29;
  font-size: 18px;
}

.assessment-list,
.skeleton-list {
  gap: 12px;
}

.assessment-card,
.skel-card,
.empty-state {
  border: 1px solid #e0dfdb;
  border-radius: 8px;
  background: #fffdfa;
  box-shadow: 0 8px 24px rgba(44, 43, 41, 0.05);
}

.assessment-card {
  padding: 18px;
}

.icon-box {
  width: 46px;
  height: 46px;
  margin-right: 14px;
  border: 1px solid #e0dfdb;
  border-radius: 6px;
  background: #f5f5f0;
  color: #ac6448;
}

.icon-box.app-icon-tile--cyan {
  background: #f1efeb;
  color: #8d846e;
}

.icon-box.app-icon-tile--violet {
  background: #f7f1ef;
  color: #ac6448;
}

.icon-box.app-icon-tile--candy {
  background: #f8ece8;
  color: #c23b22;
}

.a-title {
  color: #2c2b29;
  font-size: 16px;
}

.tags {
  flex-wrap: wrap;
  gap: 6px;
}

.tag {
  padding: 3px 8px;
  border: 1px solid #e0dfdb;
  border-radius: 4px;
  background: #faf9f6;
  color: #5a5956;
}

.tag-blue {
  border-color: rgba(172, 100, 72, 0.28);
  background: #f7f1ef;
  color: #ac6448;
}

.tag-done {
  border-color: rgba(141, 132, 110, 0.38);
  background: #f1efeb;
  color: #7d7561;
}

.btn-start {
  height: 34px;
  padding: 0 14px;
  border: 1px solid #c23b22;
  border-radius: 6px;
  background: #c23b22;
  color: #fffdfa;
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
  font-weight: 600;
  letter-spacing: 0.05em;
}

.assessment-card:active .btn-start {
  background: #a9321d;
}

.skel-square,
.skel-line {
  border-radius: 4px;
  background: linear-gradient(90deg, #efeee9 0%, #faf9f6 50%, #efeee9 100%);
  background-size: 200% 100%;
}

.empty-icon-shell {
  border: 1px solid #e0dfdb;
  border-radius: 6px;
  background: #f5f5f0;
  color: #b8975a;
}

.empty-text {
  color: #2c2b29;
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.empty-desc {
  color: #8b8a86;
}

.btn-retry {
  border-radius: 6px;
  background: #c23b22;
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
}

.assessment-container.is-dark .status-card {
  border-color: #57534e;
  background: #292524;
  box-shadow: none;
}

.assessment-container.is-dark .empty-icon-shell {
  border-color: #57534e;
  background: #1c1917;
  color: #dfc58f;
}

.assessment-container.is-dark .btn-start {
  border-color: #c23b22;
  background: #c23b22;
  color: #fffdfa;
}

.assessment-container.is-dark .tag-blue,
.assessment-container.is-dark .radar-placeholder,
.assessment-container.is-dark .radar-icon,
.assessment-container.is-dark .mbti-icon,
.assessment-container.is-dark .holland-icon {
  border-color: rgba(229, 190, 175, 0.42);
  background: rgba(172, 100, 72, 0.24);
  color: #e5beaf;
}

.assessment-container.is-dark .icon-box.app-icon-tile--candy {
  border-color: rgba(226, 123, 102, 0.42);
  background: rgba(194, 59, 34, 0.22);
  color: #e27b66;
}
</style>
