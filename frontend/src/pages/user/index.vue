<template>
  <view class="user-page app-soft-bg" :class="[themeClass, fontClass]">
    <SlScrollTopBar :title="t('profile.title')" :opacity="topBarOpacity" :safe-top="topSafeHeight" :right-avoid-width="rightAvoidWidth" />
    <view class="status-spacer" :style="{ height: topSafeHeight + 'px' }"></view>

    <view class="page-header" :style="{ paddingRight: rightAvoidWidth + 'px' }">
      <text class="page-title">{{ t('profile.title') }}</text>
      <text class="page-subtitle">{{ t('profile.subtitle') }}</text>
    </view>

    <!-- Header card: logged in -->
    <view class="header-card" v-if="isLoggedIn">
      <view class="header-avatar" @click="handleAvatarClick">
        <image
          class="avatar-img"
          :src="avatarSrc"
          mode="aspectFill"
        />
      </view>
      <view class="header-info">
        <text class="header-name">{{ userInfo.nickname || t('profile.defaultUser') }}</text>
        <text class="header-school" v-if="userInfo.school">{{ userInfo.school }}</text>
      </view>
      <view class="header-edit" @click.stop="openProfileEdit">
        <text class="header-edit-text">{{ userInfo.school ? t('profile.edit') : t('profile.complete') }}</text>
        <text class="header-edit-arrow">›</text>
      </view>
    </view>

    <!-- Header card: not logged in -->
    <view class="header-card header-guest" v-else>
      <text class="guest-title">{{ t('profile.notSignedIn') }}</text>
      <text class="guest-desc">{{ t('profile.notSignedInDesc') }}</text>
      <button class="btn-login" @click="goLogin">{{ t('profile.signIn') }}</button>
    </view>

    <!-- Stats bar -->
    <view class="stats-bar app-surface" v-if="isLoggedIn">
      <view class="stat-item">
        <text class="stat-val">{{ statsInterviews }}</text>
        <text class="stat-label">{{ t('profile.interviews') }}</text>
      </view>
      <view class="stat-divider"></view>
      <view class="stat-item">
        <text class="stat-val">{{ statsResumes }}</text>
        <text class="stat-label">{{ t('profile.resumes') }}</text>
      </view>
    </view>

    <view class="website-link-card app-card-soft" v-if="isLoggedIn">
      <view class="website-link-head">
        <view class="website-link-icon"><text class="ri-links-line"></text></view>
        <view class="website-link-copy">
          <text class="website-link-title">{{ t('profile.websiteLinkTitle') }}</text>
          <text class="website-link-desc">{{ t('profile.websiteLinkDesc') }}</text>
        </view>
        <view
          class="website-link-state"
          :class="{ 'website-link-state-unavailable': !websiteLinkConfigured }"
        ><text>{{ websiteLinkConfigured ? t('profile.websiteLinkSecure') : t('profile.websiteLinkUnavailableShort') }}</text></view>
      </view>
      <template v-if="websiteLinkConfigured">
        <view v-if="websiteLinkCode" class="website-code-panel">
          <view>
            <text class="website-code-label">{{ t('profile.websiteLinkCodeLabel') }}</text>
            <text class="website-code">{{ websiteLinkCode }}</text>
            <text class="website-code-expiry">{{ websiteLinkExpiryText }}</text>
          </view>
          <view class="website-code-copy" @click="copyWebsiteLinkCode">
            <text class="ri-file-copy-line"></text>
            <text>{{ t('profile.websiteLinkCopy') }}</text>
          </view>
        </view>
        <view class="website-destination">
          <view class="website-step"><text class="website-step-index">1</text><text>{{ t('profile.websiteLinkStepOpen') }}</text></view>
          <view class="website-url-row" @click="copyWebsiteDestination">
            <text class="website-url">{{ websitePassportUrl }}</text>
            <text class="ri-file-copy-line"></text>
          </view>
          <view class="website-step"><text class="website-step-index">2</text><text>{{ t('profile.websiteLinkStepEnter') }}</text></view>
        </view>
        <view
          class="website-link-action"
          :class="{ disabled: websiteLinkLoading }"
          @click="generateWebsiteLinkCode"
        >
          <text>{{ websiteLinkLoading ? t('profile.websiteLinkGenerating') : (websiteLinkCode ? t('profile.websiteLinkRegenerate') : t('profile.websiteLinkGenerate')) }}</text>
          <text class="ri-arrow-right-line"></text>
        </view>
      </template>
      <view v-else class="website-link-unavailable">
        <text class="website-link-unavailable-icon ri-shield-keyhole-line"></text>
        <view>
          <text class="website-link-unavailable-title">{{ t('profile.websiteLinkUnavailable') }}</text>
          <text class="website-link-unavailable-desc">{{ t('profile.websiteLinkUnavailableDesc') }}</text>
        </view>
      </view>
      <text class="website-link-note">{{ t('profile.websiteLinkNote') }}</text>
    </view>

    <view class="tag-cloud-card app-card-soft" v-if="isLoggedIn">
      <view class="tag-cloud-head">
        <view>
          <text class="tag-cloud-title">{{ t('profile.profileTagsTitle') }}</text>
          <text class="tag-cloud-subtitle">{{ t('profile.profileTagsSubtitle') }}</text>
        </view>
        <view class="tag-cloud-edit" @click="navTo('/pages/agent/profile')">
          <text class="tag-cloud-edit-text">{{ t('profile.edit') }}</text>
        </view>
      </view>
      <view v-if="wordCloudTags.length" class="tag-cloud">
        <view
          v-for="tag in wordCloudTags"
          :key="(tag.category || 'misc') + '-' + tag.label"
          class="tag-cloud-word"
          :class="'tag-cat-' + (tag.category || 'misc').toLowerCase()"
          :style="{
            left: tag.left + '%',
            top: tag.top + '%',
            fontSize: tag.fontSize + 'rpx',
            transform: `translate(-50%, -50%) rotate(${tag.rotate}deg)`,
            zIndex: tag.zIndex,
            opacity: tag.opacity,
          }"
        >
          <text class="tag-cloud-word-text">{{ tag.label }}</text>
        </view>
      </view>
      <view v-else class="tag-cloud-empty" @click="navTo('/pages/resume/index')">
        <text class="tag-cloud-empty-title">暂无可展示关键词</text>
        <text class="tag-cloud-empty-desc">上传简历或编辑画像后，系统会提取小词元并汇总到这里。</text>
      </view>
    </view>

    <!-- Menu group 1: My Assets -->
    <text class="group-label">{{ t('profile.assets') }}</text>
    <view class="menu-card app-card-soft">
      <view class="menu-item" @click="goResumes">
        <view class="app-icon-tile app-icon-tile--cyan menu-icon-wrap"><text class="menu-icon ri-file-text-line"></text></view>
        <text class="menu-text">{{ t('profile.resumeHub') }}</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @click="navTo('/pages/assessment/index')">
        <view class="app-icon-tile app-icon-tile--violet menu-icon-wrap"><text class="menu-icon ri-edit-box-line"></text></view>
        <text class="menu-text">{{ t('profile.myAssessments') }}</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @click="navTo('/pages/interview/history')">
        <view class="app-icon-tile app-icon-tile--warning menu-icon-wrap"><text class="menu-icon ri-briefcase-line"></text></view>
        <text class="menu-text">{{ t('profile.interviewRecords') }}</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" v-if="isLoggedIn" @click="navTo('/pages/user/memory')">
        <view class="app-icon-tile app-icon-tile--candy menu-icon-wrap"><text class="menu-icon ri-brain-line"></text></view>
        <text class="menu-text">{{ t('profile.aiMemory') }}</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" v-if="isLoggedIn" @click="navTo('/pages/messages/index')">
        <view class="app-icon-tile menu-icon-wrap"><text class="menu-icon ri-notification-3-line"></text></view>
        <text class="menu-text">{{ t('profile.systemMessages') }}</text>
        <text class="menu-arrow">›</text>
      </view>
      <!-- #ifdef MP-WEIXIN -->
      <view class="menu-item" v-if="isLoggedIn" @click="enableWechatReminders">
        <view class="app-icon-tile app-icon-tile--cyan menu-icon-wrap"><text class="menu-icon ri-notification-badge-line"></text></view>
        <text class="menu-text">{{ t('profile.wechatReminderTitle') }}</text>
        <text class="menu-action-text">{{ wechatReminderLoading ? t('profile.wechatReminderLoading') : t('profile.wechatReminderEnable') }}</text>
      </view>
      <!-- #endif -->
    </view>

    <!-- Menu group 2: Appearance & Accessibility -->
    <text class="group-label">{{ t('profile.appearance') }}</text>
    <view class="menu-card app-card-soft">
      <view class="menu-item">
        <view class="app-icon-tile menu-icon-wrap"><text class="menu-icon ri-palette-line"></text></view>
        <text class="menu-text">{{ t('profile.theme') }}</text>
        <view class="theme-pills">
          <view class="pill" :class="{ 'pill-active': theme === 'light' }" @click="applyTheme('light')">
            <text class="ri-sun-line"></text>
          </view>
          <view class="pill" :class="{ 'pill-active': theme === 'dark' }" @click="applyTheme('dark')">
            <text class="ri-moon-line"></text>
          </view>
          <view class="pill" :class="{ 'pill-active': theme === 'green' }" @click="applyTheme('green')">
            <text class="ri-leaf-line"></text>
          </view>
        </view>
      </view>
      <view class="menu-item">
        <view class="app-icon-tile app-icon-tile--cyan menu-icon-wrap"><text class="menu-icon ri-font-size-2"></text></view>
        <text class="menu-text">{{ t('profile.fontSize') }}</text>
        <view class="font-pills">
          <view class="pill" :class="{ 'pill-active': font === 'compact' }" @click="applyFont('compact')"><text>{{ t('profile.fontSmall') }}</text></view>
          <view class="pill" :class="{ 'pill-active': font === 'standard' }" @click="applyFont('standard')"><text>{{ t('profile.fontMedium') }}</text></view>
          <view class="pill" :class="{ 'pill-active': font === 'large' }" @click="applyFont('large')"><text>{{ t('profile.fontLarge') }}</text></view>
        </view>
      </view>
      <view class="menu-item">
        <view class="app-icon-tile app-icon-tile--violet menu-icon-wrap"><text class="menu-icon ri-global-line"></text></view>
        <text class="menu-text">{{ t('profile.language') }}</text>
        <view class="font-pills">
          <view
            class="pill"
            :class="{ 'pill-active': currentLang === 'zh-CN' }"
            @click="applyLang('zh-CN')"
          ><text>中文</text></view>
          <view
            class="pill"
            :class="{ 'pill-active': currentLang === 'en-US' }"
            @click="applyLang('en-US')"
          ><text>EN·Beta</text></view>
        </view>
      </view>
    </view>

    <!-- Menu group 3: Legal & Support -->
    <text class="group-label">{{ t('profile.legalAndSupport') }}</text>
    <view class="menu-card app-card-soft">
      <view class="menu-item" @click="navTo('/pages/user/feedback')">
        <view class="app-icon-tile app-icon-tile--candy menu-icon-wrap"><text class="menu-icon ri-chat-3-line"></text></view>
        <text class="menu-text">{{ t('profile.feedback') }}</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @click="openConsent('privacy')">
        <view class="app-icon-tile app-icon-tile--warning menu-icon-wrap"><text class="menu-icon ri-lock-line"></text></view>
        <text class="menu-text">{{ t('profile.privacyPolicy') }}</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @click="openConsent('terms')">
        <view class="app-icon-tile app-icon-tile--cyan menu-icon-wrap"><text class="menu-icon ri-clipboard-line"></text></view>
        <text class="menu-text">{{ t('profile.termsOfService') }}</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item menu-item-danger" v-if="isLoggedIn" @click="handleDeleteAccount">
        <view class="app-icon-tile menu-icon-wrap"><text class="menu-icon ri-delete-bin-line"></text></view>
        <text class="menu-text menu-text-danger">{{ t('profile.deleteAccount') }}</text>
        <text class="menu-arrow menu-arrow-danger">›</text>
      </view>
    </view>

    <view class="bottom-section">
      <!-- Logout -->
      <button class="btn-logout app-card-soft" v-if="isLoggedIn" @click="handleLogout">{{ t('profile.signOut') }}</button>
      <view class="bottom-safe"></view>
    </view>

    <!-- Profile Edit Modal -->
    <view class="modal-overlay" v-if="showProfileEdit" @click="showProfileEdit = false">
      <view class="modal-content bottom-sheet app-surface" @click.stop>
        <view class="sheet-handle"></view>
        <text class="modal-title">{{ t('profile.editProfile') }}</text>
        
        <view class="form-group">
          <text class="field-label">{{ t('profile.nickname') }}</text>
          <input class="field-input ui-input" v-model="editForm.nickname" :placeholder="t('profile.nicknamePlaceholder')" placeholder-class="ph" maxlength="64" />
        </view>
        <view class="form-group">
          <text class="field-label">{{ t('profile.schoolUniversity') }}</text>
          <input class="field-input ui-input" v-model="editForm.school" :placeholder="t('profile.schoolPlaceholder')" placeholder-class="ph" />
        </view>
        <view class="form-group">
          <text class="field-label">{{ t('profile.major') }}</text>
          <input class="field-input ui-input" v-model="editForm.major" :placeholder="t('profile.majorPlaceholder')" placeholder-class="ph" />
        </view>
        <view class="form-group">
          <text class="field-label">{{ t('profile.graduationYear') }}</text>
          <input class="field-input ui-input" v-model="editForm.graduationYear" type="number" maxlength="4" :placeholder="t('profile.gradYearPlaceholder')" placeholder-class="ph" />
        </view>

        <view class="modal-actions">
          <button class="btn-secondary" @click="showProfileEdit = false">{{ t('common.cancel') }}</button>
          <button class="btn-primary" :disabled="savingProfile" @click="saveProfile">{{ savingProfile ? t('profile.saving') : t('common.save') }}</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onUnmounted } from 'vue';
