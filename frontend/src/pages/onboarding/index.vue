<template>
  <view class="onboarding-page app-soft-bg" :class="[themeClass, fontClass]">
    <view class="top-safe-spacer" :style="{ height: topSafeHeight + 'px' }"></view>

    <view class="page-shell">
      <view class="hero">
        <text class="hero-kicker">智绘职路 CareerLoop · 学生成长端</text>
        <text class="hero-title">先用 2 分钟，让首页真正懂你</text>
        <text class="hero-desc">这里只收集制定第一批求职任务必需的信息。简历、项目、面试表现会在后续使用中继续补全。</text>
      </view>

      <view class="progress-row">
        <view
          v-for="(_, index) in steps"
          :key="index"
          class="progress-dot"
          :class="{ active: current === index, done: current > index }"
        />
      </view>

      <swiper class="slides" :current="current" :duration="260" :indicator-dots="false" :disable-touch="true">
        <swiper-item class="slide">
          <scroll-view scroll-y class="slide-scroll" enhanced :show-scrollbar="false">
            <view class="slide-inner">
              <text class="step-label">01 / 当前阶段</text>
              <text class="step-title">你现在处在哪个求职阶段？</text>
              <text class="step-desc">阶段决定首页优先展示方向、简历、项目还是面试任务。</text>
              <view class="option-grid">
                <view
                  v-for="option in stageOptions"
                  :key="option.value"
                  class="choice-card app-surface"
                  :class="{ selected: stage === option.value }"
                  @click="stage = option.value"
                 hover-class="press-fb" hover-stay-time="120">
                  <view class="choice-icon" :class="option.tone"><text :class="option.icon"></text></view>
                  <view class="choice-copy">
                    <text class="choice-title">{{ option.label }}</text>
                    <text class="choice-desc">{{ option.desc }}</text>
                  </view>
                </view>
              </view>
            </view>
          </scroll-view>
        </swiper-item>

        <swiper-item class="slide">
          <scroll-view scroll-y class="slide-scroll" enhanced :show-scrollbar="false">
            <view class="slide-inner">
              <text class="step-label">02 / 目标与痛点</text>
              <text class="step-title">你想准备什么岗位？最卡在哪里？</text>
              <view class="target-input-wrap app-surface">
                <text class="target-icon ri-focus-3-line"></text>
                <input class="target-input" v-model="targetRole" maxlength="32" placeholder="例如：Java 后端开发" placeholder-class="ph" />
              </view>
              <view class="chips">
                <view v-for="role in targetSuggestions" :key="role" class="role-chip" :class="{ selected: targetRole === role }" @click="targetRole = role" hover-class="press-fb" hover-stay-time="120">
                  <text>{{ role }}</text>
                </view>
              </view>
              <text class="mini-section-title">当前最急的问题</text>
              <view class="pill-grid">
                <view v-for="option in painOptions" :key="option.value" class="pill" :class="{ selected: painPoint === option.value }" @click="painPoint = option.value" hover-class="press-fb" hover-stay-time="120">
                  <text>{{ option.label }}</text>
                </view>
              </view>
            </view>
          </scroll-view>
        </swiper-item>

        <swiper-item class="slide">
          <scroll-view scroll-y class="slide-scroll" enhanced :show-scrollbar="false">
            <view class="slide-inner">
              <text class="step-label">03 / 简历与时间线</text>
              <text class="step-title">你离投递还有多远？</text>
              <text class="mini-section-title">简历状态</text>
              <view class="resume-options">
                <view v-for="option in resumeOptions" :key="option.value" class="resume-card app-surface" :class="{ selected: resumeStatus === option.value }" @click="resumeStatus = option.value" hover-class="press-fb" hover-stay-time="120">
                  <text class="resume-icon" :class="option.icon"></text>
                  <view class="resume-copy">
                    <text class="resume-title">{{ option.label }}</text>
                    <text class="resume-desc">{{ option.desc }}</text>
                  </view>
                </view>
              </view>
              <text class="mini-section-title">求职时间线</text>
              <view class="pill-grid">
                <view v-for="option in timelineOptions" :key="option.value" class="pill" :class="{ selected: timeline === option.value }" @click="timeline = option.value" hover-class="press-fb" hover-stay-time="120">
                  <text>{{ option.label }}</text>
                </view>
              </view>
            </view>
          </scroll-view>
        </swiper-item>

        <swiper-item class="slide">
          <scroll-view scroll-y class="slide-scroll" enhanced :show-scrollbar="false">
            <view class="slide-inner">
              <text class="step-label">04 / 基础背景</text>
              <text class="step-title">补充一点背景，让建议更贴近你</text>
              <view class="form-grid">
                <view class="field app-surface">
                  <text class="field-label">学校</text>
                  <input class="field-input" v-model="school" maxlength="32" placeholder="例如：成都理工大学" placeholder-class="ph" />
                </view>
                <view class="field app-surface">
                  <text class="field-label">专业</text>
                  <input class="field-input" v-model="major" maxlength="32" placeholder="例如：软件工程" placeholder-class="ph" />
                </view>
                <view class="field app-surface">
                  <text class="field-label">学历</text>
                  <input class="field-input" v-model="degree" maxlength="16" placeholder="本科 / 硕士 / 专科" placeholder-class="ph" />
                </view>
                <view class="field app-surface">
                  <text class="field-label">毕业年份</text>
                  <input class="field-input" v-model="graduationYear" type="number" maxlength="4" placeholder="2026" placeholder-class="ph" />
                </view>
              </view>
            </view>
          </scroll-view>
        </swiper-item>

        <swiper-item class="slide">
          <scroll-view scroll-y class="slide-scroll" enhanced :show-scrollbar="false">
            <view class="slide-inner">
              <text class="step-label">05 / 行动偏好</text>
              <text class="step-title">你希望智绘职路先帮你做什么？</text>
              <text class="mini-section-title">每周可投入时间</text>
              <view class="pill-grid">
                <view v-for="option in weeklyOptions" :key="option.value" class="pill" :class="{ selected: weeklyAvailability === option.value }" @click="weeklyAvailability = option.value" hover-class="press-fb" hover-stay-time="120">
                  <text>{{ option.label }}</text>
                </view>
              </view>
              <text class="mini-section-title">优先帮助</text>
              <view class="priority-grid">
                <view v-for="option in priorityOptions" :key="option.value" class="priority-card app-surface" :class="{ selected: priorityHelp === option.value }" @click="priorityHelp = option.value" hover-class="press-fb" hover-stay-time="120">
                  <text class="priority-icon" :class="option.icon"></text>
                  <text class="priority-title">{{ option.label }}</text>
                  <text class="priority-desc">{{ option.desc }}</text>
                </view>
              </view>
              <view class="route-preview">
                <text class="route-label">完成后进入</text>
                <text class="route-value">求职准备面板：准备度、短板和今日任务</text>
              </view>
            </view>
          </scroll-view>
        </swiper-item>
      </swiper>
    </view>

    <view class="bottom-bar">
      <view class="btn-secondary" :class="{ invisible: current === 0 }" @click="prev" hover-class="press-fb" hover-stay-time="120">
        <text class="ri-arrow-left-line"></text>
        <text>上一步</text>
      </view>
      <view class="btn-primary" :class="{ disabled: !canContinue || saving }" @click="handlePrimary" hover-class="press-fb" hover-stay-time="120">
        <text>{{ primaryText }}</text>
        <text class="ri-arrow-right-line" v-if="current < steps.length - 1"></text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { getMpSafeAreaMetrics } from '@/utils/safeArea';
