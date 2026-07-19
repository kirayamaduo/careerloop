<template>
  <SlPage class="app-soft-bg" :custom-class="[themeClass, fontClass].join(' ')">
    <SlNavBar show-back @back="goBack" :safe-top="topSafeHeight">
      <template #title>
        <view class="nav-title-wrap">
          <text class="nav-title">{{ t('interviewHistory.navTitle') }}</text>
          <view class="nav-icon-btn" @click.stop="startNew">
            <text class="ri-add-line"></text>
          </view>
        </view>
      </template>
    </SlNavBar>

    <view class="history-page">
      <view class="page-hero">
        <text class="hero-title">{{ t('interviewHistory.heroTitle') }}</text>
        <text class="hero-subtitle">{{ t('interviewHistory.heroSubtitle') }}</text>
      </view>

      <!-- Loading skeleton -->
      <view class="skeleton-list" v-if="loading">
        <view class="skel-card app-card-soft app-surface" v-for="i in 3" :key="i">
          <view class="skel-line skel-w60"></view>
          <view class="skel-line skel-w40"></view>
          <view class="skel-line skel-w80"></view>
        </view>
      </view>

      <view class="list" v-else-if="groupedInterviews.length > 0">
        <template v-for="group in groupedInterviews" :key="group.label">
          <text class="group-label">{{ group.label }}</text>
          <view
            v-for="item in group.items"
            :key="item.interviewId"
            class="swipe-row"
          >
            <view
              class="interview-card app-card-soft app-surface"
              :class="{ 'interview-card-swiped': item.interviewId && (swipeOffsets[item.interviewId] ?? 0) < 0 }"
              :style="{ transform: `translateX(${item.interviewId ? (swipeOffsets[item.interviewId] ?? 0) : 0}px)` }"
              @click="viewDetail(item)"
              @touchstart="item.interviewId && onItemTouchStart($event, item.interviewId)"
              @touchmove="item.interviewId && onItemTouchMove($event, item.interviewId)"
              @touchend="item.interviewId && onItemTouchEnd($event, item.interviewId)"
            >
              <view class="card-top">
                <text class="position">{{ item.positionName }}</text>
                <view :class="['status-pill', (item.status ?? '').toLowerCase()]">
                  <text class="pill-text">{{ statusLabel(item.status) }}</text>
                </view>
              </view>
              <view class="card-bottom">
                <view class="info-item">
                  <text class="info-label">{{ t('interviewHistory.difficultyLabel') }}</text>
                  <text class="info-val">{{ difficultyLabel(item.difficulty) }}</text>
                </view>
                <view class="info-item" v-if="item.finalScore != null">
                  <text class="info-label">{{ t('interviewHistory.scoreLabel') }}</text>
                  <text class="info-val score-val">{{ item.finalScore }}</text>
                </view>
                <view class="info-item info-item-time">
                  <text class="info-label">{{ t('interviewHistory.timeLabel') }}</text>
                  <text class="info-val">{{ formatTime(item.startedAt) }}</text>
                </view>
              </view>
            </view>
            <view
              class="swipe-delete-btn"
              :class="{ 'swipe-delete-visible': item.interviewId && (swipeOffsets[item.interviewId] ?? 0) < 0 }"
              @click.stop="deleteInterview(item)"
            >
              <text class="swipe-delete-text">删除</text>
            </view>
          </view>
        </template>
      </view>

      <view class="empty app-empty app-surface" v-else>
        <text class="empty-icon ri-briefcase-line"></text>
        <text class="empty-text">{{ t('interviewHistory.emptyText') }}</text>
        <text class="empty-desc">{{ t('interviewHistory.emptyDesc') }}</text>
        <button class="btn-primary" @click="startNew">{{ t('interviewHistory.emptyBtn') }}</button>
      </view>
    </view>
  </SlPage>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { getMpSafeAreaMetrics } from '@/utils/safeArea';
import { deleteInterviewApi, getUserInterviewsApi, type Interview } from '@/api/interview';
import { useI18n } from '@/locales';
import { useTheme } from '@/utils/theme';
import SlPage from '@/style-library/components/SlPage.vue';
import SlNavBar from '@/style-library/components/SlNavBar.vue';

const interviews = ref<Interview[]>([]);
const loading = ref(true);
const { t } = useI18n();
const { themeClass, fontClass, refresh: refreshTheme } = useTheme();
const topSafeHeight = ref(52);
const swipeOffsets = ref<Record<number, number>>({});
const activeSwipeId = ref<number | null>(null);
let touchStartX = 0;
let touchStartY = 0;
let touchBaseOffset = 0;
let dirLocked = false;
let isHorizontal = false;
const DELETE_BTN_W = 80;

