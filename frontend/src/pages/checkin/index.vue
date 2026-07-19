<template>
  <SlPage class="app-soft-bg" :custom-class="[themeClass, fontClass].join(' ')">
    <SlNavBar :title="t('checkin.navTitle')" show-back @back="goBack" :safe-top="topSafeHeight" />

    <view class="checkin-content">
      <view v-if="loading" class="checkin-state app-card-soft">
        <text class="checkin-state-icon ri-loader-4-line checkin-spinning"></text>
        <text class="checkin-state-title">{{ t('checkin.loading') }}</text>
      </view>
      <view v-else-if="loadError" class="checkin-state app-card-soft">
        <text class="checkin-state-icon ri-wifi-off-line"></text>
        <text class="checkin-state-title">{{ loadError }}</text>
        <view class="checkin-retry" @click="retryLoad"><text>{{ t('checkin.retry') }}</text></view>
      </view>
      <template v-else>
      <view class="hero-card">
        <view class="hero-row">
          <view class="hero-streak">
            <text class="streak-num">{{ status?.streakDays || 0 }}</text>
            <text class="streak-label">{{ t('checkin.dayStreakLabel') }}</text>
          </view>
          <view class="hero-progress">
            <text class="hero-progress-text">{{ status?.todayCompleted || 0 }}/{{ status?.todayTotal || 3 }}</text>
            <text class="hero-progress-label">{{ t('checkin.tasksDoneToday') }}</text>
          </view>
        </view>
        <view class="hero-bar">
          <view class="hero-bar-fill" :style="{ width: progressPercent + '%' }"></view>
        </view>
        <text class="hero-tip">{{ heroTip }}</text>
      </view>

      <view class="actions-card app-card-soft app-surface">
        <text class="actions-title">{{ t('checkin.todayTasksTitle') }}</text>
        <view class="action-list">
          <view
            v-for="a in actionItems"
            :key="a.code"
            :class="['action-row', 'ui-list-item', a.done ? 'action-done' : '']"
            @click="navTo(a.target)"
          >
            <text class="action-icon" :class="[a.tone, a.icon]"></text>
            <view class="action-body">
              <text class="action-name">{{ a.label }}</text>
              <text class="action-desc">{{ a.done ? t('checkin.completedToday') : a.cta }}</text>
            </view>
            <text class="action-arrow">›</text>
          </view>
        </view>
      </view>

      <view class="week-card app-card-soft app-surface">
        <text class="week-title">{{ t('checkin.last7Title') }}</text>
        <view class="week-grid">
          <view
            v-for="(d, idx) in last7Days"
            :key="idx"
            :class="['week-cell', d.active ? 'week-cell-active' : '', d.isToday ? 'week-cell-today' : '']"
          >
            <text class="week-cell-dow">{{ d.dow }}</text>
            <text class="week-cell-day">{{ d.dayLabel }}</text>
            <text class="week-cell-icon ri-check-line" v-if="d.active"></text>
          </view>
        </view>
        <text class="week-meta">{{ t('checkin.weekMeta', { n: status?.weeklyDays || 0 }) }}</text>
        <view class="badge-row" v-if="status?.badgeEarnedThisWeek">
          <text class="badge-icon ri-trophy-line"></text>
          <text class="badge-text">{{ t('checkin.weeklyBadge') }}</text>
        </view>
      </view>

      <view class="bottom-safe"></view>
      </template>
    </view>
  </SlPage>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useI18n } from '@/locales';
import { onShow } from '@dcloudio/uni-app';
import { getMpSafeAreaMetrics } from '@/utils/safeArea';
import { getCheckInStatusApi, getCheckInCalendarApi, type CheckInStatus, type CheckInDay } from '@/api/checkin';
import { useTheme } from '@/utils/theme';
import { isRealUser, requireAuth } from '@/utils/auth';
import SlPage from '@/style-library/components/SlPage.vue';
import SlNavBar from '@/style-library/components/SlNavBar.vue';

const { t } = useI18n();
const { themeClass, fontClass, refresh: refreshTheme } = useTheme();
const topSafeHeight = ref(52);

const status = ref<CheckInStatus | null>(null);
const calendar = ref<CheckInDay[]>([]);
const loading = ref(true);
const loadError = ref('');

const goBack = () => uni.navigateBack({ delta: 1 });

const heroTip = computed(() => {
  if (!status.value) return t('checkin.heroLoading');
  if (status.value.todayCompleted >= status.value.todayTotal) return t('checkin.heroAllDone');
  if (status.value.todayCompleted > 0) return t('checkin.heroAlmostDone');
  return t('checkin.heroStartStreak');
});

const progressPercent = computed(() => {
  if (!status.value || status.value.todayTotal === 0) return 0;
  return Math.round((status.value.todayCompleted / status.value.todayTotal) * 100);
});