import { useTheme } from '@/utils/theme';
import { getProfileSnapshotApi, updateOnboardingApi } from '@/api/user';
import { PENDING_ONBOARDING_KEY } from '@/utils/onboardingSync';
import { isRealUser } from '@/utils/auth';
import {
  markOnboardingSeen,
  readStoredOnboardingSetup,
  snapshotToOnboardingSetup,
  type StoredOnboardingSetup,
} from '@/utils/onboardingGate';
import { consumeConsentReturnUrl } from '@/utils/consent';

type Stage = 'student' | 'new_graduate' | 'internship_seeker' | 'career_switcher' | 'experienced';
type ResumeState = 'ready' | 'draft' | 'none' | 'unsure';

const { themeClass, fontClass, refresh: refreshTheme } = useTheme();
const PENDING_KEY = PENDING_ONBOARDING_KEY;

const steps = [0, 1, 2, 3, 4];
const current = ref(0);
const topSafeHeight = ref(52);
const saving = ref(false);

const stage = ref<Stage>('new_graduate');
const targetRole = ref('');
const painPoint = ref('resume_weak');
const resumeStatus = ref<ResumeState>('unsure');
const timeline = ref('within_3_months');
const school = ref('');
const major = ref('');
const degree = ref('');
const graduationYear = ref('');
const weeklyAvailability = ref('5_10h');
const priorityHelp = ref('resume');

