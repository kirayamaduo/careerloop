<template>
  <SlPage class="profile-page app-soft-bg" :custom-class="['profile-page', themeClass, fontClass].join(' ')">
    <SlNavBar
      :title="t('agent.profile.title')"
      show-back
      @back="goBack"
      :safe-top="topSafeHeight"
      :right-avoid-width="rightAvoidWidth"
    />

    <view v-if="pageLoading" class="profile-state app-card-soft">
      <view class="profile-state-spinner"></view>
      <text class="profile-state-title">{{ t('agent.profile.loading') }}</text>
    </view>
    <view v-else-if="pageError" class="profile-state app-card-soft">
      <text class="profile-state-title">{{ t('agent.profile.loadFailed') }}</text>
      <text class="profile-state-desc">{{ pageError }}</text>
      <view class="profile-state-retry" @click="loadProfilePage">
        <text>{{ t('agent.profile.retry') }}</text>
      </view>
    </view>
    <template v-else>
    <!-- completeness banner -->
    <view v-if="agentProfile" class="profile-banner">
      <view class="profile-banner-bar-wrap">
        <view
          class="profile-banner-bar-fill"
          :class="'pct-' + agentProfile.personalizationLevel.toLowerCase()"
          :style="{ width: agentProfile.completenessScore + '%' }"
        ></view>
      </view>
      <text class="profile-banner-label">
        {{ t('agent.profile.completeness', { n: agentProfile.completenessScore }) }}
        （{{ levelLabel }}）
      </text>
    </view>

    <!-- quantified profile -->
    <view v-if="agentProfile" class="profile-form profile-quant-wrap">
      <view class="profile-section app-card-soft">
        <view class="profile-section-head">
          <text class="profile-section-title">{{ t('agent.profile.sectionQuant') }}</text>
          <text class="profile-section-meta">{{ t('agent.profile.updatedAt') }} {{ profileUpdatedAt }}</text>
        </view>

        <view class="profile-target-panel">
          <view class="profile-target-copy">
            <text class="profile-target-label">{{ t('agent.profile.targetRole') }}</text>
            <text class="profile-target-role">{{ targetRoleLabel }}</text>
            <text class="profile-target-source">{{ targetSourceLabel }}</text>
          </view>
          <view class="profile-score-ring" :style="targetRingStyle">
            <view class="profile-score-ring-inner">
              <text class="profile-score-ring-value">{{ targetConfidencePercent }}</text>
              <text class="profile-score-ring-unit">%</text>
            </view>
          </view>
        </view>

        <view class="profile-readiness-grid">
          <view v-for="metric in readinessMetrics" :key="metric.key" class="profile-metric">
            <view class="profile-metric-head">
              <text class="profile-metric-label">{{ metric.label }}</text>
              <text class="profile-metric-value">{{ metric.value }}%</text>
            </view>
            <view class="profile-metric-bar">
              <view class="profile-metric-fill" :class="metric.tone" :style="{ width: metric.value + '%' }" />
            </view>
          </view>
        </view>

        <view class="profile-behavior-grid">
          <view v-for="metric in behaviorMetrics" :key="metric.key" class="profile-behavior-item">
            <text class="profile-behavior-value">{{ metric.value }}</text>
            <text class="profile-behavior-label">{{ metric.label }}</text>
          </view>
        </view>
      </view>

      <view class="profile-section app-card-soft">
        <view class="profile-section-head">
          <text class="profile-section-title">{{ t('agent.profile.sectionTagEditor') }}</text>
          <view class="profile-small-action" @click="refreshProfileTags">
            <text class="profile-small-action-text">{{ t('agent.profile.refreshTags') }}</text>
          </view>
        </view>
        <view v-for="section in tagSections" :key="section.category" class="profile-tag-editor-block">
          <view class="profile-tag-editor-head">
            <text class="profile-signal-title">{{ section.label }}</text>
            <view class="profile-tag-add" @click="addTag(section.category)">
              <text class="profile-tag-add-text">+</text>
            </view>
          </view>
          <view class="profile-signal-chips">
            <view
              v-for="(tag, idx) in tagDrafts[section.category]"
              :key="section.category + '-' + tag"
              class="profile-signal-chip profile-edit-chip"
              @click="removeTag(section.category, idx)"
            >
              <text class="profile-signal-chip-text">{{ tag }}</text>
              <text class="profile-edit-chip-x">×</text>
            </view>
          </view>
        </view>
        <view class="profile-tags-save" :class="{ 'profile-tags-saving': savingTags }" @click="saveProfileTags">
          <text class="profile-tags-save-text">{{ savingTags ? t('agent.profile.saving') : t('agent.profile.saveTags') }}</text>
        </view>
      </view>

      <view v-if="agentProfile.missingSignals?.length || agentProfile.skills?.length" class="profile-section app-card-soft">
        <text class="profile-section-title">{{ t('agent.profile.sectionSignals') }}</text>

        <view v-if="agentProfile.missingSignals?.length" class="profile-signal-block">
          <text class="profile-signal-title">{{ t('agent.profile.missingSignals') }}</text>
          <view class="profile-signal-chips">
            <view
              v-for="sig in agentProfile.missingSignals"
              :key="sig.key"
              class="profile-signal-chip"
              :class="'signal-' + sig.priority.toLowerCase()"
            >
              <text class="profile-signal-chip-text">{{ sig.label }}</text>
            </view>
          </view>
        </view>

        <view v-if="agentProfile.skills?.length" class="profile-skill-list">
          <text class="profile-signal-title">{{ t('agent.profile.skillSignals') }}</text>
          <view v-for="skill in agentProfile.skills.slice(0, 6)" :key="skill.name" class="profile-skill-row">
            <text class="profile-skill-name">{{ skill.name }}</text>
            <view class="profile-skill-bar">
              <view class="profile-skill-fill" :style="{ width: normalizeSkillLevel(skill.level) + '%' }" />
            </view>
            <text class="profile-skill-score">{{ normalizeSkillLevel(skill.level) }}%</text>
          </view>
        </view>
      </view>
    </view>

    <!-- form -->
    <view class="profile-form">
      <view class="profile-section app-card-soft">
        <text class="profile-section-title">{{ t('agent.profile.sectionGoal') }}</text>

        <view class="profile-field">
          <text class="profile-label">{{ t('agent.profile.targetCity') }}</text>
          <input
            v-model="form.targetCity"
            class="profile-input ui-input"
            :placeholder="t('agent.profile.targetCityPlaceholder')"
            maxlength="30"
          />
        </view>

        <view class="profile-field">
          <text class="profile-label">{{ t('agent.profile.targetIndustry') }}</text>
          <input
            v-model="form.targetIndustry"
            class="profile-input ui-input"
            :placeholder="t('agent.profile.targetIndustryPlaceholder')"
            maxlength="30"
          />
        </view>

        <view class="profile-field">
          <text class="profile-label">{{ t('agent.profile.timeline') }}</text>
          <view class="profile-chips">
            <view
              v-for="opt in timelineOptions"
              :key="opt.val"
              class="profile-chip ui-list-item"
              :class="{ 'profile-chip-active': form.timeline === opt.val }"
              @click="form.timeline = opt.val"
            >
              <text class="profile-chip-text">{{ opt.label }}</text>
            </view>
          </view>
        </view>

        <view class="profile-field">
          <text class="profile-label">{{ t('agent.profile.careerGoalNote') }}</text>
          <textarea
            v-model="form.careerGoalNote"
            class="profile-textarea ui-input"
            :placeholder="t('agent.profile.careerGoalNotePlaceholder')"
            maxlength="200"
          />
        </view>
      </view>

      <view class="profile-section app-card-soft">
        <text class="profile-section-title">{{ t('agent.profile.sectionLearning') }}</text>

        <view class="profile-field">
          <text class="profile-label">{{ t('agent.profile.weeklyHours') }}</text>
          <view class="profile-chips">
            <view
              v-for="opt in weeklyOptions"
              :key="opt.val"
              class="profile-chip ui-list-item"
              :class="{ 'profile-chip-active': form.weeklyHours === opt.val }"
              @click="form.weeklyHours = opt.val"
            >
              <text class="profile-chip-text">{{ opt.label }}</text>
            </view>
          </view>
        </view>

        <view class="profile-field">
          <text class="profile-label">{{ t('agent.profile.difficulty') }}</text>
          <view class="profile-chips">
            <view
              v-for="opt in difficultyOptions"
              :key="opt.val"
              class="profile-chip ui-list-item"
              :class="{ 'profile-chip-active': form.preferredDifficulty === opt.val }"
              @click="form.preferredDifficulty = opt.val"
            >
              <text class="profile-chip-text">{{ opt.label }}</text>
            </view>
          </view>
        </view>
      </view>

      <view class="profile-section app-card-soft">
        <text class="profile-section-title">{{ t('agent.profile.sectionStudy') }}</text>
        <view class="profile-toggle-row">
          <text class="profile-label">{{ t('agent.profile.gradSchool') }}</text>
          <view class="profile-toggle-group">
            <view
              class="profile-toggle-btn ui-list-item"
              :class="{ 'profile-toggle-active': form.considerGradSchool === true }"
              @click="form.considerGradSchool = true"
            ><text class="profile-toggle-text">{{ t('agent.profile.yes') }}</text></view>
            <view
              class="profile-toggle-btn ui-list-item"
              :class="{ 'profile-toggle-active': form.considerGradSchool === false }"
              @click="form.considerGradSchool = false"
            ><text class="profile-toggle-text">{{ t('agent.profile.no') }}</text></view>
          </view>
        </view>
        <view class="profile-toggle-row">
          <text class="profile-label">{{ t('agent.profile.studyAbroad') }}</text>
          <view class="profile-toggle-group">
            <view
              class="profile-toggle-btn ui-list-item"
              :class="{ 'profile-toggle-active': form.considerStudyAbroad === true }"
              @click="form.considerStudyAbroad = true"
            ><text class="profile-toggle-text">{{ t('agent.profile.yes') }}</text></view>
            <view
              class="profile-toggle-btn ui-list-item"
              :class="{ 'profile-toggle-active': form.considerStudyAbroad === false }"
              @click="form.considerStudyAbroad = false"
            ><text class="profile-toggle-text">{{ t('agent.profile.no') }}</text></view>
          </view>
        </view>
      </view>
    </view>

    <!-- submit -->
    <view class="profile-submit-wrap">
      <view
        class="profile-submit-btn"
        :class="{ 'profile-submit-loading': saving }"
        @click="saveInputs"
      >
        <text class="profile-submit-text">{{ saving ? t('agent.profile.saving') : t('agent.profile.save') }}</text>
      </view>
    </view>
    </template>
  </SlPage>
