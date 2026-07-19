<template>
  <SlPage class="hub-page app-soft-bg" :custom-class="[themeClass, fontClass].join(' ')">
    <SlNavBar
      :title="t('agent.hub.title')"
      show-back
      @back="goBack"
      :safe-top="topSafeHeight"
      :right-avoid-width="rightAvoidWidth"
    >
      <template #right>
        <view class="hub-refresh" @click="loadAll"><text class="hub-refresh-icon">↻</text></view>
      </template>
    </SlNavBar>

    <scroll-view scroll-y class="hub-scroll" :style="{ height: scrollHeight }">
      <text class="hub-brand-kicker">智绘职路 CareerLoop · 学生成长端</text>

      <!-- ① Today -->
      <view v-if="agentToday" class="hub-section app-card-soft">
        <view class="hub-section-head">
          <text class="hub-section-title">{{ t('agent.hub.todaySection') }}</text>
          <view class="hub-stage-pill"><text class="hub-stage-text">{{ stageLabel }}</text></view>
        </view>
        <text class="hub-headline">{{ displayText(agentToday.headlineKey ? t(agentToday.headlineKey) : agentToday.headline) }}</text>
        <text class="hub-focus">{{ displayText(agentToday.focusKey ? t(agentToday.focusKey) : agentToday.todayFocus) }}</text>
        <view class="hub-reason-box">
          <text class="hub-reason-label">{{ t('agent.hub.reasonSection') }}</text>
          <text class="hub-reason-text">{{ displayText(agentToday.reasonKey ? t(agentToday.reasonKey) : agentToday.reason) }}</text>
        </view>
        <view class="hub-reason-box hub-outcome-box">
          <text class="hub-reason-label">{{ t('agent.hub.outcomeSection') }}</text>
          <text class="hub-reason-text">{{ todayOutcome }}</text>
        </view>
        <view v-if="primaryTask" class="hub-primary-actions">
          <view class="hub-primary-cta" @click="navTo(taskTarget(primaryTask))">
            <text class="hub-primary-cta-text">{{ t('agent.hub.startTodayAction') }}</text>
            <text class="hub-primary-cta-arrow">›</text>
          </view>
          <view class="hub-primary-done" @click="completeTask(primaryTask.taskId)">
            <text class="hub-primary-done-text">{{ t('agent.hub.taskDone') }}</text>
          </view>
        </view>
        <view class="hub-progress-bar">
          <view class="hub-progress-fill" :style="{ width: todayProgressPercent + '%' }" />
        </view>
        <text class="hub-progress-text">{{ t('agent.hub.readiness', { n: todayProgressPercent }) }}</text>
        <view v-if="!primaryTask && agentToday.actions?.length" class="hub-actions">
          <view
            v-for="action in agentToday.actions.slice(0, 3)"
            :key="action.label"
            class="hub-action"
            :class="{ 'hub-action-primary': action.priority === 'HIGH' }"
            @click="navTo(action.target)"
          >
            <text class="hub-action-text">{{ displayText(action.labelKey ? t(action.labelKey) : action.label) }}</text>
          </view>
        </view>
      </view>
      <view v-else class="hub-section app-card-soft">
        <view class="hub-section-head">
          <text class="hub-section-title">{{ t('agent.hub.todaySection') }}</text>
          <view class="hub-stage-pill"><text class="hub-stage-text">先建立基线</text></view>
        </view>
        <text class="hub-headline">先完成一次职业测评</text>
        <text class="hub-focus">{{ loadError || '暂时还没有生成今日行动，先用测评建立第一份求职画像。' }}</text>
        <view class="hub-reason-box">
          <text class="hub-reason-label">{{ t('agent.hub.reasonSection') }}</text>
          <text class="hub-reason-text">测评结果会帮助系统判断目标方向、简历重点和后续面试练习顺序。</text>
        </view>
        <view class="hub-reason-box hub-outcome-box">
          <text class="hub-reason-label">{{ t('agent.hub.outcomeSection') }}</text>
          <text class="hub-reason-text">目标方向、岗位建议和后续准备重点</text>
        </view>
        <view class="hub-primary-actions">
          <view class="hub-primary-cta" @click="navTo('/pages/assessment/index')">
            <text class="hub-primary-cta-text">开始测评</text>
            <text class="hub-primary-cta-arrow">›</text>
          </view>
        </view>
      </view>

      <!-- ② Tasks -->
      <view class="hub-section app-card-soft">
        <view class="hub-section-head">
          <text class="hub-section-title">{{ t('agent.hub.tasksSection') }}</text>
          <text class="hub-task-count">{{ t('agent.hub.tasksOpen', { n: secondaryTasks.length }) }}</text>
        </view>
        <view v-if="secondaryTasks.length === 0" class="hub-empty">
          <text class="hub-empty-text">{{ t('agent.hub.tasksEmpty') }}</text>
        </view>
        <view v-for="task in secondaryTasks" :key="task.taskId" class="hub-task">
          <view class="hub-task-header" @click="toggleTask(task.taskId)">
            <view class="hub-task-meta">
              <view class="hub-task-badges">
                <view v-if="task.difficulty" class="hub-badge" :class="'diff-' + task.difficulty.toLowerCase()">
                  <text class="hub-badge-text">{{ task.difficulty }}</text>
                </view>
                <view v-if="task.estimatedMinutes" class="hub-badge hub-badge-time">
                  <text class="hub-badge-text">{{ task.estimatedMinutes }}min</text>
                </view>
              </view>
              <text class="hub-task-title">{{ displayText(task.title) }}</text>
              <text v-if="task.description" class="hub-task-desc">{{ displayText(task.description) }}</text>
            </view>
            <view class="hub-task-right">
              <view class="hub-task-actions">
                <view class="hub-task-btn hub-task-done" @click.stop="completeTask(task.taskId)">
                  <text class="hub-task-btn-text">{{ t('agent.hub.taskDone') }}</text>
                </view>
                <view class="hub-task-btn" @click.stop="dismissTask(task.taskId)">
                  <text class="hub-task-btn-text">{{ t('agent.hub.taskSkip') }}</text>
                </view>
              </view>
              <view class="hub-expand-btn" @click.stop="expandTask(task)">
                <text class="hub-expand-text">{{ expandedTasks.has(task.taskId) ? t('agent.hub.taskCollapse') : t('agent.hub.taskDecompose') }}</text>
              </view>
            </view>
          </view>
          <!-- subtasks -->
          <view v-if="expandedTasks.has(task.taskId)" class="hub-subtasks">
            <view v-if="subtaskLoading.has(task.taskId)" class="hub-subtask-loading">
              <text class="hub-subtask-loading-text">{{ t('agent.hub.taskGenerating') }}</text>
            </view>
            <view
              v-for="sub in subtaskMap.get(task.taskId) || []"
              :key="sub.taskId"
              class="hub-subtask"
              :class="{ 'hub-subtask-done': sub.status === 'DONE' }"
            >
              <view class="hub-subtask-badges">
                <view v-if="sub.difficulty" class="hub-badge" :class="'diff-' + sub.difficulty.toLowerCase()">
                  <text class="hub-badge-text">{{ sub.difficulty }}</text>
                </view>
                <view v-if="sub.estimatedMinutes" class="hub-badge hub-badge-time">
                  <text class="hub-badge-text">{{ sub.estimatedMinutes }}min</text>
                </view>
              </view>
              <text class="hub-subtask-title">{{ displayText(sub.title) }}</text>
              <view v-if="sub.status !== 'DONE'" class="hub-subtask-done-btn" @click="completeTask(sub.taskId)">
                <text class="hub-subtask-done-text">✓</text>
              </view>
            </view>
          </view>
        </view>
      </view>

      <!-- ③ Plan -->
      <view v-if="agentPlan" class="hub-section app-card-soft">
        <view class="hub-section-head">
          <text class="hub-section-title">{{ t('agent.hub.planSection') }}</text>
          <text class="hub-plan-health" :class="'health-' + (agentPlan.planHealth || 'missing').toLowerCase()">
            {{ planHealthLabel }}
          </text>
        </view>
        <text class="hub-plan-role">{{ displayRole(agentPlan.targetRole) || t('agent.hub.planRoleEmpty') }}</text>
        <text v-if="agentPlan.nextMilestoneTitle" class="hub-plan-milestone">
          {{ displayText(agentPlan.nextMilestoneHorizon) }} · {{ displayText(agentPlan.nextMilestoneTitle) }}
        </text>
        <view v-if="agentPlan.weeklyFocus?.length" class="hub-focus-list">
          <text v-for="f in agentPlan.weeklyFocus" :key="f" class="hub-focus-item">• {{ displayText(f) }}</text>
        </view>
        <view
          v-if="!agentPlan.hasPlan || agentPlan.planHealth === 'NEEDS_REFRESH'"
          class="hub-plan-btn"
          @click="ensurePlan"
        >
          <text class="hub-plan-btn-text">{{ planBtnLabel }}</text>
        </view>
      </view>

      <!-- ④ Current gap -->
      <view v-if="agentRisk" class="hub-section app-card-soft">
        <view class="hub-section-head">
          <text class="hub-section-title">{{ t('agent.hub.riskSection') }}</text>
        </view>
        <text class="hub-risk-primary">{{ displayText(agentRisk.risks?.[0]?.titleKey ? t(agentRisk.risks[0].titleKey) : agentRisk.primaryRiskTitle) }}</text>
        <text class="hub-risk-summary">{{ displayText(agentRisk.summary) }}</text>
        <view class="hub-risks-list">
          <view v-for="r in agentRisk.risks" :key="r.code" class="hub-risk-row">
            <view class="hub-risk-row-left">
              <view class="hub-risk-dot" :class="'dot-' + r.level.toLowerCase()" />
              <text class="hub-risk-row-title">{{ displayText(r.titleKey ? t(r.titleKey) : r.title) }}</text>
            </view>
            <view class="hub-risk-row-right">
              <text class="hub-risk-trend" :class="'trend-' + r.trend.toLowerCase()">{{ r.trend === 'RISING' ? t('home.riskTrendRising') : r.trend === 'DECREASING' ? t('home.riskTrendImproving') : t('home.riskTrendStable') }}</text>
            </view>
          </view>
        </view>
      </view>

      <!-- ⑤ Preference completeness -->
      <view class="hub-section app-card-soft" @click="navTo('/pages/agent/profile')">
        <view class="hub-section-head">
          <text class="hub-section-title">{{ t('agent.hub.profileSection') }}</text>
          <text class="hub-section-arrow">›</text>
        </view>
        <view v-if="agentProfile" class="hub-pct-wrap">
          <view class="hub-pct-bar">
            <view
              class="hub-pct-fill"
              :class="'pct-' + agentProfile.personalizationLevel.toLowerCase()"
              :style="{ width: agentProfile.completenessScore + '%' }"
            />
          </view>
          <text class="hub-pct-label">{{ levelLabel }} · {{ agentProfile.completenessScore }}%</text>
        </view>
        <view v-if="agentProfile?.missingSignals?.length" class="hub-missing-row">
          <view
            v-for="sig in agentProfile.missingSignals.slice(0, 3)"
            :key="sig.key"
            class="hub-missing-chip"
            :class="'chip-' + sig.priority.toLowerCase()"
          >
            <text class="hub-missing-chip-text">+ {{ displayText(sig.label) }}</text>
          </view>
        </view>
      </view>

      <!-- ⑥ Recent review -->
      <view v-if="weeklyReview" class="hub-section app-card-soft">
        <view class="hub-section-head">
          <text class="hub-section-title">{{ t('agent.hub.reviewSection') }}</text>
          <text class="hub-review-date">{{ weeklyReviewDate }}</text>
        </view>
        <view class="hub-review-stats">
          <view class="hub-stat">
            <text class="hub-stat-val">{{ Math.round((reviewPayload?.completionRate7d || 0) * 100) }}%</text>
            <text class="hub-stat-label">{{ t('agent.hub.reviewCompletionRate') }}</text>
          </view>
          <view class="hub-stat">
            <text class="hub-stat-val">{{ reviewPayload?.currentStage || '-' }}</text>
            <text class="hub-stat-label">{{ t('agent.hub.reviewStage') }}</text>
          </view>
          <view class="hub-stat">
            <text class="hub-stat-val">{{ reviewPayload?.preferredDifficulty || '-' }}</text>
            <text class="hub-stat-label">{{ t('agent.hub.reviewDifficulty') }}</text>
          </view>
        </view>
        <view v-if="reviewPayload?.highlights?.length" class="hub-review-highlights">
          <text v-for="h in reviewPayload.highlights" :key="h" class="hub-review-highlight">· {{ displayText(h) }}</text>
        </view>
      </view>

      <!-- ⑦ Action stats -->
      <view v-if="agentState" class="hub-section hub-state-section app-card-soft">
        <text class="hub-section-title">{{ t('agent.hub.stateSection') }}</text>
        <view class="hub-state-stats">
          <view class="hub-stat">
            <text class="hub-stat-val">{{ Math.round((agentState.taskCompletionRate7d || 0) * 100) }}%</text>
            <text class="hub-stat-label">{{ t('agent.hub.stateCompletion7d') }}</text>
          </view>
          <view class="hub-stat">
            <text class="hub-stat-val">{{ Math.round((agentState.taskDismissRate7d || 0) * 100) }}%</text>
            <text class="hub-stat-label">{{ t('agent.hub.stateDismiss7d') }}</text>
          </view>
          <view class="hub-stat">
            <text class="hub-stat-val">{{ agentState.preferredTaskDifficulty }}</text>
            <text class="hub-stat-label">{{ t('agent.hub.stateDifficulty') }}</text>
          </view>
        </view>
      </view>

      <!-- ⑧ Recent activity -->
      <view class="hub-section hub-state-section app-surface">
        <view class="hub-section-head">
          <text class="hub-section-title">{{ t('agent.hub.activitySection') }}</text>
        </view>
        <view v-if="recentEvents.length === 0" class="hub-empty">
          <text class="hub-empty-text">{{ t('agent.hub.activityEmpty') }}</text>
        </view>
        <view v-for="ev in recentEvents" :key="ev.eventId" class="hub-event-row">
          <view class="hub-event-icon-wrap" :class="'ev-' + eventColorClass(ev.eventType)">
            <text class="hub-event-icon">{{ eventIcon(ev.eventType) }}</text>
          </view>
          <view class="hub-event-body">
            <text class="hub-event-label">{{ eventLabel(ev.eventType) }}</text>
            <text class="hub-event-time">{{ formatEventTime(ev.createdAt) }}</text>
          </view>
        </view>
      </view>

      <view class="hub-bottom-spacer" />
    </scroll-view>
  </SlPage>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useI18n } from '@/locales';
