<template>
  <view class="resume-page app-soft-bg" :class="[themeClass, fontClass]">
    <SlScrollTopBar :title="t('resume.hubTitle')" :opacity="topBarOpacity" :safe-top="topSafeHeight" :right-avoid-width="rightAvoidWidth" />
    <view class="status-spacer" :style="{ height: topSafeHeight + 'px' }"></view>

    <view class="page-header" :style="{ paddingRight: rightAvoidWidth + 'px' }">
      <text class="page-title">{{ t('resume.hubTitle') }}</text>
      <text class="page-subtitle">{{ t('resume.hubSubtitle') }}</text>
    </view>

    <!-- Skeleton -->
    <view class="skeleton-list" v-if="isLoading && resumeList.length === 0">
      <view class="skel-card app-card-soft" v-for="i in 3" :key="i">
        <view class="skel-square"></view>
        <view class="skel-lines">
          <view class="skel-line skel-w70"></view>
          <view class="skel-line skel-w40"></view>
        </view>
      </view>
    </view>

    <view v-else-if="resumeList.length > 0">
      <!-- ── 我的简历 section ── -->
      <view class="section-bar">
        <view class="section-titles">
          <text class="section-title">{{ t('resume.myResumes') }}</text>
          <text class="section-sub">{{ t('resume.filesCount', { n: mainHubResumes.length }) }}</text>
        </view>
        <view class="section-action" @click="handleUploadClick">
          <text class="section-action-text">{{ t('resume.addBtn') }}</text>
        </view>
      </view>
      <view class="resume-list">
        <view class="resume-card app-card-soft" v-for="(item, idx) in mainHubResumes" :key="item.resumeId">
          <view class="rc-icon-wrap">
            <view class="rc-icon" :class="'rc-icon-' + (idx % 2)">
              <text class="rc-icon-text">PDF</text>
            </view>
          </view>
          <view class="rc-body">
            <text class="rc-name">{{ item.name }}</text>
            <view class="rc-meta-row">
              <text class="rc-time">{{ item.date }}</text>
              <view class="rc-badge" :class="'badge-' + item.status">
                <text class="rc-badge-text">{{ item.statusLabel }}</text>
              </view>
            </view>
            <view v-if="item.keywords.length" class="keyword-row">
              <text
                v-for="kw in item.keywords"
                :key="kw"
                class="keyword-pill"
              >{{ kw }}</text>
            </view>
            <text v-else class="keyword-empty" @click="retryResumeKeywords(item)">{{ keywordStatusText(item) }}</text>
          </view>
          <view class="rc-actions">
            <view class="rc-action-btn" @click="handlePreview(item)">
              <text class="rc-action-text">{{ t('resume.previewBtn') }}</text>
            </view>
            <view class="rc-more" @click="handleMore(resumeList.indexOf(item))">
              <text class="rc-more-dots">···</text>
            </view>
          </view>
        </view>
        <!-- Add new resume -->
        <view class="add-card app-card-feature" @click="handleUploadClick">
          <view class="add-icon"><text class="add-plus">+</text></view>
          <view class="add-info">
            <text class="add-title">{{ t('resume.newResume') }}</text>
            <text class="add-desc">{{ t('resume.newResumeDesc') }}</text>
          </view>
        </view>
      </view>

      <!-- ── 针对岗位优化后的简历 section（有时才显示）── -->
      <view v-if="tailoredResumes.length > 0 && originalResumes.length > 0" class="section-bar section-bar-ai">
        <view class="section-titles">
          <view class="ai-section-label-row">
            <text class="section-title">{{ t('resume.tailoredResumes') }}</text>
            <view class="ai-badge"><text class="ai-badge-text">JD</text></view>
          </view>
          <text class="section-sub">{{ t('resume.tailoredHint') }}</text>
        </view>
      </view>
      <view class="resume-list" v-if="tailoredResumes.length > 0 && originalResumes.length > 0">
        <view class="resume-card resume-card-ai app-card-soft" v-for="(item, idx) in tailoredResumes" :key="item.resumeId">
          <view class="rc-icon-wrap">
            <view class="rc-icon rc-icon-ai">
              <text class="rc-icon-text">JD</text>
            </view>
          </view>
          <view class="rc-body">
            <text class="rc-name">{{ item.name }}</text>
            <view class="rc-meta-row">
              <text class="rc-time">{{ item.date }}</text>
              <view class="rc-badge badge-ai">
                <text class="rc-badge-text">{{ t('resume.tailoredResumes') }}</text>
              </view>
            </view>
            <view v-if="item.keywords.length" class="keyword-row">
              <text
                v-for="kw in item.keywords"
                :key="kw"
                class="keyword-pill"
              >{{ kw }}</text>
            </view>
            <text v-else class="keyword-empty" @click="retryResumeKeywords(item)">{{ keywordStatusText(item) }}</text>
          </view>
          <view class="rc-actions">
            <view class="rc-action-btn" @click="handlePreview(item)">
              <text class="rc-action-text">{{ t('resume.previewBtn') }}</text>
            </view>
            <view class="rc-more" @click="handleMore(resumeList.indexOf(item))">
              <text class="rc-more-dots">···</text>
            </view>
          </view>
        </view>
      </view>
    </view>

    <view class="empty-state app-empty" v-else-if="!isLoading">
      <text class="empty-icon ri-file-text-line"></text>
      <text class="empty-title">{{ t('resume.noResumes') }}</text>
      <text class="empty-desc">{{ t('resume.noResumesDesc2') }}</text>
      <view class="add-card app-card-feature" @click="handleUploadClick">
        <view class="add-icon"><text class="add-plus">+</text></view>
        <view class="add-info">
          <text class="add-title">{{ t('resume.addFirst') }}</text>
          <text class="add-desc">{{ t('resume.addFirstDesc') }}</text>
        </view>
      </view>
    </view>

    <view class="bottom-safe"></view>

    <!-- Action sheet -->
    <view class="sheet-mask" v-if="showSheet" @click="closeSheet"></view>
    <view class="sheet" :class="{ 'sheet-open': showSheet }">
      <view class="sheet-title-bar">
        <text class="sheet-title">{{ t('resume.chooseOption') }}</text>
      </view>
      <view class="sheet-option" @click="selectAction('template')">
        <text class="sheet-option-icon ri-edit-box-line"></text>
        <text class="sheet-option-text">{{ t('resume.useTemplateBtn') }}</text>
      </view>
      <view class="sheet-option" @click="selectAction('upload')">
        <view class="sheet-option-col">
          <view class="sheet-option-row">
            <text class="sheet-option-icon ri-attachment-line"></text>
            <text class="sheet-option-text">{{ t('resume.uploadPDF') }}</text>
          </view>
          <text class="sheet-option-hint">{{ t('resume.uploadHint') }}</text>
        </view>
      </view>
      <view class="sheet-cancel" @click="closeSheet">
        <text class="sheet-cancel-text">{{ t('common.cancel') }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useI18n } from '@/locales';
