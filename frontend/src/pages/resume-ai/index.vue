<template>
  <SlPage class="resume-ai-page app-soft-bg" :custom-class="['resume-ai-page', themeClass, fontClass].join(' ')">
    <SlNavBar :title="t('resumeAi.title')" show-back @back="goBack" :safe-top="topSafeHeight" />

    <view class="resume-ai-content">
      <view class="header app-page-header">
        <text class="subtitle app-page-subtitle">{{ t('resumeAi.subtitle') }}</text>
      </view>

      <view class="analysis-card app-card-soft app-surface">
        <view class="section">
          <text class="section-title">{{ t('resumeAi.resumeLabel') }}</text>
          <view class="select-box ui-list-item" :class="{ 'has-value': !!selectedResume }" @click="selectResume">
            <view class="s-icon-wrap"><text class="s-icon-text">PDF</text></view>
            <text class="s-text">{{ selectedResume || t('resumeAi.chooseResume') }}</text>
            <text class="s-chevron">›</text>
          </view>
        </view>

        <!-- 测评推荐岗位 banner（有测评结果时才显示） -->
        <view class="assessment-banner" v-if="assessmentRoles.length > 0">
          <text class="ab-title">{{ t('resumeAi.assessmentBannerTitle') }}</text>
          <text class="ab-body">{{ t('resumeAi.assessmentBannerBody') }}</text>
          <view class="ab-chips">
            <view
              class="ab-chip"
              v-for="role in assessmentRoles"
              :key="role"
            >
              <text class="ab-chip-text">{{ role }}</text>
            </view>
          </view>
        </view>

        <view class="section">
          <view class="jd-header">
            <text class="section-title">{{ t('resumeAi.jdLabel') }}</text>
            <text class="jd-counter" :class="{ 'jd-counter-warn': jdText.length > 4000 }">
              {{ jdText.length }} / 4000
            </text>
          </view>
          <textarea
            class="jd-input ui-input"
            v-model="jdText"
            :maxlength="4000"
            :placeholder="t('resumeAi.jdPlaceholder')"
            placeholder-class="ph"
          ></textarea>
        </view>

        <button class="btn-primary" :loading="analyzing" @click="startAnalysis">
          {{ t('resumeAi.analyzeBtn') }}
        </button>
      </view>
    </view>

    <!-- Loading overlay (shared by Analyze and Tailor flows) -->
    <view class="loading-overlay" v-if="analyzing || tailoring">
      <view class="spinner"></view>
      <text class="loading-text">{{ loadingMessage }}</text>
      <view class="progress-bar-container">
        <view class="progress-bar-fill" :style="{ width: loadingProgress + '%' }"></view>
      </view>
    </view>

    <view class="resume-ai-content">
      <view class="result-card app-surface" v-if="showResult && result">
        <view class="r-header">
          <view class="r-title-wrap">
            <text class="r-title">{{ t('resumeAi.matchScore') }}</text>
            <text class="r-sub">{{ t('resumeAi.vsJobDesc') }}</text>
          </view>
          <view class="score-ring" :class="scoreClass">
            <text class="score-val">{{ result.overallScore }}</text>
          </view>
        </view>
        <view class="score-basis">
          <text class="score-basis-title">评分依据</text>
          <text class="score-basis-text">简历分由 AI 根据简历与目标岗位/JD 的匹配度评估，重点看岗位关键词、项目证据、能力表达清晰度和经历相关性；系统不会编造未提供的项目或成果。</text>
        </view>

        <view class="r-body">
          <view class="point-block strengths" v-if="result.strengths && result.strengths.length">
            <text class="point-title">{{ t('resumeAi.strengths') }}</text>
            <view class="point-list">
              <text class="point-text" v-for="(s, i) in result.strengths" :key="'s'+i">{{ s }}</text>
            </view>
          </view>

          <view class="point-block weaknesses" v-if="result.weaknesses && result.weaknesses.length">
            <text class="point-title">{{ t('resumeAi.weaknesses') }}</text>
            <view class="point-list">
              <text class="point-text" v-for="(w, i) in result.weaknesses" :key="'w'+i">{{ w }}</text>
            </view>
          </view>

          <view class="point-block suggestions" v-if="result.suggestions && result.suggestions.length">
            <text class="point-title">{{ t('resumeAi.suggestions') }}</text>
            <view class="point-list">
              <text class="point-text" v-for="(g, i) in result.suggestions" :key="'g'+i">{{ g }}</text>
            </view>
          </view>
        </view>

        <!-- 按 JD 优化简历按钮（成功后隐藏，换成结果卡） -->
        <button
          v-if="!tailorResult"
          class="btn-secondary"
          :loading="tailoring"
          @click="generateTailored"
        >{{ t('resumeAi.tailorBtn') }}</button>

        <!-- 优化成功结果卡 -->
        <view v-else class="tailor-success-card">
          <text class="ts-icon ri-check-line"></text>
          <text class="ts-title">{{ t('resumeAi.tailorSuccessTitle') }}</text>
          <text class="ts-hint">{{ tailorResult.resume.title }}</text>
          <text class="ts-sub">{{ tailorResult.changeSummary || t('resumeAi.tailorSuccessHint') }}</text>
          <view class="change-list" v-if="tailorResult.changeItems?.length">
            <text class="change-title">{{ t('resumeAi.tailorChangesTitle') }}</text>
            <view class="change-item" v-for="(item, idx) in tailorResult.changeItems" :key="idx">
              <text class="change-index">{{ idx + 1 }}</text>
              <text class="change-text">{{ item }}</text>
            </view>
          </view>
          <view class="ts-actions">
            <button class="ts-btn-primary" :loading="openingPdf" @click="viewTailoredPdf">
              {{ t('resumeAi.viewPdf') }}
            </button>
            <button class="ts-btn-secondary" @click="gotoResumes">
              {{ t('resumeAi.gotoResumes') }}
            </button>
          </view>
        </view>
      </view>
    </view>
    <SlActionSheet
      v-model:visible="showResumeSheet"
      title="选择用于分析的简历"
      :options="resumeOptions"
      :selected-value="selectedResumeId ? String(selectedResumeId) : ''"
      @select="onResumeSelect"
    />
  </SlPage>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useI18n } from '@/locales';
