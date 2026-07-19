<template>
  <view class="map-page app-soft-bg" :class="[themeClass, fontClass]">
    <SlNavBar show-back @back="goBack" :safe-top="topSafeHeight" :right-avoid-width="rightAvoidWidth">
      <template #title>
        <view class="nav-title-wrap">
          <text class="nav-title">{{ t('map.navTitle') }}</text>
          <view class="nav-icon-btn" v-if="activeTab === 'map'" @click.stop="switchRole" hover-class="press-fb" hover-stay-time="120">
            <text class="ri-swap-line"></text>
          </view>
        </view>
      </template>
    </SlNavBar>

    <view class="map-content">
      <!-- Tab switcher -->
      <view class="tab-bar">
        <view class="tab-item" :class="{ 'tab-active': activeTab === 'map' }" @click="switchTab('map')">
          <text class="tab-text">{{ t('map.tabMap') }}</text>
        </view>
        <view class="tab-item" :class="{ 'tab-active': activeTab === 'plan' }" @click="switchTab('plan')">
          <text class="tab-text">{{ t('map.tabPlan') }}</text>
          <view class="tab-badge" v-if="plan"></view>
        </view>
      </view>

      <view v-if="activeTab === 'map'">
    <view class="page-intro">
      <text class="intro-title">{{ t('map.intro') }}</text>
      <text class="intro-text">{{ t('map.introText') }}</text>
    </view>

    <!-- Role header card -->
    <view class="role-card">
      <view class="role-info">
        <text class="role-name" @click="switchRole">{{ currentPathName || t('map.pickRole') }}</text>
        <text class="role-desc">{{ currentPathDesc || t('map.defaultRoleDesc') }}</text>
      </view>
      <view class="progress-ring">
        <text class="ring-val">{{ overallPercent }}%</text>
      </view>
    </view>

      <!-- Loading skeleton -->
      <view class="skeleton-list" v-if="loading">
        <view class="skel-card app-surface" v-for="i in 4" :key="i">
          <view class="skel-line skel-w40"></view>
          <view class="skel-line skel-w70"></view>
          <view class="skel-line skel-w90"></view>
        </view>
      </view>

      <view class="empty-state app-empty app-surface" v-else-if="routeError">
        <text class="empty-icon ri-wifi-off-line"></text>
        <text class="empty-text">{{ t('map.loadFailedTitle') }}</text>
        <text class="empty-sub">{{ routeError }}</text>
        <view class="map-retry-btn" @click="retryRoute"><text>{{ t('map.retry') }}</text></view>
      </view>

      <!-- Empty state -->
      <view class="empty-state app-empty app-surface" v-else-if="nodes.length === 0">
        <text class="empty-icon ri-map-2-line"></text>
        <text class="empty-text">{{ t('map.noRoadmap') }}</text>
        <text class="empty-sub">{{ t('map.noRoadmapSub') }}</text>
      </view>

      <!-- Real skill timeline -->
      <view class="timeline" v-else>
        <view class="tl-line"></view>
        <view
          class="tl-node"
          v-for="(node, idx) in displayNodes"
          :key="node.nodeId"
          @click="openDetail(node)"
        >
          <view class="tl-dot" :class="dotClass(node)">
            <text v-if="dotIcon(node).startsWith('ri-')" :class="dotIcon(node)"></text>
            <text v-else>{{ dotIcon(node) }}</text>
          </view>
          <view class="tl-card app-surface" :class="cardClass(node)">
            <text class="tl-level">{{ t('map.stageLabel', { level: node.level, stage: idx + 1 }) }}</text>
            <text class="tl-title">{{ careerText(node.name) }}</text>
            <text class="tl-desc" v-if="node.description">{{ careerText(node.description) }}</text>
            <view class="tl-meta-row">
              <text class="tl-meta" v-if="node.estimatedHours">约 {{ node.estimatedHours }} 小时</text>
              <view class="tl-badge" :class="badgeClass(node)">
                <text class="badge-text">{{ statusLabel(node) }}</text>
              </view>
            </view>
          </view>
        </view>
      </view>

      <view class="bottom-safe"></view>

      <!-- Detail sheet -->
      <view class="sheet-mask" v-if="showDetail" @click="showDetail = false"></view>
      <view class="detail-sheet app-surface" :class="{ 'sheet-open': showDetail }" v-if="selectedNode">
        <view class="sheet-handle"></view>
        <view class="sheet-header">
          <text class="sheet-title">{{ careerText(selectedNode.name) }}</text>
          <text class="sheet-mastery">{{ statusLabel(selectedNode) }} · 约 {{ selectedNode.estimatedHours || 10 }} 小时</text>
        </view>
        <view class="sheet-section">
          <text class="sheet-label">{{ t('map.sheetCovers') }}</text>
          <text class="sheet-advice">{{ careerText(selectedNode.description) || t('map.sheetDescriptionEmpty') }}</text>
        </view>
        <view class="sheet-section" v-if="prerequisiteName">
          <text class="sheet-label">{{ t('map.sheetPrereq') }}</text>
          <view class="topic-tags">
            <view class="topic-tag"><text class="topic-text">{{ prerequisiteName }}</text></view>
          </view>
        </view>
        <view
          class="sheet-btn"
          :class="{ 'sheet-btn-disabled': isLocked(selectedNode) }"
          @click="toggleNodeStatus(selectedNode)"
        ><text class="sheet-btn-text">{{ ctaLabel(selectedNode) }}</text></view>
      </view>
    </view><!-- end map tab -->

    <view v-if="activeTab === 'plan'" class="plan-tab">

      <!-- Loading -->
      <view class="plan-loading" v-if="planLoading">
        <view class="plan-load-spinner"></view>
        <text class="plan-load-text">{{ t('map.planLoading') }}</text>
        <text class="plan-load-sub">{{ t('map.planLoadingSub') }}</text>
      </view>

      <!-- No plan yet -->
      <view class="plan-empty" v-else-if="!plan">
        <text class="plan-empty-icon ri-map-2-line"></text>
        <text class="plan-empty-title">{{ t('map.planEmpty') }}</text>
        <text class="plan-empty-sub">{{ t('map.planEmptySub') }}</text>
        <view class="plan-role-input-wrap">
          <text class="plan-role-input-label">{{ t('map.planTargetRoleLabel') }}</text>
          <input
            class="plan-role-input"
            v-model="inputTargetRole"
            :placeholder="t('map.planTargetRolePlaceholder')"
            maxlength="60"
          />
        </view>
        <view class="plan-gen-btn" :class="{ 'plan-gen-btn-dim': !inputTargetRole.trim() }" @click="handleGenerate(inputTargetRole.trim() || undefined)">
          <text class="plan-gen-btn-text">{{ t('map.planGenerate') }}</text>
        </view>
      </view>

      <!-- Plan content -->
      <view v-else>

        <!-- Target role card -->
        <view class="plan-hero">
          <text class="plan-hero-label">{{ t('map.planTargetRole') }}</text>
          <text class="plan-hero-role">{{ plan.targetRole }}</text>
          <text class="plan-hero-meta">{{ t('map.planUpdatedAt') }} {{ formatDate(plan.lastUpdatedAt) }}</text>
          <view class="plan-regen-btn" @click="handleGenerate(plan.targetRole)">
            <text class="plan-regen-text">{{ t('map.planRegenerate') }}</text>
          </view>
        </view>

        <!-- Weekly focus -->
        <view class="plan-section" v-if="weeklyFocus.length">
          <text class="plan-section-title">{{ t('map.planWeeklyFocus') }}</text>
          <view class="focus-list">
            <view class="focus-item app-card-soft" v-for="(item, i) in weeklyFocus" :key="i">
              <view class="focus-dot">{{ i + 1 }}</view>
              <text class="focus-text">{{ item }}</text>
            </view>
          </view>
        </view>

        <!-- Milestones -->
        <view class="plan-section" v-if="milestones.length">
          <text class="plan-section-title">{{ t('map.planMilestones') }}</text>
          <view class="milestone-card app-card-soft" v-for="ms in milestones" :key="ms.horizon">
            <view class="ms-header">
              <view class="ms-horizon-badge">
                <text class="ms-horizon-text">{{ horizonLabel(ms.horizon) }}</text>
              </view>
              <text class="ms-title">{{ ms.title }}</text>
            </view>
            <view class="ms-body">
              <view class="ms-row" v-if="ms.actions && ms.actions.length">
                <text class="ms-row-label">{{ t('map.planActions') }}</text>
                <view class="ms-tag-row">
                  <view class="ms-tag ms-tag-action" v-for="a in ms.actions" :key="a">
                    <text class="ms-tag-text">{{ a }}</text>
                  </view>
                </view>
              </view>
              <view class="ms-row" v-if="ms.skills && ms.skills.length">
                <text class="ms-row-label">{{ t('map.planSkills') }}</text>
                <view class="ms-tag-row">
                  <view class="ms-tag ms-tag-skill" v-for="s in ms.skills" :key="s">
                    <text class="ms-tag-text">{{ s }}</text>
                  </view>
                </view>
              </view>
              <view class="ms-row" v-if="ms.kpis && ms.kpis.length">
                <text class="ms-row-label">{{ t('map.planKpis') }}</text>
                <view class="ms-tag-row">
                  <view class="ms-tag ms-tag-kpi" v-for="k in ms.kpis" :key="k">
                    <text class="ms-tag-text">{{ k }}</text>
                  </view>
                </view>
              </view>
            </view>
          </view>
        </view>

        <view class="bottom-safe"></view>
      </view>
    </view><!-- end plan tab -->

    </view><!-- end map-content -->
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useI18n } from '@/locales';
import { onShow } from '@dcloudio/uni-app';
import {
  getCareerPathsApi,
  getPathNodesApi,
  getUserProgressApi,
  unlockNodeApi,
  completeNodeApi,
  getCurrentCareerPlanApi,
  generateCareerPlanApi,
  type CareerPath,
  type CareerNode,
  type UserCareerProgress,
  type UserCareerPlan,
  type CareerMilestone,
} from '@/api/career';
import { getProfileSnapshotApi } from '@/api/user';
import { useTheme } from '@/utils/theme';
import { getMpSafeAreaMetrics } from '@/utils/safeArea';
import { isRealUser, requireAuth } from '@/utils/auth';
import SlNavBar from '@/style-library/components/SlNavBar.vue';

