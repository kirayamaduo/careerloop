<template>
  <view class="room-page app-soft-bg" :class="[themeClass, fontClass]">
    <!-- ========== Top bar ==========
         Custom header so we can sit flush over the camera preview without
         the WeChat default white nav bar reducing contrast. -->
    <view class="top-bar" :style="{ paddingTop: statusTopPx + 'px', paddingRight: rightAvoidWidth + 'px' }">
      <view class="back-btn" @click="confirmExit">
        <text class="back-icon">‹</text>
      </view>
      <view class="top-meta">
        <text class="top-title">{{ interview?.positionName || t('interviewRoom.mockInterview') }}</text>
        <text class="top-sub">{{ difficultyLabel }} · {{ modeLabel }} · {{ languageLabel }}</text>
      </view>
      <!-- End button moved to bottom action zone for better thumb reach -->
      <view style="width: 48px;"></view>
    </view>

    <!-- ========== Interviewer status ========== -->
    <view class="avatar-stage">
      <view
        class="avatar-shell"
        :class="{
          'is-talking': aiTalking,
          'is-listening': isRecording,
          'is-thinking': isThinking,
        }"
      >
        <view class="avatar-halo" v-if="aiTalking || isRecording" />
        <view class="avatar-face">
          <view class="eyes">
            <view class="eye left" />
            <view class="eye right" />
          </view>
          <view class="mouth" :class="mouthClass" />
        </view>
      </view>

      <view class="avatar-status">
        <text class="status-dot" :class="statusDotClass" />
        <view class="status-copy">
          <text class="status-role">AI 面试官</text>
          <text class="status-text">{{ statusLabel }}</text>
        </view>
      </view>
    </view>

    <!-- ========== Camera preview ==========
         The candidate view is the primary interview surface. Frames are
         still sampled best-effort for body-language scoring. -->
    <view class="camera-stage">
    <!-- #ifdef MP-WEIXIN -->
    <camera
      v-if="cameraReady"
      class="camera-pip"
      device-position="front"
      flash="off"
      :resolution="'medium'"
      id="bodyLangCamera"
      @error="onCameraError"
    />
    <view v-else class="camera-pip camera-pip-fallback">
      <text class="camera-fallback-icon ri-camera-line"></text>
      <text class="camera-fallback-text">{{ cameraError || t('interviewRoom.tapToEnable') }}</text>
      <view class="camera-enable-btn" @click="requestCamera">
        <text class="camera-enable-text">{{ t('interviewRoom.enableCamera') }}</text>
      </view>
    </view>
    <!-- #endif -->
    <!-- #ifndef MP-WEIXIN -->
    <view class="camera-pip camera-pip-fallback">
      <text class="camera-fallback-icon ri-camera-line"></text>
      <text class="camera-fallback-text">{{ t('interviewRoom.cameraOnly') }}</text>
    </view>
    <!-- #endif -->
      <view class="camera-caption">
        <text class="camera-caption-main">面试画面</text>
        <text class="camera-caption-sub">系统会采样眼神、表情和坐姿用于行为表现评估</text>
      </view>
    </view>

    <!-- ========== Caption strip ==========
         Live captions of what the AI just said + what we transcribed. Helps
         hearing-impaired users and gives a tap-to-replay affordance. -->
    <view class="caption-card" v-if="lastAiText || lastUserText">
      <view v-if="lastAiText" class="caption-row">
        <text class="caption-tag tag-ai">{{ t('interviewRoom.captionAi') }}</text>
        <text class="caption-body">{{ lastAiText }}</text>
        <view class="caption-replay" v-if="lastAudioUrl" @click="replayAudio">
          <text class="replay-icon">↻</text>
        </view>
      </view>
      <view v-if="lastUserText" class="caption-row caption-user">
        <text class="caption-tag tag-user">{{ t('interviewRoom.captionYou') }}</text>
        <text class="caption-body">{{ lastUserText }}</text>
      </view>
    </view>

    <!-- ========== Bottom action zone ========== -->
    <view class="action-zone">
      <view class="hint" v-if="!isRecording && !aiTalking && !isThinking">
        <text>{{ recordHint }}</text>
      </view>

      <view
        class="record-btn"
        :class="{
          'is-recording': isRecording,
          'is-disabled': aiTalking || isThinking,
        }"
        @touchstart.prevent="onPressStart"
        @touchend.prevent="onPressEnd"
        @touchcancel.prevent="onPressEnd"
        @mousedown.prevent="onPressStart"
        @mouseup.prevent="onPressEnd"
        @mouseleave.prevent="onPressEnd"
      >
        <view class="record-pulse" v-if="isRecording">
          <view class="pulse-ring" />
          <view class="pulse-ring delay-1" />
          <view class="pulse-ring delay-2" />
        </view>
        <text class="record-icon ri-mic-2-line"></text>
        <text v-if="isRecording" class="record-timer">{{ recordTimerText }}</text>
      </view>

      <view class="footer-row">
        <view class="text-mode-btn" @click="switchToTextMode">
          <text class="text-mode-icon">⌨</text>
          <text class="text-mode-label">{{ t('interviewRoom.switchToText') }}</text>
        </view>
        <view class="end-btn-bottom" @click="endInterview">
          <text class="end-btn-bottom-text">{{ t('interviewRoom.endInterview') }}</text>
        </view>
      </view>
    </view>

    <!-- #ifdef MP-WEIXIN -->
    <view v-if="privacyDialogVisible" class="privacy-mask">
      <view class="privacy-dialog">
        <text class="privacy-title">面试隐私授权</text>
        <text class="privacy-copy">
          为完成语音模拟面试和可选的行为表现评估，需要使用麦克风与相机。音频用于转写，画面仅在答题时抽帧分析，不保存原始录音或照片。
        </text>
        <view class="privacy-contract" @click="openWechatPrivacyContract">
          <text>查看《小程序隐私保护指引》</text>
        </view>
        <view class="privacy-actions">
          <view class="privacy-reject" @click="rejectWechatPrivacyAuthorization">
            <text>暂不使用</text>
          </view>
          <button
            id="careerloop-privacy-agree"
            class="privacy-agree"
            open-type="agreePrivacyAuthorization"
            @agreeprivacyauthorization="agreeWechatPrivacyAuthorization"
          >
            同意并继续
          </button>
        </view>
      </view>
    </view>
    <!-- #endif -->

  </view>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { onBackPress } from '@dcloudio/uni-app';
