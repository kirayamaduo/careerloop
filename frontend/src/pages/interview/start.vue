<template>
  <view class="start-page app-soft-bg" :class="[themeClass, fontClass]">
    <SlNavBar :title="t('interview.startPageTitle')" show-back @back="goBack" :safe-top="topSafeHeight" :right-avoid-width="rightAvoidWidth" />

    <view class="start-content">
      <view class="header app-card-gradient">
        <text class="header-kicker">智绘职路 CareerLoop · AI 模拟面试</text>
        <text class="title">{{ t('interview.startPageTitle') }}</text>
        <text class="subtitle">{{ t('interview.startSubtitle') }}</text>
      </view>

      <view class="form-card app-card-soft">
        <view class="form-item">
          <text class="label">{{ t('interview.modeLabel') }}</text>
          <view class="mode-grid">
            <view
              v-for="item in modes"
              :key="item.value"
              :class="['mode-card', selectedMode === item.value ? 'active' : '']"
              @click="selectedMode = item.value as 'voice' | 'text'"
             hover-class="press-fb" hover-stay-time="120">
              <view class="mode-head">
                <text class="mode-icon">{{ item.icon }}</text>
                <text class="mode-name">{{ item.label }}</text>
                <text v-if="item.badge" class="mode-badge">{{ item.badge }}</text>
              </view>
              <text class="mode-desc">{{ item.desc }}</text>
            </view>
          </view>
        </view>

        <view class="form-item">
          <view class="label-row">
            <text class="label">{{ t('interview.positionLabel') }}</text>
            <text v-if="prefillSource" class="label-hint">{{ prefillSource }}</text>
          </view>
          <view class="picker-box ui-list-item" :class="{ 'picker-filled': selectedPosition }" @click="showPositionSheet = true" hover-class="press-fb" hover-stay-time="120">
            <text class="picker-val" :class="{ 'has-val': selectedPosition }">{{ selectedPosition || t('interview.chooseRole') }}</text>
            <text class="picker-arrow ri-arrow-down-s-line"></text>
          </view>
        </view>
      </view>

      <view class="form-card app-card-soft">
        <view class="form-item form-item-last">
          <text class="label">{{ t('interview.difficultyLabel') }}</text>
          <view class="difficulty-grid">
            <view
              v-for="item in difficulties"
              :key="item.value"
              :class="['diff-card', selectedDifficulty === item.value ? 'active' : '']"
              @click="selectedDifficulty = item.value"
             hover-class="press-fb" hover-stay-time="120">
              <text class="diff-name">{{ item.label }}</text>
              <text class="diff-desc">{{ item.desc }}</text>
            </view>
          </view>
        </view>
      </view>

      <view class="expect-card app-card-soft">
        <text class="expect-title">{{ t('interview.whatToExpect') }}</text>
        <view class="expect-row">
          <text class="expect-icon expect-icon-time">01</text>
          <view class="expect-body">
            <text class="expect-h">{{ t('interview.expect1Title') }}</text>
            <text class="expect-p">{{ t('interview.expect1Desc') }}</text>
          </view>
        </view>
        <view class="expect-row">
          <text class="expect-icon expect-icon-chat">02</text>
          <view class="expect-body">
            <text class="expect-h">{{ t('interview.expect2Title') }}</text>
            <text class="expect-p">{{ t('interview.expect2Desc') }}</text>
          </view>
        </view>
        <view class="expect-row">
          <text class="expect-icon expect-icon-score">03</text>
          <view class="expect-body">
            <text class="expect-h">{{ t('interview.expect3Title') }}</text>
            <text class="expect-p">{{ t('interview.expect3Desc') }}</text>
          </view>
        </view>
      </view>
    </view>

    <!-- Sticky CTA pinned to the bottom thumb zone.
         We use <view> instead of <button> because mp-weixin's native <button>
         element ships with very heavy default styles (white background,
         pseudo-borders) that constantly fight scoped CSS. <view> renders
         exactly what we tell it to. -->
    <view class="sticky-cta">
      <view
        class="btn-primary"
        :class="{ 'btn-disabled': !selectedPosition || loading }"
        @click="startInterview"
       hover-class="press-fb" hover-stay-time="120">
        <text class="btn-primary-label" v-if="loading">{{ t('interview.starting') }}</text>
        <text class="btn-primary-label" v-else>{{ selectedPosition ? (selectedMode === 'voice' ? t('interview.startVoice') : t('interview.startText')) : t('interview.chooseFirst') }}</text>
      </view>
    </view>
    <SlActionSheet
      v-model:visible="showPositionSheet"
      title="选择面试岗位"
      :options="positionOptions"
      :selected-value="selectedPosition"
      @select="onPositionSelect"
    />
  </view>
