<template>
  <SlPage class="app-soft-bg" :custom-class="[themeClass, fontClass].join(' ')">
    <!-- Custom nav bar -->
    <SlNavBar :title="t('memory.navTitle')" show-back @back="goBack" :safe-top="topSafe" />

    <view class="memory-page">
      <!-- Header -->
      <view class="page-header">
        <text class="page-title">{{ t('memory.title') }}</text>
        <text class="page-subtitle">{{ t('memory.subtitle') }}</text>
      </view>

      <view v-if="!loading" class="context-section">
        <view class="context-card app-card-soft app-surface">
          <view class="context-head">
            <text class="context-icon ri-user-line"></text>
            <text class="context-title">{{ t('memory.profileTitle') }}</text>
          </view>
          <view v-if="profileItems.length === 0" class="context-empty app-empty">
            <text class="context-empty-text">{{ t('memory.profileEmpty') }}</text>
          </view>
          <view v-else class="context-list">
            <view v-for="item in profileItems" :key="item.label" class="context-row">
              <text class="context-label">{{ item.label }}</text>
              <text class="context-value">{{ item.value }}</text>
            </view>
          </view>
        </view>

        <view class="context-card app-card-soft app-surface">
          <view class="context-head">
            <text class="context-icon ri-compass-3-line"></text>
            <text class="context-title">{{ t('memory.snapshotTitle') }}</text>
          </view>
          <view v-if="snapshotItems.length === 0" class="context-empty app-empty">
            <text class="context-empty-text">{{ t('memory.snapshotEmpty') }}</text>
          </view>
          <view v-else class="context-list">
            <view v-for="item in snapshotItems" :key="item.label" class="context-row">
              <text class="context-label">{{ item.label }}</text>
              <text class="context-value">{{ item.value }}</text>
            </view>
          </view>
        </view>
      </view>

      <!-- Loading skeleton -->
      <view v-if="loading" class="skeleton-wrap">
        <view v-for="i in 5" :key="i" class="skeleton-card app-card-soft app-surface"></view>
      </view>

      <!-- Empty state -->
      <view v-else-if="facts.length === 0" class="empty-state app-empty app-surface">
        <text class="empty-icon ri-brain-line"></text>
        <text class="empty-title">{{ t('memory.noFacts') }}</text>
        <text class="empty-desc">{{ t('memory.noFactsDesc') }}</text>
      </view>

      <!-- Facts grouped by category -->
      <view v-else>
        <view v-for="(group, cat) in grouped" :key="cat" class="group-section">
          <view class="group-header">
            <text class="group-icon" :class="categoryIcon(cat)"></text>
            <text class="group-label">{{ categoryLabel(cat) }}</text>
            <text class="group-count">{{ group.length }}</text>
          </view>
          <view class="facts-card app-card-soft app-surface">
            <view
              v-for="(fact, idx) in group"
              :key="fact.id"
              class="fact-row"
              :class="{ 'fact-row-last': idx === group.length - 1 }"
            >
              <view class="fact-body">
                <text class="fact-key">{{ formatKey(fact.factKey) }}</text>
                <text class="fact-value">{{ fact.factValue }}</text>
                <text class="fact-time">{{ relativeTime(fact.updatedAt) }}</text>
              </view>
              <view class="fact-conf-badge" :class="confClass(fact.confidence)">
                <text class="fact-conf-text">{{ confLabel(fact.confidence) }}</text>
              </view>
              <view class="fact-delete-btn" @click="confirmDelete(fact)" hover-class="press-fb" hover-stay-time="120">
                <text class="fact-delete-icon ri-close-line"></text>
              </view>
            </view>
          </view>
        </view>

        <!-- Clear all -->
        <view class="clear-all-row">
          <view class="btn-clear-all" @click="confirmClearAll" hover-class="press-fb" hover-stay-time="120">
            <text class="btn-clear-all-text">{{ t('memory.clearAll') }}</text>
          </view>
        </view>
      </view>

      <view class="bottom-safe"></view>
    </view>
  </SlPage>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useI18n } from '@/locales';
