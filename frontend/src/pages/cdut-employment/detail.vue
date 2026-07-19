<template>
  <SlPage class="employment-detail app-soft-bg" :custom-class="[themeClass, fontClass].join(' ')">
    <SlNavBar :title="pageTitle" show-back @back="goBack" :safe-top="topSafeHeight" />

    <view class="content">
      <view v-if="loading" class="state-card app-surface">
        <text class="state-text">正在加载就业数据...</text>
      </view>

      <view v-else-if="!insight" class="state-card app-surface">
        <text class="state-text">暂时无法加载就业数据</text>
      </view>

      <template v-else>
        <view class="hero app-card-soft app-surface">
          <text class="hero-kicker">{{ insight.school }}</text>
          <text class="hero-title">{{ pageTitle }}</text>
          <text class="hero-sub">{{ insight.major }} · {{ insight.targetRole }}</text>
        </view>

        <view v-if="hasVisualData" class="visual-card app-card-soft app-surface">
          <view class="visual-head">
            <text class="visual-title">{{ isDemoMode ? '就业数据演示可视化' : '学校就业数据可视化' }}</text>
            <text class="visual-meta">{{ insight.latestYear || '近年' }} 数据参考</text>
          </view>
          <view class="visual-top">
            <view class="visual-stat" v-if="insight.latestEmploymentRate !== undefined && insight.latestEmploymentRate !== null">
              <text class="visual-stat-value">{{ formatPercent(insight.latestEmploymentRate) }}</text>
              <text class="visual-stat-label">就业/落实率</text>
            </view>
            <view class="visual-stat" v-if="insight.latestPostgraduateRate !== undefined && insight.latestPostgraduateRate !== null">
              <text class="visual-stat-value postgrad-text">{{ formatPercent(insight.latestPostgraduateRate) }}</text>
              <text class="visual-stat-label">升学/深造率</text>
            </view>
            <view class="visual-stat">
              <text class="visual-stat-value source-text">{{ insight.sourceCount || 0 }}</text>
              <text class="visual-stat-label">{{ isDemoMode ? '演示材料' : '公开来源' }}</text>
            </view>
          </view>

          <view v-if="sourceYearBars.length" class="source-year-chart">
            <view class="year-bar-item" v-for="bar in sourceYearBars" :key="bar.key">
              <view class="year-bar-track">
                <view class="year-bar-fill" :style="{ height: bar.height }"></view>
              </view>
              <text class="year-bar-count">{{ bar.count }}</text>
              <text class="year-bar-label">{{ bar.label }}</text>
            </view>
          </view>

          <view v-if="sourceTypeBars.length" class="source-type-block">
            <view class="source-type-row" v-for="bar in sourceTypeBars" :key="bar.key">
              <text class="source-type-label">{{ bar.label }}</text>
              <view class="source-type-track">
                <view class="source-type-fill" :style="{ width: bar.width }"></view>
              </view>
              <text class="source-type-count">{{ bar.count }}</text>
            </view>
          </view>
        </view>

        <view v-if="section === 'trend'" class="section">
          <text class="section-title">就业与升学趋势</text>
          <template v-if="insight.trend?.length">
            <view class="chart-card app-card-soft app-surface">
              <view class="chart-legend">
                <view class="legend-item"><view class="legend-dot legend-employment"></view><text class="legend-text">就业率折线</text></view>
                <view class="legend-item" v-if="chartSeries.postgrad.points.length"><view class="legend-dot legend-postgrad"></view><text class="legend-text">升学率折线</text></view>
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
                <text class="trend-year">{{ p.year || '-' }}</text>
                <view class="trend-stack">
                  <view class="trend-main">
                    <text class="trend-label">就业</text>
                    <view class="trend-bar"><view class="trend-fill employment" :style="{ width: percentWidth(p.employmentRate) }"></view></view>
                    <text class="trend-value">{{ formatPercent(p.employmentRate) }}</text>
                  </view>
                  <view class="trend-main">
                    <text class="trend-label">升学</text>
                    <view class="trend-bar"><view class="trend-fill postgrad" :style="{ width: percentWidth(p.postgraduateRate) }"></view></view>
                    <text class="trend-value">{{ formatPercent(p.postgraduateRate) }}</text>
                  </view>
                </view>
              </view>
            </view>
          </template>
          <view v-else class="state-card app-surface">
            <text class="state-text">暂无可展示的趋势数据</text>
          </view>
        </view>

        <view v-if="section === 'sources'" class="section">
          <text class="section-title">{{ isDemoMode ? '演示材料' : '公开来源' }}</text>
          <view v-if="insight.sources?.length" class="source-list">
            <view class="source-card app-card-soft app-surface" v-for="source in insight.sources" :key="source.url">
              <view class="source-head">
                <text class="source-title">{{ source.title }}</text>
                <text class="source-year" v-if="source.year">{{ source.year }}</text>
              </view>
              <view class="source-tags">
                <text class="source-tag" v-if="source.sourceType">{{ sourceTypeLabel(source.sourceType) }}</text>
                <text class="source-tag" v-if="source.majorKeyword">{{ source.majorKeyword }}</text>
                <text class="source-tag" v-if="source.careerKeyword">{{ source.careerKeyword }}</text>
              </view>
              <text class="source-excerpt" v-if="source.excerpt">{{ source.excerpt }}</text>
              <view v-if="!isDemoMode" class="source-actions">
                <view class="source-btn primary" @click="openSource(source.url, source.title)">
                  <text>打开来源</text>
                </view>
                <view class="source-btn" @click="copySource(source.url)">
                  <text>复制链接</text>
                </view>
              </view>
            </view>
          </view>
          <view v-else class="state-card app-surface">
            <text class="state-text">{{ isDemoMode ? '暂无演示材料' : '暂未接入该校公开就业数据来源' }}</text>
          </view>
        </view>

        <view v-if="section === 'method'" class="section">
          <text class="section-title">数据口径</text>
          <view class="method-card app-card-soft app-surface">
            <view class="method-row">
              <text class="method-step">1</text>
              <text class="method-text">{{ methodStep1 }}</text>
            </view>
            <view class="method-row">
              <text class="method-step">2</text>
              <text class="method-text">{{ methodStep2 }}</text>
            </view>
            <view class="method-row">
              <text class="method-step">3</text>
              <text class="method-text">{{ methodStep3 }}</text>
            </view>
            <view class="method-meta">
              <text>{{ isDemoMode ? '演示材料' : '来源数量' }}：{{ insight.sourceCount || 0 }}</text>
              <text v-if="insight.updatedAt">更新时间：{{ formatDate(insight.updatedAt) }}</text>
            </view>
          </view>
        </view>
      </template>

      <view class="bottom-safe"></view>
    </view>
  </SlPage>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { onLoad, onShow } from '@dcloudio/uni-app';
