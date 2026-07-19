<template>
  <SlPage class="app-soft-bg" :custom-class="['chat-container', themeClass, fontClass].join(' ')">
    <SlNavBar
      :title="t('interviewChat.navTitle')"
      show-back
      @back="goBack"
      :safe-top="topSafeHeight"
      :right-avoid-width="rightAvoidWidth"
    >
      <template #right>
        <view class="end-link" @click="endInterview" hover-class="press-fb" hover-stay-time="120">
          <text class="end-link-text">{{ t('interviewChat.endBtn') }}</text>
        </view>
      </template>
    </SlNavBar>

    <view class="interview-info" v-if="interview">
      <view class="session-copy">
        <text class="position">{{ interview.positionName }}</text>
        <text class="session-text">{{ t('interviewChat.sessionCopy') }}</text>
      </view>
      <view class="info-actions">
        <text class="difficulty">{{ interview.difficulty }}</text>
      </view>
    </view>

    <scroll-view scroll-y class="chat-area" :scroll-top="99999" :scroll-with-animation="true">
      <view class="chat-area-surface">
      <view v-for="(msg, index) in messages" :key="index" :class="['message', msg.role.toLowerCase()]">
        <view class="msg-content">
          <text>{{ msg.content }}</text>
        </view>
      </view>
      <view v-if="aiTyping" class="message ai">
        <view class="msg-content typing">
          <view class="typing-dots">
            <view class="dot"></view>
            <view class="dot"></view>
            <view class="dot"></view>
          </view>
          <text v-if="typingElapsed > 2" class="typing-timer">{{ typingElapsed }}s</text>
        </view>
      </view>
      </view>
    </scroll-view>

    <view class="input-area">
      <input 
        class="chat-input ui-input" 
        v-model="inputText" 
        :placeholder="t('interviewChat.inputPlaceholder')" 
        placeholder-class="ph"
        @confirm="sendMessage"
      />
      <view 
        class="send-btn" 
        :class="{ 'send-active': inputText.trim() && !aiTyping }"
        @click="sendMessage"
      >
        <text class="send-label">{{ t('interviewChat.sendBtn') }}</text>
      </view>
    </view>
  </SlPage>
</template>

<script setup lang="ts">
import { ref, onBeforeUnmount, onMounted } from 'vue';
import { useI18n } from '@/locales';
import { onShow } from '@dcloudio/uni-app';
import {
  getInterviewByIdApi,
  getInterviewMessagesApi,
  sendInterviewMessageApi,
  endInterviewApi,
  generateGreetingApi,
} from '@/api/interview';
import type { Interview, InterviewMessage } from '@/api/interview';
import { useTheme } from '@/utils/theme';
import { getMpSafeAreaMetrics } from '@/utils/safeArea';
import SlPage from '@/style-library/components/SlPage.vue';
import SlNavBar from '@/style-library/components/SlNavBar.vue';

const interviewId = ref<number>(0);
const interview = ref<Interview | null>(null);
const messages = ref<InterviewMessage[]>([]);
const inputText = ref('');
const aiTyping = ref(false);
const ending = ref(false);
const typingElapsed = ref(0);
let typingTimer: any = null;
let pageActive = true;
let turnGeneration = 0;
const { t } = useI18n();
const interviewLang = (uni.getStorageSync('interview_language') as string) || 'zh';
const { themeClass, fontClass, refresh: refreshTheme } = useTheme();
const topSafeHeight = ref(52);
const rightAvoidWidth = ref(20);

const startTypingTimer = () => {
  typingElapsed.value = 0;
  if (typingTimer) clearInterval(typingTimer);
  typingTimer = setInterval(() => {
    typingElapsed.value += 1;
  }, 1000);
};