</template>

<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app';
import { computed, onMounted, ref } from 'vue';
import { useI18n } from '@/locales';
import SlNavBar from '@/style-library/components/SlNavBar.vue';
import SlPage from '@/style-library/components/SlPage.vue';
import { getAgentProfileApi, saveProfileInputsApi, refreshAgentProfileApi, getProfileInputsApi, type AgentUserProfile, type ProfileInputsRequest } from '@/api/agent';
import {
  getProfileTagsApi,
  refreshProfileTagsApi,
  saveManualProfileTagsApi,
  type ProfileTagCategory,
  type UserProfileTagSummary,
} from '@/api/profileTags';
import { getMpSafeAreaMetrics } from '@/utils/safeArea';
import { isEditableProfileTagLabel } from '@/utils/profileTagFilters';
import { useTheme } from '@/utils/theme';
import { isRealUser, requireAuth } from '@/utils/auth';

const { t } = useI18n();
const { themeClass, fontClass, refresh: refreshTheme } = useTheme();

const topSafeHeight = ref(52);
const rightAvoidWidth = ref(16);
const agentProfile = ref<AgentUserProfile | null>(null);
const profileTagSummary = ref<UserProfileTagSummary | null>(null);
const saving = ref(false);
const savingTags = ref(false);
const pageLoading = ref(true);
const pageError = ref('');
const profileReady = ref(false);
let profileLoadInFlight = false;

