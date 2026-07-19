<template>
  <view class="login-page app-soft-bg" :class="[themeClass, fontClass]">


    <view class="hero" :style="{ paddingRight: rightAvoidWidth + 'px' }">
      <view class="status-bar-spacer" :style="{ height: statusTopPx + 'px' }"></view>
      <text class="hero-kicker">智绘职路 CareerLoop · 学生成长端</text>
      <text class="hero-title">{{ t('login.heroTitle') }}</text>
      <text class="hero-subtitle">{{ t('login.heroSubtitle') }}</text>
    </view>

    <view class="form-sheet app-surface">
      <view class="segment-wrap">
        <view class="segment-bar">
          <view class="seg-item" :class="{ 'seg-active': mode === 'login' }" @click="switchMode('login')"><text>{{ t('login.tabSignIn') }}</text></view>
          <view class="seg-item" :class="{ 'seg-active': mode === 'register' }" @click="switchMode('register')"><text>{{ t('login.tabSignUp') }}</text></view>
        </view>
      </view>

      <scroll-view scroll-y enhanced :show-scrollbar="false" class="form-scroll" :style="{ height: formScrollHeight + 'px' }">
        <view class="form-scroll-inner">
          <view class="sheet-head">
            <text class="sheet-title">{{ mode === 'login' ? t('login.welcomeBack') : t('login.createYourAccount') }}</text>
            <text class="sheet-subtitle">{{ mode === 'login' ? t('login.continueWhereLeft') : t('login.unlockWorkflow') }}</text>
          </view>

          <!-- Nickname -->
          <view class="field" v-if="mode === 'register'">
            <text class="field-label">{{ t('login.nicknameLabel') }}</text>
            <input class="field-input ui-input" :class="{ 'input-error': nicknameError }" v-model="nickname"
              :placeholder="t('login.nicknamePlaceholder')" placeholder-class="ph" maxlength="20" @blur="validateNickname" />
            <text class="field-hint-error" v-if="nicknameError">{{ nicknameError }}</text>
          </view>

          <!-- Email -->
          <view class="field">
            <text class="field-label">{{ t('login.emailLabel') }}</text>
            <input class="field-input ui-input" :class="{ 'input-error': emailError }" v-model="account"
              :placeholder="t('login.emailPlaceholder')" placeholder-class="ph" maxlength="60"
              @input="onEmailInput" @blur="onEmailBlur" />
            <text class="field-hint-error" v-if="emailError">{{ emailError }}</text>
            <text class="field-hint-checking" v-if="checkingEmail">{{ t('login.checkingEmail') }}</text>
          </view>

          <!-- Password -->
          <view class="field">
            <text class="field-label">{{ t('login.passwordLabel') }}</text>
            <input class="field-input ui-input" :class="{ 'input-error': passwordStrength && passwordStrength.level === 0 }"
              v-model="password" type="password" :placeholder="t('login.passwordPlaceholder')" placeholder-class="ph" maxlength="32" />
            <view class="strength-row" v-if="mode === 'register' && password.length > 0">
              <view class="strength-bars">
                <view v-for="i in 3" :key="i" class="strength-bar"
                  :style="{ background: passwordStrength && passwordStrength.level >= i ? passwordStrength.color : '#e7e5e4' }" />
              </view>
              <text class="strength-label" :style="{ color: passwordStrength ? passwordStrength.color : '#a8a29e' }">
                {{ passwordStrength ? passwordStrength.label : '' }}
              </text>
            </view>
          </view>

          <!-- Confirm Password -->
          <view class="field" v-if="mode === 'register'">
            <text class="field-label">{{ t('login.confirmPasswordLabel') }}</text>
            <input class="field-input ui-input" :class="{ 'input-error': confirmPassword.length > 0 && confirmPassword !== password }"
              v-model="confirmPassword" type="password" :placeholder="t('login.confirmPasswordPlaceholder')"
              placeholder-class="ph" maxlength="32" />
            <text class="field-hint-error" v-if="confirmPassword.length > 0 && confirmPassword !== password">{{ t('login.passwordsNoMatch') }}</text>
          </view>

          <!-- Verification Code -->
          <view class="field" v-if="mode === 'register'">
            <text class="field-label">{{ t('login.codeLabel') }}</text>
            <view class="code-row">
              <input class="field-input code-input ui-input" :class="{ 'input-error': codeError }"
                v-model="verifyCode" :placeholder="t('login.codePlaceholder')" placeholder-class="ph"
                maxlength="6" type="number" @blur="validateCode" />
              <view class="btn-send-code" :class="{ 'is-disabled': codeCooldown > 0 || sendingCode }" @click="sendRegisterCode">
                <text class="btn-send-text">{{ sendingCode ? '…' : (codeCooldown > 0 ? codeCooldown + 's' : t('login.sendCode')) }}</text>
              </view>
            </view>
            <text class="field-hint-error" v-if="codeError">{{ codeError }}</text>
          </view>

          <!-- Forgot password -->
          <view class="forgot-row" v-if="mode === 'login'">
            <text class="forgot-link" @click="showForgotModal = true">{{ t('login.forgotPassword') }}</text>
          </view>

          <!-- Agreement -->
          <view class="agreement-row">
            <view class="checkbox" :class="{ 'checked': ageConfirmed }" @click="ageConfirmed = !ageConfirmed">
              <text v-if="ageConfirmed" class="check-mark ri-check-line"></text>
            </view>
            <view class="agreement-copy">
              <text class="agreement-text">{{ t('login.ageConfirm') }}</text>
            </view>
          </view>
          <view class="agreement-row">
            <view class="checkbox" :class="{ 'checked': agreed }" @click="agreed = !agreed">
              <text v-if="agreed" class="check-mark ri-check-line"></text>
            </view>
            <view class="agreement-copy">
              <text class="agreement-text">{{ t('login.iHaveRead') }}</text>
              <text class="link" @click="openAgreement('terms')">{{ t('login.termsLink') }}</text>
              <text class="agreement-text">{{ t('login.andText') }}</text>
              <text class="link" @click="openAgreement('privacy')">{{ t('login.privacyLink') }}</text>
            </view>
          </view>

          <view class="btn-primary" @click="handleSubmit" :class="{ 'is-loading': loading, 'is-disabled': !canSubmit }">
            <text class="btn-text">{{ loading ? t('login.waiting') : (mode === 'login' ? t('login.signInCta') : t('login.registerCta')) }}</text>
          </view>

          <view v-if="mode === 'login'">
            <view class="divider-row">
              <view class="divider-line"></view>
              <text class="divider-text">{{ t('login.otherMethods') }}</text>
              <view class="divider-line"></view>
            </view>

            <view class="social-row">
              <!-- #ifdef MP-WEIXIN -->
              <view class="btn-wechat" @click="wxLogin">
                <text class="wx-icon ri-wechat-fill"></text>
                <text class="wx-text">{{ t('login.wechatSignIn') }}</text>
              </view>
              <!-- #endif -->
              <view class="btn-guest" @click="guestLogin">
                <text class="guest-text">{{ t('login.guestModeBtn') }}</text>
              </view>
            </view>
          </view>

          <view class="bottom-safe"></view>
        </view>
      </scroll-view>
    </view>

    <!-- ── Terms / Privacy Modal ── -->
    <view class="modal-mask" v-if="showAgreementModal" @tap="showAgreementModal = false">
      <view class="modal-card agreement-modal app-surface" @tap.stop>
        <text class="modal-title">{{ agreementType === 'terms' ? t('login.termsTitle') : t('login.privacyTitle') }}</text>
        <scroll-view scroll-y class="agreement-scroll">
          <view>
            <view v-for="section in agreementSections" :key="section.title">
              <text class="agreement-section-title">{{ section.title }}</text>
              <text class="agreement-body">{{ section.body }}</text>
            </view>
          </view>
        </scroll-view>
        <view class="modal-actions">
          <view class="modal-btn modal-btn-confirm" @click="showAgreementModal = false"><text>{{ t('login.iUnderstand') }}</text></view>
        </view>
      </view>
    </view>

    <!-- ── Forgot Password Modal ── -->
    <view class="modal-mask" v-if="showForgotModal" @tap="closeForgotModal">
      <view class="modal-card app-surface" @tap.stop>
        <text class="modal-title">{{ t('login.forgotPasswordTitle') }}</text>

        <view v-if="resetStep === 1">
          <text class="modal-hint">{{ t('login.forgotPasswordHint') }}</text>
          <view class="field reset-field-main">
            <text class="field-label">{{ t('login.emailLabel') }}</text>
            <input class="field-input ui-input" :class="{ 'input-error': resetEmailError }"
              v-model="resetEmail" :placeholder="t('login.emailPlaceholder')" placeholder-class="ph"
              maxlength="60" @blur="validateResetEmail" />
            <text class="field-hint-error" v-if="resetEmailError">{{ resetEmailError }}</text>
          </view>
          <view class="code-row reset-code-row">
            <input class="field-input code-input ui-input" v-model="resetCode"
              :placeholder="t('login.codePlaceholder')" placeholder-class="ph" maxlength="6" type="number" />
            <view class="btn-send-code" :class="{ 'is-disabled': resetCooldown > 0 || sendingReset }" @click="sendResetCode">
              <text class="btn-send-text">{{ sendingReset ? '…' : (resetCooldown > 0 ? resetCooldown + 's' : t('login.sendCode')) }}</text>
            </view>
          </view>
          <view class="modal-actions">
            <view class="modal-btn modal-btn-cancel" @click="closeForgotModal"><text>{{ t('common.cancel') }}</text></view>
            <view class="modal-btn modal-btn-confirm" @click="goResetStep2"><text>{{ t('login.nextBtn') }}</text></view>
          </view>
        </view>

        <view v-if="resetStep === 2">
          <text class="modal-hint">{{ t('login.setNewPasswordFor', { email: resetEmail }) }}</text>
          <view class="field reset-field-main">
            <text class="field-label">{{ t('login.newPasswordLabel') }}</text>
            <input class="field-input ui-input" :class="{ 'input-error': resetNewPwd.length > 0 && resetNewPwd.length < 6 }"
              v-model="resetNewPwd" type="password" :placeholder="t('login.atLeast6Chars')" placeholder-class="ph" maxlength="32" />
            <text class="field-hint-error" v-if="resetNewPwd.length > 0 && resetNewPwd.length < 6">{{ t('login.atLeast6Chars') }}</text>
          </view>
          <view class="field reset-field-sub">
            <text class="field-label">{{ t('login.confirmNewPasswordLabel') }}</text>
            <input class="field-input ui-input" :class="{ 'input-error': resetConfirmPwd.length > 0 && resetConfirmPwd !== resetNewPwd }"
              v-model="resetConfirmPwd" type="password" :placeholder="t('login.confirmPasswordPlaceholder')" placeholder-class="ph" maxlength="32" />
            <text class="field-hint-error" v-if="resetConfirmPwd.length > 0 && resetConfirmPwd !== resetNewPwd">{{ t('login.passwordsNoMatch') }}</text>
          </view>
          <view class="modal-actions">
            <view class="modal-btn modal-btn-cancel" @click="resetStep = 1"><text>{{ t('common.back') }}</text></view>
            <view class="modal-btn modal-btn-confirm" :class="{ 'is-loading': resetting }" @click="doResetPassword">
              <text>{{ resetting ? t('login.resetting') : t('login.resetPasswordBtn') }}</text>
            </view>
          </view>
        </view>
      </view>
    </view>

  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useI18n } from '@/locales';
