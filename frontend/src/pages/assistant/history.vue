<template>
  <SlPage class="app-soft-bg" :custom-class="['history-page', themeClass, fontClass].join(' ')">
    <SlNavBar :title="t('assistantHistory.navTitle')" show-back @back="goBack" :safe-top="topSafe" />

    <scroll-view class="content" scroll-y>
      <view class="retention-card app-card-soft app-surface">
        <text class="retention-title">{{ t('assistantHistory.retentionTitle') }}</text>
        <text class="retention-desc">{{ t('assistantHistory.retentionDesc') }}</text>
        <text class="retention-desc">{{ t('assistantHistory.retentionDesc2') }}</text>
      </view>

      <view v-if="loading" class="loading-card app-card-soft app-surface">
        <text class="loading-text">{{ t('assistantHistory.loading') }}</text>
      </view>

      <view v-else-if="sessions.length === 0" class="empty-state app-empty app-surface">
        <text class="empty-icon ri-chat-3-line"></text>
        <text class="empty-title">{{ t('assistantHistory.emptyTitle') }}</text>
        <text class="empty-desc">{{ t('assistantHistory.emptyDesc') }}</text>
      </view>

      <view v-else class="session-list">
        <view
          v-for="session in sessions"
          :key="session.sessionId"
          class="swipe-row"
        >
          <view
            class="session-card app-card-soft app-surface"
            :class="{ 'session-card-swiped': (swipeOffsets[session.sessionId] ?? 0) < 0 }"
            :style="{ transform: `translateX(${swipeOffsets[session.sessionId] ?? 0}px)` }"
            @click="openSession(session)"
            @touchstart="onItemTouchStart($event, session.sessionId)"
            @touchmove="onItemTouchMove($event, session.sessionId)"
            @touchend="onItemTouchEnd($event, session.sessionId)"
           hover-class="press-fb" hover-stay-time="120">
            <view class="session-top">
              <view class="persona-pill">
                <text class="persona-emoji" :class="personaMeta(session.persona).emoji"></text>
                <text class="persona-name">{{ personaMeta(session.persona).label }}</text>
              </view>
              <text class="session-time">{{ formatTime(session.updatedAt || session.createdAt) }}</text>
            </view>
            <text class="session-title">{{ session.title || t('assistantHistory.newConversation') }}</text>
            <view class="session-bottom">
              <text class="session-hint">{{ t('assistantHistory.tapToContinue') }}</text>
              <text class="session-delete-hint">左滑删除</text>
            </view>
          </view>
          <view
            class="swipe-delete-btn"
            :class="{ 'swipe-delete-visible': (swipeOffsets[session.sessionId] ?? 0) < 0 }"
            @click.stop="confirmDelete(session)"
           hover-class="press-fb" hover-stay-time="120">
            <text class="swipe-delete-text">{{ t('assistantHistory.deleteBtn') }}</text>
          </view>
        </view>
      </view>

      <view class="bottom-safe"></view>
    </scroll-view>
  </SlPage>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useI18n } from '@/locales';
import { getMpSafeAreaMetrics } from '@/utils/safeArea';
import request from '@/utils/request';
import { useTheme } from '@/utils/theme';
import { isRealUser, requireAuth } from '@/utils/auth';
import SlPage from '@/style-library/components/SlPage.vue';
import SlNavBar from '@/style-library/components/SlNavBar.vue';

interface AssistantSession {
  sessionId: number;
  userId: number;
  title?: string;
  persona?: 'MENTOR' | 'CHALLENGER' | 'INTERVIEWER' | string;
  createdAt?: string;
  updatedAt?: string;
}

const { t } = useI18n();
const { themeClass, fontClass, refresh: refreshTheme } = useTheme();
const topSafe = ref(44);
const loading = ref(true);
const sessions = ref<AssistantSession[]>([]);
const swipeOffsets = ref<Record<number, number>>({});
const activeSwipeId = ref<number | null>(null);
let touchStartX = 0;
let touchStartY = 0;
let touchBaseOffset = 0;
let dirLocked = false;
let isHorizontal = false;
const DELETE_BTN_W = 80;

const ensurePageAuth = () => {
  if (isRealUser()) return true;
  loading.value = false;
  sessions.value = [];
  return requireAuth({
    redirect: 'reLaunch',
    cancelBehavior: 'back',
    message: '登录后才能查看、继续或删除你的 AI 对话记录。',
  });
};

