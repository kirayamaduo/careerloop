<template>
  <view class="chat-page app-soft-bg" :class="[themeClass, fontClass]">
    <!-- Custom nav bar -->
    <view class="chat-nav">
      <view class="nav-spacer" :style="{ height: topSafeHeight + 'px' }"></view>
      <view class="nav-row" :style="{ paddingRight: rightAvoidWidth + 'px' }">
        <view class="nav-bot-avatar" :style="{ background: currentPersona.gradient }">
          <text class="nav-bot-emoji" :class="currentPersona.emoji"></text>
        </view>
        <view class="nav-meta">
          <text class="nav-name">{{ currentPersona.name }}</text>
          <view class="nav-status-row">
            <view class="online-dot"></view>
            <text class="nav-status">{{ currentPersona.tagline }}</text>
          </view>
        </view>
        <view class="nav-action" @click="openHistory">
          <text class="ri-history-line nav-action-icon"></text>
        </view>
      </view>
    </view>

    <!-- Chat messages area -->
    <scroll-view
      class="chat-list"
      scroll-y
      :scroll-top="scrollTop"
      scroll-with-animation
      @scroll="onListScroll"
    >
      <!-- Full-bleed surface so MP scroll-view never shows a light strip
           below long threads (scroll-view default vs page theme). -->
      <view class="chat-list-surface">
      <!-- Welcome card -->
      <view class="welcome-card">
        <text class="welcome-brand">智绘职路 CareerLoop · AI 导师</text>
        <view class="welcome-icon" :class="currentPersona.emoji"></view>
        <text class="welcome-title">{{ currentPersona.name }}</text>
        <text class="welcome-desc">{{ currentPersona.intro }}</text>
        <view class="agent-scope ui-list-item">
          <text class="agent-scope-label">{{ t('assistantPage.bestFor') }}</text>
          <text class="agent-scope-text">{{ currentPersona.bestFor }}</text>
        </view>
        <view class="agent-note" v-if="currentPersona.note">
          <text class="agent-note-text">{{ currentPersona.note }}</text>
        </view>
        <view class="quick-actions">
          <view
            v-for="chip in currentPersona.chips"
            :key="chip"
            class="quick-chip ui-list-item"
            @click="sendQuick(chip)"
          >
            <text class="chip-text">{{ chip }}</text>
          </view>
        </view>
      </view>

      <!-- Timestamp -->
      <view class="time-divider">
        <text class="time-text">{{ chatTimeLabel }}</text>
      </view>

      <!-- Messages -->
      <view
        class="msg-row"
        v-for="(msg, index) in messages"
        :key="index"
        :class="msg.role === 'user' ? 'row-user' : 'row-ai'"
      >
        <!-- AI avatar (top-left of bubble) -->
        <view class="bot-avatar" v-if="msg.role === 'assistant'">
          <text class="bot-emoji ri-robot-2-line"></text>
        </view>

        <view class="bubble-area">
          <view class="bubble" :class="msg.role === 'user' ? 'bubble-user' : 'bubble-ai'">
            <view class="typing-dots" v-if="msg.isTyping">
              <view class="dot"></view>
              <view class="dot"></view>
              <view class="dot"></view>
            </view>
            <text class="bubble-text" v-else>{{ msg.content }}</text>
          </view>
        </view>
      </view>

      <view class="scroll-pad"></view>
      </view>
    </scroll-view>

    <!-- Input area -->
    <view class="input-bar">
      <text class="mode-caption">三种对话模式</text>
      <view class="input-row">
        <view class="mode-picker-wrap" @click="showPersonaSheet = true">
          <view class="mode-picker">
            <text class="mode-picker-icon" :class="currentPersona.emoji"></text>
            <text class="mode-picker-text">{{ currentPersona.label }}</text>
            <text class="mode-picker-arrow ri-arrow-down-s-line"></text>
          </view>
        </view>
        <input
          class="chat-input"
          v-model="inputText"
          :placeholder="t('assistantPage.inputHint')"
          placeholder-class="input-ph"
          @confirm="sendMessage"
          confirm-type="send"
        />
        <view
          class="send-btn"
          :class="{ 'send-active': canSend, 'send-disabled': !canSend }"
          @click="sendMessage"
        >
          <text class="send-label">{{ t('assistant.send') }}</text>
        </view>
      </view>
    </view>
    <SlActionSheet
      v-model:visible="showPersonaSheet"
      title="选择对话模式"
      :options="personaOptions"
      :selected-value="persona"
      @select="onPersonaSheetSelect"
    />
  </view>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { getMpSafeAreaMetrics } from '@/utils/safeArea';
import { requireAuth } from '@/utils/auth';
import request from '@/utils/request';
import { useTheme } from '@/utils/theme';
import { useI18n } from '@/locales';
import SlActionSheet from '@/style-library/components/SlActionSheet.vue';

