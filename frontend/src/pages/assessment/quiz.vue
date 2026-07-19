<template>
  <view class="quiz-container app-soft-bg" :class="[themeClass, fontClass]">
    <view class="header-bar" :style="{ paddingTop: topSafeHeight + 6 + 'px', paddingRight: rightAvoidWidth + 'px' }">
      <view class="header-top">
        <view class="back-btn" @click="goBack">
          <text class="back-icon">‹</text>
          <text class="back-text">{{ t('common.back') }}</text>
        </view>
      </view>
      <view class="progress-wrapper">
        <view class="progress-track">
          <view class="progress-fill" :style="{ width: progressPercentage + '%' }"></view>
        </view>
        <text class="progress-text">{{ currentIndex + 1 }} / {{ questions.length }}</text>
      </view>
    </view>

    <!-- Loading state while we fetch the question bank -->
    <view class="loading-state app-surface" v-if="loading">
      <view class="spinner"></view>
      <text class="loading-text">{{ t('quiz.loading') }}</text>
    </view>

    <view class="error-state app-surface" v-else-if="errorMsg">
      <text class="err-title">{{ errorMsg }}</text>
      <view class="btn-retry" @click="loadQuestions"><text class="btn-retry-text">{{ t('quiz.retry') }}</text></view>
    </view>

    <template v-else-if="currentQuestion">
      <view class="question-card">
        <text class="q-type">{{ t('quiz.question', { n: currentIndex + 1 }) }}</text>
        <text class="q-title">{{ currentQuestion.questionText }}</text>
      </view>

      <view class="options-list">
        <view
          class="option-item app-card-soft"
          v-for="opt in currentQuestion?.options"
          :key="opt.optionId"
          :class="{ 'option-selected': currentAnswerOptionId === opt.optionId }"
          @click="selectOption(opt.optionId)"
        >
          <view class="option-label">{{ opt.optionLabel }}</view>
          <text class="option-text">{{ opt.optionText }}</text>
        </view>
      </view>
    </template>

    <view class="bottom-action" v-if="!loading && !errorMsg">
      <view
        class="btn-prev"
        :class="{ 'btn-disabled': currentIndex === 0 }"
        @click="handlePrev"
      ><text class="btn-prev-text">{{ t('quiz.previous') }}</text></view>
      <view
        class="btn-next"
        :class="{ 'btn-next-disabled': currentAnswerOptionId == null || submitting }"
        @click="handleNext"
      ><text class="btn-next-text">{{ submitting ? t('quiz.submitting') : (isLastQuestion ? t('quiz.viewResults') : t('quiz.next')) }}</text></view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useI18n } from '@/locales';
import { onShow } from '@dcloudio/uni-app';
import {
  getScaleQuestionsApi,
  submitAssessmentApi,
  type QuizQuestion,
} from '@/api/assessment';
import { useTheme } from '@/utils/theme';
import { getMpSafeAreaMetrics } from '@/utils/safeArea';
import { requireAuth } from '@/utils/auth';

const currentIndex = ref(0);
// 防止自动前进与手动点击"下一题"竞态导致跳出题目范围
const advancing = ref(false);
let advanceTimer: ReturnType<typeof setTimeout> | null = null;
const { t } = useI18n();
const { themeClass, fontClass, refresh: refreshTheme } = useTheme();
const topSafeHeight = ref(52);
const rightAvoidWidth = ref(20);
const loading = ref(true);
const submitting = ref(false);
const errorMsg = ref('');

const scaleId = ref<number>(0);
const scaleTitle = ref('');
const questions = ref<QuizQuestion[]>([]);

// answers maps questionId -> selected optionId. Stable across navigation.
const answers = ref<Record<number, number>>({});

const currentQuestion = computed(() => questions.value[currentIndex.value]);
const currentAnswerOptionId = computed(() => {
  const q = currentQuestion.value;
  return q ? answers.value[q.questionId] : null;
});
const isLastQuestion = computed(() => currentIndex.value === questions.value.length - 1);
const progressPercentage = computed(() =>
  questions.value.length === 0
    ? 0
    : ((currentIndex.value + 1) / questions.value.length) * 100,
);

const loadQuestions = async () => {
  if (!requireAuth({
    redirect: 'reLaunch',
    message: '职业测评需要登录后进行，避免完成答题后无法保存结果。',
  })) {
    errorMsg.value = '登录后即可开始测评并保存结果';
    loading.value = false;
    return;
  }
  if (!scaleId.value) {
    errorMsg.value = t('quiz.missingScaleId');
    loading.value = false;
    return;
  }
  loading.value = true;
  errorMsg.value = '';
  try {
    const list = await getScaleQuestionsApi(scaleId.value);
    questions.value = list || [];
    if (questions.value.length === 0) {
      errorMsg.value = t('quiz.noQuestions');
    }
  } catch (e: any) {
    errorMsg.value = e?.message || t('quiz.loadFailed');
  } finally {
    loading.value = false;
  }
};