import { getMpSafeAreaMetrics } from '@/utils/safeArea';
import { sendCodeApi, resetPasswordApi, registerApi, loginApi, wechatLoginApi, checkEmailApi } from '@/api/user';
import { enterGuestMode, isRealUser } from '@/utils/auth';
import {
  consumeConsentReturnUrl,
  recordCurrentConsentOnServer,
  storeCurrentConsent,
} from '@/utils/consent';
import { useTheme } from '@/utils/theme';
import { syncPendingOnboarding } from '@/utils/onboardingSync';
import { shouldForceOnboarding } from '@/utils/onboardingGate';

const { t } = useI18n();
const statusTopPx = ref(52);
const rightAvoidWidth = ref(20);
const { themeClass, fontClass, refresh: refreshTheme } = useTheme();

// ─── 动态计算 scroll-view 高度 ─────────────────────────────────────
// uni-app 的 scroll-view 必须给一个明确的 px 高度才能滚动。
// 公式: 屏幕高度 - hero高度 - segment-wrap高度 - form-sheet上边距
const formScrollHeight = ref(400); // 默认值，onMounted 中覆盖

onMounted(() => {
  refreshTheme();
  const safeMetrics = getMpSafeAreaMetrics();
  statusTopPx.value = safeMetrics.topSafeHeight;
  rightAvoidWidth.value = safeMetrics.rightAvoidWidth;

  // 动态计算 scroll-view 可用高度
  const getWindowInfo = (uni as unknown as {
    getWindowInfo?: () => { windowHeight?: number };
  }).getWindowInfo;
  const screenH = (typeof getWindowInfo === 'function'
    ? getWindowInfo().windowHeight
    : 667) || 667;
  // hero 占据高度：statusBar + kicker + title + subtitle + padding ≈ statusBar + 90
  const heroH = statusTopPx.value + 90;
  // form-sheet overhead: padding-top(14) + segment-bar(36 + 16margin) ≈ 66
  // 新版编辑式布局只回收 1px 边线，避免内容区与顶部纸张发生重叠。
  const formSheetOverhead = 66 - 1;
  formScrollHeight.value = screenH - heroH - formSheetOverhead;
});

