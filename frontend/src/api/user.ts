import request from '@/utils/request';

export interface User {
  userId?: number;
  nickname: string;
  /**
   * Per-account OSS object key (e.g. `avatars/42/uuid.jpg`). Not loadable directly — use
   * `avatarViewUrl` for display.
   */
  avatarUrl?: string;
  /** Short-lived signed avatar URL hydrated on every read. */
  avatarViewUrl?: string;
  school?: string;
  major?: string;
  graduationYear?: number;
  points?: number;
  isVip?: boolean;
  status?: number;
}

export interface RegisterDTO {
  nickname: string;
  identityType: string; // 'EMAIL_PASSWORD'
  identifier: string;   // email
  credential: string;   // password
  code: string;         // email verification code
}

export interface LoginDTO {
  identityType: string;
  identifier: string;
  credential: string;
}

/**
 * User Register API
 */
export interface LoginResponse {
  token: string;
  user: User;
  /** Present and true when login cancelled a pending account deletion. */
  accountRestored?: boolean;
}

export const registerApi = (data: RegisterDTO) => {
  return request<User>({
    url: '/auth/register',
    method: 'POST',
    data,
  });
};

/**
 * User Login API
 */
export const loginApi = (data: LoginDTO) => {
  return request<LoginResponse>({
    url: '/auth/login',
    method: 'POST',
    data,
  });
};

/**
 * WeChat Login API
 */
export const wechatLoginApi = (data: { code: string }) => {
  return request<LoginResponse>({
    url: '/auth/wechat-login',
    method: 'POST',
    data,
  });
};

/**
 * Check if email is already registered
 */
export const checkEmailApi = (email: string) => {
  return request<boolean>({
    url: '/auth/check-email',
    method: 'POST',
    data: { email },
  });
};

/**
 * Send Email Verification Code
 */
export const sendCodeApi = (data: { email: string; purpose: 'REGISTER' | 'RESET' }) => {
  return request<string>({
    url: '/auth/send-code',
    method: 'POST',
    data,
  });
};

/**
 * Reset Password with Email Code
 */
export const resetPasswordApi = (data: { email: string; code: string; newPassword: string }) => {
  return request<string>({
    url: '/auth/reset-password',
    method: 'POST',
    data,
  });
};

/** Change the signed-in user's password and revoke all existing sessions. */
export const changePasswordApi = (data: { currentPassword: string; newPassword: string }) => {
  return request<string>({
    url: '/auth/change-password',
    method: 'POST',
    data,
  });
};

/** Server-side sign-out. Success invalidates every token for this account. */
export const logoutApi = () => {
  return request<string>({
    url: '/auth/logout',
    method: 'POST',
    silent: true,
  });
};

/**
 * Get User Info
 */
export const getUserInfoApi = (userId: number) => {
  return request<User>({
    url: `/users/${userId}`,
    method: 'GET',
  });
};

export interface UpdateUserDTO {
  nickname?: string;
  /** OSS object key from `uploadFileApi(filePath, 'avatars')`. */
  avatarUrl?: string;
  school?: string;
  major?: string;
  graduationYear?: number;
  /** Explicitly clear the nullable year; omission continues to mean no change. */
  clearGraduationYear?: boolean;
}

/**
 * Patch the current user's profile. Any field omitted is left unchanged.
 * Server enforces that `userId` matches the JWT subject (FORBIDDEN otherwise).
 */
export const updateUserApi = (userId: number, data: UpdateUserDTO) => {
  return request<User>({
    url: `/users/${userId}`,
    method: 'PUT',
    data,
  });
};

/**
 * Cross-tool user portrait. Each block can be null when the user hasn't
 * touched that tool yet, so always nullable-guard before reading.
 */
export interface UserProfileSnapshot {
  version?: number;
  updatedAt?: string;
  assessment?: {
    lastRecordId?: number;
    scaleId?: number;
    scaleTitle?: string;
    summary?: string;
    suggestedRoles?: string[];
    completedAt?: string;
  } | null;
  resume?: {
    lastResumeId?: number;
    lastResumeKey?: string;
    title?: string;
    targetJob?: string;
    diagnosisScore?: number;
    updatedAt?: string;
  } | null;
  interview?: {
    lastInterviewId?: number;
    positionName?: string;
    difficulty?: string;
    lastScore?: number;
    weakDimensions?: string[];
    strongDimensions?: string[];
    completedAt?: string;
  } | null;
  preferences?: {
    targetRole?: string;
    interviewMode?: 'voice' | 'text';
  } | null;
  onboarding?: {
    identityType?: 'student' | 'new_graduate' | 'internship_seeker' | 'career_switcher' | string;
    /** User self-reported resume state from onboarding, not proof that a resume exists in the system. */
    hasResume?: 'yes' | 'no' | 'unsure' | string;
    stage?: string;
    painPoint?: string;
    resumeStatus?: string;
    timeline?: string;
    education?: {
      school?: string;
      major?: string;
      degree?: string;
      graduationYear?: string;
    };
    weeklyAvailability?: string;
    priorityHelp?: string;
    recommendedEntry?: string;
    onboardingCompletedAt?: string;
  } | null;
}

/**
 * Get the caller's cross-tool profile snapshot. Always resolves with
 * a snapshot object (possibly empty) — never null.
 */
export type ProfileSnapshotRequestOptions = {
  silent?: boolean;
  timeout?: number;
  onTask?: (task: UniApp.RequestTask) => void;
};

export const getProfileSnapshotApi = (options?: ProfileSnapshotRequestOptions) => {
  return request<UserProfileSnapshot>({
    url: '/users/me/profile-snapshot',
    method: 'GET',
    silent: options?.silent ?? true,
    timeout: options?.timeout,
    onTask: options?.onTask,
  });
};

/**
 * Patch preferences (target role / chosen interview mode). Returns the
 * full snapshot after the merge.
 */
export const updatePreferencesApi = (data: { targetRole?: string; interviewMode?: 'voice' | 'text' }) => {
  return request<UserProfileSnapshot>({
    url: '/users/me/profile-snapshot/preferences',
    method: 'PUT',
    data,
  });
};

export interface UpdateOnboardingDTO {
  identityType?: 'student' | 'new_graduate' | 'internship_seeker' | 'career_switcher' | string;
  /** User self-reported resume state from onboarding, not proof that a resume exists in the system. */
  hasResume?: 'yes' | 'no' | 'unsure' | string;
  stage?: string;
  painPoint?: string;
  resumeStatus?: string;
  timeline?: string;
  education?: {
    school?: string;
    major?: string;
    degree?: string;
    graduationYear?: string;
  };
  weeklyAvailability?: string;
  priorityHelp?: string;
  recommendedEntry?: string;
  onboardingCompletedAt?: string;
  /** Persisted server-side into preferences.targetRole. */
  targetRole?: string;
}

export const updateOnboardingApi = (data: UpdateOnboardingDTO, options?: { silent?: boolean }) => {
  return request<UserProfileSnapshot>({
    url: '/users/me/profile-snapshot/onboarding',
    method: 'PUT',
    data,
    silent: options?.silent,
  });
};

/**
 * F25: Request account deletion (30-day soft-delete grace period).
 * After calling this the JWT will return 410 on subsequent requests.
 */
export const requestDeletionApi = () => {
  return request<string>({
    url: '/users/me',
    method: 'DELETE',
  });
};

/**
 * F25: Cancel a pending deletion request within the 30-day grace period.
 */
export const cancelDeletionApi = () => {
  return request<string>({
    url: '/users/me/cancel-deletion',
    method: 'POST',
  });
};
