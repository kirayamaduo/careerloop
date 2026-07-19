<template>
  <SlPage class="cdut-page app-soft-bg" :custom-class="[themeClass, fontClass].join(' ')">
    <SlNavBar :title="pageTitle" show-back @back="goBack" :safe-top="topSafeHeight" :right-avoid-width="rightAvoidWidth" />

    <view class="content">
      <view class="hero app-card-gradient">
        <text class="hero-kicker">{{ insight?.school || '就业数据' }}</text>
        <text class="hero-title">{{ pageTitle }}</text>
        <text class="hero-sub">{{ pageSubtitle }}</text>
      </view>

      <view v-if="loading" class="skeleton-list">
        <view class="skel-card app-surface" v-for="i in 3" :key="i">
          <view class="skel-line skel-w70"></view>
          <view class="skel-line skel-w40"></view>
          <view class="skel-line skel-w90"></view>
        </view>
      </view>

      <view v-else-if="insight">
        <view class="summary-card app-card-soft app-surface">
          <view class="summary-head">
            <view>
              <text class="summary-label">{{ insight.matchLabel }}</text>
              <text class="summary-title">{{ insight.major }} · {{ insight.targetRole }}</text>
            </view>
            <view class="refresh-btn" @click="refresh">
              <text class="refresh-icon ri-refresh-line"></text>
            </view>
          </view>
          <text class="summary-text">{{ insight.summary }}</text>
          <view class="metric-row">
            <view class="metric" v-if="insight.latestEmploymentRate !== undefined && insight.latestEmploymentRate !== null">
              <text class="metric-value">{{ formatPercent(insight.latestEmploymentRate) }}</text>
              <text class="metric-label">{{ t('cdut.employmentRate') }}</text>
            </view>
            <view class="metric" v-if="insight.latestPostgraduateRate !== undefined && insight.latestPostgraduateRate !== null">
              <text class="metric-value">{{ formatPercent(insight.latestPostgraduateRate) }}</text>
              <text class="metric-label">{{ t('cdut.postgraduateRate') }}</text>
            </view>
            <view class="metric">
              <text class="metric-value">{{ insight.sourceCount || 0 }}</text>
              <text class="metric-label">{{ sourceMetricLabel }}</text>
            </view>
          </view>
          <text class="updated" v-if="insight.updatedAt">{{ t('cdut.updatedAt') }} {{ formatDate(insight.updatedAt) }}</text>
        </view>

        <view class="section" v-if="coverageStats.total">
          <view class="section-head-row">
            <text class="section-title">{{ coverageTitle }}</text>
            <text class="section-action">{{ coverageAction }}</text>
          </view>
          <view class="coverage-card app-card-soft app-surface">
            <text class="coverage-note">{{ coverageNote }}</text>
            <view class="coverage-grid">
              <view class="coverage-item" v-for="item in visibleCoverage" :key="item.school + item.year" :class="'coverage-' + item.status.toLowerCase()">
                <text class="coverage-school">{{ item.school }}</text>
                <text class="coverage-year">{{ item.year }}</text>
                <text class="coverage-label">{{ item.label }}</text>
              </view>
            </view>
          </view>
        </view>

        <view class="section" v-if="hasVisualData">
          <view class="section-head-row">
            <text class="section-title">数据可视化</text>
            <text class="section-action" @click="openDetail('trend')">展开</text>
          </view>
          <view class="visual-card app-card-soft app-surface">
            <view class="visual-top">
              <view class="visual-stat" v-if="insight.latestEmploymentRate !== undefined && insight.latestEmploymentRate !== null">
                <text class="visual-stat-value">{{ formatPercent(insight.latestEmploymentRate) }}</text>
                <text class="visual-stat-label">最新就业/落实率</text>
              </view>
              <view class="visual-stat" v-if="insight.latestPostgraduateRate !== undefined && insight.latestPostgraduateRate !== null">
                <text class="visual-stat-value postgrad-text">{{ formatPercent(insight.latestPostgraduateRate) }}</text>
                <text class="visual-stat-label">最新升学/深造率</text>
              </view>
              <view class="visual-stat">
                <text class="visual-stat-value source-text">{{ insight.sourceCount || 0 }}</text>
                <text class="visual-stat-label">{{ sourceMetricLabel }}</text>
              </view>
            </view>

            <view v-if="sourceYearBars.length" class="bar-chart-block">
              <view class="chart-subhead">
                <text class="chart-subtitle">{{ sourceYearTitle }}</text>
                <text class="chart-submeta">{{ sourceYearMeta }}</text>
              </view>
              <view class="source-year-chart">
                <view class="year-bar-item" v-for="bar in sourceYearBars" :key="bar.key">
                  <view class="year-bar-track">
                    <view class="year-bar-fill" :style="{ height: bar.height }"></view>
                  </view>
                  <text class="year-bar-count">{{ bar.count }}</text>
                  <text class="year-bar-label">{{ bar.label }}</text>
                </view>
              </view>
            </view>

            <view v-if="sourceTypeBars.length" class="source-type-block">
              <view class="chart-subhead">
                <text class="chart-subtitle">{{ isDemoMode ? '演示材料类型' : '来源类型分布' }}</text>
              </view>
              <view class="source-type-row" v-for="bar in sourceTypeBars" :key="bar.key">
                <text class="source-type-label">{{ bar.label }}</text>
                <view class="source-type-track">
                  <view class="source-type-fill" :style="{ width: bar.width }"></view>
                </view>
                <text class="source-type-count">{{ bar.count }}</text>
              </view>
            </view>
          </view>
        </view>

        <view class="section" v-if="trimmedHighlights.length">
          <text class="section-title">{{ t('cdut.destinationTitle') }}</text>
          <view class="highlight-list">
            <view class="highlight-item app-card-soft" v-for="(item, idx) in trimmedHighlights" :key="item">
              <text class="highlight-index">{{ idx + 1 }}</text>
              <text class="highlight-text">{{ item }}</text>
            </view>
          </view>
        </view>

        <view class="section" v-if="insight.trend?.length">
          <text class="section-title">{{ t('cdut.trendTitle') }}</text>
          <view class="chart-card app-card-soft app-surface">
            <view class="chart-legend">
              <view class="legend-item"><view class="legend-dot legend-employment"></view><text class="legend-text">就业率</text></view>
              <view class="legend-item" v-if="chartSeries.postgrad.points.length"><view class="legend-dot legend-postgrad"></view><text class="legend-text">升学率</text></view>
            </view>
            <view class="line-chart">
              <view class="chart-grid" v-for="i in 4" :key="'grid' + i" :style="{ top: (i * 25) + '%' }"></view>
              <view
                class="chart-line line-employment"
                v-for="seg in chartSeries.employment.segments"
                :key="'e' + seg.key"
                :style="segmentStyle(seg)"
              ></view>
              <view
                class="chart-line line-postgrad"
                v-for="seg in chartSeries.postgrad.segments"
                :key="'p' + seg.key"
                :style="segmentStyle(seg)"
              ></view>
              <view
                class="chart-point point-employment"
                v-for="point in chartSeries.employment.points"
                :key="'ep' + point.year"
                :style="{ left: point.x + '%', top: point.y + '%' }"
              >
                <text class="point-label">{{ formatPercent(point.value) }}</text>
              </view>
              <view
                class="chart-point point-postgrad"
                v-for="point in chartSeries.postgrad.points"
                :key="'pp' + point.year"
                :style="{ left: point.x + '%', top: point.y + '%' }"
              ></view>
            </view>
            <view class="chart-years">
              <text class="chart-year" v-for="year in chartYears" :key="year">{{ year }}</text>
            </view>
          </view>
          <view class="trend-card app-card-soft app-surface">
            <view class="trend-row" v-for="p in insight.trend" :key="p.year">
              <text class="trend-year">{{ p.year }}</text>
              <view class="trend-stack">
                <view class="trend-main">
                  <text class="trend-label">就业</text>
                  <view class="trend-bar">
                    <view class="trend-fill employment" :style="{ width: percentWidth(p.employmentRate) }"></view>
                  </view>
                  <text class="trend-value">{{ formatPercent(p.employmentRate) }}</text>
                </view>
                <view class="trend-main" v-if="p.postgraduateRate !== undefined && p.postgraduateRate !== null">
                  <text class="trend-label">升学</text>
                  <view class="trend-bar">
                    <view class="trend-fill postgrad" :style="{ width: percentWidth(p.postgraduateRate) }"></view>
                  </view>
                  <text class="trend-value">{{ formatPercent(p.postgraduateRate) }}</text>
                </view>
              </view>
            </view>
          </view>
        </view>

        <view class="section">
          <text class="section-title">查看更多</text>
          <view class="detail-grid">
            <view class="detail-entry app-card-soft app-surface" @click="openDetail('trend')">
              <text class="detail-icon ri-line-chart-line"></text>
              <text class="detail-title">{{ isDemoMode ? '演示趋势' : '已验证趋势' }}</text>
              <text class="detail-desc">{{ isDemoMode ? '查看 2021-2025 年就业与升学变化' : '仅展示已从公开来源识别到的历年变化' }}</text>
            </view>
            <view class="detail-entry app-card-soft app-surface" @click="openDetail('sources')">
              <text class="detail-icon ri-links-line"></text>
              <text class="detail-title">{{ isDemoMode ? '演示材料' : '公开来源' }}</text>
              <text class="detail-desc">{{ isDemoMode ? '查看 5 条年份报告与去向摘要' : '查看报告来源、年份和原链接' }}</text>
            </view>
            <view class="detail-entry app-card-soft app-surface" @click="openDetail('method')">
              <text class="detail-icon ri-information-line"></text>
              <text class="detail-title">数据口径</text>
              <text class="detail-desc">{{ isDemoMode ? '了解演示数据的组织方式与说明' : '了解抓取、匹配和未接入说明' }}</text>
            </view>
          </view>
        </view>
      </view>

      <view v-else class="empty-state app-empty app-surface">
        <text class="empty-icon ri-school-line"></text>
        <text class="empty-text">{{ t('cdut.empty') }}</text>
        <button class="btn-retry" @click="load">{{ t('common.retry') }}</button>
      </view>

      <view class="bottom-safe"></view>
    </view>
  </SlPage>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app';