const goBack = () => uni.navigateBack({ delta: 1 });

const loadInterviews = async () => {
  const userId = uni.getStorageSync('userId');
  const numericId = Number(userId);
  if (!userId || isNaN(numericId) || numericId <= 0) {
    loading.value = false;
    return;
  }
  loading.value = true;
  try {
    interviews.value = await getUserInterviewsApi(numericId);
  } catch (error) {
    console.error('Failed to load interviews:', error);
    uni.showToast({ title: t('interviewHistory.loadFailed'), icon: 'none' });
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  refreshTheme();
  topSafeHeight.value = getMpSafeAreaMetrics().topSafeHeight;
});

// Refresh every time the page becomes visible — in particular when the
// user finishes an interview and pops back to history, the new entry
// should appear without a manual reload.
onShow(() => {
  refreshTheme();
  loadInterviews();
});

const startNew = () => {
  uni.navigateTo({ url: '/pages/interview/start' });
};

/**
 * Tapping a card always leads somewhere useful:
 *   ONGOING   → resume the same modality the candidate originally chose
 *               (VOICE → room.vue, TEXT/unknown → chat.vue) so we never
 *               silently downgrade a digital-human session into a text chat.
 *   COMPLETED → open the AI evaluation report
 *   CANCELLED → toast
 */
const viewDetail = (item: Interview) => {
  if (item.status === 'ONGOING') {
    const isVoice = (item.mode || '').toUpperCase() === 'VOICE';
    const target = isVoice
      ? `/pages/interview/room?interviewId=${item.interviewId}`
      : `/pages/interview/chat?interviewId=${item.interviewId}`;
    uni.navigateTo({ url: target });
  } else if (item.status === 'COMPLETED') {
    uni.navigateTo({ url: `/pages/interview/report?interviewId=${item.interviewId}` });
  } else {
    uni.showToast({ title: t('interviewHistory.cancelled'), icon: 'none' });
  }
};

const formatTime = (dateStr?: string) => {
  if (!dateStr) return '';
  const date = new Date(dateStr.replace(' ', 'T'));
  return `${date.getHours()}:${String(date.getMinutes()).padStart(2, '0')}`;
};

const difficultyLabel = (difficulty?: string) => {
  const key = (difficulty || '').toLowerCase();
  if (key === 'easy') return '简单';
  if (key === 'hard') return '挑战';
  if (key === 'normal' || key === 'medium') return '标准';
  return difficulty || '标准';
};

const statusLabel = (status?: string) => {
  if (status === 'COMPLETED') return t('interviewHistory.statusCompleted');
  if (status === 'CANCELLED') return t('interviewHistory.statusCancelled');
  return t('interviewHistory.statusOngoing');
};

const onItemTouchStart = (e: any, id: number) => {
  touchStartX = e.touches[0].clientX;
  touchStartY = e.touches[0].clientY;
  touchBaseOffset = swipeOffsets.value[id] ?? 0;
  dirLocked = false;
  isHorizontal = false;
  if (activeSwipeId.value !== null && activeSwipeId.value !== id) {
    swipeOffsets.value[activeSwipeId.value] = 0;
    activeSwipeId.value = null;
  }
};

const onItemTouchMove = (e: any, id: number) => {
  const dx = e.touches[0].clientX - touchStartX;
  const dy = e.touches[0].clientY - touchStartY;
  if (!dirLocked && (Math.abs(dx) > 4 || Math.abs(dy) > 4)) {
    dirLocked = true;
    isHorizontal = Math.abs(dx) > Math.abs(dy);
  }
  if (!isHorizontal) return;
  swipeOffsets.value[id] = Math.max(-DELETE_BTN_W, Math.min(0, touchBaseOffset + dx));
};

const onItemTouchEnd = (_e: any, id: number) => {
  if (!isHorizontal) return;
  const offset = swipeOffsets.value[id] ?? 0;
  if (offset < -DELETE_BTN_W / 2) {
    swipeOffsets.value[id] = -DELETE_BTN_W;
    activeSwipeId.value = id;
  } else {
    swipeOffsets.value[id] = 0;
    activeSwipeId.value = null;
  }
};

const deleteInterview = async (item: Interview) => {
  if (!item.interviewId) return;
  const backup = [...interviews.value];
  interviews.value = interviews.value.filter((it) => it.interviewId !== item.interviewId);
  delete swipeOffsets.value[item.interviewId];
  activeSwipeId.value = null;
  try {
    await deleteInterviewApi(item.interviewId);
    uni.showToast({ title: '已删除', icon: 'success' });
  } catch (e: any) {
    interviews.value = backup;
    uni.showToast({ title: e?.message || '删除失败，请稍后重试', icon: 'none' });
  }
};

const startOfDay = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();

