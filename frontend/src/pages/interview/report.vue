<template>
  <SlPage class="app-soft-bg" :custom-class="['competition-report-page', themeClass, fontClass].join(' ')">
    <SlNavBar :title="t('interviewReport.navTitle')" show-back @back="goBack" :safe-top="topSafeHeight" />


    <!-- Loading state while the report is generated -->
    <view class="loading-state app-surface" v-if="loading">
      <view class="spinner"></view>
      <text class="loading-title">{{ t('interviewReport.loading') }}</text>
      <text class="loading-sub">{{ t('interviewReport.loadingSub') }}</text>
    </view>

    <!-- Error state -->
    <view class="error-state app-surface" v-else-if="errorMsg">
      <text class="err-title">{{ t('interviewReport.errTitle') }}</text>
      <text class="err-detail">{{ errorMsg }}</text>
      <button class="btn-retry" @click="loadReport">{{ t('interviewReport.retry') }}</button>
    </view>

    <template v-else-if="report">
      <view class="report-header">
        <text class="report-brand">智绘职路 CareerLoop · AI 面试成长报告</text>
        <text class="r-title">{{ t('interviewReport.reportTitle') }}</text>
        <text class="r-sub">{{ report.positionName }} · {{ report.difficulty }}{{ report.durationSeconds ? ' · ' + formatDuration(report.durationSeconds) : '' }}</text>
        <text class="r-desc">{{ report.textSummary || t('interviewReport.summaryFallback') }}</text>
      </view>

      <!-- Overall score -->
      <view class="score-card app-card-metric app-surface">
        <view class="score-ring-box">
          <view class="score-ring" :class="scoreRingClass">
            <text class="score-num" :class="scoreNumClass">{{ report.overallScore }}</text>
          </view>
          <text class="score-label">{{ t('interviewReport.overallScore') }}</text>
        </view>
        <view class="score-summary">
          <text class="score-eval" :class="scoreNumClass">{{ scoreLabel }}</text>
          <text class="score-desc">{{ t('interviewReport.questionsAnswered', { n: report.totalQuestions }) }}</text>
        </view>
      </view>

      <view class="score-basis app-card-soft app-surface">
        <text class="basis-title">评分依据</text>
        <text class="basis-text">面试分由 AI 根据完整问答记录评估，重点看技术证据、沟通表达、逻辑结构、表达清晰度和抗压表现；语音面试会额外参考肢体语言采样。</text>
      </view>

      <view class="behavior-section" v-if="showBehaviorCard">
        <text class="section-title">行为表现评估</text>
        <view class="behavior-card app-card-soft app-surface" v-if="report.bodyLanguageAnalysis">
          <view class="behavior-head">
            <view>
              <text class="behavior-score">{{ report.bodyLanguageAnalysis.bodyLanguage }}</text>
              <text class="behavior-label">综合行为分</text>
            </view>
            <text class="behavior-samples">{{ report.bodyLanguageAnalysis.frames }} 帧采样</text>
          </view>
          <text class="behavior-summary">{{ report.bodyLanguageAnalysis.summary }}</text>
          <view class="behavior-bars">
            <view class="behavior-row" v-for="item in behaviorRows" :key="item.label">
              <text class="behavior-name">{{ item.label }}</text>
              <view class="behavior-track">
                <view class="behavior-fill" :style="{ width: item.value + '%' }"></view>
              </view>
              <text class="behavior-value">{{ item.value }}</text>
            </view>
          </view>
          <text class="behavior-note">行为表现来自语音面试期间摄像头采样聚合，只用于辅助反馈。</text>
        </view>
        <view class="behavior-card app-card-soft app-surface empty-behavior" v-else>
          <text class="empty-behavior-title">本次未获得有效行为采样</text>
          <text class="empty-behavior-text">可能是摄像头权限未开启、采样服务不可用，或有效画面不足。本次报告仅基于问答内容评分。</text>
        </view>
      </view>

      <!-- Dimensions breakdown -->
      <view class="dims-section">
        <text class="section-title">{{ t('interviewReport.dimensionBreakdown') }}</text>
        <view class="dims-card app-card-soft app-surface">
          <view class="mini-chart">
            <view v-for="(d, i) in dimensions" :key="'chart-' + i" class="mini-col">
              <view class="mini-bar-wrap">
                <view class="mini-bar" :class="'bar-' + i" :style="{ height: d.score + '%' }"></view>
              </view>
              <text class="mini-label">{{ d.short }}</text>
            </view>
          </view>
          <view class="dim-row" v-for="(d, i) in dimensions" :key="i">
            <text class="dim-name">{{ d.name }}</text>
            <view class="dim-bar-bg">
              <view class="dim-bar-fill" :class="'bar-' + i" :style="{ width: d.score + '%' }"></view>
            </view>
            <text class="dim-score">{{ d.score }}</text>
          </view>
        </view>
      </view>

      <!-- Strengths -->
      <view class="advice-section" v-if="report.strengths && report.strengths.length">
        <text class="section-title">{{ t('interviewReport.strengths') }}</text>
        <view class="advice-card app-card-soft good app-surface" v-for="(a, i) in report.strengths" :key="'s' + i">
          <text class="advice-icon ri-check-line"></text>
          <view class="advice-body">
            <text class="advice-title">{{ a.title }}</text>
            <text class="advice-detail">{{ a.detail }}</text>
          </view>
        </view>
      </view>

      <!-- Improvements -->
      <view class="advice-section" v-if="report.improvements && report.improvements.length">
        <text class="section-title">{{ t('interviewReport.improvements') }}</text>
        <view class="advice-card app-card-soft warn app-surface" v-for="(a, i) in report.improvements" :key="'i' + i">
          <text class="advice-icon">!</text>
          <view class="advice-body">
            <text class="advice-title">{{ a.title }}</text>
            <text class="advice-detail">{{ a.detail }}</text>
          </view>
        </view>
      </view>

      <!-- Contribute to the question market -->
      <view class="advice-section">
        <text class="section-title">{{ t('interviewReport.helpOthers') }}</text>
        <view class="contribute-card app-card-soft app-surface">
          <text class="contribute-tip">{{ t('interviewReport.helpOthersTip') }}</text>
          <textarea
            class="contribute-input ui-input"
            v-model="contributeText"
            :maxlength="800"
            :placeholder="t('interviewReport.questionPlaceholder')"
          />
          <view class="contribute-actions">
            <button
              class="btn-secondary"
              @click="navTo('/pages/market/index?position=' + encodeURIComponent(report.positionName || ''))"
            >{{ t('interviewReport.browseMarket') }}</button>
            <button
              class="btn-primary"
              :disabled="contributing || !contributeText.trim()"
              @click="submitContribution"
            >{{ contributing ? t('market.sharing') : t('market.shareBtn') }}</button>
          </view>
        </view>
      </view>
    </template>

    <!-- Bottom action -->
    <view class="bottom-bar">
      <button class="btn-back" @click="backToLobby">{{ t('interviewReport.backToLobby') }}</button>
    </view>
  </SlPage>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue';
