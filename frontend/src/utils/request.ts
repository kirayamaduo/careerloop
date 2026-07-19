import { clearAuthState, isGuest, LOGIN_PAGE } from '@/utils/auth';
import { translate } from '@/locales';

const DEFAULT_API_BASE_URL = 'https://api.careerloop.top';
const BASE_URL = (import.meta.env.VITE_API_BASE_URL || DEFAULT_API_BASE_URL).replace(/\/+$/, '');
let _redirectingToLogin = false;

interface Result<T> {
  code: number;
  message: string;
  data: T;
}

const isResultEnvelope = <T>(value: unknown): value is Result<T> => {
  return !!value
    && typeof value === 'object'
    && 'code' in value
    && ('data' in value || 'message' in value);
};

const isBlockedHtmlResponse = (payload: unknown): boolean => {
  if (typeof payload !== 'string') return false;
  const s = payload.toLowerCase();
  return s.includes('<html') || s.includes('<!doctype html') || s.includes('dnspod.qcloud.com/static/webblock.html') || s.includes('webblock');
};

const handleUnauthorized = (silent?: boolean) => {
  clearAuthState();
  if (_redirectingToLogin) return;
  _redirectingToLogin = true;
  if (!silent) {
    uni.showToast({ title: translate('common.unauthorized'), icon: 'none', duration: 2000 });
  }
  setTimeout(() => {
    _redirectingToLogin = false;
    uni.reLaunch({ url: LOGIN_PAGE });
  }, 1500);
};

const hasAccountSession = () => {
  if (isGuest()) return false;
  const token = String(uni.getStorageSync('token') || '').trim();
  const userId = Number(uni.getStorageSync('userId'));
  return !!token || (Number.isInteger(userId) && userId > 0);
};

type RequestOptions = UniApp.RequestOptions & {
  silent?: boolean;   // suppress error toasts when true
  _retried?: boolean; // F21: internal flag to prevent double-retry
  params?: Record<string, unknown>;
  /** Exposes the underlying RequestTask so callers can abort abandoned requests. */
  onTask?: (task: UniApp.RequestTask) => void;
};

type UploadOptions = {
  url: string;
  filePath: string;
  name: string;
  formData?: Record<string, unknown>;
  header?: Record<string, string>;
  silent?: boolean;
};

