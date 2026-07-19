<template>
  <view class="msg-page app-soft-bg" :class="[themeClass, fontClass]">

    <view class="status-spacer" :style="{ height: topSafeHeight + 'px' }"></view>

    <view class="page-header" :style="{ paddingRight: rightAvoidWidth + 'px' }">
      <view class="header-row">
        <view class="header-titles">
          <text class="page-title">{{ t('messages.title') }}</text>
          <text class="page-subtitle">{{ t('messages.subtitle') }}</text>
        </view>
        <view
          class="clear-btn-icon"
          v-if="filteredMessages.length > 0 && unreadCount > 0"
          @click="markAllReadHandler"
        ><text class="ri-check-double-line"></text></view>
      </view>
    </view>

    <!-- F9: Category segment tabs -->
    <view class="segment-wrap">
      <view class="segment-bar app-card-soft">
        <view
          v-for="tab in TABS"
          :key="tab.key"
          class="seg-item"
          :class="{ 'seg-active': activeTab === tab.key }"
          @click="activeTab = tab.key as TabKey"
        >
          <text class="seg-text">{{ tab.label }}</text>
          <view class="seg-badge" v-if="unreadByTab[tab.key] > 0">
            <text class="seg-badge-text">{{ unreadByTab[tab.key] }}</text>
          </view>
        </view>
      </view>
    </view>

    <!-- Message list -->
    <scroll-view class="msg-list" scroll-y @scroll="onListScroll">
      <view class="list-wrap">
        <view
          class="swipe-row"
          v-for="(item, idx) in filteredMessages"
          :key="item.notificationId"
        >
          <!-- 消息卡片（可左滑） -->
          <view
            class="msg-card app-card-soft"
            :class="{ 'msg-unread': item.unread, 'msg-card-swiped': (swipeOffsets[item.notificationId] ?? 0) < 0 }"
            :style="{ transform: `translateX(${swipeOffsets[item.notificationId] ?? 0}px)` }"
            @click="handleSystemClick(item)"
            @touchstart="onMsgTouchStart($event, item.notificationId)"
            @touchmove="onMsgTouchMove($event, item.notificationId)"
            @touchend="onMsgTouchEnd($event, item.notificationId)"
          >
            <view class="avatar-wrap">
              <view class="app-icon-tile" :class="'sys-' + (idx % 3)">
                <text :class="['av-icon', item.icon]"></text>
              </view>
              <view class="unread-dot" v-if="item.unread"></view>
            </view>
            <view class="msg-body">
              <view class="msg-top-row">
                <text class="msg-name">{{ item.name }}</text>
                <text class="msg-time">{{ item.time }}</text>
              </view>
              <text class="msg-preview">{{ item.preview }}</text>
            </view>
          </view>
          <!-- 删除按钮（滑动后露出） -->
          <view
            class="swipe-delete-btn"
            :class="{ 'swipe-delete-visible': (swipeOffsets[item.notificationId] ?? 0) < 0 }"
            @click="deleteMessage(item)"
          >
            <text class="swipe-delete-text">{{ t('messages.deleteBtn') }}</text>
          </view>
        </view>
      </view>

      <!-- Empty state -->
      <view class="empty-state app-empty" v-if="!systemLoading && filteredMessages.length === 0">
        <text class="empty-icon ri-notification-off-line"></text>
        <text class="empty-text">{{ t('messages.empty') }}</text>
        <text class="empty-sub">{{ t('messages.emptySub') }}</text>
      </view>

      <!-- Reserve space above tab bar to prevent last row from being covered. -->
      <view class="bottom-safe"></view>
    </scroll-view>

  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useI18n } from '@/locales';
import { onShow } from '@dcloudio/uni-app';
import { getMpSafeAreaMetrics } from '@/utils/safeArea';
import {
  listNotificationsApi,
  markReadApi,
  markAllReadApi,
  deleteNotificationApi,
  type Notification,
} from '@/api/notification';
import { useTheme } from '@/utils/theme';

const { t } = useI18n();
const topSafeHeight = ref(88);
const rightAvoidWidth = ref(20);
const { themeClass, fontClass, refresh: refreshTheme } = useTheme();