</template>

<script setup lang="ts">
import { onMounted, ref, computed } from 'vue';
import { useI18n, currentLocale } from '@/locales';
import { onShow } from '@dcloudio/uni-app';
import { startInterviewApi } from '@/api/interview';
import { getProfileSnapshotApi, updatePreferencesApi } from '@/api/user';
import { LOGIN_PAGE } from '@/utils/auth';
import { useTheme } from '@/utils/theme';
import { getMpSafeAreaMetrics } from '@/utils/safeArea';
import SlNavBar from '@/style-library/components/SlNavBar.vue';
import SlActionSheet from '@/style-library/components/SlActionSheet.vue';

const { t } = useI18n();
const positions = ref<string[]>(['Java Backend Engineer', 'Frontend Engineer', 'Full Stack Engineer', 'Product Manager', 'Data Analyst']);
const selectedPosition = ref('');
const selectedPositionIndex = ref(0);
const showPositionSheet = ref(false);
const { themeClass, fontClass, refresh: refreshTheme } = useTheme();
const topSafeHeight = ref(52);
const rightAvoidWidth = ref(20);

/**
 * Tells the user *why* a position came pre-selected — e.g. "From your INFP
 * assessment" or "From your last interview" — so the prefill doesn't feel
 * magic / wrong. Empty string when nothing is prefilled.
 */
const prefillSource = ref('');

const difficulties = computed(() => [
  { label: t('interview.diffEasy'),   value: 'Easy',   desc: t('interview.diffEasyDesc') },
  { label: t('interview.diffNormal'), value: 'Normal', desc: t('interview.diffNormalDesc') },
  { label: t('interview.diffHard'),   value: 'Hard',   desc: t('interview.diffHardDesc') },
]);
const selectedDifficulty = ref('Normal');

// Voice is the recommended path — it's the differentiated experience and
// what real interviews feel like. We keep Text as the fallback for users
// without a quiet space or with poor mic permissions.
const modes = computed(() => [
  { label: t('interview.modeVoice'), value: 'voice', icon: 'V', badge: t('interview.modeRecommended'), desc: t('interview.modeVoiceDesc') },
  { label: t('interview.modeText'),  value: 'text',  icon: 'T', badge: '',                              desc: t('interview.modeTextDesc') },
]);
// Persist the user's last choice so returning users land on their preference.
const selectedMode = ref<'voice' | 'text'>(
  (uni.getStorageSync('interview_mode') as 'voice' | 'text') || 'voice'
);
const loading = ref(false);

const positionOptions = computed(() => positions.value.map((role) => ({
  label: role,
  value: role,
  subtitle: role === selectedPosition.value && prefillSource.value ? prefillSource.value : '用于生成面试问题和评分参考',
  icon: 'ri-briefcase-4-line',
})));

const onPositionSelect = ({ value }: { value: string }) => {
  const idx = positions.value.findIndex((item) => item === value);
  if (idx < 0) return;
  selectedPositionIndex.value = idx;
  selectedPosition.value = positions.value[idx];
  prefillSource.value = '';
};

/**
 * If the picker doesn't already include the candidate role, prepend it so
 * we can preselect it without losing the standard options. Returns the
 * index where the role lives in the picker after this call.
 */