import { onShow } from '@dcloudio/uni-app';
import { getMpSafeAreaMetrics } from '@/utils/safeArea';
import request from '@/utils/request';
import { useTheme } from '@/utils/theme';
import { getProfileSnapshotApi, getUserInfoApi, type User, type UserProfileSnapshot } from '@/api/user';
import { isRealUser, requireAuth } from '@/utils/auth';
import SlPage from '@/style-library/components/SlPage.vue';
import SlNavBar from '@/style-library/components/SlNavBar.vue';

interface UserFact {
  id: number;
  category: string;
  factKey: string;
  factValue: string;
  confidence: number;
  source: string;
  updatedAt: string;
}

const topSafe = ref(44);
const loading = ref(true);
const facts = ref<UserFact[]>([]);
const userProfile = ref<User | null>(null);
const profileSnapshot = ref<UserProfileSnapshot | null>(null);
const { t } = useI18n();
const { themeClass, fontClass, refresh: refreshTheme } = useTheme();

const ensurePageAuth = () => {
  if (isRealUser()) return true;
  loading.value = false;
  facts.value = [];
  userProfile.value = null;
  profileSnapshot.value = null;
  return requireAuth({
    redirect: 'reLaunch',
    cancelBehavior: 'back',
    message: '登录后才能查看或管理 AI 为你整理的长期记忆。',
  });
};

onMounted(async () => {
  refreshTheme();
  topSafe.value = getMpSafeAreaMetrics().topSafeHeight;
  await loadFacts();
});

onShow(() => {
  refreshTheme();
});

const loadFacts = async () => {
  if (!ensurePageAuth()) return;
  loading.value = true;
  try {
    const userId = Number(uni.getStorageSync('userId'));
    const [res, profile, snapshot] = await Promise.all([
      request<UserFact[]>({ url: '/api/facts/me', method: 'GET' }),
      userId > 0 ? getUserInfoApi(userId) : Promise.resolve(null),
      getProfileSnapshotApi(),
    ]);
    facts.value = Array.isArray(res) ? res : [];
    userProfile.value = profile;
    profileSnapshot.value = snapshot;
  } catch (e: any) {
    uni.showToast({ title: e?.message || t('memory.loadFailed'), icon: 'none' });
  } finally {
    loading.value = false;
  }
};

const profileItems = computed(() => {
  const u = userProfile.value;
  if (!u) return [];
  return [
    { label: t('memory.profileNickname'), value: u.nickname },
    { label: t('memory.profileSchool'), value: u.school },
    { label: t('memory.profileMajor'), value: u.major },
    { label: t('memory.profileGraduation'), value: u.graduationYear ? String(u.graduationYear) : '' },
  ].filter((item) => item.value);
});

const snapshotItems = computed(() => {
  const s = profileSnapshot.value;
  if (!s) return [];
  const items: { label: string; value?: string }[] = [];
  if (s.assessment) {
    const roles = s.assessment.suggestedRoles?.join(', ');
    items.push({
      label: t('memory.snapshotAssessment'),
      value: [s.assessment.scaleTitle, s.assessment.summary, roles].filter(Boolean).join(' · '),
    });
  }
  if (s.resume) {
    items.push({
      label: t('memory.snapshotResume'),
      value: [s.resume.title, s.resume.targetJob, s.resume.diagnosisScore ? `${s.resume.diagnosisScore}/100` : ''].filter(Boolean).join(' · '),
    });
  }
  if (s.interview) {
    items.push({
      label: t('memory.snapshotInterview'),
      value: [s.interview.positionName, s.interview.difficulty, s.interview.lastScore ? `${s.interview.lastScore}/100` : ''].filter(Boolean).join(' · '),
    });
  }
  if (s.preferences?.targetRole) {
    items.push({ label: t('memory.snapshotTargetRole'), value: s.preferences.targetRole });
  }
  if (s.preferences?.interviewMode) {
    items.push({ label: t('memory.snapshotInterviewMode'), value: s.preferences.interviewMode });
  }
  return items.filter((item) => item.value);
});

const grouped = computed(() => {
  const map: Record<string, UserFact[]> = {};
  for (const f of facts.value) {
    const cat = f.category || 'GENERAL';
    if (!map[cat]) map[cat] = [];
    map[cat].push(f);
  }
  return map;
});