import SlNavBar from '@/style-library/components/SlNavBar.vue';
import SlPage from '@/style-library/components/SlPage.vue';
import {
  completeAgentTaskApi,
  decomposeTaskApi,
  dismissAgentTaskApi,
  ensureCareerAgentPlanApi,
  getAgentEventsApi,
  getAgentProfileApi,
  getAgentStateApi,
  getCareerAgentPlanSummaryApi,
  getCareerAgentRiskWatchApi,
  getCareerAgentTodayApi,
  getWeeklyReviewLatestApi,
  getTodayAgentTasksApi,
  type AgentEvent,
  type AgentState,
  type AgentTask,
  type AgentUserProfile,
  type CareerAgentPlanSummary,
  type CareerAgentRiskWatch,
  type CareerAgentToday,
} from '@/api/agent';
import { getMpSafeAreaMetrics } from '@/utils/safeArea';
import { useTheme } from '@/utils/theme';
import { normalizeProductCopy, normalizeRoleLabel } from '@/utils/displayText';
import { selectPrimaryTask, taskOutcome, taskTarget } from '@/utils/taskDisplay';

const { t } = useI18n();
const { themeClass, fontClass, refresh: refreshTheme } = useTheme();

const topSafeHeight = ref(52);
const rightAvoidWidth = ref(16);
const scrollHeight = ref('calc(100vh - 88px)');
const agentProfile = ref<AgentUserProfile | null>(null);
const agentToday = ref<CareerAgentToday | null>(null);
const agentRisk = ref<CareerAgentRiskWatch | null>(null);
const agentPlan = ref<CareerAgentPlanSummary | null>(null);
const agentState = ref<AgentState | null>(null);
const weeklyReview = ref<AgentEvent | null>(null);
const openTasks = ref<AgentTask[]>([]);
const recentEvents = ref<AgentEvent[]>([]);
const loadError = ref('');

