<template>
  <view class="home-page app-gradient-bg" :class="[themeClass, fontClass]">
    <SlScrollTopBar :title="t('nav.home')" :opacity="topBarOpacity" :safe-top="topSafeHeight" :right-avoid-width="rightAvoidWidth" />
    <view class="status-spacer" :style="{ height: topSafeHeight + 'px' }"></view>

    <view class="workbench-hero" :style="{ paddingRight: rightAvoidWidth + 'px' }">
      <view class="brand-row">
        <image class="user-avatar workbench-avatar" :src="avatarSrc" mode="aspectFill" @click="handleAvatarClick" />
        <view class="brand-copy">
          <text class="brand-kicker">智绘职路 CareerLoop · 学生成长端</text>
          <text class="brand-title">{{ welcomeTitle }}</text>
          <text class="brand-subtitle">{{ stageLabel }} · {{ targetRoleLabel }}</text>
        </view>
      </view>
    </view>

    <view class="today-card app-card-soft">
      <view class="today-top">
        <view class="today-icon-wrap">
          <text class="today-icon ri-sparkling-line"></text>
        </view>
        <view class="today-copy">
          <text class="today-kicker">AI 给你的下一步</text>
          <text class="today-title">{{ primaryTask.title }}</text>
          <text class="today-desc">{{ primaryTask.desc }}</text>
        </view>
      </view>
      <view class="today-result">
        <text class="today-result-label">完成后得到</text>
        <text class="today-result-text">{{ primaryTask.outcome }}</text>
      </view>
      <view class="today-actions">
        <view class="today-cta" @click="navTo(primaryTask.target)">
          <text class="today-cta-text">{{ primaryTask.cta }}</text>
          <text class="today-cta-arrow">›</text>
        </view>
        <view class="today-plan-link" @click="navTo('/pages/agent/index')">
          <text class="today-plan-link-text">查看完整下一步</text>
        </view>
        <view
          v-if="primaryTask.taskId"
          class="today-done"
          @click="completeAgentTask(primaryTask.taskId)"
        >
          <text class="today-done-text">标记完成</text>
        </view>
      </view>
    </view>

    <view class="readiness-card app-card-soft app-surface">
      <view class="readiness-head">
        <view class="readiness-copy">
          <text class="readiness-kicker">求职准备度</text>
          <text class="readiness-title">{{ readinessPercent }}%</text>
          <text class="readiness-subtitle">{{ readinessSummary }}</text>
        </view>
      </view>
      <view class="readiness-bar">
        <view class="readiness-fill" :style="{ width: readinessPercent + '%' }"></view>
      </view>
      <view class="gap-row">
        <text class="gap-label">最大短板</text>
        <text class="gap-text">{{ biggestGap }}</text>
      </view>
      <view class="readiness-dims">
        <view v-for="d in readinessDimensions" :key="d.label" class="readiness-dim">
          <text class="readiness-dim-label">{{ d.label }}</text>
          <view class="readiness-dim-track">
            <view class="readiness-dim-fill" :style="{ width: d.value + '%' }"></view>
          </view>
          <text class="readiness-dim-val">{{ d.value }}</text>
        </view>
      </view>
      <text class="score-rule">评分依据：方向清晰度、简历诊断分、面试表现分、行动连续性和求职计划加权；仅完成动作不会直接视为准备充分。</text>
    </view>

    <view class="intake-card app-card-soft app-surface compact-card">
      <view class="section-lite-head">
        <text class="section-lite-title">你的求职画像</text>
        <text class="section-lite-action" @click="startProfileCalibration">更新校准 ›</text>
      </view>
      <view class="intake-grid">
        <view v-for="item in intakeSummary" :key="item.label" class="intake-item">
          <text class="intake-label">{{ item.label }}</text>
          <text class="intake-value">{{ item.value }}</text>
        </view>
      </view>
    </view>

    <view v-if="hasLoggedInUser || growthClusters.length" class="growth-tree-card app-card-soft app-surface">
      <view class="section-lite-head">
        <view class="section-title-stack">
          <text class="section-lite-title">成长树</text>
          <text class="section-lite-sub">求职画像的可视化表达</text>
        </view>
        <text class="section-lite-action" @click="navTo('/pages/agent/profile')">编辑画像 →</text>
      </view>
      <scroll-view v-if="growthClusters.length" class="growth-tree-scroll" scroll-x :show-scrollbar="false" enhanced>
        <view class="gtree-canvas" :style="{ width: growthCanvasWidth + 'rpx' }">

          <!-- 簇间连接线 (斜线/折线，构成树状) -->
          <view v-for="conn in growthConnectors" :key="conn.key" class="gtree-connector" :class="{ 'is-arc': conn.arc }"
            :style="{ left: conn.x1 + 'rpx', top: conn.y1 + 'rpx', width: conn.len + 'rpx', transform: 'rotate(' + conn.angle + 'rad)' }">
          </view>

          <!-- 根节点 -->
          <view class="gtree-root">
            <text class="gtree-root-name">{{ growthRootName }}</text>
            <text class="gtree-root-sub">起点</text>
          </view>

          <!-- 每个簇：节点列 + 年份标签 -->
          <template v-for="cluster in growthClusters" :key="cluster.key">
            <view class="gtree-cluster-hub" :class="'gtree-cat-' + cluster.category.toLowerCase()"
              :style="{ left: cluster.x + 'rpx', top: cluster.y + 'rpx' }">
              <text class="gtree-cluster-title">{{ cluster.title }}</text>
              <text v-if="cluster.timeLabel" class="gtree-cluster-time">{{ cluster.timeLabel }}</text>
            </view>
            <!-- 年份标签 -->
            <view v-if="cluster.timeLabel" class="gtree-year-chip"
              :style="{ left: cluster.x + 'rpx', top: (cluster.y - 58) + 'rpx' }">
              <text class="gtree-year-text">{{ cluster.timeLabel }}</text>
            </view>
            <!-- 簇内节点 (垂直叠放) -->
            <view v-for="node in cluster.nodes" :key="node.key"
              class="gtree-node" :class="'gtree-cat-' + node.category.toLowerCase()"
              :style="{ left: (cluster.x + node.dx) + 'rpx', top: (cluster.y + node.dy) + 'rpx' }">
              <text class="gtree-node-label">{{ node.label }}</text>
              <text v-if="node.time" class="gtree-node-time">{{ node.time }}</text>
            </view>
            <!-- 簇内垂直连线 -->
            <view v-for="node in cluster.nodes" :key="node.key + '-branch'" class="gtree-branch"
              :style="branchStyle(cluster, node)">
            </view>
          </template>

          <!-- 目标节点 -->
          <view class="gtree-target" :style="{ left: growthTargetX + 'rpx' }">
            <text class="gtree-target-label">目标</text>
            <text class="gtree-target-title">{{ targetRoleLabel }}</text>
          </view>

        </view>
      </scroll-view>
      <view v-else class="growth-tree-empty" @click="navTo('/pages/resume/index')">
        <text class="growth-tree-empty-title">画像还在生成中</text>
        <text class="growth-tree-empty-desc">上传或解析简历后，这里会把经历、技能和目标整理成成长树。</text>
      </view>
    </view>

    <view class="core-entry-grid">
      <view v-for="entry in coreEntries" :key="entry.label" class="core-entry app-surface" @click="navTo(entry.target)">
        <view class="core-entry-icon" :class="entry.tone">
          <text :class="entry.icon"></text>
        </view>
        <text class="core-entry-label">{{ entry.label }}</text>
        <text class="core-entry-desc">{{ entry.desc }}</text>
      </view>
    </view>

    <view class="recent-card app-card-soft app-surface compact-card">
      <view class="section-lite-head">
        <text class="section-lite-title">最近进展</text>
        <text class="section-lite-action" @click="navTo('/pages/user/index')">查看全部 ›</text>
      </view>
      <view class="progress-list">
        <view v-for="item in recentProgress" :key="item.label" class="progress-item" @click="navTo(item.target)">
          <view class="progress-item-icon" :class="item.tone">
            <text :class="item.icon"></text>
          </view>
          <view class="progress-item-copy">
            <text class="progress-item-label">{{ item.label }}</text>
            <text class="progress-item-value">{{ displayText(item.value) }}</text>
          </view>
          <text class="progress-item-arrow">›</text>
        </view>
      </view>
    </view>

    <view v-if="checkin" class="support-card app-surface" @click="navTo('/pages/checkin/index')">
      <view class="support-main">
        <text class="support-title">今日行动记录</text>
        <text class="support-desc">{{ checkin.todayCompleted }}/{{ checkin.todayTotal }} 已完成 · 连续 {{ checkin.streakDays || 0 }} 天</text>
      </view>
      <text class="support-link">查看 ›</text>
    </view>

    <view v-if="cdutInsight" class="support-card app-surface" @click="navTo('/pages/cdut-employment/index')">
      <view class="support-main">
        <text class="support-title">{{ cdutHomeTitle }}</text>
        <text class="support-desc">{{ cdutHomeDesc }}</text>
      </view>
      <text class="support-link">{{ cdutInsight.demoMode ? '演示 ›' : '就业数据 ›' }}</text>
    </view>

    <view v-if="hasResourceContent || searchQuery" class="resource-search app-surface">
      <text class="resource-search-icon ri-search-line"></text>
      <input
        class="resource-search-input"
        v-model="searchQuery"
        :placeholder="t('home.searchPlaceholder')"
        placeholder-class="search-ph"
        @confirm="onSearch"
      />
      <view class="resource-search-clear" v-if="searchQuery" @click="clearSearch">
        <text class="clear-icon">×</text>
      </view>
    </view>

    <!-- Resource content is kept, but moved below the workbench so it no longer competes with the core job-search path. -->
    <view class="section resource-section" v-if="filteredVideos.length > 0">
      <view class="section-header">
        <view class="section-titles">
          <text class="section-title">资源补充</text>
          <text class="section-meta">{{ t('home.videos') }} · {{ t('home.videosSubtitle') }}</text>
        </view>
      </view>

      <scroll-view class="hscroll" scroll-x :show-scrollbar="false">
        <view class="hscroll-track">
          <view
            class="video-card app-surface"
            v-for="(v, idx) in filteredVideos"
            :key="v.id"
            @click="openLink(v.url, v.title)"
          >
            <view class="video-cover" :class="'cover-tone-' + (idx % 4)">
              <image v-if="v.coverUrl" class="video-cover-img" :src="v.coverUrl" mode="aspectFill" />
              <view class="play-icon-wrap">
                <text class="play-icon">▶</text>
              </view>
              <view class="duration-badge" v-if="v.durationSec">
                <text class="duration-text">{{ formatDuration(v.durationSec) }}</text>
              </view>
            </view>
            <view class="video-body">
              <text class="video-title">{{ displayText(v.title) }}</text>
              <view class="video-meta-row">
                <text class="video-up">{{ v.upName || 'UP' }}</text>
                <text class="video-views" v-if="v.viewCount">· {{ formatViews(v.viewCount) }}</text>
              </view>
            </view>
          </view>
        </view>
      </scroll-view>
    </view>

    <view class="section" v-if="filteredArticles.length > 0">
      <view class="section-header">
        <view class="section-titles">
          <text class="section-title">{{ t('home.articles') }}</text>
          <text class="section-meta">{{ t('home.articlesSubtitle') }}</text>
        </view>
      </view>

      <view class="article-list">
        <view
          class="article-card app-surface"
          v-for="(a, idx) in filteredArticles"
          :key="a.id"
          @click="openArticle(a)"
        >
          <view class="article-cover article-cover-art" :class="articleCoverClass(a, idx)">
            <view class="article-cover-pattern"></view>
            <text class="article-cover-icon" :class="articleCoverIcon(a)"></text>
            <text class="article-cover-label">{{ articleCoverLabel(a) }}</text>
          </view>
          <view class="article-body">
            <text class="article-title">{{ displayText(a.title) }}</text>
            <text v-if="a.summary" class="article-summary">{{ displayText(a.summary) }}</text>
            <view class="article-tag" v-if="a.category">
              <text class="article-tag-text">{{ a.category }}</text>
            </view>
          </view>
        </view>
      </view>
    </view>

    <view class="section" v-if="filteredConsultations.length > 0">
      <view class="section-header">
        <view class="section-titles">
          <text class="section-title">{{ t('home.consultations') }}</text>
          <text class="section-meta">{{ t('home.consultationsSubtitle') }}</text>
        </view>
      </view>

      <view class="consult-list">
        <view
          class="consult-card app-surface"
          v-for="c in filteredConsultations"
          :key="c.id"
          @click="c.sourceUrl ? openConsultation(c.sourceUrl, c.title) : undefined"
          :style="c.sourceUrl ? 'cursor:pointer' : ''"
        >
          <view class="consult-head">
            <text class="consult-title">{{ displayText(c.title) }}</text>
            <text v-if="c.author" class="consult-author">{{ c.author }}</text>
          </view>
          <text v-if="c.body" class="consult-body">{{ displayText(c.body) }}</text>
          <text v-if="c.sourceUrl" class="consult-link-hint">{{ consultLinkHint(c.sourceUrl) }}</text>
        </view>
      </view>
    </view>

    <view class="section" v-if="filteredCareerCards.length > 0">
      <view class="section-header">
        <view class="section-titles">
          <text class="section-title">{{ t('home.careerPaths') }}</text>
          <text class="section-meta">{{ t('home.careerPathsSubtitle') }}</text>
        </view>
        <view class="section-more" @click="navTo('/pages/map/index')">
          <text class="section-more-text">{{ t('home.careerPathsOpen') }}</text>
          <text class="section-more-arrow">›</text>
        </view>
      </view>

      <view class="path-grid">
        <view
          class="path-card app-card-soft"
          v-for="(p, idx) in filteredCareerCards"
          :key="p.pathId"
          @click="navTo('/pages/map/index?pathId=' + p.pathId)"
        >
          <view class="path-icon-wrap" :class="'tone-' + (idx % 4)">
            <text class="path-icon-glyph ri-map-pin-user-line"></text>
          </view>
          <text class="path-name">{{ displayText(p.name) }}</text>
          <text class="path-desc">{{ displayText(p.description) }}</text>
        </view>
      </view>
    </view>

    <view class="empty-state app-empty app-surface"
          v-if="searchQuery && filteredVideos.length === 0 && filteredArticles.length === 0 && filteredConsultations.length === 0 && filteredCareerCards.length === 0">
      <text class="empty-icon ri-search-line"></text>
      <text class="empty-text">{{ t('home.searchEmpty', { q: searchQuery }) }}</text>
      <button class="btn-clear" @click="clearSearch">{{ t('home.searchClear') }}</button>
    </view>

    <view class="empty-state app-empty app-surface"
          v-if="!searchQuery && homeError">
      <text class="empty-icon ri-alert-line"></text>
      <text class="empty-text">{{ homeError }}</text>
      <button class="btn-clear" @click="loadHomeContent">{{ t('common.retry') }}</button>
    </view>

    <view class="bottom-safe"></view>

    <view v-if="calibrating" class="calibration-mask">
      <view class="calibration-panel app-surface">
        <view class="calibration-orbit">
          <view v-for="signal in calibrationSignals" :key="signal.label" class="calibration-signal" :class="signal.cls">
            <text :class="signal.icon"></text>
          </view>
          <view class="calibration-core">
            <text class="calibration-core-icon ri-user-search-line"></text>
          </view>
        </view>
        <text class="calibration-title">{{ calibrationDone ? calibrationDoneTitle : '正在根据最新记录整理画像' }}</text>
        <text class="calibration-desc">{{ calibrationDone ? calibrationDoneDesc : '测评、简历、面试、打卡和就业数据正在汇聚为新的求职画像。' }}</text>
        <view class="calibration-actions" v-if="calibrationDone">
          <view class="calibration-primary" @click="closeCalibration">
            <text class="calibration-primary-text">查看新画像</text>
          </view>
          <view class="calibration-secondary" @click="editBasicProfile">
            <text class="calibration-secondary-text">编辑基础信息</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { useI18n } from '@/locales';