const ensurePageAuth = () => {
  if (isRealUser()) return true;
  profileReady.value = false;
  pageLoading.value = false;
  pageError.value = '';
  return requireAuth({
    redirect: 'reLaunch',
    cancelBehavior: 'back',
    message: '登录后才能查看和编辑你的求职画像。',
  });
};

const form = ref<ProfileInputsRequest>({
  targetCity: '',
  targetIndustry: '',
  timeline: '',
  weeklyHours: '',
  preferredDifficulty: '',
  considerGradSchool: undefined,
  considerStudyAbroad: undefined,
  careerGoalNote: '',
});

const timelineOptions = computed(() => [
  { label: t('agent.profile.timeline1m'), val: '1个月' },
  { label: t('agent.profile.timeline3m'), val: '3个月' },
  { label: t('agent.profile.timeline6m'), val: '6个月' },
  { label: t('agent.profile.timelineFull'), val: '校招季' },
  { label: t('agent.profile.timelineUnknown'), val: '不确定' },
]);

const weeklyOptions = computed(() => [
  { label: t('agent.profile.weeklyHoursLt5'), val: '3' },
  { label: t('agent.profile.weeklyHours5to10'), val: '7' },
  { label: t('agent.profile.weeklyHours10to20'), val: '15' },
  { label: t('agent.profile.weeklyHoursGt20'), val: '25' },
]);

const difficultyOptions = computed(() => [
  { label: t('agent.profile.difficultyEasy'), val: 'EASY' },
  { label: t('agent.profile.difficultyMedium'), val: 'MEDIUM' },
  { label: t('agent.profile.difficultyHard'), val: 'HARD' },
]);

const tagSections = computed(() => [
  { category: 'SKILL' as ProfileTagCategory, label: t('agent.profile.tagSkills') },
  { category: 'BACKGROUND' as ProfileTagCategory, label: t('agent.profile.tagBackground') },
  { category: 'GROWTH' as ProfileTagCategory, label: t('agent.profile.tagGrowth') },
  { category: 'GOAL' as ProfileTagCategory, label: t('agent.profile.tagGoal') },
]);

const tagDrafts = ref<Record<ProfileTagCategory, string[]>>({
  SKILL: [],
  BACKGROUND: [],
  GROWTH: [],
  GOAL: [],
});

const hydrateTagDrafts = (summary: UserProfileTagSummary | null) => {
  const next: Record<ProfileTagCategory, string[]> = {
    SKILL: [],
    BACKGROUND: [],
    GROWTH: [],
    GOAL: [],
  };
  (summary?.tags || []).forEach((tag) => {
    const category = tag.category as ProfileTagCategory;
    if (!next[category] || !tag.label || !isEditableProfileTagLabel(tag.label)) return;
    if (!next[category].includes(tag.label)) next[category].push(tag.label);
  });
  tagDrafts.value = next;
};

const levelLabel = computed(() => {
  const lvl = agentProfile.value?.personalizationLevel || 'LOW';
  if (lvl === 'HIGH') return t('agent.profile.levelHigh');
  if (lvl === 'MEDIUM') return t('agent.profile.levelMedium');
  return t('agent.profile.levelLow');
});

const clampPercent = (value: unknown) => {
  const n = Number(value ?? 0);
  if (!Number.isFinite(n)) return 0;
  return Math.max(0, Math.min(100, Math.round(n)));
};

const pctTone = (value: number) => {
  if (value >= 75) return 'metric-strong';
  if (value >= 45) return 'metric-steady';
  return 'metric-risk';
};

const targetConfidencePercent = computed(() => {
  const raw = agentProfile.value?.target?.confidence ?? 0;
  const n = Number(raw);
  return clampPercent(n <= 1 ? n * 100 : n);
});

const targetRingStyle = computed(() => ({
  background: `conic-gradient(#3f51b5 ${targetConfidencePercent.value * 3.6}deg, #e0dfdb 0deg)`,
}));

const targetRoleLabel = computed(() =>
  agentProfile.value?.target?.role || t('agent.profile.targetRoleEmpty')
);