// ─── 自定义 Toast ───────────────────────────────────────────────
const showSnack = (message: string, type: 'success' | 'error' | 'info' = 'info') => {
  uni.showToast({
    title: message,
    icon: type === 'success' ? 'success' : 'none',
    duration: 2000
  });
};

const routeAfterAuth = async () => {
  try {
    await syncPendingOnboarding();
  } catch {
    // Keep the pending setup in storage so a later real-account session can retry.
  }
  if (!isRealUser()) return;

  setTimeout(() => {
    shouldForceOnboarding().then((force) => {
      if (!isRealUser()) return;
      if (force) {
        uni.reLaunch({ url: '/pages/onboarding/index' });
        return;
      }
      const returnUrl = consumeConsentReturnUrl();
      if (returnUrl) {
        uni.reLaunch({ url: returnUrl });
        return;
      }
      uni.switchTab({ url: '/pages/home/index' });
    });
  }, 1000);
};

const storeRealSession = (token: string, user: any) => {
  uni.setStorageSync('token', token);
  uni.setStorageSync('userId', user.userId);
  uni.setStorageSync('userInfo', user);
  storeCurrentConsent();
  uni.removeStorageSync('isGuest');
  recordCurrentConsentOnServer().catch(() => {
    // Best-effort audit write. A later authenticated launch can retry.
  });
};