const expandedTasks = reactive(new Set<number>());
const subtaskMap = reactive(new Map<number, AgentTask[]>());
const subtaskLoading = reactive(new Set<number>());

const displayText = normalizeProductCopy;
const displayRole = normalizeRoleLabel;

const levelLabel = computed(() => {
  const lvl = agentProfile.value?.personalizationLevel || 'LOW';
  if (lvl === 'HIGH') return t('agent.hub.profileLevelHigh');
  if (lvl === 'MEDIUM') return t('agent.hub.profileLevelMedium');
  return t('agent.hub.profileLevelLow');
});

const stageLabel = computed(() => {
  const s = (agentToday.value?.stage || '') as keyof typeof stageMap;
  const stageMap = {
    DIRECTION_DISCOVERY: t('agent.stage.DIRECTION_DISCOVERY'),
    TARGET_ROLE_SELECTION: t('agent.stage.TARGET_ROLE_SELECTION'),
    ASSESSMENT_BASELINE: t('agent.stage.ASSESSMENT_BASELINE'),
    INTERNSHIP_RESUME_BOOTSTRAP: t('agent.stage.INTERNSHIP_RESUME_BOOTSTRAP'),
    GRADUATE_RESUME_UPLOAD: t('agent.stage.GRADUATE_RESUME_UPLOAD'),
    CAREER_SWITCH_POSITIONING: t('agent.stage.CAREER_SWITCH_POSITIONING'),
    RESUME_BOOTSTRAP: t('agent.stage.RESUME_BOOTSTRAP'),
    RESUME_IMPROVEMENT: t('agent.stage.RESUME_IMPROVEMENT'),
    INTERVIEW_BOOTSTRAP: t('agent.stage.INTERVIEW_BOOTSTRAP'),
    INTERVIEW_IMPROVEMENT: t('agent.stage.INTERVIEW_IMPROVEMENT'),
    EXECUTION_RHYTHM: t('agent.stage.EXECUTION_RHYTHM'),
    CAREER_MOMENTUM: t('agent.stage.CAREER_MOMENTUM'),
  };
  return stageMap[s] || s;
});