// ── shared ─────────────────────────────────────────────────────────────────────────────────
const { t } = useI18n();
const { themeClass, fontClass, refresh: refreshTheme } = useTheme();
const topSafeHeight = ref(44);
const rightAvoidWidth = ref(20);
const activeTab    = ref<'map' | 'plan'>(isRealUser() ? 'plan' : 'map');

const switchTab = (tab: 'map' | 'plan') => {
  if (tab === 'plan' && !isRealUser()) {
    requireAuth({ message: t('map.planLoginRequired') });
    return;
  }
  activeTab.value = tab;
  if (tab === 'plan' && !plan.value) loadPlan();
};

// ── route tab ─────────────────────────────────────────────────────────────
const showDetail = ref(false);
const loading    = ref(true);
const routeError = ref('');

type RouteNode = CareerNode & {
  source?: 'PLAN' | 'TEMPLATE';
  dynamicKey?: string;
  horizon?: string;
};

const paths = ref<CareerPath[]>([]);
const currentPath = ref<CareerPath | null>(null);
const nodes = ref<RouteNode[]>([]);
const progress = ref<UserCareerProgress[]>([]);
const selectedNode = ref<RouteNode | null>(null);

const CAREER_TEXT_ZH: Record<string, string> = {
  'Java Backend Engineer': 'Java 后端工程师',
  'Become an excellent Java backend developer, mastering core skills such as Spring Boot, microservices, and databases.': '成为优秀的 Java 后端开发者，掌握 Spring Boot、微服务、数据库等核心技能。',
  'Frontend Engineer': '前端工程师',
  'Become a modern frontend developer, mastering tech stacks like Vue, React, and TypeScript.': '成为现代前端开发者，掌握 Vue、React、TypeScript 等主流技术栈。',
  'Java Basics': 'Java 基础',
  'Spring Boot Intro': 'Spring Boot 入门',
  'Database Design': '数据库设计',
  'Spring Cloud Microservices': 'Spring Cloud 微服务',
  'HTML/CSS Basics': 'HTML/CSS 基础',
  'JavaScript Core': 'JavaScript 核心',
  'Vue.js Framework': 'Vue.js 框架',
  'Master variables, control flow, OOP, collections, generics, and exception handling. The bedrock for everything that follows.': '掌握变量、流程控制、面向对象、集合、泛型和异常处理，这是后续学习的基础。',
  'Build REST APIs with Spring Boot. Cover dependency injection, MVC, JPA, transactions, and basic error handling.': '使用 Spring Boot 构建 REST API，覆盖依赖注入、MVC、JPA、事务和基础错误处理。',
  'Design normalized schemas, write efficient SQL, understand indexes, transactions, and isolation levels.': '设计规范化表结构，编写高效 SQL，理解索引、事务和隔离级别。',
  'Service discovery, gateway, circuit breakers, distributed config, and how microservices communicate at scale.': '学习服务发现、网关、熔断、分布式配置，以及微服务在规模化场景下的通信方式。',
  'Semantic markup, modern CSS layouts (flexbox + grid), responsive design, and accessibility fundamentals.': '学习语义化标签、现代 CSS 布局、响应式设计和基础可访问性。',
  'ES2015+, async/await, the event loop, modules, and the DOM API. Skip nothing here.': '掌握 ES2015+、async/await、事件循环、模块系统和 DOM API。',
  'Composition API, reactivity, single-file components, Pinia state, Vue Router, and build tooling with Vite.': '学习 Composition API、响应式系统、单文件组件、Pinia、Vue Router 和 Vite 构建工具。',
};

const careerText = (text?: string | null) => {
  if (!text) return '';
  return CAREER_TEXT_ZH[text] || text;
};

const currentPathName = computed(() => careerText(currentPath.value?.name));
const currentPathDesc = computed(() => careerText(currentPath.value?.description));
const isPersonalizedRoute = computed(() => currentPath.value?.code === 'personalized-plan');

/**
 * Order nodes the way the timeline reads top-to-bottom: by level first,
 * then by sortOrder, then nodeId. Backend already sorts by level, but
 * we re-sort defensively in case multiple levels share an order.
 */
const displayNodes = computed(() =>
  [...nodes.value].sort((a, b) => {
    if (a.level !== b.level) return a.level - b.level;
    return (a.sortOrder ?? 0) - (b.sortOrder ?? 0) || (a.nodeId ?? 0) - (b.nodeId ?? 0);
  }),
);