const signedInMessage = (accountRestored?: boolean) =>
  t(accountRestored ? 'login.accountRestored' : 'login.signedIn');

// ─── 表单字段 ────────────────────────────────────────────────────
const mode = ref<'login' | 'register'>('login');
const nickname = ref('');
const account = ref('');
const password = ref('');
const confirmPassword = ref('');
const verifyCode = ref('');
const ageConfirmed = ref(false);
const agreed = ref(false);
const loading = ref(false);

// ─── 校验状态 ────────────────────────────────────────────────────
const nicknameError = ref('');
const emailError = ref('');
const codeError = ref('');
const resetEmailError = ref('');

const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

const validateNickname = () => {
  if (!nickname.value) { nicknameError.value = ''; return; }
  nicknameError.value = nickname.value.length < 2 ? t('login.atLeast2Chars') : '';
};

const onEmailInput = () => {
  if (!account.value) { emailError.value = ''; return; }
  emailError.value = emailRegex.test(account.value) ? '' : t('login.validEmail');
};

const emailExists = ref(false);
const checkingEmail = ref(false);
const onEmailBlur = async () => {
  if (!account.value) { emailError.value = ''; return; }
  if (!emailRegex.test(account.value)) {
    emailError.value = t('login.validEmail');
    return;
  }
  emailError.value = '';
  if (mode.value !== 'register') return;
  checkingEmail.value = true;
  try {
    const exists = await checkEmailApi(account.value);
    emailExists.value = !!exists;
    emailError.value = exists ? t('login.emailRegistered') : '';
  } catch { emailExists.value = false; }
  finally { checkingEmail.value = false; }
};

const validateResetEmail = () => {
  if (!resetEmail.value) { resetEmailError.value = ''; return; }
  resetEmailError.value = emailRegex.test(resetEmail.value) ? '' : t('login.validEmail');
};

const validateCode = () => {
  if (!verifyCode.value) { codeError.value = ''; return; }
  codeError.value = /^\d{6}$/.test(verifyCode.value) ? '' : t('login.codeMustBe6Digits');
};

// ─── 密码强度 ────────────────────────────────────────────────────
const passwordStrength = computed(() => {
  const p = password.value;
  if (!p) return null;
  if (p.length < 6) return { level: 0, label: t('login.strengthTooShort'), color: '#ef4444' };
  const types = [/[A-Z]/.test(p), /[a-z]/.test(p), /[0-9]/.test(p), /[^A-Za-z0-9]/.test(p)].filter(Boolean).length;
  if (p.length >= 10 && types >= 3) return { level: 3, label: t('login.strengthStrong'), color: '#ab863c' };
  if (p.length >= 8 && types >= 2) return { level: 2, label: t('login.strengthMedium'), color: '#f59e0b' };
  return { level: 1, label: t('login.strengthWeak'), color: '#ef4444' };
});

// ─── 验证码冷却 ──────────────────────────────────────────────────
const codeCooldown = ref(0);
const sendingCode = ref(false);
let cooldownTimer: ReturnType<typeof setInterval> | null = null;
const resetCooldown = ref(0);
const sendingReset = ref(false);
let resetCooldownTimer: ReturnType<typeof setInterval> | null = null;

const startCooldown = (cdRef: typeof codeCooldown, timerRef: { get: () => ReturnType<typeof setInterval> | null; set: (v: any) => void }) => {
  cdRef.value = 60;
  const t = setInterval(() => {
    cdRef.value--;
    if (cdRef.value <= 0) { clearInterval(t); timerRef.set(null); }
  }, 1000);
  timerRef.set(t);
};

// ─── 找回密码弹窗 ────────────────────────────────────────────────
const showForgotModal = ref(false);
const resetStep = ref(1);
const resetEmail = ref('');
const resetCode = ref('');
const resetNewPwd = ref('');
const resetConfirmPwd = ref('');
const resetting = ref(false);

const canSubmit = computed(() => {
  if (loading.value || !ageConfirmed.value || !agreed.value || !account.value || !password.value) return false;
  if (mode.value === 'register') return !!nickname.value && !!confirmPassword.value && !!verifyCode.value;
  return true;
});

const switchMode = (m: 'login' | 'register') => {
  mode.value = m;
  verifyCode.value = '';
  emailExists.value = false;
  emailError.value = '';
  nicknameError.value = '';
  codeError.value = '';
  codeCooldown.value = 0;
  if (cooldownTimer) { clearInterval(cooldownTimer); cooldownTimer = null; }
};

const sendRegisterCode = async () => {
  if (codeCooldown.value > 0 || sendingCode.value) return;
  if (!ageConfirmed.value) { showSnack(t('login.confirmAgeError'), 'error'); return; }
  if (!agreed.value) { showSnack(t('login.agreeTermsError'), 'error'); return; }
  if (!account.value || !emailRegex.test(account.value)) { showSnack(t('login.validEmailFirst'), 'error'); return; }
  if (emailExists.value) { showSnack(t('login.emailRegistered'), 'error'); return; }
  sendingCode.value = true;
  try {
    await sendCodeApi({ email: account.value, purpose: 'REGISTER' });
    showSnack(t('login.codeSent'), 'success');
    startCooldown(codeCooldown, { get: () => cooldownTimer, set: (v) => { cooldownTimer = v; } });
  } catch (e: any) {
    showSnack(e?.message || t('login.sendCodeFailed'), 'error');
  } finally { sendingCode.value = false; }
};

