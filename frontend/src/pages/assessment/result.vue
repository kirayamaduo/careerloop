<template>
  <view class="result-container app-soft-bg" :class="[themeClass, fontClass]">
    <SlNavBar :title="t('assessmentResult.navTitle')" show-back @back="goBack" :safe-top="topSafeHeight" :right-avoid-width="rightAvoidWidth" />
    <!-- Loading + error states -->
    <view class="loading-state app-surface" v-if="loading">
      <view class="spinner"></view>
      <text class="loading-text">{{ t('assessmentResult.loading') }}</text>
    </view>

    <view class="error-state app-surface" v-else-if="errorMsg">
      <text class="err-title">{{ errorMsg }}</text>
      <view class="btn-retry" @click="loadResult" hover-class="press-fb" hover-stay-time="120"><text class="btn-retry-text">{{ t('assessmentResult.retry') }}</text></view>
    </view>

    <template v-else-if="record">
      <view class="result-header app-card-gradient">
        <text class="result-subtitle">{{ t('assessmentResult.personalityType') }}</text>
        <text class="result-title">{{ summaryDisplay }}</text>
        <view class="tags-container" v-if="typeTags.length > 0">
          <text class="tag" v-for="(t, i) in typeTags" :key="i">{{ t }}</text>
        </view>
      </view>

      <view class="section-title">{{ t('assessmentResult.dimensionBreakdown') }}</view>
      <view class="radar-card app-card-soft">
        <view class="score-basis">
          <text class="score-basis-title">评分依据</text>
          <text class="score-basis-text">测评维度由你的选项按量表规则计数得出，AI 只负责解释画像、总结优势和推荐岗位方向。</text>
        </view>
        <view class="skill-bars">
          <view class="skill-row" v-for="(d, i) in dimensions" :key="i">
            <text class="skill-name">{{ d.name }}</text>
            <view class="bar-bg"><view class="bar-fill" :class="d.fillClass" :style="{ width: d.percent + '%' }"></view></view>
            <text class="skill-score">{{ d.value }}</text>
          </view>
        </view>
      </view>

      <view class="section-title">
        <text class="section-title-text">{{ t('assessmentResult.personalityInsight') }}</text>
        <text class="ai-badge" v-if="insight.fromAi">AI</text>
      </view>
      <view class="analysis-card app-card-soft">
        <view class="paragraph">
          <text class="p-title">{{ t('assessmentResult.careerStrengths') }}</text>
          <text class="p-content">{{ insight.strengths }}</text>
        </view>
        <view class="divider"></view>
        <view class="paragraph">
          <text class="p-title">{{ t('assessmentResult.growthAreas') }}</text>
          <text class="p-content">{{ insight.growth }}</text>
        </view>
        <template v-if="insight.suggestedRoles && insight.suggestedRoles.length > 0">
          <view class="divider"></view>
          <view class="paragraph">
            <text class="p-title">{{ t('assessmentResult.suggestedRoles') }}</text>
            <view class="roles-row">
              <text class="role-chip" v-for="(r, i) in insight.suggestedRoles" :key="i">{{ r }}</text>
            </view>
          </view>
        </template>
      </view>
    </template>

    <view class="bottom-action" v-if="!loading && !errorMsg">
      <view
        v-if="primarySuggestedRole"
        class="btn-primary practice-cta"
        @click="practiceInterview"
      >
        <text class="btn-primary-text">{{ t('assessmentResult.practiceInterview', { role: primarySuggestedRole }) }}</text>
      </view>
      <view class="btn-primary" :class="{ 'btn-secondary-tone': !!primarySuggestedRole }" @click="goMap">
        <text class="btn-primary-text">{{ t('assessmentResult.viewCareerMap') }}</text>
      </view>
      <view class="btn-secondary" @click="goBack"><text class="btn-secondary-text">{{ t('assessmentResult.backToAssessment') }}</text></view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useI18n } from '@/locales';
import { onShow } from '@dcloudio/uni-app';
import {
  getAssessmentRecordApi,
  type AssessmentRecord,
} from '@/api/assessment';
import { updatePreferencesApi } from '@/api/user';
import { useTheme } from '@/utils/theme';
import { getMpSafeAreaMetrics } from '@/utils/safeArea';
import SlNavBar from '@/style-library/components/SlNavBar.vue';