const categoryLabel = (cat: string) => {
  const labels: Record<string, string> = {
    PERSONALITY: t('memory.categoryPersonality'),
    CAREER_GOAL: t('memory.categoryCareerGoal'),
    SKILL: t('memory.categorySkill'),
    PREFERENCE: t('memory.categoryPreference'),
    EXPERIENCE: t('memory.categoryExperience'),
    GENERAL: t('memory.categoryGeneral'),
  };
  return labels[cat] || cat;
};

const categoryIcon = (cat: string) => {
  const icons: Record<string, string> = {
    PERSONALITY: 'ri-dna-line',
    CAREER_GOAL: 'ri-focus-2-line',
    SKILL: 'ri-flashlight-line',
    PREFERENCE: 'ri-lightbulb-line',
    EXPERIENCE: 'ri-folder-2-line',
    GENERAL: 'ri-pushpin-line',
  };
  return icons[cat] || 'ri-pushpin-line';
};

const formatKey = (key: string) =>
  key.replace(/_/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase());

const relativeTime = (iso?: string): string => {
  if (!iso) return '';
  const diff = Date.now() - new Date(iso).getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 1) return '刚刚';
  if (mins < 60) return `${mins} 分钟前`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours} 小时前`;
  const days = Math.floor(hours / 24);
  if (days < 30) return `${days} 天前`;
  const months = Math.floor(days / 30);
  if (months < 12) return `${months} 个月前`;
  return `${Math.floor(months / 12)} 年前`;
};

const confLabel = (conf: number) => {
  if (conf >= 0.9) return t('memory.confidenceHigh');
  if (conf >= 0.6) return t('memory.confidenceMed');
  return t('memory.confidenceLow');
};

const confClass = (conf: number) => {
  if (conf >= 0.9) return 'conf-high';
  if (conf >= 0.6) return 'conf-med';
  return 'conf-low';
};

const confirmDelete = (fact: UserFact) => {
  if (!ensurePageAuth()) return;
  uni.showModal({
    title: t('memory.deleteTitle'),
    content: t('memory.deleteContent', { label: formatKey(fact.factKey), value: fact.factValue }),
    confirmText: t('common.delete'),
    confirmColor: '#ef4444',
    success: async (res) => {
      if (!res.confirm) return;
      try {
        await request({ url: `/api/facts/me/${fact.id}`, method: 'DELETE' });
        facts.value = facts.value.filter((f) => f.id !== fact.id);
        uni.showToast({ title: t('memory.deleted'), icon: 'success' });
      } catch (e: any) {
        uni.showToast({ title: e?.message || t('memory.deleteFailed'), icon: 'none' });
      }
    },
  });
};

const confirmClearAll = () => {
  if (!ensurePageAuth()) return;
  uni.showModal({
    title: t('memory.clearAllTitle'),
    content: t('memory.clearAllContent'),
    confirmText: t('memory.clearAllConfirm'),
    confirmColor: '#ef4444',
    success: async (res) => {
      if (!res.confirm) return;
      try {
        const ids = facts.value.map((f) => f.id);
        await Promise.all(ids.map((id) => request({ url: `/api/facts/me/${id}`, method: 'DELETE' })));
        facts.value = [];
        uni.showToast({ title: t('memory.clearAllSuccess'), icon: 'success' });
      } catch {
        uni.showToast({ title: t('memory.clearAllPartialFailed'), icon: 'none' });
      }
    },
  });
};

const goBack = () => uni.navigateBack();
</script>

<style scoped>
.memory-page {
  padding: 0 var(--page-gutter, 20px) 0;
  box-sizing: border-box;
}

/* Header */
.page-header { padding: 8px 4px 20px; }
.page-title { display: block; font-size: 26px; font-weight: 800; color: var(--text-primary, #1c1917); }
.page-subtitle { display: block; margin-top: 6px; font-size: 13px; line-height: 1.55; color: var(--text-secondary, #78716c); }

.context-section { display: flex; flex-direction: column; gap: 12px; margin-bottom: 18px; }
.context-card {
  background: var(--surface-1, #ffffff); border-radius: var(--radius-md, 16px);
  border: 1px solid var(--border-color, #e7e5e4);
  box-shadow: var(--shadow-sm);
  padding: 14px;
}
.context-head { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; }
.context-icon { font-size: 17px; }
.context-title { font-size: 15px; font-weight: 800; color: var(--text-primary, #1c1917); }
.context-list { display: flex; flex-direction: column; gap: 8px; }
.context-row { display: flex; gap: 12px; align-items: flex-start; }
.context-label { width: 92px; flex-shrink: 0; font-size: 12px; font-weight: 700; color: var(--text-secondary, #78716c); }
.context-value { flex: 1; font-size: 13px; line-height: 1.45; color: var(--text-primary, #1c1917); word-break: break-word; }
.context-empty { background: var(--surface-2, #fafaf9); border-radius: 12px; padding: 10px; }
.context-empty-text { font-size: 12px; line-height: 1.5; color: var(--text-secondary, #78716c); }

/* Skeleton */
.skeleton-wrap { display: flex; flex-direction: column; gap: 12px; margin-top: 8px; }
.skeleton-card { height: 72px; border-radius: 14px; background: var(--surface-3, #f5f5f4); animation: pulse 1.5s ease-in-out infinite; }
@keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.5; } }

/* Empty */
.empty-state { padding: 60px 0 40px; text-align: center; }
.empty-icon { font-size: 52px; display: block; margin-bottom: 12px; }
.empty-title { display: block; font-size: 17px; font-weight: 700; color: var(--text-primary, #1c1917); margin-bottom: 8px; }
.empty-desc { display: block; font-size: 13px; line-height: 1.55; color: var(--text-secondary, #78716c); max-width: 280px; margin: 0 auto; }

/* Group */
.group-section { margin-bottom: 20px; }
.group-header {
  display: flex; align-items: center; gap: 8px;
  margin-bottom: 8px; padding: 0 4px;
}
.group-icon { font-size: 16px; }
.group-label { font-size: 12px; font-weight: 700; color: var(--text-secondary, #78716c); text-transform: uppercase; letter-spacing: 0.5px; flex: 1; }
.group-count {
  font-size: 11px; color: var(--text-tertiary, #8e8e93); background: var(--surface-3, #f5f5f4);
  border-radius: 10px; padding: 2px 7px; font-weight: 600;
}

/* Facts card */
.facts-card {
  background: var(--surface-1, #ffffff); border-radius: var(--radius-md, 16px);
  border: 1px solid var(--border-color, #e7e5e4);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
}

.fact-row {
  display: flex; align-items: center; gap: 10px;
  padding: 12px 14px;
  border-bottom: 1px solid #f5f5f4;
  transition: background 0.15s;
}

.fact-row-last { border-bottom: none; }
.fact-row:active { background: var(--surface-2, #fafaf9); }

.fact-body { flex: 1; min-width: 0; }
.fact-key { display: block; font-size: 12px; font-weight: 600; color: var(--text-secondary, #78716c); margin-bottom: 2px; }
.fact-value { display: block; font-size: 14px; font-weight: 500; color: var(--text-primary, #1c1917); word-break: break-word; }
.fact-time { display: block; font-size: 11px; color: var(--text-tertiary, #8e8e93); margin-top: 3px; }

/* Confidence badges */
.fact-conf-badge {
  flex-shrink: 0; border-radius: 8px; padding: 3px 8px;
}
.conf-high { background: #f5efe3; }
.conf-med { background: #fef9c3; }
.conf-low { background: #fee2e2; }
.fact-conf-text { font-size: 11px; font-weight: 700; }
.conf-high .fact-conf-text { color: #5b4720; }
.conf-med .fact-conf-text { color: #854d0e; }
.conf-low .fact-conf-text { color: #991b1b; }

/* Delete button */
.fact-delete-btn {
  flex-shrink: 0; width: 28px; height: 28px;
  border-radius: 50%; background: var(--surface-3, #f5f5f4);
  display: flex; align-items: center; justify-content: center;
}
.fact-delete-btn:active { background: #fee2e2; }
.fact-delete-icon { font-size: 12px; color: var(--text-tertiary, #8e8e93); font-weight: 700; }

/* Clear all */
.clear-all-row { padding: 8px 0 16px; display: flex; justify-content: center; }
.btn-clear-all {
  padding: 10px 24px; border-radius: 12px;
  border: 1px solid #fca5a5;
}
.btn-clear-all-text { font-size: 13px; color: var(--danger-color, #ef4444); font-weight: 600; }

.bottom-safe { height: 80px; }

.is-dark .empty-title,
.is-dark .fact-value,
.is-dark .context-title,
.is-dark .context-value { color: #fafaf9; }
.is-dark .page-subtitle,
.is-dark .empty-desc,
.is-dark .group-label,
.is-dark .fact-key,
.is-dark .context-label,
.is-dark .context-empty-text { color: var(--text-tertiary, #8e8e93); }
.is-dark .facts-card,
.is-dark .context-card { background: #292524; border-color: #44403c; box-shadow: none; }
.is-dark .context-empty { background: #44403c; }
.is-dark .fact-row { border-bottom-color: #44403c; }
.is-dark .fact-row:active { background: #44403c; }
.is-dark .group-count,
.is-dark .fact-delete-btn { background: #44403c; }
.is-dark .skeleton-card { background: #44403c; }

.font-compact .page-title { font-size: 24px; }
.font-compact .page-subtitle,
.font-compact .empty-desc,
.font-compact .fact-value { font-size: 12px; }
.font-large .page-title { font-size: 34px; }
.font-large .page-subtitle,
.font-large .empty-desc,
.font-large .fact-value { font-size: 17px; }

/* Competition visual system ------------------------------------------------ */
.page-title,
.context-title,
.empty-title,
.group-label {
  color: #2c2b29;
  font-family: "Songti SC", STSong, serif;
  font-weight: 600;
  letter-spacing: 0.05em;
}

.page-subtitle,
.context-label,
.context-empty-text,
.fact-key,
.empty-desc {
  color: #5a5956;
}

.context-card,
.facts-card,
.empty-state {
  background: #ffffff;
  border: 1px solid #e0dfdb;
  border-radius: 8px;
  box-shadow: 0 6px 18px rgba(44, 43, 41, 0.04);
}

.context-icon,
.group-icon {
  color: #ac6448;
}

.context-empty {
  background: #f5f5f0;
  border-radius: 4px;
}

.group-count {
  color: #8b8a86;
  background: #efeee9;
  border-radius: 3px;
}

.fact-row {
  border-color: #edece8;
}

.fact-row:active {
  background: #f5f5f0;
}

.fact-value {
  color: #2c2b29;
}

.fact-conf-badge {
  background: transparent;
  border-radius: 3px;
}

.conf-high {
  border: 1px solid rgba(148, 127, 84, 0.42);
}

.conf-med {
  border: 1px solid rgba(184, 151, 90, 0.44);
}

.conf-low {
  border: 1px solid rgba(194, 59, 34, 0.36);
}

.conf-high .fact-conf-text {
  color: #947f54;
}

.conf-med .fact-conf-text {
  color: #987632;
}

.conf-low .fact-conf-text {
  color: #c23b22;
}

.fact-delete-btn {
  background: #efeee9;
  border-radius: 4px;
}

.btn-clear-all {
  border-color: rgba(194, 59, 34, 0.42);
  border-radius: 4px;
}

.btn-clear-all-text {
  color: #c23b22;
}

.skeleton-card {
  background: #efeee9;
  border-radius: 6px;
}

.is-dark .page-title,
.is-dark .context-title,
.is-dark .empty-title,
.is-dark .fact-value {
  color: #faf9f6;
}

.is-dark .context-card,
.is-dark .facts-card,
.is-dark .empty-state {
  background: #242320;
  border-color: #494844;
}

.is-dark .context-empty,
.is-dark .fact-delete-btn,
.is-dark .group-count {
  background: #34332f;
}

.is-dark .fact-row {
  border-color: #494844;
}
</style>