const personaMeta = (persona?: string) => {
  const map: Record<string, { emoji: string; label: string }> = {
    MENTOR: { emoji: 'ri-compass-3-line', label: '求职教练' },
    CHALLENGER: { emoji: 'ri-shield-star-line', label: '严格反馈' },
    INTERVIEWER: { emoji: 'ri-mic-line', label: '面试练习' },
  };
  return map[persona || 'MENTOR'] || map.MENTOR;
};

const loadSessions = async () => {
  if (!ensurePageAuth()) return;
  loading.value = true;
  try {
    const userId = Number(uni.getStorageSync('userId'));
    const res = await request<AssistantSession[]>({ url: `/api/chat/history/${userId}`, method: 'GET' });
    sessions.value = Array.isArray(res) ? res : [];
  } catch (e: any) {
    uni.showToast({ title: e?.message || t('assistantHistory.loadFailed'), icon: 'none' });
  } finally {
    loading.value = false;
  }
};

const formatTime = (value?: string) => {
  if (!value) return '';
  const d = new Date(value.replace(' ', 'T'));
  if (Number.isNaN(d.getTime())) return value;
  const now = new Date();
  const sameDay = d.toDateString() === now.toDateString();
  const time = `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
  if (sameDay) return t('messages.timeToday', { time }) || `今天 ${time}`;
  return `${d.getMonth() + 1}/${d.getDate()} ${time}`;
};

const openSession = (session: AssistantSession) => {
  if (!ensurePageAuth()) return;
  uni.setStorageSync('assistantOpenSession', {
    sessionId: session.sessionId,
    persona: session.persona || 'MENTOR',
  });
  uni.switchTab({ url: '/pages/assistant/index' });
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

const confirmDelete = (session: AssistantSession) => {
  if (!ensurePageAuth()) return;
  uni.showModal({
    title: t('assistantHistory.deleteBtn'),
    content: t('assistantHistory.deleteConfirm', { title: session.title || t('assistantHistory.newConversation') }),
    confirmText: t('assistantHistory.deleteBtn'),
    confirmColor: '#ef4444',
    success: async (res) => {
      if (!res.confirm) return;
      try {
        await request({ url: `/api/chat/history/session/${session.sessionId}`, method: 'DELETE' });
        sessions.value = sessions.value.filter((s) => s.sessionId !== session.sessionId);
        delete swipeOffsets.value[session.sessionId];
        activeSwipeId.value = null;
        uni.showToast({ title: t('assistantHistory.deleteSuccess'), icon: 'success' });
      } catch (e: any) {
        uni.showToast({ title: e?.message || t('assistantHistory.deleteFailed'), icon: 'none' });
      }
    },
  });
};

const goBack = () => uni.navigateBack();

onMounted(() => {
  refreshTheme();
  topSafe.value = getMpSafeAreaMetrics().topSafeHeight;
  loadSessions();
});
</script>

<style scoped>
.content { height: calc(100vh - 96px); padding: 16px; box-sizing: border-box; }
.retention-card, .loading-card, .session-card { border-radius: var(--radius-lg, 18px); }
.retention-card { padding: 16px; margin-bottom: 14px; }
.retention-title { display: block; font-size: 16px; font-weight: 800; color: var(--text-primary, #1c1917); margin-bottom: 8px; }
.retention-desc { display: block; font-size: 13px; line-height: 1.55; color: var(--text-secondary, #78716c); margin-top: 6px; }
.loading-card { padding: 18px; text-align: center; }
.loading-text { font-size: 13px; color: var(--text-secondary, #78716c); }
.empty-state { padding: 56px 18px; text-align: center; }
.empty-icon { display: block; font-size: 48px; margin-bottom: 10px; }
.empty-title { display: block; font-size: 17px; font-weight: 800; color: var(--text-primary, #1c1917); margin-bottom: 8px; }
.empty-desc { display: block; font-size: 13px; line-height: 1.55; color: var(--text-secondary, #78716c); }
.session-list { display: flex; flex-direction: column; gap: 12px; }
.swipe-row { position: relative; overflow: hidden; border-radius: var(--radius-lg, 18px); }
.session-card {
  position: relative;
  z-index: 1;
  padding: 14px;
  transition: transform 0.25s cubic-bezier(0.25, 0.8, 0.25, 1);
  will-change: transform;
}
.session-card:active { transform: scale(0.99); background: var(--surface-2, #fafaf9); }
.session-card-swiped:active { transform: none; }
.session-top, .session-bottom { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.persona-pill { display: flex; align-items: center; gap: 5px; background: var(--primary-soft, #fcf5f2); border-radius: 999px; padding: 5px 9px; }
.persona-emoji { font-size: 14px; }
.persona-name { font-size: 12px; font-weight: 800; color: var(--primary-color, #cd6a43); }
.session-time { font-size: 12px; color: var(--text-tertiary, #8e8e93); }
.session-title { display: block; font-size: 15px; font-weight: 800; line-height: 1.45; color: var(--text-primary, #1c1917); margin: 12px 0; }
.session-hint { font-size: 12px; color: var(--text-secondary, #78716c); }
.session-delete-hint { font-size: 11px; color: var(--text-tertiary, #8e8e93); }
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
  border-radius: 0 var(--radius-lg, 18px) var(--radius-lg, 18px) 0;
  z-index: 0;
  opacity: 0;
  transition: opacity 0.18s ease;
}
.swipe-delete-visible { opacity: 1; }
.swipe-delete-text { font-size: 14px; font-weight: 800; color: #ffffff; }
.bottom-safe { height: 48px; }
.is-dark .retention-title, .is-dark .session-title, .is-dark .empty-title { color: #fafaf9; }
.is-dark .retention-card, .is-dark .loading-card, .is-dark .session-card { background: #292524; border-color: #44403c; box-shadow: none; }
.is-dark .retention-desc, .is-dark .loading-text, .is-dark .empty-desc, .is-dark .session-hint, .is-dark .session-time { color: var(--text-tertiary, #8e8e93); }
.is-dark .persona-pill { background: rgba(205, 106, 67,0.16); }
.is-dark .session-card:active { background: #44403c; }

/* Website-aligned editorial skin */
.content {
  height: calc(100vh - 96px);
  padding: 16px;
  background: #faf9f6;
  box-sizing: border-box;
}

.retention-card,
.loading-card,
.session-card {
  border: 1px solid #e0dfdb;
  border-radius: 8px;
  background: #faf9f6;
  box-shadow: 0 5px 16px rgba(44, 43, 41, 0.045);
}

.retention-card {
  padding: 16px 15px;
  border-left: 2px solid #ac6448;
}

.retention-title,
.empty-title,
.session-title {
  color: #2c2b29;
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.retention-desc,
.loading-text,
.empty-desc,
.session-hint {
  color: #5a5956;
}

.retention-desc,
.empty-desc {
  line-height: 1.7;
}

.empty-state {
  border: 1px solid #e0dfdb;
  border-radius: 8px;
  background: #faf9f6;
}

.empty-icon {
  color: #ac6448;
}

.session-list {
  gap: 10px;
}

.swipe-row {
  border-radius: 8px;
}

.session-card {
  padding: 15px;
}

.session-card:active {
  background: #f5f5f0;
}

.persona-pill {
  min-height: 30px;
  padding: 4px 9px;
  border: 1px solid #d9d8d3;
  border-radius: 4px;
  background: #f5f5f0;
  box-sizing: border-box;
}

.persona-emoji {
  color: #ac6448;
}

.persona-name {
  color: #ac6448;
  font-weight: 500;
  letter-spacing: 0.03em;
}

.session-time,
.session-delete-hint {
  color: #8b8a86;
}

.session-title {
  margin: 13px 0;
  font-size: 16px;
}

.swipe-delete-btn {
  border-radius: 0 8px 8px 0;
  background: #c23b22;
}

.swipe-delete-text {
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
  font-weight: 600;
  letter-spacing: 0.05em;
}

.is-dark .content {
  background: #1f1f1d;
}

.is-dark .retention-card,
.is-dark .loading-card,
.is-dark .session-card,
.is-dark .empty-state {
  background: #292926;
  border-color: #45443f;
  box-shadow: none;
}

.is-dark .retention-title,
.is-dark .session-title,
.is-dark .empty-title {
  color: #f5f5f0;
}

.is-dark .retention-desc,
.is-dark .loading-text,
.is-dark .empty-desc,
.is-dark .session-hint {
  color: #c4c2bc;
}

.is-dark .persona-pill {
  background: #33332f;
  border-color: #575650;
}

.is-dark .session-card:active {
  background: #33332f;
}
</style>
