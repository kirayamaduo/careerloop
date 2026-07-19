/**
 * Auth + guest helpers shared across pages.
 *
 * The mini-program supports two "logged-in" states:
 *  - Real account  → `userId` is a positive integer + `token` is set.
 *  - Guest preview → `isGuest` flag + `userId === -1`. No token; auth-required
 *    APIs are gated behind {@link requireAuth} so the guest is steered to the
 *    sign-in flow at the moment they reach for a feature that needs a real
 *    account (e.g. starting an interview, uploading a resume).
 *
 * Storing `userId === -1` for guests is what fixes the cold-start bug where
 * guests were silently kicked back to the login page on app relaunch — the
 * App.vue boot check is now happy with either a real id or the guest sentinel.
 */
export const LOGIN_PAGE = '/pages/login/index';

export const GUEST_USER_ID = -1;

export const isGuest = (): boolean => {
  const userId = Number(uni.getStorageSync('userId'));
  const guestFlag = uni.getStorageSync('isGuest');
  return (guestFlag === true || guestFlag === '1') && userId === GUEST_USER_ID;
};

export const isLoggedIn = (): boolean => {
  return isRealUser() || isGuest();
};

/** Real account check — false for guest sessions. */
export const isRealUser = (): boolean => {
  const userId = Number(uni.getStorageSync('userId'));
  const token = String(uni.getStorageSync('token') || '').trim();
  return Number.isInteger(userId) && userId > 0 && !!token && !isGuest();
};

const ACCOUNT_SCOPED_ONBOARDING_KEYS = [
  'onboarding_v1_seen',
  'career_onboarding_setup',
  'career_onboarding_pending',
] as const;

const getStoredOwnerId = (key: typeof ACCOUNT_SCOPED_ONBOARDING_KEYS[number]) => {
  const value = uni.getStorageSync(key);
  if (!value || typeof value !== 'object') return NaN;
  return Number((value as { userId?: number }).userId);
};

const removeCurrentAccountOnboardingState = () => {
  const currentUserId = Number(uni.getStorageSync('userId'));
  ACCOUNT_SCOPED_ONBOARDING_KEYS.forEach((key) => {
    const ownerId = getStoredOwnerId(key);
    if (!Number.isInteger(ownerId)
        || ownerId <= 0
        || !Number.isInteger(currentUserId)
        || currentUserId <= 0
        || ownerId === currentUserId) {
      uni.removeStorageSync(key);
    }
  });
};

/**
 * Preserve only records explicitly bound to an account.
 * Readers also verify `userId`, so another account can never consume them.
 * Legacy/unbound records are removed instead of being carried across sessions.
 */
const retainOwnedOnboardingState = () => {
  ACCOUNT_SCOPED_ONBOARDING_KEYS.forEach((key) => {
    const ownerId = getStoredOwnerId(key);
    if (!Number.isInteger(ownerId) || ownerId <= 0) {
      uni.removeStorageSync(key);
    }
  });
};

export const enterGuestMode = () => {
  retainOwnedOnboardingState();
  uni.removeStorageSync('token');
  uni.setStorageSync('userId', GUEST_USER_ID);
  uni.setStorageSync('isGuest', true);
  uni.setStorageSync('userInfo', { nickname: '游客预览', avatarUrl: '', isGuest: true });
};

export type ClearAuthStateOptions = {
  /**
   * Account deletion is the only normal flow that should destroy local setup
   * drafts. Logout and an expired token preserve safely account-bound drafts.
   */
  purgeAccountDrafts?: boolean;
};

export const clearAuthState = (options: ClearAuthStateOptions = {}) => {
  if (options.purgeAccountDrafts) {
    removeCurrentAccountOnboardingState();
  } else {
    retainOwnedOnboardingState();
  }
  uni.removeStorageSync('token');
  uni.removeStorageSync('userId');
  uni.removeStorageSync('userInfo');
  uni.removeStorageSync('isGuest');
};

export type RequireAuthOptions = {
  /** How to open the login page after confirmation. */
  redirect?: 'reLaunch' | 'navigateTo';
  /** Optional feature-specific explanation shown in the login prompt. */
  message?: string;
  /** Protected standalone pages return to the previous/public page on cancel. */
  cancelBehavior?: 'stay' | 'back';
};

let authPromptOpen = false;

/**
 * Gate a feature that needs a real account (interview start, resume upload,
 * assessment submission, etc.). The prompt happens before the user invests
 * effort, and navigation only occurs after an explicit confirmation.
 *
 * The string form remains supported for older call sites.
 */
export const requireAuth = (
  options: RequireAuthOptions | 'reLaunch' | 'navigateTo' = {},
): boolean => {
  if (isRealUser()) return true;

  if (authPromptOpen) return false;
  authPromptOpen = true;

  const normalized: RequireAuthOptions = typeof options === 'string'
    ? { redirect: options }
    : options;
  const redirect = normalized.redirect || 'navigateTo';
  const cancelBehavior = normalized.cancelBehavior || 'stay';
  const content = normalized.message
    || (isGuest()
      ? '当前为游客预览。登录后即可保存进度并使用完整功能。'
      : '登录后即可使用此功能并保存你的进度。');

  uni.showModal({
    title: '登录后使用',
    content,
    confirmText: '去登录',
    cancelText: cancelBehavior === 'back' ? '返回' : '继续预览',
    success: (result) => {
      if (!result.confirm) {
        if (cancelBehavior === 'back') {
          const pages = getCurrentPages();
          if (pages.length > 1) {
            uni.navigateBack();
          } else {
            uni.switchTab({ url: '/pages/home/index' });
          }
        }
        return;
      }
      if (redirect === 'reLaunch') {
        uni.reLaunch({ url: LOGIN_PAGE });
      } else {
        uni.navigateTo({ url: LOGIN_PAGE });
      }
    },
    complete: () => {
      authPromptOpen = false;
    },
  });

  return false;
};
