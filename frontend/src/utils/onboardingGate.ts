import { getProfileSnapshotApi, type UpdateOnboardingDTO, type UserProfileSnapshot } from '@/api/user';
import { isRealUser } from '@/utils/auth';
import { ONBOARDING_SETUP_KEY } from '@/utils/onboardingSync';

export const ONBOARDING_SEEN_KEY = 'onboarding_v1_seen';

export type StoredOnboardingSetup = UpdateOnboardingDTO & {
  userId?: number;
  targetRole?: string;
};

const hasText = (value?: string | null): value is string => typeof value === 'string' && value.trim().length > 0;

const hasObjectValue = (value: unknown): value is Record<string, unknown> => {
  if (!value || typeof value !== 'object') return false;
  return Object.values(value as Record<string, unknown>).some((item) => {
    if (typeof item === 'string') return item.trim().length > 0;
    return item !== null && item !== undefined;
  });
};

const ONBOARDING_SNAPSHOT_TIMEOUT_MS = 3000;

const withTimeout = async <T>(
  start: (registerTask: (task: UniApp.RequestTask) => void) => Promise<T>,
  timeoutMs: number,
): Promise<T> => {
  let timer: ReturnType<typeof setTimeout> | undefined;
  let requestTask: UniApp.RequestTask | undefined;
  const work = start((task) => {
    requestTask = task;
  });
  // If the race times out first, the HTTP call may still finish later — absorb that rejection.
  work.catch(() => undefined);
  try {
    return await Promise.race([
      work,
      new Promise<T>((_, reject) => {
        timer = setTimeout(() => {
          requestTask?.abort();
          reject(new Error('onboarding_snapshot_timeout'));
        }, timeoutMs);
      }),
    ]);
  } finally {
    if (timer) clearTimeout(timer);
  }
};

export const readStoredOnboardingSetup = (): StoredOnboardingSetup | null => {
  const stored = uni.getStorageSync(ONBOARDING_SETUP_KEY);
  if (!stored || typeof stored !== 'object') return null;
  const setup = stored as StoredOnboardingSetup;
  const uid = Number(uni.getStorageSync('userId'));
  if (!Number.isInteger(uid) || uid <= 0) return null;
  if (setup.userId === uid) return setup;

  // Migrate the short-lived legacy shape written before setup records were
  // account-scoped. Only trust it together with the legacy completion marker,
  // then immediately scope it to the currently authenticated user.
  const legacySeen = uni.getStorageSync(ONBOARDING_SEEN_KEY);
  if (setup.userId == null && legacySeen === '1') {
    const migrated = { ...setup, userId: uid };
    markOnboardingSeen(migrated);
    return migrated;
  }
  return null;
};

export const hasSeenOnboardingForCurrentUser = (): boolean => {
  const seen = uni.getStorageSync(ONBOARDING_SEEN_KEY);
  const uid = Number(uni.getStorageSync('userId'));
  if (!uid) return false;
  return !!seen && typeof seen === 'object' && Number((seen as { userId?: number }).userId) === uid;
};

export const snapshotToOnboardingSetup = (snapshot?: UserProfileSnapshot | null): StoredOnboardingSetup | null => {
  if (!snapshot) return null;
  const onboarding = snapshot.onboarding;
  const targetRole = snapshot.preferences?.targetRole
    || snapshot.resume?.targetJob
    || snapshot.interview?.positionName
    || snapshot.assessment?.suggestedRoles?.find(hasText)
    || '';

  const setup: StoredOnboardingSetup = {
    identityType: onboarding?.identityType || onboarding?.stage,
    stage: onboarding?.stage || onboarding?.identityType,
    targetRole,
    painPoint: onboarding?.painPoint,
    hasResume: onboarding?.hasResume,
    resumeStatus: onboarding?.resumeStatus,
    timeline: onboarding?.timeline,
    education: onboarding?.education,
    weeklyAvailability: onboarding?.weeklyAvailability,
    priorityHelp: onboarding?.priorityHelp,
    recommendedEntry: onboarding?.recommendedEntry,
    onboardingCompletedAt: onboarding?.onboardingCompletedAt,
  };

  const hasOnboarding = !!onboarding && (
    hasText(onboarding.onboardingCompletedAt)
    || hasText(onboarding.identityType)
    || hasText(onboarding.stage)
    || hasText(onboarding.resumeStatus)
    || hasText(onboarding.painPoint)
    || hasText(onboarding.timeline)
    || hasText(onboarding.weeklyAvailability)
    || hasText(onboarding.priorityHelp)
    || hasObjectValue(onboarding.education)
  );

  const hasUsageTrace = !!snapshot.assessment
    || !!snapshot.resume
    || !!snapshot.interview
    || hasText(targetRole);

  return hasOnboarding || hasUsageTrace ? setup : null;
};

export const markOnboardingSeen = (setup?: StoredOnboardingSetup | null) => {
  const uid = Number(uni.getStorageSync('userId'));
  if (!Number.isInteger(uid) || uid <= 0) return;
  if (setup) uni.setStorageSync(ONBOARDING_SETUP_KEY, { ...setup, userId: uid });
  uni.setStorageSync(ONBOARDING_SEEN_KEY, { userId: uid, seenAt: new Date().toISOString() });
};

export const shouldForceOnboarding = async (): Promise<boolean> => {
  // Guests and anonymous users use the product preview/login flow. They must
  // not be sent into a wizard whose result cannot be synced.
  if (!isRealUser()) return false;

  if (hasSeenOnboardingForCurrentUser()) return false;

  const localSetup = readStoredOnboardingSetup();
  if (localSetup) {
    markOnboardingSeen(localSetup);
    return false;
  }

  try {
    const snapshot = await withTimeout(
      (registerTask) => getProfileSnapshotApi({
        timeout: ONBOARDING_SNAPSHOT_TIMEOUT_MS + 500,
        onTask: registerTask,
      }),
      ONBOARDING_SNAPSHOT_TIMEOUT_MS,
    );
    const serverSetup = snapshotToOnboardingSetup(snapshot);
    if (serverSetup) {
      markOnboardingSeen(serverSetup);
      return false;
    }
    return true;
  } catch {
    // Connectivity is not evidence that onboarding is incomplete. Let the
    // user enter and retry the check next launch instead of trapping returning
    // users in a mandatory wizard during a backend/network incident.
    return false;
  }
};
