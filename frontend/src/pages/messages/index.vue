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
         hover-class="press-fb" hover-stay-time="120">
          <text class="seg-text">{{ tab.label }}</text>
          <view class="seg-badge" v-if="unreadByTab[tab.key] > 0">
            <text class="seg-badge-text">{{ unreadByTab[tab.key] }}</text>
          </view>
        </view>
      </view>
    </view>

    <!-- #ifdef MP-WEIXIN -->
    <view class="wechat-reminder-card app-card-soft" @click="enableWechatReminders" hover-class="press-fb" hover-stay-time="120">
      <view class="wechat-reminder-icon"><text class="ri-notification-badge-line"></text></view>
      <view class="wechat-reminder-copy">
        <text class="wechat-reminder-title">{{ t('messages.wechatReminderTitle') }}</text>
        <text class="wechat-reminder-desc">{{ t('messages.wechatReminderDesc') }}</text>
      </view>
      <text class="wechat-reminder-action">{{ wechatReminderLoading ? t('messages.wechatReminderLoading') : t('messages.wechatReminderEnable') }}</text>
    </view>
    <!-- #endif -->

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
           hover-class="press-fb" hover-stay-time="120">
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
           hover-class="press-fb" hover-stay-time="120">
            <text class="swipe-delete-text">{{ t('messages.deleteBtn') }}</text>
          </view>
        </view>
      </view>

      <view class="empty-state app-empty" v-if="systemLoading && systemMessages.length === 0">
        <text class="empty-icon ri-loader-4-line state-spinning"></text>
        <text class="empty-text">{{ t('messages.loading') }}</text>
      </view>

      <view class="empty-state app-empty" v-else-if="systemError">
        <text class="empty-icon ri-wifi-off-line"></text>
        <text class="empty-text">{{ t('messages.loadFailed') }}</text>
        <text class="empty-sub">{{ systemError }}</text>
        <view class="state-retry" @click="loadSystemNotifications" hover-class="press-fb" hover-stay-time="120"><text>{{ t('messages.retry') }}</text></view>
      </view>

      <!-- Empty state -->
      <view class="empty-state app-empty" v-else-if="!systemLoading && filteredMessages.length === 0">
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
import { isRealUser, requireAuth } from '@/utils/auth';
import { requestConfiguredSubscribe } from '@/utils/wxSubscribe';

const { t } = useI18n();
const topSafeHeight = ref(88);
const rightAvoidWidth = ref(20);
const { themeClass, fontClass, refresh: refreshTheme } = useTheme();
const wechatReminderLoading = ref(false);

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
const systemError = ref('');
const unreadCount = computed(() => systemMessages.value.filter((m) => m.unread).length);

const ensurePageAuth = () => {
  if (isRealUser()) return true;
  systemMessages.value = [];
  systemLoading.value = false;
  systemError.value = '';
  return requireAuth({
    redirect: 'reLaunch',
    cancelBehavior: 'back',
    message: '登录后才能查看你的任务提醒、面试报告和学校跟进消息。',
  });
};

const enableWechatReminders = async () => {
  if (wechatReminderLoading.value || !ensurePageAuth()) return;
  wechatReminderLoading.value = true;
  try {
    const result = await requestConfiguredSubscribe();
    if (!result.configured) {
      uni.showToast({ title: t('messages.wechatReminderUnavailable'), icon: 'none' });
    } else if (!result.synced) {
      uni.showToast({ title: t('messages.wechatReminderSyncFailed'), icon: 'none' });
    } else if (result.accepted > 0) {
      uni.showToast({
        title: t('messages.wechatReminderEnabled', { n: result.accepted }),
        icon: 'success',
      });
    } else {
      uni.showToast({ title: t('messages.wechatReminderNotEnabled'), icon: 'none' });
    }
  } catch {
    uni.showToast({ title: t('messages.wechatReminderFailed'), icon: 'none' });
  } finally {
    wechatReminderLoading.value = false;
  }
};

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
  if (!ensurePageAuth()) return;
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
  if (!ensurePageAuth()) return;
  systemLoading.value = true;
  systemError.value = '';
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
  } catch (e: any) {
    systemError.value = e?.message || t('messages.loadFailed');
  } finally {
    systemLoading.value = false;
  }
};