import { useI18n } from '@/locales';
import { getMpSafeAreaMetrics } from '@/utils/safeArea';
import { useTheme } from '@/utils/theme';
import {
  endInterviewApi,
  getInterviewByIdApi,
  getInterviewMessagesApi,
  voiceGreetingApi,
  voiceTurnApi,
  type Interview,
  type InterviewMessage,
  type VoiceTurnResponse,
} from '@/api/interview';
import { submitBodyLanguageFrameApi } from '@/api/bodyLanguage';

const { t } = useI18n();
const { themeClass, fontClass, refresh: refreshTheme } = useTheme();

// ───────────────────────── State ─────────────────────────
const statusTopPx = ref(52);
const rightAvoidWidth = ref(16);
const interviewId = ref<number>(0);
const interviewLang = (uni.getStorageSync('interview_language') as string) || 'zh';
const interview = ref<Interview | null>(null);

const isRecording = ref(false);
const isThinking = ref(false); // ASR + AI + TTS in flight
const aiTalking = ref(false); // AI audio currently playing
const ending = ref(false);
let pageActive = true;
let operationGeneration = 0;
let discardStoppedRecording = false;
let allowNextBack = false;
let pressHeld = false;

const lastAiText = ref('');
const lastUserText = ref('');
const lastAudioUrl = ref('');
// Tracks whether the candidate has produced at least one transcribed turn.
// The report endpoint refuses to evaluate sessions with zero user turns, so
// we use this to short-circuit "结束面试" with a friendly prompt instead of
// letting the backend reject the report request.
const hasAnswered = ref(false);

const recordSeconds = ref(0);
let recordTimerHandle: ReturnType<typeof setInterval> | null = null;
const RECORD_MAX_SECONDS = 60;

const cameraReady = ref(false);
const cameraError = ref('');
let bodyLangTimer: ReturnType<typeof setInterval> | null = null;
let bodyFrameInFlight = false;
const BODY_LANG_INTERVAL_MS = 3000;

// #ifdef MP-WEIXIN
const privacyDialogVisible = ref(false);
let resolveWechatPrivacy: ((result: { event: 'agree' | 'disagree'; buttonId?: string }) => void) | null = null;
// #endif

// Holds the recorder manager and audio context. Both are mini-program /
// uni-app singletons that live as long as the page does — we lazily
// initialize so the page can render even when permissions aren't granted.
let recorderManager: UniNamespace.RecorderManager | null = null;
let innerAudio: UniNamespace.InnerAudioContext | null = null;

// ───────────────────────── Computed ─────────────────────────
const recordTimerText = computed(() => {
  const s = Math.max(0, RECORD_MAX_SECONDS - recordSeconds.value);
  return t('interviewRoom.timeLeft', { s });
});

const statusDotClass = computed(() => {
  if (aiTalking.value) return 'dot-talking';
  if (isRecording.value) return 'dot-listening';
  if (isThinking.value) return 'dot-thinking';
  return 'dot-idle';
});

const statusLabel = computed(() => {
  if (aiTalking.value) return t('interviewRoom.statusSpeaking');
  if (isRecording.value) return t('interviewRoom.statusListening');
  if (isThinking.value) return t('interviewRoom.statusThinking');
  return t('interviewRoom.statusReady');
});

const difficultyLabel = computed(() => {
  const value = interview.value?.difficulty || 'NORMAL';
  const map: Record<string, string> = {
    EASY: t('interview.diffEasy'),
    NORMAL: t('interview.diffNormal'),
    MEDIUM: t('interview.diffNormal'),
    HARD: t('interview.diffHard'),
  };
  return map[String(value).toUpperCase()] || String(value);
});

const modeLabel = computed(() => t('interviewRoom.voiceMode'));
const languageLabel = computed(() => interviewLang === 'en' ? t('interviewRoom.languageEnglish') : t('interviewRoom.languageChinese'));

const mouthClass = computed(() => {
  if (!aiTalking.value) return 'mouth-rest';
  return 'mouth-talk';
});

const recordHint = computed(() => {
  if (!lastAiText.value) return t('interviewRoom.hintFirst');
  return t('interviewRoom.hintContinue');
});