const ensurePositionExists = (role: string): number => {
  const existing = positions.value.findIndex((p) => p.toLowerCase() === role.toLowerCase());
  if (existing >= 0) return existing;
  positions.value = [role, ...positions.value];
  return 0;
};

/**
 * Pull cross-tool portrait from the backend on entry so the user lands on
 * a form that's already filled in with their last role / mode preference.
 * Order of precedence:
 *   1. Explicit `?suggestedRole=` query (just clicked a CTA elsewhere)
 *   2. Snapshot preferences.targetRole (set by the assessment CTA earlier)
 *   3. Snapshot interview.positionName (last interview's position)
 * If none of the above we leave the picker empty so the CTA gates on a manual choice.
 */
const applyPrefill = async () => {
  const pages = getCurrentPages();
  const opts = (pages[pages.length - 1] as any).options || {};
  const fromQuery = opts.suggestedRole ? decodeURIComponent(opts.suggestedRole) : '';

  if (fromQuery) {
    const idx = ensurePositionExists(fromQuery);
    selectedPositionIndex.value = idx;
    selectedPosition.value = positions.value[idx];
    prefillSource.value = t('interview.fromAssessment');
    return;
  }

  try {
    const snap = await getProfileSnapshotApi();
    const targetRole = snap?.preferences?.targetRole;
    const lastPos = snap?.interview?.positionName;
    const lastMode = snap?.preferences?.interviewMode;

    if (targetRole) {
      const idx = ensurePositionExists(targetRole);
      selectedPositionIndex.value = idx;
      selectedPosition.value = positions.value[idx];
      prefillSource.value = snap?.assessment ? t('interview.fromAssessment') : t('interview.recentlyChosen');
    } else if (lastPos) {
      const idx = ensurePositionExists(lastPos);
      selectedPositionIndex.value = idx;
      selectedPosition.value = positions.value[idx];
      prefillSource.value = t('interview.fromLast');
    }

    // The mode prefilled on storage takes priority -- only fall back to the
    // server snapshot when local storage was empty (fresh device).
    const localMode = uni.getStorageSync('interview_mode') as 'voice' | 'text' | '';
    if (!localMode && (lastMode === 'voice' || lastMode === 'text')) {
      selectedMode.value = lastMode;
    }
  } catch {
    // Snapshot is best-effort -- if it blows up we just leave the form empty.
  }
};

onMounted(() => {
  refreshTheme();
  const safeMetrics = getMpSafeAreaMetrics();
  topSafeHeight.value = safeMetrics.topSafeHeight;
  rightAvoidWidth.value = safeMetrics.rightAvoidWidth;
  applyPrefill();
});

const goBack = () => {
  uni.navigateBack({ delta: 1 });
};

onShow(() => {
  // A cached start page becomes active again after the interview page is
  // popped; re-enable the CTA for the next intentional session.
  loading.value = false;
  refreshTheme();
  uni.setNavigationBarTitle({ title: t('interview.startPageTitle') });
});