const groupedInterviews = computed(() => {
  if (!interviews.value.length) return [] as Array<{ label: string; items: Interview[] }>;
  const today = startOfDay(new Date());
  const yesterday = today - 86400000;
  const sevenAgo = today - 6 * 86400000;

  const buckets: Record<string, Interview[]> = { 今日: [], 昨天: [], 本周: [], 更早: [] };
  for (const it of interviews.value) {
    if (!it.startedAt) { buckets['更早'].push(it); continue; }
    const day = startOfDay(new Date(it.startedAt.replace(' ', 'T')));
    if (day === today) buckets['今日'].push(it);
    else if (day === yesterday) buckets['昨天'].push(it);
    else if (day >= sevenAgo) buckets['本周'].push(it);
    else buckets['更早'].push(it);
  }
  return Object.entries(buckets)
    .filter(([, items]) => items.length > 0)
    .map(([label, items]) => ({ label, items }));
});
</script>

<style scoped>
.history-page {
  padding: 0 var(--page-gutter, 20px) 24px;
  box-sizing: border-box;
}

.nav-title-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  pointer-events: auto;
}

.nav-title {
  font-size: var(--font-section, 17px);
  font-weight: 700;
  color: var(--text-primary, #0f172a);
  letter-spacing: -0.3px;
}

.nav-icon-btn {
  font-size: 16px;
  color: var(--primary-color, #2563eb);
  background: var(--primary-soft, #eff6ff);
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 13px;
}

.is-dark .nav-icon-btn {
  background: #1e293b;
  color: #93c5fd;
}
.is-dark .nav-title { color: #f8fafc; }

/* Section labels between date buckets */
.group-label {
  display: block;
  margin: 6px 4px 4px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: var(--text-tertiary, #8e8e93);
  text-transform: uppercase;
}

/* Skeleton loader */
.skeleton-list { display: flex; flex-direction: column; gap: 12px; }
.skel-card {
  display: flex; flex-direction: column; gap: 10px;
}
.skel-line {
  height: 12px;
  border-radius: 6px;
  background: linear-gradient(90deg, #eef2f7 0%, #f7fafc 50%, #eef2f7 100%);
  background-size: 200% 100%;
  animation: skel-shimmer 1.4s infinite;
}
.skel-w40 { width: 40%; }
.skel-w60 { width: 60%; }
.skel-w80 { width: 80%; }
@keyframes skel-shimmer {
  0%   { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.page-hero { margin-bottom: 20px; padding: 0 2px; }

.hero-title {
  font-size: var(--font-hero, 28px); font-weight: 800;
  color: var(--text-primary, #0f172a); letter-spacing: -0.5px;
  display: block; margin-bottom: 6px;
}

.hero-subtitle {
  display: block; font-size: 14px; line-height: var(--line-height-body, 1.5);
  color: var(--text-secondary, #64748b);
}

.list { display: flex; flex-direction: column; gap: 12px; }

.swipe-row {
  position: relative;
  overflow: hidden;
  border-radius: var(--radius-md, 16px);
}

.interview-card {
  position: relative;
  z-index: 1;
  transition: transform 0.25s cubic-bezier(0.25, 0.8, 0.25, 1);
  will-change: transform;
}

.interview-card:active { transform: scale(0.98); }

.interview-card-swiped:active { transform: none; }

.swipe-delete-btn {
  position: absolute;
  right: 0;
  top: 0;
  bottom: 0;
  width: 80px;
  background: var(--danger-color, #ef4444);
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 0 var(--radius-md, 16px) var(--radius-md, 16px) 0;
  z-index: 0;
  opacity: 0;
  transition: opacity 0.18s ease;
}
.swipe-delete-visible { opacity: 1; }
.swipe-delete-text {
  color: #ffffff;
  font-size: 14px;
  font-weight: 800;
}

.card-top {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 14px;
}

.position { font-size: var(--font-section, 17px); font-weight: 600; color: var(--text-primary, #0f172a); }

.status-pill { padding: 4px 12px; border-radius: 10px; }

.pill-text { font-size: 12px; font-weight: 600; }

.completed { background: var(--success-soft, #dcfce7); }
.completed .pill-text { color: var(--success-color, #059669); }

.ongoing { background: #fef3c7; }
.ongoing .pill-text { color: #d97706; }

.cancelled { background: var(--surface-3, #f1f5f9); }
.cancelled .pill-text { color: var(--text-tertiary, #64748b); }

.card-bottom { display: flex; gap: 20px; }

.info-item { display: flex; flex-direction: column; gap: 2px; }

.info-label { font-size: 11px; color: var(--text-tertiary, #8e8e93); font-weight: 500; text-transform: uppercase; letter-spacing: 0.3px; }

.info-val { font-size: 14px; color: var(--text-secondary, #64748b); font-weight: 500; }

.score-val { color: var(--primary-color, #2563eb); font-weight: 700; }

.empty { text-align: center; padding: 80px 20px; }

.empty-icon { font-size: 56px; display: block; margin-bottom: 16px; }

.empty-text {
  font-size: 16px; color: var(--text-tertiary, #8e8e93); font-weight: 500;
  display: block; margin-bottom: 24px;
}

.empty-desc {
  display: block;
  margin: -14px 0 24px;
  font-size: 13px;
  line-height: 1.45;
  color: var(--text-secondary, #64748b);
}

.btn-primary {
  background: var(--primary-color, #2563eb); color: #fff; font-size: 16px; font-weight: 600;
  border-radius: var(--btn-radius, 14px); height: var(--btn-height-md, 48px); line-height: var(--btn-height-md, 48px); border: none;
}

.btn-primary:active { background: var(--primary-hover, #1d4ed8); }

/* Dark mode */
.is-dark .hero-title { color: #f8fafc; }

.is-dark .hero-subtitle { color: #94a3b8; }

.is-dark .title,
.is-dark .position { color: #f8fafc; }

.is-dark .subtitle,
.is-dark .empty-desc { color: #94a3b8; }

.is-dark .interview-card { background: transparent; box-shadow: none; }

.is-dark .info-val { color: #e2e8f0; }

/* Status pills: light-mode pastels + saturated text; dark mode needs its own pair */
.is-dark .status-pill.completed {
  background: rgba(16, 185, 129, 0.22);
}

.is-dark .status-pill.completed .pill-text {
  color: #34d399;
}

.is-dark .status-pill.ongoing {
  background: rgba(245, 158, 11, 0.22);
}

.is-dark .status-pill.ongoing .pill-text {
  color: #fbbf24;
}

.is-dark .status-pill.cancelled {
  background: #34332f;
}

.is-dark .status-pill.cancelled .pill-text {
  color: #aaa8a2;
}

.is-dark .skel-card {
  background: #1e293b;
  border-color: #334155;
}

.is-dark .skel-line {
  background: linear-gradient(90deg, #1e293b 0%, #334155 50%, #1e293b 100%);
  background-size: 200% 100%;
}

.is-dark .group-label {
  color: #64748b;
}

.is-dark .nav-new-text {
  color: #93c5fd;
}

/* Competition visual system ------------------------------------------------ */
.nav-title,
.hero-title {
  color: #2c2b29;
  font-family: "Songti SC", STSong, serif;
  font-weight: 600;
  letter-spacing: 0.05em;
}

.nav-icon-btn {
  color: #c23b22;
  background: transparent;
  border: 1px solid rgba(194, 59, 34, 0.38);
  border-radius: 4px;
}

.hero-subtitle,
.info-val {
  color: #5a5956;
}

.group-label {
  color: #8b8a86;
  font-family: "Songti SC", STSong, serif;
  font-weight: 600;
  letter-spacing: 0.08em;
}

.swipe-row {
  border-radius: 8px;
}

.interview-card,
.skel-card,
.empty {
  background: #ffffff;
  border: 1px solid #e0dfdb;
  border-radius: 8px;
  box-shadow: 0 6px 18px rgba(44, 43, 41, 0.04);
}

.interview-card {
  padding: 18px;
}

.position {
  color: #2c2b29;
  font-family: "Songti SC", STSong, serif;
  font-weight: 600;
  letter-spacing: 0.03em;
}

.status-pill {
  background: transparent;
  border-radius: 4px;
}

.completed {
  border: 1px solid rgba(107, 142, 90, 0.42);
}

.completed .pill-text {
  color: #6b8e5a;
}

.ongoing {
  border: 1px solid rgba(196, 152, 74, 0.44);
}

.ongoing .pill-text {
  color: #9b722d;
}

.cancelled {
  background: #efeee9;
}

.cancelled .pill-text {
  color: #77756f;
}

.score-val {
  color: #3f51b5;
}

.swipe-delete-btn {
  background: #c23b22;
  border-radius: 0 8px 8px 0;
}

.btn-primary {
  background: #c23b22;
  border-radius: 6px;
}

.btn-primary:active {
  background: #a8311d;
}

.skel-line {
  background: #efeee9;
}

.is-dark .nav-title,
.is-dark .hero-title,
.is-dark .position {
  color: #faf9f6;
}

.is-dark .interview-card,
.is-dark .skel-card,
.is-dark .empty {
  background: #242320;
  border-color: #494844;
}

.is-dark .hero-subtitle,
.is-dark .info-val,
.is-dark .empty-desc {
  color: #c6c4be;
}
</style>