// ───────────────────────── Lifecycle ─────────────────────────
onMounted(async () => {
  refreshTheme();
  // #ifdef MP-WEIXIN
  registerWechatPrivacyAuthorization();
  // #endif
  const safeMetrics = getMpSafeAreaMetrics();
  statusTopPx.value = safeMetrics.topSafeHeight;
  rightAvoidWidth.value = safeMetrics.rightAvoidWidth;

  const pages = getCurrentPages();
  const currentPage = pages[pages.length - 1] as any;
  interviewId.value = parseInt(currentPage.options?.interviewId || '0');

  if (!interviewId.value) {
    showToast(t('interviewRoom.missingInterviewId'), 'error');
    setTimeout(() => {
      allowNextBack = true;
      uni.navigateBack();
    }, 1200);
    return;
  }

  try {
    interview.value = await getInterviewByIdApi(interviewId.value);
    if (!pageActive) return;
    if (interview.value.status !== 'ONGOING') {
      const target = interview.value.status === 'COMPLETED'
        ? `/pages/interview/report?interviewId=${interviewId.value}`
        : '/pages/interview/history';
      uni.redirectTo({ url: target });
      return;
    }
    const history = await getInterviewMessagesApi(interviewId.value);
    if (!pageActive) return;
    restoreConversationState(history);
  } catch (e: any) {
    showToast(e?.message || t('interviewRoom.loadFailed'), 'error');
    return;
  }

  initRecorder();
  initAudio();

  // Camera scope check is non-fatal — if denied we still let the
  // candidate proceed with audio-only practice.
  // #ifdef MP-WEIXIN
  requestCamera(false);
  // #endif

  // Only a brand-new session needs a greeting. A resumed voice session uses
  // its latest persisted turns instead of replaying the very first question.
  if (!lastAiText.value && !lastUserText.value) {
    await fetchAndPlayGreeting();
  }
});

onBeforeUnmount(() => {
  pageActive = false;
  pressHeld = false;
  operationGeneration += 1;
  discardStoppedRecording = true;
  if (isRecording.value) {
    try { recorderManager?.stop(); } catch {}
  }
  cleanupRecording();
  // #ifdef MP-WEIXIN
  stopBodyLanguageCapture();
  // #endif
  if (innerAudio) {
    try { innerAudio.stop(); } catch {}
    try { innerAudio.destroy(); } catch {}
    innerAudio = null;
  }
  if (recorderManager) {
    const manager = recorderManager as any;
    manager.offStart?.(handleRecorderStart);
    manager.offStop?.(handleRecorderStop);
    manager.offError?.(handleRecorderError);
    recorderManager = null;
  }
  // #ifdef MP-WEIXIN
  unregisterWechatPrivacyAuthorization();
  if (resolveWechatPrivacy) {
    resolveWechatPrivacy({ event: 'disagree' });
    resolveWechatPrivacy = null;
  }
  privacyDialogVisible.value = false;
  // #endif
});

// ───────────────────────── Setup helpers ─────────────────────────
const restoreConversationState = (history: InterviewMessage[]) => {
  const rows = Array.isArray(history) ? history : [];
  const latestUser = [...rows].reverse().find((message) =>
    String(message.role || '').toUpperCase() === 'USER'
  );
  const latestAi = [...rows].reverse().find((message) =>
    String(message.role || '').toUpperCase() === 'AI'
  );
  if (latestUser?.content) {
    lastUserText.value = latestUser.content;
    hasAnswered.value = true;
  }
  if (latestAi?.content) {
    lastAiText.value = latestAi.content;
  }
};

const handleRecorderStart = () => {
  if (!pageActive) return;
  if (!pressHeld) {
    discardStoppedRecording = true;
    try { recorderManager?.stop(); } catch {}
    return;
  }
  discardStoppedRecording = false;
  isRecording.value = true;
  recordSeconds.value = 0;
  if (recordTimerHandle) clearInterval(recordTimerHandle);
  recordTimerHandle = setInterval(() => {
    recordSeconds.value += 1;
    if (recordSeconds.value >= RECORD_MAX_SECONDS) {
      stopRecordingAndSend();
    }
  }, 1000);
};

const handleRecorderStop = (res: any) => {
  const recordedSeconds = recordSeconds.value;
  cleanupRecording();
  if (!pageActive || discardStoppedRecording) return;
  if (!res?.tempFilePath) {
    showToast(t('interviewRoom.recordingEmpty'), 'error');
    return;
  }
  if (recordedSeconds < 1) {
    showToast(t('interviewRoom.recordingTooShort'), 'info');
    return;
  }
  sendVoiceTurn(res.tempFilePath);
};

const handleRecorderError = (err: any) => {
  cleanupRecording();
  if (!pageActive) return;
  showToast(err?.errMsg || t('interviewRoom.recorderError'), 'error');
};

const initRecorder = () => {
  if (recorderManager) return;
  recorderManager = uni.getRecorderManager();
  recorderManager.onStart(handleRecorderStart);
  recorderManager.onStop(handleRecorderStop);
  recorderManager.onError(handleRecorderError);
};

const initAudio = () => {
  innerAudio = uni.createInnerAudioContext();
  // Ignore the iOS/Android silent-mode switch so the interviewer's voice
  // is always audible — without this, a phone on vibrate/silent plays nothing
  // even though the mouth animation (driven by onPlay) still fires.
  (innerAudio as any).obeyMuteSwitch = false;
  innerAudio.onPlay(() => { aiTalking.value = true; });
  innerAudio.onEnded(() => { aiTalking.value = false; });
  innerAudio.onStop(() => { aiTalking.value = false; });
  innerAudio.onError((err: any) => {
    aiTalking.value = false;
    // operateAudio:fail is a system-level audio session error (often permission-
    // related); surfacing it as a toast is noisy and not actionable for the user.
    const msg: string = err?.errMsg || '';
    if (!msg.includes('operateAudio')) {
      showToast(msg || t('interviewRoom.audioFailed'), 'error');
    }
  });
};