const { t } = useI18n();
const { themeClass, fontClass, refresh: refreshTheme } = useTheme();
const topSafeHeight = ref(52);
const rightAvoidWidth = ref(20);
const loading = ref(true);
const errorMsg = ref('');
const recordId = ref<number>(0);
const record = ref<AssessmentRecord | null>(null);

/**
 * Static MBTI archetype labels and a short one-liner description per type.
 * The backend already produces the 4-letter code in `result_summary`; we
 * use it here as the lookup key for richer copy. Holland support can be
 * added by branching on `record.scaleId` once Holland questions are seeded.
 */
const MBTI_TYPES: Record<string, { name: string; tags: string[]; strengths: string; growth: string }> = {
  INTJ: { name: '规划型', tags: ['战略', '独立', '分析'],
    strengths: '你擅长长期规划和发现系统问题，适合需要结构化思考的岗位。',
    growth: '表达判断时多补充人的因素，让同伴理解你的逻辑和取舍。' },
  INTP: { name: '探索型', tags: ['好奇', '理论', '冷静'],
    strengths: '你适合深入研究复杂问题，调试、算法、工具和研究类任务会更容易发挥。',
    growth: '避免长时间停留在分析阶段，给自己设定可交付的小节点。' },
  ENTJ: { name: '推动型', tags: ['果断', '目标感', '组织'],
    strengths: '你擅长推动团队达成目标，适合管理、咨询和需要决策的角色。',
    growth: '放慢一点听取不同声音，避免节奏过快导致信息遗漏。' },
  ENTP: { name: '创想型', tags: ['创新', '活跃', '敢试'],
    strengths: '你能快速提出新思路，适合产品、增长和变化快的业务环境。',
    growth: '把想法落实成清晰计划，减少半途切换造成的损耗。' },
  INFJ: { name: '洞察型', tags: ['洞察', '原则', '安静领导'],
    strengths: '你能理解人的动机和长期价值，适合研究、设计和人才发展相关工作。',
    growth: '注意保留精力边界，不要把所有情绪压力都扛在自己身上。' },
  INFP: { name: '理想型', tags: ['价值感', '创意', '共情'],
    strengths: '你在有意义的事情上更有投入度，适合内容、设计和使命感强的产品。',
    growth: '把价值判断转成可衡量目标，让协作伙伴知道怎样算完成。' },
  ENFJ: { name: '带动型', tags: ['感染力', '辅导', '温暖'],
    strengths: '你擅长凝聚团队和传递愿景，适合教育、管理和合作型岗位。',
    growth: '帮助别人时也要设定边界，避免长期消耗自己。' },
  ENFP: { name: '启发型', tags: ['热情', '想象力', '社交'],
    strengths: '你适合连接人和想法，产品市场、用户运营和开发者关系会更容易发挥。',
    growth: '用固定流程承接灵感，保证重要事情能持续推进。' },
  ISTJ: { name: '执行型', tags: ['可靠', '细致', '有方法'],
    strengths: '你擅长把系统稳定运行起来，适合后端、安全、运维和质量保障。',
    growth: '面对新方法时保持开放，避免只依赖过去经验。' },
  ISFJ: { name: '支持型', tags: ['负责', '忠诚', '务实'],
    strengths: '你能用细致准备支撑团队，适合内部工具、客户支持和团队协调。',
    growth: '更早表达自己的贡献和判断，避免被低估。' },
  ESTJ: { name: '管理型', tags: ['组织', '直接', '结果导向'],
    strengths: '你重视效率和责任，适合项目管理、运营管理和执行强度高的岗位。',
    growth: '给开放探索留一些空间，避免过早锁死方案。' },
  ESFJ: { name: '协作型', tags: ['支持', '稳定', '外向'],
    strengths: '你擅长让团队保持一致，适合客户成功、合作关系和团队管理。',
    growth: '不要让追求共识取代清晰判断，必要时要说出关键问题。' },
  ISTP: { name: '实干型', tags: ['务实', '动手', '冷静'],
    strengths: '你擅长定位和解决具体问题，适合基础设施、硬件、应急响应等场景。',
    growth: '把解决过程记录下来，避免经验只停留在自己脑中。' },
  ISFP: { name: '体验型', tags: ['审美', '敏感', '随和'],
    strengths: '你对体验和细节敏感，适合设计、前端和创意工具相关方向。',
    growth: '把设计判断讲清楚，让好品味在协作中被看见。' },
  ESTP: { name: '行动型', tags: ['有活力', '务实', '大胆'],
    strengths: '你反应快、适应强，适合销售工程、商务拓展和高压运营场景。',
    growth: '做决定前多看一步长期影响，减少返工。' },
  ESFP: { name: '表现型', tags: ['自发', '活跃', '友好'],
    strengths: '你能带动现场氛围，适合客户面对面、活动、内容和开发者关系。',
    growth: '给重复但重要的工作建立节奏，比如跟进、维护和复盘。' },
};