import { onShow, onPullDownRefresh, onPageScroll } from '@dcloudio/uni-app';
import { openLink } from '@/utils/openLink';
import {
  getHomeContentApi,
  refreshHomeContentApi,
  type BiliVideoCard,
  type HomeArticle,
  type HomeConsultation,
  type CareerCard,
} from '@/api/home';
import { getCheckInStatusApi, type CheckInStatus } from '@/api/checkin';
import { getCdutEmploymentInsightApi, type CdutEmploymentInsight } from '@/api/cdutEmployment';
import {
  getAgentBundleApi,
  completeAgentTaskApi,
  dismissAgentTaskApi,
  refreshAgentProfileApi,
  type CareerAgentToday,
  type AgentTask,
  type AgentUserProfile,
} from '@/api/agent';
import { getProfileTagsApi, refreshProfileTagsApi, type UserProfileTag } from '@/api/profileTags';
import { isGrowthKeyword, findPortraitTagLabel, buildPortraitBackground, PORTRAIT_TAG_LABELS } from '@/utils/profileTagFilters';
import { getProfileSnapshotApi, type UserProfileSnapshot } from '@/api/user';
import { clearAuthState, isRealUser, LOGIN_PAGE, requireAuth } from '@/utils/auth';
import { readStoredOnboardingSetup } from '@/utils/onboardingGate';
import { getMpSafeAreaMetrics } from '@/utils/safeArea';
import { useTheme } from '@/utils/theme';
import { normalizeProductCopy, normalizeRoleLabel } from '@/utils/displayText';
import { selectPrimaryTask, taskCta, taskDescription, taskOutcome, taskTarget, taskTitle } from '@/utils/taskDisplay';
import SlScrollTopBar from '@/style-library/components/SlScrollTopBar.vue';

const { t } = useI18n();
const { themeClass, fontClass, refresh: refreshTheme } = useTheme();
const displayText = normalizeProductCopy;

const userInfo = ref<{
  nickname: string;
  avatarUrl: string;
  avatarViewUrl?: string;
}>({
  nickname: '',
  avatarUrl: '',
  avatarViewUrl: '',
});