const planHealthLabel = computed(() => {
  const h = agentPlan.value?.planHealth || 'MISSING';
  if (h === 'ON_TRACK') return t('agent.hub.planHealthOnTrack');
  if (h === 'NEEDS_REFRESH') return t('agent.hub.planHealthNeedsRefresh');
  return t('agent.hub.planHealthMissing');
});

const planBtnLabel = computed(() =>
  agentPlan.value?.hasPlan ? t('agent.hub.planRefresh') : t('agent.hub.planGenerate')
);

const primaryTask = computed(() => selectPrimaryTask(openTasks.value, agentToday.value?.actions || []));
const secondaryTasks = computed(() => {
  const primaryId = primaryTask.value?.taskId;
  return openTasks.value.filter((task) => task.taskId !== primaryId);
});

const todayOutcome = computed(() => {
  const task = primaryTask.value;
  if (task) return taskOutcome(task);
  const action = agentToday.value?.actions?.[0];
  if (action?.type === 'RESUME') return t('agent.hub.outcomeResume');
  if (action?.type === 'INTERVIEW') return t('agent.hub.outcomeInterview');
  if (action?.type === 'ASSESSMENT') return t('agent.hub.outcomeAssessment');
  if (action?.type === 'TARGET_ROLE') return t('agent.hub.outcomeTargetRole');
  if (action?.type === 'PLAN' || action?.source === 'PLAN_WEEKLY') return t('agent.hub.outcomePlan');
  return t('agent.hub.outcomeProgress');
});

const todayProgressPercent = computed(() => {
  const raw = agentProfile.value?.readiness?.overallPercent ?? agentToday.value?.progressPercent ?? 0;
  return Math.max(0, Math.min(100, Math.round(raw)));
});

const weeklyReviewDate = computed(() => {
  const d = weeklyReview.value?.createdAt;
  return d ? d.substring(0, 10) : '';
});

const reviewPayload = computed(() => {
  try {
    return weeklyReview.value?.eventPayload
      ? JSON.parse(weeklyReview.value.eventPayload)
      : null;
  } catch { return null; }
});

const EVENT_ICONS: Record<string, string> = {
  TASK_COMPLETED: '✓',
  TASK_DISMISSED: '✕',
  RISK_CHANGED: '△',
  PLAN_GENERATED: '≡',
  RESUME_SCORE_CHANGED: '≡',
  INTERVIEW_SCORE_CHANGED: '◉',
  WEEKLY_REVIEW_COMPLETED: '↗',
};
const EVENT_COLORS: Record<string, string> = {
  TASK_COMPLETED: 'green',
  TASK_DISMISSED: 'gray',
  RISK_CHANGED: 'orange',
  PLAN_GENERATED: 'blue',
  RESUME_SCORE_CHANGED: 'blue',
  INTERVIEW_SCORE_CHANGED: 'blue',
  WEEKLY_REVIEW_COMPLETED: 'purple',
};

const eventIcon = (type: string) => EVENT_ICONS[type] || '·';
const eventColorClass = (type: string) => EVENT_COLORS[type] || 'gray';
const eventLabel = (type: string) => {
  const key = `agent.event.${type}` as Parameters<typeof t>[0];
  return t(key, type);
};
const formatEventTime = (raw?: string) => {
  if (!raw) return '';
  return raw.substring(0, 16).replace('T', ' ');
};

const loadAll = async () => {
  loadError.value = '';
  const [profile, today, risk, plan, state, review, tasks, events] = await Promise.allSettled([
    getAgentProfileApi(),
    getCareerAgentTodayApi(),
    getCareerAgentRiskWatchApi(),
    getCareerAgentPlanSummaryApi(),
    getAgentStateApi(),
    getWeeklyReviewLatestApi(),
    getTodayAgentTasksApi(),
    getAgentEventsApi(10),
  ]);

  if (profile.status === 'fulfilled') agentProfile.value = profile.value;
  if (today.status === 'fulfilled') agentToday.value = today.value;
  if (risk.status === 'fulfilled') agentRisk.value = risk.value;
  if (plan.status === 'fulfilled') agentPlan.value = plan.value;
  if (state.status === 'fulfilled') agentState.value = state.value;
  if (review.status === 'fulfilled') weeklyReview.value = review.value;
  if (tasks.status === 'fulfilled') openTasks.value = (tasks.value || []).filter((task) => task.status === 'TODO');
  if (events.status === 'fulfilled') recentEvents.value = events.value || [];
  if (today.status === 'rejected' && tasks.status === 'rejected') {
    loadError.value = '网络不稳定，先从基础测评开始也可以继续推进。';
  }
};

const completeTask = async (taskId: number) => {
  try {
    await completeAgentTaskApi(taskId);
    openTasks.value = openTasks.value.filter(t => t.taskId !== taskId);
    subtaskMap.delete(taskId);
    expandedTasks.delete(taskId);
    uni.showToast({ title: '已完成，下一步已更新', icon: 'success' });
    await loadAll();
  } catch {
    uni.showToast({ title: t('agent.hub.completeError'), icon: 'none' });
  }
};

const dismissTask = async (taskId: number) => {
  try {
    await dismissAgentTaskApi(taskId);
    openTasks.value = openTasks.value.filter(t => t.taskId !== taskId);
    subtaskMap.delete(taskId);
    expandedTasks.delete(taskId);
  } catch { /* ignore */ }
};

const expandTask = async (task: AgentTask) => {
  if (expandedTasks.has(task.taskId)) {
    expandedTasks.delete(task.taskId);
    return;
  }
  expandedTasks.add(task.taskId);
  if (!subtaskMap.has(task.taskId)) {
    subtaskLoading.add(task.taskId);
    try {
      const subs = await decomposeTaskApi(task.taskId);
      subtaskMap.set(task.taskId, subs || []);
    } catch {
      subtaskMap.set(task.taskId, []);
    } finally {
      subtaskLoading.delete(task.taskId);
    }
  }
};

const toggleTask = (taskId: number) => {
  if (expandedTasks.has(taskId)) expandedTasks.delete(taskId);
};

const ensurePlan = async () => {
  try {
    agentPlan.value = await ensureCareerAgentPlanApi();
    uni.showToast({ title: t('agent.hub.planUpdated'), icon: 'success' });
  } catch {
    uni.showToast({ title: t('agent.hub.planFailed'), icon: 'none' });
  }
};