const onListScroll = (event: any) => {
  // Logic for tracking scroll if needed
};

// ─── F9: Category tabs ───────────────────────────────────────────────────────
const TABS = computed(() => [
  { key: 'ALL',       label: t('messages.tabAll') },
  { key: 'CAREER',    label: t('messages.tabCareer') },
  { key: 'SYSTEM',    label: t('messages.tabSystem') },
  { key: 'AI',        label: t('messages.tabAI') },
]);

type TabKey = 'ALL' | 'CAREER' | 'SYSTEM' | 'AI';

const TAB_TYPES: Record<TabKey, string[]> = {
  ALL:    [],
  CAREER: ['INTERVIEW_REPORT', 'INTERVIEW_COMPLETED', 'ASSESSMENT_RESULT', 'ASSESSMENT_DONE', 'RESUME_DIAGNOSIS', 'RESUME_REVIEWED', 'WEEKLY_REPORT', 'STREAK_WARNING', 'MARKET_LIKE', 'SCHOOL_INTERVENTION'],
  SYSTEM: ['SYSTEM', 'ADMIN_BROADCAST'],
  AI:     ['AI_PROACTIVE'],
};

const activeTab = ref<TabKey>('ALL');

// ─── Icon & label maps (all 9 types) ────────────────────────────────────────
const iconForType = (type: string): string => {
  switch (type) {
    case 'INTERVIEW_REPORT':  return 'ri-mic-2-line';
    case 'INTERVIEW_COMPLETED': return 'ri-mic-2-line';
    case 'ASSESSMENT_RESULT': return 'ri-brain-line';
    case 'ASSESSMENT_DONE':   return 'ri-brain-line';
    case 'RESUME_DIAGNOSIS':  return 'ri-file-text-line';
    case 'RESUME_REVIEWED':   return 'ri-file-text-line';
    case 'WEEKLY_REPORT':     return 'ri-bar-chart-2-line';
    case 'STREAK_WARNING':    return 'ri-fire-line';
    case 'MARKET_LIKE':       return 'ri-heart-fill';
    case 'AI_PROACTIVE':      return 'ri-robot-2-line';
    case 'ADMIN_BROADCAST':   return 'ri-megaphone-line';
    case 'SCHOOL_INTERVENTION': return 'ri-school-line';
    default:                  return 'ri-notification-3-line';
  }
};
const nameForType = (type: string): string => {
  switch (type) {
    case 'INTERVIEW_REPORT':
    case 'INTERVIEW_COMPLETED': return t('messages.typeInterview');
    case 'ASSESSMENT_RESULT':
    case 'ASSESSMENT_DONE':     return t('messages.typeAssessment');
    case 'RESUME_DIAGNOSIS':
    case 'RESUME_REVIEWED':     return t('messages.typeResumeAI');
    case 'WEEKLY_REPORT':       return t('messages.typeWeeklyReport');
    case 'STREAK_WARNING':      return t('messages.typeCheckin');
    case 'MARKET_LIKE':         return t('messages.typeMarket');
    case 'AI_PROACTIVE':        return t('messages.typeAI');
    case 'ADMIN_BROADCAST':     return t('messages.typeAnnouncement');
    case 'SCHOOL_INTERVENTION': return t('messages.typeSchoolIntervention');
    default:                    return t('messages.typeSystem');
  }
};

// System notifications come from the backend (/api/notifications).
// Each row is a Notification + a derived UI shape used by the existing template.
interface SystemMessageView {
  notificationId: number;
  type: string;
  icon: string;
  name: string;
  time: string;
  preview: string;
  unread: boolean;
  link?: string;
}
const systemMessages = ref<SystemMessageView[]>([]);
const systemLoading = ref(false);
const unreadCount = computed(() => systemMessages.value.filter((m) => m.unread).length);

// ─── 滑动删除状态 ────────────────────────────────────────────────────────────
// 每条消息的当前水平偏移量（px），负值表示向左滑动
const swipeOffsets = ref<Record<number, number>>({});
// 当前处于"展开"状态的消息 id（同时只允许一条展开）
const activeSwipeId = ref<number | null>(null);
let _touchStartX = 0;
let _touchStartY = 0;
let _touchBaseOffset = 0;
let _dirLocked = false;
let _isHorizontal = false;
const DELETE_BTN_W = 80;

