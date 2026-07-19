<script setup lang="ts">
import { onLaunch } from "@dcloudio/uni-app";
import { isGuest, isLoggedIn, isRealUser, LOGIN_PAGE } from "@/utils/auth";
import { useTheme } from "@/utils/theme";
import { updateTabBar } from "@/locales/index";
import { syncPendingOnboarding } from "@/utils/onboardingSync";
import { shouldForceOnboarding } from "@/utils/onboardingGate";
import {
  hasCurrentConsent,
  recordCurrentConsentOnServer,
  rememberConsentReturnUrl,
} from "@/utils/consent";

const LANDING_KEY = 'zhihui_landing_seen';
const LANDING_PAGE = '/pages/landing/index';
const CONSENT_PAGE = '/pages/consent/index';
const ONBOARDING_PAGE = '/pages/onboarding/index';
const HOME_PAGE = '/pages/home/index';
const { refresh: refreshTheme } = useTheme();

type EntryOptions = {
  path?: string;
  query?: Record<string, unknown>;
};

const getEntryOptions = (launchOptions?: EntryOptions): EntryOptions => {
  if (launchOptions?.path) return launchOptions;
  // #ifdef MP-WEIXIN
  try {
    const wxAny: any = (globalThis as any).wx;
    const info = wxAny?.getLaunchOptionsSync?.() || wxAny?.getEnterOptionsSync?.();
    return {
      path: info?.path as string | undefined,
      query: info?.query as Record<string, unknown> | undefined,
    };
  } catch {
    return {};
  }
  // #endif
  // #ifndef MP-WEIXIN
  return launchOptions || {};
  // #endif
};

const normalizePath = (path?: string) => (path || '').replace(/^\/+/, '');

const buildEntryUrl = (entry: EntryOptions): string => {
  const path = normalizePath(entry.path);
  if (!path) return '';
  const query = Object.entries(entry.query || {})
    .filter(([, value]) => value !== undefined && value !== null)
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`)
    .join('&');
  return `/${path}${query ? `?${query}` : ''}`;
};

const bootstrap = async (launchOptions?: EntryOptions) => {
  refreshTheme();
  // pages.json 里的 tabBar text 是静态值，需在启动时用检测到的语言覆盖
  updateTabBar();

  const entry = getEntryOptions(launchOptions);
  const entryPath = normalizePath(entry.path);
  const entryUrl = buildEntryUrl(entry);
  const hasSeenLanding = uni.getStorageSync(LANDING_KEY);

  if (!isLoggedIn()) {
    // Avoid a redundant relaunch when WeChat/H5 already opened one of the
    // public entry pages. Every other anonymous cold start goes through the
    // product introduction once, then login.
    if (entryPath === normalizePath(LANDING_PAGE) || entryPath === normalizePath(LOGIN_PAGE)) {
      return;
    }
    uni.reLaunch({ url: hasSeenLanding ? LOGIN_PAGE : LANDING_PAGE });
    return;
  }

  // A material policy update pauses both real and guest sessions before any
  // account API or protected page can run. Preserve a share/notification deep
  // link and resume it only after explicit acceptance.
  if (!hasCurrentConsent()) {
    const isPublicEntry = [
      LANDING_PAGE,
      LOGIN_PAGE,
      CONSENT_PAGE,
      ONBOARDING_PAGE,
    ].some((path) => entryPath === normalizePath(path));
    if (!isPublicEntry && entryUrl) rememberConsentReturnUrl(entryUrl);
    uni.reLaunch({ url: CONSENT_PAGE });
    return;
  }

  // Guest mode is a read-only product preview. It deliberately skips the
  // profile wizard because that data cannot be synced without an account.
  if (isGuest()) {
    if (
      entryPath === normalizePath(LANDING_PAGE)
      || entryPath === normalizePath(LOGIN_PAGE)
      || entryPath === normalizePath(ONBOARDING_PAGE)
    ) {
      uni.reLaunch({ url: HOME_PAGE });
    }
    return;
  }

  // There is a single ordered launch decision: pending setup sync first,
  // onboarding gate second, and at most one redirect. This prevents the old
  // promise race where a deep link could bounce home and then onboarding.
  try {
    await syncPendingOnboarding({ silent: true });
  } catch {
    // Keep pending storage for the next launch; do not block app entry.
  }

  // A silent sync may discover an expired credential and clear it.
  if (!isRealUser()) return;

  const forceOnboarding = await shouldForceOnboarding();
  if (!isRealUser()) return;

  // Idempotently repair a consent audit write that may have failed during a
  // previous weak-network session. This must never hold up cold start.
  recordCurrentConsentOnServer().catch(() => {
    // The next authenticated launch retries again.
  });

  if (forceOnboarding && entryPath !== normalizePath(ONBOARDING_PAGE)) {
    uni.reLaunch({ url: ONBOARDING_PAGE });
    return;
  }

  // Preserve valid share paths, notification links and restored inner pages.
  // Only obsolete public entry pages are normalized for an authenticated user.
  if (
    entryPath === normalizePath(LANDING_PAGE)
    || entryPath === normalizePath(LOGIN_PAGE)
  ) {
    uni.reLaunch({ url: HOME_PAGE });
  }
};

onLaunch((options: any) => {
  void bootstrap(options);
});
</script>

<style>
@import '@/style-library/styles/index.css';
</style>
