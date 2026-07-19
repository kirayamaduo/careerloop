/**
 * F4: i18n setup for CareerLoop.
 *
 * Language detection order:
 *   1. `app_lang` in localStorage (user explicit choice)
 *   2. Runtime application language
 *   3. Falls back to 'zh-CN'
 *
 * Supported locales: zh-CN, en-US
 */
import { createI18n, useI18n as _useI18n } from 'vue-i18n';
import zhCN from './zh-CN';
import enUS from './en-US';

export const LANG_KEY = 'app_lang';
export type LangCode = 'zh-CN' | 'en-US';

function detectLang(): LangCode {
  try {
    const stored = uni.getStorageSync(LANG_KEY) as LangCode;
    if (stored === 'zh-CN' || stored === 'en-US') return stored;
    const getAppBaseInfo = (uni as unknown as {
      getAppBaseInfo?: () => { language?: string };
    }).getAppBaseInfo;
    const sysLang = (typeof getAppBaseInfo === 'function'
      ? getAppBaseInfo().language
      : '') || '';
    if (sysLang.startsWith('en')) return 'en-US';
  } catch { /* ignore — uni not ready or storage unavailable */ }
  return 'zh-CN';
}

export const i18n = createI18n({
  legacy: false,
  locale: detectLang(),
  fallbackLocale: 'zh-CN',
  messages: {
    'zh-CN': zhCN,
    'en-US': enUS,
  },
});

type MessageTree = Record<string, unknown>;

function resolveMessage(messages: MessageTree, key: string): string | undefined {
  let node: unknown = messages;
  for (const segment of key.split('.')) {
    if (!node || typeof node !== 'object') return undefined;
    node = (node as MessageTree)[segment];
  }
  return typeof node === 'string' ? node : undefined;
}

function interpolate(raw: string, params?: Record<string, unknown>): string {
  if (!params) return raw;
  return Object.entries(params).reduce(
    (value, [key, replacement]) =>
      value.split(`{${key}}`).join(String(replacement ?? '')),
    raw,
  );
}

/**
 * Resolve the untouched message string first, then interpolate ourselves.
 * Calling vue-i18n without params causes its H5 runtime to remove unresolved
 * placeholders, while passing params is unreliable in the mini-program
 * compiler. Reading the locale tree gives both targets identical behaviour.
 */
export function useI18n() {
  const { t: fallbackT, locale, ...rest } = _useI18n();
  function t(key: string, params?: Record<string, unknown>): string {
    const lang = locale.value === 'en-US' ? 'en-US' : 'zh-CN';
    const primary = lang === 'en-US' ? enUS : zhCN;
    const raw = resolveMessage(primary as MessageTree, key)
      ?? resolveMessage(zhCN as MessageTree, key)
      ?? (fallbackT(key) as string);
    return interpolate(raw, params);
  }
  return { t, locale, ...(rest as ReturnType<typeof _useI18n>) };
}

export function setLocale(lang: LangCode) {
  uni.setStorageSync(LANG_KEY, lang);
  (i18n.global.locale as any).value = lang;
  updateTabBar(lang);
}

export function currentLocale(): LangCode {
  return (i18n.global.locale as any).value as LangCode;
}

export function translate(key: string, params?: Record<string, unknown>): string {
  const lang = currentLocale();
  const primary = lang === 'en-US' ? enUS : zhCN;
  const raw = resolveMessage(primary as MessageTree, key)
    ?? resolveMessage(zhCN as MessageTree, key)
    ?? (i18n.global.t(key) as string);
  return interpolate(raw, params);
}

/**
 * 用 uni.setTabBarItem() 动态覆盖 tabBar 文字。
 * pages.json 里的 text 是静态编译值，无法被 vue-i18n 响应式更新，
 * 必须在运行时主动调用此函数。此函数在 setLocale 和 App.vue onLaunch 里均需调用。
 */
export function updateTabBar(lang?: LangCode) {
  const locale = lang ?? currentLocale();
  const msgs = locale === 'zh-CN' ? zhCN : enUS;
  // 顺序必须与 pages.json tabBar.list 一致
  const navKeys: (keyof typeof zhCN.nav)[] = ['home', 'assistant', 'resume', 'profile'];
  navKeys.forEach((key, index) => {
    try {
      uni.setTabBarItem({ index, text: msgs.nav[key] });
    } catch { /* 非 tabBar 页面或渲染器未就绪时忽略 */ }
  });
}