interface ActionItem {
  code: string;
  label: string;
  cta: string;
  icon: string;
  tone: string;
  target: string;
  done: boolean;
}

const actionItems = computed<ActionItem[]>(() => {
  const done = new Set(status.value?.completedActionsToday || []);
  return [
    {
      code: 'ASSESSMENT',
      label: t('checkin.actionAssessmentLabel'),
      cta: t('checkin.actionAssessmentCta'),
      icon: 'ri-brain-line',
      tone: 'tone-blue',
      target: '/pages/assessment/index',
      done: done.has('ASSESSMENT'),
    },
    {
      code: 'INTERVIEW',
      label: t('checkin.actionInterviewLabel'),
      cta: t('checkin.actionInterviewCta'),
      icon: 'ri-mic-2-line',
      tone: 'tone-orange',
      target: '/pages/interview/start',
      done: done.has('INTERVIEW'),
    },
    {
      code: 'SKILL_NODE',
      label: t('checkin.actionSkillLabel'),
      cta: t('checkin.actionSkillCta'),
      icon: 'ri-map-2-line',
      tone: 'tone-violet',
      target: '/pages/map/index',
      done: done.has('SKILL_NODE'),
    },
  ];
});

interface DayCell {
  date: string;
  dow: string;
  dayLabel: string;
  active: boolean;
  isToday: boolean;
}

const last7Days = computed<DayCell[]>(() => {
  const days: DayCell[] = [];
  const dowNames = [t('checkin.dow.sun'), t('checkin.dow.mon'), t('checkin.dow.tue'), t('checkin.dow.wed'), t('checkin.dow.thu'), t('checkin.dow.fri'), t('checkin.dow.sat')];
  const today = new Date();
  const activeSet = new Set(calendar.value.map((c) => c.day));
  for (let i = 6; i >= 0; i--) {
    const d = new Date(today);
    d.setDate(d.getDate() - i);
    const iso = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
    days.push({
      date: iso,
      dow: dowNames[d.getDay()],
      dayLabel: String(d.getDate()),
      active: activeSet.has(iso),
      isToday: i === 0,
    });
  }
  return days;
});

const navTo = (url: string) => {
  uni.navigateTo({ url });
};

const load = async () => {
  if (!isRealUser()) {
    loading.value = false;
    loadError.value = t('checkin.loginRequired');
    return;
  }
  loading.value = true;
  loadError.value = '';
  try {
    const [s, c] = await Promise.all([getCheckInStatusApi(), getCheckInCalendarApi()]);
    status.value = s;
    calendar.value = c || [];
  } catch (e: any) {
    loadError.value = e?.message || t('checkin.loadFailed');
  } finally {
    loading.value = false;
  }
};

const retryLoad = () => {
  if (!isRealUser()) {
    requireAuth({ message: t('checkin.loginRequired') });
    return;
  }
  load();
};

onMounted(() => {
  refreshTheme();
  topSafeHeight.value = getMpSafeAreaMetrics().topSafeHeight;
});

onShow(() => {
  refreshTheme();
  load();
});
</script>

<style scoped>
.checkin-content {
  padding: 8px var(--page-gutter, 20px) 24px;
  box-sizing: border-box;
}