// #ifdef MP-WEIXIN
const handleNeedWechatPrivacyAuthorization = (resolve: typeof resolveWechatPrivacy) => {
  if (!pageActive || typeof resolve !== 'function') {
    resolve?.({ event: 'disagree' });
    return;
  }
  // Only one sensitive operation may be pending. Refuse an older pending
  // request before replacing it so no camera/recorder call remains hung.
  resolveWechatPrivacy?.({ event: 'disagree' });
  resolveWechatPrivacy = resolve;
  privacyDialogVisible.value = true;
};

const registerWechatPrivacyAuthorization = () => {
  const wxApi = (globalThis as any).wx;
  if (typeof wxApi.onNeedPrivacyAuthorization !== 'function') return;
  wxApi.onNeedPrivacyAuthorization(handleNeedWechatPrivacyAuthorization);
};

const unregisterWechatPrivacyAuthorization = () => {
  const wxApi = (globalThis as any).wx;
  wxApi?.offNeedPrivacyAuthorization?.(handleNeedWechatPrivacyAuthorization);
};

const agreeWechatPrivacyAuthorization = () => {
  const resolve = resolveWechatPrivacy;
  resolveWechatPrivacy = null;
  privacyDialogVisible.value = false;
  resolve?.({
    event: 'agree',
    buttonId: 'careerloop-privacy-agree',
  });
};

const rejectWechatPrivacyAuthorization = () => {
  const resolve = resolveWechatPrivacy;
  resolveWechatPrivacy = null;
  privacyDialogVisible.value = false;
  resolve?.({ event: 'disagree' });
  cameraReady.value = false;
  cameraError.value = t('interviewRoom.cameraNotEnabled');
};

const openWechatPrivacyContract = () => {
  const wxApi = (globalThis as any).wx;
  if (typeof wxApi.openPrivacyContract !== 'function') {
    showToast('请在微信中查看小程序隐私保护指引', 'info');
    return;
  }
  wxApi.openPrivacyContract({
    fail: () => showToast('暂时无法打开隐私保护指引', 'error'),
  });
};

const authorizeCamera = () => {
  uni.authorize({
    scope: 'scope.camera',
    success: () => {
      cameraReady.value = true;
      cameraError.value = '';
      startBodyLanguageCapture();
    },
    fail: () => {
      cameraReady.value = false;
      cameraError.value = t('interviewRoom.cameraNotEnabled');
    },
  });
};

const requestCamera = (interactive: boolean | unknown = true) => {
  uni.getSetting({
    success: (settings) => {
      const cameraSetting = settings.authSetting?.['scope.camera'];
      if (cameraSetting === true) {
        cameraReady.value = true;
        cameraError.value = '';
        startBodyLanguageCapture();
        return;
      }
      cameraReady.value = false;
      cameraError.value = t('interviewRoom.cameraNotEnabled');
      if (interactive === false) return;
      if (cameraSetting === false) {
        openPermissionSettings('scope.camera', () => {
          cameraReady.value = true;
          cameraError.value = '';
          startBodyLanguageCapture();
        });
        return;
      }
      authorizeCamera();
    },
    fail: () => {
      cameraReady.value = false;
      cameraError.value = t('interviewRoom.cameraNotEnabled');
      if (interactive !== false) authorizeCamera();
    },
  });
};

const onCameraError = (e: any) => {
  cameraReady.value = false;
  cameraError.value = e?.detail?.errMsg || t('interviewRoom.cameraFailed');
  stopBodyLanguageCapture();
};

/**
 * Start sampling frames at 1/3 Hz while the candidate is speaking. We push
 * each base64 jpeg to /api/body-language/frame and let the Spring backend
 * fan out to the FastAPI sidecar. Errors are intentionally swallowed —
 * a flaky sidecar must never block the actual interview turn.
 */
const startBodyLanguageCapture = () => {
  if (bodyLangTimer || !interviewId.value) return;
  bodyLangTimer = setInterval(() => {
    if (!cameraReady.value || bodyFrameInFlight) return;
    // Only capture while the candidate is actively answering. While the AI is
    // talking the camera shows the candidate listening, which is less useful
    // for body-language scoring and wastes bandwidth.
    if (!isRecording.value) return;
    bodyFrameInFlight = true;
    const releaseFrame = () => {
      bodyFrameInFlight = false;
    };
    try {
      const ctx = uni.createCameraContext();
      ctx.takePhoto({
        quality: 'low',
        success: (res: any) => {
          if (!pageActive || !isRecording.value) {
            releaseFrame();
            return;
          }
          const fp = res?.tempImagePath;
          if (!fp) {
            releaseFrame();
            return;
          }
          // @ts-ignore mini-program global
          wx.getFileSystemManager().readFile({
            filePath: fp,
            encoding: 'base64',
            success: (r: any) => {
              if (!pageActive || !isRecording.value || !r?.data) {
                releaseFrame();
                return;
              }
              submitBodyLanguageFrameApi(interviewId.value, r.data)
                .catch(() => {})
                .finally(releaseFrame);
            },
            fail: releaseFrame,
          });
        },
        fail: releaseFrame,
      });
    } catch {
      // Some emulators throw on createCameraContext when the camera component
      // isn't mounted yet — try again on the next tick.
      releaseFrame();
    }
  }, BODY_LANG_INTERVAL_MS);
};

