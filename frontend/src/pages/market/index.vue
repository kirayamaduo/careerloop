<template>
  <SlPage class="app-soft-bg" :custom-class="['market-page', themeClass, fontClass].join(' ')">
    <SlNavBar :title="t('market.title')" show-back @back="goBack" :safe-top="topSafeHeight" />

    <view class="source-tabs">
      <view
        v-for="s in sources"
        :key="s.value"
        :class="['source-tab', sourceFilter === s.value ? 'source-tab-on' : '']"
        @click="setSource(s.value)"
      >
        <text class="source-tab-text">{{ s.label }}</text>
      </view>
    </view>

    <view class="filters app-card-soft app-surface">
      <input
        class="filter-input ui-input"
        v-model="positionFilter"
        :placeholder="t('market.filterPlaceholder')"
        @confirm="applyFilters"
      />
      <view class="diff-row">
        <view
          v-for="d in difficulties"
          :key="d || 'any'"
          :class="['diff-chip', difficultyFilter === d ? 'diff-chip-on' : '']"
          @click="setDifficulty(d)"
        >
          <text class="diff-chip-text">{{ d || t('market.diffAny') }}</text>
        </view>
        <view class="diff-action" @click="applyFilters">
          <text class="diff-action-text">{{ t('market.applyFilter') }}</text>
        </view>
      </view>
    </view>

    <view class="contribute-card app-card-soft app-surface">
      <text class="contribute-title">{{ t('market.shareTitle') }}</text>
      <text class="contribute-sub">{{ t('market.shareSub') }}</text>
      <view class="contribute-meta">
        <input
          class="meta-input ui-input"
          v-model="contributePosition"
          :placeholder="t('market.positionPlaceholder')"
        />
        <view class="meta-picker" @click="showDifficultySheet = true">
          <text class="meta-picker-text">{{ contributeDifficultyLabel }}</text>
          <text class="meta-picker-arrow ri-arrow-down-s-line"></text>
        </view>
      </view>
      <textarea
        class="contribute-input ui-input"
        v-model="contributeContent"
        :maxlength="800"
        :placeholder="t('market.questionPlaceholder')"
      />
      <view class="contribute-actions">
        <button
          class="btn-primary"
          :disabled="contributing || !contributeContent.trim()"
          @click="submitContribution"
        >{{ contributing ? t('market.sharing') : t('market.shareBtn') }}</button>
      </view>
    </view>

    <view class="list-state" v-if="loading">
      <view class="spinner"></view>
      <text class="loading-text">{{ t('market.loading') }}</text>
    </view>

    <view class="empty-state app-empty app-surface" v-else-if="!loading && items.length === 0">
      <text class="empty-icon ri-mail-close-line"></text>
      <text class="empty-text">{{ t('market.empty') }}</text>
      <text class="empty-sub">{{ t('market.emptySub') }}</text>
    </view>

    <view class="list" v-else>
      <view
        class="q-card app-card-soft app-surface"
        v-for="q in items"
        :key="q.id"
      >
        <view class="q-head">
          <text class="q-pos">{{ q.position }}</text>
          <view class="q-badges">
            <text v-if="q.source === 'OFFICIAL'" class="badge badge-official">{{ t('market.badgeOfficial') }}</text>
            <text v-else-if="q.source === 'AI_GENERATED'" class="badge badge-ai">{{ t('market.badgeAI') }}</text>
            <text :class="['q-diff', diffClass(q.difficulty)]">{{ q.difficulty }}</text>
          </view>
        </view>
        <text class="q-content">{{ q.content }}</text>
        <view v-if="q.answer && expandedId === q.id" class="q-answer">
          <text class="q-answer-label">{{ t('market.refAnswer') }}</text>
          <text class="q-answer-text">{{ q.answer }}</text>
        </view>
        <view class="q-foot">
          <view class="like-btn" @click="like(q)">
            <text class="like-icon" :class="likedSet.has(q.id) ? 'ri-heart-fill' : 'ri-heart-line'"></text>
            <text class="like-count">{{ q.likes }}</text>
          </view>
          <text class="q-meta">{{ t('market.drawnCount', { n: q.drawCount }) }}</text>
          <view v-if="q.answer" class="answer-btn" @click="toggleAnswer(q.id)">
            <text class="answer-btn-text">{{ expandedId === q.id ? t('market.hideAnswer') : t('market.showAnswer') }}</text>
          </view>
        </view>
      </view>
    </view>

    <view class="pagination" v-if="items.length > 0 || page > 0">
      <button class="btn-page" :disabled="page === 0 || loading" @click="prevPage">‹ {{ t('market.prev') }}</button>
      <text class="page-meta">{{ page + 1 }} / {{ Math.max(1, Math.ceil(total / size)) }}</text>
      <button class="btn-page" :disabled="(page + 1) * size >= total || loading" @click="nextPage">{{ t('market.next') }} ›</button>
    </view>

    <view class="bottom-safe"></view>
    <SlActionSheet
      v-model:visible="showDifficultySheet"
      title="选择题目难度"
      :options="contributeDifficultySheetOptions"
      :selected-value="contributeDifficulty"
      @select="onPickDifficulty"
    />
  </SlPage>