import { onShow, onPageScroll } from '@dcloudio/uni-app';
import { getMpSafeAreaMetrics } from '@/utils/safeArea';
import {
  listMyResumesApi,
  createResumeApi,
  deleteResumeApi,
  updateResumeApi,
  uploadResumeFile,
  type Resume,
} from '@/api/resume';
import { useTheme } from '@/utils/theme';
import { isRealUser, requireAuth } from '@/utils/auth';

/** JD-tailored copies use a `_tailored` suffix (see ResumeGenController). */
const isTailoredResumeTitle = (title?: string | null): boolean => {
  if (!title) return false;
  return /_tailored(?:\.pdf)?$/i.test(title.trim());
};
import SlScrollTopBar from '@/style-library/components/SlScrollTopBar.vue';
import {
  getResumeKeywordStatusApi,
  startResumeKeywordExtractionApi,
  type ResumeKeywordStatus,
} from '@/api/profileTags';

const { t } = useI18n();
const { themeClass, fontClass, refresh: refreshTheme } = useTheme();

type KeywordStatus = 'idle' | 'loading' | 'done' | 'empty' | 'failed' | 'timeout';

interface ResumeItem {
  resumeId?: number;
  name: string;
  date: string;
  status: 'recent' | 'normal';
  statusLabel: string;
  /** OSS object key — useful for filename derivation, NOT for opening. */
  fileUrl?: string;
  /** Short-lived signed URL provided by the backend; safe to share / open. */
  fileViewUrl?: string;
  isTailored: boolean;
  keywords: string[];
  keywordStatus: KeywordStatus;
}

const resumeList = ref<ResumeItem[]>([]);
const isLoading = ref(false);
const keywordPollTimers = new Map<number, ReturnType<typeof setTimeout>>();

const originalResumes = computed(() => resumeList.value.filter((r) => !r.isTailored));
const tailoredResumes = computed(() => resumeList.value.filter((r) => r.isTailored));
/** When only JD copies exist, show them in the main hub instead of an empty "我的简历" section. */
const mainHubResumes = computed(() => {
  if (originalResumes.value.length > 0) return originalResumes.value;
  return tailoredResumes.value;
});
/**
 * Render an absolute timestamp (e.g. "2026-04-30 01:00:21") as a friendly
 * relative label. We deliberately keep this self-contained -- it falls back
 * to a short date for anything older than a week. (HCI: match real world,
 * minimise cognitive load.)
 */
const formatRelative = (ts?: string): string => {
  if (!ts) return '';
  const date = new Date(ts.replace(' ', 'T'));
  if (isNaN(date.getTime())) return '';
  const diffMs = Date.now() - date.getTime();
  const sec = Math.max(0, Math.floor(diffMs / 1000));
  if (sec < 60) return t('messages.timeJustNow');
  const min = Math.floor(sec / 60);
  if (min < 60) return t('messages.timeMins', { m: min });
  const hr = Math.floor(min / 60);
  if (hr < 24) return t('messages.timeHours', { h: hr });
  const day = Math.floor(hr / 24);
  if (day === 1) return t('messages.timeYesterday');
  if (day < 7) return t('messages.timeDays', { d: day });
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
};

const resumeKeywords = (resume: Resume): string[] => {
  const raw = [
    ...(resume.detail?.skills || []),
    resume.targetJob,
    resume.title,
  ];
  const seen = new Set<string>();
  return raw
    .flatMap((item) => splitKeywordText(String(item || '')))
    .filter((kw) => {
      const key = kw.toLowerCase();
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    })
    .slice(0, 6);
};

