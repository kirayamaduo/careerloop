import request from '@/utils/request';
import { isRealUser } from '@/utils/auth';

/**
 * Increment whenever the terms or privacy policy changes in a material way.
 * Keeping the storage key versioned makes a policy update require a fresh,
 * explicit confirmation instead of silently reusing an old checkbox.
 */
export const AGREEMENT_VERSION = '1.1';
export const CONSENT_KEY = `consent_v${AGREEMENT_VERSION}`;
export const CONSENT_RETURN_KEY = `consent_return_v${AGREEMENT_VERSION}`;

const normalizeConsentReturnUrl = (value: unknown): string => {
  const url = typeof value === 'string' ? value.trim() : '';
  if (!url || url.length > 1024) return '';
  if (!url.startsWith('/pages/') || url.startsWith('//')) return '';
  if (/[\u0000-\u001f\\#]/.test(url) || url.includes('..')) return '';
  const base = url.split('?')[0].replace(/\/+$/, '');
  if (base === '/pages/consent/index') return '';
  return url;
};

export const hasCurrentConsent = () => {
  try {
    return uni.getStorageSync(CONSENT_KEY) === '1';
  } catch {
    return false;
  }
};

export const storeCurrentConsent = () => {
  uni.setStorageSync(CONSENT_KEY, '1');
};

/**
 * Keep a cold-start share/notification route while an existing session is
 * paused for a newly-versioned agreement. Only in-app page URLs are accepted.
 */
export const rememberConsentReturnUrl = (url: string) => {
  const normalized = normalizeConsentReturnUrl(url);
  if (!normalized) return false;
  const userId = Number(uni.getStorageSync('userId'));
  uni.setStorageSync(CONSENT_RETURN_KEY, {
    url: normalized,
    userId: Number.isInteger(userId) ? userId : 0,
  });
  return true;
};

export const consumeConsentReturnUrl = (): string => {
  const stored = uni.getStorageSync(CONSENT_RETURN_KEY);
  uni.removeStorageSync(CONSENT_RETURN_KEY);
  if (!stored || typeof stored !== 'object') return '';
  const ownerId = Number((stored as { userId?: number }).userId);
  const currentUserId = Number(uni.getStorageSync('userId'));
  if (!Number.isInteger(ownerId) || ownerId !== currentUserId) return '';
  const normalized = normalizeConsentReturnUrl((stored as { url?: string }).url);
  return normalized;
};

/**
 * Consent can only be associated with an account after authentication.
 * Call this after a real login and when an already-authenticated user accepts
 * a new policy version.
 */
export const recordCurrentConsentOnServer = async () => {
  if (!isRealUser()) return false;
  let platform = 'app';
  // #ifdef MP-WEIXIN
  platform = 'miniprogram';
  // #endif
  // #ifdef H5
  platform = 'h5';
  // #endif
  await request<string>({
    url: '/api/consents',
    method: 'POST',
    data: {
      agreementVersion: AGREEMENT_VERSION,
      platform,
    },
    silent: true,
  });
  return true;
};