</template>

<script setup lang="ts">
import { onMounted, ref, computed } from 'vue';
import { useI18n } from '@/locales';
import { onShow } from '@dcloudio/uni-app';
import { getMpSafeAreaMetrics } from '@/utils/safeArea';
import { listMarketApi, likeQuestionApi, contributeQuestionApi, type MarketQuestion } from '@/api/market';
import { useTheme } from '@/utils/theme';
import { requireAuth } from '@/utils/auth';
import SlPage from '@/style-library/components/SlPage.vue';
import SlNavBar from '@/style-library/components/SlNavBar.vue';
import SlActionSheet from '@/style-library/components/SlActionSheet.vue';

const { t } = useI18n();
const { themeClass, fontClass, refresh: refreshTheme } = useTheme();
const topSafeHeight = ref(52);

const positionFilter = ref('');
const difficultyFilter = ref<string>(''); // '' = any
const difficulties = ['', 'Easy', 'Normal', 'Hard'];
const sourceFilter = ref<string>(''); // '' = all sources
const sources = computed(() => [
  { label: t('market.srcAll'), value: '' },
  { label: t('market.srcOfficial'), value: 'OFFICIAL' },
  { label: t('market.srcCommunity'), value: 'USER' },
  { label: t('market.srcAI'), value: 'AI_GENERATED' },
]);
const expandedId = ref<number | null>(null);

const items = ref<MarketQuestion[]>([]);
const total = ref(0);
const page = ref(0);
const size = 20;
const loading = ref(false);
let latestLoadRequestId = 0;

const contributePosition = ref('');
const contributeDifficulty = ref('Normal');
const showDifficultySheet = ref(false);
const contributeContent = ref('');
const contributing = ref(false);
const contributeDifficultyOptions = computed(() => [
  { value: 'Easy', label: t('market.diffEasyLabel') },
  { value: 'Normal', label: t('market.diffNormalLabel') },
  { value: 'Hard', label: t('market.diffHardLabel') },
]);
const contributeDifficultySheetOptions = computed(() => contributeDifficultyOptions.value.map((item) => ({
  label: item.label,
  value: item.value,
  subtitle: item.value === 'Easy' ? '适合热身和基础问题' : item.value === 'Hard' ? '适合压力面和深挖追问' : '适合常规模拟面试',
  icon: item.value === 'Hard' ? 'ri-fire-line' : item.value === 'Easy' ? 'ri-seedling-line' : 'ri-focus-3-line',
})));
const contributeDifficultyLabel = computed(() =>
  contributeDifficultyOptions.value.find((item) => item.value === contributeDifficulty.value)?.label || contributeDifficulty.value
);