const progressByNode = computed(() => {
  const map = new Map<number, UserCareerProgress>();
  progress.value.forEach((p) => map.set(p.nodeId, p));
  return map;
});

/**
 * Status of a node for the current user. We treat parent_id as a
 * prerequisite: a node is LOCKED until its parent is COMPLETED.
 */
const nodeStatus = (n: RouteNode): 'COMPLETED' | 'IN_PROGRESS' | 'UNLOCKED' | 'LOCKED' => {
  const p = progressByNode.value.get(n.nodeId ?? -1);
  if (p?.status === 'COMPLETED') return 'COMPLETED';
  if (p?.status === 'UNLOCKED') return 'IN_PROGRESS';
  // No parent OR parent already completed -> available to start.
  if (!n.parentId || n.parentId === 0) return 'UNLOCKED';
  const parent = nodes.value.find((x) => x.nodeId === n.parentId);
  const parentDone = parent && progressByNode.value.get(parent.nodeId ?? -1)?.status === 'COMPLETED';
  return parentDone ? 'UNLOCKED' : 'LOCKED';
};

const isLocked = (n: RouteNode | null) => !!n && nodeStatus(n) === 'LOCKED';

const dotClass = (n: RouteNode) => {
  const s = nodeStatus(n);
  if (s === 'COMPLETED') return 'dot-done';
  if (s === 'IN_PROGRESS') return 'dot-active';
  if (s === 'LOCKED') return 'dot-locked';
  return 'dot-ready';
};
const dotIcon = (n: RouteNode) => {
  const s = nodeStatus(n);
  if (s === 'COMPLETED') return 'ri-check-line';
  if (s === 'IN_PROGRESS') return '…';
  if (s === 'LOCKED') return 'ri-lock-line';
  return '●';
};
const cardClass = (n: RouteNode) => {
  const s = nodeStatus(n);
  if (s === 'COMPLETED') return 'card-done';
  if (s === 'IN_PROGRESS') return 'card-active';
  if (s === 'LOCKED') return 'card-locked';
  return 'card-ready';
};
const badgeClass = (n: RouteNode) => {
  const s = nodeStatus(n);
  if (s === 'COMPLETED') return 'badge-done';
  if (s === 'IN_PROGRESS') return 'badge-active';
  if (s === 'LOCKED') return 'badge-locked';
  return 'badge-ready';
};
const statusLabel = (n: RouteNode) => {
  switch (nodeStatus(n)) {
    case 'COMPLETED':   return t('map.statusCompleted');
    case 'IN_PROGRESS': return t('map.statusInProgress');
    case 'LOCKED':      return t('map.statusLocked');
    default:            return t('map.statusAvailable');
  }
};
const ctaLabel = (n: RouteNode) => {
  switch (nodeStatus(n)) {
    case 'COMPLETED':   return t('map.ctaMarkInProgress');
    case 'IN_PROGRESS': return t('map.ctaMarkCompleted');
    case 'LOCKED':      return t('map.ctaLocked');
    default:            return t('map.ctaStart');
  }
};

const overallPercent = computed(() => {
  if (nodes.value.length === 0) return 0;
  const done = nodes.value.filter((n) => nodeStatus(n) === 'COMPLETED').length;
  return Math.round((done / nodes.value.length) * 100);
});

const prerequisiteName = computed(() => {
  const n = selectedNode.value;
  if (!n || !n.parentId || n.parentId === 0) return '';
  return careerText(nodes.value.find((x) => x.nodeId === n.parentId)?.name);
});

const openDetail = (node: RouteNode) => {
  selectedNode.value = node;
  showDetail.value = true;
};

const goBack = () => uni.navigateBack({ delta: 1 });

const getUid = (): number => {
  const v = uni.getStorageSync('userId');
  const n = Number(v);
  return !isNaN(n) && n > 0 ? n : 0;
};

/**
 * The unlock endpoint creates an UNLOCKED row; complete promotes it to
 * COMPLETED. We treat the CTA as a 3-state cycle: ready -> unlocked ->
 * completed -> back to unlocked. Locked nodes are no-ops.
 */
const toggleNodeStatus = async (node: RouteNode) => {
  if (isLocked(node)) return;
  if (!isRealUser()) {
    requireAuth({ message: t('map.progressLoginRequired') });
    return;
  }
  const uid = getUid();
  if (!uid) {
    uni.showToast({ title: t('map.toastSignIn'), icon: 'none' });
    return;
  }
  const status = nodeStatus(node);
  try {
    if (status === 'IN_PROGRESS') {
      await completeNodeApi(uid, node.nodeId!);
      uni.showToast({ title: t('map.toastCompleted'), icon: 'success' });
    } else if (status === 'COMPLETED') {
      // Re-unlock to revisit; backend just updates the row's status.
      await unlockNodeApi(uid, node.nodeId!);
      uni.showToast({ title: t('map.toastReopened'), icon: 'none' });
    } else {
      await unlockNodeApi(uid, node.nodeId!);
      uni.showToast({ title: t('map.toastStarted'), icon: 'success' });
    }
    await refreshProgress();
  } catch (e: any) {
    uni.showToast({ title: e?.message || t('map.toastFail'), icon: 'none' });
  }
  showDetail.value = false;
};

const refreshProgress = async () => {
  const uid = getUid();
  if (!uid) {
    progress.value = [];
    return;
  }
  try {
    progress.value = (await getUserProgressApi(uid)) || [];
  } catch { progress.value = []; }
};

const loadPath = async (path: CareerPath) => {
  currentPath.value = path;
  loading.value = true;
  routeError.value = '';
  try {
    const [nodeList, _] = await Promise.all([
      getPathNodesApi(path.pathId!),
      refreshProgress(),
    ]);
    nodes.value = nodeList || [];
  } catch (e: any) {
    routeError.value = e?.message || t('map.loadRoadmapFailed');
  } finally {
    loading.value = false;
  }
};

const buildPersonalizedPath = (): CareerPath | null => {
  if (!plan.value) return null;
  return {
    pathId: -1,
    code: 'personalized-plan',
    name: plan.value.targetRole ? `${plan.value.targetRole}求职路线` : '我的求职路线',
    description: '根据你的画像、长期目标和本周重点动态生成，重新生成职业计划后会同步变化。',
  };
};

const joinParts = (label: string, items?: string[]) => {
  const clean = (items || []).map((item) => String(item || '').trim()).filter(Boolean).slice(0, 3);
  return clean.length ? `${label}：${clean.join('；')}` : '';
};

const stablePlanNodeId = (key: string) => {
  let hash = 0;
  for (let i = 0; i < key.length; i++) {
    hash = ((hash << 5) - hash + key.charCodeAt(i)) | 0;
  }
  return -1000000000 - Math.abs(hash % 900000000);
};