const stopBodyLanguageCapture = () => {
  if (bodyLangTimer) {
    clearInterval(bodyLangTimer);
    bodyLangTimer = null;
  }
};
// #endif

// ───────────────────────── Recording flow ─────────────────────────
const onPressStart = () => {
  if (aiTalking.value || isThinking.value || isRecording.value) return;
  pressHeld = true;
  if (!recorderManager) initRecorder();

  // #ifdef MP-WEIXIN
  uni.getSetting({
    success: (settings) => {
      if (settings.authSetting?.['scope.record'] === false) {
        openPermissionSettings('scope.record', beginRecording);
        return;
      }
      beginRecording();
    },
    fail: beginRecording,
  });
  // #endif
  // #ifndef MP-WEIXIN
  beginRecording();
  // #endif
};

const beginRecording = () => {
  if (!pageActive || !pressHeld || aiTalking.value || isThinking.value || isRecording.value) return;
  // mp3 + 16 kHz + mono matches what Paraformer-realtime-v2 wants and
  // keeps the upload small (~24 KB / sec) so even a 4G network gets the
  // payload to us inside 1s for a typical 15-second answer.
  recorderManager!.start({
    duration: RECORD_MAX_SECONDS * 1000,
    sampleRate: 16000,
    numberOfChannels: 1,
    encodeBitRate: 48000,
    format: 'mp3',
  });
};

const onPressEnd = () => {
  pressHeld = false;
  if (!isRecording.value) return;
  if (recordSeconds.value < 1) {
    // Treat "tap" as cancel rather than send — avoids surprising the user
    // with garbage transcripts after an accidental tap.
    discardStoppedRecording = true;
    try { recorderManager?.stop(); } catch {}
    cleanupRecording();
    showToast(t('interviewRoom.recordingTooShort'), 'info');
    return;
  }
  stopRecordingAndSend();
};

const stopRecordingAndSend = () => {
  if (!recorderManager) return;
  try { recorderManager.stop(); } catch {}
  // The actual upload is fired from onStop with the temp file path.
};

const cleanupRecording = () => {
  isRecording.value = false;
  if (recordTimerHandle) {
    clearInterval(recordTimerHandle);
    recordTimerHandle = null;
  }
};

// ───────────────────────── Network ─────────────────────────
const fetchAndPlayGreeting = async () => {
  const generation = ++operationGeneration;
  isThinking.value = true;
  try {
    const res = await voiceGreetingApi(interviewId.value, interviewLang);
    if (!pageActive || generation !== operationGeneration) return;
    applyVoiceResponse(res);
  } catch (e: any) {
    if (!pageActive || generation !== operationGeneration) return;
    lastAiText.value = t('interviewRoom.voiceFallback');
    showToast(e?.message || t('interviewRoom.greetingFailed'), 'error');
  } finally {
    if (pageActive && generation === operationGeneration) {
      isThinking.value = false;
    }
  }
};

const sendVoiceTurn = async (filePath: string) => {
  if (isThinking.value || !pageActive) return;
  const generation = ++operationGeneration;
  isThinking.value = true;
  try {
    const res = await voiceTurnApi(interviewId.value, filePath, 'mp3', interviewLang);
    if (!pageActive || generation !== operationGeneration) return;
    applyVoiceResponse(res);
  } catch (e: any) {
    if (!pageActive || generation !== operationGeneration) return;
    lastAiText.value = t('interviewRoom.voiceFallback');
    showToast(e?.message || t('interviewRoom.voiceTurnFailed'), 'error');
  } finally {
    if (pageActive && generation === operationGeneration) {
      isThinking.value = false;
    }
  }
};

const applyVoiceResponse = (res: VoiceTurnResponse) => {
  if (res.userText) {
    lastUserText.value = res.userText;
    hasAnswered.value = true;
  }
  lastAiText.value = res.aiText;
  lastAudioUrl.value = res.audioUrl;
  playAudio(res.audioUrl);
};

const playAudio = (url: string) => {
  if (!innerAudio || !url) return;
  try { innerAudio.stop(); } catch {}
  innerAudio.src = url;
  innerAudio.play();
};

const replayAudio = () => {
  if (lastAudioUrl.value) playAudio(lastAudioUrl.value);
};

// ───────────────────────── Exit / End ─────────────────────────
const confirmExit = () => {
  if (isInteractionBusy()) return;
  uni.showModal({
    title: t('interviewRoom.leaveTitle'),
    content: t('interviewRoom.leaveContent'),
    success: (m) => {
      if (!m.confirm) return;
      stopAiAudio();
      allowNextBack = true;
      uni.navigateBack();
    },
  });
};

const endInterview = () => {
  if (ending.value || isInteractionBusy()) return;
  stopAiAudio();
  // No transcribed answer yet — the report endpoint can't evaluate, so route
  // the candidate back to history with an explanation instead of letting the
  // backend reject the report request with a raw error.
  if (!hasAnswered.value) {
    uni.showModal({
      title: t('interviewRoom.noAnswerTitle'),
      content: t('interviewRoom.noAnswerContent'),
      confirmText: t('interviewRoom.noAnswerConfirm'),
      cancelText: t('interviewRoom.noAnswerCancel'),
      confirmColor: '#ef4444',
      success: async (m) => {
        if (!m.confirm) return;
        ending.value = true;
        try {
          await endInterviewApi(interviewId.value);
          uni.redirectTo({ url: '/pages/interview/history' });
        } catch (e: any) {
          ending.value = false;
          showToast(e?.message || t('interviewRoom.endFailed'), 'error');
        }
      },
    });
    return;
  }

  uni.showModal({
    title: t('interviewRoom.endTitle'),
    content: t('interviewRoom.endContent'),
    confirmColor: '#ef4444',
    success: async (m) => {
      if (!m.confirm) return;
      ending.value = true;
      try {
        await endInterviewApi(interviewId.value);
        // Mirror the text-mode flow: toast + brief delay before the redirect.
        // Calling redirectTo synchronously inside the modal success callback
        // races with the modal teardown on some WeChat versions and the
        // navigation silently no-ops, leaving the candidate stuck on this page.
        showToast(t('interviewRoom.endedToast'), 'success');
        setTimeout(() => {
          uni.redirectTo({ url: `/pages/interview/report?interviewId=${interviewId.value}` });
        }, 800);
      } catch (e: any) {
        ending.value = false;
        showToast(e?.message || t('interviewRoom.endFailed'), 'error');
      }
    },
  });
};

