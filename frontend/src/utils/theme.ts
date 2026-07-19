/**
 * F5/F6: Theme & font-scale utilities.
 *
 * Storage keys:
 *   app_pref_dark  '0'|'1'        — dark mode toggle (pre-existing)
 *   app_pref_theme 'light'|'dark'|'green'   — explicit theme (new)
 *   app_pref_font  'compact'|'standard'|'large'  — font scale (pre-existing, now applied)
 *
 * Usage in page components:
 *   import { useTheme } from '@/utils/theme';
 *   const { themeClass, fontClass } = useTheme();
 *   // In template: <view :class="[themeClass, fontClass]">
 */
import { ref, computed } from 'vue';

export type ThemeKey = 'light' | 'dark' | 'green';
export type FontKey  = 'compact' | 'standard' | 'large';

const THEME_KEY = 'app_pref_theme';
const DARK_KEY  = 'app_pref_dark';
const FONT_KEY  = 'app_pref_font';

// ── Module-level singleton refs ──────────────────────────────────────────────
// All components that call useTheme() share these exact same refs, so a
// setTheme() call in user/index.vue instantly updates themeClass in
// home/index.vue and assistant/index.vue without needing a page remount.
const _theme = ref<ThemeKey>(_readTheme());
const _font  = ref<FontKey>(_readFont());

const _themeClass = computed(() => {
  if (_theme.value === 'dark')  return 'is-dark';
  if (_theme.value === 'green') return 'theme-green';
  return '';
});

const _fontClass = computed(() => {
  if (_font.value === 'compact') return 'font-compact';
  if (_font.value === 'large')   return 'font-large';
  return '';
});
// ─────────────────────────────────────────────────────────────────────────────

export function useTheme() {
  function setTheme(t: ThemeKey) {
    _theme.value = t;
    uni.setStorageSync(THEME_KEY, t);
    uni.setStorageSync(DARK_KEY, t === 'dark' ? '1' : '0');
    _applyTabBarStyle(t);
  }

  function setFont(f: FontKey) {
    _font.value = f;
    uni.setStorageSync(FONT_KEY, f);
  }

  function refresh() {
    _theme.value = _readTheme();
    _font.value  = _readFont();
    _applyTabBarStyle(_theme.value);
  }

  return {
    theme: _theme,
    font: _font,
    themeClass: _themeClass,
    fontClass: _fontClass,
    setTheme,
    setFont,
    refresh,
  };
}

function _readTheme(): ThemeKey {
  try {
    const stored = uni.getStorageSync(THEME_KEY) as ThemeKey;
    if (stored === 'dark' || stored === 'green' || stored === 'light') return stored;
    const isDark = uni.getStorageSync(DARK_KEY) === '1';
    return isDark ? 'dark' : 'light';
  } catch { return 'light'; }
}

function _readFont(): FontKey {
  try {
    const stored = uni.getStorageSync(FONT_KEY) as FontKey;
    if (stored === 'compact' || stored === 'large') return stored;
  } catch { /* ignore */ }
  return 'standard';
}

function _applyTabBarStyle(t: ThemeKey) {
  if (_isTabBarPage()) {
    try {
      if (t === 'dark') {
        uni.setTabBarStyle({
          color: '#94a3b8',
          selectedColor: '#c23b22',
          backgroundColor: '#0f172a',
          borderStyle: 'black',
        });
      } else if (t === 'green') {
        uni.setTabBarStyle({
          color: '#5a5956',
          selectedColor: '#c23b22',
          backgroundColor: '#f4f5f0',
          borderStyle: 'white',
        });
      } else {
        uni.setTabBarStyle({
          color: '#8b8a86',
          selectedColor: '#c23b22',
          backgroundColor: '#faf9f6',
          borderStyle: 'white',
        });
      }
    } catch { /* ignore unsupported runtimes */ }
  }

  // WeChat `page` background in App.vue is a static light colour; sync the
  // native window chrome when the in-app theme changes (MP supports this API).
  try {
    const setBg = (uni as unknown as { setBackgroundColor?: (o: Record<string, string>) => void })
      .setBackgroundColor;
    if (typeof setBg === 'function') {
      const bg = t === 'dark' ? '#0f172a' : t === 'green' ? '#f0fdf4' : '#faf9f6';
      setBg({
        backgroundColor: bg,
        backgroundColorTop: bg,
        backgroundColorBottom: bg,
      });
    }
  } catch { /* H5 / unsupported */ }
}

function _isTabBarPage() {
  try {
    const pages = getCurrentPages?.() || [];
    const current = pages[pages.length - 1] as { route?: string } | undefined;
    const route = current?.route ? `/${current.route}` : '';
    return [
      '/pages/home/index',
      '/pages/assistant/index',
      '/pages/resume/index',
      '/pages/user/index',
    ].includes(route);
  } catch {
    return false;
  }
}