const buildPlanRouteNodes = (): RouteNode[] => {
  if (!plan.value) return [];
  const routeNodes: RouteNode[] = [];
  let previousId = 0;
  const planKey = `${plan.value.userId || getUid() || 'guest'}:${plan.value.version || plan.value.lastUpdatedAt || 'draft'}:${plan.value.targetRole || ''}`;

  weeklyFocus.value.slice(0, 3).forEach((item, idx) => {
    const dynamicKey = `${planKey}:week:${idx}:${item}`;
    const nodeId = stablePlanNodeId(dynamicKey);
    routeNodes.push({
      nodeId,
      pathId: -1,
      name: item,
      level: 1,
      parentId: previousId,
      sortOrder: idx,
      estimatedHours: 2,
      description: '本周优先行动，完成后会推动今日任务和长期路线对齐。',
      source: 'PLAN',
      dynamicKey,
    });
    previousId = nodeId;
  });

  milestones.value.forEach((ms, idx) => {
    const dynamicKey = `${planKey}:milestone:${ms.horizon}:${idx}:${ms.title || ''}`;
    const nodeId = stablePlanNodeId(dynamicKey);
    const desc = [
      joinParts('行动', ms.actions),
      joinParts('技能', ms.skills),
      joinParts('衡量', ms.kpis),
    ].filter(Boolean).join('\n');
    routeNodes.push({
      nodeId,
      pathId: -1,
      name: ms.title || horizonLabel(ms.horizon),
      level: idx + 2,
      parentId: previousId,
      sortOrder: idx + 10,
      estimatedHours: Math.max(8, (ms.actions?.length || 1) * 4 + (ms.skills?.length || 0) * 3),
      description: desc || `${horizonLabel(ms.horizon)}阶段目标`,
      source: 'PLAN',
      dynamicKey,
      horizon: ms.horizon,
    });
    previousId = nodeId;
  });

  return routeNodes;
};

const applyPersonalizedRoute = async () => {
  const path = buildPersonalizedPath();
  if (!path) return false;
  currentPath.value = path;
  nodes.value = buildPlanRouteNodes();
  await refreshProgress();
  loading.value = false;
  return nodes.value.length > 0;
};

const switchRole = async () => {
  if (paths.value.length === 0) {
    try { paths.value = await getCareerPathsApi(); } catch { paths.value = []; }
  }
  const personalized = buildPersonalizedPath();
  const options = personalized ? [personalized, ...paths.value] : paths.value;
  if (options.length === 0) return;
  uni.showActionSheet({
    itemList: options.map((p) => careerText(p.name)),
    success: async (res) => {
      const picked = options[res.tapIndex];
      if (!picked) return;
      if (picked.code === 'personalized-plan') await applyPersonalizedRoute();
      else await loadPath(picked);
    }
  });
};

const loadAll = async (preferredPathId?: number, preferredRole?: string) => {
  loading.value = true;
  routeError.value = '';
  try {
    if (isRealUser() && !preferredPathId && !preferredRole) {
      await loadPlan();
      if (await applyPersonalizedRoute()) return;
    }
    paths.value = await getCareerPathsApi();
    if (paths.value.length === 0) {
      loading.value = false;
      return;
    }
    // Selection priority:
    //   1. explicit pathId from query (e.g. tapped a card on Home)
    //   2. assessment hint stored after a quiz
    //   3. first path in list
    let preferred: CareerPath | undefined;
    if (preferredPathId) {
      preferred = paths.value.find((p) => p.pathId === preferredPathId);
    }
    if (!preferred) {
      const hint = preferredRole || uni.getStorageSync('assessment_recommended_role');
      const normalizedHint = String(hint || '').trim().toLowerCase();
      preferred = paths.value.find((p) =>
        normalizedHint && (
          p.name?.toLowerCase().includes(normalizedHint)
          || normalizedHint.includes(String(p.name || '').toLowerCase())
          || p.code?.toLowerCase() === normalizedHint
        ),
      );
    }
    await loadPath(preferred || paths.value[0]);
  } catch (e: any) {
    routeError.value = e?.message || t('map.loadPathsFailed');
    loading.value = false;
  }
};

const retryRoute = () => {
  if (currentPath.value && currentPath.value.pathId && currentPath.value.pathId > 0) {
    loadPath(currentPath.value);
    return;
  }
  loadAll();
};

onMounted(() => {
  refreshTheme();
  const safeMetrics = getMpSafeAreaMetrics();
  topSafeHeight.value = safeMetrics.topSafeHeight;
  rightAvoidWidth.value = safeMetrics.rightAvoidWidth;
  const pages = getCurrentPages();
  const opts = (pages[pages.length - 1] as any).options || {};
  const queryPathId = opts.pathId ? parseInt(opts.pathId) : undefined;
  const fromAssessment = opts.from === 'assessment';
  const queryRole = typeof opts.role === 'string' ? decodeURIComponent(opts.role) : '';
  if ((queryPathId && !isNaN(queryPathId)) || fromAssessment) activeTab.value = 'map';
  loadAll(
    queryPathId && !isNaN(queryPathId) ? queryPathId : undefined,
    fromAssessment ? queryRole : undefined,
  );
});

// Re-pull progress when the page becomes visible -- if a user marks a
// node from a different page in the future, the timeline stays in sync.
onShow(() => {
  refreshTheme();
  refreshProgress();
  if (isRealUser() && activeTab.value === 'plan' && !plan.value) {
    loadPlan();
  }
});

// ── career plan ──────────────────────────────────────────────────────────────
const plan           = ref<UserCareerPlan | null>(null);
const planLoading    = ref(false);
const inputTargetRole = ref('');
const milestones  = computed<CareerMilestone[]>(() => {
  if (!plan.value?.milestonesJson) return [];
  try { return JSON.parse(plan.value.milestonesJson) as CareerMilestone[]; }
  catch { return []; }
});
const weeklyFocus = computed<string[]>(() => {
  if (!plan.value?.weeklyFocusJson) return [];
  try { return JSON.parse(plan.value.weeklyFocusJson) as string[]; }
  catch { return []; }
});

const loadPlan = async () => {
  if (!isRealUser()) return;
  try {
    plan.value = await getCurrentCareerPlanApi();
  } catch { /* silent — empty state handles it */ }
  // Pre-fill the target role input from snapshot so the user can start
  // generating immediately without retyping what they've already told us.
  if (!inputTargetRole.value) {
    try {
      const snap = await getProfileSnapshotApi();
      const role = snap?.preferences?.targetRole
        || snap?.resume?.targetJob
        || snap?.interview?.positionName;
      if (role) inputTargetRole.value = role;
    } catch { /* best-effort */ }
  }
};

const handleGenerate = async (targetRole?: string) => {
  if (!requireAuth({ message: t('map.planLoginRequired') })) return;
  if (!targetRole || !targetRole.trim()) {
    uni.showToast({ title: '请先填写目标岗位', icon: 'none' });
    return;
  }
  planLoading.value = true;
  plan.value = null;
  try {
    plan.value = await generateCareerPlanApi(targetRole.trim());
    await applyPersonalizedRoute();
    uni.showToast({ title: t('map.planDone'), icon: 'success' });
  } catch (e: any) {
    uni.showToast({ title: e?.message || t('map.planFail'), icon: 'none' });
  } finally {
    planLoading.value = false;
  }
};

/** Convert raw horizon values like "3m", "6m", "1y", "2y" to readable labels. */
const horizonLabel = (h: string): string => {
  if (!h) return h;
  const m = h.match(/^(\d+)(m|y)$/i);
  if (!m) return h;
  const n = parseInt(m[1]);
  const unit = m[2].toLowerCase();
  return unit === 'm'
    ? t('map.horizonMonths').replace('{n}', String(n))
    : t('map.horizonYears').replace('{n}', String(n));
};

const formatDate = (iso?: string) => {
  if (!iso) return '';
  const d = new Date(iso.replace(' ', 'T'));
  return `${d.getFullYear()}/${d.getMonth() + 1}/${d.getDate()}`;
};
</script>

