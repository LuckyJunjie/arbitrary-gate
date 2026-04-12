/**
 * useAchievementToast.ts — 成就解锁 Toast 通知
 *
 * 功能：
 *   - 监听 achievementStore.unlockedAchievements，新解锁时自动弹出 toast
 *   - 普通成就（collection/draw/story）用淡雅宣纸风格
 *   - 组合成就（combination）用金色醒目标题风格
 *   - 防重复：同一成就 10 秒内不重复弹出
 *   - 导出 showAchievementToast() 可手动触发
 */

import { watch, ref } from 'vue'
import { useAchievementStore, type Achievement } from '@/stores/achievementStore'

const TOAST_DURATION_MS = 3000
const DEBOUNCE_WINDOW_MS = 10000 // 10 秒内相同成就不重复弹

// ─── 全局 toast 容器（单例） ────────────────────────────────────────────────

let toastContainer: HTMLElement | null = null

function ensureContainer(): HTMLElement {
  if (!toastContainer) {
    toastContainer = document.createElement('div')
    toastContainer.id = 'achievement-toast-container'
    toastContainer.style.cssText = `
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      z-index: 9999;
      pointer-events: none;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 10px;
      padding-top: 60px;
    `
    document.body.appendChild(toastContainer)
  }
  return toastContainer
}

// ─── Toast DOM 创建 ─────────────────────────────────────────────────────────

function createToastEl(achievement: Achievement, isCombo: boolean): HTMLElement {
  const el = document.createElement('div')

  if (isCombo) {
    // 组合成就：深色底 + 金色标题（传说风格）
    el.className = 'achievement-toast combo'
    el.innerHTML = `
      <div class="toast-badge">✦ 成就解锁 ✦</div>
      <div class="toast-icon">${achievement.icon}</div>
      <div class="toast-title">${achievement.name}</div>
      <div class="toast-desc">${achievement.description}</div>
    `
    el.style.cssText = `
      position: relative;
      z-index: 9999;
      background: linear-gradient(135deg, #2c3e50 0%, #1a252f 100%);
      border: 1px solid #c9a84c;
      border-radius: 6px;
      padding: 14px 24px;
      box-shadow: 0 4px 20px rgba(201, 168, 76, 0.35), 0 0 40px rgba(201, 168, 76, 0.1);
      font-family: "Noto Serif SC", "Source Han Serif SC", "SimSun", serif;
      color: #f5efe0;
      max-width: 300px;
      width: calc(100vw - 48px);
      text-align: center;
      animation: achievementToastIn 0.5s cubic-bezier(0.22, 1, 0.36, 1) forwards;
      pointer-events: none;
    `
    const badge = el.querySelector('.toast-badge') as HTMLElement
    if (badge) {
      badge.style.cssText = `
        font-size: 11px;
        color: #c9a84c;
        letter-spacing: 2px;
        margin-bottom: 6px;
        opacity: 0.85;
      `
    }
    const icon = el.querySelector('.toast-icon') as HTMLElement
    if (icon) {
      icon.style.cssText = `
        font-size: 32px;
        margin-bottom: 6px;
        filter: drop-shadow(0 0 8px rgba(201, 168, 76, 0.6));
      `
    }
    const title = el.querySelector('.toast-title') as HTMLElement
    if (title) {
      title.style.cssText = `
        font-size: 16px;
        font-weight: bold;
        color: #c9a84c;
        margin-bottom: 4px;
      `
    }
    const desc = el.querySelector('.toast-desc') as HTMLElement
    if (desc) {
      desc.style.cssText = `
        font-size: 12px;
        color: #d4c5a0;
        line-height: 1.5;
      `
    }
  } else {
    // 普通成就：宣纸质感淡雅风格
    el.className = 'achievement-toast'
    el.innerHTML = `
      <div class="toast-icon">${achievement.icon}</div>
      <div class="toast-title">${achievement.name}</div>
      <div class="toast-desc">${achievement.description}</div>
    `
    el.style.cssText = `
      position: relative;
      z-index: 9999;
      background: linear-gradient(135deg, #f5efe0 0%, #e8dcc8 100%);
      border: 1px solid #4A6B6B;
      border-radius: 4px;
      padding: 12px 20px;
      box-shadow: 0 4px 12px rgba(74, 107, 107, 0.25);
      font-family: "Noto Serif SC", "Source Han Serif SC", "SimSun", serif;
      color: #3a3a3a;
      max-width: 300px;
      width: calc(100vw - 48px);
      text-align: center;
      animation: achievementToastIn 0.4s cubic-bezier(0.22, 1, 0.36, 1) forwards;
      pointer-events: none;
    `
    const icon = el.querySelector('.toast-icon') as HTMLElement
    if (icon) {
      icon.style.cssText = `
        font-size: 26px;
        margin-bottom: 4px;
      `
    }
    const title = el.querySelector('.toast-title') as HTMLElement
    if (title) {
      title.style.cssText = `
        font-size: 14px;
        font-weight: bold;
        color: #2c3e50;
        margin-bottom: 2px;
      `
    }
    const desc = el.querySelector('.toast-desc') as HTMLElement
    if (desc) {
      desc.style.cssText = `
        font-size: 12px;
        color: #6b6b6b;
        line-height: 1.4;
      `
    }
  }

  return el
}