import { useI18n } from '@/locales';
import { getMpSafeAreaMetrics } from '@/utils/safeArea';
import { useTheme } from '@/utils/theme';
import SlPage from '@/style-library/components/SlPage.vue';
import SlNavBar from '@/style-library/components/SlNavBar.vue';
import {
  getCdutEmploymentInsightApi,
  refreshCdutEmploymentInsightApi,
  type CdutEmploymentInsight,
} from '@/api/cdutEmployment';

const { t } = useI18n();
const { themeClass, fontClass, refresh: refreshTheme } = useTheme();
const topSafeHeight = ref(52);
const rightAvoidWidth = ref(20);
const loading = ref(true);
const insight = ref<CdutEmploymentInsight | null>(null);

const isDemoMode = computed(() => Boolean(insight.value?.demoMode));
const pageTitle = computed(() => {
  if (isDemoMode.value) return '就业数据演示';
  if (!insight.value) return '就业数据洞察';
  return t('cdut.pageTitle');
});
const pageSubtitle = computed(() =>
  isDemoMode.value
    ? '当前为答辩脱敏演示数据，展示完整、稳定的就业信息样例。'
    : insight.value
      ? t('cdut.pageSubtitle')
      : '正在整理就业数据...'
);
const sourceMetricLabel = computed(() => isDemoMode.value ? '演示材料' : t('cdut.publicSources'));
const coverageTitle = computed(() => isDemoMode.value ? '近 5 年演示覆盖' : '近 5 年覆盖审计');
const coverageNote = computed(() =>
  isDemoMode.value
    ? '近 5 年演示材料均已补齐，覆盖趋势、摘要与演示完整状态。'
    : '只把官方报告或官方信息公开页中可抽取核心字段的年份标为完整；其他年份会保留缺失或待核验状态。'
);
const sourceYearTitle = computed(() => isDemoMode.value ? '按年份演示材料' : '按年份抓取来源');
const sourceYearMeta = computed(() => isDemoMode.value ? '柱高代表该年份演示材料数量' : '柱高代表该年份来源数量');