const switchToTextMode = () => {
  if (ending.value || isInteractionBusy()) return;
  stopAiAudio();
  uni.redirectTo({ url: `/pages/interview/chat?interviewId=${interviewId.value}` });
};

const isInteractionBusy = () => {
  if (!isRecording.value && !isThinking.value && !ending.value) return false;
  showToast(isRecording.value
    ? t('interviewRoom.statusListening')
    : t('interviewRoom.statusThinking'), 'info');
  return true;
};

const stopAiAudio = () => {
  if (!innerAudio) return;
  try { innerAudio.stop(); } catch {}
  aiTalking.value = false;
};

const openPermissionSettings = (
  scope: 'scope.camera' | 'scope.record',
  onGranted: () => void,
) => {
  const isCamera = scope === 'scope.camera';
  uni.showModal({
    title: isCamera
      ? t('interviewRoom.cameraNotEnabled')
      : t('interviewRoom.recorderError'),
    content: isCamera
      ? t('interviewRoom.cameraFailed')
      : t('interviewRoom.recorderError'),
    confirmText: interviewLang === 'en' ? 'Settings' : '去设置',
    success: (modal) => {
      if (!modal.confirm) return;
      uni.openSetting({
        success: (settings) => {
          if (settings.authSetting?.[scope]) {
            onGranted();
          }
        },
      });
    },
  });
};

onBackPress(() => {
  if (allowNextBack) {
    allowNextBack = false;
    return false;
  }
  confirmExit();
  return true;
});

// ───────────────────────── Toast ─────────────────────────
const showToast = (message: string, type: 'success' | 'error' | 'info' = 'info') => {
  uni.showToast({
    title: message,
    icon: type === 'success' ? 'success' : 'none',
    duration: 2000
  });
};
</script>

<style scoped>
.room-page {
  min-height: 100vh;
  background: linear-gradient(180deg, #1c1917 0%, #292524 50%, #1c1917 100%);
  display: flex;
  flex-direction: column;
  position: relative;
  overflow: hidden;
  font-family: -apple-system, BlinkMacSystemFont, "SF Pro Text", "Helvetica Neue", sans-serif;
}

/* ========== Top bar ========== */
.top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px 12px;
  z-index: 10;
}
.back-btn {
  width: 40px; height: 40px; border-radius: 20px;
  background: rgba(255, 255, 255, 0.12);
  display: flex; align-items: center; justify-content: center;
}
.back-icon { color: #fff; font-size: 26px; line-height: 26px; font-weight: 600; }
.top-meta { flex: 1; display: flex; flex-direction: column; align-items: center; }
.top-title { color: #fff; font-size: 15px; font-weight: 700; letter-spacing: 0.2px; }
.top-sub { color: rgba(255, 255, 255, 0.6); font-size: 11px; margin-top: 2px; }

/* ========== Camera main surface ========== */
.camera-stage {
  margin: 10px 20px 12px;
  border-radius: 20px;
  overflow: hidden;
  background: rgba(28, 25, 23, 0.92);
  border: 1px solid rgba(255, 255, 255, 0.18);
  box-shadow: 0 18px 42px rgba(0, 0, 0, 0.26);
  z-index: 2;
}
.camera-pip {
  position: relative;
  width: 100%;
  height: 58vw;
  max-height: 360px;
  min-height: 230px;
  border-radius: 0;
  overflow: hidden;
  background: #000;
}
.camera-pip-fallback {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  background: rgba(28, 25, 23, 0.75);
  gap: 6px;
  padding: 18px;
  text-align: center;
}
.camera-fallback-icon { font-size: 30px; }
.camera-fallback-text {
  color: rgba(255, 255, 255, 0.72);
  font-size: 12px;
  line-height: 1.3;
}
.camera-enable-btn {
  background: #c65e34; padding: 4px 10px; border-radius: 999px; margin-top: 2px;
}
.camera-enable-text { color: #fff; font-size: 11px; font-weight: 700; }
.camera-caption {
  padding: 10px 12px 12px;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.camera-caption-main {
  color: #fafaf9;
  font-size: 13px;
  line-height: 1.25;
  font-weight: 800;
}
.camera-caption-sub {
  color: rgba(255, 255, 255, 0.58);
  font-size: 11px;
  line-height: 1.35;
}

/* ========== Avatar stage ========== */
.avatar-stage {
  flex: none;
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 6px 20px 0;
  z-index: 2;
}
.avatar-shell {
  position: relative;
  width: 62px; height: 62px;
  display: flex; align-items: center; justify-content: center;
  transition: transform 0.4s ease;
  flex-shrink: 0;
}
.avatar-halo {
  position: absolute; inset: -8px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(221, 152, 125, 0.35) 0%, transparent 70%);
  animation: halo-pulse 1.8s ease-in-out infinite;
}
@keyframes halo-pulse {
  0%, 100% { transform: scale(1); opacity: 0.6; }
  50% { transform: scale(1.08); opacity: 1; }
}
.avatar-face {
  width: 54px; height: 54px;
  border-radius: 50%;
  background: linear-gradient(160deg, #dd987d 0%, #cd6a43 60%, #a24d2b 100%);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  box-shadow: var(--shadow-lg);
  animation: breathe 4s ease-in-out infinite;
}
.is-talking .avatar-face { animation: talking 0.45s ease-in-out infinite; }
.is-listening .avatar-face { animation: listen 1.4s ease-in-out infinite; }
@keyframes breathe {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.02); }
}
@keyframes talking {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.04); }
}
@keyframes listen {
  0%, 100% { transform: scale(1) rotate(-1deg); }
  50% { transform: scale(1.03) rotate(1deg); }
}