const stageValues = new Set(['student', 'new_graduate', 'internship_seeker', 'career_switcher', 'experienced']);
const resumeValues = new Set(['ready', 'draft', 'none', 'unsure']);
const painValues = new Set(['direction_unclear', 'resume_weak', 'project_lacking', 'interview_anxiety', 'no_plan']);
const timelineValues = new Set(['now', 'within_1_month', 'within_3_months', 'prepare_early']);
const weeklyValues = new Set(['lt_5h', '5_10h', '10_20h', 'gt_20h']);
const priorityValues = new Set(['resume', 'direction', 'interview', 'plan']);

const stageOptions = [
  { value: 'student', label: '在校探索', desc: '还在确认职业方向和能力路线', icon: 'ri-graduation-cap-line', tone: 'tone-blue' },
  { value: 'new_graduate', label: '应届 / 准应届', desc: '准备校招、秋招、春招或毕业求职', icon: 'ri-briefcase-4-line', tone: 'tone-green' },
  { value: 'internship_seeker', label: '找实习', desc: '想尽快补齐项目、简历和面试短板', icon: 'ri-calendar-check-line', tone: 'tone-amber' },
  { value: 'career_switcher', label: '转方向', desc: '已有经历，需要重塑岗位叙事', icon: 'ri-route-line', tone: 'tone-purple' },
  { value: 'experienced', label: '社招提升', desc: '希望优化简历和面试表现，冲刺更好机会', icon: 'ri-user-star-line', tone: 'tone-slate' },
] as const;

const targetSuggestions = ['Java 后端开发', '前端开发工程师', '产品经理', '数据分析师', '算法工程师', '新媒体运营'];
const painOptions = [
  { value: 'direction_unclear', label: '方向不清' },
  { value: 'resume_weak', label: '简历薄弱' },
  { value: 'project_lacking', label: '项目不足' },
  { value: 'interview_anxiety', label: '面试没底' },
  { value: 'no_plan', label: '缺少计划' },
];
const resumeOptions: Array<{ value: ResumeState; label: string; desc: string; icon: string }> = [
  { value: 'ready', label: '已有可投简历', desc: '下一步适合做岗位匹配和诊断', icon: 'ri-file-check-line' },
  { value: 'draft', label: '有草稿但不放心', desc: '需要先查问题、补关键词和项目证据', icon: 'ri-file-edit-line' },
  { value: 'none', label: '还没有简历', desc: '先从目标岗位和经历素材开始整理', icon: 'ri-file-add-line' },
  { value: 'unsure', label: '不确定能不能投', desc: '先建立基线，再决定修改顺序', icon: 'ri-question-answer-line' },
];
const timelineOptions = [
  { value: 'now', label: '马上投递' },
  { value: 'within_1_month', label: '1 个月内' },
  { value: 'within_3_months', label: '3 个月内' },
  { value: 'prepare_early', label: '提前准备' },
];
const weeklyOptions = [
  { value: 'lt_5h', label: '< 5 小时' },
  { value: '5_10h', label: '5-10 小时' },
  { value: '10_20h', label: '10-20 小时' },
  { value: 'gt_20h', label: '> 20 小时' },
];
const priorityOptions = [
  { value: 'resume', label: '改简历', desc: '先把材料变得可投递', icon: 'ri-file-search-line' },
  { value: 'direction', label: '定方向', desc: '先确认目标岗位和路径', icon: 'ri-compass-3-line' },
  { value: 'interview', label: '练面试', desc: '先提升表达和临场反馈', icon: 'ri-mic-2-line' },
  { value: 'plan', label: '做计划', desc: '先拆出本周行动任务', icon: 'ri-list-check-3-line' },
];

const trimmedTargetRole = computed(() => targetRole.value.trim());
const normalizedResumeState = computed(() => resumeStatus.value === 'ready' || resumeStatus.value === 'draft' ? 'yes' : resumeStatus.value === 'none' ? 'no' : 'unsure');
const canContinue = computed(() => {
  if (current.value === 1) return !!trimmedTargetRole.value && !!painPoint.value;
  if (current.value === 2) return !!resumeStatus.value && !!timeline.value;
  if (current.value === 4) return !!weeklyAvailability.value && !!priorityHelp.value;
  return true;
});
const primaryText = computed(() => current.value < steps.length - 1 ? '下一步' : (saving.value ? '保存中…' : '进入我的准备面板'));