const trimmedHighlights = computed(() =>
  (insight.value?.destinationHighlights || [])
    .map((item) => item.length > 58 ? `${item.slice(0, 58)}...` : item)
    .slice(0, 3)
);

type ChartPoint = { year: number; value: number; x: number; y: number };
type ChartSegment = { key: string; x: number; y: number; width: number; height: number; direction: string };
type SourceBar = { key: string; label: string; count: number; height?: string; width?: string };

const hasVisualData = computed(() =>
  !!insight.value && (
    (insight.value.trend || []).length > 0 ||
    (insight.value.sources || []).length > 0 ||
    (insight.value.latestEmploymentRate !== undefined && insight.value.latestEmploymentRate !== null) ||
    (insight.value.latestPostgraduateRate !== undefined && insight.value.latestPostgraduateRate !== null)
  )
);

const coverageStats = computed(() => {
  const rows = insight.value?.coverage || [];
  return {
    total: rows.length,
    verified: rows.filter((item) => item.status === 'VERIFIED_FULL').length,
    partial: rows.filter((item) => item.status === 'PARTIAL').length,
    missing: rows.filter((item) => item.status === 'MISSING').length,
    review: rows.filter((item) => item.status === 'NEEDS_MANUAL_REVIEW').length,
  };
});

const coverageAction = computed(() =>
  `${coverageStats.value.verified}/${coverageStats.value.total} ${isDemoMode.value ? '演示完整' : '已验证完整'}`
);

const visibleCoverage = computed(() => {
  const rows = insight.value?.coverage || [];
  const school = insight.value?.school;
  const ownSchool = rows.filter((item) => item.school === school);
  const needsAttention = rows.filter((item) => item.status !== 'VERIFIED_FULL').slice(0, 8);
  return (ownSchool.length ? ownSchool : needsAttention).slice(0, 10);
});

const chartYears = computed(() =>
  (insight.value?.trend || [])
    .map((p) => p.year)
    .filter((year): year is number => year !== undefined && year !== null)
);

const toChartPoints = (field: 'employmentRate' | 'postgraduateRate'): ChartPoint[] => {
  const trend = insight.value?.trend || [];
  const rows = trend
    .filter((p) => p.year !== undefined && p.year !== null && p[field] !== undefined && p[field] !== null)
    .map((p) => ({ year: Number(p.year), value: Number(p[field]) }));
  const last = Math.max(1, rows.length - 1);
  return rows.map((p, index) => ({
    ...p,
    x: rows.length === 1 ? 50 : (index / last) * 100,
    y: 100 - Math.max(0, Math.min(100, p.value)),
  }));
};