const appendQuery = (url: string, params?: Record<string, unknown>) => {
  if (!params) return url;
  const query = Object.entries(params)
    .filter(([, value]) => value !== undefined && value !== null)
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`)
    .join('&');
  if (!query) return url;
  return `${url}${url.includes('?') ? '&' : '?'}${query}`;
};

const buildUrl = (url: string, params?: Record<string, unknown>) => {
  const target = /^https?:\/\//i.test(url) ? url : `${BASE_URL}${url.startsWith('/') ? '' : '/'}${url}`;
  return appendQuery(target, params);
};

const parsePayload = (payload: unknown) => {
  if (typeof payload !== 'string') return payload;
  const trimmed = payload.trim();
  if (!trimmed) return payload;
  if (trimmed.startsWith('{') || trimmed.startsWith('[')) {
    try { return JSON.parse(trimmed); } catch { return payload; }
  }
  return payload;
};

const getEnvelopeCode = (value: Result<unknown>) => Number(value.code);

const showError = (message: string, silent?: boolean, duration = 2500) => {
  if (!silent) {
    uni.showToast({ title: message, icon: 'none', duration });
  }
};

const canAutoRetry = (method?: string) => {
  const normalized = String(method || 'GET').toUpperCase();
  return normalized === 'GET' || normalized === 'HEAD' || normalized === 'OPTIONS';
};

/**
 * Generic Request Function — with F21 global error handling:
 *   - 401: clear token, redirect to login
 *   - 5xx: one auto-retry for read-only requests, then show toast
 *   - Network fail: friendly localized toast + reject
 */
const request = <T>(options: RequestOptions): Promise<T> => {
  return new Promise((resolve, reject) => {
    const token = uni.getStorageSync('token');
    const header: Record<string, string> = {
      'Content-Type': 'application/json',
      ...(options.header as Record<string, string>),
    };
    if (token) header['Authorization'] = `Bearer ${token}`;

    const task = uni.request({
      // AI endpoints can take up to 2 min — keep this floor high
      timeout: 120_000,
      ...options,
      url: buildUrl(options.url, options.params),
      header,
      success: (res) => {
        const statusCode = res.statusCode;
        const data = parsePayload(res.data);
        const finalUrl = (res as any)?.errMsg || '';

        // ICP/domain interception usually serves an HTML block page.
        if (isBlockedHtmlResponse(res.data) || /dnspod|webblock/i.test(finalUrl)) {
          const blockedMsg = translate('common.apiBlocked');
          showError(blockedMsg, options.silent, 3000);
          reject(new Error(blockedMsg));
          return;
        }

        if (statusCode >= 200 && statusCode < 300) {
          if (isResultEnvelope<T>(data)) {
            // Some backend handlers return HTTP 200 with business code 401.
            // `silent` only suppresses the toast; an expired real session must
            // still be cleared so callers never keep using a stale token.
            const code = getEnvelopeCode(data);
            if (code === 401) {
              if (hasAccountSession()) handleUnauthorized(options.silent);
              reject(new Error('Unauthorized'));
              return;
            }
            if (code === 200) {
              resolve(data.data);
              return;
            }

            const errorMsg = data.message || translate('common.requestFailed', { status: statusCode });
            showError(errorMsg, options.silent);
            reject(new Error(errorMsg));
            return;
          }

          // Some endpoints return raw arrays/objects/files instead of the
          // common Result envelope. A 2xx HTTP status is still a success.
          resolve(data as T);
          return;
        }

        // F21: 401 → clear expired real sessions even for silent background
        // requests. Guest preview owns no credential, so a protected endpoint
        // simply rejects and lets the page display its login gate.
        if (statusCode === 401) {
          if (hasAccountSession()) handleUnauthorized(options.silent);
          reject(new Error('Unauthorized'));
          return;
        }

        // Never replay mutations automatically: a server/proxy can return 5xx
        // after the write has already committed.
        if (statusCode >= 500 && !options._retried && canAutoRetry(options.method)) {
          setTimeout(() => {
            request<T>({ ...options, _retried: true }).then(resolve).catch(reject);
          }, 1500);
          return;
        }

        const errorMsg = isResultEnvelope<T>(data) && data.message
          ? data.message
          : translate('common.requestFailed', { status: statusCode });
        showError(errorMsg, options.silent);
        reject(new Error(errorMsg));
      },
      fail: (err) => {
        const errMsg = String((err as UniApp.GeneralCallbackResult)?.errMsg || '');
        // Aborted by an intentional race/timeout — never toast or surface as a hard failure.
        if (/abort/i.test(errMsg)) {
          reject(new Error('Request aborted'));
          return;
        }
        const message = translate('common.networkError');
        showError(message, options.silent);
        reject(new Error(message));
      },
    });
    options.onTask?.(task);
  });
};

export const uploadFileRequest = <T>(options: UploadOptions): Promise<T> => {
  return new Promise((resolve, reject) => {
    const token = uni.getStorageSync('token');
    const header: Record<string, string> = {
      ...(options.header || {}),
    };
    if (token) header.Authorization = `Bearer ${token}`;

    uni.uploadFile({
      url: buildUrl(options.url),
      filePath: options.filePath,
      name: options.name,
      formData: options.formData as Record<string, any>,
      header,
      success: (res) => {
        const body = parsePayload(res.data);
        if (isBlockedHtmlResponse(res.data)) {
          const blockedMsg = translate('common.apiBlocked');
          showError(blockedMsg, options.silent, 3000);
          reject(new Error(blockedMsg));
          return;
        }

        if (res.statusCode === 401) {
          if (hasAccountSession()) handleUnauthorized(options.silent);
          reject(new Error('Unauthorized'));
          return;
        }

        if (res.statusCode >= 200 && res.statusCode < 300) {
          if (isResultEnvelope<T>(body)) {
            const code = getEnvelopeCode(body);
            if (code === 401) {
              if (hasAccountSession()) handleUnauthorized(options.silent);
              reject(new Error('Unauthorized'));
              return;
            }
            if (code === 200) {
              resolve(body.data);
              return;
            }
            reject(new Error(body.message || translate('common.uploadCodeFailed', { code: body.code })));
            return;
          }
          resolve(body as T);
          return;
        }

        const errorMsg = isResultEnvelope<T>(body) && body.message
          ? body.message
          : translate('common.uploadFailed', { status: res.statusCode });
        showError(errorMsg, options.silent);
        reject(new Error(errorMsg));
      },
      fail: (err) => {
        const message = (err as any)?.errMsg || translate('common.uploadNetworkError');
        showError(message, options.silent);
        reject(new Error(message));
      },
    });
  });
};

export default request;