.checkin-state {
  min-height: 280px;
  margin-top: 12px;
  padding: 32px 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
}
.checkin-state-icon { font-size: 40px; color: #ac6448; }
.checkin-state-title {
  margin-top: 12px;
  color: #2c2b29;
  font-family: "Songti SC", STSong, serif;
  font-size: 16px;
  font-weight: 600;
}
.checkin-retry {
  min-width: 120px;
  min-height: 44px;
  margin-top: 18px;
  padding: 0 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: #c23b22;
  border-radius: 6px;
  font-weight: 600;
}
.checkin-spinning { animation: checkin-spin .8s linear infinite; }
@keyframes checkin-spin { to { transform: rotate(360deg); } }

.hero-card {
  background: linear-gradient(135deg, #cd6a43 0%, #c25c33 100%);
  border-radius: var(--radius-lg, 20px);
  padding: 22px var(--space-xl, 20px);
  color: #fff;
  margin-top: var(--space-sm, 8px);
  box-shadow: var(--shadow-card);
}
.hero-row { display: flex; align-items: flex-end; justify-content: space-between; margin-bottom: 14px; }
.hero-streak { display: flex; align-items: baseline; gap: 6px; }
.streak-num { font-size: 44px; font-weight: 800; letter-spacing: -1px; line-height: 1; }
.streak-label { font-size: var(--font-caption, 13px); opacity: 0.85; }
.hero-progress { text-align: right; display: flex; flex-direction: column; gap: 2px; }
.hero-progress-text { font-size: var(--font-title, 18px); font-weight: 700; }
.hero-progress-label { font-size: 11px; opacity: 0.85; }
.hero-bar { width: 100%; height: 8px; background: rgba(255, 255, 255, 0.25); border-radius: 999px; overflow: hidden; }
.hero-bar-fill { height: 100%; background: var(--surface-2, #fafaf9); border-radius: 999px; transition: width 0.3s; }
.hero-tip { display: block; margin-top: 14px; font-size: var(--font-caption, 13px); opacity: 0.92; line-height: var(--line-height-body, 1.5); }

.actions-card { margin-top: var(--space-xl, 20px); border-radius: var(--radius-lg, 20px); padding: 18px; }
.actions-title { font-size: var(--font-caption, 13px); font-weight: 700; color: var(--text-secondary, #78716c); text-transform: uppercase; letter-spacing: 0.06em; display: block; margin-bottom: 14px; }
.action-list { display: flex; flex-direction: column; gap: 10px; }
.action-row {
  display: flex; align-items: center; gap: 14px;
  background: var(--surface-2, #fafaf9); border-color: var(--border-color, #d8c1b8);
  border-radius: var(--btn-radius, 14px); padding: 12px 14px;
  transition: transform 0.15s;
}
.action-row:active { transform: scale(0.99); }
.action-done { background: var(--success-soft, #f5efe3); border-color: #dbc59a; }
.action-icon {
  width: 40px; height: 40px; border-radius: var(--radius-sm, 12px);
  display: flex; align-items: center; justify-content: center;
  font-size: 18px;
}
.tone-blue { background: linear-gradient(135deg, var(--primary-soft, #fcf5f2), #f1d6cc); }
.tone-orange { background: linear-gradient(135deg, var(--accent-soft, #fff7ed), #fed7aa); }
.tone-violet { background: linear-gradient(135deg, #faf1ed, #efcfc3); }
.action-body { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.action-name { font-size: 14px; font-weight: 700; color: var(--text-primary, #1c1917); }
.action-desc { font-size: 12px; color: var(--text-secondary, #78716c); }
.action-done .action-desc { color: var(--success-color, #735a28); font-weight: 600; }
.action-arrow { font-size: 18px; color: var(--text-tertiary, #8e8e93); line-height: 1; }

.week-card { margin-top: var(--space-xl, 20px); border-radius: var(--radius-lg, 20px); padding: 18px; }
.week-title { font-size: var(--font-caption, 13px); font-weight: 700; color: var(--text-secondary, #78716c); text-transform: uppercase; letter-spacing: 0.06em; display: block; margin-bottom: 14px; }
.week-grid { display: flex; gap: var(--space-sm, 8px); }
.week-cell {
  flex: 1; aspect-ratio: 1 / 1.1;
  background: var(--surface-3, #f5f5f4); border-radius: var(--radius-sm, 12px);
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 2px; position: relative;
}
.week-cell-active { background: var(--primary-color, #cd6a43); box-shadow: 0 4px 10px rgba(205, 106, 67, 0.2); }
.week-cell-today { border: 2px solid var(--primary-color, #cd6a43); }
.week-cell-active.week-cell-today { border-color: rgba(255, 255, 255, 0.3); }

.week-cell-dow { font-size: 11px; color: var(--text-tertiary, #8e8e93); font-weight: 600; }
.week-cell-day { font-size: 14px; color: var(--text-secondary, #78716c); font-weight: 700; }
.week-cell-today:not(.week-cell-active) .week-cell-day { color: var(--text-primary, #1c1917); }

.week-cell-active .week-cell-dow { color: rgba(255, 255, 255, 0.85); }
.week-cell-active .week-cell-day { color: #ffffff; }
.week-cell-icon { font-size: 14px; color: #ffffff; position: absolute; bottom: 3px; font-weight: 800; }
.week-meta { display: block; margin-top: var(--space-md, 12px); font-size: 12px; color: var(--text-secondary, #78716c); }
.badge-row { display: flex; align-items: center; gap: var(--space-sm, 8px); margin-top: var(--space-md, 12px); padding: 10px 12px; background: #fef3c7; border-radius: 10px; }
.badge-icon { font-size: 18px; }
.badge-text { font-size: 12.5px; color: #92400e; font-weight: 600; line-height: 1.4; }

.bottom-safe { height: calc(env(safe-area-inset-bottom, 0px) + 24px); }

/* Dark mode */
.is-dark .actions-card,
.is-dark .week-card { background: #292524; box-shadow: none; border-color: #44403c; }
.is-dark .actions-title,
.is-dark .week-title,
.is-dark .week-meta { color: #a8a29e; }
.is-dark .action-row { background: #1c1917; border-color: #44403c; }
.is-dark .action-name { color: #fafaf9; }
.is-dark .action-desc { color: #a8a29e; }
.is-dark .action-done { background: rgba(149, 117, 52, 0.16); border-color: #bf9748; }
.is-dark .action-done .action-desc { color: #d3b882; }
.is-dark .hero-bar { background: rgba(255, 255, 255, 0.22); }
.is-dark .hero-bar-fill { background: linear-gradient(90deg, #d6bd8a, #e5d5b5); }
.is-dark .tone-blue {
  background: rgba(205, 106, 67, 0.22);
  color: #e8baa8;
  border: 1px solid rgba(232, 186, 168, 0.32);
}
.is-dark .tone-orange {
  background: rgba(245, 158, 11, 0.18);
  color: #fbbf24;
  border: 1px solid rgba(251, 191, 36, 0.32);
}
.is-dark .tone-violet {
  background: rgba(219, 147, 119, 0.2);
  color: #efcfc3;
  border: 1px solid rgba(239, 207, 195, 0.32);
}
.is-dark .week-cell { background: #1c1917; }
.is-dark .week-cell-day { color: #78716c; }
.is-dark .week-cell-today:not(.week-cell-active) .week-cell-day { color: #fafaf9; }
.is-dark .week-cell-dow { color: #57534e; }
.is-dark .week-cell-active { background: var(--primary-color, #cd6a43); box-shadow: none; }
.is-dark .week-cell-active .week-cell-day,
.is-dark .week-cell-active .week-cell-dow { color: #ffffff; }

/* Competition visual system ------------------------------------------------ */
.hero-card {
  color: #2c2b29;
  background: #f5f5f0;
  border: 1px solid #e0dfdb;
  border-top: 1px solid var(--border-color, #e0dfdb);
  border-radius: 6px;
  box-shadow: 0 8px 24px rgba(44, 43, 41, 0.05);
}

.streak-num {
  color: #c23b22;
  font-family: "Songti SC", STSong, serif;
  font-weight: 600;
}

.streak-label,
.hero-progress-label,
.hero-tip {
  color: #5a5956;
  opacity: 1;
}

.hero-progress-text {
  color: #2c2b29;
  font-family: "Songti SC", STSong, serif;
  font-weight: 600;
}

.hero-bar {
  background: #e0dfdb;
  border-radius: 2px;
}

.hero-bar-fill {
  background: #ac6448;
  border-radius: 2px;
}

.actions-card,
.week-card {
  background: #ffffff;
  border: 1px solid #e0dfdb;
  border-radius: 8px;
  box-shadow: 0 6px 18px rgba(44, 43, 41, 0.04);
}

.actions-title,
.week-title,
.action-name {
  color: #2c2b29;
  font-family: "Songti SC", STSong, serif;
  font-weight: 600;
  letter-spacing: 0.05em;
}

.action-row {
  background: #faf9f6;
  border: 1px solid #e0dfdb;
  border-radius: 6px;
}

.action-done {
  background: rgba(148, 127, 84, 0.08);
  border-color: rgba(148, 127, 84, 0.42);
}

.action-icon {
  border-radius: 4px;
}

.tone-blue {
  color: #ac6448;
  background: rgba(172, 100, 72, 0.09);
}

.tone-orange {
  color: #b8975a;
  background: rgba(184, 151, 90, 0.11);
}

.tone-violet {
  color: #c23b22;
  background: rgba(194, 59, 34, 0.08);
}

.action-desc,
.week-meta {
  color: #5a5956;
}

.action-done .action-desc {
  color: #947f54;
}

.week-cell {
  background: #f5f5f0;
  border: 1px solid #e0dfdb;
  border-radius: 4px;
}

.week-cell-active {
  background: #ac6448;
  border-color: #ac6448;
  box-shadow: none;
}

.week-cell-today {
  border-color: #c23b22;
}

.badge-row {
  color: #80652f;
  background: rgba(184, 151, 90, 0.12);
  border: 1px solid rgba(184, 151, 90, 0.32);
  border-radius: 4px;
}

.badge-text {
  color: #80652f;
}

.is-dark .hero-card,
.is-dark .actions-card,
.is-dark .week-card {
  color: #faf9f6;
  background: #242320;
  border-color: #494844;
}

.is-dark .hero-progress-text,
.is-dark .actions-title,
.is-dark .week-title,
.is-dark .action-name {
  color: #faf9f6;
}

.is-dark .streak-label,
.is-dark .hero-progress-label,
.is-dark .hero-tip,
.is-dark .action-desc,
.is-dark .week-meta {
  color: #c6c4be;
}

.is-dark .action-row,
.is-dark .week-cell {
  background: #1c1b19;
  border-color: #494844;
}
</style>