import { onShow } from '@dcloudio/uni-app';
import { getMpSafeAreaMetrics } from '@/utils/safeArea';
import {
  listMyResumesApi,
  diagnoseResumeApi,
  tailorResumeApi,
  type Resume,
  type DiagnosisResult,
  type TailorResumeResponse,
} from '@/api/resume';
import { getProfileSnapshotApi } from '@/api/user';
import { getAgentProfileApi } from '@/api/agent';
import { normalizeRoleLabel } from '@/utils/displayText';
import { useTheme } from '@/utils/theme';
import { requireAuth } from '@/utils/auth';
import SlPage from '@/style-library/components/SlPage.vue';
import SlNavBar from '@/style-library/components/SlNavBar.vue';
import SlActionSheet from '@/style-library/components/SlActionSheet.vue';

const selectedResume = ref('');
const selectedResumeId = ref<number | null>(null);
const showResumeSheet = ref(false);
const userResumes = ref<Resume[]>([]);
const jdText = ref('');
const { t } = useI18n();
const { themeClass, fontClass, refresh: refreshTheme } = useTheme();
const topSafeHeight = ref(44);
const analyzing = ref(false);
const tailoring = ref(false);
const openingPdf = ref(false);
const showResult = ref(false);
const result = ref<DiagnosisResult | null>(null);
const tailorResult = ref<TailorResumeResponse | null>(null);
const loadingMessage = ref('');
const loadingProgress = ref(0);

const API_BASE = import.meta.env.VITE_API_BASE_URL || 'https://api.careerloop.top';

const scoreClass = computed(() => {
  const s = result.value?.overallScore ?? 0;
  if (s >= 80) return 'ring-good';
  if (s >= 60) return 'ring-warn';
  return 'ring-bad';
});

const resumeOptions = computed(() => userResumes.value.map((r) => {
  const label = r.title || r.fileUrl?.split('/').pop() || `Resume #${r.resumeId}`;
  return {
    label,
    value: String(r.resumeId || ''),
    subtitle: r.targetJob ? `目标岗位：${r.targetJob}` : '用于生成匹配分、短板和定制简历',
    icon: 'ri-file-text-line',
  };
}));

const goBack = () => {
  uni.navigateBack({ delta: 1 });
};

const loadResumes = async () => {
  if (!uni.getStorageSync('token')) return;
  try {
    const raw = await listMyResumesApi();
    userResumes.value = Array.isArray(raw) ? raw : [];
  } catch {
    userResumes.value = [];
  }
};

/**
 * Auto-pick the user's most recent resume so they don't have to tap into
 * the picker on every visit. Only applies when nothing is selected yet —
 * if the user explicitly chose a different one, we leave that alone.
 * Also drops a JD placeholder hint based on their assessment-suggested role
 * so the empty textarea isn't a blank wall to fill.
 */
const applyPrefill = async () => {
  jdPlaceholder.value = t('resumeAi.jdPlaceholder');
  try {
    // Fetch user snapshot AND the AI agent profile in parallel — the agent
    // profile is the source of truth for "AI's understanding of the user"
    // (it merges preferences / resume / interview / assessment with a
    // confidence score), so we want it to drive the banner instead of the
    // raw assessment suggestion list.
    const [snapRes, agentRes] = await Promise.allSettled([
      getProfileSnapshotApi(),
      getAgentProfileApi(),
    ]);
    const snap = snapRes.status === 'fulfilled' ? snapRes.value : null;
    const agentProfile = agentRes.status === 'fulfilled' ? agentRes.value : null;

    // 预选上次使用的简历
    const lastResumeId = snap?.resume?.lastResumeId;
    if (lastResumeId && !selectedResumeId.value) {
      const match = userResumes.value.find((r) => r.resumeId === lastResumeId);
      if (match) {
        selectedResumeId.value = match.resumeId!;
        selectedResume.value = match.title || `Resume #${match.resumeId}`;
      }
    }

    // Build the recommendation list in confidence order:
    //   1. AI agent's primary target role  (highest confidence — this is
    //      "what the AI thinks you're aiming at", merging every signal)
    //   2. Whatever the user explicitly set as a preference
    //   3. The target job baked into their resume
    //   4. Roles the personality assessment suggested  (lowest confidence —
    //      these were ranked first before but felt random to users, so they
    //      become a fallback now rather than the primary source)
    // Each label is run through normalizeRoleLabel so anything the LLM
    // returned in English ("Frontend Engineer") becomes Chinese ("前端工程师").
    const roles: string[] = [];
    const pushRole = (raw?: string | null) => {
      const label = normalizeRoleLabel(raw || '');
      if (label) roles.push(label);
    };
    pushRole(agentProfile?.target?.role);
    pushRole(snap?.preferences?.targetRole);
    pushRole(snap?.resume?.targetJob);
    (snap?.assessment?.suggestedRoles || []).forEach(pushRole);

    // Stable dedupe (preserve confidence-ordering above), top 3
    assessmentRoles.value = Array.from(new Set(roles)).slice(0, 3);
  } catch {
    // Snapshot / agent profile are best-effort enrichment for the banner —
    // a network blip should never prevent the user from analyzing their
    // resume, so we just swallow.
  }
};