// ─── Inject keyframes once ───────────────────────────────────────────────────

function ensureKeyframes(): void {
  if (document.getElementById('achievement-toast-keyframes')) return
  const style = document.createElement('style')
  style.id = 'achievement-toast-keyframes'
  style.textContent = `
    @keyframes achievementToastIn {
      from {
        opacity: 0;
        transform: translateY(-16px) scale(0.95);
      }
      to {
        opacity: 1;
        transform: translateY(0) scale(1);
      }
    }
    @keyframes achievementToastOut {
      from {
        opacity: 1;
        transform: translateY(0) scale(1);
      }
      to {
        opacity: 0;
        transform: translateY(-10px) scale(0.95);
      }
    }
  `
  document.head.appendChild(style)
}

// ─── Show toast ─────────────────────────────────────────────────────────────

function showToast(achievement: Achievement, isCombo: boolean): void {
  ensureKeyframes()
  const container = ensureContainer()
  const el = createToastEl(achievement, isCombo)
  container.appendChild(el)

  // Auto-remove after duration
  setTimeout(() => {
    el.style.animation = 'achievementToastOut 0.3s ease-in forwards'
    el.addEventListener('animationend', () => el.remove(), { once: true })
    // Fallback removal
    setTimeout(() => el.remove(), 400)
  }, TOAST_DURATION_MS)
}

// ─── Composable ─────────────────────────────────────────────────────────────

export function useAchievementToast(achievementStore = useAchievementStore()) {
  // Track already-seen achievement IDs to detect new unlocks
  const seenIds = ref<Set<string>>(new Set())

  // Track recently-shown achievements (id → timestamp) for debounce
  const recentToastIds = ref<Map<string, number>>(new Map())

  function isComboAchievement(achievement: Achievement): boolean {
    return achievement.category === 'combination'
  }

  function isRecentlyShown(id: string): boolean {
    const lastShown = recentToastIds.value.get(id)
    if (!lastShown) return false
    return Date.now() - lastShown < DEBOUNCE_WINDOW_MS
  }

  function markShown(id: string): void {
    recentToastIds.value.set(id, Date.now())
  }

  /**
   * Manually trigger a toast for a given achievement
   */
  function showAchievementToast(achievement: Achievement): void {
    if (isRecentlyShown(achievement.id)) return
    markShown(achievement.id)
    showToast(achievement, isComboAchievement(achievement))
  }

  // Initialize seenIds with currently unlocked achievements
  // (so we don't fire toasts on page load for already-unlocked ones)
  for (const a of achievementStore.unlockedAchievements) {
    seenIds.value.add(a.id)
  }

  // Watch for new unlocks
  watch(
    () => achievementStore.unlockedAchievements,
    (newList) => {
      for (const achievement of newList) {
        if (!seenIds.value.has(achievement.id)) {
          seenIds.value.add(achievement.id)
          if (!isRecentlyShown(achievement.id)) {
            markShown(achievement.id)
            showToast(achievement, isComboAchievement(achievement))
          }
        }
      }
    },
    { deep: true }
  )

  return {
    showAchievementToast,
  }
}