const summaryDisplay = computed(() => {
  const code = (record.value?.resultSummary || '').toUpperCase();
  const meta = MBTI_TYPES[code];
  return meta ? `${code} · ${meta.name}` : (code || 'Result');
});

const typeTags = computed(() => {
  const meta = MBTI_TYPES[(record.value?.resultSummary || '').toUpperCase()];
  return meta ? meta.tags : [];
});

interface InsightView {
  fromAi: boolean;
  strengths: string;
  growth: string;
  suggestedRoles: string[];
}

/**
 * Prefer the AI-generated insight (returned by the backend with the record),
 * fall back to the static MBTI table when AI failed or wasn't run, and a
 * generic message when even that doesn't apply.
 */
const insight = computed<InsightView>(() => {
  const aiRaw = record.value?.aiInsight;
  if (aiRaw) {
    try {
      const parsed = JSON.parse(aiRaw) as {
        strengths?: string;
        growth?: string;
        suggestedRoles?: string[];
      };
      if (parsed?.strengths && parsed?.growth) {
        return {
          fromAi: true,
          strengths: parsed.strengths,
          growth: parsed.growth,
          suggestedRoles: Array.isArray(parsed.suggestedRoles) ? parsed.suggestedRoles : [],
        };
      }
    } catch { /* fall through to static */ }
  }

  const meta = MBTI_TYPES[(record.value?.resultSummary || '').toUpperCase()];
  if (meta) {
    return { fromAi: false, strengths: meta.strengths, growth: meta.growth, suggestedRoles: [] };
  }
  return {
    fromAi: false,
    strengths: '你的答案呈现出一组独特的倾向组合，可以先参考上方维度条判断自己更自然的工作方式。',
    growth:    '优先关注得分较低或波动明显的维度，把它们作为后续训练和岗位选择时的校准点。',
    suggestedRoles: [],
  };
});

/**
 * Convert the raw {dimension: count} JSON into ordered, percentage-scaled
 * rows for the bars. For MBTI we display the 4 axes side-by-side so you
 * see which side dominates; the percent is the dominant share.
 */
interface DimensionRow { name: string; value: number; percent: number; fillClass: string; }

const MBTI_AXES: Array<[string, string, string]> = [
  // [left letter, right letter, label of the axis]
  ['E', 'I', 'Extra/Intro'],
  ['S', 'N', 'Sense/Intu'],
  ['T', 'F', 'Think/Feel'],
  ['J', 'P', 'Judge/Perc'],
];

const FILL_CLASSES = ['fill-logic', 'fill-creative', 'fill-exec', 'fill-team', 'fill-pressure'];