const targetSourceLabel = computed(() => {
  const source = agentProfile.value?.target?.source;
  if (!source) return t('agent.profile.targetSourceMissing');
  return t('agent.profile.targetSource', { source });
});

const profileUpdatedAt = computed(() => {
  const d = agentProfile.value?.updatedAt || agentProfile.value?.generatedAt || '';
  return d ? d.replace('T', ' ').substring(0, 16) : '-';
});

const readinessMetrics = computed(() => {
  const r = agentProfile.value?.readiness;
  const metrics = [
    { key: 'overall', label: t('agent.profile.metricOverall'), value: clampPercent(r?.overallPercent) },
    { key: 'direction', label: t('agent.profile.metricDirection'), value: clampPercent(r?.directionClarityPercent) },
    { key: 'resume', label: t('agent.profile.metricResume'), value: clampPercent(r?.resumeReadinessPercent) },
    { key: 'interview', label: t('agent.profile.metricInterview'), value: clampPercent(r?.interviewReadinessPercent) },
    { key: 'action', label: t('agent.profile.metricAction'), value: clampPercent(r?.actionContinuityPercent) },
  ];
  return metrics.map((m) => ({ ...m, tone: pctTone(m.value) }));
});

const behaviorMetrics = computed(() => {
  const b = agentProfile.value?.behavior;
  return [
    { key: 'streak', label: t('agent.profile.behaviorStreak'), value: `${b?.streakDays ?? 0}d` },
    { key: 'weekly', label: t('agent.profile.behaviorWeekly'), value: `${b?.weeklyDays ?? 0}/7` },
    { key: 'done', label: t('agent.profile.behaviorDone'), value: `${b?.todayCompleted ?? 0}/${b?.todayTotal ?? 0}` },
    { key: 'completion', label: t('agent.profile.behaviorCompletion'), value: `${clampPercent((b?.completionRate7d ?? 0) * 100)}%` },
    { key: 'dismiss', label: t('agent.profile.behaviorDismiss'), value: `${clampPercent((b?.dismissRate7d ?? 0) * 100)}%` },
    { key: 'difficulty', label: t('agent.profile.behaviorDifficulty'), value: b?.preferredDifficulty || '-' },
  ];
});

const normalizeSkillLevel = (level: number | undefined) => {
  const n = Number(level ?? 0);
  if (!Number.isFinite(n)) return 0;
  return clampPercent(n <= 1 ? n * 100 : n);
};

const loadProfilePage = async () => {
  if (!ensurePageAuth()) return;
  if (profileLoadInFlight) return;
  profileLoadInFlight = true;
  pageLoading.value = true;
  pageError.value = '';
  try {
    const [profile, tags, inputs] = await Promise.all([
      getAgentProfileApi(),
      getProfileTagsApi(),
      getProfileInputsApi(),
    ]);
    agentProfile.value = profile;
    profileTagSummary.value = tags;
    hydrateTagDrafts(tags);
    form.value = {
      targetCity: inputs.targetCity ?? '',
      targetIndustry: inputs.targetIndustry ?? '',
      timeline: inputs.timeline ?? '',
      weeklyHours: inputs.weeklyHours ?? '',
      preferredDifficulty: inputs.preferredDifficulty ?? '',
      considerGradSchool: inputs.considerGradSchool,
      considerStudyAbroad: inputs.considerStudyAbroad,
      careerGoalNote: inputs.careerGoalNote ?? '',
    };
    profileReady.value = true;
  } catch (e: any) {
    profileReady.value = false;
    pageError.value = e?.message || t('agent.profile.loadFailed');
  } finally {
    profileLoadInFlight = false;
    pageLoading.value = false;
  }
};

onMounted(async () => {
  refreshTheme();
  const safeMetrics = getMpSafeAreaMetrics();
  topSafeHeight.value = safeMetrics.topSafeHeight;
  rightAvoidWidth.value = safeMetrics.rightAvoidWidth;
  await loadProfilePage();
});

onShow(() => {
  refreshTheme();
  // 底部保存后 navigateBack 会触发 onShow；保存过程中不要重新拉标签覆盖草稿
  if (!saving.value && !savingTags.value && profileReady.value) {
    loadProfilePage();
  }
});

const addTag = (category: ProfileTagCategory) => {
  uni.showModal({
    title: t('agent.profile.addTag'),
    editable: true,
    placeholderText: t('agent.profile.addTagPlaceholder'),
    confirmText: t('common.save'),
    cancelText: t('common.cancel'),
    success: (res: any) => {
      if (!res.confirm) return;
      const value = String(res.content || '').trim();
      if (!value) return;
      if (!tagDrafts.value[category].includes(value)) {
        tagDrafts.value[category] = [...tagDrafts.value[category], value];
      }
    },
  });
};

const removeTag = (category: ProfileTagCategory, index: number) => {
  tagDrafts.value[category] = tagDrafts.value[category].filter((_, i) => i !== index);
};

const buildManualTagsFromDrafts = () =>
  tagSections.value.flatMap((section) =>
    tagDrafts.value[section.category]
      .filter((label) => Boolean(label) && isEditableProfileTagLabel(label))
      .map((label) => ({
        category: section.category,
        label,
        weight: 80,
        source: 'USER_EDITED',
        evidence: 'user edited',
        editable: true,
      }))
  );