import { useI18n } from '@/locales';
import { onShow } from '@dcloudio/uni-app';
import { getMpSafeAreaMetrics } from '@/utils/safeArea';
import { getInterviewReportApi, type InterviewReport } from '@/api/interview';
import { contributeQuestionApi } from '@/api/market';
import { useTheme } from '@/utils/theme';
import SlPage from '@/style-library/components/SlPage.vue';
import SlNavBar from '@/style-library/components/SlNavBar.vue';

const { t } = useI18n();
const { themeClass, fontClass, refresh: refreshTheme } = useTheme();
const topSafeHeight = ref(52);
const loading = ref(true);
const errorMsg = ref('');
const report = ref<InterviewReport | null>(null);
const interviewId = ref<number>(0);

const goBack = () => uni.navigateBack({ delta: 1 });

const dimensions = computed(() => {
  const r = report.value?.radarChart;
  if (!r) return [] as Array<{ name: string; short: string; score: number }>;
  const base: Array<{ name: string; short: string; score: number }> = [
    { name: '技术能力', short: '技术', score: r.technical },
    { name: '沟通表达', short: '沟通', score: r.communication },
    { name: '逻辑结构', short: '逻辑', score: r.logic },
    { name: '表达清晰度', short: '表达', score: r.expression },
    { name: '抗压表现', short: '抗压', score: r.pressureResistance },
  ];
  // Sprint C-1: surface the 6th dimension only when the sidecar actually
  // collected frames. Null bodyLanguage means "text interview", we don't
  // want to plot a fake zero score against it.
  if (r.bodyLanguage != null) {
    base.push({ name: '肢体语言', short: '体态', score: r.bodyLanguage });
  }
  return base;
});