const selectOption = (optionId: number) => {
  const q = currentQuestion.value;
  if (!q) return;
  answers.value[q.questionId] = optionId;
  // Auto-advance feels great on a long quiz, but only when this isn't the
  // last question -- the final tap should require an explicit submit.
  // Use the `advancing` flag + a stored timer ID to prevent a race where the
  // user taps the manual "Next" button within the 250 ms window, which would
  // cause the index to increment twice and land on a non-existent question.
  if (!isLastQuestion.value && !advancing.value) {
    advancing.value = true;
    if (advanceTimer !== null) clearTimeout(advanceTimer);
    advanceTimer = setTimeout(() => {
      advanceTimer = null;
      advancing.value = false;
      const next = currentIndex.value + 1;
      if (next < questions.value.length) {
        currentIndex.value = next;
      }
    }, 250);
  }
};

const handlePrev = () => {
  // 取消任何待执行的自动前进，避免"上一题"后又被弹回去
  if (advanceTimer !== null) {
    clearTimeout(advanceTimer);
    advanceTimer = null;
    advancing.value = false;
  }
  if (currentIndex.value > 0) currentIndex.value--;
};

const submitQuiz = async () => {
  if (submitting.value) return;
  if (!requireAuth({
    redirect: 'reLaunch',
    message: '登录后才能提交测评并生成你的职业画像。',
  })) return;
  submitting.value = true;
  uni.showLoading({ title: t('quiz.submitting') });
  try {
    const record = await submitAssessmentApi(scaleId.value, answers.value);
    uni.hideLoading();
    uni.redirectTo({
      url: `/pages/assessment/result?recordId=${record.recordId}`,
    });
  } catch (e: any) {
    uni.hideLoading();
    uni.showToast({ title: e?.message || t('quiz.submissionFailed'), icon: 'none' });
  } finally {
    submitting.value = false;
  }
};

const handleNext = () => {
  if (currentAnswerOptionId.value == null) {
    uni.showToast({ title: t('quiz.selectOption'), icon: 'none' });
    return;
  }
  if (isLastQuestion.value) {
    submitQuiz();
    return;
  }
  // 如果自动前进的定时器还在跑，取消它并立即前进一次，避免重复计数
  if (advanceTimer !== null) {
    clearTimeout(advanceTimer);
    advanceTimer = null;
    advancing.value = false;
  }
  if (!advancing.value) {
    const next = currentIndex.value + 1;
    if (next < questions.value.length) {
      currentIndex.value = next;
    }
  }
};

const goBack = () => {
  if (Object.keys(answers.value).length > 0) {
    uni.showModal({
      title: t('quiz.exitTitle'),
      content: t('quiz.exitContent'),
      confirmColor: '#ef4444',
      success: (res) => {
        if (res.confirm) uni.navigateBack({ delta: 1 });
      }
    });
    return;
  }
  uni.navigateBack({ delta: 1 });
};

onMounted(() => {
  refreshTheme();
  const safeMetrics = getMpSafeAreaMetrics();
  topSafeHeight.value = safeMetrics.topSafeHeight;
  rightAvoidWidth.value = safeMetrics.rightAvoidWidth;
  // Pull scaleId + title from query params (set by assessment/index.vue).
  const pages = getCurrentPages();
  const opts = (pages[pages.length - 1] as any).options || {};
  scaleId.value = parseInt(opts.scaleId || '0');
  if (opts.title) {
    try { scaleTitle.value = decodeURIComponent(opts.title); } catch { /* keep default */ }
    uni.setNavigationBarTitle({ title: scaleTitle.value });
  }
  loadQuestions();
});

onShow(() => {
  refreshTheme();
  uni.setNavigationBarTitle({ title: scaleTitle.value || t('assessment.pageTitle') });
});
</script>

<style scoped>
.quiz-container {
  min-height: 100vh;
  padding: 122px 20px calc(120px + env(safe-area-inset-bottom, 0px)) 20px;
  font-family: -apple-system, BlinkMacSystemFont, "SF Pro Text", "Helvetica Neue", sans-serif;
  box-sizing: border-box;
}