const sendResetCode = async () => {
  if (resetCooldown.value > 0 || sendingReset.value) return;
  if (!resetEmail.value || !emailRegex.test(resetEmail.value)) { showSnack(t('login.validEmailFirst'), 'error'); return; }
  sendingReset.value = true;
  try {
    await sendCodeApi({ email: resetEmail.value, purpose: 'RESET' });
    showSnack(t('login.codeSent'), 'success');
    startCooldown(resetCooldown, { get: () => resetCooldownTimer, set: (v) => { resetCooldownTimer = v; } });
  } catch (e: any) {
    showSnack(e?.message || t('login.sendCodeFailed'), 'error');
  } finally { sendingReset.value = false; }
};

const goResetStep2 = () => {
  if (!resetEmail.value || !emailRegex.test(resetEmail.value)) { showSnack(t('login.enterValidEmail'), 'error'); return; }
  if (!resetCode.value || !/^\d{6}$/.test(resetCode.value)) { showSnack(t('login.enter6DigitCode'), 'error'); return; }
  resetStep.value = 2;
};

const doResetPassword = async () => {
  if (resetNewPwd.value.length < 6) { showSnack(t('login.passwordMin6'), 'error'); return; }
  if (resetNewPwd.value !== resetConfirmPwd.value) { showSnack(t('login.passwordsNoMatch'), 'error'); return; }
  resetting.value = true;
  try {
    await resetPasswordApi({ email: resetEmail.value, code: resetCode.value, newPassword: resetNewPwd.value });
    showSnack(t('login.passwordResetSuccess'), 'success');
    closeForgotModal();
  } catch (e: any) {
    showSnack(e?.message || t('login.resetFailed'), 'error');
  } finally { resetting.value = false; }
};

const closeForgotModal = () => {
  showForgotModal.value = false;
  resetStep.value = 1;
  resetEmail.value = '';
  resetCode.value = '';
  resetNewPwd.value = '';
  resetConfirmPwd.value = '';
  resetEmailError.value = '';
  resetCooldown.value = 0;
  if (resetCooldownTimer) { clearInterval(resetCooldownTimer); resetCooldownTimer = null; }
};

const showAgreementModal = ref(false);
const agreementType = ref<'terms' | 'privacy'>('terms');
const agreementSections = computed(() => {
  const prefix = agreementType.value === 'terms' ? 'legal.terms' : 'legal.privacy';
  const count = agreementType.value === 'terms' ? 9 : 8;
  return Array.from({ length: count }, (_, index) => {
    const n = index + 1;
    return {
      title: t(`${prefix}.s${n}Title`),
      body: t(`${prefix}.s${n}Body`),
    };
  });
});
const openAgreement = (type: 'terms' | 'privacy') => { agreementType.value = type; showAgreementModal.value = true; };

const handleSubmit = async () => {
  if (!ageConfirmed.value) { showSnack(t('login.confirmAgeError'), 'error'); return; }
  if (!agreed.value) { showSnack(t('login.agreeTermsError'), 'error'); return; }
  if (!account.value || !emailRegex.test(account.value)) { showSnack(t('login.validEmail'), 'error'); return; }
  if (!password.value) { showSnack(t('login.enterPassword'), 'error'); return; }
  if (mode.value === 'register') {
    if (!nickname.value || nickname.value.length < 2) { showSnack(t('login.nicknameMin2'), 'error'); return; }
    if (emailExists.value) { showSnack(t('login.emailRegistered'), 'error'); return; }
    if (password.value.length < 6) { showSnack(t('login.passwordMin6'), 'error'); return; }
    if (confirmPassword.value !== password.value) { showSnack(t('login.passwordsNoMatchAlt'), 'error'); return; }
    if (!verifyCode.value || !/^\d{6}$/.test(verifyCode.value)) { showSnack(t('login.enter6DigitCode'), 'error'); return; }
  }
  loading.value = true;
  try {
    if (mode.value === 'register') {
      await registerApi({ nickname: nickname.value, identityType: 'EMAIL_PASSWORD', identifier: account.value, credential: password.value, code: verifyCode.value });
      password.value = '';
      confirmPassword.value = '';
      verifyCode.value = '';
      nickname.value = '';
      mode.value = 'login';
      showSnack('注册成功，请登录', 'success');
      return;
    } else {
      const res = await loginApi({ identityType: 'EMAIL_PASSWORD', identifier: account.value, credential: password.value });
      storeRealSession(res.token, res.user);
      showSnack(signedInMessage(res.accountRestored), 'success');
    }
    await routeAfterAuth();
  } catch (e: any) {
    showSnack(e?.message || t('login.requestFailed'), 'error');
  } finally { loading.value = false; }
};