const { t } = useI18n();
const { themeClass, fontClass, refresh: refreshTheme } = useTheme();

interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
  isTyping?: boolean;
}

interface AssistantMessage {
  msgId: number;
  sessionId: number;
  role: 'user' | 'assistant' | 'system' | 'USER' | 'ASSISTANT' | 'SYSTEM';
  content: string;
  createdAt?: string;
}

type PersonaKey = 'MENTOR' | 'CHALLENGER' | 'INTERVIEWER';

// F15: persona definitions — 所有文案通过 t() 读取，随语言切换自动变化
const PERSONAS: { key: PersonaKey; emoji: string; label: string; name: string; tagline: string; intro: string; bestFor: string; note: string; gradient: string; chips: string[] }[] = [
  {
    key: 'MENTOR',
    emoji: 'ri-compass-3-line',
    label: '求职教练',
    name: t('assistantPage.personas.MENTOR.name'),
    tagline: t('assistantPage.personas.MENTOR.tagline'),
    intro: t('assistantPage.personas.MENTOR.intro'),
    bestFor: t('assistantPage.personas.MENTOR.bestFor'),
    note: t('assistantPage.personas.MENTOR.note'),
    gradient: 'linear-gradient(135deg, #2563eb, #8b5cf6)',
    chips: [
      t('assistantPage.personas.MENTOR.chip1'),
      t('assistantPage.personas.MENTOR.chip2'),
      t('assistantPage.personas.MENTOR.chip3'),
    ],
  },
  {
    key: 'CHALLENGER',
    emoji: 'ri-shield-star-line',
    label: '严格反馈',
    name: t('assistantPage.personas.CHALLENGER.name'),
    tagline: t('assistantPage.personas.CHALLENGER.tagline'),
    intro: t('assistantPage.personas.CHALLENGER.intro'),
    bestFor: t('assistantPage.personas.CHALLENGER.bestFor'),
    note: t('assistantPage.personas.CHALLENGER.note'),
    gradient: 'linear-gradient(135deg, #dc2626, #f97316)',
    chips: [
      t('assistantPage.personas.CHALLENGER.chip1'),
      t('assistantPage.personas.CHALLENGER.chip2'),
      t('assistantPage.personas.CHALLENGER.chip3'),
    ],
  },
  {
    key: 'INTERVIEWER',
    emoji: 'ri-mic-line',
    label: '面试练习',
    name: t('assistantPage.personas.INTERVIEWER.name'),
    tagline: t('assistantPage.personas.INTERVIEWER.tagline'),
    intro: t('assistantPage.personas.INTERVIEWER.intro'),
    bestFor: t('assistantPage.personas.INTERVIEWER.bestFor'),
    note: t('assistantPage.personas.INTERVIEWER.note'),
    gradient: 'linear-gradient(135deg, #7c3aed, #a78bfa)',
    chips: [
      t('assistantPage.personas.INTERVIEWER.chip1'),
      t('assistantPage.personas.INTERVIEWER.chip2'),
      t('assistantPage.personas.INTERVIEWER.chip3'),
    ],
  },
];

const persona = ref<PersonaKey>('MENTOR');
const currentPersona = computed(() => PERSONAS.find(p => p.key === persona.value) || PERSONAS[0]);
const showPersonaSheet = ref(false);
const personaOptions = computed(() => PERSONAS.map((p) => ({
  label: p.label,
  value: p.key,
  subtitle: p.bestFor,
  icon: p.emoji,
})));

const messages = ref<ChatMessage[]>([]);
const apiHistory = ref<{ role: string; content: string }[]>([]);

const inputText = ref('');
const scrollTop = ref(0);
const topSafeHeight = ref(88);
const rightAvoidWidth = ref(20);
const isSending = ref(false);
const isLoadingSession = ref(false);
const chatTimeLabel = ref('');
const sessionId = ref<number | null>(null);
let requestGeneration = 0;
const canSend = computed(() =>
  inputText.value.trim().length > 0 && !isSending.value && !isLoadingSession.value
);

const onListScroll = (event: any) => {
  // scrolling logic if needed
};

const openHistory = () => {
  if (!requireAuth({ message: '登录后才能查看和保存 AI 导师对话记录。' })) return;
  uni.navigateTo({ url: '/pages/assistant/history' });
};

const isPersonaKey = (value: string): value is PersonaKey =>
  value === 'MENTOR' || value === 'CHALLENGER' || value === 'INTERVIEWER';

const normalizeChatRole = (role: AssistantMessage['role']): ChatMessage['role'] | 'system' => {
  const normalized = String(role || '').toLowerCase();
  if (normalized === 'user') return 'user';
  if (normalized === 'system') return 'system';
  return 'assistant';
};