const stopTypingTimer = () => {
  if (typingTimer) {
    clearInterval(typingTimer);
    typingTimer = null;
  }
  typingElapsed.value = 0;
};
onMounted(async () => {
  const loadGeneration = turnGeneration;
  refreshTheme();
  const safeMetrics = getMpSafeAreaMetrics();
  topSafeHeight.value = safeMetrics.topSafeHeight;
  rightAvoidWidth.value = safeMetrics.rightAvoidWidth;
  const pages = getCurrentPages();
  const currentPage = pages[pages.length - 1] as any;
  interviewId.value = parseInt(currentPage.options?.interviewId || '0');

  if (interviewId.value) {
    try {
      const loadedInterview = await getInterviewByIdApi(interviewId.value);
      if (!pageActive || loadGeneration !== turnGeneration) return;
      interview.value = loadedInterview;
      if (loadedInterview.status !== 'ONGOING') {
        const target = loadedInterview.status === 'COMPLETED'
          ? `/pages/interview/report?interviewId=${interviewId.value}`
          : '/pages/interview/history';
        uni.redirectTo({ url: target });
        return;
      }
      const loadedMessages = await getInterviewMessagesApi(interviewId.value);
      if (!pageActive || loadGeneration !== turnGeneration) return;
      messages.value = loadedMessages;
      
      // First time entering this session: ask the backend to generate the
      // opening question. The endpoint is idempotent —
      // if a greeting already exists it just returns it.
      if (messages.value.length === 0) {
        aiTyping.value = true;
        startTypingTimer();
        try {
          const greeting = await generateGreetingApi(interviewId.value, interviewLang);
          if (!pageActive || loadGeneration !== turnGeneration) return;
          if (greeting) messages.value = [greeting];
        } catch {
          if (!pageActive || loadGeneration !== turnGeneration) return;
          uni.showToast({ title: t('interviewChat.greetingFailed'), icon: 'none', duration: 3000 });
        } finally {
          if (pageActive && loadGeneration === turnGeneration) {
            aiTyping.value = false;
            stopTypingTimer();
          }
        }
      }
    } catch (error: any) {
      if (!pageActive || loadGeneration !== turnGeneration) return;
      console.error('Failed to load interview:', error);
      uni.showToast({ title: error?.message || t('interviewRoom.loadFailed'), icon: 'none' });
    }
  }
});

onBeforeUnmount(() => {
  pageActive = false;
  turnGeneration += 1;
  stopTypingTimer();
});

onShow(() => {
  refreshTheme();
  uni.setNavigationBarTitle({ title: t('interviewChat.navTitle') });
});

const sendMessage = async () => {
  const text = inputText.value.trim();
  if (!text || aiTyping.value) return;

  const generation = ++turnGeneration;
  messages.value.push({ interviewId: interviewId.value, role: 'USER', content: text });
  inputText.value = '';
  aiTyping.value = true;
  startTypingTimer();

  try {
    const response = await sendInterviewMessageApi(interviewId.value, text, interviewLang);
    if (!pageActive || generation !== turnGeneration) return;
    messages.value.push({ interviewId: interviewId.value, role: 'AI', content: response.aiMessage });
  } catch (error) {
    if (!pageActive || generation !== turnGeneration) return;
    console.error('Failed to send message:', error);
    uni.showToast({ title: t('interviewChat.sendFailed'), icon: 'none' });
  } finally {
    if (pageActive && generation === turnGeneration) {
      aiTyping.value = false;
      stopTypingTimer();
    }
  }
};

const endInterview = () => {
  if (ending.value) return;
  if (aiTyping.value) {
    uni.showToast({ title: t('interviewRoom.statusThinking'), icon: 'none' });
    return;
  }
  const hasUserAnswer = messages.value.some((m) =>
    String(m.role || '').toUpperCase() === 'USER' && String(m.content || '').trim().length > 0
  );
  if (!hasUserAnswer) {
    uni.showModal({
      title: t('interviewRoom.noAnswerTitle'),
      content: t('interviewRoom.noAnswerContent'),
      confirmText: t('interviewRoom.noAnswerConfirm'),
      cancelText: t('interviewRoom.noAnswerCancel'),
      confirmColor: '#ef4444',
      success: async (res) => {
        if (!res.confirm) return;
        ending.value = true;
        try {
          await endInterviewApi(interviewId.value);
          uni.redirectTo({ url: '/pages/interview/history' });
        } catch (error: any) {
          ending.value = false;
          uni.showToast({ title: error?.message || t('interviewChat.endFailed'), icon: 'none' });
        }
      },
    });
    return;
  }

  uni.showModal({
    title: t('interviewChat.endTitle'),
    content: t('interviewChat.endConfirm'),
    confirmColor: '#ef4444',
    success: async (res) => {
      if (res.confirm) {
        ending.value = true;
        try {
          // Score is now produced by the AI report endpoint, not the client.
          await endInterviewApi(interviewId.value);
          uni.showToast({ title: t('interviewChat.ended'), icon: 'success' });
          // Jump straight to the report screen; it will trigger the AI
          // evaluation on first open and cache it on the interview row.
          setTimeout(() => {
            uni.redirectTo({ url: `/pages/interview/report?interviewId=${interviewId.value}` });
          }, 800);
        } catch (error: any) {
          ending.value = false;
          console.error('Failed to end interview:', error);
          uni.showToast({ title: error?.message || t('interviewChat.endFailed'), icon: 'none' });
        }
      }
    }
  });
};