const onMsgTouchStart = (e: any, id: number) => {
  _touchStartX = e.touches[0].clientX;
  _touchStartY = e.touches[0].clientY;
  _touchBaseOffset = swipeOffsets.value[id] ?? 0;
  _dirLocked = false;
  _isHorizontal = false;
  // 关闭其他展开的条目
  if (activeSwipeId.value !== null && activeSwipeId.value !== id) {
    swipeOffsets.value[activeSwipeId.value] = 0;
    activeSwipeId.value = null;
  }
};
const onMsgTouchMove = (e: any, id: number) => {
  const dx = e.touches[0].clientX - _touchStartX;
  const dy = e.touches[0].clientY - _touchStartY;
  if (!_dirLocked && (Math.abs(dx) > 4 || Math.abs(dy) > 4)) {
    _dirLocked = true;
    _isHorizontal = Math.abs(dx) > Math.abs(dy);
  }
  if (!_isHorizontal) return;
  swipeOffsets.value[id] = Math.max(-DELETE_BTN_W, Math.min(0, _touchBaseOffset + dx));
};
const onMsgTouchEnd = (_e: any, id: number) => {
  if (!_isHorizontal) return;
  const offset = swipeOffsets.value[id] ?? 0;
  if (offset < -DELETE_BTN_W / 2) {
    swipeOffsets.value[id] = -DELETE_BTN_W;
    activeSwipeId.value = id;
  } else {
    swipeOffsets.value[id] = 0;
    activeSwipeId.value = null;
  }
};
const closeSwipe = (id: number) => {
  swipeOffsets.value[id] = 0;
  if (activeSwipeId.value === id) activeSwipeId.value = null;
};

const deleteMessage = async (item: SystemMessageView) => {
  // 乐观 UI：先从列表移除，失败再恢复
  const backup = [...systemMessages.value];
  systemMessages.value = systemMessages.value.filter((m) => m.notificationId !== item.notificationId);
  delete swipeOffsets.value[item.notificationId];
  activeSwipeId.value = null;
  try {
    await deleteNotificationApi(item.notificationId);
  } catch {
    systemMessages.value = backup;
    uni.showToast({ title: t('messages.deleteFailed'), icon: 'none' });
  }
};

const filteredMessages = computed(() => {
  if (activeTab.value === 'ALL') return systemMessages.value;
  const allowed = TAB_TYPES[activeTab.value];
  return systemMessages.value.filter((m) => allowed.includes(m.type));
});

const unreadByTab = computed<Record<string, number>>(() => {
  const out: Record<string, number> = { ALL: 0, CAREER: 0, SYSTEM: 0, AI: 0 };
  systemMessages.value.forEach((m) => {
    if (!m.unread) return;
    out['ALL']++;
    for (const key of Object.keys(TAB_TYPES) as TabKey[]) {
      if (TAB_TYPES[key].includes(m.type)) { out[key]++; break; }
    }
  });
  return out;
});

const formatRelativeTime = (ts?: string): string => {
  if (!ts) return '';
  const date = new Date(ts.replace(' ', 'T'));
  if (isNaN(date.getTime())) return '';
  const diff = Math.max(0, Math.floor((Date.now() - date.getTime()) / 1000));
  if (diff < 60) return t('messages.timeJustNow');
  const m = Math.floor(diff / 60);
  if (m < 60) return t('messages.timeMins', { m });
  const h = Math.floor(m / 60);
  if (h < 24) return t('messages.timeHours', { h });
  const d = Math.floor(h / 24);
  if (d === 1) return t('messages.timeYesterday');
  if (d < 7) return t('messages.timeDays', { d });
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
};

const loadSystemNotifications = async () => {
  systemLoading.value = true;
  try {
    const list: Notification[] = (await listNotificationsApi()) || [];
    systemMessages.value = list.map((n) => ({
      notificationId: n.notificationId,
      type: n.type,
      icon: iconForType(n.type),
      name: nameForType(n.type) + (n.title ? ' · ' + n.title : ''),
      time: formatRelativeTime(n.createdAt),
      preview: n.content || '',
      unread: !n.readFlag,
      link: n.link,
    }));
  } catch {
    systemMessages.value = [];
  } finally {
    systemLoading.value = false;
  }
};