const toSegments = (points: ChartPoint[]): ChartSegment[] =>
  points.slice(1).map((p, index) => {
    const prev = points[index];
    const dx = p.x - prev.x;
    const dy = p.y - prev.y;
    return {
      key: `${prev.year}-${p.year}`,
      x: Math.min(prev.x, p.x),
      y: Math.min(prev.y, p.y),
      width: Math.abs(dx),
      height: Math.max(1.2, Math.abs(dy)),
      direction: dy >= 0 ? 'to bottom right' : 'to top right',
    };
  });

const chartSeries = computed(() => {
  const employment = toChartPoints('employmentRate');
  const postgrad = toChartPoints('postgraduateRate');
  return {
    employment: { points: employment, segments: toSegments(employment) },
    postgrad: { points: postgrad, segments: toSegments(postgrad) },
  };
});

const sourceYearBars = computed<SourceBar[]>(() => {
  const counts = new Map<number, number>();
  for (const source of insight.value?.sources || []) {
    if (source.year === undefined || source.year === null) continue;
    const year = Number(source.year);
    counts.set(year, (counts.get(year) || 0) + 1);
  }
  const max = Math.max(1, ...Array.from(counts.values()));
  return Array.from(counts.entries())
    .sort(([a], [b]) => a - b)
    .map(([year, count]) => ({
      key: String(year),
      label: String(year),
      count,
      height: `${Math.max(18, Math.round((count / max) * 100))}%`,
    }));
});

const sourceTypeBars = computed<SourceBar[]>(() => {
  const counts = new Map<string, number>();
  for (const source of insight.value?.sources || []) {
    const type = source.sourceType || '公开来源';
    counts.set(type, (counts.get(type) || 0) + 1);
  }
  const max = Math.max(1, ...Array.from(counts.values()));
  return Array.from(counts.entries())
    .sort((a, b) => b[1] - a[1])
    .slice(0, 4)
    .map(([type, count]) => ({
      key: type,
      label: sourceTypeLabel(type),
      count,
      width: `${Math.max(14, Math.round((count / max) * 100))}%`,
    }));
});

const segmentStyle = (seg: ChartSegment) => ({
  left: `${seg.x}%`,
  top: `${seg.y}%`,
  width: `${seg.width}%`,
  height: `${seg.height}%`,
  '--line-direction': seg.direction,
});

const load = async () => {
  loading.value = true;
  try {
    insight.value = await getCdutEmploymentInsightApi();
  } catch {
    insight.value = null;
  } finally {
    loading.value = false;
  }
};

const refresh = async () => {
  loading.value = true;
  try {
    insight.value = await refreshCdutEmploymentInsightApi();
    uni.showToast({ title: t('common.refreshed'), icon: 'success' });
  } catch (e: any) {
    uni.showToast({ title: e?.message || t('common.refreshFailed'), icon: 'none' });
  } finally {
    loading.value = false;
  }
};

const formatPercent = (n?: number) => {
  if (n === undefined || n === null || isNaN(Number(n))) return '-';
  const value = Number(n);
  return `${Number.isInteger(value) ? value.toFixed(0) : value.toFixed(1)}%`;
};

const percentWidth = (n?: number) => `${Math.max(0, Math.min(100, Number(n || 0)))}%`;

const sourceTypeLabel = (type: string) => {
  if (!type) return '公开来源';
  if (type.includes('DEMO')) return '演示材料';
  if (type.includes('PDF')) return '官方报告';
  if (type.includes('OFFICIAL')) return '官方页面';
  if (type.includes('SUMMARY')) return '公开汇总';
  return type.replace(/_/g, ' ');
};

const formatDate = (raw: string) => {
  const d = new Date(raw.replace(' ', 'T'));
  if (isNaN(d.getTime())) return raw;
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};

const openDetail = (section: 'trend' | 'sources' | 'method') => {
  uni.navigateTo({ url: `/pages/cdut-employment/detail?section=${section}` });
};

const goBack = () => {
  uni.navigateBack({ delta: 1 });
};

onMounted(() => {
  refreshTheme();
  const safeMetrics = getMpSafeAreaMetrics();
  topSafeHeight.value = safeMetrics.topSafeHeight;
  rightAvoidWidth.value = safeMetrics.rightAvoidWidth;
  load();
});

onShow(() => {
  refreshTheme();
});

onPullDownRefresh(async () => {
  try {
    await refresh();
  } finally {
    uni.stopPullDownRefresh();
  }
});
</script>