const loadSessionMessages = async (sid: number, selectedPersona?: string) => {
  const generation = ++requestGeneration;
  isSending.value = false;
  isLoadingSession.value = true;
  const nextPersona = selectedPersona && isPersonaKey(selectedPersona) ? selectedPersona : persona.value;
  persona.value = nextPersona;
  sessionId.value = sid;
  try {
    const res = await request<AssistantMessage[]>({
      url: `/api/chat/history/session/${sid}`,
      method: 'GET',
    });
    if (generation !== requestGeneration || sessionId.value !== sid) return;
    const rows = Array.isArray(res) ? res.filter((m) => normalizeChatRole(m.role) !== 'system') : [];
    messages.value = rows.length > 0
      ? rows.map((m) => ({ role: normalizeChatRole(m.role) === 'user' ? 'user' : 'assistant', content: m.content }))
      : [{ role: 'assistant', content: currentPersona.value.intro }];
    apiHistory.value = rows.map((m) => ({
      role: normalizeChatRole(m.role) === 'assistant' ? 'assistant' : 'user',
      content: m.content,
    }));
    scrollToBottom();
  } catch (e: any) {
    if (generation !== requestGeneration) return;
    uni.showToast({ title: e?.message || t('assistantPage.openHistoryFailed'), icon: 'none' });
  } finally {
    if (generation === requestGeneration) {
      isLoadingSession.value = false;
    }
  }
};

const openPendingSessionIfAny = () => {
  const pending = uni.getStorageSync('assistantOpenSession');
  if (!pending?.sessionId) return;
  uni.removeStorageSync('assistantOpenSession');
  loadSessionMessages(Number(pending.sessionId), pending.persona);
};

const switchPersona = (key: PersonaKey) => {
  if (persona.value === key) return;
  requestGeneration += 1;
  isSending.value = false;
  isLoadingSession.value = false;
  persona.value = key;
  sessionId.value = null;
  // Clear history for fresh start with new persona
  apiHistory.value = [];
  messages.value = [
    { role: 'assistant', content: currentPersona.value.intro },
  ];
};

const onPersonaSheetSelect = (payload: { value: string }) => {
  if (isPersonaKey(payload.value)) switchPersona(payload.value);
};

const scrollToBottom = () => {
  nextTick(() => {
    scrollTop.value = scrollTop.value === 99998 ? 99999 : 99998;
  });
};

const sendQuick = (text: string) => {
  inputText.value = text;
  sendMessage();
};

const sendMessage = async () => {
  const text = inputText.value.trim();
  if (!text || isSending.value || isLoadingSession.value) return;
  if (!requireAuth({ message: '登录后才能与 AI 导师对话并保存成长记录。' })) return;

  const generation = ++requestGeneration;
  const requestPersona = persona.value;
  const historySnapshot = apiHistory.value.map((message) => ({ ...message }));
  const targetMessages = messages.value;
  messages.value.push({ role: 'user', content: text });
  inputText.value = '';
  isSending.value = true;
  scrollToBottom();

  const typingIdx = messages.value.length;
  messages.value.push({ role: 'assistant', content: '', isTyping: true });
  scrollToBottom();

  try {
    const sid = await ensureSession(requestPersona, generation);
    const res = await request<{ reply: string }>({
      url: '/api/chat/send',
      method: 'POST',
      data: {
        message: text,
        history: historySnapshot,
        persona: requestPersona,
        sessionId: sid,
      },
    });
    const reply = (res as any)?.reply ?? res ?? '';
    if (sid) {
      await appendMessage(sid, text, String(reply || ''), requestPersona);
    }
    if (generation !== requestGeneration || messages.value !== targetMessages) return;
    messages.value[typingIdx] = { role: 'assistant', content: reply || t('assistantPage.noResponse') };
    apiHistory.value.push(
      { role: 'user', content: text },
      { role: 'assistant', content: String(reply) },
    );
  } catch {
    if (generation !== requestGeneration || messages.value !== targetMessages) return;
    messages.value[typingIdx] = {
      role: 'assistant',
      content: t('assistantPage.requestFailed'),
    };
  } finally {
    if (generation === requestGeneration) {
      isSending.value = false;
      scrollToBottom();
    }
  }
};

const ensureSession = async (requestPersona: PersonaKey, generation: number) => {
  if (sessionId.value) return sessionId.value;
  try {
    const session = await request<{ sessionId: number }>({
      url: '/api/chat/history/create',
      method: 'POST',
      data: { title: t('assistantHistory.newConversation'), persona: requestPersona },
    });
    if (generation === requestGeneration && persona.value === requestPersona) {
      sessionId.value = session.sessionId;
    }
    return session.sessionId;
  } catch {
    return null;
  }
};