<style scoped>
.map-page {
  min-height: 100vh;
  background: var(--surface-1, #ffffff);
  font-family: -apple-system, BlinkMacSystemFont, "SF Pro Text", "Helvetica Neue", sans-serif;
}

.map-content {
  padding: 12px 20px 0;
  box-sizing: border-box;
}

.page-intro {
  margin-bottom: 18px;
}

.intro-title {
  display: block;
  font-size: 28px;
  line-height: 1.12;
  font-weight: 800;
  color: #1c1917;
}

.intro-text {
  display: block;
  margin-top: 8px;
  font-size: 14px;
  line-height: 1.5;
  color: #57534e;
}

.nav-right { display: flex; justify-content: flex-end; align-items: center; }

.nav-title-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  pointer-events: auto;
}

.nav-title {
  font-size: var(--font-section, 17px);
  font-weight: 700;
  color: var(--text-primary, #1c1917);
  letter-spacing: -0.3px;
}

.nav-icon-btn {
  font-size: 16px;
  color: var(--primary-color, #cd6a43);
  background: var(--primary-soft, #fcf5f2);
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 13px;
}

.role-card {
  background: linear-gradient(135deg, #292524 0%, #44403c 100%);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 20px; padding: 24px; color: #ffffff;
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 16px; box-shadow: var(--shadow-card);
}

.role-info { flex: 1; }

.role-name { font-size: 20px; font-weight: 700; display: block; margin-bottom: 6px; }

.role-desc { font-size: 13px; opacity: 0.7; }

.progress-ring {
  width: 56px; height: 56px; border-radius: 28px;
  border: 3px solid #dd987d; display: flex;
  justify-content: center; align-items: center;
  background: rgba(221, 152, 125, 0.1);
}

.ring-val { font-size: 16px; font-weight: 700; color: #dd987d; }

.demo-notice {
  background: #ffffff;
  border: 1px solid rgba(205, 106, 67, 0.14);
  border-radius: 12px; padding: 10px 14px;
  margin-bottom: 20px;
}

.demo-text { font-size: 12px; color: #cd6a43; }

/* Timeline */
.timeline { position: relative; padding-left: 28px; padding-bottom: 20px; }

.tl-line {
  position: absolute; left: 12px; top: 20px; bottom: 20px;
  width: 2px; background: #e7e5e4;
}

.tl-node { position: relative; margin-bottom: 24px; }

.tl-dot {
  position: absolute; left: -28px; top: 16px;
  width: 28px; height: 28px; border-radius: 14px;
  display: flex; justify-content: center; align-items: center;
  font-size: 14px; z-index: 2;
  background: #ffffff;
}

.dot-done { background: #f5efe3; }

.dot-active {
  background: #f7e8e2; position: relative;
}

.pulse-ring {
  position: absolute; width: 36px; height: 36px;
  border-radius: 18px; border: 2px solid #d47f5d;
  animation: pulse 2s ease-in-out infinite;
}

@keyframes pulse {
  0% { transform: scale(0.8); opacity: 1; }
  100% { transform: scale(1.4); opacity: 0; }
}

.dot-locked { background: #f5f5f4; opacity: 0.6; }

.tl-card {
  background: #ffffff;
  border: 1px solid var(--border-color, #d8c1b8);
  border-radius: 16px;
  padding: 18px;
  box-shadow: var(--shadow-sm, 0 4px 16px rgba(0,0,0,0.12), 0 2px 6px rgba(0,0,0,0.08));
}

.card-done { border-left: 3px solid #ab863c; }

.card-active { border-left: 3px solid #d47f5d; box-shadow: var(--shadow-sm); }

.card-locked { opacity: 0.5; }

.tl-level { font-size: 11px; font-weight: 600; color: #a8a29e; display: block; margin-bottom: 4px; text-transform: uppercase; letter-spacing: 0.5px; }

.tl-title { font-size: 17px; font-weight: 700; color: #292524; display: block; margin-bottom: 6px; }

.tl-desc { font-size: 13px; color: #78716c; line-height: 1.5; display: block; margin-bottom: 10px; }

.tl-badge { display: inline-flex; padding: 3px 10px; border-radius: 8px; }

.badge-done { background: #f5efe3; }
.badge-done .badge-text { color: #896b30; font-size: 11px; font-weight: 600; }

.badge-locked { background: #f5f5f4; }
.badge-locked .badge-text { color: #a8a29e; font-size: 11px; font-weight: 600; }

.tl-progress { display: flex; align-items: center; gap: 10px; }

.tl-progress-bg { flex: 1; height: 6px; background: #e7e5e4; border-radius: 3px; overflow: hidden; }

.tl-progress-fill { height: 100%; background: #d47f5d; border-radius: 3px; }

.tl-progress-num { font-size: 12px; font-weight: 600; color: #d47f5d; }

.bottom-safe { height: 40px; }

/* Detail sheet */
.sheet-mask {
  position: fixed; top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0, 0, 0, 0.35); z-index: 998;
}

.detail-sheet {
  position: fixed; left: 0; right: 0; bottom: -450px;
  background: #ffffff; border-radius: 24px 24px 0 0;
  padding: 12px 24px calc(24px + env(safe-area-inset-bottom, 0px));
  z-index: 999; transition: bottom 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
  max-height: 70vh; overflow-y: auto;
}

.sheet-open { bottom: 0; }

.sheet-handle {
  width: 36px; height: 5px; border-radius: 3px;
  background: #e7e5e4; margin: 0 auto 16px;
}

.sheet-header { margin-bottom: 20px; }

.sheet-title { font-size: 22px; font-weight: 700; color: #1c1917; display: block; margin-bottom: 4px; }

.sheet-mastery { font-size: 14px; color: #78716c; }

.sheet-section { margin-bottom: 20px; }

.sheet-label { font-size: 13px; font-weight: 600; color: #a8a29e; text-transform: uppercase; letter-spacing: 0.5px; display: block; margin-bottom: 10px; }

.topic-tags { display: flex; flex-wrap: wrap; gap: 8px; }

.topic-tag { background: #fcf5f2; padding: 6px 14px; border-radius: 12px; }

.topic-text { font-size: 13px; font-weight: 500; color: #cd6a43; }

.sheet-advice { font-size: 14px; color: #57534e; line-height: 1.6; }

.sheet-btn {
  width: 100%; background: #cd6a43;
  border-radius: 14px;
  height: 48px;
  display: flex; align-items: center; justify-content: center;
  margin-top: 8px;
  box-shadow: var(--shadow-card);
  transition: background 0.15s;
}
.sheet-btn-text { color: #ffffff; font-size: 16px; font-weight: 700; }
.sheet-btn:active { background: #c25c33; }
.sheet-btn-disabled { background: #d6d3d1; box-shadow: none; }
.sheet-btn-disabled .sheet-btn-text { color: #ffffff; opacity: 0.85; }

/* Dot + card variants for the new "ready" status (parent done, not yet started) */
.dot-ready { background: #fcf5f2; color: #cd6a43; font-weight: 800; }
.card-ready { border-left: 3px solid #f3dbd2; }

/* Inline meta row (estimated hours + status badge) on each timeline card */
.tl-meta-row { display: flex; align-items: center; gap: 10px; margin-top: 10px; }
.tl-meta {
  font-size: 11px; color: #78716c; font-weight: 600;
  background: #f5f5f4; padding: 3px 8px; border-radius: 999px;
}

/* Status badges */
.tl-badge { padding: 3px 10px; border-radius: 999px; }
.badge-text { font-size: 11px; font-weight: 700; }
.badge-done   { background: #f5efe3; }
.badge-done   .badge-text { color: #6e5627; }
.badge-active { background: #f7e8e2; }
.badge-active .badge-text { color: #c25c33; }
.badge-locked { background: #f5f5f4; }
.badge-locked .badge-text { color: #a8a29e; }
.badge-ready  { background: #fcf5f2; }
.badge-ready  .badge-text { color: #cd6a43; }

/* Skeleton */
.skeleton-list { display: flex; flex-direction: column; gap: 12px; margin-top: 4px; }
.skel-card {
  background: #ffffff; border: 1px solid var(--border-color, #d8c1b8); border-radius: 16px;
  padding: 18px; display: flex; flex-direction: column; gap: 8px;
}
.skel-line {
  height: 12px; border-radius: 6px;
  background: linear-gradient(90deg, #f6f1ef 0%, #fcf9f7 50%, #f6f1ef 100%);
  background-size: 200% 100%;
  animation: skel-shimmer 1.4s infinite;
}
.skel-w40 { width: 40%; }
.skel-w70 { width: 70%; }
.skel-w90 { width: 90%; }
@keyframes skel-shimmer {
  0%   { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

/* Empty state */
.empty-state {
  text-align: center; padding: 60px 20px;
  background: #ffffff; border: 1px solid var(--border-color, #d8c1b8);
  border-radius: 20px;
}
.empty-icon { font-size: 48px; display: block; margin-bottom: 12px; }
.empty-text { font-size: 16px; font-weight: 700; color: #57534e; display: block; margin-bottom: 8px; }
.empty-sub { font-size: 13px; color: #a8a29e; line-height: 1.5; }

/* Dark mode */
.is-dark { background: #1c1917; }

.is-dark .intro-title,
.is-dark .tl-title,
.is-dark .sheet-title { color: #fafaf9; }

.is-dark .intro-text { color: #a8a29e; }

.is-dark .tl-card,
.is-dark .detail-sheet { background: #292524; border-color: #44403c; }

.is-dark .tl-desc,
.is-dark .sheet-advice { color: #a8a29e; }

.is-dark .demo-notice { background: #292524; }

.is-dark .nav-icon-btn {
  background: rgba(205, 106, 67, 0.2);
  color: #e8baa8;
}
.is-dark .tl-line { background: #44403c; }
.is-dark .tl-dot {
  border: 1px solid #57534e;
  color: #d6d3d1;
}
.is-dark .dot-done {
  background: rgba(149, 117, 52, 0.22);
  border-color: rgba(191, 151, 72, 0.45);
  color: #d3b882;
}
.is-dark .dot-active,
.is-dark .dot-ready {
  background: rgba(205, 106, 67, 0.24);
  border-color: rgba(232, 186, 168, 0.48);
  color: #f1d6cc;
}
.is-dark .dot-locked {
  background: #292524;
  border-color: #44403c;
  color: #78716c;
  opacity: 1;
}
.is-dark .tl-meta {
  background: #44403c;
  color: #d6d3d1;
}

/* ── Tab bar ─────────────────────────────────────────────────────────────── */
.tab-bar {
  display: flex; background: #f5f5f4; border-radius: 12px;
  padding: 4px; margin-bottom: 20px; gap: 4px;
}
.tab-item {
  flex: 1; text-align: center; padding: 8px 0; border-radius: 9px;
  display: flex; align-items: center; justify-content: center; gap: 5px;
  transition: background 0.15s;
  position: relative;
}
.tab-active { background: #ffffff; box-shadow: var(--shadow-xs); }
.tab-text { font-size: 14px; font-weight: 600; color: #78716c; }
.tab-active .tab-text { color: #1c1917; }
.tab-badge {
  width: 7px; height: 7px; border-radius: 50%;
  background: #cd6a43;
}

/* ── AI Plan tab ─────────────────────────────────────────────────────────── */
.plan-tab { padding-bottom: 40px; }

/* Loading */
.plan-loading {
  display: flex; flex-direction: column; align-items: center;
  padding: 60px 20px; gap: 14px;
}
.plan-load-spinner {
  width: 40px; height: 40px; border-radius: 50%;
  border: 3px solid #e7e5e4; border-top-color: #cd6a43;
  animation: spin 0.9s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }
.plan-load-text { font-size: 16px; font-weight: 700; color: #1c1917; }
.plan-load-sub  { font-size: 13px; color: #a8a29e; text-align: center; line-height: 1.5; }

/* Empty */
.plan-empty {
  display: flex; flex-direction: column; align-items: center;
  text-align: center; padding: 56px 20px;
  background: #ffffff; border: 1px solid #e7e5e4; border-radius: 24px;
}
.plan-empty-icon  { font-size: 52px; margin-bottom: 16px; }
.plan-empty-title { font-size: 18px; font-weight: 700; color: #292524; display: block; margin-bottom: 8px; }
.plan-empty-sub   { font-size: 13px; color: #78716c; line-height: 1.6; margin-bottom: 20px; }

/* Target role input in empty state */
.plan-role-input-wrap {
  width: 100%; margin-bottom: 20px;
  display: flex; flex-direction: column; align-items: flex-start; gap: 8px;
}
.plan-role-input-label {
  font-size: 13px; font-weight: 600; color: #57534e;
}
.plan-role-input {
  width: 100%; box-sizing: border-box;
  border: 1.5px solid #e7e5e4; border-radius: 12px;
  padding: 12px 14px; font-size: 14px; color: #1c1917;
  background: #fafaf9;
}
.plan-role-input:focus { border-color: #cd6a43; background: #ffffff; }

.plan-gen-btn {
  background: linear-gradient(135deg, #cd6a43 0%, #d27855 100%);
  border-radius: 14px; padding: 0 32px; height: 48px;
  display: flex; align-items: center; justify-content: center;
  box-shadow: var(--shadow-card);
}
.plan-gen-btn:active { opacity: 0.85; }
.plan-gen-btn-dim { opacity: 0.55; box-shadow: none; }
.plan-gen-btn-text { color: #ffffff; font-size: 16px; font-weight: 700; }

/* Hero card */
.plan-hero {
  background: linear-gradient(135deg, #292524 0%, #a24d2b 100%);
  border-radius: 22px; padding: 24px; color: #fff; margin-bottom: 16px;
  box-shadow: var(--shadow-card);
}
.plan-hero-label { font-size: 11px; font-weight: 700; letter-spacing: 1px; text-transform: uppercase; opacity: 0.65; display: block; margin-bottom: 6px; }
.plan-hero-role  { font-size: 22px; font-weight: 800; display: block; margin-bottom: 6px; line-height: 1.2; }
.plan-hero-meta  { font-size: 12px; opacity: 0.55; display: block; margin-bottom: 14px; }
.plan-regen-btn {
  display: inline-flex; align-items: center; justify-content: center;
  background: rgba(255,255,255,0.15); border-radius: 999px;
  padding: 5px 14px;
}
.plan-regen-btn:active { background: rgba(255,255,255,0.25); }
.plan-regen-text { font-size: 13px; font-weight: 600; color: #fff; }

/* Section */
.plan-section { margin-bottom: 16px; }
.plan-section-title { font-size: 16px; font-weight: 800; color: #1c1917; display: block; margin-bottom: 12px; }

/* Weekly focus */
.focus-list { display: flex; flex-direction: column; gap: 10px; }
.focus-item {
  display: flex; align-items: flex-start; gap: 12px;
  background: #ffffff; border: 1px solid #e7e5e4; border-radius: 14px;
  padding: 14px 16px;
}
.focus-dot {
  width: 26px; height: 26px; border-radius: 13px; flex-shrink: 0;
  background: #cd6a43; color: #fff; font-size: 13px; font-weight: 700;
  display: flex; align-items: center; justify-content: center;
}
.focus-text { font-size: 14px; color: #44403c; line-height: 1.5; flex: 1; }

/* Milestone cards */
.milestone-card {
  background: #ffffff; border: 1px solid #e7e5e4; border-radius: 18px;
  margin-bottom: 12px; overflow: hidden;
}
.ms-header {
  display: flex; align-items: center; gap: 12px;
  padding: 16px 18px 12px;
}
.ms-horizon-badge {
  background: #fcf5f2; border-radius: 8px; padding: 4px 10px;
  flex-shrink: 0;
}
.ms-horizon-text { font-size: 12px; font-weight: 700; color: #cd6a43; }
.ms-title { font-size: 15px; font-weight: 700; color: #292524; flex: 1; }
.ms-body { padding: 0 18px 16px; display: flex; flex-direction: column; gap: 10px; }
.ms-row { display: flex; flex-direction: column; gap: 6px; }
.ms-row-label { font-size: 11px; font-weight: 600; color: #a8a29e; text-transform: uppercase; letter-spacing: 0.5px; }
.ms-tag-row { display: flex; flex-wrap: wrap; gap: 6px; }
.ms-tag { border-radius: 8px; padding: 4px 10px; }
.ms-tag-text { font-size: 12px; font-weight: 500; }
.ms-tag-action { background: #fbf8f2; }
.ms-tag-action .ms-tag-text { color: #6e5627; }
.ms-tag-skill  { background: #fcf5f2; }
.ms-tag-skill  .ms-tag-text { color: #c25c33; }
.ms-tag-kpi    { background: #fef9c3; }
.ms-tag-kpi    .ms-tag-text { color: #854d0e; }

/* Dark overrides for plan tab */
.is-dark .plan-section-title { color: #f5f5f4; }
.is-dark .focus-item,
.is-dark .milestone-card { background: #292524; border-color: #44403c; }
.is-dark .focus-text    { color: #d6d3d1; }
.is-dark .ms-title      { color: #f5f5f4; }
.is-dark .plan-empty    { background: #292524; border-color: #44403c; }
.is-dark .plan-empty-title { color: #f5f5f4; }
.is-dark .tab-bar       { background: #1c1917; border: 1px solid #44403c; }
.is-dark .tab-active    { background: #cd6a43; box-shadow: 0 0 0 1px rgba(232, 186, 168, 0.45) inset; }
.is-dark .tab-text      { color: #a8a29e; }
.is-dark .tab-active .tab-text { color: #ffffff; }
.is-dark .tab-badge { background: #fbbf24; }
.is-dark .ms-horizon-badge { background: rgba(205, 106, 67, 0.24); border: 1px solid rgba(232, 186, 168, 0.36); }
.is-dark .ms-horizon-text { color: #f1d6cc; }
.is-dark .ms-tag-action { background: rgba(149, 117, 52, 0.18); }
.is-dark .ms-tag-action .ms-tag-text { color: #d3b882; }
.is-dark .ms-tag-skill { background: rgba(205, 106, 67, 0.2); }
.is-dark .ms-tag-skill .ms-tag-text { color: #e8baa8; }
.is-dark .ms-tag-kpi { background: rgba(245, 158, 11, 0.18); }
.is-dark .ms-tag-kpi .ms-tag-text { color: #fbbf24; }
.is-dark .focus-dot {
  background: rgba(205, 106, 67, 0.24);
  border: 1px solid rgba(232, 186, 168, 0.36);
  color: #f1d6cc;
}

/* ── CareerLoop editorial skin ─────────────────────────────────────────── */
.map-page {
  background: #faf9f6;
  color: #2c2b29;
  font-family: "Noto Sans SC", "PingFang SC", "Microsoft YaHei", sans-serif;
}

.map-content {
  padding: 14px 20px 0;
}

.intro-title,
.nav-title,
.role-name,
.tl-title,
.sheet-title,
.plan-load-text,
.plan-empty-title,
.plan-hero-role,
.plan-section-title,
.ms-title {
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
  font-weight: 600;
  letter-spacing: 0.05em;
}

.intro-title {
  color: #2c2b29;
  font-size: 27px;
  line-height: 1.35;
}

.intro-text,
.role-desc,
.tl-desc,
.sheet-advice,
.plan-empty-sub,
.plan-load-sub,
.focus-text {
  color: #5a5956;
  line-height: 1.7;
}

.nav-title {
  color: #2c2b29;
}

.nav-icon-btn {
  width: 44px;
  height: 44px;
  border: 1px solid rgba(172, 100, 72, 0.32);
  border-radius: 6px;
  background: #f7f1ef;
  color: #ac6448;
}

.map-retry-btn {
  min-width: 120px;
  min-height: 44px;
  margin: 18px auto 0;
  padding: 0 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  color: #fffdfa;
  background: #c23b22;
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
  font-size: 14px;
  font-weight: 600;
}

.tab-bar {
  padding: 0;
  gap: 0;
  border-bottom: 1px solid #e0dfdb;
  border-radius: 0;
  background: transparent;
}

.tab-item {
  height: 42px;
  padding: 0;
  border-bottom: 2px solid transparent;
  border-radius: 0;
  background: transparent;
}

.tab-active {
  border-bottom-color: #c23b22;
  background: transparent;
  box-shadow: none;
}

.tab-text {
  color: #5a5956;
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
  font-weight: 600;
  letter-spacing: 0.05em;
}

.tab-active .tab-text {
  color: #c23b22;
}

.tab-badge {
  width: 6px;
  height: 6px;
  border-radius: 1px;
  background: #b8975a;
}

.role-card,
.plan-hero {
  border: 1px solid #ac6448;
  border-radius: 8px;
  background: #ac6448;
  box-shadow: 0 8px 24px rgba(44, 43, 41, 0.07);
}

.role-card {
  padding: 22px;
}

.role-name,
.plan-hero-role {
  color: #fffdfa;
}

.role-desc,
.plan-hero-label,
.plan-hero-meta {
  color: rgba(255, 253, 250, 0.76);
  opacity: 1;
}

.progress-ring {
  border-color: #d9bd82;
  border-radius: 8px;
  background: rgba(255, 253, 250, 0.1);
}

.ring-val {
  color: #f1d9a6;
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
  font-weight: 600;
}

.timeline {
  padding-left: 30px;
}

.tl-line {
  left: 13px;
  width: 1px;
  background: #d8d6cf;
}

.tl-node {
  margin-bottom: 18px;
}

.tl-dot {
  left: -30px;
  width: 28px;
  height: 28px;
  border: 1px solid #d8d6cf;
  border-radius: 6px;
  background: #faf9f6;
  color: #8b8a86;
}

.dot-done {
  border-color: rgba(141, 132, 110, 0.45);
  background: #f1efeb;
  color: #947f54;
}

.dot-active {
  border-color: rgba(194, 59, 34, 0.42);
  background: #f8ece8;
  color: #c23b22;
}

.dot-ready {
  border-color: rgba(172, 100, 72, 0.35);
  background: #f7f1ef;
  color: #ac6448;
}

.dot-locked {
  border-color: #e0dfdb;
  background: #efeee9;
  color: #8b8a86;
}

.tl-card,
.skel-card,
.empty-state,
.focus-item,
.milestone-card,
.plan-empty {
  border: 1px solid #e0dfdb;
  border-radius: 8px;
  background: #fffdfa;
  box-shadow: 0 8px 24px rgba(44, 43, 41, 0.05);
}

.tl-card {
  padding: 17px;
}

.card-done {
  border-left: 3px solid #8d846e;
}

.card-active {
  border-left: 3px solid #c23b22;
}

.card-ready {
  border-left: 3px solid #ac6448;
}

.tl-level,
.sheet-label,
.ms-row-label {
  color: #8b8a86;
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
  font-weight: 600;
  letter-spacing: 0.06em;
}

.tl-title,
.sheet-title,
.plan-load-text,
.plan-empty-title,
.plan-section-title,
.ms-title {
  color: #2c2b29;
}

.tl-meta,
.tl-badge,
.topic-tag,
.ms-horizon-badge,
.ms-tag,
.plan-regen-btn {
  border-radius: 4px;
}

.tl-meta {
  border: 1px solid #e0dfdb;
  background: #f5f5f0;
  color: #5a5956;
}

.badge-done,
.ms-tag-action {
  border: 1px solid rgba(141, 132, 110, 0.36);
  background: #f1efeb;
}

.badge-done .badge-text,
.ms-tag-action .ms-tag-text {
  color: #766f5d;
}

.badge-active {
  border: 1px solid rgba(194, 59, 34, 0.34);
  background: #f8ece8;
}

.badge-active .badge-text {
  color: #c23b22;
}

.badge-ready,
.ms-horizon-badge,
.ms-tag-skill {
  border: 1px solid rgba(172, 100, 72, 0.28);
  background: #f7f1ef;
}

.badge-ready .badge-text,
.ms-horizon-text,
.ms-tag-skill .ms-tag-text {
  color: #ac6448;
}

.badge-locked {
  border: 1px solid #e0dfdb;
  background: #efeee9;
}

.badge-locked .badge-text {
  color: #8b8a86;
}

.detail-sheet {
  border-top: 1px solid #e0dfdb;
  border-radius: 10px 10px 0 0;
  background: #fffdfa;
  box-shadow: 0 -12px 36px rgba(44, 43, 41, 0.1);
}

.sheet-handle {
  border-radius: 2px;
  background: #d8d6cf;
}

.topic-tag {
  border: 1px solid rgba(172, 100, 72, 0.28);
  background: #f7f1ef;
}

.topic-text {
  color: #ac6448;
}

.sheet-btn,
.plan-gen-btn {
  border-radius: 6px;
  background: #c23b22;
  box-shadow: none;
}

.sheet-btn:active,
.plan-gen-btn:active {
  background: #a9321d;
}

.sheet-btn-text,
.plan-gen-btn-text {
  color: #fffdfa;
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
  font-weight: 600;
  letter-spacing: 0.05em;
}

.sheet-btn-disabled,
.plan-gen-btn-dim {
  border-color: #d8d6cf;
  background: #d8d6cf;
  opacity: 1;
}

.sheet-btn-disabled .sheet-btn-text,
.plan-gen-btn-dim .plan-gen-btn-text {
  color: #5a5956;
}

.skel-line {
  border-radius: 3px;
  background: linear-gradient(90deg, #efeee9 0%, #faf9f6 50%, #efeee9 100%);
  background-size: 200% 100%;
}

.empty-text {
  color: #2c2b29;
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
}

.empty-sub {
  color: #8b8a86;
}

.plan-role-input-label {
  color: #5a5956;
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
  letter-spacing: 0.04em;
}

.plan-role-input {
  border-color: #d8d6cf;
  border-radius: 6px;
  background: #faf9f6;
  color: #2c2b29;
}

.plan-role-input:focus {
  border-color: #ac6448;
  background: #fffdfa;
}

.plan-hero {
  padding: 22px;
}

.plan-regen-btn {
  border: 1px solid rgba(255, 253, 250, 0.45);
  background: transparent;
}

.plan-regen-text {
  color: #fffdfa;
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
}

.plan-section-title {
  padding-bottom: 9px;
  border-bottom: 1px solid #edece8;
}

.focus-item {
  padding: 14px 16px;
}

.focus-dot {
  border-radius: 4px;
  background: #c23b22;
  color: #fffdfa;
  font-family: "Noto Serif SC", "Songti SC", STSong, serif;
}

.milestone-card {
  overflow: hidden;
}

.ms-tag-kpi {
  border: 1px solid rgba(184, 151, 90, 0.36);
  background: #f6f1e7;
}

.ms-tag-kpi .ms-tag-text {
  color: #9a783f;
}

/* Dark mode keeps the same brand hierarchy without reverting to candy blue. */
.map-page.is-dark {
  background: #24231f;
}

.map-page.is-dark .tab-bar {
  border-color: #46443e;
  background: transparent;
}

.map-page.is-dark .tab-active {
  border-bottom-color: #dc6a52;
  background: transparent;
  box-shadow: none;
}

.map-page.is-dark .tab-active .tab-text {
  color: #f08a73;
}

.map-page.is-dark .tl-card,
.map-page.is-dark .detail-sheet,
.map-page.is-dark .focus-item,
.map-page.is-dark .milestone-card,
.map-page.is-dark .plan-empty {
  border-color: #46443e;
  background: #302f2a;
}

.map-page.is-dark .tl-title,
.map-page.is-dark .sheet-title,
.map-page.is-dark .plan-section-title,
.map-page.is-dark .ms-title,
.map-page.is-dark .plan-empty-title,
.map-page.is-dark .plan-load-text {
  color: #f5f2ea;
}

.map-page.is-dark .tl-desc,
.map-page.is-dark .sheet-advice,
.map-page.is-dark .focus-text,
.map-page.is-dark .plan-empty-sub {
  color: #bbb7ae;
}

.map-page.is-dark .focus-dot {
  border-color: #dc6a52;
  background: #c23b22;
  color: #fffdfa;
}

.map-page.is-dark .badge-done {
  border-color: rgba(197, 191, 176, 0.46);
  background: rgba(141, 132, 110, 0.22);
}

.map-page.is-dark .badge-done .badge-text {
  color: #c5bfb0;
}

.map-page.is-dark .badge-active {
  border-color: rgba(226, 123, 102, 0.48);
  background: rgba(194, 59, 34, 0.22);
}

.map-page.is-dark .badge-active .badge-text {
  color: #e27b66;
}

.map-page.is-dark .badge-ready,
.map-page.is-dark .topic-tag {
  border-color: rgba(229, 190, 175, 0.48);
  background: rgba(172, 100, 72, 0.24);
}

.map-page.is-dark .badge-ready .badge-text,
.map-page.is-dark .topic-text {
  color: #e5beaf;
}

.map-page.is-dark .badge-locked {
  border-color: #575650;
  background: #33332f;
}

.map-page.is-dark .badge-locked .badge-text {
  color: #aaa79f;
}

.map-page.is-dark .sheet-btn-disabled,
.map-page.is-dark .plan-gen-btn-dim {
  border-color: #575650;
  background: #33332f;
}

.map-page.is-dark .sheet-btn-disabled .sheet-btn-text,
.map-page.is-dark .plan-gen-btn-dim .plan-gen-btn-text {
  color: #c4c2bc;
}
</style>