const markAllReadHandler = async () => {
  if (!ensurePageAuth()) return;
  const previousUnread = new Set(
    systemMessages.value.filter((message) => message.unread).map((message) => message.notificationId),
  );
  systemMessages.value.forEach((m) => { m.unread = false; });
  try {
    await markAllReadApi();
    uni.showToast({ title: t('messages.allMarkedRead'), icon: 'success' });
  } catch (e: any) {
    systemMessages.value.forEach((message) => {
      message.unread = previousUnread.has(message.notificationId);
    });
    uni.showToast({ title: e?.message || t('common.failed'), icon: 'none' });
  }
};

const handleSystemClick = async (item: SystemMessageView) => {
  if (!ensurePageAuth()) return;
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
      const base = item.link.split('?')[0];
      if (['/pages/home/index', '/pages/assistant/index', '/pages/resume/index', '/pages/user/index'].includes(base)) {
        uni.switchTab({ url: base });
      } else {
        uni.navigateTo({ url: item.link });
      }
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
  font-family: var(--font-sans, "Noto Sans SC", "PingFang SC", "Microsoft YaHei", sans-serif);
  height: calc(100vh - var(--window-top, 0px) - var(--window-bottom, 0px));
  overflow: hidden;
}

.state-retry {
  min-width: 120px;
  min-height: 44px;
  margin: 16px auto 0;
  padding: 0 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: var(--vermilion, #c23b22);
  border-radius: var(--btn-radius, 6px);
  font-size: 14px;
  font-weight: 600;
}
.state-spinning { animation: message-spin .8s linear infinite; }
@keyframes message-spin { to { transform: rotate(360deg); } }

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
  background: var(--primary-soft, #fcf5f2);
  border: 1px solid #f7e8e2;
  border-radius: 999px;
  display: flex; align-items: center; justify-content: center;
  color: var(--primary-color, #cd6a43);
  font-size: 18px;
}
.clear-btn-icon:active { background: #f7e8e2; }

.topbar-action {
  width: 32px;
  height: 32px;
  background: var(--primary-soft, #fcf5f2);
  border: 1px solid #f7e8e2;
  border-radius: 999px;
  color: var(--primary-color, #cd6a43);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
}

.page-title {
  display: block;
  font-size: var(--font-hero, 28px);
  font-weight: 800;
  color: var(--text-primary, #1c1917);
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

.wechat-reminder-card {
  margin: 0 20px 14px;
  padding: 14px;
  display: flex;
  align-items: center;
  gap: 12px;
}
.wechat-reminder-icon {
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  border-radius: var(--radius-sm, 8px);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--primary-color, #cd6a43);
  background: var(--primary-soft, #fcf5f2);
  font-size: 19px;
}
.wechat-reminder-copy {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.wechat-reminder-title {
  color: var(--text-primary, #1c1917);
  font-size: var(--font-body, 14px);
  font-weight: 700;
}
.wechat-reminder-desc {
  color: var(--text-tertiary, #8e8e93);
  font-size: var(--font-caption, 12px);
  line-height: 1.45;
}
.wechat-reminder-action {
  flex-shrink: 0;
  color: var(--primary-color, #cd6a43);
  font-size: var(--font-caption, 12px);
  font-weight: 700;
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
  color: var(--text-secondary, #78716c);
  transition: all 0.25s cubic-bezier(0.25, 0.8, 0.25, 1);
  position: relative;
  gap: 6px;
}

.seg-active {
  background: var(--surface-2, #fafaf9);
  color: var(--text-primary, #1c1917);
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
  border-color: var(--primary-color, #cd6a43);
  background: linear-gradient(90deg, var(--primary-soft, #fcf5f2), var(--surface-1, #ffffff));
}
.msg-unread::before {
  content: '';
  position: absolute;
  left: 0;
  top: 14px;
  bottom: 14px;
  width: 4px;
  border-radius: 999px;
  background: var(--primary-color, #cd6a43);
}
.msg-unread .msg-name {
  color: var(--primary-color, #cd6a43);
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
  background: var(--primary-color, #cd6a43);
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
  color: var(--text-primary, #1c1917);
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
  color: var(--text-secondary, #78716c);
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
  background: var(--primary-soft, #fcf5f2);
}
.tag-progress .tag-text { color: var(--primary-color, #cd6a43); }

.tag-sent {
  background: #fbf8f2;
}
.tag-sent .tag-text { color: #896b30; }

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
  color: var(--text-secondary, #78716c);
}

.is-dark {
  background: #1c1917;
}

.is-dark .page-title,
.is-dark .msg-name {
  color: #fafaf9;
}

.is-dark .msg-card {
  background: #292524;
  box-shadow: none;
}

.is-dark .msg-preview,
.is-dark .msg-time {
  color: var(--text-tertiary, #8e8e93);
}

.is-dark .segment-bar { background: #292524; border-color: #44403c; }
.is-dark .seg-item { color: var(--text-tertiary, #8e8e93); }
.is-dark .seg-active { background: #44403c; color: #fafaf9; }
.is-dark .clear-btn-icon { background: rgba(205, 106, 67, 0.15); border-color: rgba(205, 106, 67, 0.3); color: #dd987d; }
.is-dark .msg-unread { background: linear-gradient(0deg, rgba(205, 106, 67, 0.15), rgba(205, 106, 67, 0.15)), #292524; border-color: rgba(205, 106, 67, 0.35); }
.is-dark .tag-progress { background: rgba(205, 106, 67, 0.15); }
.is-dark .tag-progress .tag-text { color: #dd987d; }
.is-dark .tag-sent { background: rgba(149, 117, 52, 0.15); }
.is-dark .tag-sent .tag-text { color: #bf9748; }
.is-dark .tag-pass { background: rgba(245, 158, 11, 0.15); }
.is-dark .tag-pass .tag-text { color: #fbbf24; }
.is-dark .empty-text { color: var(--text-tertiary, #8e8e93); }
.is-dark .empty-sub { color: var(--text-secondary, #78716c); }
.is-dark .unread-dot { border-color: var(--text-primary, #1c1917); }

/* ================================================================
 *  MP-WEIXIN parity overrides — HARDCODED values, no CSS vars.
 * ================================================================ */
/* #ifdef MP-WEIXIN */

.msg-card {
  overflow: visible;
  border: 1.5px solid #e7e5e4;
  box-shadow: var(--shadow-xs);
}

.segment-bar {
  overflow: visible;
  border: 1.5px solid #d3b8ad;
  box-shadow: var(--shadow-sm);
}

.seg-active {
  background: #f7e8e2;
  color: var(--primary-color, #cd6a43);
  box-shadow: var(--shadow-xs);
}

/* Dark theme on MP: the block above is light-first; pin dark styles with
   higher specificity so the segment bar never stays a white slab. */

.msg-page.is-dark .segment-bar {
  background: #292524;
  border-color: #44403c;
  box-shadow: none;
}

.msg-page.is-dark .seg-item {
  color: var(--text-tertiary, #8e8e93);
}

.msg-page.is-dark .seg-active {
  background: #44403c;
  color: #fafaf9;
  box-shadow: none;
}

.msg-page.is-dark .msg-card {
  background: #292524;
  border-color: #44403c;
  box-shadow: none;
}

.msg-page.is-dark .empty-state {
  background: transparent;
  border: none;
}

.msg-page.is-dark .msg-name {
  color: #fafaf9;
}

.msg-page.is-dark .msg-preview,
.msg-page.is-dark .msg-time {
  color: var(--text-tertiary, #8e8e93);
}

.msg-page.is-dark .clear-btn-icon {
  background: rgba(205, 106, 67, 0.15);
  border-color: rgba(205, 106, 67, 0.3);
  color: #dd987d;
}

.msg-page.is-dark .topbar-action {
  background: rgba(205, 106, 67, 0.15);
  border-color: rgba(205, 106, 67, 0.3);
  color: #dd987d;
}

/* #endif */

/* ── CareerLoop editorial skin ─────────────────────────────────────────── */
.msg-page {
  background: #faf9f6;
  color: #2c2b29;
  font-family: "Noto Sans SC", "PingFang SC", "Microsoft YaHei", sans-serif;
}

.page-header {
  padding-bottom: 16px;
}

.page-title,
.msg-name,
.empty-text {
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
  font-weight: 600;
  letter-spacing: 0.05em;
}

.page-title {
  color: #2c2b29;
  font-size: 27px;
  line-height: 1.35;
}

.page-subtitle {
  color: #8b8a86;
  line-height: 1.65;
}

.clear-btn-icon,
.topbar-action {
  width: 32px;
  height: 32px;
  border: 1px solid rgba(172, 100, 72, 0.32);
  border-radius: 6px;
  background: #f7f1ef;
  color: #ac6448;
}

.clear-btn-icon:active {
  background: #f1e8e4;
}

.segment-wrap {
  padding-bottom: 14px;
}

.segment-bar {
  padding: 0;
  gap: 0;
  border: none;
  border-bottom: 1px solid #e0dfdb;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
}

.seg-item {
  height: 40px;
  border-bottom: 2px solid transparent;
  border-radius: 0;
  color: #5a5956;
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
  letter-spacing: 0.04em;
}

.seg-active {
  border-bottom-color: #c23b22;
  background: transparent;
  color: #c23b22;
  box-shadow: none;
}

.seg-badge {
  min-width: 17px;
  height: 17px;
  border-radius: 3px;
  background: #c23b22;
}

.list-wrap {
  gap: 10px;
}

.swipe-row {
  border-radius: 8px;
}

.msg-card {
  min-height: 76px;
  padding: 15px 16px;
  border: 1px solid #e0dfdb;
  border-radius: 8px;
  background: #fffdfa;
  box-shadow: 0 8px 24px rgba(44, 43, 41, 0.04);
}

.msg-card:not(.msg-unread) {
  background: #fffdfa;
}

.msg-card:not(.msg-unread) .msg-name,
.msg-card:not(.msg-unread) .msg-preview {
  opacity: 0.78;
}

.msg-unread {
  border-color: rgba(194, 59, 34, 0.38);
  background: #fdf7f4;
}

.msg-unread::before {
  width: 3px;
  border-radius: 0;
  background: #c23b22;
}

.msg-unread .msg-name {
  color: #c23b22;
}

.avatar-wrap {
  margin-right: 13px;
}

.app-icon-tile {
  border: 1px solid #e0dfdb;
  border-radius: 6px;
}

.sys-0 {
  background: #f7f1ef;
  color: #ac6448;
}

.sys-1 {
  background: #f1efeb;
  color: #8d846e;
}

.sys-2 {
  background: #f6f1e7;
  color: #b8975a;
}

.unread-dot {
  border-radius: 2px;
  border-color: #fffdfa;
  background: #c23b22;
}

.msg-name {
  color: #2c2b29;
}

.msg-time {
  color: #8b8a86;
  font-variant-numeric: tabular-nums;
}

.msg-preview {
  color: #5a5956;
  line-height: 1.65;
}

.swipe-delete-btn {
  border-radius: 0 8px 8px 0;
  background: #c23b22;
}

.status-tag {
  border-radius: 4px;
}

.tag-progress {
  border: 1px solid rgba(172, 100, 72, 0.28);
  background: #f7f1ef;
}

.tag-progress .tag-text {
  color: #ac6448;
}

.tag-sent {
  border: 1px solid rgba(141, 132, 110, 0.36);
  background: #f1efeb;
}

.tag-sent .tag-text {
  color: #766f5d;
}

.tag-pass {
  border: 1px solid rgba(184, 151, 90, 0.36);
  background: #f6f1e7;
}

.tag-pass .tag-text {
  color: #9a783f;
}

.empty-text {
  color: #2c2b29;
}

.empty-sub {
  color: #8b8a86;
}

.msg-page.is-dark {
  background: #24231f;
}

.msg-page.is-dark .segment-bar {
  border-color: #46443e;
  background: transparent;
}

.msg-page.is-dark .seg-active {
  border-bottom-color: #dc6a52;
  background: transparent;
  color: #f08a73;
}

.msg-page.is-dark .msg-card {
  border-color: #46443e;
  background: #302f2a;
}

.msg-page.is-dark .msg-unread {
  border-color: rgba(220, 106, 82, 0.48);
  background: #392b27;
}

.msg-page.is-dark .msg-unread .msg-name {
  color: #f08a73;
}

.msg-page.is-dark .clear-btn-icon,
.msg-page.is-dark .topbar-action {
  border-color: rgba(214, 178, 163, 0.38);
  background: rgba(172, 100, 72, 0.18);
  color: #ebc9bc;
}
</style>