const appendMessage = async (
  sid: number,
  userMessage: string,
  assistantReply: string,
  requestPersona: PersonaKey,
) => {
  try {
    await request({
      url: `/api/chat/history/session/${sid}/append`,
      method: 'POST',
      data: { userMessage, assistantReply, persona: requestPersona },
    });
  } catch { }
};

onMounted(() => {
  refreshTheme();
  const safeMetrics = getMpSafeAreaMetrics();
  topSafeHeight.value = safeMetrics.topSafeHeight;
  rightAvoidWidth.value = safeMetrics.rightAvoidWidth;
  const now = new Date();
  const timeStr = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
  chatTimeLabel.value = t('messages.timeToday', { time: timeStr });
  // Initialise with persona greeting
  messages.value = [{ role: 'assistant', content: currentPersona.value.intro }];
  openPendingSessionIfAny();
});

onShow(() => {
  refreshTheme();
  openPendingSessionIfAny();
});
</script>

<style scoped>
.chat-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--surface-1, #ffffff);
  font-family: -apple-system, BlinkMacSystemFont, "SF Pro Text", "Helvetica Neue", sans-serif;
}

/* ---- Nav bar ---- */
.chat-nav {
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  border-bottom: 0.5px solid rgba(60, 60, 67, 0.12);
  flex-shrink: 0;
  z-index: 10;
}

.nav-spacer {
  width: 100%;
}

.nav-row {
  display: flex;
  align-items: center;
  padding: 10px 20px 12px;
}