const likedSet = ref<Set<number>>(new Set());

const goBack = () => uni.navigateBack({ delta: 1 });

const setDifficulty = (d: string) => {
  difficultyFilter.value = d;
};

const setSource = (s: string) => {
  sourceFilter.value = s;
  page.value = 0;
  load();
};

const toggleAnswer = (id: number) => {
  expandedId.value = expandedId.value === id ? null : id;
};

const onPickDifficulty = ({ value }: { value: string }) => {
  if (contributeDifficultyOptions.value.some((item) => item.value === value)) {
    contributeDifficulty.value = value;
  }
};

const diffClass = (d: string) => {
  const k = (d || '').toLowerCase();
  if (k === 'easy') return 'diff-easy';
  if (k === 'hard') return 'diff-hard';
  return 'diff-normal';
};

const load = async () => {
  const requestId = ++latestLoadRequestId;
  loading.value = true;
  try {
    const res = await listMarketApi({
      position: positionFilter.value.trim() || undefined,
      difficulty: difficultyFilter.value || undefined,
      source: sourceFilter.value || undefined,
      page: page.value,
      size,
    });
    if (requestId !== latestLoadRequestId) return;
    items.value = res?.items || [];
    total.value = res?.total || 0;
  } catch (e: any) {
    if (requestId !== latestLoadRequestId) return;
    items.value = [];
    total.value = 0;
    uni.showToast({ title: e?.message || t('market.loadFailed'), icon: 'none' });
  } finally {
    if (requestId === latestLoadRequestId) loading.value = false;
  }
};

const applyFilters = async () => {
  page.value = 0;
  await load();
};

const prevPage = async () => {
  if (page.value === 0) return;
  page.value -= 1;
  await load();
};

const nextPage = async () => {
  if ((page.value + 1) * size >= total.value) return;
  page.value += 1;
  await load();
};

const like = async (q: MarketQuestion) => {
  if (!requireAuth({ message: '登录后才能点赞题目，并保留你的互动记录。' })) return;
  // Optimistic update — the backend doesn't track per-user votes, but we
  // still keep a per-session set so the heart fills only once until reload.
  if (likedSet.value.has(q.id)) return;
  likedSet.value.add(q.id);
  q.likes += 1;
  try {
    await likeQuestionApi(q.id);
  } catch (e: any) {
    likedSet.value.delete(q.id);
    q.likes = Math.max(0, q.likes - 1);
    uni.showToast({ title: e?.message || t('market.likeFailed'), icon: 'none' });
  }
};

const submitContribution = async () => {
  if (contributing.value) return;
  if (!requireAuth({ message: '登录后才能向题库投稿并查看审核结果。' })) return;
  const trimmed = contributeContent.value.trim();
  if (trimmed.length < 8) {
    uni.showToast({ title: t('market.addMoreWords'), icon: 'none' });
    return;
  }
  contributing.value = true;
  try {
    await contributeQuestionApi({
      position: contributePosition.value.trim() || 'General',
      difficulty: contributeDifficulty.value,
      content: trimmed,
    });
    contributeContent.value = '';
    uni.showToast({ title: t('market.shareSuccess'), icon: 'success' });
    page.value = 0;
    await load();
  } catch (e: any) {
    uni.showToast({ title: e?.message || t('market.shareFailed'), icon: 'none' });
  } finally {
    contributing.value = false;
  }
};

onMounted(() => {
  refreshTheme();
  topSafeHeight.value = getMpSafeAreaMetrics().topSafeHeight;
  // Pre-fill filters / contribute form when launched from /pages/interview/report.
  const pages = getCurrentPages();
  const opts = (pages[pages.length - 1] as any)?.options || {};
  if (opts.position) {
    const p = decodeURIComponent(opts.position);
    positionFilter.value = p;
    contributePosition.value = p;
  }
  if (opts.difficulty) contributeDifficulty.value = decodeURIComponent(opts.difficulty);
});