/**
 * WeChat MP one-tap sign-in:
 *   uni.login({ provider:'weixin' })  →  short-lived `code` (WeChat side)
 *   POST /auth/wechat-login { code }  →  backend exchanges via jscode2session,
 *                                        returns { token, user }
 *
 * The very first time a WeChat openid is seen the backend creates a user with
 * a placeholder nickname ("WeChat User"); the Profile page will hand the user
 * a chance to personalize. We keep that two-step flow because requesting a
 * nickname/avatar via wx.getUserProfile requires a button-tap and we don't
 * want to gate first-run behind an extra modal.
 */
const wxLogin = () => {
  if (!ageConfirmed.value) { showSnack(t('login.confirmAgeError'), 'error'); return; }
  if (!agreed.value) { showSnack(t('login.agreeTermsError'), 'error'); return; }
  uni.showLoading({ title: t('login.signingIn') });
  uni.login({
    provider: 'weixin',
    success: async (loginRes) => {
      if (!loginRes.code) {
        uni.hideLoading();
        showSnack(t('login.wechatNoCode'), 'error');
        return;
      }
      try {
        const res = await wechatLoginApi({ code: loginRes.code });
        storeRealSession(res.token, res.user);
        uni.hideLoading();
        showSnack(signedInMessage(res.accountRestored), 'success');
        await routeAfterAuth();
      } catch (e: any) {
        uni.hideLoading();
        // Surface the actual reason -- usually a misconfigured appId/secret on
        // the server, or the user's tenant hasn't whitelisted our backend.
        showSnack(e?.message || t('login.wechatServerFailed'), 'error');
      }
    },
    fail: () => { uni.hideLoading(); showSnack(t('login.wechatCanceled'), 'error'); }
  });
};

const guestLogin = () => {
  if (!ageConfirmed.value) { showSnack(t('login.confirmAgeError'), 'error'); return; }
  if (!agreed.value) { showSnack(t('login.agreeTermsError'), 'error'); return; }
  storeCurrentConsent();
  enterGuestMode();
  showSnack(t('login.guestEnabled'), 'info');
  setTimeout(() => {
    // Guests get an explicit read-only preview. Onboarding data is account
    // state and cannot be synchronized for a sentinel guest user.
    uni.switchTab({ url: '/pages/home/index' });
  }, 500);
};
</script>