.nav-bot-avatar {
  width: 38px;
  height: 38px;
  border-radius: 19px;
  background: linear-gradient(135deg, #2563eb, #60a5fa);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 12px;
  flex-shrink: 0;
}

.nav-bot-emoji {
  font-size: 20px;
  color: #ffffff;
}

.nav-meta {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
}

.nav-name {
  font-size: var(--font-section, 17px);
  font-weight: 700;
  color: var(--text-primary, #0f172a);
  letter-spacing: -0.3px;
}

.nav-status-row {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-top: 2px;
}

.online-dot {
  width: 6px;
  height: 6px;
  border-radius: 3px;
  background: #22c55e;
}

.nav-status {
  font-size: 12px;
  color: var(--text-secondary, #64748b);
  line-height: 1.35;
}

.nav-action {
  flex-shrink: 0;
  background: var(--primary-soft, #eff6ff);
  border-radius: 50%;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.nav-action:active {
  background: #dbeafe;
}

.nav-action-icon {
  font-size: 16px;
  color: var(--primary-color, #2563eb);
}

/* ---- Persona bar ---- */
.persona-bar {
  display: flex;
  padding: 8px 16px 12px;
  gap: 8px;
}
.persona-chip {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 6px 14px;
  background: rgba(255, 255, 255, 0.6);
  border: 1.5px solid rgba(60, 60, 67, 0.12);
  border-radius: 6px;
  transition: all 0.2s;
}
.persona-chip:active { opacity: 0.75; }
.persona-active {
  background: var(--primary-soft, #eff6ff);
  border-color: var(--primary-color, #2563eb);
}
.persona-label { font-size: 13px; font-weight: 600; color: var(--text-secondary, #64748b); }
.persona-active .persona-label { color: var(--primary-color, #2563eb); }



/* ---- Chat list ---- */
.chat-list {
  flex: 1;
  min-height: 0;
  height: 0;
  box-sizing: border-box;
}

.chat-list-surface {
  min-height: 100%;
  padding: 16px 16px 0;
  box-sizing: border-box;
  background-color: var(--surface-1, #ffffff);
}

.scroll-pad {
  height: 140px;
}

/* ---- Welcome card ---- */
.welcome-card {
  background: var(--card-bg, #ffffff);
  border-radius: var(--radius-lg, 20px);
  padding: 24px 20px;
  margin-bottom: 20px;
  border: 1px solid var(--border-color, #e2e8f0);
  box-shadow: var(--shadow-sm);
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.welcome-brand {
  display: block;
  margin-bottom: 12px;
  color: var(--brand-color, #3f51b5);
  font-size: 10px;
  line-height: 1.3;
  font-weight: 800;
  letter-spacing: 0.06em;
}

.welcome-icon {
  font-size: 36px;
  margin-bottom: 12px;
}

.welcome-title {
  font-size: 17px;
  font-weight: 700;
  color: var(--text-primary, #0f172a);
  margin-bottom: 8px;
}

.welcome-desc {
  font-size: 13px;
  color: var(--text-secondary, #64748b);
  line-height: 1.55;
  margin-bottom: 14px;
}

.agent-scope {
  width: 100%;
  background: var(--surface-2, #f8fafc);
  border: 1px solid var(--border-color, #e2e8f0);
  border-radius: var(--radius-sm, 12px);
  padding: 10px 12px;
  box-sizing: border-box;
  margin-bottom: 10px;
  text-align: left;
}

.agent-scope-label {
  display: block;
  font-size: 11px;
  font-weight: 800;
  color: var(--primary-color, #2563eb);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  margin-bottom: 4px;
}

.agent-scope-text {
  display: block;
  font-size: 12px;
  line-height: 1.45;
  color: var(--text-secondary, #64748b);
}

.agent-note {
  width: 100%;
  background: var(--primary-soft, #eff6ff);
  border-radius: var(--radius-sm, 12px);
  padding: 9px 12px;
  box-sizing: border-box;
  margin-bottom: 18px;
  text-align: left;
}

.agent-note-text {
  font-size: 12px;
  line-height: 1.45;
  color: var(--primary-hover, #1d4ed8);
}

.quick-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
}

.quick-chip {
  background: var(--primary-soft, #eff6ff);
  border-radius: var(--radius-lg, 20px);
  padding: 8px 16px;
  transition: all 0.15s;
}

.quick-chip:active {
  background: var(--primary-mid, #3b82f6);
  transform: scale(0.96);
}

.quick-chip:active .chip-text {
  color: #ffffff;
}

.chip-text {
  font-size: 13px;
  font-weight: 500;
  color: var(--primary-color, #2563eb);
}

/* ---- Time divider ---- */
.time-divider {
  display: flex;
  justify-content: center;
  margin: 16px 0;
}

.time-text {
  font-size: 11px;
  color: var(--text-tertiary, #8e8e93);
  background: rgba(142, 142, 147, 0.12);
  padding: 3px 12px;
  border-radius: 10px;
}

/* ---- Messages ---- */
.msg-row {
  display: flex;
  margin-bottom: 20px;
  align-items: flex-start;
}

.row-user {
  justify-content: flex-end;
}

.bot-avatar {
  width: 34px;
  height: 34px;
  border-radius: 17px;
  background: var(--card-bg, #ffffff);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 10px;
  margin-top: 2px;
  flex-shrink: 0;
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--border-color, #e2e8f0);
}

.bot-emoji {
  font-size: 18px;
}

.bubble-area {
  max-width: 72%;
  min-width: 0;
}

.row-user .bubble-area {
  display: flex;
  justify-content: flex-end;
  max-width: 78%;
}

.bubble {
  padding: 12px 16px;
  font-size: 15px;
  line-height: 1.55;
  overflow-wrap: break-word;
  word-break: normal;
}

.bubble-ai {
  background: var(--card-bg, #ffffff);
  color: var(--text-primary, #0f172a);
  border-radius: 4px 20px 20px 20px;
  border: 1px solid var(--border-color, #e2e8f0);
  box-shadow: var(--shadow-sm);
}

.bubble-user {
  background: var(--gradient-primary, linear-gradient(135deg, #2563eb 0%, #8b5cf6 100%));
  color: #ffffff;
  border-radius: 20px 4px 20px 20px;
  box-shadow: var(--shadow-sm);
  border: none;
}

.bubble-text {
  display: block;
}

.bubble-user .bubble-text {
  color: #ffffff !important;
}

/* ---- Typing dots ---- */
.typing-dots {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 4px 6px;
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 3px;
  background: #8e8e93;
  animation: bounce 1.4s infinite ease-in-out both;
}

.dot:nth-child(1) { animation-delay: -0.32s; }
.dot:nth-child(2) { animation-delay: -0.16s; }

@keyframes bounce {
  0%, 80%, 100% { transform: scale(0); }
  40% { transform: scale(1); }
}

/* ---- Input bar ---- */
.input-bar {
  background: rgba(245, 245, 247, 0.92);
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  border-top: 0.5px solid rgba(60, 60, 67, 0.1);
  padding: 8px 16px 10px;
  position: fixed;
  bottom: var(--window-bottom, 0);
  left: 0;
  right: 0;
  z-index: 10;
  box-sizing: border-box;
}

.mode-caption {
  display: block;
  margin: 0 0 6px 4px;
  font-size: 11px;
  line-height: 1.2;
  color: var(--text-tertiary, #8e8e93);
}

.input-row {
  display: flex;
  align-items: center;
  width: 100%;
  min-width: 0;
  background: var(--card-bg, #ffffff);
  border-radius: 24px;
  padding: 4px 4px 4px 6px;
  border: 1px solid var(--border-color, #e2e8f0);
  box-shadow: var(--shadow-sm);
}

.mode-picker-wrap {
  flex-shrink: 0;
}

.mode-picker {
  height: 36px;
  width: 96px;
  max-width: 96px;
  padding: 0 8px;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  gap: 4px;
  border-radius: 18px;
  background: var(--primary-soft, #eff6ff);
  color: var(--primary-color, #2563eb);
}

.mode-picker-icon,
.mode-picker-arrow {
  flex-shrink: 0;
  font-size: 15px;
}

.mode-picker-text {
  min-width: 0;
  font-size: 12px;
  font-weight: 800;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-input {
  flex: 1 1 0;
  width: 0;
  min-width: 0;
  height: 40px;
  font-size: 15px;
  color: var(--text-primary, #0f172a);
  background: transparent;
  border: none;
  outline: none;
  padding-left: 8px;
}

.input-ph {
  color: var(--text-tertiary, #8e8e93);
  font-size: 15px;
}

.send-btn {
  width: 58px;
  min-width: 58px;
  height: 36px;
  padding: 0;
  box-sizing: border-box;
  border-radius: 18px;
  background: #e5e5ea;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-left: 6px;
  transition: background 0.2s;
  flex-shrink: 0;
}

.send-active {
  background: var(--primary-color, #2563eb);
  border: none;
}

.send-disabled {
  opacity: 0.72;
}

.send-label {
  color: var(--text-tertiary, #8e8e93);
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 0.02em;
}
.send-active .send-label {
  color: #ffffff !important;
}

/* ---- Dark mode ---- */
.is-dark {
  background: #0f172a;
}

.is-dark .chat-nav {
  background: rgba(15, 23, 42, 0.88);
  border-color: var(--text-secondary, #64748b);
}

.is-dark .nav-name,
.is-dark .welcome-title {
  color: #f8fafc;
}

.is-dark .welcome-desc,
.is-dark .nav-status,
.is-dark .agent-scope-text {
  color: var(--text-tertiary, #8e8e93);
}

.is-dark .agent-scope {
  background: #0f172a;
  border-color: var(--text-secondary, #64748b);
}

.is-dark .agent-note {
  background: rgba(37, 99, 235, 0.14);
}

.is-dark .agent-note-text {
  color: #93c5fd;
}

.is-dark .welcome-card,
.is-dark .bubble-ai,
.is-dark .bot-avatar {
  background: #1e293b;
  box-shadow: none;
}

.is-dark .bubble-ai {
  color: #f8fafc;
}

.is-dark .input-bar {
  background: rgba(15, 23, 42, 0.92);
  border-color: var(--text-secondary, #64748b);
}

.is-dark .topbar-history {
  background: rgba(37, 99, 235, 0.2);
  color: #93c5fd;
}

.is-dark .input-row {
  background: #1e293b;
  border-color: var(--text-secondary, #64748b);
}

.is-dark .mode-picker {
  background: rgba(37, 99, 235, 0.2);
  color: #93c5fd;
}

.is-dark .chat-input {
  color: #f8fafc;
}

.is-dark .quick-chip { background: rgba(37, 99, 235, 0.15); }
.is-dark .quick-chip:active { background: rgba(37, 99, 235, 0.25); }
.is-dark .chip-text { color: #60a5fa; }
.is-dark .nav-action { background: rgba(37, 99, 235, 0.16); }
.is-dark .nav-action:active { background: rgba(37, 99, 235, 0.26); }
.is-dark .persona-chip { background: rgba(30, 41, 59, 0.7); border-color: #334155; }
.is-dark .persona-active {
  background: #2563eb;
  border-color: #93c5fd;
  box-shadow: 0 0 0 1px rgba(147, 197, 253, 0.42) inset;
}
.is-dark .persona-label { color: var(--text-tertiary, #8e8e93); }
.is-dark .persona-active .persona-label,
.is-dark .persona-active .persona-emoji { color: #ffffff; }
.is-dark .time-text { color: var(--text-tertiary, #8e8e93); background: rgba(100, 116, 139, 0.15); }
.is-dark .send-btn { background: #334155; }
.is-dark .send-label { color: var(--text-secondary, #64748b); }
.is-dark .send-active { background: var(--primary-color, #2563eb) !important; }
.is-dark .send-active .send-label { color: #ffffff !important; }
.is-dark .dot { background: #64748b; }

.is-dark .chat-list-surface {
  background-color: var(--bg-color, #0f172a);
}

.theme-green .chat-list-surface {
  background-color: #f0fdf4;
}

/* ================================================================
 *  MP-WEIXIN parity overrides (scoped to assistant/chat page)
 * ================================================================ */
/* #ifdef MP-WEIXIN */

/* backdrop-filter unsupported — use solid background for input bar */
.input-bar {
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
  background: var(--surface-1, #ffffff);
}

/* Stronger border on the pill-shaped input row */
.input-row {
  border: 1.5px solid #e2e8f0;
  box-shadow: var(--shadow-xs);
}

/* Chat nav: solid white instead of frosted */
.chat-nav {
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
  background: #ffffff;
  border-bottom: 1px solid #d0dae4;
}

/* Welcome card: content card gets depth */
.welcome-card {
  overflow: visible;
  border: 1px solid #b8c8d8;
  box-shadow: var(--shadow-sm);
}

.is-dark .chat-nav,
.is-dark .input-bar {
  background: #0f172a;
  border-color: var(--text-secondary, #64748b);
}

.is-dark .welcome-card,
.is-dark .bubble-ai,
.is-dark .bot-avatar,
.is-dark .input-row {
  background: #1e293b;
  border-color: var(--text-secondary, #64748b);
}

.is-dark .persona-chip {
  background: #1e293b;
  border-color: #334155;
}

.is-dark .persona-label {
  color: #cbd5e1;
}

.is-dark .persona-active {
  background: #2563eb;
  border-color: #93c5fd;
}

.is-dark .persona-active .persona-label,
.is-dark .persona-active .persona-emoji {
  color: #ffffff;
}

.chat-page.is-dark .input-bar {
  background: rgba(15, 23, 42, 0.96);
  border-color: var(--text-secondary, #64748b);
}

.chat-page.is-dark .chat-nav {
  background: #0f172a;
  border-bottom-color: var(--text-secondary, #64748b);
}

.chat-page.is-dark .input-row {
  background: #1e293b;
  border-color: var(--text-secondary, #64748b);
  box-shadow: none;
}

.chat-list {
  background-color: var(--surface-1, #ffffff);
}

.chat-page.is-dark .chat-list,
.chat-page.is-dark .chat-list-surface {
  background-color: var(--bg-color, #0f172a);
}

.chat-page.theme-green .chat-list,
.chat-page.theme-green .chat-list-surface {
  background-color: #f0fdf4;
}

/* #endif */

/* ================================================================
 * CareerLoop editorial skin
 * Mirrors the website's warm-paper, neo-Chinese visual language.
 * This block intentionally sits after the MP overrides so WeChat and
 * H5 resolve to the same final palette.
 * ================================================================ */
.chat-page {
  background: #faf9f6;
  color: #2c2b29;
  font-family: "Noto Sans SC", "PingFang SC", "Microsoft YaHei", sans-serif;
}

.chat-nav {
  background: rgba(250, 249, 246, 0.96);
  border-bottom: 1px solid #e0dfdb;
  box-shadow: none;
}

.nav-row {
  padding-top: 9px;
  padding-bottom: 10px;
}

.nav-bot-avatar {
  width: 38px;
  height: 38px;
  border-radius: 6px;
  background: #3f51b5 !important;
  margin-right: 12px;
}

.nav-name,
.welcome-title {
  color: #2c2b29;
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
  font-weight: 600;
  letter-spacing: 0.05em;
}

.nav-status {
  color: #5a5956;
}

.online-dot {
  border-radius: 1px;
  background: #7b8d6e;
}

.nav-action {
  width: 36px;
  height: 36px;
  border: 1px solid #e0dfdb;
  border-radius: 6px;
  background: #f5f5f0;
}

.nav-action:active {
  background: #efeee9;
}

.nav-action-icon {
  color: #3f51b5;
}

.chat-list,
.chat-list-surface {
  background: #faf9f6;
}

.chat-list-surface {
  padding: 16px 16px 0;
}

.welcome-card {
  align-items: flex-start;
  text-align: left;
  padding: 20px 18px;
  margin-bottom: 18px;
  border: 1px solid #e0dfdb;
  border-radius: 8px;
  background: #faf9f6;
  box-shadow: 0 6px 18px rgba(44, 43, 41, 0.05);
}

.welcome-brand {
  margin-bottom: 14px;
  color: #c23b22;
  font-weight: 600;
  letter-spacing: 0.08em;
}

.welcome-icon {
  margin-bottom: 10px;
  color: #3f51b5;
}

.welcome-title {
  font-size: 18px;
}

.welcome-desc {
  color: #5a5956;
  line-height: 1.75;
}

.agent-scope {
  border: 1px solid #e0dfdb;
  border-radius: 6px;
  background: #f5f5f0;
}

.agent-scope-label {
  color: #3f51b5;
  font-weight: 600;
  letter-spacing: 0.06em;
}

.agent-scope-text {
  color: #5a5956;
  line-height: 1.65;
}

.agent-note {
  border-left: 2px solid #3f51b5;
  border-radius: 0 4px 4px 0;
  background: #f5f5f0;
}

.agent-note-text {
  color: #5a5956;
}

.quick-actions {
  justify-content: flex-start;
  gap: 8px;
}

.quick-chip {
  min-height: 40px;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  padding: 8px 13px;
  border: 1px solid #e0dfdb;
  border-radius: 6px;
  background: transparent;
}

.quick-chip:active {
  border-color: #c23b22;
  background: rgba(194, 59, 34, 0.08);
  transform: scale(0.98);
}

.chip-text,
.quick-chip:active .chip-text {
  color: #2c2b29;
  font-weight: 400;
}

.time-divider {
  margin: 14px 0;
}

.time-text {
  padding: 2px 8px;
  border-radius: 3px;
  background: #efeee9;
  color: #8b8a86;
}

.msg-row {
  margin-bottom: 16px;
}

.bot-avatar {
  border: 1px solid #e0dfdb;
  border-radius: 6px;
  background: #f5f5f0;
  box-shadow: none;
}

.bot-emoji {
  color: #3f51b5;
}

.bubble {
  padding: 11px 14px;
  line-height: 1.65;
}

.bubble-ai {
  border: 1px solid #e0dfdb;
  border-radius: 4px 8px 8px 8px;
  background: #faf9f6;
  color: #2c2b29;
  box-shadow: none;
}

.bubble-user {
  border: 1px solid #c23b22;
  border-radius: 8px 4px 8px 8px;
  background: #c23b22;
  box-shadow: none;
}

.input-bar {
  padding: 8px 16px calc(10px + env(safe-area-inset-bottom, 0px));
  border-top: 1px solid #e0dfdb;
  background: rgba(250, 249, 246, 0.97);
}

.mode-caption {
  color: #8b8a86;
  letter-spacing: 0.04em;
}

.input-row {
  padding: 4px;
  border: 1px solid #d4d2cc;
  border-radius: 8px;
  background: #faf9f6;
  box-shadow: 0 4px 14px rgba(44, 43, 41, 0.04);
}

.mode-picker {
  height: 40px;
  border-right: 1px solid #e0dfdb;
  border-radius: 4px 0 0 4px;
  background: #f5f5f0;
  color: #3f51b5;
}

.mode-picker-text {
  font-weight: 500;
  letter-spacing: 0.03em;
}

.chat-input {
  height: 44px;
  color: #2c2b29;
}

.input-ph {
  color: #b0afab;
}

.send-btn {
  width: 62px;
  min-width: 62px;
  height: 40px;
  border: 1px solid #e0dfdb;
  border-radius: 6px;
  background: #efeee9;
}

.send-active {
  border-color: #c23b22;
  background: #c23b22;
}

.send-label {
  color: #8b8a86;
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
  font-weight: 600;
  letter-spacing: 0.05em;
}

.chat-page.is-dark {
  background: #1f1f1d;
  color: #f5f5f0;
}

.chat-page.is-dark .chat-nav,
.chat-page.is-dark .input-bar {
  background: #1f1f1d;
  border-color: #45443f;
}

.chat-page.is-dark .chat-list,
.chat-page.is-dark .chat-list-surface {
  background: #1f1f1d;
}

.chat-page.is-dark .welcome-card,
.chat-page.is-dark .bubble-ai,
.chat-page.is-dark .bot-avatar,
.chat-page.is-dark .input-row {
  background: #292926;
  border-color: #45443f;
  box-shadow: none;
}

.chat-page.is-dark .nav-name,
.chat-page.is-dark .welcome-title,
.chat-page.is-dark .bubble-ai,
.chat-page.is-dark .chat-input {
  color: #f5f5f0;
}

.chat-page.is-dark .nav-status,
.chat-page.is-dark .welcome-desc,
.chat-page.is-dark .agent-scope-text {
  color: #c4c2bc;
}

.chat-page.is-dark .agent-scope,
.chat-page.is-dark .agent-note,
.chat-page.is-dark .mode-picker,
.chat-page.is-dark .nav-action {
  background: #33332f;
  border-color: #45443f;
}

.chat-page.is-dark .agent-note-text {
  color: #c4c2bc;
}

.chat-page.is-dark .quick-chip {
  background: transparent;
  border-color: #575650;
}

.chat-page.is-dark .chip-text,
.chat-page.is-dark .quick-chip:active .chip-text {
  color: #f5f5f0;
}

.chat-page.is-dark .send-btn {
  border-color: #45443f;
  background: #33332f;
}

.chat-page.is-dark .send-active {
  border-color: #c23b22;
  background: #c23b22 !important;
}

.chat-page.is-dark .persona-chip.persona-active {
  border-color: #aab3ea !important;
  background: rgba(63, 81, 181, 0.34) !important;
}

.chat-page.is-dark .persona-chip.persona-active .persona-label,
.chat-page.is-dark .persona-chip.persona-active .persona-emoji {
  color: #f8fafc !important;
}
</style>