.header-bar {
  position: fixed;
  top: 0; left: 0; right: 0;
  background: var(--surface-1, #ffffff);
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
  z-index: 100;
  padding: 26px 20px 14px 20px;
  border-bottom: 0.5px solid var(--border-color, #e7e5e4);
  box-sizing: border-box;
}

.header-top { display: flex; align-items: center; min-height: 44px; margin-bottom: 10px; }

.back-btn { display: inline-flex; align-items: center; gap: 2px; color: var(--text-primary, #1c1917); width: var(--nav-back-width, 64px); }

.back-icon { font-size: 26px; font-weight: 500; line-height: 1; }

.back-text { display: none; }

.progress-wrapper { width: 100%; display: flex; align-items: center; gap: 12px; }

.progress-track { flex: 1; height: 6px; background-color: #e7e5e4; border-radius: 3px; overflow: hidden; }

.progress-fill { height: 100%; background-color: #c95f36; border-radius: 3px; transition: width 0.4s cubic-bezier(0.25, 0.8, 0.25, 1); }

.progress-text { font-size: 13px; font-weight: 500; color: #8e8e93; min-width: 52px; white-space: nowrap; flex-shrink: 0; text-align: right; }

.question-card { margin-bottom: 40px; padding: 0 8px; }

.q-type { font-size: 14px; font-weight: 600; color: #c95f36; margin-bottom: 12px; display: block; letter-spacing: 1px; }

.q-title { font-size: 26px; font-weight: 700; color: #000000; line-height: 1.4; letter-spacing: -0.5px; }

.options-list { display: flex; flex-direction: column; gap: 16px; }

.option-item {
  display: flex;
  align-items: flex-start;
  transition: all 0.2s ease;
}

.option-item:active { transform: scale(0.98); }

.option-selected { background-color: #fcf5f2; border-color: #d47f5d; }

.option-label {
  width: 28px; height: 28px; border-radius: 14px;
  background-color: #f8f3f1; color: #636366;
  font-size: 14px; font-weight: 600;
  display: flex; justify-content: center; align-items: center;
  margin-right: 16px; flex-shrink: 0;
}

.option-selected .option-label { background-color: #d47f5d; color: #ffffff; }

.option-text { font-size: 17px; color: #1c1c1e; line-height: 1.5; flex: 1; }

.option-selected .option-text { color: #824026; font-weight: 500; }

.bottom-action {
  position: fixed; bottom: 0; left: 0; right: 0;
  padding: 16px 20px calc(20px + env(safe-area-inset-bottom, 0px)) 20px;
  background: rgba(245, 245, 247, 0.85);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-top: 0.5px solid rgba(60, 60, 67, 0.1);
  z-index: 100;
  display: flex; gap: 16px;
}

/* Custom-rendered buttons (using <view>) so mp-weixin's native <button>
   default styles can't override our colours. */
.btn-prev {
  flex: 1; background-color: #ffffff;
  border-radius: 16px;
  height: 52px;
  display: flex; align-items: center; justify-content: center;
  border: 1px solid var(--border-color, #d8c1b8);
}
.btn-prev-text {
  color: #cd6a43; font-size: 17px; font-weight: 600;
}
.btn-disabled .btn-prev-text { color: #c7c7cc; }
.btn-disabled { background-color: #f8f3f1; border-color: transparent; }

.btn-next {
  flex: 2; background-color: #cd6a43;
  border-radius: 16px;
  height: 52px;
  display: flex; align-items: center; justify-content: center;
  box-shadow: var(--shadow-card);
  transition: background 0.15s, opacity 0.15s;
}
.btn-next-text {
  color: #ffffff; font-size: 17px; font-weight: 700;
}
.btn-next:active { background-color: #c25c33; }
.btn-next-disabled { background-color: #d6d3d1; box-shadow: none; }
.btn-next-disabled .btn-next-text { color: #ffffff; opacity: 0.85; }

/* Loading + error states */
.loading-state, .error-state {
  background: #ffffff;
  border: 1px solid var(--border-color, #d8c1b8);
  border-radius: 20px;
  padding: 60px 24px;
  display: flex; flex-direction: column; align-items: center; gap: 14px;
  margin-top: 24px;
}
.spinner {
  width: 32px; height: 32px;
  border: 3px solid #e7e5e4; border-top-color: #cd6a43;
  border-radius: 50%;
  animation: quiz-spin 0.9s linear infinite;
}
@keyframes quiz-spin { to { transform: rotate(360deg); } }
.loading-text { font-size: 14px; color: #57534e; }
.err-title { font-size: 15px; color: #b91c1c; font-weight: 600; }
.btn-retry {
  background: #cd6a43; height: 40px; padding: 0 24px;
  border-radius: 12px; display: flex; align-items: center;
}
.btn-retry-text { color: #fff; font-size: 14px; font-weight: 600; }

.is-dark { background-color: #1c1917; }

.is-dark .header-bar,
.is-dark .option-item,
.is-dark .bottom-action { background: #292524; border-color: #44403c; }

.is-dark .q-title,
.is-dark .option-text,
.is-dark .progress-text,
.is-dark .back-text { color: #fafaf9; }

.is-dark .option-label,
.is-dark .progress-track { background-color: #44403c; color: #a8a29e; }

/* ── CareerLoop editorial skin ─────────────────────────────────────────── */
.quiz-container {
  background: #faf9f6;
  color: #2c2b29;
  font-family: "Noto Sans SC", "PingFang SC", "Microsoft YaHei", sans-serif;
}

.header-bar {
  background: #faf9f6;
  border-bottom: 1px solid #e0dfdb;
}

.back-btn {
  color: #2c2b29;
}

.progress-track {
  height: 5px;
  border-radius: 2px;
  background: #e0dfdb;
}

.progress-fill {
  border-radius: 2px;
  background: #c23b22;
}

.progress-text {
  color: #8b8a86;
  font-variant-numeric: tabular-nums;
}

.question-card {
  margin-bottom: 32px;
  padding: 0 2px;
}

.q-type {
  color: #b8975a;
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
  font-weight: 600;
  letter-spacing: 0.08em;
}

.q-title {
  color: #2c2b29;
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
  font-size: 24px;
  font-weight: 600;
  line-height: 1.6;
  letter-spacing: 0.05em;
}

.options-list {
  gap: 12px;
}

.option-item {
  padding: 16px;
  border: 1px solid #e0dfdb;
  border-radius: 8px;
  background: #fffdfa;
  box-shadow: 0 6px 18px rgba(44, 43, 41, 0.04);
}

.option-selected {
  border-color: #c23b22;
  background: #f8ece8;
  box-shadow: 0 6px 18px rgba(194, 59, 34, 0.08);
}

.option-label {
  width: 28px;
  height: 28px;
  border: 1px solid #d8d6cf;
  border-radius: 4px;
  background: #f5f5f0;
  color: #5a5956;
}

.option-selected .option-label {
  border-color: #c23b22;
  background: #c23b22;
  color: #fffdfa;
}

.option-text,
.option-selected .option-text {
  color: #2c2b29;
  font-size: 16px;
  line-height: 1.65;
}

.option-selected .option-text {
  font-weight: 600;
}

.bottom-action {
  gap: 12px;
  background: rgba(250, 249, 246, 0.97);
  border-top: 1px solid #e0dfdb;
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
}

.btn-prev,
.btn-next {
  height: 50px;
  border-radius: 6px;
  box-shadow: none;
}

.btn-prev {
  border: 1px solid #ac6448;
  background: #faf9f6;
}

.btn-prev-text {
  color: #ac6448;
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
  font-weight: 600;
  letter-spacing: 0.05em;
}

.btn-next {
  background: #c23b22;
}

.btn-next-text {
  color: #fffdfa;
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
  font-weight: 600;
  letter-spacing: 0.06em;
}

.btn-next:active {
  background: #a9321d;
}

.btn-disabled,
.btn-next-disabled {
  border-color: #d8d6cf;
  background: #e0dfdb;
}

.btn-disabled .btn-prev-text,
.btn-next-disabled .btn-next-text {
  color: #8b8a86;
}

.loading-state,
.error-state {
  border-color: #e0dfdb;
  border-radius: 8px;
  background: #fffdfa;
  box-shadow: 0 8px 24px rgba(44, 43, 41, 0.05);
}

.spinner {
  border-color: #e0dfdb;
  border-top-color: #c23b22;
}

.loading-text {
  color: #5a5956;
}

.btn-retry {
  border-radius: 6px;
  background: #c23b22;
}

.quiz-container.is-dark .btn-prev {
  border-color: #e5beaf;
  background: #1c1917;
}

.quiz-container.is-dark .btn-prev-text {
  color: #e5beaf;
}

.quiz-container.is-dark .btn-disabled,
.quiz-container.is-dark .btn-next-disabled {
  border-color: #57534e;
  background: #44403c;
}

.quiz-container.is-dark .btn-disabled .btn-prev-text,
.quiz-container.is-dark .btn-next-disabled .btn-next-text {
  color: #a8a29e;
}

.quiz-container.is-dark .option-item.option-selected {
  border-color: #e27b66;
  background: rgba(194, 59, 34, 0.2);
}

.quiz-container.is-dark .option-item.option-selected .option-label {
  border-color: #c23b22;
  background: #c23b22;
  color: #fffdfa;
}

.quiz-container.is-dark .option-item.option-selected .option-text {
  color: #fafaf9;
}
</style>