const startInterview = async () => {
  if (loading.value) return;
  if (!selectedPosition.value) {
    uni.showToast({ title: t('interview.chooseFirst'), icon: 'none' });
    return;
  }

  const userId = uni.getStorageSync('userId');
  const numericId = Number(userId);
  if (!userId || isNaN(numericId) || numericId <= 0) {
    uni.showToast({ title: t('map.toastSignIn'), icon: 'none' });
    setTimeout(() => {
      uni.reLaunch({ url: LOGIN_PAGE });
    }, 600);
    return;
  }

  loading.value = true;
  try {
    // userId is resolved server-side from the JWT; don't send it in the body.
    // Persist the modality on the interview row so History can resume in the
    // *same* mode the candidate originally chose.
    const interview = await startInterviewApi({
      positionName: selectedPosition.value,
      difficulty: selectedDifficulty.value,
      mode: selectedMode.value === 'voice' ? 'VOICE' : 'TEXT',
    });

    uni.setStorageSync('interview_mode', selectedMode.value);
    // Persist current UI language so greeting/message/voice APIs can match it
    uni.setStorageSync('interview_language', currentLocale() === 'zh-CN' ? 'zh' : 'en');
    // Mirror the choice into the cross-tool snapshot so the assistant
    // and the next session both know the user's preferred mode. Best-effort.
    updatePreferencesApi({
      targetRole: selectedPosition.value,
      interviewMode: selectedMode.value,
    }).catch(() => { /* snapshot writes are non-blocking */ });

    const targetPath = selectedMode.value === 'voice'
      ? `/pages/interview/room?interviewId=${interview.interviewId}`
      : `/pages/interview/chat?interviewId=${interview.interviewId}`;
    uni.showToast({ title: t('interview.start'), icon: 'success' });
    await new Promise<void>((resolve, reject) => {
      uni.navigateTo({
        url: targetPath,
        success: () => resolve(),
        fail: (error) => reject(error),
      });
    });
  } catch (error: any) {
    console.error(error);
    uni.showToast({ title: error?.message || t('interview.startFailed'), icon: 'none' });
  } finally {
    loading.value = false;
  }
};
</script>

<style scoped>
.start-page {
  min-height: 100vh;
  /* Reserve space for the sticky CTA so content never sits under it */
  font-family: -apple-system, BlinkMacSystemFont, "SF Pro Text", "Helvetica Neue", sans-serif;
  box-sizing: border-box;
}

.start-content {
  padding: 24px var(--page-gutter, 20px) calc(120px + env(safe-area-inset-bottom, 0px));
  box-sizing: border-box;
}

.header {
  margin-bottom: 24px;
  padding: 26px 24px;
  border-radius: var(--radius-xl, 24px);
  position: relative;
  overflow: hidden;
}

.header-kicker {
  display: block;
  margin-bottom: 6px;
  color: rgba(255, 255, 255, 0.78);
  font-size: 10px;
  line-height: 1.3;
  font-weight: 800;
  letter-spacing: 0.06em;
}

.title {
  font-size: var(--font-hero, 28px);
  font-weight: 800;
  color: #ffffff;
  letter-spacing: -0.5px;
  display: block;
  margin-bottom: 8px;
}

.subtitle {
  font-size: var(--font-caption, 13px);
  color: rgba(255, 255, 255, 0.86);
  display: block;
  line-height: var(--line-height-caption, 1.45);
}

.form-card {
  border-radius: var(--radius-lg, 20px);
  padding: 22px 20px 8px;
  margin-bottom: 18px;
}

.form-item { margin-bottom: 22px; }
.form-item-last { margin-bottom: 14px; }