const showBehaviorCard = computed(() =>
  !!report.value?.bodyLanguageAnalysis || String(report.value?.mode || '').toUpperCase() === 'VOICE'
);

const behaviorRows = computed(() => {
  const b = report.value?.bodyLanguageAnalysis;
  if (!b) return [];
  return [
    { label: '眼神交流', value: b.eyeContact },
    { label: '表情状态', value: b.expression },
    { label: '坐姿稳定', value: b.posture },
  ];
});

const scoreLabel = computed(() => {
  const s = report.value?.overallScore ?? 0;
  if (s >= 85) return '表现优秀';
  if (s >= 70) return t('interviewReport.scoreGood');
  if (s >= 55) return t('interviewReport.scoreFair');
  return t('interviewReport.scoreNeedsWork');
});

const scoreNumClass = computed(() => {
  const s = report.value?.overallScore ?? 0;
  if (s >= 70) return 'tone-good';
  if (s >= 55) return 'tone-warn';
  return 'tone-bad';
});

const scoreRingClass = computed(() => 'ring-' + scoreNumClass.value);

const formatDuration = (seconds: number) => {
  if (!seconds || seconds < 0) return '';
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return m > 0 ? `${m}m ${s}s` : `${s}s`;
};

const loadReport = async () => {
  if (!interviewId.value) {
    errorMsg.value = t('interviewReport.missingInterviewId');
    loading.value = false;
    return;
  }
  loading.value = true;
  errorMsg.value = '';
  try {
    report.value = await getInterviewReportApi(interviewId.value);
  } catch (e: any) {
    const msg = String(e?.message || '');
    errorMsg.value = /no candidate answers|cannot evaluate|尚未作答|没有任何回答/i.test(msg)
      ? t('interviewReport.noAnswer')
      : (msg || t('interviewReport.generateFailed'));
  } finally {
    loading.value = false;
  }
};

const backToLobby = () => {
  uni.reLaunch({ url: '/pages/interview/index' });
};

const contributeText = ref('');
const contributing = ref(false);

const navTo = (url: string) => uni.navigateTo({ url });

const submitContribution = async () => {
  const trimmed = contributeText.value.trim();
  if (trimmed.length < 8) {
    uni.showToast({ title: t('market.addMoreWords'), icon: 'none' });
    return;
  }
  if (!report.value) return;
  contributing.value = true;
  try {
    await contributeQuestionApi({
      position: report.value.positionName || 'General',
      difficulty: report.value.difficulty || 'Normal',
      content: trimmed,
    });
    contributeText.value = '';
    uni.showToast({ title: t('market.shareSuccess'), icon: 'success' });
  } catch (e: any) {
    uni.showToast({ title: e?.message || t('market.shareFailed'), icon: 'none' });
  } finally {
    contributing.value = false;
  }
};