import { onShow, onPageScroll } from '@dcloudio/uni-app';
import { useI18n } from '@/locales';
import { clearAuthState, isRealUser, LOGIN_PAGE, requireAuth } from '@/utils/auth';
import { getMpSafeAreaMetrics } from '@/utils/safeArea';
import { getUserInterviewsApi } from '@/api/interview';
import { listMyResumesApi } from '@/api/resume';
import { updateUserApi, getUserInfoApi, requestDeletionApi, logoutApi } from '@/api/user';
import { uploadFileApi } from '@/api/file';
import { getProfileTagsApi, type UserProfileTag } from '@/api/profileTags';
import { createWebsiteLinkCodeApi } from '@/api/integration';
import { isCloudKeyword } from '@/utils/profileTagFilters';
import { useTheme, type FontKey, type ThemeKey } from '@/utils/theme';
import { setLocale, currentLocale, type LangCode } from '@/locales/index';
import SlScrollTopBar from '@/style-library/components/SlScrollTopBar.vue';
import { requestConfiguredSubscribe } from '@/utils/wxSubscribe';

const { t } = useI18n();
const { theme, font, themeClass, fontClass, setTheme, setFont } = useTheme();
const currentLang = ref<LangCode>(currentLocale());

const applyTheme = (themeKey: ThemeKey) => {
  setTheme(themeKey);
  const toastKey = themeKey === 'dark'
    ? 'profile.themeDarkToast'
    : themeKey === 'green'
      ? 'profile.themeGreenToast'
      : 'profile.themeLightToast';
  uni.showToast({ title: t(toastKey), icon: 'none' });
};

