import request from '@/utils/request';

/**
 * F10: WeChat subscribe message utility.
 *
 * Usage pattern (call after user completes an action that triggers a push):
 *
 *   import { requestSubscribe } from '@/utils/wxSubscribe';
 *   await requestSubscribe([TEMPLATE_IDS.WEEKLY_REPORT]);
 */

export interface QuotaItem {
  id: number;
  userId: number;
  templateId: string;
  remaining: number;
  updatedAt?: string;
}

export type SubscribePurpose = 'weekly' | 'assessment' | 'interview';
export type ConfiguredSubscribeTemplates = Partial<Record<SubscribePurpose, string>>;

export interface SubscribeRequestResult {
  requested: number;
  accepted: number;
  synced: boolean;
  configured: boolean;
}

const MAX_TEMPLATES_PER_REQUEST = 3;
const TEMPLATE_PURPOSE_ORDER: SubscribePurpose[] = ['interview', 'assessment', 'weekly'];
const PENDING_GRANTS_KEY = 'wx_subscribe_pending_grants_v1';

const normalizeTemplateIds = (templateIds: string[]) => Array.from(new Set(
  (templateIds || []).map((id) => String(id || '').trim()).filter(Boolean),
)).slice(0, MAX_TEMPLATES_PER_REQUEST);

/**
 * Call wx.requestSubscribeMessage for the given template IDs, then report
 * the results to the backend so it can track quota.
 *
 * Silently swallows errors — subscribe prompts are best-effort and must
 * never break the primary user flow.
 *
 * @param templateIds  list of WeChat subscribe template IDs to prompt for
 */
export async function requestSubscribe(templateIds: string[]): Promise<SubscribeRequestResult> {
  const safeTemplateIds = normalizeTemplateIds(templateIds);
  if (safeTemplateIds.length === 0) {
    return { requested: 0, accepted: 0, synced: true, configured: false };
  }

  try {
    const res: any = await new Promise((resolve) => {
      uni.requestSubscribeMessage({
        tmplIds: safeTemplateIds,
        success: (r) => resolve(r),
        fail: (e) => resolve(e),
      });
    });

    const results: Record<string, string> = {};
    safeTemplateIds.forEach((id) => {
      const value = res?.[id];
      if (value === 'accept' || value === 'reject' || value === 'ban') {
        results[id] = value;
      }
    });

    let synced = true;
    if (Object.keys(results).length > 0) {
      try {
        await reportGrantToBackend(results);
      } catch {
        synced = false;
        rememberPendingGrant(results);
      }
    }
    return {
      requested: safeTemplateIds.length,
      accepted: Object.values(results).filter((value) => value === 'accept').length,
      synced,
      configured: true,
    };
  } catch {
    return {
      requested: safeTemplateIds.length,
      accepted: 0,
      synced: false,
      configured: true,
    };
  }
}

/** Fetch only server-configured templates that have a real sender path. */
export async function getConfiguredSubscribeTemplates(): Promise<ConfiguredSubscribeTemplates> {
  return request<ConfiguredSubscribeTemplates>({
    url: '/api/wx-subscribe/templates',
    method: 'GET',
    silent: true,
  });
}

/**
 * Explicit-click entry point used by Profile and Messages. It never opens a
 * prompt on page load and never relies on a template ID bundled in the app.
 */
export async function requestConfiguredSubscribe(): Promise<SubscribeRequestResult> {
  await flushPendingGrants();
  const configured = await getConfiguredSubscribeTemplates();
  const templateIds = TEMPLATE_PURPOSE_ORDER
    .map((purpose) => configured?.[purpose])
    .filter((id): id is string => typeof id === 'string' && id.trim().length > 0);
  return requestSubscribe(templateIds);
}

/**
 * Fetch remaining quota for all templates. Useful to decide which
 * templates to include in the next requestSubscribe prompt.
 */
export async function getSubscribeQuota(): Promise<QuotaItem[]> {
  try {
    return await request<QuotaItem[]>({
      url: '/api/wx-subscribe/quota',
      method: 'GET',
      silent: true,
    });
  } catch {
    return [];
  }
}

/**
 * POST the wx.requestSubscribeMessage result map to the backend.
 * Keys are template IDs, values are "accept" | "reject" | "ban".
 */
async function reportGrantToBackend(results: Record<string, string>): Promise<void> {
  await request<string>({
    url: '/api/wx-subscribe/grant',
    method: 'POST',
    data: { results },
    silent: true,
  });
}

const readPendingGrants = (): Record<string, string>[] => {
  const value = uni.getStorageSync(PENDING_GRANTS_KEY);
  if (!Array.isArray(value)) return [];
  return value.filter((item) => item && typeof item === 'object').slice(-10);
};

const rememberPendingGrant = (results: Record<string, string>) => {
  const pending = readPendingGrants();
  pending.push(results);
  uni.setStorageSync(PENDING_GRANTS_KEY, pending.slice(-10));
};

const flushPendingGrants = async () => {
  const pending = readPendingGrants();
  if (pending.length === 0) return;
  const remaining: Record<string, string>[] = [];
  for (const results of pending) {
    try {
      await reportGrantToBackend(results);
    } catch {
      remaining.push(results);
    }
  }
  if (remaining.length > 0) {
    uni.setStorageSync(PENDING_GRANTS_KEY, remaining);
  } else {
    uni.removeStorageSync(PENDING_GRANTS_KEY);
  }
}