onMounted(async () => {
  refreshTheme();
  topSafeHeight.value = getMpSafeAreaMetrics().contentTop;
  if (!isRealUser()) {
    uni.switchTab({ url: '/pages/home/index' });
    return;
  }
  await prefillSetup();
});

const asString = (value: unknown) => typeof value === 'string' ? value.trim() : '';

const pickKnown = <T extends string>(value: unknown, allowed: Set<string>, fallback: T): T => {
  const normalized = asString(value);
  return allowed.has(normalized) ? normalized as T : fallback;
};

const applySetup = (setup: StoredOnboardingSetup) => {
  const setupStage = setup.stage || setup.identityType;
  stage.value = pickKnown<Stage>(setupStage, stageValues, stage.value);
  targetRole.value = asString(setup.targetRole) || targetRole.value;
  painPoint.value = pickKnown(setup.painPoint, painValues, painPoint.value);
  resumeStatus.value = pickKnown<ResumeState>(setup.resumeStatus, resumeValues, resumeStatus.value);
  timeline.value = pickKnown(setup.timeline, timelineValues, timeline.value);
  weeklyAvailability.value = pickKnown(setup.weeklyAvailability, weeklyValues, weeklyAvailability.value);
  priorityHelp.value = pickKnown(setup.priorityHelp, priorityValues, priorityHelp.value);

  const education = setup.education || {};
  school.value = asString(education.school) || school.value;
  major.value = asString(education.major) || major.value;
  degree.value = asString(education.degree) || degree.value;
  graduationYear.value = asString(education.graduationYear) || graduationYear.value;
};

const prefillSetup = async () => {
  const localSetup = readStoredOnboardingSetup();
  if (localSetup) {
    applySetup(localSetup);
    return;
  }

  if (!isRealUser()) return;
  try {
    const snapshot = await getProfileSnapshotApi();
    const serverSetup = snapshotToOnboardingSetup(snapshot);
    if (serverSetup) applySetup(serverSetup);
  } catch {
    // Prefill is best-effort; users can still finish the flow manually.
  }
};

const prev = () => {
  if (current.value > 0) current.value--;
};

const next = () => {
  if (!canContinue.value) {
    uni.showToast({ title: current.value === 1 ? '请填写目标岗位并选择痛点' : '请先完成当前选择', icon: 'none' });
    return;
  }
  if (current.value < steps.length - 1) current.value++;
};

const persistSetup = async () => {
  const setup = {
    userId: Number(uni.getStorageSync('userId')) || undefined,
    identityType: stage.value,
    stage: stage.value,
    targetRole: trimmedTargetRole.value,
    painPoint: painPoint.value,
    hasResume: normalizedResumeState.value,
    resumeStatus: resumeStatus.value,
    timeline: timeline.value,
    education: {
      school: school.value.trim(),
      major: major.value.trim(),
      degree: degree.value.trim(),
      graduationYear: graduationYear.value.trim(),
    },
    weeklyAvailability: weeklyAvailability.value,
    priorityHelp: priorityHelp.value,
    recommendedEntry: priorityHelp.value === 'interview' ? 'interview' : priorityHelp.value === 'direction' ? 'assessment' : priorityHelp.value,
    onboardingCompletedAt: new Date().toISOString(),
  };

  markOnboardingSeen(setup);
  uni.setStorageSync(PENDING_KEY, setup);

  await updateOnboardingApi(setup);
  uni.removeStorageSync(PENDING_KEY);
};

const finish = async () => {
  if (!canContinue.value || saving.value) {
    next();
    return;
  }

  saving.value = true;
  try {
    await persistSetup();
    uni.showToast({ title: '画像已建立', icon: 'success' });
  } catch {
    uni.showToast({ title: '已保存本地设置，稍后会继续同步', icon: 'none' });
  } finally {
    saving.value = false;
  }
  if (!isRealUser()) return;
  setTimeout(() => {
    const returnUrl = consumeConsentReturnUrl();
    if (returnUrl) {
      uni.reLaunch({ url: returnUrl });
      return;
    }
    uni.switchTab({ url: '/pages/home/index' });
  }, 450);
};

const handlePrimary = () => {
  if (current.value < steps.length - 1) {
    next();
    return;
  }
  finish();
};
</script>