const SWITCH_TAB_PATHS = new Set([
  '/pages/home/index',
  '/pages/assistant/index',
  '/pages/resume/index',
  '/pages/user/index',
]);

const navTo = (path: string) => {
  if (!path) {
    uni.showToast({ title: '暂时无法打开，请稍后重试', icon: 'none' });
    return;
  }
  const base = path.split('?')[0];
  if (SWITCH_TAB_PATHS.has(base)) {
    uni.switchTab({
      url: base,
      fail: () => uni.showToast({ title: '暂时无法打开，请稍后重试', icon: 'none' }),
    });
    return;
  }
  uni.navigateTo({
    url: path,
    fail: () => uni.showToast({ title: '暂时无法打开，请稍后重试', icon: 'none' }),
  });
};
const goBack = () => uni.navigateBack();

onMounted(() => {
  refreshTheme();
  const safeMetrics = getMpSafeAreaMetrics();
  topSafeHeight.value = safeMetrics.topSafeHeight;
  rightAvoidWidth.value = safeMetrics.rightAvoidWidth;
  scrollHeight.value = `calc(100vh - ${safeMetrics.contentTop}px)`;
  loadAll();
});
</script>

<style scoped>
.hub-page {
  box-sizing: border-box;
}
.hub-refresh {
  width: 64rpx; height: 64rpx; display: flex; align-items: center;
  justify-content: center; border-radius: 50%; background: var(--surface-3, #f1f5f9);
}
.hub-refresh-icon { font-size: 20px; color: var(--text-secondary, #64748b); }

.hub-scroll {
  width: 100%;
  box-sizing: border-box;
}

.hub-section {
  margin: 16rpx 24rpx; padding: 24rpx; margin-bottom: 24rpx;
  border-radius: 24rpx;
  box-sizing: border-box;
  overflow: hidden;
}

.hub-brand-kicker {
  display: block;
  margin: 2px 2px 10px;
  color: var(--brand-color, #3f51b5);
  font-size: 10px;
  line-height: 1.3;
  font-weight: 800;
  letter-spacing: 0.06em;
}
.hub-state-section { margin-bottom: 0; }
.hub-bottom-spacer { height: 48rpx; }

.hub-section-head {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 16rpx;
}
.hub-section-title { font-size: 13px; font-weight: 700; color: var(--text-secondary, #64748b); }
.hub-section-arrow { font-size: 16px; color: var(--text-tertiary, #8e8e93); }
.hub-task-count { font-size: 12px; color: var(--text-secondary, #64748b); }
.hub-review-date { font-size: 11px; color: var(--text-tertiary, #8e8e93); }

/* profile */
.hub-pct-wrap { display: flex; align-items: center; gap: 12rpx; margin-bottom: 12rpx; }
.hub-pct-bar { flex: 1; height: 8rpx; background: #e5e7eb; border-radius: 4rpx; overflow: hidden; }
.hub-pct-fill { height: 100%; border-radius: 4rpx; transition: width .4s; }
.pct-low { background: #94a3b8; } .pct-medium { background: #38bdf8; } .pct-high { background: #34d399; }
.hub-pct-label { font-size: 11.5px; color: var(--text-secondary, #64748b); white-space: nowrap; }
.hub-missing-row { display: flex; flex-wrap: wrap; gap: 8rpx; }
.hub-missing-chip {
  border-radius: 999rpx; padding: 6rpx 14rpx;
  border: 1.5rpx solid #d1d5db;
}
.chip-high { border-color: #ef4444; background: #fef2f2; }
.chip-medium { border-color: #f59e0b; background: #fffbeb; }
.chip-low { border-color: #d1d5db; background: var(--surface-2, #f8fafc); }
.hub-missing-chip-text { font-size: 11px; color: var(--text-secondary, #64748b); }

/* today */
.hub-stage-pill { background: var(--primary-soft, #eff6ff); border-radius: 999rpx; padding: 4rpx 14rpx; }
.hub-stage-text { font-size: 11px; color: var(--primary-hover, #1d4ed8); font-weight: 600; }
.hub-headline { font-size: 15px; font-weight: 700; color: var(--text-primary, #0f172a); line-height: 1.4; margin-bottom: 6rpx; }
.hub-focus { font-size: 12.5px; color: var(--text-secondary, #64748b); margin-bottom: 14rpx; }
.hub-reason-box {
  margin: 12rpx 0;
  padding: 16rpx;
  border-radius: 14rpx;
  background: var(--surface-2, #f8fafc);
  border: 1px solid var(--border-color, #e2e8f0);
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}
.hub-outcome-box {
  background: var(--primary-soft, #eff6ff);
  border-color: rgba(37, 99, 235, 0.14);
}
.hub-reason-label {
  font-size: 11px;
  font-weight: 800;
  color: var(--text-tertiary, #8e8e93);
}
.hub-reason-text {
  font-size: 12px;
  line-height: 1.5;
  color: var(--text-primary, #0f172a);
}
.hub-primary-actions {
  display: flex;
  align-items: center;
  gap: 14rpx;
  margin: 16rpx 0 14rpx;
}
.hub-primary-cta {
  flex: 1;
  min-width: 0;
  height: 76rpx;
  border-radius: 999rpx;
  background: linear-gradient(135deg, #1d4ed8, #6366f1);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
}
.hub-primary-cta-text,
.hub-primary-cta-arrow {
  font-size: 13px;
  color: #ffffff;
  font-weight: 800;
}
.hub-primary-done {
  flex-shrink: 0;
  height: 76rpx;
  padding: 0 22rpx;
  border-radius: 999rpx;
  background: var(--surface-3, #f1f5f9);
  display: flex;
  align-items: center;
  justify-content: center;
}
.hub-primary-done-text {
  font-size: 12px;
  color: var(--text-secondary, #64748b);
  font-weight: 700;
}
.hub-progress-bar { height: 6rpx; background: #e5e7eb; border-radius: 3rpx; overflow: hidden; margin-bottom: 6rpx; }
.hub-progress-fill { height: 100%; background: linear-gradient(90deg,#1d4ed8,#6366f1); border-radius: 3rpx; }
.hub-progress-text { font-size: 11px; color: var(--text-tertiary, #8e8e93); margin-bottom: 14rpx; }
.hub-actions { display: flex; flex-wrap: wrap; gap: 10rpx; }
.hub-action {
  border: 1.5rpx solid #d1d5db; border-radius: 999rpx;
  padding: 8rpx 18rpx; background: var(--surface-2, #f8fafc);
}
.hub-action-primary { border-color: var(--primary-hover, #1d4ed8); background: var(--primary-soft, #eff6ff); }
.hub-action-text { font-size: 12px; color: var(--text-secondary, #64748b); }
.hub-action-primary .hub-action-text { color: var(--primary-hover, #1d4ed8); font-weight: 600; }

/* tasks */
.hub-empty { padding: 20rpx 0; }
.hub-empty-text { font-size: 12.5px; color: var(--text-tertiary, #8e8e93); }
.hub-task { border-top: 1rpx solid #f3f4f6; padding-top: 16rpx; margin-top: 12rpx; }
.hub-task:first-child { border-top: none; margin-top: 0; }
.hub-task-header { display: flex; flex-direction: column; gap: 14rpx; }
.hub-task-meta { flex: 1; min-width: 0; }
.hub-task-badges { display: flex; gap: 6rpx; margin-bottom: 6rpx; flex-wrap: wrap; }
.hub-badge {
  border-radius: 6rpx; padding: 2rpx 10rpx;
}
.diff-easy { background: #d1fae5; } .diff-easy .hub-badge-text { color: #065f46; }
.diff-medium { background: #fef3c7; } .diff-medium .hub-badge-text { color: #92400e; }
.diff-hard { background: #fee2e2; } .diff-hard .hub-badge-text { color: #991b1b; }
.hub-badge-time { background: #ede9fe; }
.hub-badge-time .hub-badge-text { color: #5b21b6; }
.hub-badge-text { font-size: 10px; font-weight: 600; }
.hub-task-title { font-size: 13px; font-weight: 600; color: var(--text-primary, #0f172a); line-height: 1.45; word-break: break-word; }
.hub-task-desc { font-size: 11.5px; color: var(--text-secondary, #64748b); margin-top: 4rpx; line-height: 1.45; word-break: break-word; }
.hub-task-right { display: flex; align-items: center; justify-content: space-between; gap: 12rpx; width: 100%; }
.hub-task-actions { display: flex; gap: 8rpx; flex-wrap: wrap; min-width: 0; }
.hub-task-btn {
  border: 1.5rpx solid #d1d5db; border-radius: 999rpx;
  padding: 6rpx 14rpx; background: var(--surface-2, #f8fafc);
}
.hub-task-done { border-color: var(--primary-hover, #1d4ed8); background: var(--primary-soft, #eff6ff); }
.hub-task-btn-text { font-size: 11.5px; color: var(--text-secondary, #64748b); }
.hub-task-done .hub-task-btn-text { color: var(--primary-hover, #1d4ed8); font-weight: 600; }
.hub-expand-btn { border-radius: 999rpx; padding: 4rpx 12rpx; background: var(--surface-3, #f1f5f9); flex-shrink: 0; }
.hub-expand-text { font-size: 11px; color: var(--text-secondary, #64748b); }

/* subtasks */
.hub-subtasks { margin-top: 12rpx; padding-left: 16rpx; border-left: 3rpx solid #e0e7ff; }
.hub-subtask-loading { padding: 8rpx 0; }
.hub-subtask-loading-text { font-size: 12px; color: var(--text-tertiary, #8e8e93); }
.hub-subtask {
  display: flex; align-items: flex-start; gap: 8rpx;
  padding: 10rpx 0; border-bottom: 1rpx solid #f3f4f6;
}
.hub-subtask:last-child { border-bottom: none; }
.hub-subtask-done { opacity: .45; }
.hub-subtask-badges { display: flex; gap: 4rpx; margin-bottom: 4rpx; flex-wrap: wrap; }
.hub-subtask-title { font-size: 12px; color: var(--text-secondary, #64748b); flex: 1; min-width: 0; line-height: 1.45; word-break: break-word; }
.hub-subtask-done-btn {
  width: 40rpx; height: 40rpx; border-radius: 50%;
  background: #d1fae5; display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.hub-subtask-done-text { font-size: 14px; color: #065f46; }

/* risk */
.hub-risk-pill { border-radius: 999rpx; padding: 4rpx 14rpx; }
.risk-high { background: #fee2e2; } .risk-high .hub-risk-pill-text { color: #991b1b; }
.risk-medium { background: #fef3c7; } .risk-medium .hub-risk-pill-text { color: #92400e; }
.risk-low { background: #d1fae5; } .risk-low .hub-risk-pill-text { color: #065f46; }
.hub-risk-pill-text { font-size: 11px; font-weight: 600; }
.hub-risk-primary { font-size: 14px; font-weight: 700; color: var(--text-primary, #0f172a); margin-bottom: 6rpx; }
.hub-risk-summary { font-size: 12px; color: var(--text-secondary, #64748b); margin-bottom: 14rpx; }
.hub-risks-list { display: flex; flex-direction: column; gap: 10rpx; }
.hub-risk-row { display: flex; align-items: center; justify-content: space-between; gap: 12rpx; }
.hub-risk-row-left { display: flex; align-items: center; gap: 10rpx; flex: 1; min-width: 0; }
.hub-risk-dot { width: 10rpx; height: 10rpx; border-radius: 50%; }
.dot-high { background: #ef4444; } .dot-medium { background: #f59e0b; } .dot-low { background: #10b981; }
.hub-risk-row-title { font-size: 12px; color: var(--text-secondary, #64748b); min-width: 0; word-break: break-word; }
.hub-risk-row-right { display: flex; align-items: center; gap: 10rpx; flex-shrink: 0; }
.hub-risk-trend { font-size: 11px; }
.trend-rising { color: #ef4444; } .trend-stable { color: var(--text-secondary, #64748b); } .trend-decreasing { color: #10b981; }
.hub-risk-score { font-size: 11px; color: var(--text-tertiary, #8e8e93); }

/* plan */
.hub-plan-health { font-size: 12px; font-weight: 600; }
.health-on_track { color: #10b981; } .health-needs_refresh { color: #f59e0b; } .health-missing { color: #ef4444; }
.hub-plan-role { font-size: 14px; font-weight: 700; color: var(--text-primary, #0f172a); margin-bottom: 6rpx; }
.hub-plan-milestone { font-size: 12px; color: var(--text-secondary, #64748b); margin-bottom: 10rpx; }
.hub-focus-list { display: flex; flex-direction: column; gap: 6rpx; margin-bottom: 12rpx; }
.hub-focus-item { font-size: 12px; color: var(--text-secondary, #64748b); }
.hub-plan-btn {
  background: linear-gradient(135deg, #1d4ed8, #6366f1);
  border-radius: 999rpx; padding: 14rpx 0; text-align: center; margin-top: 8rpx;
}
.hub-plan-btn-text { font-size: 13px; font-weight: 700; color: #fff; }

/* weekly review */
.hub-review-stats, .hub-state-stats {
  display: flex; gap: 8rpx; margin-bottom: 14rpx;
}
.hub-stat { flex: 1; min-width: 0; text-align: center; }
.hub-stat-val { font-size: 16px; font-weight: 700; color: var(--text-primary, #0f172a); display: block; line-height: 1.25; word-break: break-word; }
.hub-stat-label { font-size: 11px; color: var(--text-tertiary, #8e8e93); display: block; margin-top: 4rpx; line-height: 1.35; word-break: break-word; }
.hub-review-highlights { display: flex; flex-direction: column; gap: 6rpx; }
.hub-review-highlight { font-size: 12px; color: var(--text-secondary, #64748b); }

/* events timeline */
.hub-event-row {
  display: flex; align-items: center; gap: 16rpx;
  padding: 12rpx 0; border-bottom: 1rpx solid #f3f4f6;
}
.hub-event-row:last-child { border-bottom: none; }
.hub-event-icon-wrap {
  width: 48rpx; height: 48rpx; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.ev-green  { background: #d1fae5; }
.ev-gray   { background: var(--surface-3, #f1f5f9); }
.ev-orange { background: #fef3c7; }
.ev-blue   { background: #dbeafe; }
.ev-purple { background: #ede9fe; }
.hub-event-icon { font-size: 14px; }
.hub-event-body { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2rpx; }
.hub-event-label { font-size: 12.5px; color: var(--text-secondary, #64748b); font-weight: 600; }
.hub-event-time { font-size: 11px; color: var(--text-tertiary, #8e8e93); }

/* Dark mode: this hub has many custom local surfaces, so keep the
   contrast rules page-scoped instead of relying only on generic cards. */
.hub-page.is-dark {
  background: #09111f;
}

.hub-page.is-dark .hub-scroll {
  background: #09111f;
}

.hub-page.is-dark .hub-refresh {
  background: rgba(59, 130, 246, 0.22);
  border: 1px solid rgba(147, 197, 253, 0.46);
}

.hub-page.is-dark .hub-refresh-icon {
  color: #dbeafe;
}

.hub-page.is-dark .hub-section {
  background: #172235;
  border: 1px solid #315173;
  box-shadow: none;
}

.hub-page.is-dark .hub-section-title,
.hub-page.is-dark .hub-task-count,
.hub-page.is-dark .hub-review-date {
  color: #e2e8f0;
}

.hub-page.is-dark .hub-section-arrow,
.hub-page.is-dark .hub-progress-text,
.hub-page.is-dark .hub-stat-label,
.hub-page.is-dark .hub-review-date,
.hub-page.is-dark .hub-empty-text,
.hub-page.is-dark .hub-subtask-loading-text {
  color: #94a3b8;
}

.hub-page.is-dark .hub-pct-bar,
.hub-page.is-dark .hub-progress-bar {
  background: #26364d;
  border: 1px solid rgba(148, 163, 184, 0.22);
}

.hub-page.is-dark .hub-pct-fill,
.hub-page.is-dark .hub-progress-fill {
  box-shadow: 0 0 12rpx rgba(34, 211, 238, 0.28);
}

.hub-page.is-dark .hub-stage-pill {
  background: rgba(37, 99, 235, 0.22) !important;
  border-color: rgba(147, 197, 253, 0.36) !important;
}

/* 主要操作：朱红行动色 + 纯白字，对比清晰 */
.hub-page.is-dark .hub-action-primary {
  background: #d85a42 !important;
  border-color: #d85a42 !important;
}
.hub-page.is-dark .hub-action-primary .hub-action-text {
  color: #ffffff !important;
}

/* 完成按钮：朱红底 + 白字 */
.hub-page.is-dark .hub-task-done {
  background: #d85a42 !important;
  border-color: #d85a42 !important;
}
.hub-page.is-dark .hub-task-done .hub-task-btn-text {
  color: #ffffff !important;
}

/* stage pill */
.hub-page.is-dark .hub-stage-pill {
  background: rgba(37, 99, 235, 0.22) !important;
  border-color: rgba(147, 197, 253, 0.36) !important;
}
.hub-page.is-dark .hub-stage-text {
  color: #bfdbfe;
}

/* 次要按钮：中灰底 + 亮白字 */
.hub-page.is-dark .hub-action,
.hub-page.is-dark .hub-task-btn,
.hub-page.is-dark .hub-expand-btn,
.hub-page.is-dark .hub-primary-done {
  background: #243449 !important;
  border-color: #5b7190 !important;
}

.hub-page.is-dark .hub-task,
.hub-page.is-dark .hub-subtask,
.hub-page.is-dark .hub-event-row {
  border-color: #315173;
}

.hub-page.is-dark .hub-subtasks {
  border-left-color: rgba(147, 197, 253, 0.36);
}

.hub-page.is-dark .chip-high,
.hub-page.is-dark .risk-high,
.hub-page.is-dark .diff-hard {
  background: #4a1818 !important;
  border-color: #f87171 !important;
}

.hub-page.is-dark .chip-high .hub-missing-chip-text,
.hub-page.is-dark .risk-high .hub-risk-pill-text,
.hub-page.is-dark .diff-hard .hub-badge-text {
  color: #fecaca !important;
}

.hub-page.is-dark .chip-medium,
.hub-page.is-dark .risk-medium,
.hub-page.is-dark .diff-medium {
  background: #422006 !important;
  border-color: #fbbf24 !important;
}

.hub-page.is-dark .chip-medium .hub-missing-chip-text,
.hub-page.is-dark .risk-medium .hub-risk-pill-text,
.hub-page.is-dark .diff-medium .hub-badge-text {
  color: #fde68a !important;
}

.hub-page.is-dark .chip-low,
.hub-page.is-dark .risk-low,
.hub-page.is-dark .diff-easy {
  background: #063525 !important;
  border-color: #34d399 !important;
}

.hub-page.is-dark .chip-low .hub-missing-chip-text,
.hub-page.is-dark .risk-low .hub-risk-pill-text,
.hub-page.is-dark .diff-easy .hub-badge-text {
  color: #bbf7d0 !important;
}

.hub-page.is-dark .hub-badge-time {
  background: #31235f !important;
  border-color: #a78bfa !important;
}

.hub-page.is-dark .hub-badge-time .hub-badge-text {
  color: #ddd6fe !important;
}

.hub-page.is-dark .hub-subtask-done-btn {
  background: rgba(16, 185, 129, 0.2);
  border: 1px solid rgba(52, 211, 153, 0.36);
}

.hub-page.is-dark .hub-subtask-done-text {
  color: #6ee7b7;
}

.hub-page.is-dark .hub-event-icon-wrap {
  border: 1px solid transparent;
}

.hub-page.is-dark .ev-green {
  background: rgba(16, 185, 129, 0.24);
  border-color: rgba(110, 231, 183, 0.48);
  color: #a7f3d0;
}

.hub-page.is-dark .ev-gray {
  background: #243449;
  border-color: #64748b;
  color: #e2e8f0;
}

.hub-page.is-dark .ev-orange {
  background: rgba(245, 158, 11, 0.24);
  border-color: rgba(253, 230, 138, 0.5);
  color: #fde68a;
}

.hub-page.is-dark .ev-blue {
  background: rgba(37, 99, 235, 0.28);
  border-color: rgba(191, 219, 254, 0.5);
  color: #dbeafe;
}

.hub-page.is-dark .ev-purple {
  background: rgba(139, 92, 246, 0.28);
  border-color: rgba(221, 214, 254, 0.5);
  color: #ede9fe;
}

.hub-page.is-dark .hub-bottom-spacer {
  background: #09111f;
}

/* ── Primary text in dark mode ── */
.hub-page.is-dark .hub-headline,
.hub-page.is-dark .hub-task-title,
.hub-page.is-dark .hub-risk-primary,
.hub-page.is-dark .hub-plan-role,
.hub-page.is-dark .hub-stat-val {
  color: #f1f5f9;
}

/* ── Secondary text in dark mode ── */
.hub-page.is-dark .hub-focus,
.hub-page.is-dark .hub-action-text,
.hub-page.is-dark .hub-event-label,
.hub-page.is-dark .hub-risk-summary,
.hub-page.is-dark .hub-plan-milestone,
.hub-page.is-dark .hub-focus-item,
.hub-page.is-dark .hub-task-desc,
.hub-page.is-dark .hub-pct-label,
.hub-page.is-dark .hub-risk-row-title,
.hub-page.is-dark .hub-subtask-title {
  color: #d5e1f0;
}

/* ── Tertiary text in dark mode ── */
.hub-page.is-dark .hub-event-time,
.hub-page.is-dark .hub-risk-score {
  color: #94a3b8;
}

/* 次要按钮文字：灰底 + 亮白字 */
.hub-page.is-dark .hub-task-btn .hub-task-btn-text,
.hub-page.is-dark .hub-expand-text,
.hub-page.is-dark .hub-action .hub-action-text,
.hub-page.is-dark .hub-primary-done-text {
  color: #f1f5f9 !important;
}

/* ── Event icon colors (inherit from parent ev-* class) ── */
.hub-page.is-dark .hub-event-icon { color: inherit; }

/* Redundant .is-dark selectors are intentional for mp-weixin output: some
   scoped descendant rules can lose to global text and light chip styles. */
.is-dark .hub-missing-chip,
.is-dark .hub-risk-pill,
.is-dark .hub-badge,
.is-dark .hub-stage-pill,
.is-dark .hub-plan-health {
  border-width: 1.5rpx !important;
  border-style: solid !important;
  box-shadow: none !important;
}

.is-dark .chip-high,
.is-dark .risk-high,
.is-dark .diff-hard {
  background: #4a1818 !important;
  border-color: #f87171 !important;
}

.is-dark .chip-high .hub-missing-chip-text,
.is-dark .risk-high .hub-risk-pill-text,
.is-dark .diff-hard .hub-badge-text {
  color: #fecaca !important;
}

.is-dark .chip-medium,
.is-dark .risk-medium,
.is-dark .diff-medium {
  background: #422006 !important;
  border-color: #fbbf24 !important;
}

.is-dark .chip-medium .hub-missing-chip-text,
.is-dark .risk-medium .hub-risk-pill-text,
.is-dark .diff-medium .hub-badge-text {
  color: #fde68a !important;
}

.is-dark .chip-low,
.is-dark .risk-low,
.is-dark .diff-easy {
  background: #063525 !important;
  border-color: #34d399 !important;
}

.is-dark .chip-low .hub-missing-chip-text,
.is-dark .risk-low .hub-risk-pill-text,
.is-dark .diff-easy .hub-badge-text {
  color: #bbf7d0 !important;
}

.is-dark .hub-stage-pill,
.is-dark .hub-plan-health {
  background: #243449 !important;
  border-color: #5b7190 !important;
  border-radius: 999rpx;
  padding: 4rpx 14rpx;
}

.is-dark .hub-stage-text,
.is-dark .hub-plan-health {
  color: #f1f5f9 !important;
}

.is-dark .hub-missing-chip-text,
.is-dark .hub-risk-pill-text,
.is-dark .hub-badge-text {
  font-weight: 700 !important;
}

.is-dark .hub-review-stats,
.is-dark .hub-state-stats {
  flex-direction: column;
  gap: 10rpx;
}

.is-dark .hub-stat {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14rpx;
  text-align: left;
  padding: 10rpx 12rpx;
  border-radius: 12rpx;
  background: #111827;
  border: 1rpx solid #315173;
}

.is-dark .hub-stat-val {
  flex: 1;
  min-width: 0;
  color: #f8fafc !important;
  font-size: 14px;
  overflow-wrap: anywhere;
}

.is-dark .hub-stat-label {
  flex-shrink: 0;
  max-width: 160rpx;
  margin-top: 0;
  color: #cbd5e1 !important;
  text-align: right;
}

</style>