const markAllReadHandler = async () => {
  // Optimistic UI: flip every row, then call the backend.
  systemMessages.value.forEach((m) => { m.unread = false; });
  try {
    await markAllReadApi();
    uni.showToast({ title: t('messages.allMarkedRead'), icon: 'success' });
  } catch (e: any) {
    uni.showToast({ title: e?.message || t('common.failed'), icon: 'none' });
  }
};

const handleSystemClick = async (item: SystemMessageView) => {
  // 如果卡片正处于滑开状态，先关闭，不执行跳转
  if (activeSwipeId.value === item.notificationId) {
    closeSwipe(item.notificationId);
    return;
  }
  if (item.unread) {
    item.unread = false;
    try { await markReadApi(item.notificationId); } catch { /* best-effort */ }
  }

  if (item.link) {
    if (item.link.startsWith('/pages/')) {
      // The interview report deep link needs an interviewId — without it the
      // page boots into a 404 state. Send those orphans to the history list
      // instead so the user can still recover the right report.
      if (item.link.startsWith('/pages/interview/report')
          && !/[?&]interviewId=\d+/.test(item.link)) {
        uni.showToast({ title: t('messages.linkMissingReport'), icon: 'none' });
        uni.navigateTo({ url: '/pages/interview/history' });
        return;
      }
      uni.navigateTo({ url: item.link });
    } else {
      uni.showToast({ title: item.link, icon: 'none' });
    }
    return;
  }

  // Legacy fallback for older notifications without a link payload.
  if (item.preview.includes('report')) {
    uni.navigateTo({ url: '/pages/interview/history' });
  } else if (item.preview.includes('diagnosis')) {
    uni.navigateTo({ url: '/pages/resume-ai/index' });
  }
};

onMounted(() => {
  refreshTheme();
  const safeMetrics = getMpSafeAreaMetrics();
  topSafeHeight.value = safeMetrics.topSafeHeight;
  rightAvoidWidth.value = safeMetrics.rightAvoidWidth;
});

// Pull notifications every time the tab becomes visible -- new alerts can
// arrive while the user is on another page (e.g. they just finished an
// interview / quiz).
onShow(() => {
  refreshTheme();
  loadSystemNotifications();
});
</script>

<style scoped>
.msg-page {
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  font-family: -apple-system, BlinkMacSystemFont, "SF Pro Text", "Helvetica Neue", sans-serif;
  height: calc(100vh - var(--window-top, 0px) - var(--window-bottom, 0px));
  overflow: hidden;
}

.status-spacer {
  width: 100%;
  flex-shrink: 0;
}