onShow(() => {
  refreshTheme();
  load();
});
</script>

<style scoped>
.filters {
  border-radius: var(--radius-md, 16px); padding: 14px;
  display: flex; flex-direction: column; gap: 10px;
  margin-top: 8px;
}
.filter-input {
  height: 40px; padding: 0 12px;
  border-radius: var(--radius-sm, 12px);
  font-size: 14px; color: var(--text-primary, #1c1917);
}
.diff-row { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.diff-chip { padding: 6px 14px; background: var(--surface-3, #f5f5f4); border-radius: 999px; }
.diff-chip-text { font-size: 12px; color: var(--text-secondary, #78716c); font-weight: 600; }
.diff-chip-on { background: var(--primary-color, #cd6a43); }
.diff-chip-on .diff-chip-text { color: #fff; }
.diff-action { margin-left: auto; padding: 6px 14px; background: #1c1917; border-radius: 999px; }
.diff-action-text { font-size: 12px; color: #fff; font-weight: 700; }

.contribute-card {
  border-radius: var(--radius-lg, 20px); padding: 16px; margin-top: 14px;
  display: flex; flex-direction: column; gap: 10px;
}
.contribute-title { font-size: 15px; font-weight: 700; color: var(--text-primary, #1c1917); }
.contribute-sub { font-size: 12px; color: var(--text-secondary, #78716c); line-height: 1.45; }
.contribute-meta { display: flex; gap: 8px; }
.meta-input {
  flex: 1; height: 40px; padding: 0 12px;
  border-radius: 10px; background: var(--surface-3, #f5f5f4); font-size: 13px; color: var(--text-primary, #1c1917);
}
.meta-picker {
  display: inline-flex; align-items: center; gap: 6px;
  height: 40px; padding: 0 14px; border-radius: 10px;
  background: var(--surface-3, #f5f5f4); min-width: 96px; justify-content: center;
}
.meta-picker-text { font-size: 13px; color: var(--text-primary, #1c1917); font-weight: 600; }
.meta-picker-arrow { font-size: 12px; color: var(--text-secondary, #78716c); }
.contribute-input {
  width: 100%; min-height: 80px; padding: 10px 12px;
  border-radius: 10px; background: var(--surface-3, #f5f5f4);
  font-size: 14px; line-height: 1.5; color: var(--text-primary, #1c1917);
  box-sizing: border-box;
}
.contribute-actions { display: flex; }
.btn-primary {
  flex: 1; height: 42px; line-height: 42px;
  border-radius: 12px; background: var(--primary-color, #cd6a43); color: #fff;
  font-size: 14px; font-weight: 700; border: none;
}
.btn-primary[disabled] { opacity: 0.55; }

.list-state, .empty-state {
  text-align: center; padding: 36px 16px;
}
.spinner {
  width: 36px; height: 36px; border-radius: 18px; margin: 0 auto 14px;
  border: 3px solid #e7e5e4; border-top-color: var(--primary-color, #cd6a43);
  animation: spin 0.9s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }
.loading-text { font-size: 14px; color: var(--text-secondary, #78716c); }
.empty-icon { font-size: 40px; display: block; margin-bottom: 10px; }
.empty-text { font-size: 14px; color: var(--text-secondary, #78716c); font-weight: 600; display: block; }
.empty-sub { font-size: 12px; color: var(--text-tertiary, #8e8e93); margin-top: 6px; display: block; }

.list { display: flex; flex-direction: column; gap: 12px; margin-top: 18px; }
.q-card {
  border-radius: var(--radius-md, 16px); padding: 14px 16px;
  display: flex; flex-direction: column; gap: 8px;
}
.q-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.q-pos { font-size: 12px; font-weight: 700; color: #cd6a43; text-transform: uppercase; letter-spacing: 0.06em; }
.q-diff {
  font-size: 11px; font-weight: 700; padding: 2px 8px; border-radius: 999px;
  letter-spacing: 0.04em;
}
.diff-easy { background: var(--success-soft, #f5efe3); color: var(--success-color, #735a28); }
.diff-normal { background: var(--primary-soft, #fcf5f2); color: var(--primary-hover, #c25c33); }
.diff-hard { background: var(--danger-soft, #fef2f2); color: var(--danger-color, #ef4444); }
.q-content { font-size: 14px; color: var(--text-primary, #1c1917); line-height: 1.5; }
.q-foot { display: flex; align-items: center; gap: 12px; }
.like-btn { display: inline-flex; align-items: center; gap: 4px; padding: 4px 10px; background: #fef2f2; border-radius: 999px; }
.like-icon { font-size: 14px; color: #ef4444; line-height: 1; }
.like-count { font-size: 12px; color: var(--text-secondary, #78716c); font-weight: 700; }
.q-meta { font-size: 11px; color: var(--text-tertiary, #8e8e93); }

.pagination { display: flex; align-items: center; justify-content: center; gap: 14px; margin-top: 18px; }
.btn-page {
  height: 36px; line-height: 36px; padding: 0 14px;
  border-radius: 999px; background: var(--surface-3, #f5f5f4);
  font-size: 13px; font-weight: 600; color: var(--text-primary, #1c1917); border: none;
}
.btn-page[disabled] { opacity: 0.5; }
.page-meta { font-size: 13px; color: var(--text-secondary, #78716c); font-weight: 600; }

.bottom-safe { height: calc(env(safe-area-inset-bottom, 0px) + 24px); }

.source-tabs { display: flex; gap: 8px; margin: 8px 0; overflow-x: auto; padding-bottom: 2px; }
.source-tab { padding: 6px 14px; border-radius: 999px; background: var(--surface-3, #f5f5f4); white-space: nowrap; }
.source-tab-text { font-size: 12px; font-weight: 600; color: var(--text-secondary, #78716c); }
.source-tab-on { background: #1c1917; }
.source-tab-on .source-tab-text { color: #fff; }

.q-badges { display: flex; align-items: center; gap: 6px; }
.badge { font-size: 10px; font-weight: 700; padding: 2px 6px; border-radius: 999px; }
.badge-official { background: var(--success-soft, #f5efe3); color: var(--success-color, #735a28); }
.badge-ai { background: #fef3c7; color: #b45309; }

.q-answer { margin-top: 8px; padding: 10px; background: #fefbf9; border-radius: 10px; }
.q-answer-label { font-size: 11px; font-weight: 700; color: #cd6a43; display: block; margin-bottom: 4px; }
.q-answer-text { font-size: 13px; color: #44403c; line-height: 1.6; }

.answer-btn { margin-left: auto; padding: 3px 10px; background: var(--primary-soft, #fcf5f2); border-radius: 999px; }
.answer-btn-text { font-size: 11px; font-weight: 700; color: #cd6a43; }

.is-dark .source-tab { background: #292524; }
.is-dark .source-tab-text { color: var(--text-tertiary, #8e8e93); }
.is-dark .source-tab-on { background: #44403c; }
.is-dark .source-tab-on .source-tab-text { color: #fafaf9; }
.is-dark .q-answer { background: #292524; }
.is-dark .q-answer-text { color: #d6d3d1; }
.is-dark .answer-btn { background: rgba(205, 106, 67,0.18); }
.is-dark .answer-btn-text { color: #e8baa8; }

/* Dark mode */
.is-dark .contribute-title,
.is-dark .q-content { color: #fafaf9; }
.is-dark .filters,
.is-dark .contribute-card,
.is-dark .q-card { background: #292524; box-shadow: none; border-color: #44403c; }
.is-dark .filter-input,
.is-dark .meta-input,
.is-dark .meta-picker,
.is-dark .contribute-input { background: #1c1917; color: #fafaf9; }
.is-dark .meta-picker-text { color: #fafaf9; }
.is-dark .diff-chip { background: #1c1917; }
.is-dark .diff-chip-text { color: #f2d8ce; }
.is-dark .like-btn { background: rgba(239, 68, 68, 0.18); }
.is-dark .like-count { color: #f2d8ce; }
.is-dark .q-meta { color: var(--text-tertiary, #8e8e93); }
.is-dark .btn-page { background: #1c1917; color: #f2d8ce; }

/* Competition visual system ------------------------------------------------ */
.filters,
.contribute-card,
.q-card,
.empty-state {
  background: #ffffff;
  border: 1px solid #e0dfdb;
  border-radius: 8px;
  box-shadow: 0 6px 18px rgba(44, 43, 41, 0.04);
}

.filter-input,
.meta-input,
.meta-picker,
.contribute-input {
  color: #2c2b29;
  background: #faf9f6;
  border: 1px solid #d8d7d2;
  border-radius: 6px;
}

.diff-chip,
.source-tab,
.btn-page,
.answer-btn,
.like-btn {
  background: #f5f5f0;
  border: 1px solid #e0dfdb;
  border-radius: 4px;
}

.diff-chip-on,
.source-tab-on {
  background: #ac6448;
  border-color: #ac6448;
}

.diff-action {
  background: #c23b22;
  border-radius: 4px;
}

.contribute-title {
  color: #2c2b29;
  font-family: "Songti SC", STSong, serif;
  font-weight: 600;
  letter-spacing: 0.05em;
}

.contribute-sub,
.q-meta,
.page-meta,
.loading-text,
.empty-sub {
  color: #5a5956;
}

.btn-primary {
  color: #faf9f6;
  background: #c23b22;
  border-radius: 6px;
}

.spinner {
  border-color: #e0dfdb;
  border-top-color: #ac6448;
}

.q-pos,
.q-answer-label,
.answer-btn-text {
  color: #ac6448;
}

.q-content {
  color: #2c2b29;
  font-family: "Songti SC", STSong, serif;
  font-size: 15px;
}

.q-diff,
.badge {
  background: transparent;
  border-radius: 3px;
}

.diff-easy,
.badge-official {
  color: #947f54;
  border: 1px solid rgba(148, 127, 84, 0.4);
}

.diff-normal {
  color: #ac6448;
  border: 1px solid rgba(172, 100, 72, 0.36);
}

.diff-hard {
  color: #c23b22;
  border: 1px solid rgba(194, 59, 34, 0.36);
}

.badge-ai {
  color: #987632;
  border: 1px solid rgba(184, 151, 90, 0.42);
}

.like-btn {
  color: #c23b22;
  background: rgba(194, 59, 34, 0.05);
  border-color: rgba(194, 59, 34, 0.22);
}

.like-icon {
  color: #c23b22;
}

.q-answer {
  background: #f5f5f0;
  border-left: 2px solid #ac6448;
  border-radius: 2px;
}

.q-answer-text {
  color: #5a5956;
}

.is-dark .filters,
.is-dark .contribute-card,
.is-dark .q-card,
.is-dark .empty-state {
  background: #242320;
  border-color: #494844;
}

.is-dark .filter-input,
.is-dark .meta-input,
.is-dark .meta-picker,
.is-dark .contribute-input {
  color: #faf9f6;
  background: #1c1b19;
  border-color: #494844;
}

.is-dark .contribute-title,
.is-dark .q-content {
  color: #faf9f6;
}

.is-dark .contribute-sub,
.is-dark .q-meta,
.is-dark .page-meta,
.is-dark .q-answer-text {
  color: #c6c4be;
}

.is-dark .diff-chip:not(.diff-chip-on),
.is-dark .source-tab:not(.source-tab-on),
.is-dark .btn-page,
.is-dark .answer-btn {
  background: #34332f;
  border-color: #494844;
}

.is-dark .q-answer {
  background: #1c1b19;
}
</style>