const dimensions = computed<DimensionRow[]>(() => {
  const raw = record.value?.resultJson;
  if (!raw) return [];
  let traits: Record<string, number> = {};
  try { traits = JSON.parse(raw); } catch { return []; }

  const rows: DimensionRow[] = [];
  // MBTI display: dominant letter wins each axis, percent = dominant / (a+b) * 100
  const looksLikeMbti = MBTI_AXES.every(([a, b]) => a in traits || b in traits);
  if (looksLikeMbti) {
    MBTI_AXES.forEach(([a, b], i) => {
      const va = traits[a] || 0;
      const vb = traits[b] || 0;
      const total = va + vb || 1;
      const dominant = va >= vb ? a : b;
      const value = Math.max(va, vb);
      rows.push({
        name: `${dominant} (${a}/${b})`,
        value,
        percent: Math.round((value / total) * 100),
        fillClass: FILL_CLASSES[i % FILL_CLASSES.length],
      });
    });
    return rows;
  }

  // Generic fallback (e.g. Holland): show every dimension scaled to the max.
  const max = Math.max(1, ...Object.values(traits));
  Object.entries(traits)
    .sort((a, b) => b[1] - a[1])
    .forEach(([k, v], i) => {
      rows.push({
        name: k,
        value: v,
        percent: Math.round((v / max) * 100),
        fillClass: FILL_CLASSES[i % FILL_CLASSES.length],
      });
    });
  return rows;
});

const loadResult = async () => {
  if (!recordId.value) {
    errorMsg.value = t('assessmentResult.missingRecordId');
    loading.value = false;
    return;
  }
  loading.value = true;
  errorMsg.value = '';
  try {
    record.value = await getAssessmentRecordApi(recordId.value);
  } catch (e: any) {
    errorMsg.value = e?.message || t('assessmentResult.loadFailed');
  } finally {
    loading.value = false;
  }
};

/**
 * The first AI-suggested role for this profile. Used as the headline CTA
 * label so the user can jump straight from "I'm an INFP" to "okay, let me
 * practice a UX Researcher interview" with one tap. Empty when the AI
 * insight failed and we have no concrete roles to suggest.
 */
const primarySuggestedRole = computed(() => {
  const roles = insight.value.suggestedRoles;
  return roles && roles.length > 0 ? roles[0] : '';
});

const goMap = async () => {
  const role = primarySuggestedRole.value;
  if (role) {
    uni.setStorageSync('assessment_recommended_role', role);
    try {
      await updatePreferencesApi({ targetRole: role });
    } catch {
      // Keep the navigation usable offline; the role hint still selects the
      // closest public career path locally.
    }
  }
  const roleQuery = role ? `&role=${encodeURIComponent(role)}` : '';
  uni.navigateTo({ url: `/pages/map/index?from=assessment${roleQuery}` });
};

/**
 * Bridge from assessment → interview without making the user re-type the role.
 * Persists the chosen role into the cross-tool snapshot (so resume diagnosis
 * + interview start + AI assistant all see it next time), then navigates.
 * The snapshot write is best-effort — even if the network blips we still
 * navigate so the user doesn't get stranded.
 */
const practiceInterview = async () => {
  const role = primarySuggestedRole.value;
  if (!role) return;
  uni.setStorageSync('assessment_recommended_role', role);
  try {
    await updatePreferencesApi({ targetRole: role });
  } catch {
    // Snapshot write failed -- non-fatal, we still hand off to the interview start page.
  }
  uni.navigateTo({ url: `/pages/interview/start?suggestedRole=${encodeURIComponent(role)}` });
};

const goBack = () => {
  uni.navigateBack({
    delta: 1,
    fail: () => uni.redirectTo({ url: '/pages/assessment/index' }),
  });
};

onMounted(() => {
  refreshTheme();
  const safeMetrics = getMpSafeAreaMetrics();
  topSafeHeight.value = safeMetrics.topSafeHeight;
  rightAvoidWidth.value = safeMetrics.rightAvoidWidth;
  const pages = getCurrentPages();
  const opts = (pages[pages.length - 1] as any).options || {};
  recordId.value = parseInt(opts.recordId || '0');
  loadResult();
});

onShow(() => {
  refreshTheme();
  uni.setNavigationBarTitle({ title: t('assessmentResult.navTitle') });
});
</script>

<style scoped>
.result-container {
  min-height: 100vh;
  padding: 24px 20px;
  padding-bottom: calc(228px + env(safe-area-inset-bottom, 0px));
  font-family: var(--font-sans, "Noto Sans SC", "PingFang SC", "Microsoft YaHei", sans-serif);
  box-sizing: border-box;
}

.result-header {
  display: flex; flex-direction: column; align-items: center;
  padding: 40px 20px;
  border-radius: var(--radius-xl, 24px); color: white; margin-bottom: 32px;
}