const jdPlaceholder = ref('');
// 测评推荐岗位列表（非空时展示预填充 banner）
const assessmentRoles = ref<string[]>([]);

const selectResume = () => {
  if (!userResumes.value.length) {
    uni.showToast({ title: t('resume.noResumes'), icon: 'none' });
    return;
  }
  showResumeSheet.value = true;
};

const onResumeSelect = ({ value }: { value: string }) => {
  const r = userResumes.value.find((item) => String(item.resumeId || '') === value);
  if (r && r.resumeId) {
    selectedResume.value = r.title || r.fileUrl?.split('/').pop() || `Resume #${r.resumeId}`;
    selectedResumeId.value = r.resumeId;
  }
};

let progressTimers: number[] = [];
const clearProgressTimers = () => {
  progressTimers.forEach((t) => clearTimeout(t));
  progressTimers = [];
};

const runProgressAnimation = () => {
  loadingProgress.value = 0;
  loadingMessage.value = t('resumeAi.progressConnecting');
  clearProgressTimers();
  progressTimers = [
    setTimeout(() => { loadingMessage.value = t('resumeAi.progressParsing'); loadingProgress.value = 22; }, 400) as unknown as number,
    setTimeout(() => { loadingMessage.value = t('resumeAi.progressComparing'); loadingProgress.value = 55; }, 1500) as unknown as number,
    setTimeout(() => { loadingMessage.value = t('resumeAi.progressInsights'); loadingProgress.value = 82; }, 3500) as unknown as number,
  ];
};

// Tailoring is a longer pipeline (AI rewrite -> HTML -> PDF -> OSS upload).
// Total typical duration is 30-90s, so we crawl progress slowly and cap at
// 92% until the API actually returns, then snap to 100%.
const runTailorProgress = () => {
  loadingProgress.value = 0;
  loadingMessage.value = t('resumeAi.progressPrep');
  clearProgressTimers();
  const stages: Array<{ at: number; pct: number; msgKey: string }> = [
    { at: 800,    pct: 8,  msgKey: 'resumeAi.progressReading' },
    { at: 2500,   pct: 18, msgKey: 'resumeAi.progressExtracting' },
    { at: 5000,   pct: 32, msgKey: 'resumeAi.progressRewriting' },
    { at: 15000,  pct: 52, msgKey: 'resumeAi.progressRewriting' },
    { at: 30000,  pct: 70, msgKey: 'resumeAi.progressPolishing' },
    { at: 50000,  pct: 82, msgKey: 'resumeAi.progressRendering' },
    { at: 75000,  pct: 90, msgKey: 'resumeAi.progressUploading' },
    { at: 100000, pct: 92, msgKey: 'resumeAi.progressAlmost' },
  ];
  progressTimers = stages.map(
    (s) =>
      setTimeout(() => {
        loadingMessage.value = t(s.msgKey);
        loadingProgress.value = s.pct;
      }, s.at) as unknown as number
  );
};

const startAnalysis = async () => {
  if (!requireAuth({ message: '登录后才能使用 AI 简历诊断并保存分析结果。' })) return;
  if (!selectedResumeId.value) {
    uni.showToast({ title: t('resumeAi.selectResumeFirst'), icon: 'none' });
    return;
  }
  if (!jdText.value || !jdText.value.trim()) {
    uni.showToast({ title: t('resumeAi.pasteJdFirst'), icon: 'none' });
    return;
  }

  analyzing.value = true;
  showResult.value = false;
  result.value = null;
  runProgressAnimation();

  try {
    const res = await diagnoseResumeApi({
      resumeId: selectedResumeId.value,
      jobDescription: jdText.value.trim(),
    });
    loadingProgress.value = 100;
    loadingMessage.value = t('common.success');
    result.value = res;
    showResult.value = true;
    uni.showToast({ title: t('resumeAi.diagnosisComplete'), icon: 'success' });
  } catch (e: any) {
    uni.showToast({ title: t('resumeAi.diagnosisFailed'), icon: 'none' });
  } finally {
    progressTimers.forEach((t) => clearTimeout(t));
    analyzing.value = false;
  }
};

const generateTailored = async () => {
  if (!requireAuth({ message: '登录后才能生成并保存岗位定制简历。' })) return;
  if (!selectedResumeId.value) {
    uni.showToast({ title: t('resumeAi.selectResumeFirst'), icon: 'none' });
    return;
  }
  const userId = Number(uni.getStorageSync('userId'));
  if (!userId) {
    uni.showToast({ title: t('login.wechatLogin'), icon: 'none' });
    return;
  }
  tailoring.value = true;
  tailorResult.value = null;
  runTailorProgress();
  try {
    const res = await tailorResumeApi({
      userId,
      resumeId: selectedResumeId.value,
      jobDescription: jdText.value.trim(),
    });
    clearProgressTimers();
    loadingProgress.value = 100;
    loadingMessage.value = t('common.success');
    tailorResult.value = res;
  } catch (e: any) {
    uni.showToast({ title: t('resumeAi.tailorFailed'), icon: 'none' });
  } finally {
    clearProgressTimers();
    tailoring.value = false;
  }
};