const avatarSrc = computed(() => {
  if (userInfo.value.avatarViewUrl) return userInfo.value.avatarViewUrl;
  const raw = userInfo.value.avatarUrl;
  if (raw && /^https?:\/\//i.test(raw)) return raw;
  return '/static/default-avatar.png';
});

const topSafeHeight = ref(88);
const rightAvoidWidth = ref(20);
const scrollTopValue = ref(0);
const searchQuery = ref('');

const videos = ref<BiliVideoCard[]>([]);
const articles = ref<HomeArticle[]>([]);
const consultations = ref<HomeConsultation[]>([]);
const careerCards = ref<CareerCard[]>([]);
const homeError = ref('');
const checkin = ref<CheckInStatus | null>(null);
const cdutInsight = ref<CdutEmploymentInsight | null>(null);
const agentToday = ref<CareerAgentToday | null>(null);
const agentTasks = ref<AgentTask[]>([]);
const agentProfile = ref<AgentUserProfile | null>(null);
const profileTags = ref<UserProfileTag[]>([]);
const profileSnapshot = ref<UserProfileSnapshot | null>(null);
const localOnboardingSetup = ref<any>(null);
const calibrating = ref(false);
const calibrationDone = ref(false);
const calibrationHadChange = ref(false);
const calibrationSignals = [
  { label: '测评', icon: 'ri-compass-3-line', cls: 'signal-assessment' },
  { label: '简历', icon: 'ri-file-text-line', cls: 'signal-resume' },
  { label: '面试', icon: 'ri-mic-2-line', cls: 'signal-interview' },
  { label: '打卡', icon: 'ri-checkbox-circle-line', cls: 'signal-checkin' },
  { label: '就业', icon: 'ri-building-4-line', cls: 'signal-employment' },
  { label: '标签', icon: 'ri-price-tag-3-line', cls: 'signal-tags' },
];
const checkinPercent = computed(() => {
  if (!checkin.value || !checkin.value.todayTotal) return 0;
  return Math.round((checkin.value.todayCompleted / checkin.value.todayTotal) * 100);
});
const tkKey = (key: string | undefined, fallback: string) => {
  if (!key) return fallback;
  const translated = t(key);
  return translated && translated !== key ? translated : fallback;
};
const agentHeadline = computed(() => tkKey(agentToday.value?.headlineKey, agentToday.value?.headline || ''));
const agentFocus = computed(() => tkKey(agentToday.value?.focusKey, agentToday.value?.todayFocus || ''));
const agentReason = computed(() => tkKey(agentToday.value?.reasonKey, agentToday.value?.reason || ''));
const agentRiskReasons = computed(() => {
  const today = agentToday.value;
  if (!today?.riskReasons?.length) return [];
  return today.riskReasons.map((raw, i) => {
    const key = today.riskReasonKeys?.[i];
    return key ? t(key) : raw;
  });
});

const welcomeTitle = computed(() => {
  const name = userInfo.value.nickname && userInfo.value.nickname !== 'Guest' ? userInfo.value.nickname : '同学';
  return `${name}，今天推进一个关键动作`;
});

const targetRoleLabel = computed(() => {
  const role = agentProfile.value?.target?.role
    || profileSnapshot.value?.preferences?.targetRole
    || onboardingProfile.value?.targetRole
    || profileSnapshot.value?.resume?.targetJob
    || profileSnapshot.value?.interview?.positionName;
  return normalizeRoleLabel(role) || '还未锁定目标岗位';
});

const onboardingProfile = computed(() => profileSnapshot.value?.onboarding || localOnboardingSetup.value || null);

const stageLabel = computed(() => {
  const stage = agentProfile.value?.currentStage || agentToday.value?.stage || '';
  const onboardingStage = onboardingProfile.value?.stage || onboardingProfile.value?.identityType;
  if (!stage && onboardingStage) {
    const stageMap: Record<string, string> = {
      student: '在校探索',
      new_graduate: '应届求职',
      internship_seeker: '实习冲刺',
      career_switcher: '转岗定位',
      experienced: '社招提升',
    };
    return stageMap[onboardingStage] || '求职准备中';
  }
  const map: Record<string, string> = {
    DIRECTION_DISCOVERY: '探索方向',
    TARGET_ROLE_SELECTION: '选择目标岗位',
    RESUME_BOOTSTRAP: '建立简历基线',
    ASSESSMENT_BASELINE: '建立测评基线',
    INTERNSHIP_RESUME_BOOTSTRAP: '准备实习简历',
    GRADUATE_RESUME_UPLOAD: '校招简历诊断',
    CAREER_SWITCH_POSITIONING: '转岗定位',
    RESUME_IMPROVEMENT: '优化简历',
    INTERVIEW_BOOTSTRAP: '启动面试练习',
    INTERVIEW_IMPROVEMENT: '提升面试表现',
    EXECUTION_RHYTHM: '建立执行节奏',
    CAREER_MOMENTUM: '持续冲刺',
  };
  return map[stage] || '求职准备中';
});

const labelMap = {
  pain: {
    direction_unclear: '方向不清',
    resume_weak: '简历薄弱',
    project_lacking: '项目不足',
    interview_anxiety: '面试没底',
    no_plan: '缺少计划',
  },
  timeline: {
    now: '马上投递',
    within_1_month: '1 个月内',
    within_3_months: '3 个月内',
    prepare_early: '提前准备',
  },
  weekly: {
    lt_5h: '< 5 小时',
    '5_10h': '5-10 小时',
    '10_20h': '10-20 小时',
    gt_20h: '> 20 小时',
  },
  priority: {
    resume: '优先改简历',
    direction: '优先定方向',
    interview: '优先练面试',
    plan: '优先做计划',
  },
  resume: {
    ready: '已有可投简历',
    draft: '有草稿待优化',
    none: '还没有简历',
    unsure: '不确定质量',
    yes: '已有简历',
    no: '暂无简历',
  },
} as const;

const mapLabel = (group: keyof typeof labelMap, value?: string) => {
  if (!value) return '待补充';
  return (labelMap[group] as Record<string, string>)[value] || value;
};

const resolvePortraitField = (
  group: keyof typeof labelMap,
  onboardingValue?: string,
  tagLabels?: readonly string[],
) => {
  const mapped = onboardingValue ? mapLabel(group, onboardingValue) : '待补充';
  if (mapped !== '待补充') return mapped;
  return findPortraitTagLabel(profileTags.value, tagLabels || []) || '待补充';
};

const intakeSummary = computed(() => {
  const onboarding = onboardingProfile.value || {};
  const background = buildPortraitBackground(
    profileTags.value,
    onboarding.education?.school,
    onboarding.education?.major,
  );
  return [
    { label: '当前痛点', value: resolvePortraitField('pain', onboarding.painPoint, PORTRAIT_TAG_LABELS.pain) },
    { label: '简历状态', value: resolvePortraitField('resume', onboarding.resumeStatus || onboarding.hasResume, PORTRAIT_TAG_LABELS.resume) },
    { label: '求职时间线', value: resolvePortraitField('timeline', onboarding.timeline, PORTRAIT_TAG_LABELS.timeline) },
    { label: '每周投入', value: resolvePortraitField('weekly', onboarding.weeklyAvailability, PORTRAIT_TAG_LABELS.weekly) },
    { label: '优先帮助', value: resolvePortraitField('priority', onboarding.priorityHelp, PORTRAIT_TAG_LABELS.priority) },
    { label: '背景', value: background },
  ];
});

const clampPercent = (value?: number) => {
  const normalized = Number(value ?? 0);
  if (!Number.isFinite(normalized)) return 0;
  return Math.max(0, Math.min(100, Math.round(normalized)));
};

const fallbackReadinessMetrics = () => {
  const snap = profileSnapshot.value;
  const directionClarity = Math.min(100, (targetRoleLabel.value !== '还未锁定目标岗位' ? 60 : 0) + (snap?.assessment ? 40 : 0));
  const resumeReadiness = snap?.resume
    ? clampPercent(snap.resume.diagnosisScore ?? 35)
    : 0;
  const interviewReadiness = snap?.interview
    ? clampPercent(snap.interview.lastScore ?? 30)
    : 0;
  const actionContinuity = Math.min(100, (checkin.value?.weeklyDays ?? 0) * 20 + ((checkin.value?.todayCompleted ?? 0) > 0 ? 20 : 0));
  const overall = clampPercent(
    directionClarity * 0.2
      + resumeReadiness * 0.3
      + interviewReadiness * 0.3
      + actionContinuity * 0.2,
  );
  return { directionClarity, resumeReadiness, interviewReadiness, actionContinuity, overall };
};

const readinessPercent = computed(() => {
  if (agentProfile.value?.readiness?.overallPercent !== undefined) {
    return clampPercent(agentProfile.value.readiness.overallPercent);
  }
  if (agentToday.value?.progressPercent !== undefined) {
    return clampPercent(agentToday.value.progressPercent);
  }
  return fallbackReadinessMetrics().overall;
});

const readinessDimensions = computed(() => {
  const readiness = agentProfile.value?.readiness;
  if (readiness) {
    return [
      { label: '方向', value: clampPercent(readiness.directionClarityPercent ?? 0) },
      { label: '简历', value: clampPercent(readiness.resumeReadinessPercent ?? 0) },
      { label: '面试', value: clampPercent(readiness.interviewReadinessPercent ?? 0) },
      { label: '行动', value: clampPercent(readiness.actionContinuityPercent ?? 0) },
    ];
  }
  const metrics = fallbackReadinessMetrics();
  return [
    { label: '方向', value: metrics.directionClarity },
    { label: '简历', value: metrics.resumeReadiness },
    { label: '面试', value: metrics.interviewReadiness },
    { label: '行动', value: metrics.actionContinuity },
  ];
});

const readinessSummary = computed(() => {
  const readiness = agentProfile.value?.readiness;
  if (readiness) {
    const direction = readiness.directionClarityPercent ?? 0;
    const resume = readiness.resumeReadinessPercent ?? 0;
    const interview = readiness.interviewReadinessPercent ?? 0;
    const action = readiness.actionContinuityPercent ?? 0;
    const dimensions = [
      { label: '方向清晰度', value: direction },
      { label: '简历可投递度', value: resume },
      { label: '面试准备度', value: interview },
      { label: '行动连续性', value: action },
    ];
    const weakest = dimensions.sort((a, b) => a.value - b.value)[0];
    if (weakest && weakest.value < 60) return `${weakest.label}还需要补齐，今天先推进最关键的一步。`;
  }
  if (readinessPercent.value >= 80) return '已经接近可投递/可面试状态，重点做最后验证。';
  if (readinessPercent.value >= 55) return '已有基础信号，下一步要补齐最短板。';
  if (readinessPercent.value >= 25) return '已经开始准备，但还缺少关键材料和练习。';
  return '先确定方向，再建立测评、简历和面试基线。';
});

const biggestGap = computed(() => {
  const readiness = agentProfile.value?.readiness;
  if (readiness) {
    const dimensions = [
      { label: '方向清晰度', value: readiness.directionClarityPercent },
      { label: '简历可投递度', value: readiness.resumeReadinessPercent },
      { label: '面试准备度', value: readiness.interviewReadinessPercent },
      { label: '行动连续性', value: readiness.actionContinuityPercent },
    ].filter((item) => item.value !== undefined) as Array<{ label: string; value: number }>;
    if (dimensions.length) {
      const weakest = dimensions.sort((a, b) => a.value - b.value)[0];
      if (weakest.value < 60) return weakest.label;
    }
  }
  if (agentProfile.value?.missingSignals?.length) {
    return agentProfile.value.missingSignals[0].label;
  }
  const snap = profileSnapshot.value;
  if (targetRoleLabel.value === '还未锁定目标岗位') return '目标岗位不清晰';
  if (!snap?.assessment) return '缺少测评画像';
  if (!snap?.resume) return '缺少针对目标岗位的简历';
  if (!snap?.interview) return '缺少一次模拟面试验证';
  if (agentRiskReasons.value.length) return agentRiskReasons.value[0];
  return '继续用今日任务保持节奏';
});
const homeUserId = ref('');

const growthRootName = computed(() => (
  userInfo.value.nickname && userInfo.value.nickname !== 'Guest' ? userInfo.value.nickname : '同学'
));
const hasLoggedInUser = computed(() => {
  const uid = Number(homeUserId.value || uni.getStorageSync('userId'));
  return uid > 0;
});

const TREE_ROOT = { x: 172, y: 112 };
const TREE_TARGET_Y = 112;
const CLUSTER_Y = [78, 134, 104, 152];
const NODE_OFFSETS = [
  { dx: -36, dy: -48 },
  { dx: 64, dy: -34 },
  { dx: -60, dy: 42 },
  { dx: 72, dy: 44 },
];

interface GrowthCluster {
  key: string;
  timeLabel: string;
  title: string;
  category: string;
  nodes: Array<{ key: string; label: string; category: string; time: string; dx: number; dy: number }>;
  x: number;   // 节点列中心 X
  y: number;   // 首节点中心 Y（所在泳道）
  lane: number; // 0 顶 | 1 中 | 2 底
}

interface GrowthConnector {
  key: string;
  x1: number; y1: number;
  x2: number; y2: number;
  len: number;
  angle: number;
  arc?: boolean;
}

const growthClusters = computed<GrowthCluster[]>(() => {
  const order: Record<string, number> = { BACKGROUND: 1, SKILL: 2, GROWTH: 3, GOAL: 4 };
  const seen = new Set<string>();
  const filtered = profileTags.value
    .filter((tag) => tag.category !== 'GOAL')
    .filter((tag) => {
      const label = String(tag.label || '').trim();
      if (!isGrowthKeyword(label) || seen.has(label.toLowerCase())) return false;
      seen.add(label.toLowerCase());
      return true;
    })
    .sort((a, b) => {
      const timeA = extractNodeTime(a.label);
      const timeB = extractNodeTime(b.label);
      if (timeA && timeB && timeA !== timeB) return timeA.localeCompare(timeB);
      if (timeA && !timeB) return -1;
      if (!timeA && timeB) return 1;
      return (order[a.category] || 9) - (order[b.category] || 9) || (b.weight || 0) - (a.weight || 0);
    });

  const buckets: Array<{ key: string; tags: typeof filtered }> = [];
  for (const tag of filtered) {
    const time = extractNodeTime(tag.label);
    const key = time ? time.slice(0, 4) : semanticClusterKey(tag);
    const existing = buckets.find((b) => b.key === key && b.tags.length < 4);
    if (existing) existing.tags.push(tag);
    else buckets.push({ key, tags: [tag] });
  }

  const capped = buckets
    .sort((a, b) => bucketSortKey(a).localeCompare(bucketSortKey(b)))
    .slice(0, 7);
  const n = capped.length;
  const spacing = n > 0 ? Math.max(190, Math.min(238, 1420 / n)) : 210;

  return capped.map(({ key: cKey, tags }, idx) => {
    const x = Math.round(270 + idx * spacing);
    const lane = idx % CLUSTER_Y.length;
    const y = CLUSTER_Y[lane];
    const sortedTags = [...tags].sort((a, b) => (b.weight || 0) - (a.weight || 0));
    return {
      key: cKey + '-' + idx,
      timeLabel: clusterTimeLabel(sortedTags),
      title: clusterTitle(sortedTags),
      category: dominantCategory(sortedTags),
      nodes: sortedTags.map((t, ni) => ({
        key: `n-${idx}-${ni}`,
        label: cleanNodeLabel(t.label),
        category: t.category,
        time: extractNodeTime(t.label),
        dx: NODE_OFFSETS[ni % NODE_OFFSETS.length].dx,
        dy: NODE_OFFSETS[ni % NODE_OFFSETS.length].dy,
      })),
      x, y, lane,
    };
  });
});

const growthConnectors = computed<GrowthConnector[]>(() => {
  const clusters = growthClusters.value;
  if (!clusters.length) return [];
  const makeConn = (key: string, x1: number, y1: number, x2: number, y2: number, arc = true): GrowthConnector => {
    const dx = x2 - x1, dy = y2 - y1;
    return { key, x1, y1, x2, y2, len: Math.round(Math.sqrt(dx * dx + dy * dy)), angle: Math.atan2(dy, dx), arc };
  };
  const conns: GrowthConnector[] = [];
  conns.push(makeConn('root', TREE_ROOT.x, TREE_ROOT.y, clusters[0].x - 76, clusters[0].y));
  for (let i = 0; i < clusters.length - 1; i++) {
    conns.push(makeConn(`c${i}`, clusters[i].x + 76, clusters[i].y, clusters[i + 1].x - 76, clusters[i + 1].y));
  }
  const last = clusters[clusters.length - 1];
  conns.push(makeConn('tgt', last.x + 76, last.y, growthTargetX.value, TREE_TARGET_Y, false));
  return conns;
});

const growthCanvasWidth = computed(() => {
  if (!growthClusters.value.length) return 1040;
  return Math.max(1040, growthTargetX.value + 230);
});

const growthTargetX = computed(() => {
  if (!growthClusters.value.length) return 820;
  return growthClusters.value[growthClusters.value.length - 1].x + 210;
});

const branchStyle = (
  cluster: GrowthCluster,
  node: GrowthCluster['nodes'][number],
) => {
  const dx = node.dx;
  const dy = node.dy;
  const len = Math.max(34, Math.round(Math.sqrt(dx * dx + dy * dy)) - 28);
  const angle = Math.atan2(dy, dx);
  return {
    left: cluster.x + 'rpx',
    top: cluster.y + 'rpx',
    width: len + 'rpx',
    transform: `rotate(${angle}rad)`,
  };
};

const extractNodeTime = (label?: string) => {
  const match = String(label || '').match(/(?:19|20)\d{2}(?:\.\d{1,2})?/);
  return match?.[0] || '';
};

const semanticClusterKey = (tag: UserProfileTag) => {
  const label = cleanNodeLabel(tag.label);
  if (/项目|实践|比赛|实习|工作|经历/.test(label)) return 'experience';
  if (/课程|专业|学校|学院|本科|硕士|博士|排名/.test(label)) return 'education';
  if (/学习|探索|准备|提升|训练|成长/.test(label)) return 'growth';
  return String(tag.category || 'OTHER');
};

const bucketSortKey = (bucket: { key: string; tags: UserProfileTag[] }) => {
  const time = bucket.tags.map((tag) => extractNodeTime(tag.label)).find(Boolean);
  if (time) return `0-${time}`;
  const first = bucket.tags[0];
  const order: Record<string, number> = { BACKGROUND: 1, SKILL: 2, GROWTH: 3, GOAL: 4 };
  return `1-${order[first?.category] || 9}-${bucket.key}`;
};

const dominantCategory = (tags: UserProfileTag[]) => {
  const scores = new Map<string, number>();
  for (const tag of tags) scores.set(tag.category, (scores.get(tag.category) || 0) + (tag.weight || 1));
  return [...scores.entries()].sort((a, b) => b[1] - a[1])[0]?.[0] || 'GROWTH';
};

const clusterTimeLabel = (tags: UserProfileTag[]) => {
  const years = [...new Set(tags.map((tag) => extractNodeTime(tag.label).slice(0, 4)).filter(Boolean))];
  if (!years.length) return '';
  if (years.length === 1) return years[0];
  return `${years[0]}-${years[years.length - 1]}`;
};

const clusterTitle = (tags: UserProfileTag[]) => {
  const time = clusterTimeLabel(tags);
  if (time) return time;
  const cat = dominantCategory(tags);
  if (cat === 'BACKGROUND') return '背景';
  if (cat === 'SKILL') return '技能';
  if (cat === 'GROWTH') return '探索';
  return '阶段';
};
// isGrowthKeyword 已提取到 @/utils/profileTagFilters

// 去掉标签里的日期前缀，只保留有意义的文字部分
const cleanNodeLabel = (label: string): string => {
  return String(label || '').replace(/^(?:19|20)\d{2}(?:[.年\-\/]\d{0,2})?[年月]?\s*/, '').trim() || String(label || '').trim();
};

interface HomeTaskView {
  title: string;
  desc: string;
  outcome: string;
  cta: string;
  target: string;
  taskId?: number;
}

const primaryTask = computed<HomeTaskView>(() => {
  const task = selectPrimaryTask(agentTasks.value, agentToday.value?.actions || []);
  if (task) {
    return {
      title: agentHeadline.value || taskTitle(task),
      desc: agentFocus.value || taskDescription(task),
      outcome: taskOutcome(task),
      cta: taskCta(task),
      target: taskTarget(task),
      taskId: task.taskId,
    };
  }

  const snap = profileSnapshot.value;
  const onboarding = onboardingProfile.value || {};
  if (onboarding.priorityHelp === 'plan' || onboarding.painPoint === 'no_plan') {
    return {
      title: '先生成一份本周求职行动表',
      desc: '把你的目标、时间线和每周投入拆成可执行任务，避免只收藏资料不推进。',
      outcome: '本周重点 + 今日任务 + 后续行动顺序',
      cta: '查看计划',
      target: '/pages/agent/index',
    };
  }
  if (targetRoleLabel.value === '还未锁定目标岗位' || onboarding.priorityHelp === 'direction' || onboarding.painPoint === 'direction_unclear' || !snap?.assessment) {
    return {
      title: '先完成一次职业测评',
      desc: '用测评结果建立第一份职业画像，并把模糊方向收敛到可准备的岗位。',
      outcome: '目标方向 + 推荐岗位 + 下一步准备建议',
      cta: '开始测评',
      target: '/pages/assessment/index',
    };
  }
  if (!snap?.resume || onboarding.priorityHelp === 'resume' || ['resume_weak', 'project_lacking'].includes(onboarding.painPoint)) {
    return {
      title: `为「${targetRoleLabel.value}」准备简历`,
      desc: '上传或创建简历，再用目标岗位做匹配诊断，优先补经历证据和关键词。',
      outcome: '简历匹配分数 + 项目/关键词修改建议',
      cta: '匹配诊断',
      target: '/pages/resume-ai/index',
    };
  }
  if (!snap?.interview || onboarding.priorityHelp === 'interview' || onboarding.painPoint === 'interview_anxiety') {
    return {
      title: `练一次「${targetRoleLabel.value}」模拟面试`,
      desc: '用目标岗位做一次 10 分钟练习，找到真实表达短板。',
      outcome: '面试分数 + 优势/待改进维度',
      cta: '开始面试',
      target: `/pages/interview/start?suggestedRole=${encodeURIComponent(targetRoleLabel.value)}`,
    };
  }
  return {
    title: agentHeadline.value || '复盘最近一次求职准备',
    desc: agentReason.value || '根据最近的简历、测评和面试记录，整理下一步行动。',
    outcome: '一份更清晰的本周求职行动计划',
    cta: '查看计划',
    target: '/pages/agent/index',
  };
});

const coreEntries = computed(() => [
  {
    label: '测评',
    desc: profileSnapshot.value?.assessment ? '更新画像' : '建立画像',
    icon: 'ri-compass-3-line',
    tone: 'core-tone-blue',
    target: '/pages/assessment/index',
  },
  {
    label: '简历',
    desc: profileSnapshot.value?.resume?.diagnosisScore ? `${profileSnapshot.value.resume.diagnosisScore} 分` : 'JD 匹配',
    icon: 'ri-file-text-line',
    tone: 'core-tone-pink',
    target: '/pages/resume-ai/index',
  },
  {
    label: '面试',
    desc: profileSnapshot.value?.interview?.lastScore ? `${profileSnapshot.value.interview.lastScore} 分` : '10 分钟练习',
    icon: 'ri-mic-2-line',
    tone: 'core-tone-orange',
    target: '/pages/interview/start',
  },
]);

const recentProgress = computed(() => {
  const snap = profileSnapshot.value;
  return [
    {
      label: '目标岗位',
      value: targetRoleLabel.value,
      icon: 'ri-focus-2-line',
      tone: 'progress-blue',
      target: '/pages/agent/index',
    },
    {
      label: '最近测评',
      value: snap?.assessment?.summary || snap?.assessment?.scaleTitle || '还没有测评记录',
      icon: 'ri-brain-line',
      tone: 'progress-violet',
      target: '/pages/assessment/index',
    },
    {
      label: '最近简历',
      value: snap?.resume?.diagnosisScore ? `匹配分 ${snap.resume.diagnosisScore}/100` : (snap?.resume?.title || '还没有简历诊断'),
      icon: 'ri-file-text-line',
      tone: 'progress-pink',
      target: '/pages/resume/index',
    },
    {
      label: '最近面试',
      value: snap?.interview?.lastScore ? `${snap.interview.positionName || '模拟面试'} · ${snap.interview.lastScore}/100` : '还没有面试练习',
      icon: 'ri-mic-2-line',
      tone: 'progress-orange',
      target: '/pages/interview/history',
    },
  ];
});

// Search filters every section so the home page works as a quick triage tool.
const matches = (haystack: string | undefined | null, q: string) =>
  !!haystack && haystack.toLowerCase().includes(q);

const filterBySearch = <T>(list: T[]) => {
  if (!searchQuery.value) return list;
  const q = searchQuery.value.toLowerCase();
  return list.filter((item: any) => {
    if (item.title) return matches(item.title, q);
    if (item.upName) return matches(item.upName, q);
    if (item.keyword) return matches(item.keyword, q);
    if (item.summary) return matches(item.summary, q);
    if (item.category) return matches(item.category, q);
    if (item.body) return matches(item.body, q);
    if (item.author) return matches(item.author, q);
    return false;
  });
};

const filteredVideos = computed(() => filterBySearch(videos.value));
const filteredArticles = computed(() => filterBySearch(articles.value));
const topBarOpacity = computed(() => Math.min(1, Math.max(0, (scrollTopValue.value - 12) / 56)));

const extractOrigin = (raw?: string): string => {
  const match = raw?.match(/^https?:\/\/[^/]+/i);
  return match?.[0] || '';
};

const extractHost = (raw?: string): string => {
  return extractOrigin(raw).replace(/^https?:\/\//i, '').replace(/^www\./i, '');
};

const articleSourceText = (a: HomeArticle): string => {
  const anyArticle = a as HomeArticle & { source?: string; sourceName?: string; publisher?: string };
  return [
    anyArticle.sourceName,
    anyArticle.source,
    anyArticle.publisher,
    extractHost(a.sourceUrl || a.url),
    a.category,
  ].filter(Boolean).join(' ').toLowerCase();
};

const articleTopicText = (a: HomeArticle): string =>
  [a.title, a.summary, a.category, articleSourceText(a)].filter(Boolean).join(' ').toLowerCase();

const articleCoverMeta = (a: HomeArticle) => {
  const text = articleTopicText(a);
  if (/简历|resume|cv/.test(text)) return { label: '简历', icon: 'ri-file-text-line', tone: 'resume' };
  if (/面试|interview|mock/.test(text)) return { label: '面试', icon: 'ri-mic-2-line', tone: 'interview' };
  if (/规划|职业|career|方向/.test(text)) return { label: '规划', icon: 'ri-compass-3-line', tone: 'career' };
  if (/编程|代码|开发|java|python|前端|后端|算法|coding|developer/.test(text)) return { label: '编程', icon: 'ri-code-s-slash-line', tone: 'code' };
  if (/数据|分析|bi|sql|算法|趋势|data|analytics/.test(text)) return { label: '数据', icon: 'ri-bar-chart-box-line', tone: 'data' };
  if (/就业|校招|招聘|岗位|offer|趋势|employment/.test(text)) return { label: '就业', icon: 'ri-building-4-line', tone: 'employment' };
  if (/沟通|表达|汇报|presentation|communication/.test(text)) return { label: '表达', icon: 'ri-chat-voice-line', tone: 'comm' };
  return { label: '学习', icon: 'ri-graduation-cap-line', tone: 'default' };
};

const articleCoverClass = (a: HomeArticle, idx: number): string =>
  `cover-topic-${articleCoverMeta(a).tone} cover-tone-${idx % 4}`;

const articleCoverIcon = (a: HomeArticle): string => articleCoverMeta(a).icon;

const articleCoverLabel = (a: HomeArticle): string => articleCoverMeta(a).label;

const filteredConsultations = computed(() => {
  if (!searchQuery.value) return consultations.value;
  const q = searchQuery.value.toLowerCase();
  return consultations.value.filter(c => matches(c.title, q) || matches(c.body, q) || matches(c.author, q));
});

const filteredCareerCards = computed(() => {
  if (!searchQuery.value) return careerCards.value;
  const q = searchQuery.value.toLowerCase();
  return careerCards.value.filter(p => matches(p.name, q) || matches(p.description, q));
});

const hasResourceContent = computed(() =>
  videos.value.length > 0
  || articles.value.length > 0
  || consultations.value.length > 0
  || careerCards.value.length > 0
);

const onSearch = () => {
  uni.hideKeyboard();
};

const clearSearch = () => {
  searchQuery.value = '';
};

const formatDuration = (sec: number): string => {
  if (!sec || sec < 0) return '';
  const m = Math.floor(sec / 60);
  const s = sec % 60;
  return `${m}:${String(s).padStart(2, '0')}`;
};

const formatViews = (n: number): string => {
  if (n >= 100_000_000) return (n / 100_000_000).toFixed(1) + '亿';
  if (n >= 10_000) return (n / 10_000).toFixed(1) + '万';
  if (n >= 1000) return (n / 1000).toFixed(1) + 'k';
  return String(n);
};

const formatPercent = (n?: number): string => {
  if (n === undefined || n === null || isNaN(Number(n))) return '-';
  const value = Number(n);
  return `${Number.isInteger(value) ? value.toFixed(0) : value.toFixed(1)}%`;
};

const cdutHomeTitle = computed(() => {
  if (!cdutInsight.value) return t('cdut.homeTitle');
  if (cdutInsight.value.demoMode) return '就业数据演示';
  return cdutInsight.value.school ? `${cdutInsight.value.school}就业数据` : t('cdut.homeTitle');
});

const cdutHomeDesc = computed(() => {
  if (!cdutInsight.value) return '';
  if (cdutInsight.value.demoMode) {
    const employment = formatPercent(cdutInsight.value.latestEmploymentRate);
    const postgrad = formatPercent(cdutInsight.value.latestPostgraduateRate);
    const materials = cdutInsight.value.sourceCount || 5;
    return `${cdutInsight.value.matchLabel} · 落实率 ${employment} · 深造率 ${postgrad} · ${materials} 条演示材料`;
  }
  return `${cdutInsight.value.matchLabel} · ${cdutInsight.value.latestYear || t('cdut.publicSources')}`;
});

const openArticle = (a: HomeArticle) => {
  if (!a.url) {
    uni.showToast({ title: t('home.noLinkAttached'), icon: 'none' });
    return;
  }
  if (a.url.startsWith('/pages/')) {
    navTo(a.url);
  } else {
    openLink(a.url, a.title);
  }
};

const loadHomeContent = async (): Promise<boolean> => {
  homeError.value = '';
  try {
    const data = await getHomeContentApi(isRealUser());

    videos.value = data?.videos || [];
    articles.value = data?.articles || [];
    consultations.value = data?.consultations || [];
    careerCards.value = data?.careerCards || [];
    return true;
  } catch {
    // Preserve the last successfully rendered feed during a transient outage.
    homeError.value = t('home.contentLoadFailed');
    return false;
  }
};

// Best-effort: failing to load streak should never break the home feed.
const loadCheckin = async (): Promise<boolean> => {
  const uid = Number(uni.getStorageSync('userId'));
  if (!uid || uid <= 0) {
    checkin.value = null;
    return true;
  }
  try {
    checkin.value = await getCheckInStatusApi();
    return true;
  } catch {
    return false;
  }
};

const loadAgentToday = async (): Promise<boolean> => {
  const uid = Number(uni.getStorageSync('userId'));
  if (!uid || uid <= 0) {
    agentToday.value = null;
    agentTasks.value = [];
    agentProfile.value = null;
    return true;
  }
  try {
    const bundle = await getAgentBundleApi();
    agentToday.value = bundle.today;
    agentTasks.value = (bundle.tasks || []).filter((task) => task.status === 'TODO');
    agentProfile.value = bundle.profile;
    return true;
  } catch {
    return false;
  }
};

const loadCdutInsight = async (): Promise<boolean> => {
  const uid = Number(uni.getStorageSync('userId'));
  if (!uid || uid <= 0) {
    cdutInsight.value = null;
    return true;
  }
  try {
    cdutInsight.value = await getCdutEmploymentInsightApi();
    return true;
  } catch {
    return false;
  }
};

const loadProfileSnapshot = async (): Promise<boolean> => {
  const uid = Number(uni.getStorageSync('userId'));
  if (!uid || uid <= 0) {
    profileSnapshot.value = null;
    return true;
  }
  try {
    profileSnapshot.value = await getProfileSnapshotApi();
    return true;
  } catch {
    return false;
  }
};

const loadLocalOnboarding = () => {
  localOnboardingSetup.value = readStoredOnboardingSetup();
};

const loadProfileTags = async (): Promise<boolean> => {
  const uid = Number(uni.getStorageSync('userId'));
  if (!uid || uid <= 0) {
    profileTags.value = [];
    return true;
  }
  try {
    const summary = await getProfileTagsApi();
    profileTags.value = summary.tags || [];
    return true;
  } catch {
    return false;
  }
};

let dashboardLoadInFlight: Promise<boolean[]> | null = null;
const loadDashboard = (): Promise<boolean[]> => {
  if (dashboardLoadInFlight) return dashboardLoadInFlight;
  dashboardLoadInFlight = Promise.all([
    loadCheckin(),
    loadCdutInsight(),
    loadAgentToday(),
    loadProfileTags(),
    loadProfileSnapshot(),
  ]).finally(() => {
    dashboardLoadInFlight = null;
  });
  return dashboardLoadInFlight;
};

const completeAgentTask = async (taskId: number) => {
  try {
    await completeAgentTaskApi(taskId);
    agentTasks.value = agentTasks.value.filter((task) => task.taskId !== taskId);
    uni.showToast({ title: '已完成，下一步已更新', icon: 'success' });
    await Promise.allSettled([loadAgentToday(), loadProfileSnapshot(), loadProfileTags(), loadCheckin()]);
  } catch {
    uni.showToast({ title: t('common.failed'), icon: 'none' });
  }
};

const dismissAgentTask = async (taskId: number) => {
  try {
    await dismissAgentTaskApi(taskId);
    agentTasks.value = agentTasks.value.filter((task) => task.taskId !== taskId);
    uni.showToast({ title: t('home.taskSkipped'), icon: 'none' });
  } catch {
    uni.showToast({ title: t('common.failed'), icon: 'none' });
  }
};

const profileSignature = () => JSON.stringify({
  snapshot: profileSnapshot.value,
  tags: profileTags.value.map((tag) => `${tag.category}:${tag.label}:${tag.weight}`).sort(),
  profile: agentProfile.value,
  today: agentToday.value,
});

const calibrationDoneTitle = computed(() => calibrationHadChange.value ? '已根据最新信息更新画像' : '当前画像已是最新');
const calibrationDoneDesc = computed(() => calibrationHadChange.value
  ? '首页画像、准备度、成长树和今日任务已经同步刷新。'
  : '暂时没有发现新的测评、简历、面试或行动记录。');

const startProfileCalibration = async () => {
  if (calibrating.value) return;
  const before = profileSignature();
  calibrating.value = true;
  calibrationDone.value = false;
  calibrationHadChange.value = false;
  try {
    const results = await Promise.allSettled([
      refreshAgentProfileApi(),
      refreshProfileTagsApi(),
      getCdutEmploymentInsightApi(),
    ]);
    for (const result of results) {
      if (result.status === 'fulfilled') {
        if ('tags' in (result.value as any)) profileTags.value = (result.value as any).tags || [];
        if ('target' in (result.value as any)) agentProfile.value = result.value as AgentUserProfile;
        if ('school' in (result.value as any)) cdutInsight.value = result.value as CdutEmploymentInsight;
      }
    }
    await Promise.allSettled([loadAgentToday(), loadProfileSnapshot(), loadProfileTags(), loadCheckin(), loadCdutInsight()]);
    calibrationHadChange.value = before !== profileSignature();
  } catch {
    calibrationHadChange.value = false;
  } finally {
    calibrationDone.value = true;
  }
};

const closeCalibration = () => {
  calibrating.value = false;
  calibrationDone.value = false;
};

const editBasicProfile = () => {
  closeCalibration();
  navTo('/pages/onboarding/index');
};

const syncUserFromStorage = () => {
  homeUserId.value = String(uni.getStorageSync('userId') || '');
  const info = uni.getStorageSync('userInfo');
  userInfo.value = info
    ? { avatarUrl: '', avatarViewUrl: '', nickname: '', ...info }
    : { nickname: '', avatarUrl: '', avatarViewUrl: '' };
};

onMounted(() => {
  syncUserFromStorage();
  refreshTheme();
  loadLocalOnboarding();
  const safeMetrics = getMpSafeAreaMetrics();
  topSafeHeight.value = safeMetrics.topSafeHeight;
  rightAvoidWidth.value = safeMetrics.rightAvoidWidth;
  loadHomeContent();
});

onShow(() => {
  syncUserFromStorage();
  refreshTheme();
  loadLocalOnboarding();
  // Refresh streak on tab return so finishing an interview/assessment
  // immediately bumps the chip without requiring a pull-to-refresh.
  void loadDashboard();
});

onPullDownRefresh(async () => {
  try {
    // Fire-and-forget: trigger a fresh Bilibili pull in the background.
    // We deliberately do NOT await this — a 429 rate-limit or network
    // hiccup must never prevent the local content from reloading.
    if (isRealUser()) {
      refreshHomeContentApi().catch(() => {/* rate-limited or offline, ignore */});
    }
    loadLocalOnboarding();
    const [homeLoaded, dashboardResults] = await Promise.all([
      loadHomeContent(),
      loadDashboard(),
    ]);
    const fullyRefreshed = homeLoaded && dashboardResults.every(Boolean);
    uni.showToast({
      title: fullyRefreshed ? t('common.refreshed') : t('common.partialRefresh'),
      icon: fullyRefreshed ? 'success' : 'none',
    });
  } catch {
    uni.showToast({ title: t('common.refreshFailed'), icon: 'none' });
  } finally {
    uni.stopPullDownRefresh();
  }
});

onPageScroll(({ scrollTop }) => {
  scrollTopValue.value = scrollTop;
});

const navTo = (url: string) => {
  if (!url) {
    uni.showToast({ title: '暂时无法打开，请稍后重试', icon: 'none' });
    return;
  }
  const base = url.split('?')[0];
  if (PROTECTED_PAGE_PATHS.has(base) && !requireAuth()) return;
  if (SWITCH_TAB_PATHS.has(base)) {
    uni.switchTab({
      url: base,
      fail: () => uni.showToast({ title: '暂时无法打开，请稍后重试', icon: 'none' }),
    });
    return;
  }
  uni.navigateTo({
    url,
    fail: () => uni.showToast({ title: '暂时无法打开，请稍后重试', icon: 'none' }),
  });
};

/** Tab roots from pages.json — use switchTab so MP doesn’t open a webview. */
const SWITCH_TAB_PATHS = new Set([
  '/pages/home/index',
  '/pages/assistant/index',
  '/pages/resume/index',
  '/pages/user/index',
]);

const PROTECTED_PAGE_PATHS = new Set([
  '/pages/agent/index',
  '/pages/agent/profile',
  '/pages/assistant/index',
  '/pages/checkin/index',
  '/pages/interview/start',
  '/pages/interview/history',
  '/pages/interview/room',
  '/pages/interview/chat',
  '/pages/interview/report',
  '/pages/onboarding/index',
  '/pages/resume-ai/index',
  '/pages/user/memory',
]);

const consultLinkHint = (raw: string) => {
  const base = (raw || '').split('?')[0];
  const n = base.startsWith('/') ? base : `/${base}`;
  if (n.startsWith('/pages/')) return t('home.tryIt');
  return t('home.readFull');
};

const openConsultation = (raw: string, title?: string) => {
  if (!raw) return;
  const base = raw.split('?')[0];
  const normalizedBase = base.startsWith('/') ? base : `/${base}`;
  if (normalizedBase.startsWith('/pages/')) {
    navTo(raw.startsWith('/') ? raw : `/${raw}`);
    return;
  }
  openLink(raw, title);
};

const handleAvatarClick = () => {
  uni.showActionSheet({
    itemList: [t('home.viewProfile'), t('home.logOut')],
    success: (res) => {
      if (res.tapIndex === 0) {
        uni.switchTab({ url: '/pages/user/index' });
      } else if (res.tapIndex === 1) {
        uni.showModal({
          title: t('home.logOut'),
          content: t('home.logOutConfirm'),
          success: (r) => {
            if (r.confirm) {
              clearAuthState();
              userInfo.value = { nickname: '', avatarUrl: '', avatarViewUrl: '' };
              uni.reLaunch({ url: LOGIN_PAGE });
            }
          },
        });
      }
    },
  });
};
</script>

<style scoped>
.home-page {
  font-family: var(--font-sans, "PingFang SC", "Microsoft YaHei", -apple-system, sans-serif);
  padding-bottom: env(safe-area-inset-bottom, 0px);
}

.status-spacer { width: 100%; }

/* ---- Career workbench ---- */
.workbench-hero { padding: 10px 20px 0; }
.brand-row { display: flex; align-items: center; justify-content: space-between; gap: 14px; }
.brand-copy { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 5px; }
.brand-kicker {
  font-size: 11px;
  line-height: 1.2;
  font-weight: 600;
  letter-spacing: 0.14em;
  color: var(--vermilion, #c23b22);
  text-transform: uppercase;
}
.brand-title {
  font-size: 25px;
  line-height: 1.35;
  font-weight: 600;
  color: var(--text-primary, #1c1917);
  font-family: var(--font-serif, "Songti SC", STSong, serif);
  letter-spacing: 0.05em;
}
.brand-subtitle {
  font-size: 13px;
  line-height: 1.45;
  color: var(--text-secondary, #78716c);
}
.workbench-avatar { width: 40px; height: 40px; border-radius: 20px; }

.readiness-card {
  margin: 18px 20px 0;
  padding: 18px;
  border-radius: var(--radius-md, 6px);
}
.readiness-head { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.readiness-copy { flex: 1; min-width: 0; }
.readiness-kicker {
  display: block;
  font-size: 11px;
  font-weight: 600;
  color: var(--vermilion, #c23b22);
  letter-spacing: 0.08em;
}
.readiness-title {
  display: block;
  margin-top: 5px;
  font-size: 38px;
  line-height: 1;
  font-weight: 600;
  color: var(--text-primary, #1c1917);
  font-family: var(--font-serif, "Songti SC", STSong, serif);
}
.readiness-subtitle {
  display: block;
  margin-top: 7px;
  font-size: 13px;
  line-height: 1.45;
  color: var(--text-secondary, #78716c);
}
.readiness-bar {
  height: 8px;
  border-radius: 999px;
  overflow: hidden;
  background: var(--surface-3, #f5f5f4);
  margin-top: 16px;
}
.readiness-fill {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, var(--sage, #8d846e), var(--vermilion, #c23b22));
}
.gap-row {
  margin-top: 12px;
  padding: 11px 12px;
  border-radius: var(--radius-sm, 4px);
  background: var(--surface-2, #fafaf9);
  display: flex;
  align-items: center;
  gap: 10px;
}
.gap-label {
  flex-shrink: 0;
  font-size: 11px;
  font-weight: 600;
  color: var(--vermilion, #c23b22);
}
.gap-text {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  line-height: 1.4;
  font-weight: 700;
  color: var(--text-primary, #1c1917);
}
.readiness-dims {
  margin-top: 12px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 9px 12px;
}
.readiness-dim {
  min-width: 0;
  display: grid;
  grid-template-columns: 34px 1fr 26px;
  align-items: center;
  gap: 8px;
}
.readiness-dim-label,
.readiness-dim-val {
  font-size: 11px;
  line-height: 1.2;
  font-weight: 800;
  color: var(--text-secondary, #78716c);
}
.readiness-dim-val { text-align: right; color: var(--text-primary, #1c1917); }
.readiness-dim-track {
  height: 6px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--surface-3, #e7e5e4);
}
.readiness-dim-fill {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, var(--sage, #8d846e), var(--vermilion, #c23b22));
}
.score-rule {
  display: block;
  margin-top: 12px;
  font-size: 11px;
  line-height: 1.45;
  color: var(--text-tertiary, #8e8e93);
}

.intake-card {
  margin: 14px 20px 0;
  padding: 16px;
  border-radius: 18px;
}
.intake-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 9px;
}
.intake-item {
  min-width: 0;
  padding: 10px 11px;
  border-radius: 13px;
  background: var(--surface-2, #fafaf9);
  border: 1px solid var(--border-color, #e7e5e4);
}
.intake-label {
  display: block;
  font-size: 10.5px;
  font-weight: 900;
  color: var(--text-tertiary, #8e8e93);
  margin-bottom: 5px;
}
.intake-value {
  display: block;
  font-size: 12.5px;
  line-height: 1.35;
  font-weight: 800;
  color: var(--text-primary, #1c1917);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.growth-tree-card {
  margin: 14px 20px 0;
  padding: 14px 0 14px 16px;
  overflow: hidden;
}
.growth-tree-scroll {
  width: 100%;
  height: 250rpx;
  margin-top: 4rpx;
}
.growth-tree-empty {
  margin: 12rpx 16rpx 2rpx 0;
  height: 156rpx;
  border: 1rpx dashed rgba(184,151,90,.48);
  border-radius: 12rpx;
  background: var(--gold-soft, #f5efe4);
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 0 24rpx;
  box-sizing: border-box;
}
.growth-tree-empty-title {
  display: block;
  font-size: 13px;
  line-height: 1.25;
  font-weight: 600;
  color: var(--ink, #2c2b29);
  font-family: var(--font-serif, "Songti SC", STSong, serif);
}
.growth-tree-empty-desc {
  display: block;
  margin-top: 8rpx;
  font-size: 11px;
  line-height: 1.4;
  color: var(--ink-secondary, #5a5956);
}

/* ══ Growth Tree (gtree-*) ══ */
.gtree-canvas {
  position: relative;
  height: 238rpx;
  min-width: 1040rpx;
}
/* 斜线连接器 */
.gtree-connector {
  position: absolute;
  height: 3rpx;
  background: linear-gradient(to right, rgba(172, 100, 72,.28), rgba(141, 132, 110,.16));
  border-radius: 2rpx;
  transform-origin: left center;
  z-index: 0;
}
.gtree-connector.is-arc {
  height: 22rpx;
  background: transparent;
  border-top: 3rpx solid rgba(172, 100, 72,.22);
  border-radius: 999rpx 999rpx 0 0;
}
/* 根节点 */
.gtree-root {
  position: absolute;
  left: 8rpx;
  top: 72rpx;
  width: 164rpx;
  height: 80rpx;
  border-radius: 12rpx;
  padding: 14rpx 16rpx;
  background: var(--indigo, #ac6448);
  border-left: 5rpx solid var(--vermilion, #c23b22);
  box-shadow: 0 6rpx 18rpx rgba(44, 43, 41, .08);
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  justify-content: center;
  z-index: 3;
}
.gtree-cluster-hub {
  position: absolute;
  transform: translate(-50%, -50%);
  width: 82rpx;
  height: 58rpx;
  border-radius: 10rpx;
  border: 2rpx solid var(--border-color, #e0dfdb);
  box-shadow: 0 4rpx 12rpx rgba(44,43,41,.05);
  z-index: 3;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}
.gtree-cluster-title {
  display: block;
  font-size: 11px;
  line-height: 1.1;
  font-weight: 600;
  color: var(--ink, #2c2b29);
  font-family: var(--font-serif, "Songti SC", STSong, serif);
  max-width: 70rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.gtree-cluster-time {
  display: block;
  margin-top: 2rpx;
  font-size: 9.5px;
  font-weight: 600;
  color: var(--indigo, #ac6448);
}
.gtree-root-name {
  display: block; font-size: 13px; font-weight: 600; color: #fff;
  font-family: var(--font-serif, "Songti SC", STSong, serif);
  line-height: 1.2; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.gtree-root-sub {
  display: block; margin-top: 4rpx; font-size: 10px; font-weight: 600; color: rgba(255,255,255,.76);
}
/* 泳道节点 */
.gtree-node {
  position: absolute;
  transform: translate(-50%, -50%);
  border-radius: 8rpx;
  padding: 7rpx 12rpx;
  box-sizing: border-box;
  max-width: 132rpx;
  min-width: 62rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  z-index: 2;
  border: 2rpx solid var(--border-color, #e0dfdb);
  background: var(--card-bg, #fffefa);
  box-shadow: 0 3rpx 10rpx rgba(44,43,41,.05);
}
.gtree-node-label {
  display: block; font-size: 11px; font-weight: 600;
  color: var(--ink, #2c2b29); text-align: center;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 106rpx;
}
.gtree-node-time {
  display: block; font-size: 9.5px; font-weight: 600; margin-top: 2rpx;
  color: var(--indigo, #ac6448); text-align: center;
}
.gtree-branch {
  position: absolute;
  height: 2rpx;
  border-top: 2rpx solid rgba(172, 100, 72,.18);
  border-radius: 999rpx;
  transform-origin: left center;
  z-index: 1;
}
/* 簇内垂直连线 */
.gtree-vline-cluster {
  position: absolute;
  width: 3rpx;
  background: rgba(172, 100, 72,.18);
  border-radius: 2rpx;
  transform: translateX(-50%);
  z-index: 1;
}
/* 年份标签 */
.gtree-year-chip {
  position: absolute;
  transform: translateX(-50%);
  background: var(--indigo-soft, #f9f0ed);
  border-radius: 4rpx;
  padding: 3rpx 10rpx;
  z-index: 2;
}
.gtree-year-text { display: block; font-size: 10px; font-weight: 600; color: var(--indigo, #ac6448); }
/* 分类色 */
.gtree-cat-skill      { border-color: rgba(141, 132, 110,.48) !important; background: var(--sage-soft, #f2efe9) !important; }
.gtree-cat-background { border-color: rgba(172, 100, 72,.38) !important; background: var(--indigo-soft, #f9f0ed) !important; }
.gtree-cat-growth     { border-color: rgba(184,151,90,.48) !important; background: var(--gold-soft, #f5efe4) !important; }
.gtree-cat-goal       { border-color: rgba(194,59,34,.38) !important; background: var(--vermilion-soft, #f8ebe7) !important; }
/* 目标节点 */
.gtree-target {
  position: absolute;
  top: 72rpx;
  width: 160rpx;
  min-height: 80rpx;
  border-left: 4rpx dashed rgba(194,59,34,.42);
  padding: 14rpx 16rpx;
  box-sizing: border-box;
  z-index: 2;
}
.gtree-target-label {
  display: block; font-size: 10px; font-weight: 600; letter-spacing: .06em; color: var(--text-secondary, #78716c);
}
.gtree-target-title {
  display: block; margin-top: 8rpx; font-size: 14px; line-height: 1.3;
  font-weight: 600; color: var(--vermilion, #c23b22);
  font-family: var(--font-serif, "Songti SC", STSong, serif);
  overflow: hidden; text-overflow: ellipsis;
}

.today-card {
  margin: 14px 20px 0;
  padding: 18px;
  border: 1px solid #e8c6be;
  border-top: 1px solid var(--border-color, #e0dfdb);
  border-radius: var(--radius-md, 6px);
  background: #fffafa;
  color: var(--ink, #2c2b29);
  box-shadow: 0 6px 20px rgba(194, 59, 34, 0.07);
}
.today-top { display: flex; align-items: flex-start; gap: 13px; }
.today-icon-wrap {
  width: 42px;
  height: 42px;
  border-radius: var(--radius-sm, 4px);
  background: var(--vermilion-soft, #f8ebe7);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.today-icon { font-size: 22px; color: var(--vermilion, #c23b22); }
.today-copy { flex: 1; min-width: 0; }
.today-kicker {
  display: block;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.06em;
  color: var(--vermilion, #c23b22);
}
.today-title {
  display: block;
  margin-top: 5px;
  font-size: 19px;
  line-height: 1.3;
  font-weight: 600;
  color: var(--ink, #2c2b29);
  font-family: var(--font-serif, "Songti SC", STSong, serif);
  letter-spacing: 0.04em;
  overflow-wrap: anywhere;
  word-break: break-word;
}
.today-desc {
  display: block;
  margin-top: 7px;
  font-size: 13px;
  line-height: 1.55;
  color: var(--ink-secondary, #5a5956);
  overflow-wrap: anywhere;
  word-break: break-word;
}
.today-result {
  margin-top: 14px;
  padding: 12px;
  border: 1px solid var(--border-color, #e0dfdb);
  border-radius: var(--radius-sm, 4px);
  background: var(--paper-soft, #f5f5f0);
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.today-result-label { font-size: 11px; font-weight: 600; color: var(--heritage-gold, #b8975a); }
.today-result-text { font-size: 13px; line-height: 1.55; color: var(--ink-secondary, #5a5956); }
.today-actions { display: flex; align-items: center; gap: 10px; margin-top: 14px; }
.today-cta {
  flex: 1;
  min-height: 44px;
  border-radius: var(--btn-radius, 6px);
  background: var(--vermilion, #c23b22);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}
.today-cta-text,
.today-cta-arrow { font-size: 14px; font-weight: 600; color: #ffffff; }
.today-plan-link {
  min-height: 44px;
  border: 1px solid var(--border-color, #e0dfdb);
  border-radius: var(--btn-radius, 6px);
  padding: 0 12px;
  background: var(--paper-soft, #f5f5f0);
  display: flex;
  align-items: center;
  justify-content: center;
}
.today-plan-link-text {
  font-size: 12px;
  font-weight: 600;
  color: var(--ink, #2c2b29);
  white-space: nowrap;
}
.today-done {
  min-height: 44px;
  border: 1px solid var(--border-color, #e0dfdb);
  border-radius: var(--btn-radius, 6px);
  padding: 0 14px;
  background: var(--paper-soft, #f5f5f0);
  display: flex;
  align-items: center;
  justify-content: center;
}
.today-done-text { font-size: 13px; font-weight: 600; color: var(--ink, #2c2b29); }

.core-entry-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  padding: 14px 20px 0;
}
.core-entry {
  border-radius: var(--radius-md, 6px);
  padding: 14px 8px;
  min-height: 100px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-start;
  gap: 6px;
  text-align: center;
}
.core-entry-icon {
  width: 34px;
  height: 34px;
  border-radius: var(--radius-sm, 4px);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
}
.core-tone-blue { background: var(--indigo-soft, #f9f0ed); color: var(--indigo, #ac6448); }
.core-tone-pink { background: var(--vermilion-soft, #f8ebe7); color: var(--vermilion, #c23b22); }
.core-tone-orange { background: var(--gold-soft, #f5efe4); color: var(--heritage-gold, #b8975a); }
.core-entry-label { font-size: 13px; font-weight: 600; color: var(--ink, #2c2b29); line-height: 1.2; font-family: var(--font-serif, "Songti SC", STSong, serif); }
.core-entry-desc { font-size: 10.5px; line-height: 1.25; color: var(--ink-secondary, #5a5956); }

.recent-card {
  margin: 14px 20px 0;
  padding: 16px;
  border-radius: var(--radius-md, 6px);
}
.section-lite-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 10px; }
.section-title-stack { display: flex; flex-direction: column; gap: 3px; min-width: 0; }
.section-lite-title { font-size: 16px; font-weight: 600; color: var(--ink, #2c2b29); font-family: var(--font-serif, "Songti SC", STSong, serif); }
.section-lite-sub { font-size: 11px; line-height: 1.35; color: var(--ink-tertiary, #8b8a86); }
.section-lite-action { font-size: 12px; font-weight: 600; color: var(--indigo, #ac6448); }
.compact-card { padding-top: 14px; padding-bottom: 14px; }
.progress-list { display: flex; flex-direction: column; gap: 8px; }
.progress-item {
  min-height: 48px;
  display: flex;
  align-items: center;
  gap: 10px;
  border-radius: var(--radius-sm, 4px);
  padding: 8px;
  background: var(--paper-soft, #f5f5f0);
}
.progress-item-icon {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-sm, 4px);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  flex-shrink: 0;
}
.progress-blue { background: var(--indigo-soft, #f9f0ed); color: var(--indigo, #ac6448); }
.progress-violet { background: var(--sage-soft, #f2efe9); color: var(--sage, #8d846e); }
.progress-pink { background: var(--vermilion-soft, #f8ebe7); color: var(--vermilion, #c23b22); }
.progress-orange { background: var(--gold-soft, #f5efe4); color: var(--heritage-gold, #b8975a); }
.progress-item-copy { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.progress-item-label { font-size: 11px; font-weight: 600; color: var(--ink-secondary, #5a5956); }
.progress-item-value {
  font-size: 13px;
  font-weight: 700;
  line-height: 1.35;
  color: var(--ink, #2c2b29);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.progress-item-arrow { color: var(--ink-tertiary, #8b8a86); font-size: 17px; }

.support-card {
  margin: 12px 20px 0;
  padding: 13px 15px;
  border-radius: var(--radius-md, 6px);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.support-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 3px; }
.support-title { font-size: 13px; font-weight: 600; color: var(--ink, #2c2b29); font-family: var(--font-serif, "Songti SC", STSong, serif); }
.support-desc { font-size: 12px; color: var(--ink-secondary, #5a5956); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.support-link { font-size: 12px; font-weight: 600; color: var(--indigo, #ac6448); flex-shrink: 0; }

.resource-search {
  margin: 18px 20px 0;
  height: 40px;
  border-radius: var(--radius-md, 6px);
  padding: 0 13px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.resource-search-icon { font-size: 15px; color: var(--text-tertiary, #8e8e93); }
.resource-search-input { flex: 1; height: 38px; font-size: 13px; color: var(--text-primary, #1c1917); }
.resource-search-clear { padding: 4px; }
.resource-section { margin-top: 2px; }

/* ---- Top bar ---- */
.top-bar { display: flex; align-items: center; padding: 8px 20px 0; gap: 12px; }
.search-bar {
  flex: 1; display: flex; align-items: center;
  height: 42px;
  border-radius: var(--btn-radius, 14px);
  padding: 0 16px;
}
.search-icon-wrap { width: 18px; height: 18px; display: flex; align-items: center; justify-content: center; margin-right: 6px; }
.search-icon-svg { font-size: 14px; }
.search-input { flex: 1; font-size: var(--font-body, 15px); color: var(--text-primary, #1c1917); height: 38px; }
.search-input { border: none; background: transparent; padding: 0; box-shadow: none; }
.search-ph { color: var(--text-tertiary, #8e8e93); font-size: var(--font-body, 15px); }
.search-clear { padding: 4px; }
.clear-icon { font-size: 18px; color: var(--text-tertiary, #8e8e93); line-height: 1; }
.user-avatar { width: 36px; height: 36px; border-radius: 18px; background: #e7e5e4; flex-shrink: 0; }

/* ---- Greeting ---- */
.greeting-row { padding: 18px 20px 6px; }
.greeting-kicker {
  display: block; font-size: 11px; font-weight: 700;
  color: var(--primary-color, #cd6a43); letter-spacing: 0.08em;
  text-transform: uppercase; margin-bottom: 6px;
}
.greeting-title { display: block; font-size: var(--font-hero, 28px); line-height: 1.12; font-weight: 800; color: var(--text-primary, #1c1917); }
.greeting-text { display: block; margin-top: 8px; font-size: var(--font-body, 15px); line-height: 1.5; color: var(--text-secondary, #78716c); }

/* ---- Feature grid ---- */
.feature-grid { display: flex; flex-wrap: wrap; gap: 12px; padding: 16px 20px 8px; }
.feature-item {
  width: calc(50% - 6px);
  display: flex; flex-direction: column; align-items: flex-start; gap: 12px;
  box-sizing: border-box;
  min-height: 104px;
  padding: 16px;
  border: none;
  box-shadow: none;
  color: #ffffff;
}
.feature-item:nth-child(1) { background: linear-gradient(135deg, #a17e39 0%, #cd6a43 100%); }
.feature-item:nth-child(2) { background: linear-gradient(135deg, #d27855 0%, #cd6a43 100%); }
.feature-item:nth-child(3) { background: linear-gradient(135deg, #ec4899 0%, #db9377 100%); }
.feature-item:nth-child(4) { background: linear-gradient(135deg, #f59e0b 0%, #ef4444 100%); }
.feature-item .app-icon-tile {
  background: rgba(255,255,255,0.22);
  color: #ffffff;
}
.fi-char { font-size: 20px; }
.feature-label { font-size: var(--font-body, 15px); font-weight: 800; color: #ffffff; line-height: 1.25; min-height: 36px; }

.agent-card {
  margin: 16px 20px 0;
  background: var(--surface-1, #ffffff);
  color: var(--text-primary, #1c1917);
}
.agent-card-head { display: flex; align-items: center; gap: 10px; }
.agent-icon-wrap {
  width: 42px; height: 42px; border-radius: 15px;
  background: var(--primary-soft, #fcf5f2);
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.agent-icon { font-size: 21px; color: var(--primary-color, #cd6a43); }
.agent-head-copy { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 3px; }
.agent-kicker { font-size: 10px; font-weight: 800; color: var(--primary-color, #cd6a43); letter-spacing: 0.08em; text-transform: uppercase; }
.agent-title { font-size: 17px; line-height: 1.25; font-weight: 900; color: var(--text-primary, #1c1917); overflow-wrap: anywhere; word-break: break-word; }
.risk-pill { border-radius: 999px; padding: 5px 8px; background: var(--surface-3, #f5f5f4); flex-shrink: 0; }
.risk-high { background: #fee2e2; }
.risk-medium { background: #fef3c7; }
.risk-low { background: #f5efe3; }
.risk-text { font-size: 10px; font-weight: 900; color: var(--text-primary, #1c1917); }
.agent-progress { margin-top: 14px; display: flex; align-items: center; gap: 10px; }
.agent-progress-bar {
  flex: 1; height: 7px; border-radius: 999px;
  background: var(--surface-3, #f5f5f4); overflow: hidden;
}
.agent-progress-fill { height: 100%; border-radius: 999px; background: linear-gradient(90deg, #cd6a43, #d47e5c); }
.agent-progress-text { font-size: 11px; font-weight: 700; color: var(--text-secondary, #78716c); }
.agent-focus { display: block; margin-top: 12px; font-size: 14px; line-height: 1.45; font-weight: 800; color: var(--text-primary, #1c1917); }
.agent-reason { display: block; margin-top: 6px; font-size: 12px; line-height: 1.55; color: var(--text-secondary, #78716c); }
.risk-watch-card {
  margin-top: 12px;
  padding: 12px;
  border-radius: 16px;
  background: var(--surface-2, #fafaf9);
  border: 1px solid var(--border-color, #e7e5e4);
}
.risk-watch-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.risk-watch-kicker { font-size: 10px; font-weight: 900; color: var(--primary-color, #cd6a43); letter-spacing: 0.08em; text-transform: uppercase; }
.risk-watch-badges { display: flex; align-items: center; gap: 6px; flex-shrink: 0; }
.risk-watch-level,
.risk-watch-trend {
  border-radius: 999px;
  padding: 4px 7px;
  font-size: 10px;
  font-weight: 900;
  color: var(--text-primary, #1c1917);
  background: var(--surface-3, #f5f5f4);
}
.risk-watch-high { background: #fee2e2; }
.risk-watch-medium { background: #fef3c7; }
.risk-watch-low { background: #f5efe3; }
.risk-watch-title { display: block; margin-top: 8px; font-size: 14px; font-weight: 900; color: var(--text-primary, #1c1917); line-height: 1.35; }
.risk-watch-summary { display: block; margin-top: 5px; font-size: 11.5px; color: var(--text-secondary, #78716c); line-height: 1.45; }
.risk-watch-next { display: block; margin-top: 7px; font-size: 11.5px; color: var(--primary-color, #cd6a43); line-height: 1.45; font-weight: 700; }
.agent-plan-card {
  margin-top: 12px;
  padding: 12px;
  border-radius: 16px;
  background: var(--surface-2, #fafaf9);
  border: 1px solid var(--border-color, #e7e5e4);
}
.agent-plan-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.agent-plan-kicker { font-size: 10px; font-weight: 900; color: var(--primary-color, #cd6a43); letter-spacing: 0.08em; text-transform: uppercase; }
.agent-plan-health {
  border-radius: 999px;
  padding: 4px 7px;
  font-size: 10px;
  font-weight: 900;
  color: var(--text-primary, #1c1917);
  background: var(--surface-3, #f5f5f4);
}
.agent-plan-on_track { background: #f5efe3; }
.agent-plan-needs_refresh { background: #fef3c7; }
.agent-plan-missing { background: #fee2e2; }
.agent-plan-title { display: block; margin-top: 8px; font-size: 14px; line-height: 1.35; font-weight: 900; color: var(--text-primary, #1c1917); }
.agent-plan-milestone { display: block; margin-top: 5px; font-size: 12px; line-height: 1.4; color: var(--text-primary, #1c1917); font-weight: 700; }
.agent-plan-focus-list { display: flex; flex-direction: column; gap: 4px; margin-top: 8px; }
.agent-plan-focus { font-size: 11.5px; line-height: 1.4; color: var(--text-secondary, #78716c); }
.agent-plan-reason { display: block; margin-top: 7px; font-size: 11px; line-height: 1.45; color: var(--text-secondary, #78716c); }
.agent-plan-action {
  align-self: flex-start;
  margin-top: 10px;
  border-radius: 999px;
  padding: 7px 11px;
  background: var(--primary-color, #cd6a43);
}
.agent-plan-action-text { font-size: 12px; font-weight: 900; color: #ffffff; }
.agent-pct-row {
  display: flex; align-items: center; gap: 8px; margin: 8px 0 4px;
}
.agent-pct-bar-wrap {
  flex: 1; height: 5px; background: var(--surface-3, #f5f5f4); border-radius: 3px; overflow: hidden;
}
.agent-pct-bar-fill {
  height: 100%; border-radius: 3px; transition: width .4s ease;
}
.agent-pct-low   { background: #a8a29e; }
.agent-pct-medium { background: #d47e5c; }
.agent-pct-high  { background: #bf9748; }
.agent-pct-label { font-size: 10.5px; color: var(--text-secondary, #78716c); white-space: nowrap; flex: 1; }
.agent-pct-arrow { font-size: 14px; color: var(--text-tertiary, #8e8e93); margin-left: 2px; }
.agent-missing-row { margin: 6px 0 2px; }
.agent-missing-label { font-size: 10.5px; color: var(--text-tertiary, #8e8e93); margin-bottom: 5px; display: block; }
.agent-missing-chips { display: flex; flex-wrap: wrap; gap: 6px; }
.agent-missing-chip {
  background: var(--primary-soft, #fcf5f2); border-radius: 12px; padding: 3px 10px;
}
.agent-missing-chip-text { font-size: 11px; color: var(--primary-color, #cd6a43); font-weight: 600; }
.agent-risks { margin-top: 10px; display: flex; flex-direction: column; gap: 4px; }
.agent-risk-item { font-size: 11.5px; line-height: 1.4; color: var(--text-secondary, #78716c); }
.agent-actions { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 13px; }
.agent-action {
  border-radius: 999px;
  padding: 8px 12px;
  background: var(--surface-2, #fafaf9);
  border: 1px solid var(--border-color, #e7e5e4);
}
.agent-action-primary { background: var(--primary-color, #cd6a43); border-color: var(--primary-color, #cd6a43); }
.agent-action-text { font-size: 12px; font-weight: 800; color: var(--text-primary, #1c1917); }
.agent-action-primary .agent-action-text { color: #ffffff; }
.agent-task-list { margin-top: 14px; display: flex; flex-direction: column; gap: 8px; }
.agent-task-row {
  display: flex; align-items: center; gap: 10px;
  background: var(--surface-2, #fafaf9);
  border: 1px solid var(--border-color, #e7e5e4);
  border-radius: 14px;
  padding: 10px;
}
.agent-task-done { opacity: 0.68; }
.agent-task-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 3px; }
.agent-task-title-row { display: flex; align-items: center; gap: 6px; min-width: 0; }
.agent-task-title { font-size: 13px; font-weight: 900; color: var(--text-primary, #1c1917); line-height: 1.35; }
.agent-task-source {
  flex-shrink: 0;
  border-radius: 999px;
  padding: 2px 6px;
  background: var(--surface-3, #f5f5f4);
  font-size: 9px;
  font-weight: 900;
  color: var(--text-secondary, #78716c);
}
.agent-task-desc { font-size: 11px; line-height: 1.4; color: var(--text-secondary, #78716c); }
.agent-task-actions { display: flex; align-items: center; gap: 6px; flex-shrink: 0; }
.agent-task-btn {
  border-radius: 999px;
  padding: 6px 9px;
  background: var(--surface-3, #f5f5f4);
}
.agent-task-complete { background: var(--primary-color, #cd6a43); }
.agent-task-btn-text { font-size: 11px; font-weight: 900; color: var(--text-secondary, #78716c); }
.agent-task-complete .agent-task-btn-text { color: #ffffff; }

.agent-hub-entry {
  margin-top: 14px;
  border: 1.5px solid var(--border-color, #e7e5e4);
  border-radius: 999px; padding: 10px 0; text-align: center;
  background: var(--surface-2, #fafaf9);
}
.agent-hub-entry-text { font-size: 13px; font-weight: 700; color: var(--primary-color, #cd6a43); }

/* ---- Daily check-in chip ---- */
.checkin-card {
  display: flex; align-items: center; justify-content: space-between;
  margin: 16px 20px 0; padding: 14px 16px;
  background: var(--gradient-success);
  border-radius: var(--radius-md, 16px);
  box-shadow: var(--shadow-sm);
  color: #fff;
}
.checkin-card:active { transform: scale(0.99); }
.checkin-left { display: flex; flex-direction: column; gap: 2px; min-width: 0; flex: 1; }
.checkin-kicker {
  font-size: 10px; font-weight: 700; color: rgba(255,255,255,0.8);
  letter-spacing: 0.08em; text-transform: uppercase;
}
.checkin-title { font-size: 18px; font-weight: 800; color: #fff; line-height: 1.1; }
.checkin-sub { font-size: 12px; color: rgba(255,255,255,0.9); margin-top: 2px; }
.checkin-right {
  display: flex; flex-direction: column; align-items: flex-end; gap: 6px;
  min-width: 110px;
}
.checkin-bar {
  width: 96px; height: 6px; border-radius: 3px;
  background: rgba(255,255,255,0.3); overflow: hidden;
}
.checkin-bar-fill {
  height: 100%; background: #fff;
  border-radius: 3px; transition: width 0.3s ease;
}
.checkin-cta { font-size: 12px; font-weight: 700; color: #fff; }
.checkin-tip {
  margin: 6px 20px 0; padding: 6px 14px;
  font-size: 11.5px; color: var(--text-secondary, #78716c); line-height: 1.4;
}
.checkin-tip-text { font-size: 11.5px; color: var(--text-secondary, #78716c); }

/* ---- CDUT employment insight ---- */
.cdut-card {
  margin: 16px 20px 0;
  padding: 15px 16px;
  background: var(--surface-1, #ffffff);
  color: var(--text-primary, #1c1917);
}
.cdut-card:active { transform: scale(0.99); }
.cdut-head { display: flex; align-items: center; gap: 10px; }
.cdut-icon-wrap {
  width: 40px; height: 40px; border-radius: 14px;
  display: flex; align-items: center; justify-content: center;
  background: var(--primary-soft, #fcf5f2);
  flex-shrink: 0;
}
.cdut-icon { font-size: 20px; color: var(--primary-color, #cd6a43); }
.cdut-head-copy { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.cdut-kicker {
  font-size: 10px; font-weight: 900; letter-spacing: 0.08em;
  color: var(--primary-color, #cd6a43);
}
.cdut-title { font-size: 16px; line-height: 1.28; font-weight: 900; color: var(--text-primary, #1c1917); }
.cdut-arrow { font-size: 18px; color: var(--text-tertiary, #8e8e93); }
.cdut-match-row { margin-top: 12px; display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.cdut-match {
  font-size: 11px; color: var(--primary-color, #cd6a43); font-weight: 800;
  background: var(--primary-soft, #fcf5f2); padding: 4px 8px; border-radius: 999px;
}
.cdut-updated { font-size: 11px; color: var(--text-tertiary, #8e8e93); }
.cdut-summary {
  display: block; margin-top: 8px;
  font-size: 12.5px; line-height: 1.55; color: var(--text-secondary, #78716c);
  display: -webkit-box; line-clamp: 3; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden;
}
.cdut-metrics {
  display: flex; gap: 8px; margin-top: 12px;
}
.cdut-metric {
  flex: 1; min-width: 0; padding: 10px 8px; border-radius: 12px;
  background: var(--surface-2, #fafaf9);
  border: 1px solid var(--border-color, #e7e5e4);
}
.cdut-metric-val { display: block; font-size: 16px; line-height: 1.1; font-weight: 900; color: var(--text-primary, #1c1917); }
.cdut-metric-label { display: block; margin-top: 4px; font-size: 10px; color: var(--text-tertiary, #8e8e93); }
.cdut-highlight {
  margin-top: 10px; padding: 9px 10px; border-radius: 12px;
  background: var(--surface-2, #fafaf9);
}
.cdut-highlight-text { font-size: 11.5px; line-height: 1.45; color: var(--text-secondary, #78716c); }

/* ---- Generic section header ---- */
.section { padding: 24px 0 0; }
.section-header {
  display: flex; justify-content: space-between; align-items: flex-end;
  padding: 0 20px; margin-bottom: 14px; gap: 12px;
}
.section-titles { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.section-title { font-size: var(--font-title, 18px); font-weight: 700; color: var(--text-primary, #1c1917); letter-spacing: -0.3px; }
.section-meta { font-size: var(--font-caption, 13px); color: var(--text-tertiary, #8e8e93); }
.section-more { display: flex; align-items: center; gap: 4px; min-height: 32px; padding: 0 6px; }
.section-more-text { font-size: 13px; color: var(--primary-color, #cd6a43); font-weight: 600; }
.section-more-arrow { font-size: 16px; color: var(--primary-color, #cd6a43); line-height: 1; }

/* ---- Reusable cover tints (used when no image) ---- */
.cover-tone-0 { background: linear-gradient(135deg, #f7e8e2, #f1d6cc); }
.cover-tone-1 { background: linear-gradient(135deg, #f8ece7, #f3dbd2); }
.cover-tone-2 { background: linear-gradient(135deg, #faf1ed, #f0abfc); }
.cover-tone-3 { background: linear-gradient(135deg, #ffedd5, #fed7aa); }

/* ---- Horizontal scroller (used by videos) ---- */
.hscroll { width: 100%; white-space: nowrap; }
.hscroll-track { display: inline-flex; padding: 0 20px 4px; gap: 16px; padding-right: 40px; }

/* ---- Video card ---- */
.video-card {
  display: inline-flex; flex-direction: column;
  width: 240px; flex-shrink: 0;
  border-radius: var(--radius-md, 16px);
  overflow: hidden;
  transition: transform 0.15s;
}
.video-card:active { transform: scale(0.98); }
.video-cover { width: 100%; height: 134px; position: relative; overflow: hidden; background: #e7e5e4; }
.video-cover-img { width: 100%; height: 100%; display: block; }
.duration-badge {
  position: absolute; bottom: 8px; right: 8px;
  background: rgba(0,0,0,0.55); padding: 2px 8px; border-radius: 6px;
}
.duration-text { color: #fff; font-size: 11px; font-weight: 600; }
.play-icon-wrap {
  position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%);
  width: 40px; height: 40px; border-radius: 20px; background: rgba(0,0,0,0.5);
  display: flex; align-items: center; justify-content: center;
}
.play-icon { font-size: 14px; color: #fff; margin-left: 2px; }
.video-body { padding: 10px 14px 14px; white-space: normal; }
.video-title {
  font-size: var(--font-body, 15px); font-weight: 600; color: var(--text-primary, #1c1917); line-height: 1.4;
  margin-bottom: 6px; display: -webkit-box;
  line-clamp: 2; -webkit-line-clamp: 2; -webkit-box-orient: vertical;
  overflow: hidden; height: 38px;
}
.video-meta-row { display: flex; align-items: center; gap: 4px; }
.video-up { font-size: 12px; color: var(--primary-color, #cd6a43); font-weight: 600; }
.video-views { font-size: 12px; color: var(--text-tertiary, #8e8e93); }

/* ---- Article cards (vertical list) ---- */
.article-list { display: flex; flex-direction: column; gap: 12px; padding: 0 20px; }
.article-card {
  display: flex; align-items: stretch; gap: 14px;
  border-radius: var(--radius-md, 16px); padding: 14px;
  transition: transform 0.15s;
}
.article-card:active { transform: scale(0.99); }
.article-cover {
  width: 96px; height: 96px; flex-shrink: 0;
  border-radius: 12px; overflow: hidden;
  background: var(--surface-2, #fafaf9);
}
.article-cover-art {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: flex-end;
  padding: 10px;
  box-sizing: border-box;
  color: #ffffff;
}
.article-cover-pattern {
  position: absolute;
  inset: 0;
  opacity: 0.18;
  background-image:
    linear-gradient(135deg, rgba(255,255,255,0.95) 0 2px, transparent 2px 16px),
    radial-gradient(circle at 76% 18%, rgba(255,255,255,0.9) 0 18px, transparent 19px);
}
.article-cover-icon,
.article-cover-label {
  position: relative;
  z-index: 1;
}
.article-cover-icon {
  font-size: 27px;
  line-height: 1;
  margin-bottom: 9px;
}
.article-cover-label {
  padding: 3px 7px;
  border-radius: 6px;
  background: rgba(28, 25, 23, 0.22);
  font-size: 11px;
  line-height: 1.2;
  font-weight: 900;
}
.cover-topic-resume { background: linear-gradient(135deg, #cd6a43, #624d23); }
.cover-topic-interview { background: linear-gradient(135deg, #dc2626, #f97316); }
.cover-topic-career { background: linear-gradient(135deg, #d37b58, #934627); }
.cover-topic-code { background: linear-gradient(135deg, #1c1917, #cd6a43); }
.cover-topic-data { background: linear-gradient(135deg, #624d23, #82662e); }
.cover-topic-employment { background: linear-gradient(135deg, #7c2d12, #ea580c); }
.cover-topic-comm { background: linear-gradient(135deg, #be185d, #d27855); }
.cover-topic-default { background: linear-gradient(135deg, #44403c, #78716c); }
.article-source-icon {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}
.article-source-glyph {
  font-size: 34px;
  color: var(--primary-color, #cd6a43);
}
.article-cover-img { width: 100%; height: 100%; display: block; }
.article-body { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 4px; }
.article-title {
  font-size: var(--font-body, 15px); font-weight: 700; color: var(--text-primary, #1c1917);
  display: -webkit-box; line-clamp: 2; -webkit-line-clamp: 2;
  -webkit-box-orient: vertical; overflow: hidden; line-height: 1.35;
}
.article-summary {
  font-size: var(--font-caption, 13px); color: var(--text-secondary, #78716c); line-height: 1.45;
  display: -webkit-box; line-clamp: 2; -webkit-line-clamp: 2;
  -webkit-box-orient: vertical; overflow: hidden;
}
.article-tag {
  align-self: flex-start; margin-top: 4px;
  background: var(--primary-soft, #fcf5f2); padding: 3px 8px; border-radius: 6px;
}
.article-tag-text { font-size: 10px; font-weight: 700; color: var(--primary-color, #cd6a43); text-transform: uppercase; letter-spacing: 0.04em; }

/* ---- Consultation cards ---- */
.consult-list { display: flex; flex-direction: column; gap: 12px; padding: 0 20px; }
.consult-card {
  border-radius: var(--radius-md, 16px); padding: 14px 16px;
  display: flex; flex-direction: column; gap: 6px;
}
.consult-head { display: flex; flex-direction: column; gap: 4px; }
.consult-title { font-size: var(--font-body, 15px); font-weight: 700; color: var(--text-primary, #1c1917); line-height: 1.3; }
.consult-author { font-size: 11px; color: var(--text-tertiary, #8e8e93); font-weight: 500; }
.consult-body { font-size: var(--font-caption, 13px); color: var(--text-secondary, #78716c); line-height: 1.55; white-space: pre-line; }
.consult-link-hint { font-size: 12px; color: var(--primary-color, #cd6a43); margin-top: 6px; font-weight: 500; }

/* ---- Career path spotlight ---- */
.path-grid { display: flex; flex-wrap: wrap; gap: 12px; padding: 0 20px; }
.path-card {
  width: calc(50% - 6px); box-sizing: border-box;
  border-radius: var(--radius-md, 16px); padding: 16px;
  display: flex; flex-direction: column; gap: 6px;
  background: var(--surface-1, #ffffff);
}
.path-icon-wrap {
  width: 32px; height: 32px; border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
  margin-bottom: 4px;
}
.path-icon-glyph { font-size: 16px; font-weight: 800; }
.tone-0 { background: var(--primary-soft, #fcf5f2); color: var(--primary-color, #cd6a43); }
.tone-1 { background: #faf1ed; color: #d0724d; }
.tone-2 { background: #f8ece7; color: #d37b58; }
.tone-3 { background: #ffedd5; color: #ea580c; }
.path-name { font-size: var(--font-body, 15px); font-weight: 700; color: var(--text-primary, #1c1917); }
.path-desc {
  font-size: var(--font-caption, 13px); color: var(--text-secondary, #78716c); line-height: 1.45;
  display: -webkit-box; line-clamp: 3; -webkit-line-clamp: 3;
  -webkit-box-orient: vertical; overflow: hidden;
}

/* ---- Empty / safe area ---- */
.empty-state { text-align: center; padding: 60px 20px 20px; }
.empty-icon { font-size: 48px; display: block; margin-bottom: 16px; }
.empty-text { font-size: 15px; color: var(--text-secondary, #78716c); font-weight: 500; display: block; margin-bottom: 24px; }
.btn-clear {
  background: #cd6a43; color: #fff; font-size: 14px; font-weight: 600;
  border-radius: var(--radius-sm, 12px); height: 40px; line-height: 40px; border: none; width: 140px;
}
.bottom-safe { height: calc(var(--tab-bar-height, 50px) + 20px); }

.calibration-mask {
  position: fixed;
  inset: 0;
  z-index: 50;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(28, 25, 23, 0.38);
  backdrop-filter: blur(8px);
}
.calibration-panel {
  width: 100%;
  max-width: 360px;
  border-radius: 18px;
  padding: 24px 20px 20px;
  text-align: center;
  box-shadow: 0 24px 64px rgba(28, 25, 23, 0.24);
}
.calibration-orbit {
  position: relative;
  width: 180px;
  height: 180px;
  margin: 0 auto 18px;
}
.calibration-core {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 64px;
  height: 64px;
  margin-left: -32px;
  margin-top: -32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #cd6a43, #624d23);
  box-shadow: 0 16px 28px rgba(205, 106, 67, 0.28);
}
.calibration-core-icon { font-size: 28px; color: #fff; }
.calibration-signal {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 38px;
  height: 38px;
  margin-left: -19px;
  margin-top: -19px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #cd6a43;
  background: #fcf5f2;
  border: 1px solid rgba(205, 106, 67, 0.16);
  animation: signalGather 1.8s ease-in-out infinite;
}
.calibration-signal text { font-size: 18px; }
.signal-assessment { --sx: -64px; --sy: -58px; animation-delay: 0s; }
.signal-resume { --sx: 58px; --sy: -62px; animation-delay: .1s; }
.signal-interview { --sx: 72px; --sy: 14px; animation-delay: .2s; }
.signal-checkin { --sx: 36px; --sy: 70px; animation-delay: .3s; }
.signal-employment { --sx: -58px; --sy: 56px; animation-delay: .4s; }
.signal-tags { --sx: -78px; --sy: 0px; animation-delay: .5s; }
@keyframes signalGather {
  0% { transform: translate(var(--sx), var(--sy)) scale(1); opacity: .95; }
  58% { transform: translate(0, 0) scale(.72); opacity: .42; }
  100% { transform: translate(var(--sx), var(--sy)) scale(1); opacity: .95; }
}
.calibration-title {
  display: block;
  font-size: 18px;
  line-height: 1.35;
  font-weight: 900;
  color: var(--text-primary, #1c1917);
}
.calibration-desc {
  display: block;
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.55;
  color: var(--text-secondary, #78716c);
}
.calibration-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-top: 18px;
}
.calibration-primary,
.calibration-secondary {
  height: 40px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.calibration-primary { background: var(--primary-color, #cd6a43); }
.calibration-secondary {
  background: var(--surface-2, #fafaf9);
  border: 1px solid var(--border-color, #e7e5e4);
}
.calibration-primary-text,
.calibration-secondary-text { font-size: 13px; font-weight: 900; }
.calibration-primary-text { color: #fff; }
.calibration-secondary-text { color: var(--text-secondary, #78716c); }

/* ---- Dark Mode ---- */
.is-dark { background-color: var(--text-primary, #1c1917); }
.is-dark .search-bar { background: #292524; box-shadow: none; }
.is-dark .search-input { color: #fafaf9; }
.is-dark .feature-label { color: #e7e5e4; }
.is-dark .feature-item { background: #292524; box-shadow: none; border-color: #44403c; }
.is-dark .feature-item:nth-child(1) { background: linear-gradient(135deg, #3c1c10, #4f2a1c); }
.is-dark .feature-item:nth-child(2) { background: linear-gradient(135deg, #5c2c19, #4f2a1c); }
.is-dark .feature-item:nth-child(3) { background: linear-gradient(135deg, #4a1c4e, #5c2c19); }
.is-dark .feature-item:nth-child(4) { background: linear-gradient(135deg, #451a03, #450a0a); }
.is-dark .feature-item .app-icon-tile {
  background: rgba(255, 255, 255, 0.16) !important;
  border: 1px solid rgba(255, 255, 255, 0.18);
  color: #fafaf9 !important;
}
.is-dark .section-title { color: #fafaf9; }
.is-dark .video-card,
.is-dark .article-card,
.is-dark .consult-card,
.is-dark .path-card { background: #292524; box-shadow: none; border-color: #44403c; }
.is-dark .checkin-card { border-color: #7d3b21; }
.is-dark .checkin-kicker { color: #d6bd8a; }
.is-dark .checkin-title { color: #fcf6f3; }
.is-dark .checkin-sub { color: #f0d3c7; }
.is-dark .checkin-cta { color: #d6bd8a; }
.is-dark .checkin-tip-text { color: var(--text-tertiary, #8e8e93); }
.is-dark .cdut-card,
.is-dark .cdut-metric,
.is-dark .cdut-highlight {
  background: #292524;
  border-color: #44403c;
  box-shadow: none;
}
.is-dark .cdut-icon-wrap,
.is-dark .cdut-match {
  background: rgba(205, 106, 67, 0.2);
}
.is-dark .cdut-title,
.is-dark .cdut-metric-val { color: #fafaf9; }
.is-dark .cdut-summary,
.is-dark .cdut-highlight-text { color: #a8a29e; }
.is-dark .article-tag { background: #4f2a1c; }
.is-dark .article-tag-text,
.is-dark .section-more-text,
.is-dark .section-more-arrow,
.is-dark .video-up,
.is-dark .consult-link-hint { color: #e8baa8; }
.is-dark .video-title,
.is-dark .article-title,
.is-dark .consult-title,
.is-dark .path-name { color: #fafaf9; }
.is-dark .article-summary,
.is-dark .consult-body,
.is-dark .path-desc { color: var(--text-tertiary, #8e8e93); }
.is-dark .tone-0 { background: rgba(205, 106, 67, 0.22); color: #e8baa8; }
.is-dark .tone-1 { background: rgba(218, 144, 114, 0.22); color: #efcfc3; }
.is-dark .tone-2 { background: rgba(219, 149, 121, 0.22); color: #f3dbd2; }
.is-dark .tone-3 { background: rgba(245, 158, 11, 0.2); color: #fcd34d; }
.is-dark .risk-medium { background: rgba(245, 158, 11, 0.18); border: 1px solid rgba(245, 158, 11, 0.38); }
.is-dark .risk-medium .risk-text { color: #fbbf24; }
.is-dark .risk-low { background: rgba(149, 117, 52, 0.18); border: 1px solid rgba(149, 117, 52, 0.36); }
.is-dark .risk-low .risk-text { color: #d3b882; }
.is-dark .risk-high { background: rgba(239, 68, 68, 0.18); border: 1px solid rgba(239, 68, 68, 0.36); }
.is-dark .risk-high .risk-text { color: #fca5a5; }

/* ================================================================
 *  MP-WEIXIN parity overrides — HARDCODED values, no CSS vars.
 *  CSS custom properties set on :root / page may not cascade into
 *  scoped component styles in the mini-program runtime, so we
 *  bypass var() entirely and write concrete values here.
 * ================================================================ */
/* #ifdef MP-WEIXIN */

/* ---- Page background: slightly more contrast vs. white cards ---- */
.home-page {
  background-color: var(--surface-1, #ffffff);
}

.is-dark.home-page {
  background-color: var(--text-primary, #1c1917);
}

/* ---- Icon pills: force GPU compositing to fix blurry border-radius ---- */
.app-icon-tile {
  transform: translateZ(0);
  -webkit-transform: translateZ(0);
  will-change: transform;
}

/* ---- Search bar ---- */
.search-bar {
  background: #ffffff;
  border: none;
  box-shadow: 0 2px 8px rgba(28, 25, 23,0.06);
}

/* ---- Daily check-in card ---- */
.checkin-card {
  overflow: visible;
  background: linear-gradient(135deg, #957534 0%, #bf9748 100%);
  border: none;
  box-shadow: 0 4px 12px rgba(0,0,0,0.04), 0 2px 4px rgba(0,0,0,0.04);
}
.checkin-kicker { color: rgba(255,255,255,0.8); }
.checkin-title  { color: #ffffff; }
.checkin-sub    { color: rgba(255,255,255,0.9); }
.checkin-cta    { color: #ffffff; }

/* ---- Article / consult / path cards ---- */
.article-card,
.consult-card {
  overflow: visible;
  border: 1px solid #e7e5e4;
  box-shadow: 0 4px 12px rgba(0,0,0,0.04), 0 2px 4px rgba(0,0,0,0.04);
}

.cdut-card {
  overflow: visible;
  border: 1px solid #e7e5e4;
  box-shadow: 0 4px 12px rgba(0,0,0,0.04), 0 2px 4px rgba(0,0,0,0.04);
}

.path-card {
  overflow: visible;
  border: 1px solid #e7e5e4;
  box-shadow: 0 4px 12px rgba(0,0,0,0.04), 0 2px 4px rgba(0,0,0,0.04);
}

/* ---- Video cards: overflow:hidden must stay for image clipping,
        so use filter:drop-shadow which renders outside the layer ---- */
.video-card {
  overflow: hidden;
  box-shadow: none;
  filter: none;
}

.home-page.is-dark .feature-item {
  background: #292524;
  border-color: #44403c;
  box-shadow: none;
}

.home-page.is-dark .search-bar {
  background: #292524;
  border-color: #44403c;
  box-shadow: none;
}

.home-page.is-dark .checkin-card {
  background: linear-gradient(135deg, #401e11, #602e1a);
  border-color: #7d3b21;
  box-shadow: none;
}

.home-page.is-dark .checkin-kicker { color: #d6bd8a; }
.home-page.is-dark .checkin-title { color: #fcf6f3; }
.home-page.is-dark .checkin-sub { color: #f0d3c7; }
.home-page.is-dark .checkin-cta { color: #d6bd8a; }

.home-page.is-dark .article-card,
.home-page.is-dark .consult-card,
.home-page.is-dark .path-card,
.home-page.is-dark .cdut-card {
  background: #292524;
  border-color: #44403c;
  box-shadow: none;
}

.home-page.is-dark .video-card {
  background: #292524;
  border-color: #44403c;
}

/* #endif */

/* Final editorial pass for the content feed and profile calibration overlay. */
.video-title,
.article-title,
.consult-title,
.path-name {
  color: #2c2b29;
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
  font-weight: 600;
  letter-spacing: 0.035em;
}

.tone-0 {
  background: #f7f1ef;
  color: #ac6448;
}

.tone-1 {
  background: #f1efeb;
  color: #766f5d;
}

.tone-2 {
  background: #f8ece8;
  color: #c23b22;
}

.tone-3 {
  background: #f6f1e7;
  color: #9a783f;
}

.btn-clear {
  border: 1px solid #c23b22;
  border-radius: 6px;
  background: #c23b22;
  color: #fffdfa;
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
  font-weight: 600;
}

.calibration-mask {
  background: rgba(44, 43, 41, 0.48);
  backdrop-filter: blur(3px);
}

.calibration-panel {
  border: 1px solid #e0dfdb;
  border-radius: 8px;
  background: #fffdfa;
  box-shadow: 0 14px 40px rgba(44, 43, 41, 0.14);
}

.calibration-orbit {
  width: 168px;
  height: 168px;
}

.calibration-core {
  width: 58px;
  height: 58px;
  margin-left: -29px;
  margin-top: -29px;
  border-radius: 8px;
  background: #c23b22;
  box-shadow: 0 8px 20px rgba(194, 59, 34, 0.16);
}

.calibration-signal {
  border: 1px solid #d8d6cf;
  border-radius: 6px;
  background: #faf9f6;
  color: #ac6448;
}

.calibration-signal.signal-resume,
.calibration-signal.signal-checkin {
  color: #8d846e;
}

.calibration-signal.signal-interview,
.calibration-signal.signal-tags {
  color: #c23b22;
}

.calibration-signal.signal-employment {
  color: #b8975a;
}

.calibration-title {
  color: #2c2b29;
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
  font-weight: 600;
  letter-spacing: 0.05em;
}

.calibration-desc {
  color: #5a5956;
}

.calibration-primary,
.calibration-secondary {
  border-radius: 6px;
}

.calibration-primary {
  background: #c23b22;
}

.calibration-secondary {
  border-color: #d8d6cf;
  background: #faf9f6;
}

.calibration-primary-text,
.calibration-secondary-text {
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.calibration-secondary-text {
  color: #5a5956;
}

.article-card,
.consult-card,
.path-card,
.cdut-card {
  border-color: #e0dfdb;
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(44, 43, 41, 0.05);
}

.video-card {
  border-radius: 8px;
}

.home-page.is-dark .video-title,
.home-page.is-dark .article-title,
.home-page.is-dark .consult-title,
.home-page.is-dark .path-name {
  color: #f5f2ea;
}

.home-page.is-dark .tone-0 {
  background: rgba(172, 100, 72, 0.24);
  color: #e5beaf;
}

.home-page.is-dark .tone-1 {
  background: rgba(141, 132, 110, 0.24);
  color: #c5bfb0;
}

.home-page.is-dark .tone-2 {
  background: rgba(194, 59, 34, 0.22);
  color: #e27b66;
}

.home-page.is-dark .tone-3 {
  background: rgba(184, 151, 90, 0.22);
  color: #dfc58f;
}

.home-page.is-dark .calibration-panel {
  border-color: #46443e;
  background: #302f2a;
}

.home-page.is-dark .calibration-title {
  color: #f5f2ea;
}

.home-page.is-dark .calibration-desc,
.home-page.is-dark .calibration-secondary-text {
  color: #bbb7ae;
}

.home-page.is-dark .calibration-signal,
.home-page.is-dark .calibration-secondary {
  border-color: #575650;
  background: #24231f;
}

.agent-title,
.agent-focus,
.risk-watch-title,
.agent-plan-title,
.agent-task-title,
.checkin-title,
.cdut-title,
.cdut-metric-val,
.article-cover-label,
.path-icon-glyph {
  font-weight: 600;
}

.agent-kicker,
.risk-text,
.risk-watch-kicker,
.agent-plan-kicker,
.agent-plan-action-text,
.agent-action-text,
.agent-task-btn-text,
.cdut-kicker,
.cdut-match {
  font-weight: 600;
}

.agent-title,
.agent-focus,
.risk-watch-title,
.agent-plan-title,
.agent-task-title,
.checkin-title,
.cdut-title,
.cdut-metric-val {
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
}
</style>