.page-header {
  padding: 8px 20px 14px;
}
.header-row {
  display: flex; align-items: center; justify-content: space-between;
}
.header-titles {
  display: flex; flex-direction: column; gap: 4px;
}
.clear-btn-icon {
  width: 32px;
  height: 32px;
  background: var(--primary-soft, #eff6ff);
  border: 1px solid #dbeafe;
  border-radius: 999px;
  display: flex; align-items: center; justify-content: center;
  color: var(--primary-color, #2563eb);
  font-size: 18px;
}
.clear-btn-icon:active { background: #dbeafe; }

.topbar-action {
  width: 32px;
  height: 32px;
  background: var(--primary-soft, #eff6ff);
  border: 1px solid #dbeafe;
  border-radius: 999px;
  color: var(--primary-color, #2563eb);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
}

.page-title {
  display: block;
  font-size: var(--font-hero, 28px);
  font-weight: 800;
  color: var(--text-primary, #0f172a);
}

.page-subtitle {
  display: block;
  font-size: var(--font-caption, 13px);
  line-height: var(--line-height-caption, 1.45);
  color: var(--text-tertiary, #8e8e93);
}

/* ---- Segment tabs ---- */
.segment-wrap {
  padding: 0 20px 16px;
}

.segment-bar {
  display: flex;
  border-radius: var(--btn-radius, 14px);
  padding: 4px;
  gap: 2px;
}

.seg-item {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 36px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 500;
  color: var(--text-secondary, #64748b);
  transition: all 0.25s cubic-bezier(0.25, 0.8, 0.25, 1);
  position: relative;
  gap: 6px;
}

.seg-active {
  background: var(--surface-2, #f8fafc);
  color: var(--text-primary, #0f172a);
  font-weight: 600;
  box-shadow: none;
}

.seg-badge {
  min-width: 18px;
  height: 18px;
  border-radius: 9px;
  background: var(--danger-color, #ef4444);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 5px;
}

.badge-num,
.seg-badge-text {
  font-size: 10px;
  font-weight: 700;
  color: #ffffff;
}

/* ---- Message list ---- */
.msg-list {
  flex: 1;
  height: 0;
  min-height: 0;
  padding-bottom: calc(16px + env(safe-area-inset-bottom, 0px));
  box-sizing: border-box;
}

.list-wrap {
  padding: 0 20px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.bottom-safe {
  height: calc(var(--tab-bar-height, 50px) + 20px);
}

/* 滑动删除容器 */
.swipe-row {
  position: relative;
  overflow: hidden;
  border-radius: var(--radius-md, 16px);
  margin-bottom: 0;
}

.msg-card {
  display: flex;
  align-items: center;
  padding: 16px;
  transition: transform 0.25s cubic-bezier(0.25, 0.8, 0.25, 1);
  position: relative;
  z-index: 1;
  will-change: transform;
}

.msg-card:active {
  opacity: 0.92;
}

.swipe-delete-btn {
  position: absolute;
  right: 0; top: 0; bottom: 0;
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
.swipe-delete-visible {
  opacity: 1;
}
.swipe-delete-text {
  color: #ffffff;
  font-size: 14px;
  font-weight: 700;
}

.msg-unread {
  border-color: var(--primary-color, #2563eb);
  background: linear-gradient(90deg, var(--primary-soft, #eff6ff), var(--surface-1, #ffffff));
}
.msg-unread::before {
  content: '';
  position: absolute;
  left: 0;
  top: 14px;
  bottom: 14px;
  width: 4px;
  border-radius: 999px;
  background: var(--primary-color, #2563eb);
}
.msg-unread .msg-name {
  color: var(--primary-color, #2563eb);
  font-weight: 800;
}
.msg-card:not(.msg-unread) {
  background: var(--surface-1, #ffffff);
}
.msg-card:not(.msg-unread) .msg-name,
.msg-card:not(.msg-unread) .msg-preview {
  opacity: 0.72;
}

/* ---- Avatar ---- */
.avatar-wrap {
  position: relative;
  margin-right: 14px;
  flex-shrink: 0;
}

.sys-0 { background: var(--primary-soft); color: var(--primary-color); }
.sys-1 { background: var(--violet-soft); color: var(--violet); }
.sys-2 { background: var(--cyan-soft); color: var(--cyan); }

.av-icon {
  font-size: 20px;
}

.unread-dot {
  position: absolute;
  top: -2px;
  right: -2px;
  width: 10px;
  height: 10px;
  border-radius: 5px;
  background: var(--primary-color, #2563eb);
  border: 2px solid #ffffff;
}

/* ---- Message body ---- */
.msg-body {
  flex: 1;
  min-width: 0;
}

.msg-top-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 5px;
}

.msg-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary, #0f172a);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
  margin-right: 8px;
}

.msg-time {
  font-size: 12px;
  color: var(--text-tertiary, #8e8e93);
  flex-shrink: 0;
}

.msg-preview {
  font-size: var(--font-caption, 13px);
  color: var(--text-secondary, #64748b);
  display: -webkit-box;
  line-clamp: 2;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: var(--line-height-caption, 1.45);
}

/* ---- Status tags ---- */
.status-tag {
  display: inline-flex;
  margin-top: 8px;
  padding: 3px 10px;
  border-radius: 8px;
}

.tag-text {
  font-size: 11px;
  font-weight: 600;
}

.tag-progress {
  background: var(--primary-soft, #eff6ff);
}
.tag-progress .tag-text { color: var(--primary-color, #2563eb); }

.tag-sent {
  background: #f0fdf4;
}
.tag-sent .tag-text { color: #16a34a; }

.tag-pass {
  background: #fef3c7;
}
.tag-pass .tag-text { color: #d97706; }

/* ---- Empty state ---- */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 80px 20px;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.empty-text {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-tertiary, #8e8e93);
  margin-bottom: 6px;
}

.empty-sub {
  font-size: 13px;
  color: var(--text-secondary, #64748b);
}

.is-dark {
  background: #0f172a;
}

.is-dark .page-title,
.is-dark .msg-name {
  color: #f8fafc;
}

.is-dark .msg-card {
  background: #1e293b;
  box-shadow: none;
}

.is-dark .msg-preview,
.is-dark .msg-time {
  color: var(--text-tertiary, #8e8e93);
}

.is-dark .segment-bar { background: #1e293b; border-color: #334155; }
.is-dark .seg-item { color: var(--text-tertiary, #8e8e93); }
.is-dark .seg-active { background: #334155; color: #f8fafc; }
.is-dark .clear-btn-icon { background: rgba(37, 99, 235, 0.15); border-color: rgba(37, 99, 235, 0.3); color: #60a5fa; }
.is-dark .msg-unread { background: linear-gradient(0deg, rgba(37, 99, 235, 0.15), rgba(37, 99, 235, 0.15)), #1e293b; border-color: rgba(37, 99, 235, 0.35); }
.is-dark .tag-progress { background: rgba(37, 99, 235, 0.15); }
.is-dark .tag-progress .tag-text { color: #60a5fa; }
.is-dark .tag-sent { background: rgba(16, 185, 129, 0.15); }
.is-dark .tag-sent .tag-text { color: #34d399; }
.is-dark .tag-pass { background: rgba(245, 158, 11, 0.15); }
.is-dark .tag-pass .tag-text { color: #fbbf24; }
.is-dark .empty-text { color: var(--text-tertiary, #8e8e93); }
.is-dark .empty-sub { color: var(--text-secondary, #64748b); }
.is-dark .unread-dot { border-color: var(--text-primary, #0f172a); }

/* ================================================================
 *  MP-WEIXIN parity overrides — HARDCODED values, no CSS vars.
 * ================================================================ */
/* #ifdef MP-WEIXIN */

.msg-card {
  overflow: visible;
  border: 1.5px solid #e2e8f0;
  box-shadow: var(--shadow-xs);
}

.segment-bar {
  overflow: visible;
  border: 1.5px solid #b0bfd0;
  box-shadow: var(--shadow-sm);
}

.seg-active {
  background: #dbeafe;
  color: var(--primary-color, #2563eb);
  box-shadow: var(--shadow-xs);
}

/* Dark theme on MP: the block above is light-first; pin dark styles with
   higher specificity so the segment bar never stays a white slab. */

.msg-page.is-dark .segment-bar {
  background: #1e293b;
  border-color: #334155;
  box-shadow: none;
}

.msg-page.is-dark .seg-item {
  color: var(--text-tertiary, #8e8e93);
}

.msg-page.is-dark .seg-active {
  background: #334155;
  color: #f8fafc;
  box-shadow: none;
}

.msg-page.is-dark .msg-card {
  background: #1e293b;
  border-color: #334155;
  box-shadow: none;
}

.msg-page.is-dark .empty-state {
  background: transparent;
  border: none;
}

.msg-page.is-dark .msg-name {
  color: #f8fafc;
}

.msg-page.is-dark .msg-preview,
.msg-page.is-dark .msg-time {
  color: var(--text-tertiary, #8e8e93);
}

.msg-page.is-dark .clear-btn-icon {
  background: rgba(37, 99, 235, 0.15);
  border-color: rgba(37, 99, 235, 0.3);
  color: #60a5fa;
}

.msg-page.is-dark .topbar-action {
  background: rgba(37, 99, 235, 0.15);
  border-color: rgba(37, 99, 235, 0.3);
  color: #60a5fa;
}

/* #endif */
</style>