.label {
  font-size: 15px; font-weight: 700; color: var(--text-primary, #1c1917);
  margin-bottom: 12px; display: block;
}

/* Inline hint next to a label, e.g. "From your assessment" — explains the
   provenance of an auto-filled value so the prefill feels intentional rather
   than mysterious. */
.label-row {
  display: flex; align-items: baseline; justify-content: space-between;
  margin-bottom: 12px;
}
.label-row .label { margin-bottom: 0; }
.label-hint {
  font-size: 11px; font-weight: 600;
  color: #d37b58;
  background: #fbf4f2;
  padding: 3px 8px;
  border-radius: 999px;
  letter-spacing: 0.02em;
}

/* Mode picker — same affordance language as the difficulty cards so the
   form reads as a single coherent group. Voice is the hero so we reach
   for the brand gradient when active. */
.mode-grid { display: flex; gap: 10px; }
.mode-card {
  flex: 1;
  display: flex; flex-direction: column; gap: 8px;
  padding: 14px;
  border: 2px solid #e7e5e4; border-radius: 14px;
  background: #ffffff;
  transition: all 0.15s;
  min-height: 96px;
}
.mode-head { display: flex; align-items: center; gap: 8px; }
.mode-icon {
  width: 30px;
  height: 30px;
  border-radius: 12px;
  background: var(--primary-soft, #fcf5f2);
  color: var(--primary-color, #cd6a43);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  line-height: 30px;
  font-weight: 800;
}
.mode-name { font-size: 14px; font-weight: 700; color: var(--text-primary, #1c1917); }
.mode-badge {
  margin-left: auto;
  font-size: 9px; font-weight: 700;
  letter-spacing: 0.04em;
  background: #faf6ef; color: #5c4820;
  padding: 2px 6px; border-radius: 999px;
  text-transform: uppercase;
}
.mode-desc { font-size: 11.5px; color: var(--text-secondary, #78716c); line-height: 1.45; }

.mode-card.active {
  border-color: var(--primary-color, #cd6a43);
  background: var(--gradient-primary, linear-gradient(135deg, #cd6a43 0%, #d47f5d 100%));
}
.mode-card.active .mode-icon { background: rgba(255, 255, 255, 0.24); color: #ffffff; }
.mode-card.active .mode-name { color: #ffffff; }
.mode-card.active .mode-desc { color: rgba(255, 255, 255, 0.85); }
.mode-card.active .mode-badge { background: rgba(255, 255, 255, 0.22); color: #ffffff; }
.mode-card:active { transform: scale(0.97); }

/* Position picker -- clearer empty/filled affordance */
.picker-box {
  display: flex; align-items: center; justify-content: space-between;
  border: 1.5px solid #e7e5e4; border-radius: 14px;
  padding: 14px 16px; background: var(--surface-2, #fafaf9);
  min-height: 52px; box-sizing: border-box;
  transition: border-color 0.15s, background 0.15s;
}
.picker-box:active { border-color: var(--primary-color, #cd6a43); background: var(--primary-soft, #fcf5f2); }
.picker-filled { border-color: #f3dbd2; background: #ffffff; }

.picker-val { font-size: 15px; color: var(--text-tertiary, #8e8e93); flex: 1; }
.has-val { color: var(--text-primary, #1c1917); font-weight: 600; }

.picker-arrow { font-size: 20px; color: #c7c7cc; line-height: 1; flex-shrink: 0; }

/* Difficulty cards now show name AND description so the choice is informed
   (HCI: recognition over recall, match real-world expectations) */
.difficulty-grid { display: flex; gap: 10px; }

.diff-card {
  flex: 1;
  display: flex; flex-direction: column; align-items: flex-start;
  gap: 4px;
  padding: 12px;
  border: 2px solid #e7e5e4; border-radius: 14px;
  background: #ffffff;
  transition: all 0.15s;
  min-height: 72px;
}

.diff-name { font-size: 14px; font-weight: 700; color: var(--text-primary, #1c1917); }
.diff-desc { font-size: 11px; color: var(--text-secondary, #78716c); line-height: 1.35; }

.diff-card.active {
  border-color: var(--primary-color, #cd6a43);
  background: var(--gradient-primary, linear-gradient(135deg, #cd6a43 0%, #d47f5d 100%));
}
.diff-card.active .diff-name { color: #ffffff; }
.diff-card.active .diff-desc { color: rgba(255, 255, 255, 0.85); }

.diff-card:active { transform: scale(0.97); }

/* What-to-expect card */
.expect-card {
  background: #ffffff;
  border: 1px solid var(--border-color, #d8c1b8);
  border-radius: var(--radius-lg, 20px);
  padding: 20px;
  display: flex; flex-direction: column; gap: 16px;
}

.expect-title {
  font-size: 12px; font-weight: 700;
  color: var(--text-secondary, #78716c);
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.expect-row { display: flex; gap: 14px; align-items: flex-start; }
.expect-icon {
  width: 32px; height: 32px; flex-shrink: 0;
  background: var(--primary-soft, #fcf5f2); border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
  font-size: 11px;
  line-height: 32px;
  font-weight: 800;
  letter-spacing: 0.02em;
}
.expect-icon-time { background: var(--primary-soft, #fcf5f2); color: var(--primary-color, #cd6a43); }
.expect-icon-chat { background: var(--violet-soft, #fcf7f6); color: var(--violet, #db9377); }
.expect-icon-score { background: var(--success-soft, #f2ead9); color: var(--success-color, #957534); }
.expect-body { flex: 1; display: flex; flex-direction: column; gap: 2px; }
.expect-h { font-size: 14px; font-weight: 700; color: var(--text-primary, #1c1917); }
.expect-p { font-size: 12px; color: var(--text-secondary, #78716c); line-height: 1.45; }

/* Sticky CTA -- pinned to the bottom thumb zone (HCI: Fitts's law) */
.sticky-cta {
  position: fixed;
  left: 0; right: 0; bottom: 0;
  padding: 12px 20px calc(12px + env(safe-area-inset-bottom, 0px));
  background: rgba(245, 245, 247, 0.94);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-top: 0.5px solid rgba(60, 60, 67, 0.1);
  z-index: 10;
}

/* Custom-rendered button (just a styled <view>) -- guaranteed colour fidelity
   on mp-weixin where native <button> defaults are aggressive.
   We hardcode colours here rather than using CSS vars to guarantee contrast
   is never accidentally broken by a variable resolution issue. */
.btn-primary {
  height: 52px;
  border-radius: 16px;
  background: var(--primary-color, #cd6a43);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--shadow-card);
  transition: background 0.15s, opacity 0.15s;
}

.btn-primary-label {
  color: #ffffff;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0.01em;
}

.btn-primary:active { background: #c25c33; }

/* Disabled = no position selected. Use a mid-grey background with dark text
   so "Choose a position to begin" is legible — white-on-lightgrey fails WCAG. */
.btn-disabled {
  background: var(--surface-3, #f5f5f4);
  box-shadow: none;
}
.btn-disabled .btn-primary-label {
  color: var(--text-secondary, #78716c);
  font-weight: 600;
}

/* Dark mode */
.is-dark { background-color: var(--bg-color, #1c1917); }

.is-dark .title,
.is-dark .label,
.is-dark .has-val,
.is-dark .expect-h,
.is-dark .diff-name { color: #fafaf9; }

.is-dark .form-card,
.is-dark .expect-card { background: #292524; border-color: #44403c; box-shadow: none; }

.is-dark .picker-box { border-color: #44403c; background: #1c1917; }
.is-dark .diff-card:not(.active) { border-color: #44403c; background: #1c1917; }
.is-dark .mode-card:not(.active) { border-color: #44403c; background: #1c1917; }
.is-dark .mode-name { color: #fafaf9; }

.is-dark .picker-val { color: var(--text-secondary, #78716c); }
.is-dark .label-hint {
  background: rgba(219, 149, 121, 0.2);
  border: 1px solid rgba(227, 172, 150, 0.36);
  color: #f3dbd2;
}

.is-dark .expect-icon { background: rgba(205, 106, 67, 0.2); }

.is-dark .sticky-cta {
  background: rgba(28, 25, 23, 0.92);
  border-color: #44403c;
}

.is-dark .btn-disabled {
  background: #292524;
}
.is-dark .btn-disabled .btn-primary-label {
  color: var(--text-tertiary, #8e8e93);
}

/* ================================================================
 *  MP-WEIXIN parity overrides (scoped to interview/start page)
 * ================================================================ */
/* #ifdef MP-WEIXIN */

/* Sticky CTA: backdrop-filter not supported — use solid background */
.sticky-cta {
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
  background: var(--surface-1, #ffffff);
}

/* Mode cards and diff cards: overflow:visible for visible shadows */
.mode-card,
.diff-card,
.expect-card {
  overflow: visible;
}

.start-page.is-dark .sticky-cta {
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
  background: rgba(28, 25, 23, 0.96);
  border-color: #44403c;
}

/* #endif */

/* Competition visual system ------------------------------------------------ */
.start-page {
  color: #2c2b29;
  background: #faf9f6;
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
}

.start-content {
  padding-top: 18px;
}

.header {
  padding: 24px 20px;
  border: 1px solid #e0dfdb;
  border-top: 1px solid var(--border-color, #e0dfdb);
  border-radius: 6px;
  background: #f5f5f0;
  box-shadow: 0 8px 24px rgba(44, 43, 41, 0.05);
}

.header-kicker {
  color: #c23b22;
  font-weight: 600;
  letter-spacing: 0.12em;
}

.title {
  color: #2c2b29;
  font-family: "Songti SC", STSong, serif;
  font-weight: 600;
  letter-spacing: 0.06em;
}

.subtitle {
  color: #5a5956;
}

.form-card,
.expect-card {
  background: #ffffff;
  border: 1px solid #e0dfdb;
  border-radius: 8px;
  box-shadow: 0 6px 18px rgba(44, 43, 41, 0.04);
}

.label,
.expect-title {
  color: #2c2b29;
  font-family: "Songti SC", STSong, serif;
  font-weight: 600;
  letter-spacing: 0.05em;
}

.label-hint {
  color: #ac6448;
  background: transparent;
  border: 1px solid rgba(172, 100, 72, 0.32);
  border-radius: 4px;
}

.mode-card,
.diff-card {
  color: #2c2b29;
  background: #faf9f6;
  border: 1px solid #e0dfdb;
  border-radius: 6px;
}

.mode-icon,
.expect-icon {
  color: #ac6448;
  background: #efeee9;
  border-radius: 4px;
}

.mode-badge {
  color: #947f54;
  background: transparent;
  border: 1px solid rgba(148, 127, 84, 0.4);
  border-radius: 3px;
}

.mode-name,
.diff-name,
.expect-h {
  color: #2c2b29;
  font-weight: 600;
}

.mode-desc,
.diff-desc,
.expect-p {
  color: #5a5956;
}

.mode-card.active,
.diff-card.active {
  color: #faf9f6;
  background: #ac6448;
  border-color: #ac6448;
}

.mode-card.active .mode-icon {
  background: rgba(250, 249, 246, 0.16);
}

.picker-box {
  background: #faf9f6;
  border: 1px solid #d8d7d2;
  border-radius: 6px;
}

.picker-box:active,
.picker-filled {
  background: #ffffff;
  border-color: #ac6448;
}

.expect-icon-chat {
  color: #b8975a;
}

.expect-icon-score {
  color: #947f54;
}

.sticky-cta {
  background: #faf9f6;
  border-top: 1px solid #e0dfdb;
}

.btn-primary {
  height: 50px;
  background: #c23b22;
  border-radius: 6px;
  box-shadow: none;
}

.btn-primary:active {
  background: #a8311d;
}

.btn-disabled {
  background: #efeee9;
  border: 1px solid #e0dfdb;
}

.btn-primary-label {
  color: #faf9f6;
  font-family: "Songti SC", STSong, serif;
  font-weight: 600;
  letter-spacing: 0.06em;
}

.start-page.is-dark .header,
.start-page.is-dark .form-card,
.start-page.is-dark .expect-card {
  background: #242320;
  border-color: #494844;
}

.start-page.is-dark .title,
.start-page.is-dark .label,
.start-page.is-dark .expect-title {
  color: #faf9f6;
}

.start-page.is-dark {
  background: #1c1b19;
}

.start-page.is-dark .mode-card:not(.active),
.start-page.is-dark .diff-card:not(.active),
.start-page.is-dark .picker-box {
  color: #faf9f6;
  background: #1c1b19;
  border-color: #494844;
}

.start-page.is-dark .mode-name,
.start-page.is-dark .diff-name,
.start-page.is-dark .expect-h,
.start-page.is-dark .has-val {
  color: #faf9f6;
}

.start-page.is-dark .mode-desc,
.start-page.is-dark .diff-desc,
.start-page.is-dark .expect-p {
  color: #c6c4be;
}

.start-page.is-dark .sticky-cta {
  background: #1c1b19;
  border-color: #494844;
}

.start-page.is-dark .mode-card:not(.active) .mode-icon,
.start-page.is-dark .expect-icon {
  background: #33332f;
  color: #e5beaf;
}
</style>