.eyes { display: flex; gap: 11px; }
.eye {
  width: 5px; height: 5px; border-radius: 50%;
  background: #fafaf9;
  box-shadow: var(--shadow-xs);
  animation: blink 4s ease-in-out infinite;
}
@keyframes blink {
  0%, 88%, 100% { transform: scaleY(1); }
  92%, 96% { transform: scaleY(0.1); }
}

.mouth {
  width: 18px; height: 5px; background: #fafaf9; border-radius: 4px;
  transition: all 0.15s ease;
}
.mouth-rest { width: 16px; height: 4px; }
.mouth-talk { animation: mouth-flap 0.32s ease-in-out infinite; }
@keyframes mouth-flap {
  0%, 100% { width: 14px; height: 4px; border-radius: 4px; }
  33% { width: 20px; height: 9px; border-radius: 8px; }
  66% { width: 17px; height: 6px; border-radius: 6px; }
}

.avatar-status {
  flex: 1;
  min-width: 0;
  display: flex; align-items: center; gap: 8px;
  background: rgba(255, 255, 255, 0.08);
  padding: 9px 12px; border-radius: 14px;
  max-width: 270px;
}
.status-dot { width: 8px; height: 8px; border-radius: 50%; }
.dot-idle { background: #78716c; }
.dot-talking { background: #bf9748; box-shadow: var(--shadow-xs); }
.dot-listening { background: #f87171; animation: pulse-dot 0.9s infinite; }
.dot-thinking { background: #fbbf24; animation: pulse-dot 1.4s infinite; }
@keyframes pulse-dot { 0%, 100% { opacity: 0.45; } 50% { opacity: 1; } }
.status-copy { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.status-role { color: #fafaf9; font-size: 12px; line-height: 1.2; font-weight: 800; }
.status-text { color: rgba(255, 255, 255, 0.72); font-size: 11px; line-height: 1.25; font-weight: 600; }

/* ========== Caption card ========== */
.caption-card {
  margin: 0 20px 10px;
  background: rgba(255, 255, 255, 0.07);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 16px;
  padding: 12px 14px;
  display: flex; flex-direction: column; gap: 10px;
  z-index: 2;
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
}
.caption-row { display: flex; align-items: flex-start; gap: 10px; }
.caption-tag {
  flex-shrink: 0;
  font-size: 10px; font-weight: 800;
  padding: 3px 8px; border-radius: 999px;
  letter-spacing: 0.04em;
}
.tag-ai { background: rgba(221, 152, 125, 0.25); color: #f1d6cc; }
.tag-user { background: rgba(248, 113, 113, 0.22); color: #fecaca; }
.caption-body {
  flex: 1; color: #fafaf9; font-size: 13px; line-height: 1.55; word-break: break-word;
}
.caption-user .caption-body { color: rgba(255, 255, 255, 0.78); font-size: 12.5px; }
.caption-replay {
  width: 28px; height: 28px; border-radius: 14px;
  background: rgba(255, 255, 255, 0.1);
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.replay-icon { color: #fafaf9; font-size: 16px; }

/* ========== Action zone ========== */
.action-zone {
  margin-top: auto;
  padding: 6px 16px calc(18px + env(safe-area-inset-bottom, 0px));
  display: flex; flex-direction: column; align-items: center; gap: 12px;
  z-index: 2;
}
.hint {
  background: rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.78);
  font-size: 12px; font-weight: 500;
  padding: 6px 14px; border-radius: 999px;
}

.record-btn {
  position: relative;
  width: 96px; height: 96px;
  border-radius: 50%;
  background: linear-gradient(135deg, #ef4444 0%, #b91c1c 100%);
  box-shadow: var(--shadow-card);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  transition: transform 0.15s ease, box-shadow 0.2s ease;
}
.record-btn:active { transform: scale(0.96); }
.record-icon { font-size: 32px; }
.record-timer { color: #fff; font-size: 10px; font-weight: 700; letter-spacing: 0.04em; }

.record-btn.is-recording {
  background: linear-gradient(135deg, #f87171 0%, #ef4444 100%);
  box-shadow: var(--shadow-lg);
}
.record-btn.is-disabled {
  background: rgba(120, 113, 108, 0.7);
  box-shadow: none;
  pointer-events: none;
  opacity: 0.6;
}

.record-pulse {
  position: absolute; inset: 0; border-radius: 50%;
  pointer-events: none;
}
.pulse-ring {
  position: absolute; inset: 0; border-radius: 50%;
  border: 2px solid rgba(248, 113, 113, 0.55);
  animation: ripple 1.8s ease-out infinite;
}
.delay-1 { animation-delay: 0.6s; }
.delay-2 { animation-delay: 1.2s; }
@keyframes ripple {
  0% { transform: scale(1); opacity: 0.7; }
  100% { transform: scale(2.2); opacity: 0; }
}

.footer-row {
  margin-top: 6px;
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.text-mode-btn {
  display: flex; align-items: center; gap: 6px;
  padding: 10px 14px; border-radius: 999px;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.14);
  flex: 1;
}
.text-mode-icon { color: #fafaf9; font-size: 14px; }
.text-mode-label { color: rgba(255, 255, 255, 0.85); font-size: 12px; font-weight: 600; }
.text-mode-btn:active { background: rgba(255, 255, 255, 0.16); }

.end-btn-bottom {
  display: flex; align-items: center; justify-content: center;
  padding: 10px 20px; border-radius: 999px;
  background: rgba(239, 68, 68, 0.85);
  border: 1px solid rgba(239, 68, 68, 0.9);
  min-width: 110px;
}
.end-btn-bottom:active { background: rgba(239, 68, 68, 1); }
.end-btn-bottom-text { color: #fff; font-size: 13px; font-weight: 700; letter-spacing: 0.02em; }

/* Competition visual system: an ink-toned focus room with restrained accents. */
.room-page {
  background: #242320;
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
}

.top-bar {
  border-bottom: 1px solid rgba(250, 249, 246, 0.12);
}

.back-btn {
  border-radius: 6px;
  background: rgba(250, 249, 246, 0.08);
  border: 1px solid rgba(250, 249, 246, 0.16);
}

.top-title,
.camera-caption-main,
.status-role {
  color: #faf9f6;
  font-family: "Songti SC", STSong, serif;
  font-weight: 600;
  letter-spacing: 0.05em;
}

.top-sub,
.camera-caption-sub,
.status-text {
  color: rgba(250, 249, 246, 0.62);
}

.camera-stage {
  background: #1c1b19;
  border: 1px solid rgba(224, 223, 219, 0.24);
  border-radius: 6px;
  box-shadow: 0 10px 28px rgba(0, 0, 0, 0.2);
}

.camera-pip-fallback {
  background: #1c1b19;
}

.camera-enable-btn {
  background: #c23b22;
  border-radius: 4px;
}

.avatar-halo {
  background: radial-gradient(circle, rgba(172, 100, 72, 0.34) 0%, transparent 70%);
}

.avatar-face {
  background: #ac6448;
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.22);
}

.avatar-status,
.caption-card {
  background: rgba(250, 249, 246, 0.06);
  border: 1px solid rgba(224, 223, 219, 0.16);
  border-radius: 6px;
}

.caption-card {
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
}

.caption-tag,
.caption-replay,
.hint,
.text-mode-btn,
.end-btn-bottom {
  border-radius: 4px;
}

.tag-ai {
  color: #eacec3;
  background: rgba(172, 100, 72, 0.26);
}

.tag-user {
  color: #efb3a7;
  background: rgba(194, 59, 34, 0.22);
}

.record-btn,
.record-btn.is-recording {
  background: #c23b22;
  box-shadow: 0 8px 24px rgba(194, 59, 34, 0.24);
}

.record-btn.is-disabled {
  background: #5a5956;
}

.pulse-ring {
  border-color: rgba(194, 59, 34, 0.56);
}

.text-mode-btn {
  background: rgba(250, 249, 246, 0.06);
  border-color: rgba(224, 223, 219, 0.18);
}

.end-btn-bottom {
  background: #c23b22;
  border-color: #c23b22;
}

.end-btn-bottom:active {
  background: #a8311d;
}

.privacy-mask {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  padding-bottom: calc(24px + env(safe-area-inset-bottom));
  background: rgba(15, 15, 14, 0.7);
}

.privacy-dialog {
  width: 100%;
  max-width: 360px;
  padding: 24px 20px 18px;
  background: #faf9f6;
  border: 1px solid #dedbd3;
  border-radius: 8px;
  box-shadow: 0 20px 56px rgba(0, 0, 0, 0.32);
}

.privacy-title {
  display: block;
  color: #22211f;
  font-family: "Songti SC", STSong, serif;
  font-size: 20px;
  font-weight: 600;
  text-align: center;
  letter-spacing: 0.04em;
}

.privacy-copy {
  display: block;
  margin-top: 14px;
  color: #5f5d58;
  font-size: 13px;
  line-height: 1.7;
}

.privacy-contract {
  display: flex;
  justify-content: center;
  margin-top: 12px;
  padding: 5px;
  color: #ac6448;
  font-size: 13px;
}

.privacy-actions {
  display: flex;
  gap: 10px;
  margin-top: 18px;
}

.privacy-reject,
.privacy-agree {
  box-sizing: border-box;
  display: flex;
  flex: 1;
  min-width: 0;
  height: 44px;
  align-items: center;
  justify-content: center;
  margin: 0;
  padding: 0 12px;
  border-radius: 5px;
  font-size: 14px;
  font-weight: 600;
  line-height: 44px;
}

.privacy-reject {
  color: #55534f;
  background: #f0efeb;
  border: 1px solid #dedbd3;
}

.privacy-agree {
  color: #fff;
  background: #c23b22;
  border: 1px solid #c23b22;
}

.privacy-agree::after {
  border: 0;
}

</style>
