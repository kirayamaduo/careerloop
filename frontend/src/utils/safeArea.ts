export interface MpSafeAreaMetrics {
  statusBarHeight: number;
  menuTop: number;
  menuBottom: number;
  menuHeight: number;
  menuLeft: number;
  menuRight: number;
  menuWidth: number;
  windowWidth: number;
  topSafeHeight: number;
  navHeight: number;
  contentTop: number;
  rightAvoidWidth: number;
}

const FALLBACK_MENU_WIDTH = 96;
const FALLBACK_NAV_HEIGHT = 44;
const CAPSULE_GAP = 12;

export function getMpSafeAreaMetrics(): MpSafeAreaMetrics {
  const getWindowInfo = (uni as unknown as {
    getWindowInfo?: () => { statusBarHeight?: number; windowWidth?: number };
  }).getWindowInfo;
  const windowInfo = typeof getWindowInfo === 'function'
    ? getWindowInfo()
    : { statusBarHeight: 20, windowWidth: 375 };
  const statusBarHeight = windowInfo.statusBarHeight || 20;
  const windowWidth = windowInfo.windowWidth || 375;
  let menuTop = statusBarHeight + 8;
  let menuHeight = 32;
  let menuWidth = FALLBACK_MENU_WIDTH;
  let menuRight = windowWidth - 8;

  // #ifndef H5
  const menuButton = uni.getMenuButtonBoundingClientRect?.();
  if (menuButton && menuButton.top && menuButton.left) {
    menuTop = menuButton.top;
    menuHeight = menuButton.height || menuHeight;
    menuWidth = menuButton.width || menuWidth;
    menuRight = menuButton.right || menuRight;
  }
  // #endif

  const menuBottom = menuTop + menuHeight;
  const menuLeft = menuRight - menuWidth;
  const topSafeHeight = menuTop;
  const navHeight = Math.max(FALLBACK_NAV_HEIGHT, menuBottom - statusBarHeight + CAPSULE_GAP);
  const contentTop = menuBottom + CAPSULE_GAP;
  const rightAvoidWidth = Math.max(0, windowWidth - menuLeft + CAPSULE_GAP);

  return {
    statusBarHeight,
    menuTop,
    menuBottom,
    menuHeight,
    menuLeft,
    menuRight,
    menuWidth,
    windowWidth,
    topSafeHeight,
    navHeight,
    contentTop,
    rightAvoidWidth,
  };
}

export function getTopSafeHeight(): number {
  return getMpSafeAreaMetrics().topSafeHeight;
}

export function getCapsuleRightAvoidWidth(): number {
  return getMpSafeAreaMetrics().rightAvoidWidth;
}

export function getContentSafeTop(): number {
  return getMpSafeAreaMetrics().contentTop;
}
