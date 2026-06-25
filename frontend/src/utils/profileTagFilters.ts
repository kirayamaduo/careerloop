/**
 * 画像标签过滤公共工具
 *
 * 词云（user/index.vue）和成长树（home/index.vue）都从 profileTags 取数，
 * 但过滤规则略有不同。统一维护在这里，避免两边悄悄出现规则漂移。
 *
 * 区别：
 *  - 词云：不限 category，但要求 label ≤ 14 字符（显示空间有限）
 *  - 成长树：排除 GOAL 类（目标岗位单独渲染为终点节点），允许更长的 label（≤ 20 字符）
 */

/** 两者共用的基础过滤：纯数字、无意义占位词、太短的全部排除 */
const BASE_BLOCKLIST = ['用户', '目标', '岗位', '状态', '待补充', '简历状态', '简历匹配'];

/** 后端枚举/布尔原值，不应作为画像标签展示 */
const TECHNICAL_ENUM_TOKENS = new Set([
  'true', 'false', 'yes', 'no', 'null', 'undefined', 'unsure',
  'new_graduate', 'internship_seeker', 'career_switcher', 'experienced', 'student',
  'ready', 'draft', 'within_1_month', 'within_3_months', 'prepare_early',
  'lt_5h', '5_10h', '10_20h', 'gt_20h', 'easy', 'medium', 'hard',
]);

const hasRealText = (text: string) =>
  /[A-Za-z]{2,}|[一-龥]{2,}|AI|UI|UX/.test(text);

/** 画像标签编辑区 / 保存时应保留的标签 */
export const isEditableProfileTagLabel = (label?: string): boolean => {
  const text = String(label || '').trim();
  if (!text || text.length > 24) return false;
  if (/^\d+$/.test(text)) return false;
  if (BASE_BLOCKLIST.includes(text)) return false;
  if (TECHNICAL_ENUM_TOKENS.has(text.toLowerCase())) return false;
  return hasRealText(text);
};

/**
 * 词云关键词过滤（my/index.vue 使用）
 * 要求 label ≤ 14 字符，过滤纯数字和占位词
 */
export const isCloudKeyword = (label?: string): boolean => {
  const text = String(label || '').trim();
  if (!text || text.length > 14) return false;
  if (!isEditableProfileTagLabel(text)) return false;
  return true;
};

/**
 * 成长树节点过滤（home/index.vue 使用）
 * 排除 GOAL 类（由调用方在 computed 里过滤），允许 label ≤ 20 字符
 * 纯年份/日期格式保留（会被当做时间轴信息使用）
 */
export const isGrowthKeyword = (label?: string): boolean => {
  const text = String(label || '').trim();
  if (!text || text.length > 20) return false;
  if (/^\d+$/.test(text)) return false;
  // 纯年份（如 "2020"、"2020.3"）排除——这类会由 extractNodeTime 单独处理
  if (/^(?:19|20)\d{2}(?:[.年\-\/]\d{0,2})?[年月]?$/.test(text)) return false;
  if (BASE_BLOCKLIST.includes(text)) return false;
  return hasRealText(text);
};

/** 首页「求职画像」卡片与词云/成长树共用的中文标签集合 */
export const PORTRAIT_TAG_LABELS = {
  pain: ['方向不清', '简历薄弱', '项目不足', '面试没底', '缺少计划'],
  resume: ['已有可用简历', '已有可投简历', '简历草稿中', '暂无简历', '简历质量待确认'],
  timeline: ['马上投递', '1 个月内', '3 个月内', '提前准备'],
  weekly: ['< 5 小时', '5-10 小时', '10-20 小时', '> 20 小时'],
  priority: ['优先改简历', '优先定方向', '优先练面试', '优先做计划'],
} as const;

export interface PortraitTagLike {
  label?: string;
  category?: string;
}

/** 从 profileTags 中按已知中文标签查找，供首页画像卡片回填 */
export const findPortraitTagLabel = (
  tags: PortraitTagLike[] | undefined,
  labels: readonly string[],
): string | undefined => {
  if (!tags?.length) return undefined;
  const wanted = new Set(labels);
  return tags.find((tag) => wanted.has(String(tag.label || '').trim()))?.label?.trim();
};

/** 合并 onboarding 背景与 BACKGROUND 标签，供首页画像卡片展示 */
export const buildPortraitBackground = (
  tags: PortraitTagLike[] | undefined,
  school?: string,
  major?: string,
): string => {
  const parts = [school, major].filter(Boolean);
  if (parts.length) return parts.join(' · ');

  const backgroundTags = (tags || [])
    .filter((tag) => tag.category === 'BACKGROUND')
    .map((tag) => String(tag.label || '').trim())
    .filter((label) => label && !PORTRAIT_TAG_LABELS.resume.includes(label as typeof PORTRAIT_TAG_LABELS.resume[number]))
    .filter((label) => !['在校学生', '应届求职', '实习求职', '转行求职', '职场进阶'].includes(label));

  if (backgroundTags.length >= 2) {
    return `${backgroundTags[0]} · ${backgroundTags[1]}`;
  }
  if (backgroundTags.length === 1) return backgroundTags[0];
  return '待补充';
};
