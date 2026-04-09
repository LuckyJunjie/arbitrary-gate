/**
 * UI-11 触感反馈工具
 *
 * 提供触感反馈功能，在关键交互点触发设备振动：
 * - 墨池点击抽卡时
 * - 卡片翻转时
 * - 手势滑动确认时
 *
 * 如果 navigator.vibrate 不可用，静默失败（不影响功能）
 */

/**
 * 触发短振动（抽卡、翻转等轻微交互）
 */
export function hapticLight(): void {
  try {
    if (typeof navigator !== 'undefined' && navigator.vibrate) {
      navigator.vibrate(10)
    }
  } catch {
    // 静默失败
  }
}

/**
 * 触发中等振动（滑动确认等）
 */
export function hapticMedium(): void {
  try {
    if (typeof navigator !== 'undefined' && navigator.vibrate) {
      navigator.vibrate(30)
    }
  } catch {
    // 静默失败
  }
}

/**
 * 触发用力长按振动（长按手势）
 */
export function hapticForceful(): void {
  try {
    if (typeof navigator !== 'undefined' && navigator.vibrate) {
      navigator.vibrate([50, 30, 50])
    }
  } catch {
    // 静默失败
  }
}