const goBack = () => {
  uni.navigateBack({ delta: 1 });
};
</script>

<style scoped>
.interview-info {
  background: var(--gradient-primary);
  padding: 16px 20px;
  display: flex; justify-content: space-between; align-items: center;
  flex-shrink: 0;
  gap: 12px;
}

.position { font-size: 16px; font-weight: 700; color: #fff; }

.session-copy {
  flex: 1;
  min-width: 0;
}

.session-text {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.45;
  color: rgba(255, 255, 255, 0.78);
}

.info-actions {
  display: flex; align-items: center; gap: 10px;
  flex-shrink: 0;
}

.difficulty {
  font-size: 12px; color: rgba(255, 255, 255, 0.85);
  background: rgba(255,255,255,0.18);
  padding: 4px 10px; border-radius: 999px;
  font-weight: 600;
}

/* End is intentionally low-emphasis: it lives at the top corner so a thumb
   can't accidentally hit it during fast typing in the bottom half of the screen. */
.end-link {
  min-width: 44px; min-height: 44px;
  padding: 0 12px;
  display: flex; align-items: center; justify-content: center;
  border-radius: 10px;
}
.end-link:active { background: rgba(255,255,255,0.12); }
.end-link-text {
  font-size: 14px; font-weight: 600;
  color: #ef4444;
}

.chat-area {
  flex: 1;
  min-height: 0;
  max-height: calc(100vh - 200px);
}

.chat-area-surface {
  min-height: 100%;
  padding: 16px;
  box-sizing: border-box;
}

.message { margin-bottom: 16px; display: flex; }

.message.user { justify-content: flex-end; }

.message.ai { justify-content: flex-start; }

.msg-content {
  max-width: 72%; padding: 12px 16px;
  border-radius: 16px; font-size: 15px; line-height: 1.55;
}

.message.user .msg-content {
  background: var(--primary-color, #cd6a43); color: #fff;
  border-radius: 20px 4px 20px 20px;
  box-shadow: var(--shadow-sm);
}

.message.ai .msg-content {
  background: #fff;
  border: 1px solid var(--border-color, #d8c1b8);
  color: var(--text-primary, #1c1917);
  border-radius: 4px 20px 20px 20px;
  box-shadow: var(--shadow-xs, 0 1px 3px rgba(0,0,0,0.08), 0 1px 8px rgba(0,0,0,0.05));
}

/* Typing dots */
.typing { display: flex; align-items: center; gap: 8px; }
.typing-dots { display: flex; gap: 5px; padding: 4px 6px; align-items: center; }
.typing-timer {
  font-size: 11px;
  color: var(--text-tertiary, #8e8e93);
  font-variant-numeric: tabular-nums;
}

.dot {
  width: 6px; height: 6px; border-radius: 3px;
  background: #a8a29e; animation: bounce 1.4s infinite ease-in-out both;
}

.dot:nth-child(1) { animation-delay: -0.32s; }
.dot:nth-child(2) { animation-delay: -0.16s; }

@keyframes bounce {
  0%, 80%, 100% { transform: scale(0); }
  40% { transform: scale(1); }
}

.input-area {
  display: flex; padding: 10px 16px calc(10px + env(safe-area-inset-bottom, 0px)); gap: 10px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(20px); -webkit-backdrop-filter: blur(20px);
  border-top: 0.5px solid rgba(60, 60, 67, 0.1);
  align-items: center;
}

.chat-input {
  flex: 1; border: 1.5px solid #e7e5e4; border-radius: 22px;
  padding: 10px 18px; font-size: 15px; background: var(--surface-2, #fafaf9);
  color: var(--text-primary, #1c1917); height: 44px;
}

.ph { color: var(--text-tertiary, #8e8e93); }

.send-btn {
  min-width: 64px; height: 38px; padding: 0 14px;
  border-radius: 19px;
  background: var(--surface-3, #f5f5f4); display: flex;
  align-items: center; justify-content: center;
  flex-shrink: 0; transition: background 0.2s;
}

.send-active { background: var(--primary-color, #cd6a43); }

.send-label {
  color: var(--text-tertiary, #8e8e93); font-size: 14px; font-weight: 700; letter-spacing: 0.02em;
}
.send-active .send-label { color: #ffffff; }

/* Dark mode */
.is-dark { background-color: var(--bg-color, #1c1917); }

.is-dark .message.ai .msg-content { background: #292524; color: #fafaf9; box-shadow: none; }

.is-dark .input-area { background: rgba(28, 25, 23, 0.92); border-color: #44403c; }

.is-dark .chat-input { background: #292524; border-color: #44403c; color: #fafaf9; }

/* #ifdef MP-WEIXIN */
.input-area {
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
  background: var(--surface-1, #ffffff);
}

.message.ai .msg-content {
  border: 1px solid #e7e5e4;
  box-shadow: var(--shadow-xs);
}

.chat-container.is-dark .input-area {
  background: #1c1917;
  border-color: #44403c;
}

.chat-container.is-dark .message.ai .msg-content {
  background: #292524;
  border-color: #44403c;
  box-shadow: none;
}
/* #endif */

/* Competition visual system ------------------------------------------------ */
.interview-info {
  background: #f5f5f0;
  border-bottom: 1px solid #e0dfdb;
  padding-top: 14px;
  padding-bottom: 14px;
}

.position {
  font-family: "Songti SC", STSong, serif;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: #2c2b29;
}

.session-text {
  color: #5a5956;
}

.difficulty {
  color: #ac6448;
  background: transparent;
  border: 1px solid rgba(172, 100, 72, 0.36);
  border-radius: 4px;
}

.end-link-text {
  color: #c23b22;
}

.chat-area-surface {
  background: #faf9f6;
}

.msg-content {
  border-radius: 8px;
  box-shadow: none;
}

.message.user .msg-content {
  color: #faf9f6;
  background: #ac6448;
  border-radius: 8px 2px 8px 8px;
  box-shadow: none;
}

.message.ai .msg-content {
  color: #2c2b29;
  background: #ffffff;
  border: 1px solid #e0dfdb;
  border-radius: 2px 8px 8px 8px;
  box-shadow: 0 6px 18px rgba(44, 43, 41, 0.05);
}

.dot {
  background: #8b8a86;
}

.input-area {
  background: #faf9f6;
  border-top: 1px solid #e0dfdb;
}

.chat-input {
  height: 44px;
  color: #2c2b29;
  background: #ffffff;
  border: 1px solid #d8d7d2;
  border-radius: 6px;
}

.send-btn {
  min-width: 64px;
  height: 44px;
  color: #8b8a86;
  background: #efeee9;
  border: 1px solid #e0dfdb;
  border-radius: 6px;
}

.send-active {
  background: #c23b22;
  border-color: #c23b22;
}

.send-active .send-label {
  color: #faf9f6;
}

.chat-container.is-dark .interview-info {
  background: #242320;
  border-color: #494844;
}

.chat-container.is-dark .position {
  color: #faf9f6;
}

.chat-container.is-dark .session-text {
  color: #c6c4be;
}

.chat-container.is-dark .chat-area-surface,
.chat-container.is-dark .input-area {
  background: #1c1b19;
}

.chat-container.is-dark .message.ai .msg-content,
.chat-container.is-dark .chat-input {
  color: #faf9f6;
  background: #242320;
  border-color: #494844;
}

.chat-container.is-dark .send-btn:not(.send-active) {
  background: #34332f;
  border-color: #494844;
}
</style>