<style scoped>
.onboarding-page {
  height: 100vh;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding-bottom: env(safe-area-inset-bottom, 0px);
  box-sizing: border-box;
  background: var(--paper, #faf9f6);
  color: var(--ink, #2c2b29);
  font-family: var(--font-sans, "PingFang SC", "Microsoft YaHei", sans-serif);
}

.top-safe-spacer { width: 100%; flex-shrink: 0; }

.page-shell {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  width: 100%;
  max-width: var(--content-max-width, 640px);
  margin: 0 auto;
  padding: 14px 20px 0;
  box-sizing: border-box;
}

.hero { padding: 4px 0 16px; }
.hero-kicker {
  display: block;
  font-size: 11px;
  font-weight: 600;
  color: var(--vermilion, #c23b22);
  margin-bottom: 8px;
  font-family: var(--font-serif, "Songti SC", STSong, serif);
  letter-spacing: 0.12em;
}
.hero-title {
  display: block;
  font-size: 28px;
  line-height: 1.35;
  font-weight: 600;
  color: var(--ink, #2c2b29);
  font-family: var(--font-serif, "Songti SC", STSong, serif);
  letter-spacing: 0.05em;
}
.hero-desc {
  display: block;
  margin-top: 9px;
  font-size: 13px;
  line-height: 1.75;
  color: var(--ink-secondary, #5a5956);
}

.progress-row { display: flex; gap: 7px; margin-bottom: 14px; }
.progress-dot {
  flex: 1;
  height: 4px;
  border-radius: 2px;
  background: var(--border-light, #ecebe7);
}
.progress-dot.done { background: var(--sage, #8d846e); }
.progress-dot.active { background: var(--vermilion, #c23b22); }

.slides {
  flex: 1;
  width: 100%;
  height: auto;
  min-height: 0;
}
.slide,
.slide-scroll,
.slide-inner { width: 100%; height: 100%; }
.slide-inner { box-sizing: border-box; padding-bottom: 20px; }

.step-label,
.mini-section-title {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: var(--vermilion, #c23b22);
  margin-bottom: 8px;
  font-family: var(--font-serif, "Songti SC", STSong, serif);
  letter-spacing: 0.06em;
}
.mini-section-title { margin-top: 20px; color: var(--ink-secondary, #5a5956); }
.step-title {
  display: block;
  font-size: 22px;
  line-height: 1.45;
  font-weight: 600;
  color: var(--ink, #2c2b29);
  font-family: var(--font-serif, "Songti SC", STSong, serif);
  letter-spacing: 0.05em;
}
.step-desc {
  display: block;
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.5;
  color: var(--ink-secondary, #5a5956);
}

.option-grid,
.resume-options,
.priority-grid,
.form-grid {
  display: flex;
  flex-direction: column;
  gap: 11px;
  margin-top: 18px;
}
.choice-card,
.resume-card,
.priority-card,
.field {
  border: 1px solid var(--border-color, #e0dfdb);
  border-radius: var(--radius-md, 6px);
  box-sizing: border-box;
  background: var(--card-bg, #fffefa);
}
.choice-card,
.resume-card {
  display: flex;
  align-items: center;
  gap: 13px;
  padding: 13px;
}
.choice-card.selected,
.resume-card.selected,
.priority-card.selected,
.pill.selected {
  border-color: var(--vermilion, #c23b22);
  background: var(--vermilion-soft, #f8ebe7);
}
.choice-icon,
.resume-icon,
.priority-icon {
  width: 42px;
  height: 42px;
  border-radius: var(--radius-sm, 4px);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 22px;
}
.resume-icon { color: var(--vermilion, #c23b22); background: var(--vermilion-soft, #f8ebe7); }
.tone-blue { background: var(--indigo-soft, #f9f0ed); color: var(--indigo, #ac6448); }
.tone-green { background: var(--sage-soft, #f2efe9); color: var(--sage, #8d846e); }
.tone-amber { background: var(--gold-soft, #f5efe4); color: var(--heritage-gold, #b8975a); }
.tone-purple { background: var(--vermilion-soft, #f8ebe7); color: var(--vermilion, #c23b22); }
.tone-slate { background: var(--paper-deep, #efeee9); color: var(--ink-secondary, #5a5956); }

.choice-copy,
.resume-copy {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
  min-width: 0;
}
.choice-title,
.resume-title,
.priority-title {
  font-size: 15px;
  line-height: 1.3;
  font-weight: 600;
  color: var(--ink, #2c2b29);
  font-family: var(--font-serif, "Songti SC", STSong, serif);
  letter-spacing: 0.04em;
}
.choice-desc,
.resume-desc,
.priority-desc {
  font-size: 12px;
  line-height: 1.45;
  color: var(--ink-secondary, #5a5956);
}

.target-input-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 18px;
  border: 1px solid var(--border-color, #e0dfdb);
  border-radius: var(--radius-sm, 4px);
  padding: 0 14px;
  height: 54px;
  box-sizing: border-box;
}
.target-icon { font-size: 22px; color: var(--vermilion, #c23b22); }
.target-input,
.field-input {
  flex: 1;
  height: 52px;
  font-size: 15px;
  color: var(--ink, #2c2b29);
}
.ph { color: var(--ink-placeholder, #aaa9a5); }

.chips,
.pill-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 14px;
}
.role-chip,
.pill {
  border: 1px solid var(--border-color, #e0dfdb);
  border-radius: var(--radius-sm, 4px);
  padding: 9px 12px;
  background: var(--card-bg, #fffefa);
}
.role-chip text,
.pill text {
  font-size: 13px;
  font-weight: 600;
  color: var(--ink-secondary, #5a5956);
}
.role-chip.selected,
.pill.selected { border-color: var(--vermilion, #c23b22); background: var(--vermilion-soft, #f8ebe7); }
.role-chip.selected text,
.pill.selected text { color: var(--vermilion, #c23b22); }

.field {
  padding: 10px 14px 0;
  background: var(--card-bg, #fffefa);
}
.field-label {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: var(--ink-secondary, #5a5956);
  font-family: var(--font-serif, "Songti SC", STSong, serif);
  letter-spacing: 0.04em;
}
.field-input { width: 100%; }

.priority-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
.priority-card {
  padding: 13px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.priority-icon {
  color: var(--indigo, #ac6448);
  background: var(--indigo-soft, #f9f0ed);
}
.priority-title,
.priority-desc { display: block; }

.route-preview {
  margin-top: 18px;
  border-left: 3px solid var(--heritage-gold, #b8975a);
  padding-left: 12px;
}
.route-label {
  display: block;
  font-size: 12px;
  color: var(--ink-tertiary, #8b8a86);
  margin-bottom: 4px;
}
.route-value {
  display: block;
  font-size: 14px;
  line-height: 1.45;
  font-weight: 600;
  color: var(--ink, #2c2b29);
  font-family: var(--font-serif, "Songti SC", STSong, serif);
}

.bottom-bar {
  flex-shrink: 0;
  width: 100%;
  max-width: var(--content-max-width, 640px);
  margin: 0 auto;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 20px 28px;
  box-sizing: border-box;
}
.btn-secondary,
.btn-primary {
  height: 50px;
  border-radius: var(--btn-radius, 6px);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 600;
  font-family: var(--font-serif, "Songti SC", STSong, serif);
  letter-spacing: 0.05em;
}
.btn-secondary {
  width: 112px;
  color: var(--ink-secondary, #5a5956);
  background: var(--paper-soft, #f5f5f0);
  border: 1px solid var(--border-color, #e0dfdb);
}
.btn-secondary.invisible { display: none; }
.btn-primary {
  flex: 1;
  color: #ffffff;
  background: var(--vermilion, #c23b22);
  box-shadow: 0 5px 14px rgba(194, 59, 34, 0.12);
}
.btn-primary.disabled { opacity: 0.55; }

.is-dark .hero-title,
.is-dark .step-title,
.is-dark .choice-title,
.is-dark .resume-title,
.is-dark .priority-title,
.is-dark .target-input,
.is-dark .field-input,
.is-dark .route-value { color: #fafaf9; }
.is-dark .hero-desc,
.is-dark .step-desc,
.is-dark .choice-desc,
.is-dark .resume-desc,
.is-dark .priority-desc { color: #a8a29e; }
.is-dark .field { background: #292524; }

@media (max-height: 720px) {
  .page-shell { padding-top: 8px; }
  .hero { padding-bottom: 10px; }
  .hero-kicker { margin-bottom: 5px; }
  .hero-title { font-size: 24px; }
  .hero-desc { margin-top: 5px; line-height: 1.5; }
  .progress-row { margin-bottom: 9px; }
  .bottom-bar { padding-top: 8px; padding-bottom: 16px; }
  .choice-card,
  .resume-card { padding: 11px; }
}
</style>