.result-subtitle { font-size: 14px; font-weight: 500; opacity: 0.8; margin-bottom: 8px; letter-spacing: 1px; }

.result-title { font-size: 36px; font-weight: 800; letter-spacing: -1px; margin-bottom: 20px; }

.tags-container { display: flex; gap: 12px; }

.tag {
  background: rgba(255, 255, 255, 0.15);
  backdrop-filter: blur(10px); -webkit-backdrop-filter: blur(10px);
  padding: 6px 14px; border-radius: 16px; font-size: 13px; font-weight: 600;
  border: 1px solid rgba(255, 255, 255, 0.2);
}

.section-title {
  font-size: 20px; font-weight: 700; color: #000000;
  letter-spacing: -0.5px; margin-top: 12px; margin-bottom: 16px; padding-left: 4px;
  display: flex; align-items: center; gap: 10px;
}
.section-title-text { font-size: 20px; font-weight: 700; color: inherit; }

/* "AI" pill -- signals the copy below was generated for this specific user */
.ai-badge {
  display: inline-flex; align-items: center;
  font-size: 10px; font-weight: 800; letter-spacing: 0.08em;
  padding: 3px 8px; border-radius: 999px;
  color: #ffffff;
  background: linear-gradient(135deg, #db9579 0%, #db9377 100%);
}

/* Suggested-roles chips */
.roles-row { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 4px; }
.role-chip {
  font-size: 12px; font-weight: 600; color: #cd6a43;
  background: #fcf5f2; border: 1px solid #f7e8e2;
  padding: 6px 12px; border-radius: 999px;
}

.radar-card {
  margin-bottom: 32px;
}

.score-basis {
  margin-bottom: 16px;
  padding: 11px 12px;
  border-radius: 12px;
  background: rgba(205, 106, 67, 0.06);
  border: 1px solid #f7e8e2;
}
.score-basis-title {
  display: block;
  font-size: 12px;
  font-weight: 900;
  color: #cd6a43;
  margin-bottom: 5px;
}
.score-basis-text {
  display: block;
  font-size: 12px;
  line-height: 1.5;
  color: #57534e;
}

.skill-bars { display: flex; flex-direction: column; gap: 20px; }

.skill-row { display: flex; align-items: center; gap: 12px; }

.skill-name { width: 75px; font-size: 14px; font-weight: 600; color: #1c1c1e; }

.bar-bg { flex: 1; height: 8px; background-color: #f8f3f1; border-radius: 4px; overflow: hidden; }

.bar-fill { height: 100%; border-radius: 4px; }

.fill-logic { background: linear-gradient(90deg, #d47f5d, #dd987d); }
.fill-creative { background: linear-gradient(90deg, #db9377, #e6b39f); }
.fill-exec { background: linear-gradient(90deg, #957534, #bf9748); }
.fill-team { background: linear-gradient(90deg, #f59e0b, #fbbf24); }
.fill-pressure { background: linear-gradient(90deg, #ef4444, #f87171); }

.skill-score { width: 24px; font-size: 14px; font-weight: 700; color: #000000; text-align: right; }

.analysis-card { margin-bottom: 24px; }

.paragraph { display: flex; flex-direction: column; gap: 8px; }

.p-title { font-size: 16px; font-weight: 600; color: #1c1c1e; letter-spacing: -0.3px; }

.p-content { font-size: 15px; color: #636366; line-height: 1.6; }

.divider { height: 1px; background-color: #f8f3f1; margin: 20px 0; }

.bottom-action {
  position: fixed; bottom: 0; left: 0; right: 0;
  padding: 16px 20px calc(20px + env(safe-area-inset-bottom, 0px)) 20px;
  background: rgba(245, 245, 247, 0.92);
  backdrop-filter: blur(20px); -webkit-backdrop-filter: blur(20px);
  border-top: 0.5px solid rgba(60, 60, 67, 0.1);
  z-index: 300; display: flex; flex-direction: column; gap: 12px;
  pointer-events: auto;
}

/* Custom-rendered buttons (using <view>) so mp-weixin's native <button>
   defaults can't override our colours. */
.btn-primary {
  width: 100%; background-color: #cd6a43;
  border-radius: 16px;
  height: 52px;
  display: flex; align-items: center; justify-content: center;
  box-shadow: var(--shadow-card);
}
.btn-primary-text { color: #ffffff; font-size: 17px; font-weight: 700; }
.btn-primary:active { background-color: #c25c33; }

/* Hero CTA when an AI-suggested role exists -- gradient fill so it visually
   leads against the standard solid blue secondary action below it. */
.btn-primary.practice-cta {
  background: linear-gradient(135deg, #db9579 0%, #db9377 100%);
  box-shadow: var(--shadow-card);
}
.btn-primary.practice-cta:active { background: linear-gradient(135deg, #d37b58 0%, #d27855 100%); }

.result-container.is-dark .radar-card,
.result-container.is-dark .analysis-card,
.result-container.is-dark .loading-state,
.result-container.is-dark .error-state {
  background: #292524;
  border-color: #44403c;
  box-shadow: none;
}

.result-container.is-dark .section-title,
.result-container.is-dark .skill-name,
.result-container.is-dark .skill-score,
.result-container.is-dark .p-title {
  color: #fafaf9;
}

.result-container.is-dark .p-content {
  color: #d6d3d1;
}

.result-container.is-dark .bar-bg {
  background-color: #44403c;
}

.result-container.is-dark .divider {
  background-color: #44403c;
}

.result-container.is-dark .role-chip {
  background: rgba(205, 106, 67, 0.18);
  border-color: rgba(232, 186, 168, 0.32);
  color: #f1d6cc;
}

.result-container.is-dark .score-basis {
  background: rgba(205, 106, 67, 0.14);
  border-color: #824026;
}

.result-container.is-dark .score-basis-text {
  color: #a8a29e;
}

.result-container.is-dark .bottom-action {
  background: rgba(28, 25, 23, 0.96);
  border-top-color: #44403c;
}

/* When the practice CTA is present we tone down the map button so the eye
   prioritises the practice action without making the secondary disappear. */
.btn-primary.btn-secondary-tone {
  background-color: #ffffff;
  border: 1px solid #d6d3d1;
  box-shadow: none;
}
.btn-primary.btn-secondary-tone .btn-primary-text { color: #cd6a43; }
.btn-primary.btn-secondary-tone:active { background-color: #f5f5f4; }

.btn-secondary {
  width: 100%; height: 44px;
  display: flex; align-items: center; justify-content: center;
}
.btn-secondary-text { color: #cd6a43; font-size: 15px; font-weight: 600; }
.btn-secondary:active { opacity: 0.7; }

/* Loading + error */
.loading-state, .error-state {
  background: #ffffff; border: 1px solid var(--border-color, #d8c1b8);
  border-radius: 20px; padding: 60px 24px;
  display: flex; flex-direction: column; align-items: center; gap: 14px;
  margin-top: 12px;
}
.spinner {
  width: 32px; height: 32px;
  border: 3px solid #e7e5e4; border-top-color: #cd6a43;
  border-radius: 50%;
  animation: result-spin 0.9s linear infinite;
}
@keyframes result-spin { to { transform: rotate(360deg); } }
.loading-text { font-size: 14px; color: #57534e; }
.err-title { font-size: 15px; color: #b91c1c; font-weight: 600; }
.btn-retry {
  background: #cd6a43; height: 40px; padding: 0 24px;
  border-radius: 12px;
  display: flex; align-items: center;
}
.btn-retry-text { color: #fff; font-size: 14px; font-weight: 600; }

.is-dark { background-color: #1c1917; }

.is-dark .section-title,
.is-dark .p-title,
.is-dark .skill-name,
.is-dark .skill-score { color: #fafaf9; }

.is-dark .p-content,
.is-dark .result-subtitle { color: #a8a29e; }

/* ── CareerLoop editorial skin ─────────────────────────────────────────── */
.result-container {
  background: #faf9f6;
  color: #2c2b29;
  font-family: "Noto Sans SC", "PingFang SC", "Microsoft YaHei", sans-serif;
}

.result-header {
  padding: 34px 20px;
  border: 1px solid #e0dfdb;
  border-top: 1px solid var(--border-color, #e0dfdb);
  border-radius: 8px;
  background: #f5f5f0;
  color: #2c2b29;
  box-shadow: 0 8px 24px rgba(44, 43, 41, 0.06);
}

.result-subtitle {
  color: #8b8a86;
  opacity: 1;
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
  letter-spacing: 0.08em;
}

.result-title {
  color: #2c2b29;
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
  font-size: 34px;
  font-weight: 600;
  letter-spacing: 0.05em;
}

.tags-container {
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
}

.tag {
  padding: 5px 12px;
  border: 1px solid #d8d6cf;
  border-radius: 4px;
  background: #faf9f6;
  color: #5a5956;
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
}

.section-title,
.section-title-text,
.p-title {
  color: #2c2b29;
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
  font-weight: 600;
  letter-spacing: 0.05em;
}

.section-title {
  margin: 10px 0 14px;
  padding: 0 0 10px;
  border-bottom: 1px solid #edece8;
  font-size: 18px;
}

.section-title-text {
  font-size: 18px;
}

.ai-badge {
  padding: 3px 7px;
  border-radius: 3px;
  background: #ac6448;
  color: #fffdfa;
}

.radar-card,
.analysis-card,
.loading-state,
.error-state {
  border: 1px solid #e0dfdb;
  border-radius: 8px;
  background: #fffdfa;
  box-shadow: 0 8px 24px rgba(44, 43, 41, 0.05);
}

.score-basis {
  border: 1px solid rgba(184, 151, 90, 0.32);
  border-radius: 6px;
  background: #f6f1e7;
}

.score-basis-title {
  color: #9a783f;
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
  letter-spacing: 0.05em;
}

.score-basis-text,
.p-content {
  color: #5a5956;
  line-height: 1.75;
}

.skill-name,
.skill-score {
  color: #2c2b29;
}

.bar-bg {
  background: #efeee9;
}

.fill-logic { background: #ac6448; }
.fill-creative { background: #b8975a; }
.fill-exec { background: #8d846e; }
.fill-team { background: #9f6a55; }
.fill-pressure { background: #c23b22; }

.divider {
  background: #edece8;
}

.role-chip {
  padding: 5px 10px;
  border: 1px solid rgba(141, 132, 110, 0.45);
  border-radius: 4px;
  background: #f1efeb;
  color: #766f5d;
}

.bottom-action {
  gap: 10px;
  background: rgba(250, 249, 246, 0.97);
  border-top: 1px solid #e0dfdb;
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
}

.btn-primary {
  height: 50px;
  border: 1px solid #c23b22;
  border-radius: 6px;
  background: #c23b22;
  box-shadow: none;
}

.btn-primary.practice-cta {
  background: #c23b22;
  box-shadow: none;
}

.btn-primary.practice-cta:active,
.btn-primary:active {
  background: #a9321d;
}

.btn-primary-text,
.btn-secondary-text {
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
  font-weight: 600;
  letter-spacing: 0.05em;
}

.btn-primary.btn-secondary-tone {
  border-color: #ac6448;
  background: #faf9f6;
}

.btn-primary.btn-secondary-tone .btn-primary-text {
  color: #ac6448;
}

.btn-primary.btn-secondary-tone:active {
  background: #f7f1ef;
}

.btn-secondary-text {
  color: #5a5956;
}

.spinner {
  border-color: #e0dfdb;
  border-top-color: #c23b22;
}

.loading-text {
  color: #5a5956;
}

.btn-retry {
  border-radius: 6px;
  background: #c23b22;
}

.result-container.is-dark .result-header {
  border-color: #57534e;
  border-top-color: #e27b66;
  background: #292524;
  color: #fafaf9;
  box-shadow: none;
}

.result-container.is-dark .result-title {
  color: #fafaf9;
}

.result-container.is-dark .result-subtitle {
  color: #d6d3d1;
}

.result-container.is-dark .role-chip {
  border-color: rgba(197, 191, 176, 0.44);
  background: rgba(141, 132, 110, 0.22);
  color: #c5bfb0;
}

.result-container.is-dark .score-basis {
  border-color: rgba(223, 197, 143, 0.42);
  background: rgba(184, 151, 90, 0.18);
}

.result-container.is-dark .score-basis-title,
.result-container.is-dark .score-basis-text {
  color: #dfc58f;
}
</style>