<style scoped>
.cdut-page { min-height: 100vh; }
.content { padding: 16px var(--page-gutter, 20px) 0; box-sizing: border-box; }
.hero {
  border-radius: var(--radius-lg, 20px);
  padding: 22px 20px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.hero-kicker { font-size: 11px; font-weight: 900; letter-spacing: 0.12em; color: rgba(255,255,255,0.78); }
.hero-title { font-size: 24px; line-height: 1.16; font-weight: 900; color: #ffffff; }
.hero-sub { font-size: 13px; line-height: 1.5; color: rgba(255,255,255,0.86); }
.skeleton-list { display: flex; flex-direction: column; gap: 12px; margin-top: 18px; }
.skel-card { border-radius: var(--radius-md, 16px); padding: 18px; }
.skel-line { height: 12px; border-radius: 999px; background: var(--surface-3, #f1f5f9); margin-bottom: 10px; }
.skel-w70 { width: 70%; }
.skel-w40 { width: 40%; }
.skel-w90 { width: 90%; }
.summary-card { margin-top: 16px; padding: 16px; }
.summary-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.summary-label {
  display: inline-flex; padding: 4px 8px; border-radius: 999px;
  background: var(--primary-soft, #eff6ff); color: var(--primary-color, #2563eb);
  font-size: 11px; font-weight: 900;
}
.summary-title { display: block; margin-top: 8px; font-size: 18px; line-height: 1.32; font-weight: 900; color: var(--text-primary, #0f172a); }
.refresh-btn {
  width: 34px; height: 34px; border-radius: 12px; display: flex; align-items: center; justify-content: center;
  background: var(--surface-2, #f8fafc); border: 1px solid var(--border-color, #e2e8f0);
}
.refresh-icon { color: var(--primary-color, #2563eb); font-size: 17px; }
.summary-text { display: block; margin-top: 12px; font-size: 13px; line-height: 1.55; color: var(--text-secondary, #64748b); }
.metric-row { display: flex; gap: 8px; margin-top: 14px; }
.metric {
  flex: 1; min-width: 0; padding: 12px 8px; border-radius: 14px;
  background: var(--surface-2, #f8fafc); border: 1px solid var(--border-color, #e2e8f0);
}
.metric-value { display: block; font-size: 18px; line-height: 1.1; font-weight: 900; color: var(--text-primary, #0f172a); }
.metric-label { display: block; margin-top: 5px; font-size: 10.5px; color: var(--text-tertiary, #8e8e93); }
.updated { display: block; margin-top: 12px; font-size: 11px; color: var(--text-tertiary, #8e8e93); }
.section { margin-top: 22px; }
.section-title { display: block; font-size: 17px; font-weight: 900; color: var(--text-primary, #0f172a); margin-bottom: 12px; }
.section-head-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
.section-head-row .section-title { margin-bottom: 0; }
.section-action { font-size: 12px; font-weight: 900; color: var(--primary-color, #2563eb); }
.visual-card { padding: 14px; }
.coverage-card { padding: 14px; }
.coverage-note {
  display: block;
  font-size: 12px;
  line-height: 1.5;
  color: var(--text-secondary, #64748b);
  margin-bottom: 12px;
}
.coverage-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}
.coverage-item {
  min-width: 0;
  border-radius: 12px;
  padding: 10px;
  border: 1px solid var(--border-color, #e2e8f0);
  background: var(--surface-2, #f8fafc);
  display: grid;
  grid-template-columns: 1fr auto;
  row-gap: 4px;
  column-gap: 6px;
}
.coverage-school {
  min-width: 0;
  font-size: 12px;
  font-weight: 900;
  color: var(--text-primary, #0f172a);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.coverage-year {
  font-size: 11px;
  font-weight: 900;
  color: var(--text-tertiary, #8e8e93);
}
.coverage-label {
  grid-column: span 2;
  font-size: 11px;
  font-weight: 900;
}
.coverage-verified_full .coverage-label { color: #0f766e; }
.coverage-partial .coverage-label { color: #2563eb; }
.coverage-missing .coverage-label { color: #ef4444; }
.coverage-needs_manual_review .coverage-label { color: #f59e0b; }
.visual-top { display: flex; gap: 8px; margin-bottom: 16px; }
.visual-stat {
  flex: 1;
  min-width: 0;
  border-radius: 14px;
  padding: 12px 8px;
  background: var(--surface-2, #f8fafc);
  border: 1px solid var(--border-color, #e2e8f0);
}
.visual-stat-value {
  display: block;
  font-size: 18px;
  line-height: 1.05;
  font-weight: 900;
  color: var(--primary-color, #2563eb);
}
.postgrad-text { color: #0f766e; }
.source-text { color: #7c3aed; }
.visual-stat-label {
  display: block;
  margin-top: 6px;
  font-size: 10px;
  line-height: 1.25;
  color: var(--text-tertiary, #8e8e93);
}
.bar-chart-block { margin-top: 4px; }
.chart-subhead { display: flex; align-items: baseline; justify-content: space-between; gap: 10px; margin-bottom: 10px; }
.chart-subtitle { font-size: 13px; font-weight: 900; color: var(--text-primary, #0f172a); }
.chart-submeta { font-size: 10px; color: var(--text-tertiary, #8e8e93); }
.source-year-chart {
  height: 132px;
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 8px 4px 0;
  border-radius: 14px;
  background: linear-gradient(180deg, rgba(37,99,235,0.05), rgba(15,118,110,0.04));
}
.year-bar-item {
  flex: 1;
  min-width: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
}
.year-bar-track {
  flex: 1;
  width: 100%;
  max-width: 34px;
  display: flex;
  align-items: flex-end;
  justify-content: center;
}
.year-bar-fill {
  width: 100%;
  min-height: 16px;
  border-radius: 10px 10px 4px 4px;
  background: linear-gradient(180deg, #3b82f6, #0f766e);
  box-shadow: 0 8px 16px rgba(37, 99, 235, 0.18);
}
.year-bar-count {
  margin-top: 5px;
  font-size: 11px;
  font-weight: 900;
  color: var(--text-primary, #0f172a);
}
.year-bar-label {
  margin-top: 2px;
  font-size: 10px;
  color: var(--text-tertiary, #8e8e93);
}
.source-type-block { margin-top: 16px; }
.source-type-row { display: flex; align-items: center; gap: 8px; margin-top: 9px; }
.source-type-label {
  width: 72px;
  font-size: 11px;
  font-weight: 800;
  color: var(--text-secondary, #64748b);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.source-type-track {
  flex: 1;
  height: 9px;
  border-radius: 999px;
  background: var(--surface-3, #f1f5f9);
  overflow: hidden;
}
.source-type-fill {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, #7c3aed, #38bdf8);
}
.source-type-count {
  width: 18px;
  text-align: right;
  font-size: 11px;
  font-weight: 900;
  color: var(--text-primary, #0f172a);
}
.highlight-list, .source-list { display: flex; flex-direction: column; gap: 10px; }
.highlight-item { display: flex; gap: 10px; padding: 13px; align-items: flex-start; }
.highlight-index {
  width: 24px; height: 24px; border-radius: 12px; background: var(--primary-soft, #eff6ff);
  color: var(--primary-color, #2563eb); font-size: 12px; font-weight: 900; text-align: center; line-height: 24px;
  flex-shrink: 0;
}
.highlight-text { flex: 1; font-size: 13px; line-height: 1.5; color: var(--text-secondary, #64748b); }
.trend-card { padding: 14px; display: flex; flex-direction: column; gap: 12px; }
.chart-card { padding: 14px; margin-bottom: 10px; }
.chart-legend { display: flex; align-items: center; gap: 14px; margin-bottom: 12px; }
.legend-item { display: flex; align-items: center; gap: 5px; }
.legend-dot { width: 8px; height: 8px; border-radius: 50%; }
.legend-employment { background: #2563eb; }
.legend-postgrad { background: #0f766e; }
.legend-text { font-size: 11px; font-weight: 800; color: var(--text-secondary, #64748b); }
.line-chart {
  position: relative;
  height: 156px;
  margin: 2px 8px 0;
  border-left: 1px solid var(--border-color, #e2e8f0);
  border-bottom: 1px solid var(--border-color, #e2e8f0);
}
.chart-grid {
  position: absolute;
  left: 0;
  right: 0;
  height: 1px;
  background: var(--surface-3, #f1f5f9);
}
.chart-line {
  position: absolute;
  min-height: 2px;
  background: linear-gradient(
    var(--line-direction, to bottom right),
    transparent calc(50% - 1px),
    var(--line-color, #2563eb) calc(50% - 1px),
    var(--line-color, #2563eb) calc(50% + 1px),
    transparent calc(50% + 1px)
  );
  z-index: 1;
}
.line-employment { --line-color: #2563eb; }
.line-postgrad { --line-color: #0f766e; opacity: 0.9; }
.chart-point {
  position: absolute;
  width: 9px;
  height: 9px;
  border-radius: 50%;
  transform: translate(-50%, -50%);
  border: 2px solid #ffffff;
  box-shadow: 0 2px 6px rgba(15, 23, 42, 0.18);
  z-index: 2;
}
.point-employment { background: #2563eb; }
.point-postgrad { background: #0f766e; }
.point-label {
  position: absolute;
  left: 50%;
  bottom: 10px;
  transform: translateX(-50%);
  font-size: 10px;
  font-weight: 900;
  color: var(--primary-color, #2563eb);
  white-space: nowrap;
}
.chart-years { display: flex; justify-content: space-between; gap: 4px; padding: 7px 4px 0 12px; }
.chart-year { font-size: 10px; color: var(--text-tertiary, #8e8e93); }
.trend-row { display: flex; align-items: flex-start; gap: 10px; }
.trend-year { width: 44px; font-size: 12px; font-weight: 900; color: var(--text-primary, #0f172a); }
.trend-stack { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 7px; }
.trend-main { flex: 1; display: flex; align-items: center; gap: 8px; }
.trend-label { width: 28px; font-size: 11px; font-weight: 800; color: var(--text-tertiary, #8e8e93); }
.trend-bar { flex: 1; height: 8px; background: var(--surface-3, #f1f5f9); border-radius: 999px; overflow: hidden; }
.trend-fill { height: 100%; border-radius: 999px; }
.trend-fill.employment { background: linear-gradient(90deg, #2563eb, #38bdf8); }
.trend-fill.postgrad { background: linear-gradient(90deg, #0f766e, #84cc16); }
.trend-value { width: 48px; text-align: right; font-size: 12px; font-weight: 800; color: var(--text-secondary, #64748b); }
.detail-grid { display: grid; grid-template-columns: 1fr; gap: 10px; }
.detail-entry {
  padding: 14px;
  display: grid;
  grid-template-columns: 36px 1fr;
  column-gap: 10px;
  row-gap: 3px;
  align-items: center;
}
.detail-icon {
  grid-row: span 2;
  width: 36px;
  height: 36px;
  border-radius: 12px;
  background: var(--primary-soft, #eff6ff);
  color: var(--primary-color, #2563eb);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
}
.detail-title {
  font-size: 14px;
  font-weight: 900;
  color: var(--text-primary, #0f172a);
}
.detail-desc {
  font-size: 12px;
  line-height: 1.4;
  color: var(--text-secondary, #64748b);
}
.source-card { padding: 14px; }
.source-head { display: flex; justify-content: space-between; gap: 10px; }
.source-title { flex: 1; font-size: 14px; line-height: 1.4; font-weight: 900; color: var(--text-primary, #0f172a); }
.source-year { font-size: 11px; color: var(--text-tertiary, #8e8e93); }
.source-tags { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px; }
.source-tag { border-radius: 999px; padding: 3px 8px; background: var(--surface-2, #f8fafc); color: var(--text-secondary, #64748b); font-size: 10px; font-weight: 800; }
.source-excerpt { display: block; margin-top: 9px; font-size: 12px; line-height: 1.5; color: var(--text-secondary, #64748b); }
.source-link { display: block; margin-top: 10px; font-size: 12px; font-weight: 900; color: var(--primary-color, #2563eb); }
.note { margin-top: 20px; padding: 14px; }
.note-title { display: block; font-size: 13px; font-weight: 900; color: var(--text-primary, #0f172a); }
.note-text { display: block; margin-top: 6px; font-size: 12px; line-height: 1.5; color: var(--text-secondary, #64748b); }
.method-card { margin-top: 12px; padding: 14px; }
.method-row { display: flex; align-items: flex-start; gap: 10px; margin-top: 10px; }
.method-step {
  width: 22px; height: 22px; border-radius: 11px; line-height: 22px; text-align: center;
  background: var(--primary-soft, #eff6ff); color: var(--primary-color, #2563eb);
  font-size: 11px; font-weight: 900; flex-shrink: 0;
}
.method-text { flex: 1; font-size: 12px; line-height: 1.5; color: var(--text-secondary, #64748b); }
.empty-state { margin-top: 18px; padding: 44px 20px; text-align: center; }
.empty-icon { font-size: 42px; display: block; margin-bottom: 12px; }
.empty-text { display: block; font-size: 14px; color: var(--text-secondary, #64748b); margin-bottom: 16px; }
.btn-retry { width: 136px; height: 40px; line-height: 40px; border-radius: 12px; background: var(--primary-color, #2563eb); color: #fff; font-size: 14px; }
.bottom-safe { height: calc(env(safe-area-inset-bottom, 0px) + 28px); }

.is-dark .summary-card,
.is-dark .trend-card,
  .is-dark .chart-card,
  .is-dark .visual-card,
  .is-dark .coverage-card,
  .is-dark .coverage-item,
.is-dark .source-card,
.is-dark .note,
.is-dark .method-card,
.is-dark .metric,
.is-dark .visual-stat,
.is-dark .refresh-btn,
.is-dark .source-tag {
  background: #1e293b;
  border-color: #334155;
  box-shadow: none;
}
.is-dark .summary-title,
.is-dark .metric-value,
  .is-dark .visual-stat-value,
  .is-dark .coverage-school,
.is-dark .chart-subtitle,
.is-dark .year-bar-count,
.is-dark .source-type-count,
.is-dark .section-title,
.is-dark .trend-year,
.is-dark .source-title,
.is-dark .note-title { color: #f8fafc; }
.is-dark .summary-text,
.is-dark .highlight-text,
.is-dark .trend-value,
.is-dark .legend-text,
  .is-dark .visual-stat-label,
  .is-dark .coverage-note,
.is-dark .chart-submeta,
.is-dark .source-type-label,
.is-dark .source-excerpt,
.is-dark .note-text { color: #94a3b8; }
.is-dark .source-year-chart { background: #0f172a; }
.is-dark .chart-point { border-color: #1e293b; }
.is-dark .summary-label,
.is-dark .highlight-index,
.is-dark .method-step { background: rgba(37, 99, 235, 0.2); }
.is-dark .method-text { color: #94a3b8; }

/* Competition visual system ------------------------------------------------ */
.hero {
  background: #f5f5f0;
  border: 1px solid #e0dfdb;
  border-top: 1px solid var(--border-color, #e0dfdb);
  border-radius: 6px;
  box-shadow: 0 8px 24px rgba(44, 43, 41, 0.05);
}

.hero-kicker {
  color: #c23b22;
  font-weight: 600;
}

.hero-title,
.summary-title,
.section-title,
.chart-subtitle,
.detail-title,
.source-title,
.note-title {
  color: #2c2b29;
  font-family: "Songti SC", STSong, serif;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.hero-sub,
.summary-text,
.coverage-note,
.highlight-text,
.detail-desc,
.source-excerpt,
.note-text,
.method-text {
  color: #5a5956;
}

.skel-card,
.summary-card,
.coverage-card,
.visual-card,
.highlight-item,
.chart-card,
.trend-card,
.detail-entry,
.source-card,
.note,
.method-card,
.empty-state {
  background: #ffffff;
  border: 1px solid #e0dfdb;
  border-radius: 8px;
  box-shadow: 0 6px 18px rgba(44, 43, 41, 0.04);
}

.skel-line {
  background: #efeee9;
  border-radius: 2px;
}

.summary-label {
  color: #3f51b5;
  background: transparent;
  border: 1px solid rgba(63, 81, 181, 0.36);
  border-radius: 3px;
}

.refresh-btn,
.metric,
.coverage-item,
.visual-stat {
  background: #faf9f6;
  border: 1px solid #e0dfdb;
  border-radius: 4px;
}

.refresh-icon,
.section-action,
.visual-stat-value,
.point-label,
.source-link {
  color: #3f51b5;
}

.metric-value,
.visual-stat-value,
.year-bar-count,
.source-type-count,
.trend-year {
  color: #2c2b29;
  font-family: "Songti SC", STSong, serif;
  font-weight: 600;
}

.postgrad-text {
  color: #6b8e5a;
}

.source-text {
  color: #b8975a;
}

.coverage-item {
  border-radius: 4px;
}

.coverage-verified_full .coverage-label {
  color: #6b8e5a;
}

.coverage-partial .coverage-label {
  color: #3f51b5;
}

.coverage-missing .coverage-label {
  color: #c23b22;
}

.coverage-needs_manual_review .coverage-label {
  color: #987632;
}

.source-year-chart {
  background: #f5f5f0;
  border: 1px solid #edece8;
  border-radius: 4px;
}

.year-bar-fill {
  background: #3f51b5;
  border-radius: 3px 3px 0 0;
  box-shadow: none;
}

.source-type-track,
.trend-bar {
  background: #efeee9;
  border-radius: 2px;
}

.source-type-fill {
  background: #b8975a;
  border-radius: 2px;
}

.highlight-index {
  color: #c23b22;
  background: rgba(194, 59, 34, 0.07);
  border: 1px solid rgba(194, 59, 34, 0.25);
  border-radius: 3px;
}

.legend-employment,
.point-employment,
.trend-fill.employment {
  background: #3f51b5;
}

.legend-postgrad,
.point-postgrad,
.trend-fill.postgrad {
  background: #6b8e5a;
}

.line-employment {
  --line-color: #3f51b5;
}

.line-postgrad {
  --line-color: #6b8e5a;
}

.trend-fill {
  border-radius: 2px;
}

.detail-icon,
.method-step {
  color: #3f51b5;
  background: rgba(63, 81, 181, 0.08);
  border-radius: 4px;
}

.source-tag {
  color: #5a5956;
  background: #f5f5f0;
  border: 1px solid #e0dfdb;
  border-radius: 3px;
}

.btn-retry {
  color: #faf9f6;
  background: #c23b22;
  border-radius: 6px;
}

.is-dark .hero,
.is-dark .summary-card,
.is-dark .coverage-card,
.is-dark .visual-card,
.is-dark .highlight-item,
.is-dark .chart-card,
.is-dark .trend-card,
.is-dark .detail-entry,
.is-dark .source-card,
.is-dark .note,
.is-dark .method-card {
  background: #242320;
  border-color: #494844;
}

.is-dark .hero-title,
.is-dark .summary-title,
.is-dark .section-title,
.is-dark .chart-subtitle,
.is-dark .detail-title,
.is-dark .source-title,
.is-dark .note-title {
  color: #faf9f6;
}

.is-dark .refresh-btn,
.is-dark .metric,
.is-dark .coverage-item,
.is-dark .visual-stat,
.is-dark .source-tag {
  background: #1c1b19;
  border-color: #494844;
}

.is-dark .source-year-chart {
  background: #1c1b19;
  border-color: #494844;
}

.is-dark .hero-sub,
.is-dark .summary-text,
.is-dark .coverage-note,
.is-dark .highlight-text,
.is-dark .detail-desc,
.is-dark .source-excerpt,
.is-dark .note-text,
.is-dark .method-text {
  color: #c6c4be;
}

.is-dark .source-type-track,
.is-dark .trend-bar,
.is-dark .skel-line {
  background: #45443f;
}

.summary-label,
.section-action,
.coverage-label,
.point-label,
.chart-subtitle,
.legend-text,
.trend-label,
.trend-value,
.source-tag,
.source-link,
.note-title,
.method-step {
  font-weight: 600;
}

.summary-title,
.section-title,
.detail-title,
.source-title,
.note-title {
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
  font-weight: 600;
}
</style>