const applyFont = (fontKey: FontKey) => {
  setFont(fontKey);
  uni.showToast({ title: t('profile.fontUpdated'), icon: 'none' });
};

const applyLang = (lang: LangCode) => {
  if (lang === currentLang.value) return;
  setLocale(lang);
  currentLang.value = lang;
  const toastTitle = lang === 'zh-CN' ? t('profile.languageZhToast') : t('profile.languageEnToast');
  uni.showToast({ title: toastTitle, icon: 'none' });
  // 微信小程序 tabBar 页面常驻内存，切换语言后不会自动重渲染。
  // reLaunch 销毁所有页面栈并重建，确保所有页面以新语言重新加载。
  setTimeout(() => {
    uni.reLaunch({ url: '/pages/home/index' });
  }, 800);
};

const userInfo = ref({
  nickname: '',
  /** OSS object key — what we persist. Never feed to <image> directly. */
  avatarUrl: '',
  /** Short-lived signed URL hydrated by backend on every read. */
  avatarViewUrl: '',
  school: '',
  major: '',
  graduationYear: '',
});
const userId = ref('');

/**
 * What the <image> actually loads. Priority:
 *   1. presigned avatarViewUrl returned by the backend
 *   2. raw avatarUrl if it's still a legacy https URL (transitional)
 *   3. local default
 */