const saveProfileTags = async () => {
  if (!ensurePageAuth()) return;
  if (savingTags.value || !profileReady.value) return;
  savingTags.value = true;
  try {
    const saved = await saveManualProfileTagsApi(buildManualTagsFromDrafts());
    profileTagSummary.value = saved;
    hydrateTagDrafts(saved);
    uni.showToast({ title: t('agent.profile.tagsSaved'), icon: 'success' });
    // 标签变更后同步刷新 AgentProfile，让首页准备度和成长树也能感知新标签
    refreshAgentProfileApi().then((fresh) => {
      agentProfile.value = fresh;
    }).catch(() => { /* 静默失败，不影响主流程 */ });
  } catch (e: any) {
    uni.showToast({ title: e?.message || t('agent.profile.saveFailed'), icon: 'none' });
  } finally {
    savingTags.value = false;
  }
};

const refreshProfileTags = async () => {
  if (!ensurePageAuth()) return;
  if (!profileReady.value) return;
  try {
    const refreshed = await refreshProfileTagsApi();
    profileTagSummary.value = refreshed;
    hydrateTagDrafts(refreshed);
    uni.showToast({ title: t('agent.profile.tagsRefreshed'), icon: 'success' });
  } catch (e: any) {
    uni.showToast({ title: e?.message || t('agent.profile.saveFailed'), icon: 'none' });
  }
};

const saveInputs = async () => {
  if (!ensurePageAuth()) return;
  if (saving.value || !profileReady.value) return;
  saving.value = true;
  try {
    const payload: ProfileInputsRequest = {};
    if (form.value.targetCity?.trim()) payload.targetCity = form.value.targetCity.trim();
    if (form.value.targetIndustry?.trim()) payload.targetIndustry = form.value.targetIndustry.trim();
    if (form.value.timeline) payload.timeline = form.value.timeline;
    if (form.value.weeklyHours) payload.weeklyHours = form.value.weeklyHours;
    if (form.value.preferredDifficulty) payload.preferredDifficulty = form.value.preferredDifficulty;
    if (form.value.considerGradSchool !== undefined) payload.considerGradSchool = form.value.considerGradSchool;
    if (form.value.considerStudyAbroad !== undefined) payload.considerStudyAbroad = form.value.considerStudyAbroad;
    if (form.value.careerGoalNote?.trim()) payload.careerGoalNote = form.value.careerGoalNote.trim();

    // 先持久化标签，再保存求职偏好，避免并行请求后标签被旧数据覆盖
    const savedTags = await saveManualProfileTagsApi(buildManualTagsFromDrafts());
    profileTagSummary.value = savedTags;
    hydrateTagDrafts(savedTags);
    agentProfile.value = await saveProfileInputsApi(payload);
    uni.showToast({ title: t('agent.profile.saveSuccess'), icon: 'success' });
    setTimeout(() => uni.navigateBack(), 800);
  } catch (e: any) {
    uni.showToast({ title: e?.message || t('agent.profile.saveFailed'), icon: 'none' });
  } finally {
    saving.value = false;
  }
};

const goBack = () => uni.navigateBack();
</script>