<style scoped>
/* ── Page ── */
.login-page {
  min-height: 100vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  background: var(--paper, #faf9f6);
  color: var(--ink, #2c2b29);
  font-family: var(--font-sans, "PingFang SC", "Microsoft YaHei", sans-serif);
  box-sizing: border-box;
}
.login-page button { margin: 0; }
.login-page button::after { border: none !important; }

.hero {
  padding: 0 20px 18px;
  background:
    radial-gradient(circle at 88% 12%, rgba(184, 151, 90, 0.13), transparent 32%),
    radial-gradient(circle at 8% 30%, rgba(194, 59, 34, 0.08), transparent 34%),
    var(--paper, #faf9f6);
  border-bottom: 1px solid var(--border-color, #e0dfdb);
}
/* #ifdef H5 */
.hero { padding-top: 20px; }
/* #endif */
.status-bar-spacer { width: 100%; }
.hero-kicker { font-size: 11px; font-weight: 600; color: var(--vermilion, #c23b22); letter-spacing: 0.12em; display: block; margin-bottom: 8px; margin-top: 8px; font-family: var(--font-serif, "Songti SC", STSong, serif); }
.hero-title { font-size: 30px; font-weight: 600; color: var(--ink, #2c2b29); display: block; line-height: 1.35; letter-spacing: 0.05em; font-family: var(--font-serif, "Songti SC", STSong, serif); }
.hero-subtitle {
  display: block;
  margin-top: 8px;
  max-width: 520px;
  font-size: 13px;
  line-height: 1.75;
  color: var(--ink-secondary, #5a5956);
}

.form-sheet {
  width: calc(100% - 40px);
  max-width: var(--content-max-width, 640px);
  flex: 1;
  display: flex;
  flex-direction: column;
  margin-top: -1px;
  margin-left: auto;
  margin-right: auto;
  background: var(--card-bg, #fffefa);
  border: 1px solid var(--border-color, #e0dfdb);
  border-top: none;
  border-radius: 0 0 var(--radius-md, 6px) var(--radius-md, 6px);
  padding: 14px var(--page-gutter-tight, 16px) 0;
  box-shadow: none;
  box-sizing: border-box;
}
.form-scroll {
  /* height set via :style binding for uni-app compat */
  scrollbar-width: none;
  -ms-overflow-style: none;
}
.form-scroll::-webkit-scrollbar {
  display: none;
}
.form-scroll-inner {
  padding-bottom: 48px;
}
.segment-wrap { margin-bottom: 16px; flex-shrink: 0; }
.segment-bar { display: flex; background: transparent; border: 0 solid var(--border-color, #e0dfdb); border-bottom-width: 1px; border-radius: 0; padding: 0; }
.seg-item { flex: 1; text-align: center; height: 36px; line-height: 36px; border-radius: 0; font-size: 14px; font-weight: 600; color: var(--ink-tertiary, #8b8a86); font-family: var(--font-serif, "Songti SC", STSong, serif); letter-spacing: 0.08em; }
.seg-active { background: transparent; color: var(--vermilion, #c23b22); font-weight: 600; border-bottom: 2px solid var(--vermilion, #c23b22); box-shadow: none; }
.sheet-head { margin-bottom: 16px; }
.sheet-title { display: block; font-size: 20px; font-weight: 600; color: var(--ink, #2c2b29); font-family: var(--font-serif, "Songti SC", STSong, serif); letter-spacing: 0.05em; }
.sheet-subtitle {
  display: block;
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.5;
  color: var(--ink-secondary, #5a5956);
}

.field { margin-bottom: 12px; }
.field-label { font-size: 12px; font-weight: 600; color: var(--ink-secondary, #5a5956); display: block; margin-bottom: 6px; font-family: var(--font-serif, "Songti SC", STSong, serif); letter-spacing: 0.04em; }
.field-input { width: 100%; height: 46px; border: 1px solid var(--border-color, #e0dfdb); border-radius: var(--radius-sm, 4px); padding: 0 16px; font-size: 14px; color: var(--ink, #2c2b29); background: var(--card-bg, #fffefa); box-sizing: border-box; box-shadow: none; }
.ph { color: var(--ink-placeholder, #aaa9a5); }
.input-error { border-color: #ef4444 !important; background: #fff8f8 !important; }
.field-hint-error { display: block; font-size: 12px; color: #ef4444; margin-top: 5px; font-weight: 500; }
.field-hint-checking { display: block; font-size: 12px; color: var(--ink-tertiary, #8b8a86); margin-top: 5px; }

.strength-row { display: flex; align-items: center; gap: 8px; margin-top: 8px; }
.strength-bars { display: flex; gap: 4px; flex: 1; }
.strength-bar { flex: 1; height: 4px; border-radius: 2px; transition: background 0.3s; }
.strength-label { font-size: 12px; font-weight: 600; white-space: nowrap; }

.code-row { display: flex; gap: 10px; align-items: center; }
.reset-field-main { margin-top: 14px; }
.reset-field-sub,
.reset-code-row { margin-top: 12px; }
.code-input { flex: 1; }
.btn-send-code { flex-shrink: 0; height: 46px; padding: 0 14px; background: transparent; border: 1px solid var(--vermilion, #c23b22); border-radius: var(--btn-radius, 6px); display: flex; align-items: center; justify-content: center; white-space: nowrap; box-shadow: none; }
.btn-send-code.is-disabled { background: transparent; border-color: var(--border-strong, #ceccc5); pointer-events: none; }
.btn-send-text { color: var(--vermilion, #c23b22); font-size: 13px; font-weight: 600; }
.btn-send-code.is-disabled .btn-send-text { color: var(--ink-tertiary, #8b8a86); }

.forgot-row { text-align: right; margin-top: -2px; margin-bottom: 12px; }
.forgot-link { font-size: 13px; color: var(--vermilion, #c23b22); font-weight: 600; }

.agreement-row { display: flex; align-items: flex-start; gap: 10px; margin-bottom: 12px; }
.checkbox { width: 18px; height: 18px; border-radius: 3px; flex-shrink: 0; border: 1.5px solid var(--border-strong, #ceccc5); display: flex; align-items: center; justify-content: center; background: var(--card-bg, #fffefa); margin-top: 1px; }
.checked { background: var(--indigo, #ac6448); border-color: var(--indigo, #ac6448); }
.check-mark { font-size: 13px; color: #ffffff; font-weight: 700; }
.agreement-copy { display: flex; flex-wrap: wrap; align-items: center; row-gap: 2px; column-gap: 2px; flex: 1; }
.agreement-text { font-size: 12px; color: var(--ink-secondary, #5a5956); line-height: 1.6; }
.link { color: var(--vermilion, #c23b22); font-weight: 600; font-size: 12px; }

.btn-primary { width: 100%; height: 48px; background: var(--vermilion, #c23b22); border-radius: var(--btn-radius, 6px); display: flex; align-items: center; justify-content: center; margin-bottom: 16px; box-shadow: 0 5px 14px rgba(194, 59, 34, 0.12); transition: all 0.2s ease; }
.btn-text { color: #ffffff; font-size: 16px; font-weight: 700; }
.is-loading { opacity: 0.7; pointer-events: none; }
.is-disabled { opacity: 0.55; }
.btn-primary:active { opacity: 0.88; transform: scale(0.98); }

.divider-row { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.divider-line { flex: 1; height: 1px; background: var(--border-light, #ecebe7); }
.divider-text { font-size: 12px; color: var(--ink-tertiary, #8b8a86); white-space: nowrap; }

.social-row { display: flex; gap: 12px; margin-bottom: 0; }
.btn-wechat { flex: 1; height: 44px; background: var(--primary-mid, #d85a42); border: 1px solid var(--primary-color, #c23b22); border-radius: var(--btn-radius, 6px); display: flex; align-items: center; justify-content: center; gap: 6px; box-shadow: none; }
.wx-icon { font-size: 20px; color: #ffffff; }
.wx-text { color: #ffffff; font-size: 14px; font-weight: 700; }
.btn-guest { flex: 1; height: 44px; background: transparent; border: 1px solid var(--border-color, #e0dfdb); border-radius: var(--btn-radius, 6px); display: flex; align-items: center; justify-content: center; }
.guest-text { color: var(--ink-secondary, #5a5956); font-size: 14px; font-weight: 600; }
.btn-guest:active { background: var(--paper-soft, #f5f5f0); }
.btn-wechat:active { opacity: 0.88; }
.bottom-safe { height: calc(env(safe-area-inset-bottom, 0px) + 12px); }

/* ── Modals ── */
.modal-mask { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(28, 25, 23,0.5); display: flex; align-items: center; justify-content: center; z-index: 999; padding: 0 var(--page-gutter, 20px); box-sizing: border-box; }
.modal-card { background: var(--card-bg, #fffefa); border-radius: var(--radius-md, 6px); padding: 24px 20px; width: 100%; max-width: 400px; border: 1px solid var(--border-color, #e0dfdb); box-shadow: var(--shadow-md); box-sizing: border-box; }
.modal-title { display: block; font-size: 18px; font-weight: 600; color: var(--ink, #2c2b29); margin-bottom: 4px; font-family: var(--font-serif, "Songti SC", STSong, serif); letter-spacing: 0.05em; }
.modal-hint { display: block; font-size: 13px; color: var(--ink-secondary, #5a5956); line-height: 1.5; }
.modal-actions { display: flex; gap: 10px; margin-top: 20px; }
.modal-btn { flex: 1; height: 46px; border-radius: var(--btn-radius, 6px); display: flex; align-items: center; justify-content: center; font-size: 14px; font-weight: 600; }
.modal-btn-cancel { background: var(--paper-soft, #f5f5f0); color: var(--ink-secondary, #5a5956); }
.modal-btn-confirm { background: var(--vermilion, #c23b22); color: #ffffff; }
.modal-btn-confirm.is-loading { opacity: 0.7; pointer-events: none; }

.agreement-modal { max-height: 80vh; display: flex; flex-direction: column; }
.agreement-scroll { flex: 1; max-height: 55vh; margin: 12px 0; }
.agreement-section-title { display: block; font-size: 13px; font-weight: 600; color: var(--ink, #2c2b29); margin-top: 14px; margin-bottom: 4px; font-family: var(--font-serif, "Songti SC", STSong, serif); letter-spacing: 0.04em; }
.agreement-body { display: block; font-size: 12px; color: var(--ink-secondary, #5a5956); line-height: 1.7; }

@media (max-width: 375px) {
  .hero {
    padding-left: var(--page-gutter-tight, 16px);
    padding-right: var(--page-gutter-tight, 16px);
    padding-bottom: 24px;
  }

  .hero-title {
    font-size: 27px;
  }

  .form-sheet {
    width: calc(100% - 32px);
    padding-left: 16px;
    padding-right: 16px;
  }

  .code-row,
  .modal-actions {
    flex-direction: column;
  }

  .btn-send-code,
  .modal-btn {
    width: 100%;
  }
}

/* ---- Dark mode ---- */
.is-dark {
  background: #1c1917;
}
.is-dark .login-page {
  background: var(--text-primary, #1c1917);
}
.is-dark .form-sheet {
  background: rgba(28, 25, 23, 0.96);
  border-color: #44403c;
}
.is-dark .sheet-title { color: #fafaf9; }
.is-dark .sheet-subtitle { color: var(--text-tertiary, #8e8e93); }
.is-dark .segment-bar { background: #292524; border-color: #44403c; }
.is-dark .seg-active { background: #44403c; color: #fafaf9; }
.is-dark .field-label { color: var(--text-tertiary, #8e8e93); }
.is-dark .field-input {
  background: #292524;
  border-color: #44403c;
  color: #fafaf9;
}
.is-dark .ph { color: var(--text-secondary, #78716c); }
.is-dark .input-error { background: rgba(239, 68, 68, 0.08) !important; border-color: #ef4444 !important; }
.is-dark .checkbox { background: #292524; border-color: #44403c; }
.is-dark .agreement-text { color: var(--text-tertiary, #8e8e93); }
.is-dark .link { color: #dd987d; }
.is-dark .forgot-link { color: #dd987d; }
.is-dark .divider-line { background: #44403c; }
.is-dark .btn-guest { background: #292524; border-color: #44403c; }
.is-dark .guest-text { color: var(--text-tertiary, #8e8e93); }
.is-dark .strength-bar { background: #44403c; }
.is-dark .modal-card { background: #292524; border-color: #44403c; }
.is-dark .modal-title { color: #fafaf9; }
.is-dark .modal-hint { color: var(--text-tertiary, #8e8e93); }
.is-dark .agreement-section-title { color: #fafaf9; }
.is-dark .agreement-body { color: var(--text-tertiary, #8e8e93); }
</style>