const avatarSrc = computed(() => {
  if (userInfo.value.avatarViewUrl) return userInfo.value.avatarViewUrl;
  const raw = userInfo.value.avatarUrl;
  if (raw && /^https?:\/\//i.test(raw)) return raw;
  return '/static/default-avatar.png';
});
const topSafeHeight = ref(44);
const scrollTopValue = ref(0);
const topBarOpacity = computed(() => Math.min(1, Math.max(0, (scrollTopValue.value - 12) / 56)));

const statsInterviews = ref(0);
const statsResumes = ref(0);
const profileTags = ref<UserProfileTag[]>([]);
const rightAvoidWidth = ref(20);

const showProfileEdit = ref(false);
const editForm = ref({ nickname: '', school: '', major: '', graduationYear: '' });
const savingProfile = ref(false);
const wechatReminderLoading = ref(false);
const websiteLinkCode = ref('');
const websiteLinkExpiresAt = ref(0);
const websiteLinkLoading = ref(false);
let websiteLinkExpiryTimer: ReturnType<typeof setTimeout> | undefined;
let websiteLinkAuthorizationConfirmed = false;

const isTrustedWebsitePassportUrl = (raw: string) => {
  const value = raw.trim();
  const match = value.match(/^https:\/\/([a-z0-9.-]+)(?::([0-9]{1,5}))?(\/[^?#]*)?$/i);
  if (!match) return false;
  const hostname = match[1].toLowerCase();
  const port = match[2] ? Number(match[2]) : 443;
  const pathname = (match[3] || '/').replace(/\/+$/, '') || '/';
  if (!hostname.includes('.') || hostname === 'localhost' || hostname.endsWith('.local')) return false;
  if (/^[0-9.]+$/.test(hostname) || hostname.includes('..')) return false;
  if (!Number.isInteger(port) || port < 1 || port > 65535) return false;
  return pathname === '/passport';
};

const configuredWebsitePassportUrl = String(import.meta.env.VITE_WEBSITE_PASSPORT_URL || '').trim();
const websitePassportUrl = isTrustedWebsitePassportUrl(configuredWebsitePassportUrl)
  ? configuredWebsitePassportUrl
  : '';
const websiteLinkConfigured = computed(() => !!websitePassportUrl);
const websiteLinkExpiryText = computed(() => {
  if (!websiteLinkExpiresAt.value) return '';
  const minutes = Math.max(1, Math.ceil((websiteLinkExpiresAt.value - Date.now()) / 60_000));
  return t('profile.websiteLinkExpires', { n: minutes });
});

const isLoggedIn = computed(() => isRealUser());
const CLOUD_POINTS = [
  [50, 44, 0], [25, 28, -7], [74, 29, 6], [28, 62, 5], [72, 62, -6], [50, 72, 4],
  [15, 46, -4], [86, 45, 5], [39, 18, 3], [61, 18, -3], [38, 84, -5], [63, 84, 5],
  [12, 73, 6], [89, 72, -6], [19, 15, 2], [82, 15, -2],
];

const wordCloudTags = computed(() =>
  profileTags.value
    .filter((tag) => isCloudKeyword(tag.label))
    .slice()
    .sort((a, b) => (b.weight || 0) - (a.weight || 0))
    .slice(0, 16)
    .map((tag, index) => {
      const point = CLOUD_POINTS[index % CLOUD_POINTS.length];
      const weight = Math.max(1, Math.min(100, tag.weight || 50));
      return {
      ...tag,
      left: point[0],
      top: point[1],
      rotate: point[2],
      fontSize: Math.round(22 + (weight / 100) * 20 + (index === 0 ? 4 : 0)),
      zIndex: 30 - index,
      opacity: (0.72 + (weight / 100) * 0.28).toFixed(2),
    };
  })
);

// isCloudKeyword 已提取到 @/utils/profileTagFilters，直接使用导入版本

const goLogin = () => {
  uni.reLaunch({ url: LOGIN_PAGE });
};

const openProfileEdit = () => {
  // Pre-populate the form with whatever we already know so the user is
  // editing, not retyping. (HCI: recognition over recall.)
  editForm.value = {
    nickname: userInfo.value.nickname || '',
    school: userInfo.value.school || '',
    major: userInfo.value.major || '',
    graduationYear: userInfo.value.graduationYear || '',
  };
  showProfileEdit.value = true;
};

const goResumes = () => {
  uni.switchTab({ url: '/pages/resume/index' });
};

const generateWebsiteLinkCode = async () => {
  if (websiteLinkLoading.value) return;
  if (!requireAuth({
    redirect: 'reLaunch',
    message: '登录后才能授权网站端读取你的成长护照。',
  })) return;
  if (!websiteLinkConfigured.value) {
    uni.showToast({ title: t('profile.websiteLinkUnavailable'), icon: 'none' });
    return;
  }
  if (!websiteLinkAuthorizationConfirmed) {
    const authorized = await new Promise<boolean>((resolve) => {
      uni.showModal({
        title: t('profile.websiteLinkConsentTitle'),
        content: t('profile.websiteLinkConsentContent'),
        confirmText: t('profile.websiteLinkConsentConfirm'),
        cancelText: t('common.cancel'),
        success: (result) => resolve(result.confirm),
        fail: () => resolve(false),
      });
    });
    if (!authorized) return;
    websiteLinkAuthorizationConfirmed = true;
  }
  websiteLinkLoading.value = true;
  try {
    const result = await createWebsiteLinkCodeApi();
    websiteLinkCode.value = result.code;
    websiteLinkExpiresAt.value = Date.now() + result.expiresInSeconds * 1000;
    if (websiteLinkExpiryTimer) clearTimeout(websiteLinkExpiryTimer);
    const generatedCode = result.code;
    websiteLinkExpiryTimer = setTimeout(() => {
      if (websiteLinkCode.value !== generatedCode) return;
      websiteLinkCode.value = '';
      websiteLinkExpiresAt.value = 0;
    }, result.expiresInSeconds * 1000);
    uni.showToast({ title: t('profile.websiteLinkGenerated'), icon: 'success' });
  } catch (e: any) {
    uni.showToast({ title: e?.message || t('profile.websiteLinkFailed'), icon: 'none' });
  } finally {
    websiteLinkLoading.value = false;
  }
};

const copyWebsiteLinkCode = () => {
  if (!websiteLinkCode.value) return;
  uni.setClipboardData({
    data: websiteLinkCode.value,
    success: () => uni.showToast({ title: t('profile.websiteLinkCopied'), icon: 'success' }),
  });
};

const copyWebsiteDestination = () => {
  if (!websitePassportUrl) {
    uni.showToast({ title: t('profile.websiteLinkUnavailable'), icon: 'none' });
    return;
  }
  uni.setClipboardData({
    data: websitePassportUrl,
    success: () => uni.showToast({
      title: t('profile.websiteLinkDestinationCopied'),
      icon: 'success',
    }),
  });
};

const SWITCH_TAB_PATHS = new Set([
  '/pages/home/index',
  '/pages/assistant/index',
  '/pages/resume/index',
  '/pages/user/index',
]);

const AUTH_REQUIRED_PATHS = new Set([
  '/pages/interview/history',
  '/pages/user/feedback',
]);

const navTo = (url: string) => {
  if (!url) {
    uni.showToast({ title: '暂时无法打开，请稍后重试', icon: 'none' });
    return;
  }
  const base = url.split('?')[0];
  if (AUTH_REQUIRED_PATHS.has(base) && !requireAuth()) {
    return;
  }
  if (SWITCH_TAB_PATHS.has(base)) {
    uni.switchTab({
      url: base,
      fail: () => uni.showToast({ title: '暂时无法打开，请稍后重试', icon: 'none' }),
    });
    return;
  }
  uni.navigateTo({
    url,
    fail: () => uni.showToast({ title: '暂时无法打开，请稍后重试', icon: 'none' }),
  });
};

const saveProfile = async () => {
  if (savingProfile.value || !isLoggedIn.value) return;
  const graduationYearText = editForm.value.graduationYear.trim();
  const graduationYear = graduationYearText ? Number(graduationYearText) : undefined;
  if (graduationYearText && (
    !Number.isInteger(graduationYear)
    || graduationYear! < 1970
    || graduationYear! > 2100
  )) {
    uni.showToast({ title: t('profile.gradYearInvalid'), icon: 'none' });
    return;
  }
  const numericId = Number(userId.value);
  if (!Number.isInteger(numericId) || numericId <= 0) return;

  savingProfile.value = true;
  try {
    const updated = await updateUserApi(numericId, {
      nickname: editForm.value.nickname.trim() || userInfo.value.nickname,
      // Empty strings are intentional: users must be able to clear optional
      // profile fields instead of having omission mean "keep old value".
      school: editForm.value.school.trim(),
      major: editForm.value.major.trim(),
      graduationYear,
      clearGraduationYear: !graduationYearText,
    });
    userInfo.value = {
      ...userInfo.value,
      ...updated,
      graduationYear: updated.graduationYear == null
        ? ''
        : String(updated.graduationYear),
    };
    uni.setStorageSync('userInfo', userInfo.value);
    showProfileEdit.value = false;
    uni.showToast({ title: t('profile.profileSaved'), icon: 'success' });
    // The profile update endpoint already refreshes tags. Only fetch the
    // resulting list so the word cloud reflects the saved profile.
    await loadProfileTags();
  } catch (e: any) {
    uni.showToast({
      title: e?.message || t('profile.updateFailed'),
      icon: 'none',
    });
  } finally {
    savingProfile.value = false;
  }
};

/**
 * Upload a new avatar:
 *   1. uni.chooseImage → local temp path
 *   2. POST /api/files/upload?folder=avatars → OSS object key
 *   3. PUT /users/{id} { avatarUrl: key } → server stores key, hydrates viewUrl
 *   4. Local cache + global event so home top-bar refreshes too.
 *
 * The previous version stored `tempFilePath` in localStorage which vanished
 * the moment the WeChat app cache was cleared.
 */
const handleAvatarClick = () => {
  if (!isLoggedIn.value) {
    uni.showToast({ title: t('profile.signInFirst'), icon: 'none' });
    return;
  }
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    sourceType: ['album', 'camera'],
    success: async (res) => {
      const filePath = res.tempFilePaths[0];
      if (!filePath) return;
      uni.showLoading({ title: t('profile.uploading'), mask: true });
      try {
        const objectKey = await uploadFileApi(filePath, 'avatars');
        const numericId = Number(userId.value);
        const updated = await updateUserApi(numericId, { avatarUrl: objectKey });

        userInfo.value.avatarUrl = updated.avatarUrl || objectKey;
        userInfo.value.avatarViewUrl = updated.avatarViewUrl || '';
        uni.setStorageSync('userInfo', userInfo.value);
        uni.hideLoading();
        uni.showToast({ title: t('profile.avatarUpdated'), icon: 'success' });
      } catch (e: any) {
        uni.hideLoading();
        uni.showToast({ title: e?.message || t('profile.updateFailed'), icon: 'none' });
      }
    },
  });
};

const openConsent = (type: 'privacy' | 'terms') => {
  uni.navigateTo({ url: `/pages/consent/index?view=${type}&readonly=1` });
};

const handleDeleteAccount = () => {
  uni.showModal({
    title: t('profile.deleteAccountTitle'),
    content: t('profile.deleteAccountContent'),
    confirmText: t('profile.deleteAccountContinue'),
    cancelText: t('common.cancel'),
    confirmColor: '#c23b22',
    success: (res) => {
      if (!res.confirm) return;
      uni.showModal({
        title: t('profile.deleteConfirmTitle'),
        content: t('profile.deleteConfirmContent', { phrase: t('profile.deleteConfirmPhrase') }),
        editable: true,
        placeholderText: t('profile.deleteConfirmPhrase'),
        confirmText: t('profile.deleteConfirmPhrase'),
        cancelText: t('common.cancel'),
        confirmColor: '#c23b22',
        success: async (res2) => {
          if (!res2.confirm) return;
          if ((res2 as any).content?.trim() !== t('profile.deleteConfirmPhrase')) {
            uni.showToast({ title: t('profile.deleteConfirmWrong'), icon: 'none', duration: 2500 });
            return;
          }
          try {
            uni.showLoading({ title: t('profile.processing'), mask: true });
            await requestDeletionApi();
            uni.hideLoading();
            clearAuthState({ purgeAccountDrafts: true });
            uni.showModal({
              title: t('profile.deleteSubmittedTitle'),
              content: t('profile.deleteSubmittedContent'),
              showCancel: false,
              success: () => { uni.reLaunch({ url: LOGIN_PAGE }); }
            });
          } catch (e: any) {
            uni.hideLoading();
            uni.showToast({ title: e?.message || t('profile.operationFailed'), icon: 'none' });
          }
        }
      });
    }
  });
};

const handleLogout = () => {
  uni.showModal({
    title: t('profile.signOutConfirmTitle'),
    content: t('profile.signOutConfirmContent'),
    confirmColor: '#c23b22',
    success: async (res) => {
      if (res.confirm) {
        try {
          uni.showLoading({ title: t('profile.signingOut'), mask: true });
          await logoutApi();
          uni.hideLoading();
        } catch (error) {
          uni.hideLoading();
          // A global 401 handler already cleared and redirected an expired
          // session. For an actual network/server failure, keep the credential
          // locally so the user can retry a real server-side revocation.
          if (!isRealUser()) return;
          uni.showToast({ title: t('profile.signOutFailed'), icon: 'none', duration: 2500 });
          return;
        }
        clearAuthState();
        userId.value = '';
        userInfo.value = { nickname: '', avatarUrl: '', avatarViewUrl: '', school: '', major: '', graduationYear: '' };
        uni.showToast({ title: t('common.success'), icon: 'success' });
        setTimeout(() => {
          uni.reLaunch({ url: LOGIN_PAGE });
        }, 400);
      }
    },
  });
};

const enableWechatReminders = async () => {
  if (wechatReminderLoading.value || !isRealUser()) return;
  wechatReminderLoading.value = true;
  try {
    const result = await requestConfiguredSubscribe();
    if (!result.configured) {
      uni.showToast({ title: t('profile.wechatReminderUnavailable'), icon: 'none' });
    } else if (!result.synced) {
      uni.showToast({ title: t('profile.wechatReminderSyncFailed'), icon: 'none' });
    } else if (result.accepted > 0) {
      uni.showToast({
        title: t('profile.wechatReminderEnabled', { n: result.accepted }),
        icon: 'success',
      });
    } else {
      uni.showToast({ title: t('profile.wechatReminderNotEnabled'), icon: 'none' });
    }
  } catch {
    uni.showToast({ title: t('profile.wechatReminderFailed'), icon: 'none' });
  } finally {
    wechatReminderLoading.value = false;
  }
};

const loadStats = async (uid: number) => {
  try {
    const [interviews, resumes] = await Promise.all([
      getUserInterviewsApi(uid),
      listMyResumesApi(),
    ]);
    statsInterviews.value = Array.isArray(interviews) ? interviews.length : 0;
    statsResumes.value = Array.isArray(resumes) ? resumes.length : 0;
  } catch {
    // silently fail, stats will remain 0
  }
};

const loadProfileTags = async () => {
  try {
    const summary = await getProfileTagsApi();
    profileTags.value = summary.tags || [];
  } catch {
    profileTags.value = [];
  }
};

/**
 * Avatar URLs we store in localStorage are short-lived signed URLs that
 * expire ~30 min after issue. Re-fetch the user from the backend on every
 * mount so the <image> never tries to load a stale signature.
 */
const refreshUserFromBackend = async (numericId: number) => {
  try {
    const fresh = await getUserInfoApi(numericId);
    userInfo.value = {
      ...userInfo.value,
      ...fresh,
      graduationYear: fresh.graduationYear == null
        ? ''
        : String(fresh.graduationYear),
    };
    uni.setStorageSync('userInfo', userInfo.value);
  } catch { /* offline or token invalid — keep cached values, page still renders */ }
};

onShow(() => {
  try {
    loadProfile();
  } catch (e) {
    console.error('[user] onShow failed', e);
  }
});

onPageScroll(({ scrollTop }) => {
  scrollTopValue.value = scrollTop;
});

const loadProfile = async () => {
  userId.value = uni.getStorageSync('userId') || '';
  const info = uni.getStorageSync('userInfo');
  if (info) {
    userInfo.value = {
      ...userInfo.value,
      ...info,
      graduationYear: info.graduationYear == null
        ? String(info.gradYear || '')
        : String(info.graduationYear),
    };
    editForm.value.school = userInfo.value.school || '';
    editForm.value.nickname = userInfo.value.nickname || '';
    editForm.value.major = userInfo.value.major || '';
    editForm.value.graduationYear = userInfo.value.graduationYear || '';
  }

  const safeMetrics = getMpSafeAreaMetrics();
  topSafeHeight.value = safeMetrics.topSafeHeight;
  rightAvoidWidth.value = safeMetrics.rightAvoidWidth;

  const numericId = Number(userId.value);
  if (userId.value && !isNaN(numericId) && numericId > 0) {
    loadStats(numericId);
    refreshUserFromBackend(numericId);
    loadProfileTags();
  }
};

onUnmounted(() => {
  if (websiteLinkExpiryTimer) clearTimeout(websiteLinkExpiryTimer);
});
</script>

<style scoped>
.user-page {
  display: flex;
  flex-direction: column;
  padding: 0 var(--page-gutter, 20px);
  padding-bottom: calc(env(safe-area-inset-bottom, 0px) + 28px);
  font-family: var(--font-sans, "PingFang SC", "Microsoft YaHei", -apple-system, sans-serif);
  box-sizing: border-box;
  min-height: 100vh;
}

.bottom-section {
  margin-top: 32px;
}

.status-spacer { width: 100%; }

.page-header {
  padding: 8px 0 6px;
}

.page-title {
  display: block;
  font-size: var(--font-hero, 28px);
  font-weight: 600;
  color: var(--ink, #2c2b29);
  font-family: var(--font-serif, "Songti SC", STSong, serif);
  letter-spacing: 0.05em;
}

.page-subtitle {
  display: block;
  margin-top: 4px;
  font-size: var(--font-caption, 13px);
  line-height: 1.5;
  color: var(--ink-secondary, #5a5956);
}

/* Edit/Complete chip on the header card — affords tappability with both
   a label and a chevron (HCI: signifiers, recognition over recall) */
.header-edit {
  margin-left: auto;
  display: flex; align-items: center; gap: 2px;
  background: var(--paper-soft, #f5f5f0);
  border: 1px solid var(--border-color, #e0dfdb);
  padding: 8px 6px 8px 12px;
  border-radius: var(--radius-sm, 4px);
  min-height: 32px;
}
.header-edit:active { background: var(--paper-deep, #efeee9); }
.header-edit-text { color: var(--vermilion, #c23b22); font-size: 13px; font-weight: 600; }
.header-edit-arrow { color: var(--vermilion, #c23b22); font-size: 16px; line-height: 1; padding-right: 6px; }

/* Header card */
.header-card {
  background: var(--card-bg, #fffefa);
  border: 1px solid var(--border-color, #e0dfdb);
  border-top: 1px solid var(--border-color, #e0dfdb);
  border-radius: var(--radius-md, 6px); padding: 24px 20px; margin: 12px 0 16px;
  display: flex; align-items: center; gap: 16px;
  box-shadow: var(--shadow-card);
}

.header-guest {
  flex-direction: column; align-items: flex-start; gap: 8px;
}

.guest-title { font-size: 20px; font-weight: 600; color: var(--ink, #2c2b29); font-family: var(--font-serif, "Songti SC", STSong, serif); }

.guest-desc { font-size: 13px; color: var(--ink-secondary, #5a5956); margin-bottom: 4px; }

.btn-login {
  background: var(--vermilion, #c23b22);
  color: #ffffff; border: 1px solid rgba(255, 255, 255, 0.3);
  font-size: 14px; font-weight: 600; border-radius: var(--btn-radius, 6px);
  padding: 0 var(--page-gutter, 20px); height: 36px; line-height: 36px;
}

.header-avatar {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
  background: var(--paper-soft, #f5f5f0);
}

.avatar-img {
  display: block;
  width: 56px; height: 56px; border-radius: 50%;
  border: 2px solid var(--border-color, #e0dfdb);
  box-sizing: border-box;
}

.header-info { flex: 1; }

.header-name {
  font-size: var(--font-title, 18px); font-weight: 600; color: var(--ink, #2c2b29);
  font-family: var(--font-serif, "Songti SC", STSong, serif);
  display: block; margin-bottom: 4px;
}

.header-school { font-size: var(--font-caption, 13px); color: var(--ink-secondary, #5a5956); }

.edit-btn { background: var(--paper-soft, #f5f5f0); color: var(--vermilion, #c23b22); padding: 4px 10px; border: 1px solid var(--border-color, #e0dfdb); border-radius: var(--radius-sm, 4px); display: inline-block; margin-top: 4px; }

/* Stats */
.stats-bar {
  display: flex; align-items: center;
  background: var(--card-bg, #fffefa);
  border: 1px solid var(--border-color, #e0dfdb);
  border-radius: var(--radius-md, 6px);
  box-shadow: var(--shadow-sm);
  padding: 16px 0; margin-bottom: 24px;
}

.website-link-card {
  margin: 0 0 20px;
  padding: 16px;
  border-top: 1px solid var(--border-color, #e0dfdb);
}
.website-link-head {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}
.website-link-icon {
  width: 36px;
  height: 36px;
  border-radius: 7px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--brand-color, #ac6448);
  background: var(--brand-soft, #f9f0ed);
  font-size: 18px;
}
.website-link-copy { flex: 1; min-width: 0; }
.website-link-title {
  display: block;
  color: var(--text-primary, #2c2b29);
  font-family: var(--font-serif, "Songti SC", STSong, serif);
  font-size: 17px;
  font-weight: 600;
  letter-spacing: 0.04em;
}
.website-link-desc {
  display: block;
  margin-top: 3px;
  color: var(--text-secondary, #5a5956);
  font-size: 11.5px;
  line-height: 1.45;
}
.website-link-state {
  flex-shrink: 0;
  padding: 4px 7px;
  border-radius: var(--radius-sm, 4px);
  background: var(--sage-soft, #f2efe9);
  color: var(--sage, #8d846e);
  font-size: 10px;
  font-weight: 800;
}
.website-link-state-unavailable {
  color: var(--text-tertiary, #8b8a86);
  background: var(--paper-soft, #f5f5f0);
}
.website-link-unavailable {
  margin-top: 14px;
  padding: 12px;
  display: flex;
  align-items: flex-start;
  gap: 10px;
  color: var(--text-secondary, #5a5956);
  background: var(--paper-soft, #f5f5f0);
  border: 1px dashed var(--border-color, #e0dfdb);
  border-radius: var(--radius-sm, 4px);
}
.website-link-unavailable-icon {
  flex-shrink: 0;
  color: var(--heritage-gold, #b8975a);
  font-size: 20px;
}
.website-link-unavailable-title {
  display: block;
  color: var(--text-primary, #2c2b29);
  font-size: 13px;
  font-weight: 800;
}
.website-link-unavailable-desc {
  display: block;
  margin-top: 3px;
  color: var(--text-secondary, #5a5956);
  font-size: 11px;
  line-height: 1.5;
}
.website-code-panel {
  margin-top: 14px;
  padding: 12px;
  border-radius: var(--radius-sm, 4px);
  border: 1px dashed var(--heritage-gold, #b8975a);
  background: var(--gold-soft, #f5efe4);
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}
.website-code-label,
.website-code-expiry {
  display: block;
  color: var(--text-secondary, #5a5956);
  font-size: 10px;
}
.website-code {
  display: block;
  margin: 2px 0;
  color: var(--vermilion, #c23b22);
  font-size: 24px;
  line-height: 1.1;
  font-weight: 900;
  letter-spacing: 0.16em;
}
.website-code-copy {
  min-height: 44px;
  padding: 0 10px;
  border-radius: var(--btn-radius, 6px);
  display: flex;
  align-items: center;
  gap: 5px;
  color: #fff;
  background: var(--vermilion, #c23b22);
  font-size: 12px;
  font-weight: 700;
}
.website-destination {
  margin-top: 12px;
  padding: 12px;
  border: 1px solid var(--border-color, #e0dfdb);
  border-radius: var(--radius-sm, 4px);
  background: var(--paper-soft, #f5f5f0);
}
.website-step {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-secondary, #5a5956);
  font-size: 12px;
  line-height: 1.5;
}
.website-step-index {
  width: 20px;
  height: 20px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: #fff;
  background: var(--indigo, #ac6448);
  font-size: 11px;
  font-weight: 700;
}
.website-url-row {
  min-height: 44px;
  margin: 8px 0;
  padding: 0 10px 0 28px;
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--indigo, #ac6448);
  background: var(--card-bg, #fffefa);
  border: 1px dashed var(--indigo, #ac6448);
  border-radius: var(--radius-sm, 4px);
}
.website-url {
  flex: 1;
  min-width: 0;
  font-size: 11px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.website-link-action {
  min-height: 44px;
  margin-top: 12px;
  padding: 0 14px;
  border-radius: var(--btn-radius, 6px);
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #fff;
  background: var(--action-color, #c23b22);
  font-size: 14px;
  font-weight: 800;
}
.website-link-action.disabled { opacity: 0.55; }
.website-link-note {
  display: block;
  margin-top: 8px;
  color: var(--text-tertiary, #8b8a86);
  font-size: 10.5px;
  line-height: 1.45;
}
.is-dark .website-link-icon,
.is-dark .website-code-panel,
.is-dark .website-destination,
.is-dark .website-url-row,
.is-dark .website-link-unavailable {
  background: #4b3228;
  border-color: #d69f89;
}
.is-dark .website-link-state {
  background: #433e33;
}

.tag-cloud-card {
  margin: 16px 0 6px;
  padding: 18px;
}
.tag-cloud-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}
.tag-cloud-title {
  display: block;
  font-size: 16px;
  font-weight: 600;
  color: var(--ink, #2c2b29);
  font-family: var(--font-serif, "Songti SC", STSong, serif);
  letter-spacing: 0.05em;
}
.tag-cloud-subtitle {
  display: block;
  margin-top: 3px;
  font-size: 11.5px;
  line-height: 1.4;
  color: var(--ink-secondary, #5a5956);
}
.tag-cloud-edit {
  padding: 6px 12px;
  border-radius: var(--radius-sm, 4px);
  background: var(--vermilion-soft, #f8ebe7);
  border: 1px solid rgba(194, 59, 34, 0.22);
}
.tag-cloud-edit-text {
  font-size: 12px;
  font-weight: 600;
  color: var(--vermilion, #c23b22);
}
.tag-cloud {
  position: relative;
  height: 360rpx;
  overflow: hidden;
  border-radius: 12rpx;
  background:
    radial-gradient(circle at 24% 28%, rgba(172, 100, 72,.07), transparent 34%),
    radial-gradient(circle at 72% 68%, rgba(141, 132, 110,.09), transparent 36%),
    var(--paper-soft, #f5f5f0);
  border: 1px solid var(--border-color, #e0dfdb);
}
.tag-cloud-empty {
  height: 220rpx;
  border-radius: 12rpx;
  border: 1px dashed rgba(184,151,90,.48);
  background: var(--gold-soft, #f5efe4);
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 0 28rpx;
  box-sizing: border-box;
}
.tag-cloud-empty-title {
  display: block;
  font-size: 14px;
  line-height: 1.25;
  font-weight: 600;
  color: var(--ink, #2c2b29);
  font-family: var(--font-serif, "Songti SC", STSong, serif);
}
.tag-cloud-empty-desc {
  display: block;
  margin-top: 8rpx;
  font-size: 11.5px;
  line-height: 1.45;
  color: var(--ink-secondary, #5a5956);
}
.tag-cloud-word {
  position: absolute;
  transform-origin: center;
  max-width: 34%;
  padding: 2rpx 4rpx;
  white-space: nowrap;
}
.tag-cloud-word-text {
  display: block;
  line-height: 1.12;
  font-weight: 600;
  color: var(--ink, #2c2b29);
  font-family: var(--font-serif, "Songti SC", STSong, serif);
  text-shadow: 0 1px 0 rgba(255,255,255,.76);
  overflow: hidden;
  text-overflow: ellipsis;
}
.tag-cat-skill .tag-cloud-word-text { color: var(--sage, #8d846e); }
.tag-cat-background .tag-cloud-word-text { color: var(--indigo, #ac6448); }
.tag-cat-growth .tag-cloud-word-text { color: var(--heritage-gold, #b8975a); }
.tag-cat-goal .tag-cloud-word-text { color: var(--vermilion, #c23b22); }

.stat-item {
  flex: 1; display: flex; flex-direction: column;
  align-items: center; gap: 4px;
}

.stat-val { font-size: var(--font-title, 18px); font-weight: 600; color: var(--ink, #2c2b29); font-family: var(--font-serif, "Songti SC", STSong, serif); }

.stat-label { font-size: var(--font-micro, 11px); color: var(--ink-tertiary, #8b8a86); font-weight: 500; }

.stat-divider { width: 1px; height: 24px; background: var(--border-color, #e0dfdb); }

/* Menu */
.group-label {
  font-size: var(--font-caption, 13px);
  font-weight: 500;
  color: var(--ink-tertiary, #8b8a86);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  display: block;
  margin-bottom: 8px;
  padding-left: 4px;
}

.menu-card {
  border-radius: var(--radius-md, 6px);
  overflow: hidden; margin-bottom: 24px;
}

.menu-item {
  display: flex; align-items: center; padding: 12px 12px;
  position: relative;
}

.menu-item:not(:last-child)::after {
  content: ''; position: absolute;
  bottom: 0; left: 52px; right: 0;
  height: 1px; background: var(--border-light, #ecebe7);
}

.menu-icon-wrap {
  margin-right: 12px;
  width: 28px;
  height: 28px;
  border-radius: var(--radius-sm, 4px);
  display: flex; align-items: center; justify-content: center;
}

.menu-icon { font-size: 16px; margin: 0; display: block; }

.menu-text {
  flex: 1;
  font-size: var(--font-body, 15px);
  color: var(--ink, #2c2b29);
  font-weight: 500;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.menu-action-text {
  flex-shrink: 0;
  margin-left: 10px;
  color: var(--primary-color, #cd6a43);
  font-size: var(--font-caption, 13px);
  font-weight: 600;
}

.menu-arrow { font-size: 20px; color: var(--ink-placeholder, #aaa9a5); flex-shrink: 0; }

.dark-switch { transform: scale(0.85); }

.font-pills { display: flex; gap: 6px; }
.theme-pills { display: flex; gap: 6px; }

/* Reduced pill size for more compact layout */
.pill {
  min-width: 36px; min-height: 36px;
  padding: 4px 12px; border-radius: var(--radius-sm, 4px);
  font-size: var(--font-caption, 13px); font-weight: 600; color: var(--ink-secondary, #5a5956);
  background: var(--paper-soft, #f5f5f0);
  border: 1px solid var(--border-color, #e0dfdb);
  display: flex; align-items: center; justify-content: center;
  transition: background 0.15s, color 0.15s;
}

.pill-active { background: var(--vermilion, #c23b22); border-color: var(--vermilion, #c23b22); color: #ffffff; }

/* Logout */
.btn-logout {
  width: 100%; height: 48px; background: var(--vermilion, #c23b22);
  color: #ffffff; font-size: var(--font-body, 15px); font-weight: 600;
  border-radius: var(--btn-radius, 6px); border: none;
  display: flex; align-items: center; justify-content: center; padding: 0;
  box-shadow: var(--shadow-xs, 0 1px 3px rgba(0,0,0,0.08), 0 1px 8px rgba(0,0,0,0.05));
}

.btn-logout::after { border: none; }

.btn-logout:active { background: #a8321e; }

.bottom-safe {
  height: calc(var(--tab-bar-height, 50px) + 16px);
  flex-shrink: 0;
}

/* Modals */
.modal-overlay {
  position: fixed; top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0, 0, 0, 0.4); z-index: 1000;
  display: flex; align-items: flex-end;
}

.modal-content.bottom-sheet {
  width: 100%; background: var(--card-bg, #fffefa);
  border-radius: var(--radius-lg, 8px) var(--radius-lg, 8px) 0 0; padding: 16px 20px 32px;
  box-sizing: border-box;
  border-top: 1px solid var(--border-color, #e0dfdb);
}

.sheet-handle {
  width: 36px; height: 4px; border-radius: 2px; background: var(--border-strong, #ceccc5);
  margin: 0 auto 16px;
}

.modal-title { font-size: var(--font-title, 18px); font-weight: 600; color: var(--ink, #2c2b29); display: block; margin-bottom: 24px; text-align: center; font-family: var(--font-serif, "Songti SC", STSong, serif); letter-spacing: 0.05em; }

.form-group { margin-bottom: 16px; }
.field-label { font-size: var(--font-caption, 13px); font-weight: 600; color: var(--ink-secondary, #5a5956); margin-bottom: 8px; display: block; font-family: var(--font-serif, "Songti SC", STSong, serif); }
.field-input { width: 100%; height: 48px; border: 1px solid var(--border-color, #e0dfdb); border-radius: var(--radius-sm, 4px); padding: 0 16px; font-size: var(--font-body, 15px); box-sizing: border-box; background: var(--card-bg, #fffefa); color: var(--ink, #2c2b29); }

.modal-actions { display: flex; gap: 12px; margin-top: 32px; }
.btn-secondary { flex: 1; height: 48px; background: var(--paper-soft, #f5f5f0); color: var(--ink-secondary, #5a5956); font-weight: 600; border-radius: var(--btn-radius, 6px); border: 1px solid var(--border-color, #e0dfdb); line-height: 48px; }
.btn-primary { flex: 2; height: 48px; background: var(--vermilion, #c23b22); color: #fff; font-weight: 600; border-radius: var(--btn-radius, 6px); border: none; line-height: 48px; }

/* Danger row (Delete Account) */
.menu-text-danger { color: var(--vermilion, #c23b22) !important; }
.menu-arrow-danger { color: var(--vermilion, #c23b22) !important; }
.menu-item-danger:active { background: var(--vermilion-soft, #f8ebe7); }

/* Dark mode */
.is-dark { background: #1c1917; }

.is-dark .stats-bar,
.is-dark .menu-card { background: #292524; box-shadow: none; }

.is-dark .btn-logout { background: #b91c1c; border: none; box-shadow: none; }

.is-dark .stat-val,
.is-dark .menu-text { color: #fafaf9; }

.is-dark .group-label,
.is-dark .stat-label { color: var(--text-secondary, #78716c); }

.is-dark .btn-logout { color: #ffffff; }

.is-dark .pill { background: #44403c; color: var(--text-tertiary, #8e8e93); }

.is-dark .pill-active { background: var(--vermilion, #c23b22); color: #ffffff; }

.is-dark .modal-content { background: #292524; }
.is-dark .sheet-handle { background: #44403c; }
.is-dark .modal-title { color: #fafaf9; }
.is-dark .field-label { color: #e7e5e4; }
.is-dark .field-input { background: #1c1917; border-color: var(--text-secondary, #78716c); color: #fafaf9; }
.is-dark .btn-secondary { background: #44403c; color: #e7e5e4; }

/* ================================================================
 *  MP-WEIXIN parity overrides — HARDCODED values, no CSS vars.
 * ================================================================ */
/* #ifdef MP-WEIXIN */

.header-card {
  overflow: visible;
  border: 1px solid #e0dfdb;
  border-top: 1px solid var(--border-color, #e0dfdb);
  background: #fffefa;
  box-shadow: 0 2px 10px rgba(44,43,41,.035);
}

.stats-bar {
  overflow: visible;
  border: 1px solid #e0dfdb;
  box-shadow: 0 2px 10px rgba(44,43,41,.035);
}

.menu-card {
  overflow: hidden;
  box-shadow: none;
  filter: none;
}

.menu-item:not(:last-child)::after {
  background: #ecebe7;
}

.is-dark .stats-bar,
.is-dark .menu-card,
.is-dark .modal-content.bottom-sheet {
  background: #292524;
  border-color: var(--text-secondary, #78716c);
  filter: none;
}

.is-dark .menu-item:not(:last-child)::after {
  background: #44403c;
}

/* #endif */
</style>