const splitKeywordText = (text: string): string[] => {
  const terms = ['前端', '后端', '全栈', '算法', '数据', '产品', '运营', '设计', '测试', '开发', '工程师',
    'Java', 'Python', 'JavaScript', 'TypeScript', 'Vue', 'React', 'Spring', 'SpringBoot', 'Node',
    'SQL', 'MySQL', 'Redis', 'Docker', 'Git', 'AI', 'UI', 'UX', '小程序', '项目', '实习', '面试'];
  const out = new Set<string>();
  terms.forEach((term) => {
    if (text.toLowerCase().includes(term.toLowerCase())) out.add(term);
  });
  text.split(/[\s,，、。;；/|+()（）【】[\]{}:：]+/)
    .map((part) => part.trim())
    .filter((part) => /^[A-Za-z][A-Za-z0-9#.+-]{1,23}$/.test(part) || /^[\u4e00-\u9fa5]{2,8}$/.test(part))
    .forEach((part) => out.add(part));
  return Array.from(out).filter((kw) => !['目标', '岗位', '状态', '简历', '用户'].includes(kw));
};

const hydrateResumeKeywords = () => {
  mainHubResumes.value.slice(0, 4).forEach((item) => {
    if (item.resumeId && item.keywordStatus === 'idle') hydrateOneResumeKeywords(item.resumeId);
  });
};

const mapKeywordStatus = (status?: string): KeywordStatus => {
  if (status === 'READY') return 'done';
  if (status === 'EMPTY') return 'empty';
  if (status === 'FAILED') return 'failed';
  if (status === 'PENDING' || status === 'PROCESSING') return 'loading';
  return 'idle';
};

const applyResumeKeywordStatus = (resumeId: number, status: ResumeKeywordStatus) => {
  const item = resumeList.value.find((resume) => resume.resumeId === resumeId);
  if (!item) return;
  const keywords = (status.keywords || []).map((tag) => tag.label).filter(Boolean).slice(0, 8);
  item.keywords = keywords;
  item.keywordStatus = keywords.length ? 'done' : mapKeywordStatus(status.status);
};

const clearKeywordPoll = (resumeId: number) => {
  const timer = keywordPollTimers.get(resumeId);
  if (timer) clearTimeout(timer);
  keywordPollTimers.delete(resumeId);
};

const scheduleKeywordPoll = (resumeId: number, attempt = 0) => {
  clearKeywordPoll(resumeId);
  if (attempt >= 30) {
    const item = resumeList.value.find((resume) => resume.resumeId === resumeId);
    if (item && item.keywordStatus === 'loading') item.keywordStatus = 'timeout';
    return;
  }
  const delay = attempt < 4 ? 1200 : 2500;
  const timer = setTimeout(async () => {
    try {
      const status = await getResumeKeywordStatusApi(resumeId);
      applyResumeKeywordStatus(resumeId, status);
      const item = resumeList.value.find((resume) => resume.resumeId === resumeId);
      if (item?.keywordStatus === 'loading') scheduleKeywordPoll(resumeId, attempt + 1);
      else clearKeywordPoll(resumeId);
    } catch {
      const item = resumeList.value.find((resume) => resume.resumeId === resumeId);
      if (item) item.keywordStatus = 'failed';
      clearKeywordPoll(resumeId);
    }
  }, delay);
  keywordPollTimers.set(resumeId, timer);
};

const hydrateOneResumeKeywords = async (resumeId: number, force = false) => {
  const item = resumeList.value.find((resume) => resume.resumeId === resumeId);
  if (!item) return;
  if (!force && item.keywordStatus !== 'idle') return;
  item.keywordStatus = 'loading';
  try {
    const status = await startResumeKeywordExtractionApi(resumeId, force);
    applyResumeKeywordStatus(resumeId, status);
    if (item.keywordStatus === 'loading') scheduleKeywordPoll(resumeId);
  } catch {
    item.keywordStatus = 'failed';
  }
};

const keywordStatusText = (item: ResumeItem) => {
  if (item.keywordStatus === 'loading') return '关键词后台提取中';
  if (item.keywordStatus === 'timeout') return '提取较慢，点此重试';
  if (item.keywordStatus === 'failed') return '提取失败，点此重试';
  if (item.keywordStatus === 'empty') return '暂无可展示关键词';
  return '等待提取关键词';
};

const retryResumeKeywords = (item: ResumeItem) => {
  if (!item.resumeId || item.keywordStatus === 'loading') return;
  clearKeywordPoll(item.resumeId);
  item.keywords = [];
  hydrateOneResumeKeywords(item.resumeId, true);
};

const loadResumes = async () => {
  keywordPollTimers.forEach((timer) => clearTimeout(timer));
  keywordPollTimers.clear();
  if (!isRealUser()) {
    resumeList.value = [];
    return;
  }
  isLoading.value = true;
  try {
    const raw = await listMyResumesApi();
    // Guard against the API returning a non-array (e.g. a paginated
    // wrapper object or a null body) to prevent ".map is not a function".
    const resumes: Resume[] = Array.isArray(raw) ? raw : [];
    resumeList.value = resumes.map((r: Resume) => {
      const title = r.title || r.fileUrl?.split('/').pop() || 'Untitled.pdf';
      const localKeywords = resumeKeywords(r);
      return {
        resumeId: r.resumeId,
        name: title,
        date: formatRelative(r.updatedAt || r.createdAt),
        status: 'recent' as const,
        statusLabel: r.status || 'Active',
        fileUrl: r.fileUrl,
        fileViewUrl: r.fileViewUrl,
        isTailored: isTailoredResumeTitle(title),
        keywords: localKeywords,
        keywordStatus: localKeywords.length ? 'done' : 'idle',
      };
    });
    hydrateResumeKeywords();
  } catch (e: any) {
    // Surface the actual error instead of silently showing an empty hub.
    // Common causes: stale token after a rebuild (signature mismatch),
    // userId in storage doesn't match the JWT subject, or backend down.
    resumeList.value = [];
    const msg = e?.message || t('resume.loadFailed');
    if (msg.toLowerCase().includes('forbidden') || msg.includes('未登录') || msg.toLowerCase().includes('unauthor')) {
      uni.showToast({ title: t('resume.sessionExpired'), icon: 'none' });
      uni.removeStorageSync('token');
      uni.removeStorageSync('userId');
    } else {
      uni.showToast({ title: msg, icon: 'none' });
    }
  } finally {
    isLoading.value = false;
  }
};

const showSheet = ref(false);
const topSafeHeight = ref(88);
const rightAvoidWidth = ref(20);
const scrollTopValue = ref(0);
const RESUME_AUTO_UPLOAD_KEY = 'resume_auto_upload_once';
const topBarOpacity = computed(() => Math.min(1, Math.max(0, (scrollTopValue.value - 12) / 56)));

const handleUploadClick = () => {
  if (!requireAuth({ message: '登录后才能创建、上传和保存你的简历。' })) return;
  showSheet.value = true;
};

const openPendingUploadSheet = () => {
  if (uni.getStorageSync(RESUME_AUTO_UPLOAD_KEY) !== '1') return;
  uni.removeStorageSync(RESUME_AUTO_UPLOAD_KEY);
  setTimeout(() => {
    handleUploadClick();
  }, 250);
};

const closeSheet = () => {
  showSheet.value = false;
};

const selectAction = (type: string) => {
  closeSheet();
  if (!requireAuth({ message: '登录后才能创建、上传和保存你的简历。' })) return;
  if (type === 'upload') {
    const userId = Number(uni.getStorageSync('userId'));
    if (!userId || isNaN(userId) || userId <= 0) {
      uni.showToast({ title: t('map.toastSignIn'), icon: 'none' });
      return;
    }
    uni.chooseMessageFile({
      count: 1,
      type: 'file',
      success: async (fileRes) => {
        const file = fileRes.tempFiles?.[0];
        const fileName = file?.name || t('resume.untitledPdf');
        const filePath = file?.path;
        if (!filePath) {
          uni.showToast({ title: t('resume.noFileSelected'), icon: 'none' });
          return;
        }
        if (!/\.pdf$/i.test(fileName)) {
          uni.showToast({ title: t('resume.pdfOnly'), icon: 'none' });
          return;
        }

        uni.showLoading({ title: t('profile.uploading') });
        try {
          const fileUrl = await uploadResumeFile(filePath, 'resumes');
          const created = await createResumeApi({
            userId,
            title: fileName.replace(/\.pdf$/i, ''),
            targetJob: '',
            fileUrl,
            status: 'ACTIVE',
          });
          uni.hideLoading();
          resumeList.value.unshift({
            resumeId: created.resumeId,
            name: created.title || fileName,
            date: t('resume.justNow'),
            status: 'recent',
            statusLabel: t('resume.active'),
            fileUrl: created.fileUrl,
            fileViewUrl: created.fileViewUrl,
            isTailored: false,
            keywords: [],
            keywordStatus: 'loading',
          });
          if (created.resumeId) hydrateOneResumeKeywords(created.resumeId, true);
          uni.showToast({ title: t('resume.uploadSuccessful'), icon: 'success' });
        } catch (e: any) {
          uni.hideLoading();
          uni.showToast({ title: e?.message || t('resume.uploadFailed'), icon: 'none' });
        }
      },
      fail: () => {
        uni.showToast({ title: t('resume.noFileSelected'), icon: 'none' });
      }
    });
  } else if (type === 'template') {
    uni.navigateTo({ url: '/pages/resume/template' });
  }
};

const handlePreview = (item: ResumeItem) => {
  if (!item.resumeId) {
    uni.showToast({ title: t('resume.fileUnavailable'), icon: 'none' });
    return;
  }
  // Use authenticated backend proxy instead of the raw OSS URL.
  // This avoids the WeChat mini-program domain whitelist requirement
  // and enforces owner-only access on the server side.
  const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'https://api.careerloop.top';
  const token = uni.getStorageSync('token');
  uni.showLoading({ title: t('resume.opening') });
  uni.downloadFile({
    url: `${BASE_URL}/api/resumes/${item.resumeId}/download`,
    header: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      // Bypass the ngrok browser-warning interstitial so we get the raw PDF
      // bytes instead of an HTML page (which would render as a black screen).
      'ngrok-skip-browser-warning': 'true',
    },
    success: (dl) => {
      if (dl.statusCode === 200) {
        uni.openDocument({
          filePath: dl.tempFilePath,
          fileType: 'pdf',
          showMenu: true,
          success: () => uni.hideLoading(),
          fail: () => {
            uni.hideLoading();
            // openDocument is unreliable in the WeChat DevTools simulator;
            // works on real devices. Hint the user instead of being mysterious.
            uni.showModal({
              title: t('resume.cannotPreviewTitle'),
              content: t('resume.cannotPreviewContent'),
              showCancel: false,
              confirmText: t('resume.ok'),
            });
          },
        });
      } else {
        uni.hideLoading();
        uni.showToast({ title: t('resume.downloadFailed', { status: dl.statusCode }), icon: 'none' });
      }
    },
    fail: () => {
      uni.hideLoading();
      uni.showToast({ title: t('resume.networkError'), icon: 'none' });
    },
  });
};

const handleMore = (idx: number) => {
  if (!resumeList.value[idx]) return;

  uni.showActionSheet({
    itemList: [t('resume.actionRename'), t('resume.actionShare'), t('resume.actionDelete')],
    success: (res) => {
      if (res.tapIndex === 0) {
        const item = resumeList.value[idx];
        const oldName = item.name;
        const dotIndex = oldName.lastIndexOf('.pdf');
        const base = dotIndex > 0 ? oldName.slice(0, dotIndex) : oldName;
        uni.showModal({
          title: t('resume.renameTitle'),
          editable: true,
          placeholderText: t('resume.renamePlaceholder'),
          content: base,
          success: async (mr) => {
            if (!mr.confirm) return;
            const newBase = (mr.content || '').trim();
            if (!newBase) return;
            const newName = newBase.endsWith('.pdf') ? newBase : `${newBase}.pdf`;
            if (item.resumeId) {
              try {
                await updateResumeApi(item.resumeId, { title: newBase });
              } catch (e: any) {
                uni.showToast({ title: e?.message || t('resume.renameFailed'), icon: 'none' });
                return;
              }
            }
            item.name = newName;
            uni.showToast({ title: t('resume.renamed'), icon: 'success' });
          },
        });
      } else if (res.tapIndex === 1) {
        const item = resumeList.value[idx];
        // Share copies the short-lived presigned URL (15 min) instead of the
        // raw OSS key. The recipient can open the PDF directly until expiry;
        // after that they'll need a fresh link.
        const shareUrl = item.fileViewUrl;
        if (!shareUrl) {
          uni.showToast({ title: t('resume.fileUrlUnavailable'), icon: 'none' });
          return;
        }
        uni.setClipboardData({
          data: shareUrl,
          success: () => uni.showToast({ title: t('resume.shareCopied'), icon: 'none' }),
          fail: () => uni.showToast({ title: t('resume.copyFailed'), icon: 'none' })
        });
      } else if (res.tapIndex === 2) {
        uni.showModal({
          title: t('resume.confirmDeleteTitle'),
          content: t('resume.confirmDeleteContent'),
          success: async (r) => {
            if (!r.confirm) return;
            const item = resumeList.value[idx];
            if (item?.resumeId) {
              try {
                await deleteResumeApi(item.resumeId);
              } catch (e: any) {
                uni.showToast({ title: e?.message || t('resume.deleteFailed'), icon: 'none' });
                return;
              }
            }
            resumeList.value.splice(idx, 1);
            uni.showToast({ title: t('resume.deleted'), icon: 'success' });
          },
        });
      }
    },
  });
};

onMounted(() => {
  refreshTheme();
  const safeMetrics = getMpSafeAreaMetrics();
  topSafeHeight.value = safeMetrics.topSafeHeight;
  rightAvoidWidth.value = safeMetrics.rightAvoidWidth;
});

// Tab pages are kept alive across navigation; onShow re-fires every time
// the page becomes visible (including after login -> switchTab back).
onShow(() => {
  try {
    refreshTheme();
    loadResumes();
    openPendingUploadSheet();
  } catch (e) {
    console.error('[resume] onShow failed', e);
  }
});

onPageScroll(({ scrollTop }) => {
  scrollTopValue.value = scrollTop;
});
</script>

<style scoped>
.resume-page {
  padding: 0 var(--page-gutter, 20px);
  padding-bottom: env(safe-area-inset-bottom, 0px);
  font-family: -apple-system, BlinkMacSystemFont, "SF Pro Text", "Helvetica Neue", sans-serif;
  box-sizing: border-box;
}

.status-spacer { width: 100%; }

.page-header { padding: 8px 0 6px; }

.page-title {
  display: block;
  font-size: var(--font-hero, 28px);
  font-weight: 800;
  color: var(--text-primary, #1c1917);
}

.page-subtitle {
  display: block;
  font-size: var(--font-caption, 13px);
  color: var(--text-tertiary, #8e8e93);
  margin-top: 4px;
  line-height: var(--line-height-caption, 1.45);
}

.section-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 16px 0 14px;
}
.section-titles { display: flex; flex-direction: column; gap: 2px; }
.section-title {
  font-size: var(--font-section, 17px);
  font-weight: 700;
  color: var(--text-primary, #1c1917);
}
.section-sub { font-size: 12px; color: var(--text-tertiary, #8e8e93); }

.section-action {
  padding: 6px 12px;
  background: var(--primary-soft, #fcf5f2);
  border-radius: 999px;
  border: 1px solid #f7e8e2;
}
.section-action:active { background: var(--primary-soft, #fcf5f2); }
.section-action-text { font-size: 13px; color: var(--primary-color, #cd6a43); font-weight: 600; }

/* Skeleton placeholders shown during initial load.
   HCI: visibility of system status -- a shimmering layout previews
   what's coming, which feels much faster than a centered spinner. */
.skeleton-list { display: flex; flex-direction: column; gap: 10px; }
.skel-card {
  border-radius: var(--radius-md, 16px);
  padding: 16px;
  display: flex; align-items: center; gap: 14px;
}
.skel-square {
  width: 44px; height: 44px; border-radius: 12px;
  background: linear-gradient(90deg, #f6f1ef 0%, #fcf9f7 50%, #f6f1ef 100%);
  background-size: 200% 100%;
  animation: skel-shimmer 1.4s infinite;
  flex-shrink: 0;
}
.skel-lines { flex: 1; display: flex; flex-direction: column; gap: 8px; }
.skel-line {
  height: 12px; border-radius: 6px;
  background: linear-gradient(90deg, #f6f1ef 0%, #fcf9f7 50%, #f6f1ef 100%);
  background-size: 200% 100%;
  animation: skel-shimmer 1.4s infinite;
}
.skel-w40 { width: 40%; }
.skel-w70 { width: 70%; }
@keyframes skel-shimmer {
  0%   { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.resume-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 28px;
}

.resume-card {
  display: flex;
  align-items: center;
  border-radius: var(--radius-md, 16px);
  padding: 16px;
}

.rc-icon-wrap { margin-right: 14px; flex-shrink: 0; }

.rc-icon {
  width: 44px;
  height: 52px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.rc-icon-0 { background: linear-gradient(135deg, #f7e8e2, #f1d6cc); }
.rc-icon-1 { background: linear-gradient(135deg, #f8ece7, #f3dbd2); }

.rc-icon-text {
  font-size: 11px;
  font-weight: 800;
  color: var(--primary-color, #cd6a43);
  letter-spacing: 0.5px;
}

.rc-body { flex: 1; min-width: 0; }

.rc-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary, #1c1917);
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 6px;
}

.rc-meta-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.rc-time { font-size: 12px; color: var(--text-tertiary, #8e8e93); }

.keyword-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6rpx;
  margin-top: 8rpx;
}

.keyword-pill {
  max-width: 116rpx;
  padding: 3rpx 9rpx;
  border-radius: 999rpx;
  background: var(--surface-3, #f5f5f4);
  color: var(--text-secondary, #78716c);
  font-size: 10px;
  line-height: 1.35;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.keyword-empty {
  display: block;
  margin-top: 8rpx;
  font-size: 10px;
  color: var(--text-tertiary, #8e8e93);
}

.rc-badge { padding: 2px 8px; border-radius: 6px; flex-shrink: 0; }

.badge-recent { background: var(--primary-soft, #fcf5f2); }
.badge-recent .rc-badge-text { color: var(--primary-color, #cd6a43); }

.badge-normal { background: #fbf4f2; }
.badge-normal .rc-badge-text { color: #d37b58; }

.rc-badge-text { font-size: 11px; font-weight: 600; }

.rc-actions { display: flex; align-items: center; gap: 8px; margin-left: 10px; }

.rc-action-btn { padding: 6px 14px; border-radius: 10px; background: var(--primary-soft, #fcf5f2); }

.rc-action-btn:active { background: var(--primary-soft, #fcf5f2); }

.rc-action-text { font-size: 13px; font-weight: 500; color: var(--primary-color, #cd6a43); }

.rc-more { padding: 6px 4px; }

.rc-more-dots { font-size: 16px; color: var(--text-tertiary, #8e8e93); font-weight: 700; letter-spacing: 1px; }

.add-card {
  display: flex;
  align-items: center;
  border: 1.5px dashed #e8baa8;
  border-radius: var(--radius-md, 16px);
  padding: 16px;
  gap: 14px;
  box-shadow: var(--shadow-xs, 0 1px 3px rgba(0,0,0,0.08), 0 1px 8px rgba(0,0,0,0.05));
  transition: all 0.2s;
}

.add-card:active {
  border-color: #e8baa8;
  background: var(--primary-soft, #fcf5f2);
  transform: scale(0.98);
}

.add-icon {
  width: 44px;
  height: 44px;
  border-radius: 14px;
  background: var(--primary-soft, #fcf5f2);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.add-plus { font-size: 22px; color: var(--primary-color, #cd6a43); font-weight: 300; }

.add-info { display: flex; flex-direction: column; }

.add-title { font-size: 15px; font-weight: 600; color: var(--primary-hover, #c25c33); margin-bottom: 3px; }

.add-desc { font-size: 12px; color: var(--text-secondary, #78716c); }

.bottom-safe {
  height: calc(var(--tab-bar-height, 50px) + 20px);
}

.empty-state {
  padding: 24px 0 28px;
}

.empty-icon {
  display: block;
  font-size: 40px;
  margin-bottom: 12px;
}

.empty-title {
  display: block;
  font-size: 17px;
  font-weight: 700;
  color: var(--text-primary, #1c1917);
}

.empty-desc {
  display: block;
  margin: 8px 0 18px;
  font-size: 13px;
  line-height: 1.5;
  color: var(--text-secondary, #78716c);
}

/* ---- Action sheet ---- */
.sheet-mask {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0, 0, 0, 0.35);
  z-index: 998;
}

.sheet {
  position: fixed;
  left: 12px; right: 12px;
  bottom: -320px;
  z-index: 999;
  transition: bottom 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
}

.sheet-open { bottom: 30px; }

.sheet-title-bar {
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  text-align: center;
  padding: 16px;
  border-top-left-radius: 16px;
  border-top-right-radius: 16px;
  border-bottom: 0.5px solid rgba(60, 60, 67, 0.1);
}

.sheet-title { font-size: 13px; color: #8e8e93; font-weight: 500; }

.sheet-option {
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  display: flex;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 0.5px solid rgba(60, 60, 67, 0.1);
}

.sheet-option:last-of-type {
  border-bottom-left-radius: 16px;
  border-bottom-right-radius: 16px;
  border-bottom: none;
}

.sheet-option-icon { font-size: 18px; width: 22px; text-align: center; flex-shrink: 0; }

.sheet-option-text { font-size: 17px; color: #c95f36; }

.sheet-option-col { display: flex; flex-direction: column; gap: 4px; }
.sheet-option-row { display: flex; align-items: center; gap: 10px; }
.sheet-option-hint { font-size: 11px; color: #8e8e93; padding-left: 32px; line-height: 1.4; }

.sheet-cancel {
  background: #ffffff;
  text-align: center;
  padding: 16px;
  border-radius: 16px;
  margin-top: 10px;
}

.sheet-cancel-text { font-size: 17px; font-weight: 600; color: #c95f36; }

.is-dark { background: #1c1917; }

.is-dark .page-title,
.is-dark .section-title,
.is-dark .rc-name,
.is-dark .add-title { color: #fafaf9; }

.is-dark .page-subtitle,
.is-dark .section-count,
.is-dark .rc-time,
.is-dark .add-desc { color: var(--text-tertiary, #8e8e93); }

.is-dark .resume-card,
.is-dark .add-card,
.is-dark .sheet-option,
.is-dark .sheet-title-bar,
.is-dark .sheet-cancel { background: #292524; border-color: #44403c; }
.is-dark .empty-state {
  background: transparent;
  border: none;
}

.is-dark .skel-card { background: #292524; }
.is-dark .skel-line { background: linear-gradient(90deg, #292524 0%, #44403c 50%, #292524 100%); background-size: 200% 100%; }
.is-dark .skel-square { background: linear-gradient(90deg, #292524 0%, #44403c 50%, #292524 100%); background-size: 200% 100%; }
.is-dark .section-action { background: rgba(205, 106, 67, 0.15); border-color: rgba(205, 106, 67, 0.3); }
.is-dark .section-action-text { color: #dd987d; }
.is-dark .badge-recent { background: rgba(205, 106, 67, 0.15); }
.is-dark .badge-recent .rc-badge-text { color: #dd987d; }
.is-dark .badge-normal { background: rgba(211, 123, 88, 0.15); }
.is-dark .badge-normal .rc-badge-text { color: #e3ac96; }
.is-dark .rc-action-btn { background: rgba(205, 106, 67, 0.15); }
.is-dark .rc-action-btn:active { background: rgba(205, 106, 67, 0.25); }
.is-dark .rc-action-text { color: #dd987d; }
.is-dark .add-card:active { background: rgba(205, 106, 67, 0.08); border-color: rgba(205, 106, 67, 0.3); }
.is-dark .add-icon { background: rgba(205, 106, 67, 0.15); }
.is-dark .add-plus { color: #dd987d; }
.is-dark .rc-more-dots { color: var(--text-secondary, #78716c); }

/* ================================================================
 *  MP-WEIXIN parity overrides (scoped to resume hub page)
 * ================================================================ */
/* #ifdef MP-WEIXIN */

/* Sheet panels already have 96% opacity — just strip the
   unneeded backdrop-filter call. */
.sheet-title-bar,
.sheet-option {
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
  background: #ffffff;
}

/* Resume cards and add cards: overflow:visible for shadow */
.resume-card,
.add-card {
  overflow: visible;
}

/* Dark on MP: sheet rows above force #fff — re-apply dark surfaces with
   page-scoped specificity so action sheets match the theme. */
.resume-page.is-dark .sheet-title-bar,
.resume-page.is-dark .sheet-option {
  background: #292524;
  border-color: #44403c;
}

.resume-page.is-dark .sheet-title {
  color: var(--text-tertiary, #8e8e93);
}

.resume-page.is-dark .sheet-option-text {
  color: #e8baa8;
}

.resume-page.is-dark .sheet-option-hint {
  color: var(--text-secondary, #78716c);
}

.resume-page.is-dark .sheet-cancel {
  background: #292524;
}

.resume-page.is-dark .sheet-cancel-text {
  color: #e8baa8;
}

.resume-page.is-dark .section-action {
  background: rgba(205, 106, 67, 0.2);
  border: 1px solid rgba(205, 106, 67, 0.35);
}

.resume-page.is-dark .section-action-text {
  color: #e8baa8;
}

.resume-page.is-dark .rc-action-btn {
  background: rgba(205, 106, 67, 0.2);
  border: 1px solid rgba(205, 106, 67, 0.35);
}

.resume-page.is-dark .rc-action-text {
  color: #e8baa8;
}

.resume-page.is-dark .rc-icon-0 {
  background: linear-gradient(135deg, rgba(212, 127, 93, 0.42), rgba(205, 106, 67, 0.24));
  border: 1px solid rgba(232, 186, 168, 0.34);
}

.resume-page.is-dark .rc-icon-1 {
  background: linear-gradient(135deg, rgba(219, 149, 121, 0.42), rgba(211, 123, 88, 0.24));
  border: 1px solid rgba(243, 219, 210, 0.34);
}

.resume-page.is-dark .rc-icon-text {
  color: #fafaf9;
}

.resume-page.is-dark .keyword-pill {
  background: #44403c;
  color: #d6d3d1;
}

.resume-page.is-dark .badge-recent {
  background: rgba(205, 106, 67, 0.2);
}

.resume-page.is-dark .badge-recent .rc-badge-text {
  color: #e8baa8;
}

.resume-page.is-dark .badge-normal {
  background: rgba(219, 149, 121, 0.2);
}

.resume-page.is-dark .badge-normal .rc-badge-text {
  color: #efcfc3;
}

.resume-page.is-dark .add-card {
  border-color: rgba(232, 186, 168, 0.35);
  background: #292524;
}

.resume-page.is-dark .add-card:active {
  background: rgba(205, 106, 67, 0.12);
}

.resume-page.is-dark .add-icon {
  background: rgba(205, 106, 67, 0.2);
}

.resume-page.is-dark .add-plus {
  color: #e8baa8;
}

.resume-page.is-dark .add-title {
  color: #fafaf9;
}

/* #endif */

/* 针对岗位优化后的简历分区样式 */
.section-bar-ai { margin-top: 24px; }
.ai-section-label-row { display: flex; align-items: center; gap: 6px; }
.ai-badge {
  background: linear-gradient(135deg, #d27855, #cd6a43);
  border-radius: 6px; padding: 2px 6px;
}
.ai-badge-text { font-size: 10px; font-weight: 800; color: #fff; letter-spacing: 0.5px; }
.rc-icon-ai {
  background: linear-gradient(135deg, #faf1ed, #f7e8e2) !important;
}
.rc-icon-ai .rc-icon-text { color: #d27855 !important; font-size: 11px !important; }
.resume-card-ai { border-left: 3px solid #d27855; }
.badge-ai { background: #faf1ed !important; }
.badge-ai .rc-badge-text { color: #d27855 !important; font-size: 10px !important; }

.resume-page.is-dark .resume-card-ai { border-left-color: #e6b39f; }
.resume-page.is-dark .rc-icon-ai { background: rgba(210, 120, 85,0.2) !important; }
.resume-page.is-dark .badge-ai { background: rgba(210, 120, 85,0.2) !important; }
.resume-page.is-dark .badge-ai .rc-badge-text { color: #e6b39f !important; }

/* ================================================================
 * CareerLoop editorial skin — shared with the student website.
 * ================================================================ */
.resume-page {
  background: #faf9f6;
  color: #2c2b29;
  font-family: "Noto Sans SC", "PingFang SC", "Microsoft YaHei", sans-serif;
}

.page-header {
  padding-top: 10px;
  padding-bottom: 10px;
}

.page-title,
.section-title,
.empty-title,
.rc-name,
.add-title {
  color: #2c2b29;
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
  font-weight: 600;
  letter-spacing: 0.05em;
}

.page-title {
  font-size: 27px;
}

.page-subtitle,
.section-sub,
.rc-time,
.keyword-empty {
  color: #8b8a86;
}

.section-bar {
  margin: 18px 0 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid #edece8;
}

.section-action {
  min-height: 40px;
  padding: 0 14px;
  border: 1px solid #c23b22;
  border-radius: 6px;
  background: #c23b22;
  display: flex;
  align-items: center;
  box-sizing: border-box;
}

.section-action:active {
  background: #a9321d;
}

.section-action-text {
  color: #faf9f6;
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.resume-list {
  gap: 10px;
}

.skel-card,
.resume-card {
  border: 1px solid #e0dfdb;
  border-radius: 8px;
  background: #faf9f6;
  box-shadow: 0 5px 16px rgba(44, 43, 41, 0.045);
}

.skel-square,
.skel-line {
  background: linear-gradient(90deg, #efeee9 0%, #f7f6f2 50%, #efeee9 100%);
  background-size: 200% 100%;
}

.resume-card {
  padding: 15px;
}

.rc-icon {
  width: 42px;
  height: 50px;
  border: 1px solid #d4d2cc;
  border-radius: 6px;
  background: #f5f5f0;
}

.rc-icon-0,
.rc-icon-1 {
  background: #f5f5f0;
}

.rc-icon-text {
  color: #ac6448;
  font-weight: 600;
  letter-spacing: 0.08em;
}

.rc-name {
  font-size: 15px;
}

.keyword-pill {
  padding: 3rpx 9rpx;
  border: 1px solid #e0dfdb;
  border-radius: 4px;
  background: #f5f5f0;
  color: #5a5956;
  font-weight: 500;
}

.rc-badge {
  padding: 2px 7px;
  border: 1px solid #d9d8d3;
  border-radius: 4px;
  background: transparent;
}

.badge-recent {
  border-color: rgba(194, 59, 34, 0.34);
  background: rgba(194, 59, 34, 0.06);
}

.badge-recent .rc-badge-text {
  color: #c23b22;
}

.badge-normal {
  background: #f5f5f0;
}

.badge-normal .rc-badge-text {
  color: #5a5956;
}

.rc-badge-text {
  font-weight: 500;
}

.rc-action-btn {
  min-height: 40px;
  padding: 0 13px;
  border: 1px solid #c23b22;
  border-radius: 6px;
  background: transparent;
  display: flex;
  align-items: center;
  box-sizing: border-box;
}

.rc-action-btn:active {
  background: rgba(194, 59, 34, 0.08);
}

.rc-action-text {
  color: #c23b22;
  font-weight: 500;
}

.rc-more {
  min-width: 40px;
  min-height: 40px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.rc-more-dots {
  color: #8b8a86;
  font-weight: 500;
}

.add-card {
  min-height: 76px;
  padding: 15px;
  gap: 14px;
  border: 1px dashed #b9b7b1;
  border-radius: 8px;
  background: #f5f5f0;
  box-shadow: none;
}

.add-card:active {
  border-color: #c23b22;
  background: rgba(194, 59, 34, 0.05);
  transform: scale(0.99);
}

.add-icon {
  width: 44px;
  height: 44px;
  border: 1px solid rgba(194, 59, 34, 0.28);
  border-radius: 6px;
  background: #faf9f6;
}

.add-plus,
.add-title {
  color: #c23b22;
}

.add-desc,
.empty-desc {
  color: #5a5956;
}

.empty-icon {
  color: #ac6448;
}

.sheet-mask {
  background: rgba(44, 43, 41, 0.34);
}

.sheet {
  left: 16px;
  right: 16px;
}

.sheet-title-bar,
.sheet-option,
.sheet-cancel {
  background: #faf9f6;
  border-color: #e0dfdb;
}

.sheet-title-bar {
  border-top-left-radius: 8px;
  border-top-right-radius: 8px;
}

.sheet-option:last-of-type {
  border-bottom-left-radius: 8px;
  border-bottom-right-radius: 8px;
}

.sheet-title {
  color: #8b8a86;
  letter-spacing: 0.04em;
}

.sheet-option {
  min-height: 56px;
  box-sizing: border-box;
}

.sheet-option-icon {
  color: #ac6448;
}

.sheet-option-text,
.sheet-cancel-text {
  color: #c23b22;
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.sheet-option-hint {
  color: #8b8a86;
}

.sheet-cancel {
  border: 1px solid #e0dfdb;
  border-radius: 8px;
}

.section-bar-ai {
  margin-top: 26px;
}

.ai-badge {
  border: 1px solid #ac6448;
  border-radius: 4px;
  background: #ac6448;
}

.ai-badge-text {
  font-weight: 600;
}

.rc-icon-ai {
  border-color: rgba(172, 100, 72, 0.32) !important;
  background: rgba(172, 100, 72, 0.06) !important;
}

.rc-icon-ai .rc-icon-text {
  color: #ac6448 !important;
}

.resume-card-ai {
  border-left: 2px solid #ac6448;
}

.badge-ai {
  border-color: rgba(172, 100, 72, 0.3) !important;
  background: rgba(172, 100, 72, 0.06) !important;
}

.badge-ai .rc-badge-text {
  color: #ac6448 !important;
  font-weight: 500 !important;
}

.resume-page.is-dark {
  background: #1f1f1d;
}

.resume-page.is-dark .page-title,
.resume-page.is-dark .section-title,
.resume-page.is-dark .empty-title,
.resume-page.is-dark .rc-name {
  color: #f5f5f0;
}

.resume-page.is-dark .skel-card,
.resume-page.is-dark .resume-card,
.resume-page.is-dark .add-card,
.resume-page.is-dark .sheet-title-bar,
.resume-page.is-dark .sheet-option,
.resume-page.is-dark .sheet-cancel {
  background: #292926;
  border-color: #45443f;
  box-shadow: none;
}

.resume-page.is-dark .section-action {
  border-color: #c23b22;
  background: #c23b22;
}

.resume-page.is-dark .section-action-text {
  color: #faf9f6;
}

.resume-page.is-dark .rc-icon,
.resume-page.is-dark .add-icon {
  background: #33332f;
  border-color: #575650;
}

.resume-page.is-dark .keyword-pill,
.resume-page.is-dark .badge-normal {
  background: #33332f;
  border-color: #575650;
  color: #d4d2cc;
}

.resume-page.is-dark .rc-action-btn {
  border-color: #d8644d;
  background: transparent;
}

.resume-page.is-dark .rc-action-text,
.resume-page.is-dark .add-plus,
.resume-page.is-dark .add-title,
.resume-page.is-dark .sheet-option-text,
.resume-page.is-dark .sheet-cancel-text {
  color: #e27b66;
}

.resume-page.is-dark .rc-icon-ai,
.resume-page.is-dark .badge-ai {
  background: rgba(202, 135, 108, 0.13) !important;
  border-color: rgba(219, 170, 151, 0.35) !important;
}

.resume-page.is-dark .resume-card-ai {
  border-left-color: #d3a18d;
}

.resume-page.is-dark .badge-ai .rc-badge-text,
.resume-page.is-dark .rc-icon-ai .rc-icon-text {
  color: #e5beaf !important;
}
</style>