onMounted(() => {
  refreshTheme();
  topSafeHeight.value = getMpSafeAreaMetrics().topSafeHeight;
  const pages = getCurrentPages();
  const currentPage = pages[pages.length - 1] as any;
  interviewId.value = parseInt(currentPage.options?.interviewId || '0');
  loadReport();
});

onShow(() => {
  refreshTheme();
});
</script>

<style scoped>
.report-header { margin-bottom: 24px; padding: 0 20px; }
.report-brand {
  display: block;
  margin-bottom: 7px;
  color: var(--brand-color, #ac6448);
  font-size: 10px;
  line-height: 1.3;
  font-weight: 800;
  letter-spacing: 0.06em;
}

.r-title {
  font-size: 28px; font-weight: 800; color: #1c1917;
  letter-spacing: -0.5px; display: block; margin-bottom: 6px;
}

.r-sub { font-size: 13px; color: #a8a29e; }

.r-desc {
  display: block;
  margin-top: 10px;
  font-size: 14px;
  line-height: 1.5;
  color: #78716c;
}

/* Score card */
.score-card {
  padding: 28px 24px;
  display: flex; gap: 20px; align-items: center; margin-bottom: 28px; margin-left: 20px; margin-right: 20px;
}

.score-basis { margin: -8px 20px 22px; padding: 14px 16px; }
.basis-title { display: block; font-size: 14px; font-weight: 900; color: var(--text-primary, #1c1917); margin-bottom: 6px; }
.basis-text { display: block; font-size: 12.5px; line-height: 1.55; color: var(--text-secondary, #78716c); }

.behavior-section { margin-bottom: 28px; padding: 0 20px; }
.behavior-card { padding: 16px; }
.behavior-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}
.behavior-score {
  display: block;
  font-size: 32px;
  line-height: 1;
  font-weight: 900;
  color: var(--primary-color, #cd6a43);
}
.behavior-label {
  display: block;
  margin-top: 4px;
  font-size: 11px;
  font-weight: 800;
  color: var(--text-tertiary, #8e8e93);
}
.behavior-samples {
  flex-shrink: 0;
  padding: 4px 8px;
  border-radius: 999px;
  background: var(--primary-soft, #fcf5f2);
  color: var(--primary-color, #cd6a43);
  font-size: 11px;
  font-weight: 900;
}
.behavior-summary {
  display: block;
  font-size: 13px;
  line-height: 1.55;
  color: var(--text-secondary, #78716c);
  margin-bottom: 14px;
}
.behavior-bars { display: flex; flex-direction: column; gap: 11px; }
.behavior-row {
  display: grid;
  grid-template-columns: 66px 1fr 28px;
  align-items: center;
  gap: 10px;
}
.behavior-name { font-size: 12px; font-weight: 800; color: var(--text-primary, #1c1917); }
.behavior-track {
  height: 8px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--surface-3, #f5f5f4);
}
.behavior-fill {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, #cd6a43, #977635);
}
.behavior-value {
  font-size: 12px;
  font-weight: 900;
  text-align: right;
  color: var(--text-primary, #1c1917);
}
.behavior-note {
  display: block;
  margin-top: 12px;
  font-size: 11px;
  line-height: 1.45;
  color: var(--text-tertiary, #8e8e93);
}
.empty-behavior-title {
  display: block;
  font-size: 14px;
  font-weight: 900;
  color: var(--text-primary, #1c1917);
  margin-bottom: 6px;
}
.empty-behavior-text {
  display: block;
  font-size: 12px;
  line-height: 1.55;
  color: var(--text-secondary, #78716c);
}

.score-ring-box {
  display: flex; flex-direction: column; align-items: center; gap: 8px;
  flex-shrink: 0;
}

.score-ring {
  width: 80px; height: 80px; border-radius: 40px;
  border: 5px solid transparent;
  background-image: linear-gradient(#ffffff, #ffffff), linear-gradient(135deg, #ab863c, #896b30);
  background-origin: border-box;
  background-clip: padding-box, border-box;
  display: flex; justify-content: center; align-items: center;
}

.score-num { font-size: 28px; font-weight: 800; }
.score-num.tone-good { color: #6e5627; }
.score-num.tone-warn { color: #b45309; }
.score-num.tone-bad  { color: #b91c1c; }

.ring-tone-good { background-image: linear-gradient(#ffffff, #ffffff), linear-gradient(135deg, #ab863c, #896b30); }
.ring-tone-warn { background-image: linear-gradient(#ffffff, #ffffff), linear-gradient(135deg, #f59e0b, #d97706); }
.ring-tone-bad  { background-image: linear-gradient(#ffffff, #ffffff), linear-gradient(135deg, #ef4444, #dc2626); }

.score-label { font-size: 12px; color: #a8a29e; font-weight: 500; }

.score-summary { flex: 1; }

.score-eval {
  font-size: 20px; font-weight: 700;
  display: block; margin-bottom: 6px;
}
.score-eval.tone-good { color: #6e5627; }
.score-eval.tone-warn { color: #b45309; }
.score-eval.tone-bad  { color: #b91c1c; }

.score-desc { font-size: 13px; color: #78716c; line-height: 1.5; }

.dims-section { margin-bottom: 28px; padding: 0 20px; }

.section-title {
  font-size: 18px; font-weight: 700; color: #1c1917;
  margin-bottom: 14px; display: block; letter-spacing: -0.3px;
}

.dims-card {
  padding: 20px;
}

.mini-chart {
  height: 132px;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 2px 14px;
  margin-bottom: 14px;
  border-bottom: 1px solid var(--border-color, #e7e5e4);
}
.mini-col { flex: 1; min-width: 0; display: flex; flex-direction: column; align-items: center; gap: 6px; height: 100%; }
.mini-bar-wrap {
  width: 100%;
  max-width: 26px;
  flex: 1;
  border-radius: 999px;
  background: var(--surface-3, #f5f5f4);
  display: flex;
  align-items: flex-end;
  overflow: hidden;
}
.mini-bar { width: 100%; min-height: 8%; border-radius: 999px 999px 0 0; }
.mini-label { font-size: 10px; color: var(--text-tertiary, #8e8e93); white-space: nowrap; }

.dim-row {
  display: flex; align-items: center; gap: 12px;
  margin-bottom: 18px;
}

.dim-row:last-child { margin-bottom: 0; }

.dim-name { width: 90px; font-size: 14px; font-weight: 600; color: #44403c; }

.dim-bar-bg {
  flex: 1; height: 8px; background: #f5f5f4; border-radius: 4px; overflow: hidden;
}

.dim-bar-fill { height: 100%; border-radius: 4px; transition: width 0.5s ease; }

.bar-0 { background: linear-gradient(90deg, #d47f5d, #dd987d); }
.bar-1 { background: linear-gradient(90deg, #957534, #bf9748); }
.bar-2 { background: linear-gradient(90deg, #db9377, #e6b39f); }
.bar-3 { background: linear-gradient(90deg, #f59e0b, #fbbf24); }

.dim-score { width: 28px; font-size: 14px; font-weight: 700; color: #1c1917; text-align: right; }

/* Advice */
.advice-section { margin-bottom: 24px; padding: 0 20px; }

.advice-card {
  display: flex; gap: 14px; padding: 18px; margin-bottom: 12px;
}

.advice-icon {
  font-size: 16px; font-weight: 700; flex-shrink: 0;
  width: 28px; height: 28px; line-height: 28px; text-align: center;
  border-radius: 14px; color: #fff;
}
.advice-card.good .advice-icon { background: #ab863c; }
.advice-card.warn .advice-icon { background: #f59e0b; }

.loading-state, .error-state {
  background: #fff; border: 1px solid var(--border-color, #d8c1b8);
  border-radius: 20px; padding: 36px 24px;
  display: flex; flex-direction: column; align-items: center; gap: 10px;
  margin-top: 12px;
}
.loading-title { font-size: 16px; font-weight: 700; color: #1c1917; }
.loading-sub { font-size: 13px; color: #78716c; line-height: 1.5; text-align: center; }
.spinner {
  width: 36px; height: 36px; border: 3px solid #e0dfdb; border-top-color: #ac6448;
  border-radius: 50%; animation: spin 0.9s linear infinite; margin-bottom: 8px;
}
@keyframes spin { to { transform: rotate(360deg); } }
.err-title { font-size: 16px; font-weight: 700; color: #b91c1c; }
.err-detail { font-size: 13px; color: #78716c; text-align: center; line-height: 1.5; }
.btn-retry {
  margin-top: 8px; background: #c23b22; color: #fff;
  border: none; height: 40px; line-height: 40px;
  font-size: 14px; font-weight: 600; border-radius: 12px; padding: 0 20px;
}

.advice-body { flex: 1; }

.advice-title {
  font-size: 15px; font-weight: 600; color: #292524;
  display: block; margin-bottom: 6px;
}

.advice-detail { font-size: 13px; color: #78716c; line-height: 1.55; }

/* Bottom bar */
.bottom-bar {
  position: fixed; bottom: 0; left: 0; right: 0;
  padding: 16px 20px calc(20px + env(safe-area-inset-bottom, 0px));
  background: rgba(250, 249, 246, 0.94);
  backdrop-filter: blur(24px); -webkit-backdrop-filter: blur(24px);
  border-top: 0.5px solid rgba(60, 60, 67, 0.1); z-index: 100;
}

.btn-back {
  width: 100%; background: #c23b22; color: #ffffff;
  font-size: 16px; font-weight: 600; border-radius: 16px;
  height: 52px; line-height: 52px; border: none;
}

.btn-back:active { background: #a8321d; }

/* Phase 4 — contribute card */
.contribute-card {
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.contribute-tip { font-size: 13px; line-height: 1.5; color: #78716c; }
.contribute-input {
  width: 100%;
  min-height: 88px;
  padding: 10px 12px;
  border: 1px solid var(--border-color, #d8c1b8);
  border-radius: 12px;
  background: #fafaf9;
  font-size: 14px; line-height: 1.5;
  color: #1c1917;
  box-sizing: border-box;
}
.contribute-actions { display: flex; gap: 10px; }
.btn-secondary {
  flex: 1; height: 40px; line-height: 40px; padding: 0 14px;
  border-radius: 12px; font-size: 13px; font-weight: 600;
  background: #f5f5f0; color: #ac6448; border: 1px solid #e0dfdb;
}
.btn-primary {
  flex: 1; height: 40px; line-height: 40px; padding: 0 14px;
  border-radius: 12px; font-size: 13px; font-weight: 700;
  background: #c23b22; color: #fff; border: none;
}
.btn-primary[disabled] { opacity: 0.55; }

/* Dark mode */
.is-dark .r-title,
.is-dark .section-title,
.is-dark .dim-name,
.is-dark .dim-score,
.is-dark .advice-title { color: #fafaf9; }

.is-dark .score-card,
.is-dark .dims-card,
.is-dark .advice-card,
.is-dark .contribute-card { background: transparent; box-shadow: none; border-color: #44403c; }
.is-dark .contribute-input { background: #1c1917; color: #fafaf9; border-color: #44403c; }
.is-dark .contribute-tip { color: #a8a29e; }
.is-dark .btn-secondary { background: #292524; color: #e8baa8; border-color: #44403c; }

.is-dark .score-desc,
.is-dark .r-desc,
.is-dark .advice-detail { color: #a8a29e; }

.is-dark .bottom-bar { background: rgba(28, 25, 23, 0.88); border-color: #44403c; }

/* Competition visual system ------------------------------------------------ */
.report-brand {
  color: #c23b22;
  font-weight: 600;
  letter-spacing: 0.12em;
}

.r-title,
.section-title,
.basis-title,
.advice-title,
.loading-title {
  color: #2c2b29;
  font-family: "Songti SC", STSong, serif;
  font-weight: 600;
  letter-spacing: 0.05em;
}

.r-sub {
  color: #8b8a86;
}

.r-desc,
.score-desc,
.basis-text,
.behavior-summary,
.behavior-note,
.advice-detail,
.contribute-tip,
.loading-sub,
.err-detail {
  color: #5a5956;
}

.score-card,
.score-basis,
.behavior-card,
.dims-card,
.advice-card,
.contribute-card,
.loading-state,
.error-state {
  background: #ffffff;
  border: 1px solid #e0dfdb;
  border-radius: 8px;
  box-shadow: 0 6px 18px rgba(44, 43, 41, 0.04);
}

.score-card {
  border-top: 1px solid var(--border-color, #e0dfdb);
}

.score-ring {
  background: #ffffff;
  background-image: none;
  border: 4px solid #947f54;
}

.ring-tone-warn {
  border-color: #b8975a;
}

.ring-tone-bad {
  border-color: #c23b22;
}

.score-num {
  font-family: "Songti SC", STSong, serif;
  font-weight: 600;
}

.behavior-score,
.dim-score {
  color: #ac6448;
  font-family: "Songti SC", STSong, serif;
  font-weight: 600;
}

.behavior-samples {
  color: #ac6448;
  background: transparent;
  border: 1px solid rgba(172, 100, 72, 0.32);
  border-radius: 4px;
}

.behavior-track,
.dim-bar-bg,
.mini-bar-wrap {
  background: #efeee9;
  border-radius: 2px;
}

.behavior-fill,
.bar-0 {
  background: #ac6448;
}

.bar-1 {
  background: #947f54;
}

.bar-2 {
  background: #b8975a;
}

.bar-3 {
  background: #c23b22;
}

.mini-bar,
.dim-bar-fill {
  border-radius: 2px 2px 0 0;
}

.advice-icon {
  border-radius: 4px;
}

.advice-card.good .advice-icon {
  background: #947f54;
}

.advice-card.warn .advice-icon {
  background: #b8975a;
}

.contribute-input {
  color: #2c2b29;
  background: #faf9f6;
  border: 1px solid #d8d7d2;
  border-radius: 6px;
}

.btn-secondary,
.btn-primary,
.btn-retry,
.btn-back {
  border-radius: 6px;
}

.btn-secondary {
  color: #ac6448;
  background: #faf9f6;
  border-color: #d8d7d2;
}

.btn-primary,
.btn-retry,
.btn-back {
  color: #faf9f6;
  background: #c23b22;
}

.bottom-bar {
  background: #faf9f6;
  border-top: 1px solid #e0dfdb;
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
}

.is-dark .r-title,
.is-dark .section-title,
.is-dark .basis-title,
.is-dark .advice-title {
  color: #faf9f6;
}

.is-dark .score-card,
.is-dark .score-basis,
.is-dark .behavior-card,
.is-dark .dims-card,
.is-dark .advice-card,
.is-dark .contribute-card,
.is-dark .loading-state,
.is-dark .error-state {
  background: #242320;
  border-color: #494844;
}

.is-dark .score-ring {
  background: #242320;
}

.is-dark .contribute-input {
  color: #faf9f6;
  background: #1c1b19;
  border-color: #494844;
}

.is-dark .bottom-bar {
  background: #1c1b19;
  border-color: #494844;
}

.is-dark .behavior-track,
.is-dark .dim-bar-bg,
.is-dark .mini-bar-wrap {
  background: #45443f;
}
</style>