const viewTailoredPdf = () => {
  const resumeId = tailorResult.value?.resume?.resumeId;
  if (!resumeId) return;
  const token = uni.getStorageSync('token');
  openingPdf.value = true;
  uni.showLoading({ title: t('resumeAi.openingPdf') });
  uni.downloadFile({
    url: `${API_BASE}/api/resumes/${resumeId}/download`,
    header: {
      'ngrok-skip-browser-warning': 'true',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    success: (dl) => {
      if (dl.statusCode === 200) {
        uni.openDocument({
          filePath: dl.tempFilePath,
          fileType: 'pdf',
          showMenu: true,
          success: () => { uni.hideLoading(); openingPdf.value = false; },
          fail: () => {
            uni.hideLoading();
            openingPdf.value = false;
            uni.showModal({
              title: t('resumeAi.pdfOpenFail'),
              content: t('resumeAi.pdfPreviewUnsupported'),
              showCancel: false,
            });
          },
        });
      } else {
        uni.hideLoading();
        openingPdf.value = false;
        uni.showToast({ title: t('resumeAi.downloadFailed', { status: dl.statusCode }), icon: 'none' });
      }
    },
    fail: () => {
      uni.hideLoading();
      openingPdf.value = false;
      uni.showToast({ title: t('resumeAi.tailorFailed'), icon: 'none' });
    },
  });
};

const gotoResumes = () => uni.switchTab({ url: '/pages/resume/index' });

onMounted(async () => {
  refreshTheme();
  topSafeHeight.value = getMpSafeAreaMetrics().topSafeHeight;
  if (!requireAuth({
    redirect: 'reLaunch',
    message: '登录后才能使用 AI 简历诊断和岗位定制。',
  })) return;
  await loadResumes();
  await applyPrefill();
});

onShow(() => {
  refreshTheme();
});
</script>

<style scoped>
.resume-ai-page {
  background: var(--surface-1, #ffffff);
}

.resume-ai-content {
  padding: 0 var(--page-gutter, 20px) 60px;
  box-sizing: border-box;
}

.header { margin-bottom: var(--space-xl, 20px); }

.title {
  font-size: 28px;
  font-weight: 800;
  color: var(--text-primary, #1c1917);
  letter-spacing: -0.5px;
  display: block;
  margin-bottom: 8px;
}

.subtitle {
  font-size: 14px;
  color: var(--text-secondary, #78716c);
  line-height: 1.6;
  display: block;
}

.section { margin-bottom: var(--space-lg, 16px); }

.analysis-card {
  margin-bottom: var(--space-2xl, 24px);
  padding: var(--space-xl, 20px);
  border-radius: var(--radius-xl, 24px);
  box-sizing: border-box;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary, #1c1917);
  margin-bottom: 12px;
  display: block;
}

.select-box {
  display: flex;
  align-items: center;
  padding: 16px;
  border-radius: var(--radius-md, 16px);
}

.select-box:active { background-color: #f5f5f4; }

.s-icon-wrap {
  width: 36px; height: 44px;
  border-radius: 8px;
  background: linear-gradient(135deg, #f7e8e2, #f1d6cc);
  display: flex; align-items: center; justify-content: center;
  margin-right: 12px; flex-shrink: 0;
}
.s-icon-text { font-size: 10px; font-weight: 800; color: var(--primary-color, #cd6a43); letter-spacing: 0.5px; }

.s-text {
  flex: 1;
  min-width: 0;
  font-size: 15px;
  color: var(--text-tertiary, #8e8e93);
  line-height: 1.45;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-right: 10px;
}
.select-box.has-value .s-text { color: var(--text-primary, #1c1917); font-weight: 500; }

.s-chevron {
  font-size: 22px;
  color: var(--text-secondary, #78716c);
  font-weight: 400;
  line-height: 1;
  flex-shrink: 0;
}

/* Header row with the live character counter on the right.
   Helps the user judge if they've pasted enough JD context for a useful diagnosis. */
.jd-header {
  display: flex; align-items: baseline; justify-content: space-between;
  margin-bottom: 8px;
}
.jd-counter {
  font-size: 11px; font-weight: 600;
  color: var(--text-tertiary, #8e8e93);
  font-variant-numeric: tabular-nums;
}
.jd-counter-warn { color: #f59e0b; }

.jd-input {
  width: 100%;
  height: 160px;
  border-radius: var(--radius-md, 16px);
  padding: 16px;
  box-sizing: border-box;
  font-size: 15px;
  color: var(--text-secondary, #78716c);
  border: 1px solid var(--border-color, #d8c1b8);
  line-height: 1.5;
}

.ph { color: var(--text-tertiary, #8e8e93); }

/* WeChat <button> defaults to white bg with a ::after pseudo border.
   Use solid hex (gradients on button are unreliable on mp-weixin) and
   reset the ::after border so our background actually shows through. */
.btn-primary {
  background-color: var(--primary-color, #cd6a43) !important;
  background: linear-gradient(135deg, #cd6a43 0%, #c25c33 100%) !important;
  color: #ffffff !important;
  font-size: 16px;
  font-weight: 600;
  border-radius: var(--btn-radius, 14px);
  height: 52px;
  line-height: 52px;
  border: none;
  box-shadow: none;
  margin-top: 4px;
}
.btn-primary::after { border: none; }
.btn-primary[disabled] { background-color: #a8a29e !important; color: #ffffff !important; }
.btn-primary:active { opacity: 0.92; }

.result-card {
  background: #ffffff;
  border-radius: 20px;
  padding: 22px 18px;
  border: 1px solid var(--border-color, #d8c1b8);
  box-shadow: var(--shadow-sm, 0 4px 16px rgba(0,0,0,0.12), 0 2px 6px rgba(0,0,0,0.08));
}

.r-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 18px;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--surface-3, #f5f5f4);
}

.r-title-wrap { display: flex; flex-direction: column; gap: 2px; }
.r-title { font-size: 18px; font-weight: 700; color: var(--text-primary, #1c1917); }
.r-sub { font-size: 12px; color: var(--text-tertiary, #8e8e93); }

.score-ring {
  width: 76px; height: 76px;
  border-radius: 38px;
  border: 5px solid #957534;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #faf6ef;
  flex-shrink: 0;
}
.score-ring.ring-good { border-color: #957534; background: #faf6ef; }
.score-ring.ring-good .score-val { color: #5c4820; }
.score-ring.ring-warn { border-color: #f59e0b; background: #fffbeb; }
.score-ring.ring-warn .score-val { color: #b45309; }
.score-ring.ring-bad  { border-color: #ef4444; background: #fef2f2; }
.score-ring.ring-bad  .score-val { color: #b91c1c; }

.score-val { font-size: 26px; font-weight: 800; line-height: 1; }

.score-basis {
  margin: -4px 0 18px;
  padding: 11px 12px;
  border-radius: 12px;
  background: var(--surface-2, #fafaf9);
  border: 1px solid var(--border-color, #e7e5e4);
}
.score-basis-title {
  display: block;
  font-size: 12px;
  font-weight: 900;
  color: var(--primary-color, #cd6a43);
  margin-bottom: 5px;
}
.score-basis-text {
  display: block;
  font-size: 12px;
  line-height: 1.5;
  color: var(--text-secondary, #78716c);
}

.r-body { margin-bottom: 20px; display: flex; flex-direction: column; gap: 14px; }

.point-block {
  border-left: 3px solid #d6d3d1;
  padding: 4px 0 4px 12px;
}
.point-block.strengths { border-left-color: #957534; }
.point-block.weaknesses { border-left-color: #ef4444; }
.point-block.suggestions { border-left-color: #db9579; }

.point-title { font-size: 13px; font-weight: 700; letter-spacing: 0.4px; text-transform: uppercase; color: #57534e; margin-bottom: 8px; display: block; }
.point-block.strengths .point-title { color: #5c4820; }
.point-block.weaknesses .point-title { color: #b91c1c; }
.point-block.suggestions .point-title { color: #bf6643; }

.point-list { display: flex; flex-direction: column; gap: 6px; }
.point-text { font-size: 14px; color: var(--text-secondary, #78716c); line-height: 1.55; display: block; }

.btn-secondary {
  background-color: var(--primary-soft, #fcf5f2) !important;
  color: var(--primary-color, #cd6a43) !important;
  font-size: 15px;
  font-weight: 600;
  border-radius: var(--btn-radius, 14px);
  height: 48px;
  line-height: 48px;
  border: none;
}

.btn-secondary::after { border: none; }
.btn-secondary:active { background-color: #f7e8e2 !important; }

/* 测评推荐岗位 banner */
.assessment-banner {
  background: linear-gradient(135deg, #fcf5f2, #fcf7f6);
  border: 1px solid #f3dbd2;
  border-radius: var(--btn-radius, 14px);
  padding: 14px 16px;
  margin-bottom: 16px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.ab-title { font-size: 13px; font-weight: 700; color: #bf6643; }
.ab-body  { font-size: 12px; color: #db9579; }
.ab-chips { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 6px; }
.ab-chip {
  background: #ffffff;
  border: 1px solid #ebc5b6;
  border-radius: 999px;
  padding: 4px 12px;
}
.ab-chip-text { font-size: 13px; font-weight: 600; color: #bf6643; }

.is-dark .assessment-banner {
  background: linear-gradient(135deg, rgba(191, 102, 67,0.15), rgba(219, 149, 121,0.1));
  border-color: rgba(219, 149, 121,0.4);
}
.is-dark .ab-title { color: #ebc5b6; }
.is-dark .ab-body  { color: #e3ac96; }
.is-dark .ab-chip  { background: rgba(219, 149, 121,0.15); border-color: rgba(219, 149, 121,0.4); }
.is-dark .ab-chip-text { color: #ebc5b6; }

/* 优化简历成功卡片 */
.tailor-success-card {
  margin-top: 16px;
  background: linear-gradient(135deg, #fbf8f2, #f5efe3);
  border: 1.5px solid #dbc59a;
  border-radius: 20px;
  padding: 24px 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}
.ts-icon  { font-size: 36px; }
.ts-title { font-size: 18px; font-weight: 800; color: #6e5627; }
.ts-hint  { font-size: 14px; font-weight: 600; color: #5b4720; }
.ts-sub   { font-size: 12px; color: #c7a561; text-align: center; line-height: 1.5; }
.change-list {
  width: 100%;
  margin-top: 8px;
  padding: 12px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.72);
  box-sizing: border-box;
}
.change-title {
  display: block;
  font-size: 13px;
  font-weight: 900;
  color: #5b4720;
  margin-bottom: 8px;
}
.change-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-top: 8px;
}
.change-index {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  border-radius: 10px;
  background: #896b30;
  color: #ffffff;
  font-size: 11px;
  font-weight: 900;
  line-height: 20px;
  text-align: center;
}
.change-text {
  flex: 1;
  font-size: 12px;
  line-height: 1.5;
  color: #5b4720;
}
.ts-actions {
  display: flex; flex-direction: column; gap: 10px;
  width: 100%; margin-top: 8px;
}
.ts-btn-primary {
  background-color: #896b30 !important;
  color: #ffffff !important;
  font-size: 15px; font-weight: 700;
  border-radius: var(--btn-radius, 14px); height: 48px; line-height: 48px;
  border: none; box-shadow: var(--shadow-card);
}
.ts-btn-primary::after { border: none; }
.ts-btn-primary:active { opacity: 0.88; }
.ts-btn-secondary {
  background-color: rgba(137, 107, 48,0.1) !important;
  color: #896b30 !important;
  font-size: 14px; font-weight: 600;
  border-radius: var(--btn-radius, 14px); height: 44px; line-height: 44px;
  border: none;
}
.ts-btn-secondary::after { border: none; }
.ts-btn-secondary:active { background-color: rgba(137, 107, 48,0.2) !important; }

.is-dark .tailor-success-card {
  background: linear-gradient(135deg, #261e0d, #4b3b1c);
  border-color: #5b4720;
}
.is-dark .ts-title { color: #c7a561; }
.is-dark .ts-hint  { color: #dbc59a; }
.is-dark .ts-sub   { color: #5b4720; }
.is-dark .change-list { background: rgba(38, 30, 13, 0.8); }
.is-dark .change-title,
.is-dark .change-text { color: #ebdfc7; }

.loading-overlay {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(255,255,255,0.9);
  backdrop-filter: blur(4px);
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  z-index: 1000;
}

.spinner {
  width: 50px;
  height: 50px;
  border: 4px solid var(--primary-soft, #fcf5f2);
  border-top: 1px solid var(--border-color, #e0dfdb);
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin-bottom: 20px;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.loading-text { font-size: 16px; font-weight: 600; color: var(--text-primary, #1c1917); }

.progress-bar-container {
  width: 220px;
  height: 6px;
  background-color: #e7e5e4;
  border-radius: 3px;
  margin-top: 16px;
  overflow: hidden;
}

.progress-bar-fill {
  height: 100%;
  background-color: var(--primary-color, #cd6a43);
  border-radius: 3px;
  transition: width 0.4s cubic-bezier(0.25, 0.8, 0.25, 1);
}

/* Dark mode */

.is-dark .title,
.is-dark .section-title,
.is-dark .r-title,
.is-dark .point-title,
.is-dark .loading-text { color: #fafaf9; }

.is-dark .subtitle,
.is-dark .point-text,
.is-dark .s-text { color: var(--text-tertiary, #8e8e93); }

.is-dark .select-box,
.is-dark .jd-input { background-color: transparent; border-color: var(--text-secondary, #78716c); }

.is-dark .loading-overlay { background: rgba(28, 25, 23, 0.88); }

.is-dark .score-ring { background-color: var(--bg-color, #1c1917); }
.is-dark .score-ring.ring-good { border-color: #957534; background: #221b0c; }
.is-dark .score-ring.ring-good .score-val { color: #bf9748; }
.is-dark .score-ring.ring-warn { border-color: #f59e0b; background: #451a03; }
.is-dark .score-ring.ring-warn .score-val { color: #fbbf24; }
.is-dark .score-ring.ring-bad { border-color: #ef4444; background: #450a0a; }
.is-dark .score-ring.ring-bad .score-val { color: #f87171; }
.is-dark .score-basis { background: #1c1917; border-color: #44403c; }
.is-dark .score-basis-text { color: #a8a29e; }
.is-dark .point-block { border-left-color: #57534e; }
.is-dark .point-block.strengths { border-left-color: #957534; }
.is-dark .point-block.strengths .point-title { color: #bf9748; }
.is-dark .point-block.weaknesses { border-left-color: #ef4444; }
.is-dark .point-block.weaknesses .point-title { color: #f87171; }
.is-dark .point-block.suggestions { border-left-color: #db9579; }
.is-dark .point-block.suggestions .point-title { color: #e3ac96; }
.is-dark .point-title { color: var(--text-tertiary, #8e8e93); }
.is-dark .point-text { color: var(--text-secondary, #78716c); }
.is-dark .btn-secondary { background: rgba(205, 106, 67, 0.15); color: #dd987d; }
.is-dark .btn-secondary:active { background: rgba(205, 106, 67, 0.25); }
.is-dark .s-chevron { color: var(--text-secondary, #78716c); }
.is-dark .select-box.has-value .s-text { color: #fafaf9; }
.is-dark .jd-input { color: #fafaf9; }
.is-dark .progress-bar-container { background-color: var(--text-secondary, #78716c); }

/* ================================================================
 * CareerLoop editorial skin — warm paper + vermilion primary action.
 * ================================================================ */
.resume-ai-page {
  background: #faf9f6;
  color: #2c2b29;
  font-family: "Noto Sans SC", "PingFang SC", "Microsoft YaHei", sans-serif;
}

.resume-ai-content {
  background: #faf9f6;
}

.header {
  margin-bottom: 18px;
}

.subtitle {
  color: #5a5956;
  line-height: 1.75;
}

.analysis-card,
.result-card {
  border: 1px solid #e0dfdb;
  border-radius: 8px;
  background: #faf9f6;
  box-shadow: 0 6px 18px rgba(44, 43, 41, 0.05);
}

.analysis-card {
  padding: 18px;
}

.section {
  margin-bottom: 18px;
}

.section-title,
.r-title,
.score-basis-title,
.point-title,
.ts-title,
.change-title,
.loading-text {
  color: #2c2b29;
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
  font-weight: 600;
  letter-spacing: 0.05em;
}

.select-box {
  min-height: 60px;
  padding: 12px 13px;
  border: 1px solid #d4d2cc;
  border-radius: 6px;
  background: #faf9f6;
  box-sizing: border-box;
}

.select-box:active {
  background: #f5f5f0;
}

.s-icon-wrap {
  border: 1px solid #d4d2cc;
  border-radius: 5px;
  background: #f5f5f0;
}

.s-icon-text {
  color: #ac6448;
  font-weight: 600;
  letter-spacing: 0.08em;
}

.s-text {
  color: #8b8a86;
}

.select-box.has-value .s-text {
  color: #2c2b29;
}

.s-chevron {
  color: #8b8a86;
}

.jd-counter {
  color: #8b8a86;
  font-weight: 500;
}

.jd-counter-warn {
  color: #b8975a;
}

.jd-input {
  border: 1px solid #d4d2cc;
  border-radius: 6px;
  background: #faf9f6;
  color: #2c2b29;
  line-height: 1.7;
}

.ph {
  color: #b0afab;
}

.btn-primary {
  height: 50px;
  line-height: 50px;
  border: 1px solid #c23b22;
  border-radius: 6px;
  background: #c23b22 !important;
  background-color: #c23b22 !important;
  color: #faf9f6 !important;
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
  font-weight: 600;
  letter-spacing: 0.06em;
}

.btn-primary:active {
  background: #a9321d !important;
  background-color: #a9321d !important;
  opacity: 1;
}

.btn-primary[disabled] {
  border-color: #d4d2cc;
  background: #efeee9 !important;
  background-color: #efeee9 !important;
  color: #9f9d97 !important;
}

.assessment-banner {
  padding: 13px 14px;
  border: 1px solid rgba(172, 100, 72, 0.3);
  border-left: 2px solid #ac6448;
  border-radius: 0 6px 6px 0;
  background: #f5f5f0;
}

.ab-title {
  color: #ac6448;
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.ab-body {
  color: #5a5956;
  line-height: 1.6;
}

.ab-chip {
  min-height: 32px;
  padding: 4px 10px;
  border: 1px solid #d4d2cc;
  border-radius: 4px;
  background: #faf9f6;
  display: flex;
  align-items: center;
  box-sizing: border-box;
}

.ab-chip-text {
  color: #ac6448;
  font-weight: 500;
}

.result-card {
  padding: 20px 17px;
}

.r-header {
  padding-bottom: 16px;
  border-bottom-color: #e0dfdb;
}

.r-title {
  font-size: 18px;
}

.r-sub {
  color: #8b8a86;
}

.score-ring {
  width: 72px;
  height: 64px;
  border-width: 1px;
  border-radius: 6px;
  background: #f5f5f0;
}

.score-ring.ring-good {
  border-color: #8d846e;
  background: rgba(141, 132, 110, 0.08);
}

.score-ring.ring-good .score-val {
  color: #736b58;
}

.score-ring.ring-warn {
  border-color: #b8975a;
  background: rgba(184, 151, 90, 0.08);
}

.score-ring.ring-warn .score-val {
  color: #8f713d;
}

.score-ring.ring-bad {
  border-color: #c23b22;
  background: rgba(194, 59, 34, 0.06);
}

.score-ring.ring-bad .score-val {
  color: #c23b22;
}

.score-val {
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
  font-weight: 600;
}

.score-basis {
  padding: 11px 12px;
  border: 1px solid #e0dfdb;
  border-radius: 6px;
  background: #f5f5f0;
}

.score-basis-title {
  color: #ac6448;
}

.score-basis-text,
.point-text {
  color: #5a5956;
  line-height: 1.7;
}

.point-block {
  padding-left: 12px;
  border-left-width: 2px;
}

.point-block.strengths {
  border-left-color: #8d846e;
}

.point-block.weaknesses {
  border-left-color: #c23b22;
}

.point-block.suggestions {
  border-left-color: #ac6448;
}

.point-block.strengths .point-title {
  color: #736b58;
}

.point-block.weaknesses .point-title {
  color: #c23b22;
}

.point-block.suggestions .point-title {
  color: #ac6448;
}

.btn-secondary {
  height: 48px;
  line-height: 48px;
  border: 1px solid #c23b22;
  border-radius: 6px;
  background: transparent !important;
  background-color: transparent !important;
  color: #c23b22 !important;
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
  font-weight: 600;
  letter-spacing: 0.05em;
}

.btn-secondary:active {
  background: rgba(194, 59, 34, 0.08) !important;
  background-color: rgba(194, 59, 34, 0.08) !important;
}

.tailor-success-card {
  padding: 20px 18px;
  border: 1px solid #a8a191;
  border-radius: 8px;
  background: rgba(141, 132, 110, 0.07);
}

.ts-icon,
.ts-title,
.ts-hint {
  color: #736b58;
}

.ts-title {
  font-weight: 600;
}

.ts-hint {
  font-weight: 500;
}

.ts-sub {
  color: #5a5956;
}

.change-list {
  padding: 12px;
  border: 1px solid #d9d8d3;
  border-radius: 6px;
  background: rgba(250, 249, 246, 0.7);
}

.change-title,
.change-text {
  color: #5a5956;
}

.change-index {
  border-radius: 4px;
  background: #8d846e;
  font-weight: 600;
}

.ts-btn-primary,
.ts-btn-secondary {
  border-radius: 6px;
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
  font-weight: 600;
  letter-spacing: 0.04em;
  box-shadow: none;
}

.ts-btn-primary {
  border: 1px solid #c23b22;
  background: #c23b22 !important;
  background-color: #c23b22 !important;
  color: #faf9f6 !important;
}

.ts-btn-primary:active {
  background: #a9321d !important;
  background-color: #a9321d !important;
}

.ts-btn-secondary {
  border: 1px solid #ac6448;
  background: transparent !important;
  background-color: transparent !important;
  color: #ac6448 !important;
}

.ts-btn-secondary:active {
  background: rgba(172, 100, 72, 0.07) !important;
  background-color: rgba(172, 100, 72, 0.07) !important;
}

.loading-overlay {
  background: rgba(250, 249, 246, 0.94);
  backdrop-filter: none;
}

.spinner {
  border-color: #e0dfdb;
  border-top-color: #c23b22;
}

.loading-text {
  color: #2c2b29;
}

.progress-bar-container {
  height: 4px;
  border-radius: 1px;
  background: #e0dfdb;
}

.progress-bar-fill {
  border-radius: 1px;
  background: #c23b22;
}

.is-dark.resume-ai-page,
.is-dark .resume-ai-content {
  background: #1f1f1d;
}

.is-dark .analysis-card,
.is-dark .result-card,
.is-dark .select-box,
.is-dark .jd-input {
  border-color: #45443f;
  background: #292926;
  box-shadow: none;
}

.is-dark .section-title,
.is-dark .r-title,
.is-dark .loading-text {
  color: #f5f5f0;
}

.is-dark .subtitle,
.is-dark .point-text,
.is-dark .score-basis-text,
.is-dark .s-text {
  color: #c4c2bc;
}

.is-dark .assessment-banner,
.is-dark .score-basis,
.is-dark .change-list {
  border-color: #575650;
  background: #33332f;
}

.is-dark .assessment-banner {
  border-left-color: #d3a18d;
}

.is-dark .ab-title,
.is-dark .ab-chip-text {
  color: #e5beaf;
}

.is-dark .ab-body {
  color: #c4c2bc;
}

.is-dark .ab-chip {
  border-color: #575650;
  background: #292926;
}

.is-dark .tailor-success-card {
  border-color: #756e5c;
  background: rgba(141, 132, 110, 0.09);
}

.is-dark .score-ring.ring-good {
  border-color: #8d846e;
  background: rgba(141, 132, 110, 0.1);
}

.is-dark .score-ring.ring-good .score-val {
  color: #b5afa0;
}

.is-dark .score-ring.ring-warn {
  border-color: #b8975a;
  background: rgba(184, 151, 90, 0.1);
}

.is-dark .score-ring.ring-warn .score-val {
  color: #d0b278;
}

.is-dark .score-ring.ring-bad {
  border-color: #c23b22;
  background: rgba(194, 59, 34, 0.1);
}

.is-dark .score-ring.ring-bad .score-val {
  color: #e27b66;
}

.is-dark .point-block.strengths {
  border-left-color: #8d846e;
}

.is-dark .point-block.strengths .point-title {
  color: #b5afa0;
}

.is-dark .point-block.weaknesses {
  border-left-color: #c23b22;
}

.is-dark .point-block.weaknesses .point-title {
  color: #e27b66;
}

.is-dark .point-block.suggestions {
  border-left-color: #d3a18d;
}

.is-dark .point-block.suggestions .point-title {
  color: #e5beaf;
}

.is-dark .ts-title,
.is-dark .ts-hint {
  color: #b5afa0;
}

.is-dark .ts-sub,
.is-dark .change-title,
.is-dark .change-text {
  color: #c4c2bc;
}

.is-dark .loading-overlay {
  background: rgba(31, 31, 29, 0.94);
}

.is-dark .btn-secondary {
  border-color: #d8644d;
  background: transparent !important;
  color: #e27b66 !important;
}

.is-dark .s-icon-wrap {
  border-color: #575650;
  background: #33332f;
}

.is-dark .s-icon-text {
  color: #e5beaf;
}

.btn-primary[disabled] {
  border-color: #d8d6cf;
  background: #e0dfdb !important;
  background-color: #e0dfdb !important;
  color: #5a5956 !important;
}

.is-dark .btn-primary[disabled] {
  border-color: #575650;
  background: #33332f !important;
  background-color: #33332f !important;
  color: #c4c2bc !important;
}
</style>