import { getMpSafeAreaMetrics } from '@/utils/safeArea';
import { useTheme } from '@/utils/theme';
import { openLink } from '@/utils/openLink';
import SlPage from '@/style-library/components/SlPage.vue';
import SlNavBar from '@/style-library/components/SlNavBar.vue';
import { getCdutEmploymentInsightApi, type CdutEmploymentInsight } from '@/api/cdutEmployment';

type Section = 'trend' | 'sources' | 'method';

const { themeClass, fontClass, refresh: refreshTheme } = useTheme();
const topSafeHeight = ref(52);
const loading = ref(true);
const insight = ref<CdutEmploymentInsight | null>(null);
const section = ref<Section>('sources');
const isDemoMode = computed(() => Boolean(insight.value?.demoMode));

const pageTitle = computed(() => {
  if (section.value === 'trend') return isDemoMode.value ? '演示趋势' : '已验证趋势';
  if (section.value === 'method') return '数据口径';
  if (!insight.value) return '数据来源';
  return isDemoMode.value ? '演示材料' : '公开来源';
});

const methodStep1 = computed(() =>
  isDemoMode.value
    ? '当前页面使用答辩脱敏演示数据，学校、来源和统计值均为虚构样例，用于展示完整业务流程。'
    : '系统优先使用学校公开就业质量报告、学院就业去向公告等可追溯来源。'
);
const methodStep2 = computed(() =>
  isDemoMode.value
    ? '专业「软件工程」与目标岗位「Java 后端开发」用于演示匹配流程，不连接真实学校公开数据。'
    : '专业和目标岗位只用于筛选相关来源，不会把其他学校的数据替代到当前学校。'
);
const methodStep3 = computed(() =>
  isDemoMode.value
    ? '2021-2025 年演示材料均已补齐趋势、摘要与覆盖状态，便于稳定展示完整效果。'
    : '未接入学校会明确显示暂无数据；就业率、升学率为空时不生成图表结论。'
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

const chartYears = computed(() =>
  (insight.value?.trend || [])
    .map((p) => p.year)
    .filter((year): year is number => year !== undefined && year !== null)
);

const toChartPoints = (field: 'employmentRate' | 'postgraduateRate'): ChartPoint[] => {
  const rows = (insight.value?.trend || [])
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

const openSource = (url: string, title?: string) => {
  if (url) openLink(url, title);
};

const copySource = (url: string) => {
  uni.setClipboardData({
    data: url,
    success: () => uni.showToast({ title: '链接已复制', icon: 'success' }),
  });
};

const goBack = () => uni.navigateBack({ delta: 1 });

onLoad((options: any) => {
  const next = options?.section;
  if (next === 'trend' || next === 'sources' || next === 'method') section.value = next;
});

onMounted(() => {
  refreshTheme();
  topSafeHeight.value = getMpSafeAreaMetrics().topSafeHeight;
  load();
});

onShow(() => {
  refreshTheme();
});
</script>

<style scoped>
.employment-detail { min-height: 100vh; }
.content { padding: 16px var(--page-gutter, 20px) 0; box-sizing: border-box; }
.hero { padding: 16px; margin-bottom: 20px; }
.hero-kicker {
  display: block;
  font-size: 11px;
  font-weight: 900;
  color: var(--primary-color, #cd6a43);
  margin-bottom: 6px;
}
.hero-title {
  display: block;
  font-size: 24px;
  line-height: 1.15;
  font-weight: 900;
  color: var(--text-primary, #1c1917);
}
.hero-sub {
  display: block;
  margin-top: 8px;
  font-size: 13px;
  color: var(--text-secondary, #78716c);
}
.visual-card { padding: 14px; margin-bottom: 20px; }
.visual-head { display: flex; align-items: baseline; justify-content: space-between; gap: 10px; margin-bottom: 12px; }
.visual-title { font-size: 14px; font-weight: 900; color: var(--text-primary, #1c1917); }
.visual-meta { font-size: 10.5px; color: var(--text-tertiary, #8e8e93); }
.visual-top { display: flex; gap: 8px; margin-bottom: 14px; }
.visual-stat {
  flex: 1;
  min-width: 0;
  border-radius: 14px;
  padding: 12px 8px;
  background: var(--surface-2, #fafaf9);
  border: 1px solid var(--border-color, #e7e5e4);
}
.visual-stat-value {
  display: block;
  font-size: 18px;
  line-height: 1.05;
  font-weight: 900;
  color: var(--primary-color, #cd6a43);
}
.postgrad-text { color: #624d23; }
.source-text { color: #d27855; }
.visual-stat-label {
  display: block;
  margin-top: 6px;
  font-size: 10px;
  line-height: 1.25;
  color: var(--text-tertiary, #8e8e93);
}
.source-year-chart {
  height: 132px;
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 8px 4px 0;
  border-radius: 14px;
  background: linear-gradient(180deg, rgba(205, 106, 67,0.05), rgba(98, 77, 35,0.04));
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
  background: linear-gradient(180deg, #d47f5d, #624d23);
  box-shadow: 0 8px 16px rgba(205, 106, 67, 0.18);
}
.year-bar-count {
  margin-top: 5px;
  font-size: 11px;
  font-weight: 900;
  color: var(--text-primary, #1c1917);
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
  color: var(--text-secondary, #78716c);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.source-type-track {
  flex: 1;
  height: 9px;
  border-radius: 999px;
  background: var(--surface-3, #f5f5f4);
  overflow: hidden;
}
.source-type-fill {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, #d27855, #d47e5c);
}
.source-type-count {
  width: 18px;
  text-align: right;
  font-size: 11px;
  font-weight: 900;
  color: var(--text-primary, #1c1917);
}
.section { margin-top: 20px; }
.section-title {
  display: block;
  font-size: 17px;
  font-weight: 900;
  color: var(--text-primary, #1c1917);
  margin-bottom: 12px;
}
.state-card { padding: 20px; text-align: center; }
.state-text { font-size: 13px; color: var(--text-secondary, #78716c); }
.trend-card { padding: 14px; display: flex; flex-direction: column; gap: 14px; }
.chart-card { padding: 14px; margin-bottom: 10px; }
.chart-legend { display: flex; align-items: center; gap: 14px; margin-bottom: 12px; }
.legend-item { display: flex; align-items: center; gap: 5px; }
.legend-dot { width: 8px; height: 8px; border-radius: 50%; }
.legend-employment { background: #cd6a43; }
.legend-postgrad { background: #624d23; }
.legend-text { font-size: 11px; font-weight: 800; color: var(--text-secondary, #78716c); }
.line-chart {
  position: relative;
  height: 172px;
  margin: 2px 8px 0;
  border-left: 1px solid var(--border-color, #e7e5e4);
  border-bottom: 1px solid var(--border-color, #e7e5e4);
}
.chart-grid {
  position: absolute;
  left: 0;
  right: 0;
  height: 1px;
  background: var(--surface-3, #f5f5f4);
}
.chart-line {
  position: absolute;
  min-height: 2px;
  background: linear-gradient(
    var(--line-direction, to bottom right),
    transparent calc(50% - 1px),
    var(--line-color, #cd6a43) calc(50% - 1px),
    var(--line-color, #cd6a43) calc(50% + 1px),
    transparent calc(50% + 1px)
  );
  z-index: 1;
}
.line-employment { --line-color: #cd6a43; }
.line-postgrad { --line-color: #624d23; opacity: 0.9; }
.chart-point {
  position: absolute;
  width: 9px;
  height: 9px;
  border-radius: 50%;
  transform: translate(-50%, -50%);
  border: 2px solid #ffffff;
  box-shadow: 0 2px 6px rgba(28, 25, 23, 0.18);
  z-index: 2;
}
.point-employment { background: #cd6a43; }
.point-postgrad { background: #624d23; }
.point-label {
  position: absolute;
  left: 50%;
  bottom: 10px;
  transform: translateX(-50%);
  font-size: 10px;
  font-weight: 900;
  color: var(--primary-color, #cd6a43);
  white-space: nowrap;
}
.chart-years { display: flex; justify-content: space-between; gap: 4px; padding: 7px 4px 0 12px; }
.chart-year { font-size: 10px; color: var(--text-tertiary, #8e8e93); }
.trend-row { display: flex; gap: 10px; align-items: flex-start; }
.trend-year { width: 44px; font-size: 12px; font-weight: 900; color: var(--text-primary, #1c1917); }
.trend-stack { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 8px; }
.trend-main { display: flex; align-items: center; gap: 8px; }
.trend-label { width: 28px; font-size: 11px; font-weight: 800; color: var(--text-tertiary, #8e8e93); }
.trend-bar { flex: 1; height: 9px; background: var(--surface-3, #f5f5f4); border-radius: 999px; overflow: hidden; }
.trend-fill { height: 100%; border-radius: 999px; }
.trend-fill.employment { background: linear-gradient(90deg, #cd6a43, #d47e5c); }
.trend-fill.postgrad { background: linear-gradient(90deg, #624d23, #a7833b); }
.trend-value { width: 48px; text-align: right; font-size: 12px; font-weight: 900; color: var(--text-secondary, #78716c); }
.source-list { display: flex; flex-direction: column; gap: 10px; }
.source-card { padding: 14px; }
.source-head { display: flex; justify-content: space-between; gap: 10px; }
.source-title { flex: 1; font-size: 14px; line-height: 1.4; font-weight: 900; color: var(--text-primary, #1c1917); }
.source-year { font-size: 11px; color: var(--text-tertiary, #8e8e93); }
.source-tags { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px; }
.source-tag { border-radius: 999px; padding: 3px 8px; background: var(--surface-2, #fafaf9); color: var(--text-secondary, #78716c); font-size: 10px; font-weight: 800; }
.source-excerpt { display: block; margin-top: 9px; font-size: 12px; line-height: 1.5; color: var(--text-secondary, #78716c); }
.source-actions { display: flex; gap: 8px; margin-top: 12px; }
.source-btn {
  flex: 1;
  height: 36px;
  border-radius: 10px;
  background: var(--surface-2, #fafaf9);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 800;
  color: var(--text-primary, #1c1917);
}
.source-btn.primary { background: var(--primary-color, #cd6a43); color: #ffffff; }
.method-card { padding: 14px; }
.method-row { display: flex; align-items: flex-start; gap: 10px; margin-top: 10px; }
.method-row:first-child { margin-top: 0; }
.method-step {
  width: 22px; height: 22px; border-radius: 11px; line-height: 22px; text-align: center;
  background: var(--primary-soft, #fcf5f2); color: var(--primary-color, #cd6a43);
  font-size: 11px; font-weight: 900; flex-shrink: 0;
}
.method-text { flex: 1; font-size: 12px; line-height: 1.55; color: var(--text-secondary, #78716c); }
.method-meta {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--border-color, #e7e5e4);
  display: flex;
  flex-direction: column;
  gap: 5px;
  font-size: 11px;
  color: var(--text-tertiary, #8e8e93);
}
.bottom-safe { height: calc(env(safe-area-inset-bottom, 0px) + 28px); }
.is-dark .hero,
.is-dark .trend-card,
.is-dark .chart-card,
.is-dark .visual-card,
.is-dark .source-card,
.is-dark .method-card,
.is-dark .state-card,
.is-dark .visual-stat,
.is-dark .source-btn,
.is-dark .source-tag {
  background: #292524;
  border-color: #44403c;
  box-shadow: none;
}
.is-dark .hero-title,
.is-dark .visual-title,
.is-dark .visual-stat-value,
.is-dark .year-bar-count,
.is-dark .source-type-count,
.is-dark .section-title,
.is-dark .trend-year,
.is-dark .source-title,
.is-dark .source-btn { color: #fafaf9; }
.is-dark .hero-sub,
.is-dark .visual-meta,
.is-dark .visual-stat-label,
.is-dark .source-type-label,
.is-dark .state-text,
.is-dark .trend-value,
.is-dark .legend-text,
.is-dark .source-excerpt,
.is-dark .method-text { color: #a8a29e; }
.is-dark .source-year-chart { background: #1c1917; }
.is-dark .chart-point { border-color: #292524; }

/* Competition visual system ------------------------------------------------ */
.hero,
.visual-card,
.chart-card,
.trend-card,
.source-card,
.method-card,
.state-card {
  background: #ffffff;
  border: 1px solid #e0dfdb;
  border-radius: 8px;
  box-shadow: 0 6px 18px rgba(44, 43, 41, 0.04);
}

.hero {
  background: #f5f5f0;
  border-top: 1px solid var(--border-color, #e0dfdb);
}

.hero-kicker {
  color: #c23b22;
  font-weight: 600;
  letter-spacing: 0.1em;
}

.hero-title,
.visual-title,
.section-title,
.source-title {
  color: #2c2b29;
  font-family: "Songti SC", STSong, serif;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.hero-sub,
.visual-meta,
.state-text,
.trend-value,
.legend-text,
.source-excerpt,
.method-text,
.method-meta {
  color: #5a5956;
}

.visual-stat {
  background: #faf9f6;
  border: 1px solid #e0dfdb;
  border-radius: 4px;
}

.visual-stat-value,
.year-bar-count,
.source-type-count,
.trend-year,
.point-label {
  color: #ac6448;
  font-family: "Songti SC", STSong, serif;
  font-weight: 600;
}

.postgrad-text {
  color: #947f54;
}

.source-text {
  color: #b8975a;
}

.source-year-chart {
  background: #f5f5f0;
  border: 1px solid #edece8;
  border-radius: 4px;
}

.year-bar-fill {
  background: #ac6448;
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

.legend-employment,
.point-employment,
.trend-fill.employment {
  background: #ac6448;
}

.legend-postgrad,
.point-postgrad,
.trend-fill.postgrad {
  background: #947f54;
}

.line-employment {
  --line-color: #ac6448;
}

.line-postgrad {
  --line-color: #947f54;
}

.trend-fill {
  border-radius: 2px;
}

.source-tag {
  color: #5a5956;
  background: #f5f5f0;
  border: 1px solid #e0dfdb;
  border-radius: 3px;
}

.source-btn {
  color: #2c2b29;
  background: #faf9f6;
  border: 1px solid #e0dfdb;
  border-radius: 4px;
}

.source-btn.primary {
  color: #faf9f6;
  background: #c23b22;
  border-color: #c23b22;
}

.method-step {
  color: #ac6448;
  background: rgba(172, 100, 72, 0.08);
  border: 1px solid rgba(172, 100, 72, 0.22);
  border-radius: 3px;
}

.is-dark .hero,
.is-dark .visual-card,
.is-dark .chart-card,
.is-dark .trend-card,
.is-dark .source-card,
.is-dark .method-card,
.is-dark .state-card {
  background: #242320;
  border-color: #494844;
}

.is-dark .hero-title,
.is-dark .visual-title,
.is-dark .section-title,
.is-dark .source-title {
  color: #faf9f6;
}

.is-dark .visual-stat,
.is-dark .source-btn,
.is-dark .source-tag,
.is-dark .source-year-chart {
  background: #1c1b19;
  border-color: #494844;
}

.is-dark .hero-sub,
.is-dark .visual-meta,
.is-dark .state-text,
.is-dark .trend-value,
.is-dark .legend-text,
.is-dark .source-excerpt,
.is-dark .method-text,
.is-dark .method-meta {
  color: #c6c4be;
}

.is-dark .source-type-track,
.is-dark .trend-bar {
  background: #45443f;
}

.visual-stat-value,
.year-bar-count,
.source-type-count,
.trend-year,
.trend-label,
.trend-value,
.legend-text,
.source-tag,
.source-btn,
.method-step {
  font-weight: 600;
}

.hero-title,
.visual-title,
.section-title,
.source-title {
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
  font-weight: 600;
}
</style>