<style scoped>
.profile-page {
  min-height: 100vh;
  background: var(--surface-1, #ffffff);
  padding-bottom: 120rpx;
}
.profile-banner {
  margin: 16rpx 28rpx;
  background: linear-gradient(135deg, #1d4ed8 0%, #6366f1 100%);
  border-radius: 18rpx;
  padding: 20rpx 24rpx;
}
.profile-banner-bar-wrap {
  height: 8rpx; background: rgba(255,255,255,.25); border-radius: 4rpx; overflow: hidden; margin-bottom: 10rpx;
}
.profile-banner-bar-fill {
  height: 100%; border-radius: 4rpx; transition: width .4s ease;
}
.pct-low    { background: #94a3b8; }
.pct-medium { background: #38bdf8; }
.pct-high   { background: #34d399; }
.profile-banner-label { font-size: 12px; color: rgba(255,255,255,.85); }

.profile-form { padding: 16rpx 28rpx; display: flex; flex-direction: column; gap: 20rpx; }
.profile-quant-wrap { padding-bottom: 0; }
.profile-section {
  padding: 24rpx; margin-bottom: 24rpx;
  display: flex; flex-direction: column; gap: 20rpx;
}
.profile-section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
}
.profile-section-title {
  font-size: 13px; font-weight: 700; color: var(--text-secondary, #64748b);
  border-left: 4rpx solid #1d4ed8; padding-left: 10rpx;
}
.profile-section-meta {
  font-size: 10.5px;
  color: var(--text-tertiary, #8e8e93);
  white-space: nowrap;
}
.profile-target-panel {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
  padding: 20rpx;
  border: 1rpx solid #e5e7eb;
  border-radius: 14rpx;
  background: var(--surface-2, #f8fafc);
}
.profile-target-copy {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}
.profile-target-label {
  font-size: 11px;
  font-weight: 800;
  color: var(--primary-color, #2563eb);
}
.profile-target-role {
  font-size: 18px;
  line-height: 1.2;
  font-weight: 900;
  color: var(--text-primary, #0f172a);
}
.profile-target-source {
  font-size: 11px;
  line-height: 1.35;
  color: var(--text-secondary, #64748b);
}
.profile-score-ring {
  width: 116rpx;
  height: 116rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.profile-score-ring-inner {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  background: var(--surface-1, #ffffff);
  display: flex;
  align-items: baseline;
  justify-content: center;
  padding-top: 28rpx;
  box-sizing: border-box;
}
.profile-score-ring-value {
  font-size: 18px;
  line-height: 1;
  font-weight: 900;
  color: var(--text-primary, #0f172a);
}
.profile-score-ring-unit {
  font-size: 10px;
  font-weight: 800;
  color: var(--text-secondary, #64748b);
}
.profile-readiness-grid {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}
.profile-metric {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}
.profile-metric-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
}
.profile-metric-label,
.profile-metric-value {
  font-size: 12px;
  font-weight: 800;
  color: var(--text-secondary, #64748b);
}
.profile-metric-value { color: var(--text-primary, #0f172a); }
.profile-metric-bar {
  height: 10rpx;
  background: #eef2f7;
  border-radius: 999rpx;
  overflow: hidden;
}
.profile-metric-fill {
  height: 100%;
  border-radius: 999rpx;
  transition: width .35s ease;
}
.metric-strong { background: #10b981; }
.metric-steady { background: #38bdf8; }
.metric-risk { background: #f59e0b; }
.profile-behavior-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12rpx;
}
.profile-behavior-item {
  min-height: 96rpx;
  border: 1rpx solid #e5e7eb;
  border-radius: 12rpx;
  background: var(--surface-2, #f8fafc);
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: 5rpx;
  padding: 10rpx;
}
.profile-behavior-value {
  font-size: 15px;
  line-height: 1.1;
  font-weight: 900;
  color: var(--text-primary, #0f172a);
}
.profile-behavior-label {
  font-size: 10.5px;
  line-height: 1.2;
  color: var(--text-secondary, #64748b);
  text-align: center;
}
.profile-signal-block,
.profile-skill-list,
.profile-tag-editor-block {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}
.profile-tag-editor-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12rpx;
}
.profile-small-action,
.profile-tag-add {
  border-radius: 999rpx;
  background: var(--primary-soft, #eff6ff);
  padding: 8rpx 16rpx;
}
.profile-small-action-text,
.profile-tag-add-text {
  font-size: 12px;
  font-weight: 900;
  color: var(--primary-color, #2563eb);
}
.profile-tag-add {
  width: 42rpx;
  height: 42rpx;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}
.profile-signal-title {
  font-size: 12px;
  font-weight: 800;
  color: var(--text-secondary, #64748b);
}
.profile-signal-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx;
}
.profile-signal-chip {
  border-radius: 999rpx;
  padding: 8rpx 16rpx;
  background: #eef2ff;
}
.signal-high { background: #fee2e2; }
.signal-medium { background: #fef3c7; }
.signal-low { background: #e0f2fe; }
.profile-signal-chip-text {
  font-size: 11.5px;
  font-weight: 700;
  color: var(--text-primary, #0f172a);
}
.profile-edit-chip {
  display: flex;
  align-items: center;
  gap: 8rpx;
  background: #f8fafc;
  border: 1rpx solid #e5e7eb;
}
.profile-edit-chip-x {
  font-size: 12px;
  font-weight: 900;
  color: #ef4444;
}
.profile-tags-save {
  margin-top: 4rpx;
  border-radius: 999rpx;
  background: var(--primary-color, #2563eb);
  padding: 18rpx 0;
  text-align: center;
}
.profile-tags-saving { opacity: .65; }
.profile-tags-save-text {
  font-size: 13px;
  font-weight: 900;
  color: #ffffff;
}
.profile-skill-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 160rpx 54rpx;
  align-items: center;
  gap: 12rpx;
}
.profile-skill-name {
  font-size: 12px;
  color: var(--text-primary, #0f172a);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.profile-skill-bar {
  height: 8rpx;
  border-radius: 999rpx;
  overflow: hidden;
  background: #eef2f7;
}
.profile-skill-fill {
  height: 100%;
  border-radius: 999rpx;
  background: linear-gradient(90deg, #2563eb, #14b8a6);
}
.profile-skill-score {
  font-size: 11px;
  font-weight: 800;
  color: var(--text-secondary, #64748b);
  text-align: right;
}
.profile-field { display: flex; flex-direction: column; gap: 8rpx; }
.profile-label { font-size: 12.5px; color: var(--text-secondary, #64748b); }
.profile-input {
  border: 1.5rpx solid #e5e7eb;
  border-radius: 10rpx;
  padding: 14rpx 16rpx;
  font-size: 13.5px;
  color: var(--text-primary, #0f172a);
  background: var(--surface-2, #f8fafc);
}
.profile-textarea {
  border: 1.5rpx solid #e5e7eb;
  border-radius: 10rpx;
  padding: 14rpx 16rpx;
  font-size: 13px;
  color: var(--text-primary, #0f172a);
  background: var(--surface-2, #f8fafc);
  height: 120rpx;
}
.profile-chips { display: flex; flex-wrap: wrap; gap: 10rpx; }
.profile-chip {
  border: 1.5rpx solid #d1d5db;
  border-radius: 999rpx;
  padding: 8rpx 18rpx;
  background: var(--surface-2, #f8fafc);
}
.profile-chip-active {
  border-color: var(--primary-hover, #1d4ed8);
  background: var(--primary-soft, #eff6ff);
}
.profile-chip-text { font-size: 12.5px; color: var(--text-secondary, #64748b); }
.profile-chip-active .profile-chip-text { color: var(--primary-hover, #1d4ed8); font-weight: 700; }

.profile-toggle-row {
  display: flex; align-items: center; justify-content: space-between;
}
.profile-toggle-group { display: flex; gap: 10rpx; }
.profile-toggle-btn {
  border: 1.5rpx solid #d1d5db;
  border-radius: 999rpx;
  padding: 8rpx 22rpx;
  background: var(--surface-2, #f8fafc);
}
.profile-toggle-active {
  border-color: var(--primary-hover, #1d4ed8); background: var(--primary-soft, #eff6ff);
}
.profile-toggle-text { font-size: 12.5px; color: var(--text-secondary, #64748b); }
.profile-toggle-active .profile-toggle-text { color: var(--primary-hover, #1d4ed8); font-weight: 700; }

.profile-submit-wrap {
  position: fixed; bottom: 0; left: 0; right: 0;
  padding: 20rpx 28rpx calc(32rpx + env(safe-area-inset-bottom, 0px));
  background: var(--surface-1, #ffffff);
  border-top: 1rpx solid #f1f5f9;
}
.profile-submit-btn {
  background: linear-gradient(135deg, #1d4ed8 0%, #6366f1 100%);
  border-radius: 999rpx;
  padding: 26rpx 0;
  text-align: center;
}
.profile-submit-loading { opacity: .65; }
.profile-submit-text { font-size: 15px; font-weight: 700; color: #fff; }

/* ── CareerLoop editorial skin ─────────────────────────────────────────── */
.profile-page {
  background: #faf9f6;
  color: #2c2b29;
  font-family: "Noto Sans SC", "PingFang SC", "Microsoft YaHei", sans-serif;
}

.profile-banner {
  border: 1rpx solid #e0dfdb;
  border-left: 5rpx solid #3f51b5;
  border-radius: 12rpx;
  background: #f5f5f0;
  box-shadow: 0 12rpx 32rpx rgba(44, 43, 41, 0.05);
}

.profile-banner-bar-wrap {
  background: #e0dfdb;
}

.profile-banner-label {
  color: #5a5956;
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
  letter-spacing: 0.04em;
}

.pct-low { background: #8b8a86; }
.pct-medium { background: #b8975a; }
.pct-high { background: #7b8d6e; }

.profile-section {
  border: 1rpx solid #e0dfdb;
  border-radius: 16rpx;
  background: #fffdfa;
  box-shadow: 0 12rpx 32rpx rgba(44, 43, 41, 0.05);
}

.profile-section-title,
.profile-target-role,
.profile-behavior-value {
  color: #2c2b29;
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
  font-weight: 600;
  letter-spacing: 0.05em;
}

.profile-section-title {
  border-left-color: #c23b22;
  font-size: 14px;
}

.profile-section-meta,
.profile-target-source,
.profile-behavior-label,
.profile-label,
.profile-skill-score {
  color: #8b8a86;
}

.profile-target-panel,
.profile-behavior-item {
  border-color: #e0dfdb;
  border-radius: 10rpx;
  background: #f5f5f0;
}

.profile-target-label {
  color: #3f51b5;
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
  letter-spacing: 0.05em;
}

.profile-target-role {
  font-weight: 600;
}

.profile-score-ring-inner {
  background: #fffdfa;
}

.profile-score-ring-value {
  color: #2c2b29;
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
  font-weight: 600;
}

.profile-score-ring-unit,
.profile-metric-label,
.profile-signal-title {
  color: #5a5956;
}

.profile-metric-value {
  color: #2c2b29;
}

.profile-metric-bar,
.profile-skill-bar {
  border-radius: 2rpx;
  background: #efeee9;
}

.profile-metric-fill,
.profile-skill-fill {
  border-radius: 2rpx;
}

.metric-strong { background: #7b8d6e; }
.metric-steady { background: #3f51b5; }
.metric-risk { background: #b8975a; }

.profile-small-action,
.profile-tag-add {
  border: 1rpx solid rgba(63, 81, 181, 0.34);
  border-radius: 6rpx;
  background: #efeff7;
}

.profile-small-action-text,
.profile-tag-add-text {
  color: #3f51b5;
  font-weight: 700;
}

.profile-signal-chip,
.profile-edit-chip {
  border: 1rpx solid #e0dfdb;
  border-radius: 6rpx;
  background: #f5f5f0;
}

.signal-high {
  border-color: rgba(194, 59, 34, 0.34);
  background: #f8ece8;
}

.signal-medium {
  border-color: rgba(184, 151, 90, 0.38);
  background: #f6f1e7;
}

.signal-low {
  border-color: rgba(123, 141, 110, 0.38);
  background: #eef1eb;
}

.profile-signal-chip-text,
.profile-skill-name {
  color: #2c2b29;
}

.profile-edit-chip-x {
  color: #c23b22;
}

.profile-tags-save,
.profile-submit-btn {
  border-radius: 10rpx;
  background: #c23b22;
  box-shadow: none;
}

.profile-tags-save-text,
.profile-submit-text {
  color: #fffdfa;
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
  font-weight: 600;
  letter-spacing: 0.06em;
}

.profile-skill-fill {
  background: #7b8d6e;
}

.profile-input,
.profile-textarea {
  border-color: #d8d6cf;
  border-radius: 6rpx;
  background: #faf9f6;
  color: #2c2b29;
}

.profile-chip,
.profile-toggle-btn {
  border-color: #d8d6cf;
  border-radius: 6rpx;
  background: #faf9f6;
}

.profile-chip-active,
.profile-toggle-active {
  border-color: #c23b22;
  background: #f8ece8;
}

.profile-chip-text,
.profile-toggle-text {
  color: #5a5956;
}

.profile-chip-active .profile-chip-text,
.profile-toggle-active .profile-toggle-text {
  color: #c23b22;
}

.profile-submit-wrap {
  border-top-color: #e0dfdb;
  background: rgba(250, 249, 246, 0.98);
}

.profile-page.is-dark {
  background: #0f172a;
  color: #e2e8f0;
}

.profile-page.is-dark .profile-banner,
.profile-page.is-dark .profile-section {
  border-color: #475569;
  background: #1e293b;
  box-shadow: none;
}

.profile-page.is-dark .profile-target-panel,
.profile-page.is-dark .profile-behavior-item,
.profile-page.is-dark .profile-score-ring-inner,
.profile-page.is-dark .profile-signal-chip,
.profile-page.is-dark .profile-edit-chip,
.profile-page.is-dark .profile-input,
.profile-page.is-dark .profile-textarea,
.profile-page.is-dark .profile-chip,
.profile-page.is-dark .profile-toggle-btn {
  border-color: #475569;
  background: #0f172a;
}

.profile-page.is-dark .profile-banner-bar-wrap,
.profile-page.is-dark .profile-metric-bar,
.profile-page.is-dark .profile-skill-bar {
  background: #334155;
}

.profile-page.is-dark .profile-section-title,
.profile-page.is-dark .profile-target-role,
.profile-page.is-dark .profile-score-ring-value,
.profile-page.is-dark .profile-metric-value,
.profile-page.is-dark .profile-behavior-value,
.profile-page.is-dark .profile-signal-chip-text,
.profile-page.is-dark .profile-skill-name,
.profile-page.is-dark .profile-input,
.profile-page.is-dark .profile-textarea {
  color: #f8fafc;
}

.profile-page.is-dark .profile-banner-label,
.profile-page.is-dark .profile-section-meta,
.profile-page.is-dark .profile-target-source,
.profile-page.is-dark .profile-score-ring-unit,
.profile-page.is-dark .profile-metric-label,
.profile-page.is-dark .profile-behavior-label,
.profile-page.is-dark .profile-signal-title,
.profile-page.is-dark .profile-skill-score,
.profile-page.is-dark .profile-label,
.profile-page.is-dark .profile-chip-text,
.profile-page.is-dark .profile-toggle-text {
  color: #cbd5e1;
}

.profile-page.is-dark .profile-target-label,
.profile-page.is-dark .profile-small-action-text,
.profile-page.is-dark .profile-tag-add-text {
  color: #aab3ea;
}

.profile-page.is-dark .profile-small-action,
.profile-page.is-dark .profile-tag-add {
  border-color: rgba(170, 179, 234, 0.46);
  background: rgba(63, 81, 181, 0.22);
}

.profile-page.is-dark .signal-high {
  border-color: rgba(226, 123, 102, 0.48);
  background: rgba(194, 59, 34, 0.2);
}

.profile-page.is-dark .signal-medium {
  border-color: rgba(223, 197, 143, 0.48);
  background: rgba(184, 151, 90, 0.2);
}

.profile-page.is-dark .signal-low {
  border-color: rgba(184, 198, 175, 0.48);
  background: rgba(123, 141, 110, 0.2);
}

.profile-page.is-dark .profile-chip-active,
.profile-page.is-dark .profile-toggle-active {
  border-color: #e27b66;
  background: rgba(194, 59, 34, 0.2);
}

.profile-page.is-dark .profile-chip-active .profile-chip-text,
.profile-page.is-dark .profile-toggle-active .profile-toggle-text {
  color: #e27b66;
}

.profile-page.is-dark .profile-submit-wrap {
  border-top-color: #334155;
  background: rgba(15, 23, 42, 0.98);
}

.profile-target-label,
.profile-score-ring-value,
.profile-score-ring-unit,
.profile-metric-label,
.profile-metric-value,
.profile-behavior-value,
.profile-signal-title,
.profile-signal-chip-text,
.profile-small-action-text,
.profile-tag-add-text,
.profile-tags-save-text,
.profile-skill-score,
.profile-chip-active .profile-chip-text,
.profile-toggle-active .profile-toggle-text {
  font-weight: 600;
}

.profile-state {
  margin: 32rpx 28rpx;
  min-height: 280rpx;
  padding: 40rpx 32rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
}
.profile-state-spinner {
  width: 44rpx;
  height: 44rpx;
  margin-bottom: 20rpx;
  border: 4rpx solid #e0dfdb;
  border-top-color: #c23b22;
  border-radius: 50%;
  animation: profile-spin .8s linear infinite;
}
.profile-state-title {
  color: #2c2b29;
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
  font-size: 30rpx;
  font-weight: 600;
}
.profile-state-desc {
  margin-top: 12rpx;
  color: #5a5956;
  font-size: 24rpx;
  line-height: 1.6;
}
.profile-state-retry {
  min-width: 200rpx;
  min-height: 88rpx;
  margin-top: 28rpx;
  padding: 0 28rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10rpx;
  color: #fff;
  background: #c23b22;
  font-size: 26rpx;
  font-weight: 600;
}
.profile-tag-add {
  min-width: 88rpx;
  min-height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.profile-page.is-dark .profile-state-title { color: #f8fafc; }
.profile-page.is-dark .profile-state-desc { color: #cbd5e1; }
@keyframes profile-spin { to { transform: rotate(360deg); } }
</style>
