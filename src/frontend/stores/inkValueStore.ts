import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

// ─── Constants ────────────────────────────────────────────────────────────────

const STORAGE_KEY = 'arbitrary_gate_ink_value'

/** 稀有度对应基础墨香值 */
export const RARITY_INK_BONUS: Record<number, number> = {
  1: 0,   // 凡
  2: 10,  // 珍
  3: 25,  // 奇
  4: 50,  // 绝
}

/** 每次抽卡基础墨香值 */
export const BASE_INK_PER_DRAW = 10

/** 连抽加成：每次连续抽卡额外 +5，上限 +20 */
export const STREAK_BONUS_MAX = 20
export const STREAK_BONUS_PER_STEP = 5

/** 段位定义 */
export const INK_LEVELS = [
  { level: 1, name: '初墨',   minPoints: 0,    maxPoints: 99,    color: '#8C8C8C' },
  { level: 2, name: '入墨',   minPoints: 100,  maxPoints: 299,   color: '#6B8E6B' },
  { level: 3, name: '渐墨',   minPoints: 300,  maxPoints: 599,   color: '#7B6BA0' },
  { level: 4, name: '深墨',   minPoints: 600,  maxPoints: 999,   color: '#2196F3' },
  { level: 5, name: '凝墨',   minPoints: 1000, maxPoints: 1499,  color: '#FF9800' },
  { level: 6, name: '墨魂',   minPoints: 1500, maxPoints: Infinity, color: '#FFD700' },
]

// ─── Interface ─────────────────────────────────────────────────────────────────

export interface InkRecord {
  /** 获得墨香值 */
  points: number
  /** 来源卡牌稀有度 */
  rarity: number
  /** 连抽加成 */
  streakBonus: number
  /** 时间 */
  awardedAt: string
}

// ─── Store ─────────────────────────────────────────────────────────────────────

export const useInkValueStore = defineStore('inkValue', () => {
  // 总墨香值（累计）
  const totalPoints = ref(0)

  // 历史记录
  const records = ref<InkRecord[]>([])

  // 当前连抽计数（同一次会话内的连续抽卡）
  // 每次抽卡后 +1，领奖后重置为 0
  const drawStreak = ref(0)

  // 上次抽卡时间（用于判断是否在"连续"时间窗口内）
  let lastDrawTime: number | null = null

  // ── Getters ──────────────────────────────────────────────────────────────

  const currentLevel = computed(() => {
    for (let i = INK_LEVELS.length - 1; i >= 0; i--) {
      if (totalPoints.value >= INK_LEVELS[i].minPoints) {
        return INK_LEVELS[i]
      }
    }
    return INK_LEVELS[0]
  })

  /** 当前等级内进度 (0..1) */
  const levelProgress = computed(() => {
    const lv = currentLevel.value
    if (!isFinite(lv.maxPoints)) return 1 // 墨魂级：满进度
    const range = lv.maxPoints - lv.minPoints + 1
    const earned = totalPoints.value - lv.minPoints
    return Math.min(1, Math.max(0, earned / range))
  })

  /** 距离下一级还差多少 */
  const pointsToNextLevel = computed(() => {
    const lv = currentLevel.value
    if (!isFinite(lv.maxPoints)) return null
    return lv.maxPoints + 1 - totalPoints.value
  })

  const nextLevel = computed(() => {
    const idx = INK_LEVELS.findIndex(l => l.level === currentLevel.value.level)
    return idx < INK_LEVELS.length - 1 ? INK_LEVELS[idx + 1] : null
  })

  // ── Storage ──────────────────────────────────────────────────────────────

  function loadFromStorage() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY)
      if (raw) {
        const data = JSON.parse(raw)
        totalPoints.value = data.totalPoints ?? 0
        records.value = data.records ?? []
        // drawStreak 和 lastDrawTime 不持久化，会话级别
      }
    } catch {
      // ignore corrupt data
    }
  }

  function syncToStorage() {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify({
        totalPoints: totalPoints.value,
        records: records.value,
      }))
    } catch {
      // ignore storage errors
    }
  }

  // ── Actions ───────────────────────────────────────────────────────────────

  /**
   * 抽卡后调用，记录墨香值
   * @param rarity 卡牌稀有度 1-4
   * @returns 本次获得的墨香值
   */
  function awardInkForDraw(rarity: number): number {
    const now = Date.now()

    // 判断是否"连续"（30 分钟内的抽卡算连续）
    const STREAK_WINDOW_MS = 30 * 60 * 1000
    if (lastDrawTime !== null && now - lastDrawTime < STREAK_WINDOW_MS) {
      drawStreak.value++
    } else {
      drawStreak.value = 1
    }
    lastDrawTime = now

    const base = BASE_INK_PER_DRAW
    const rarityBonus = RARITY_INK_BONUS[rarity] ?? 0
    // 连抽加成：第1次(首次)无加成，第2次起 +5/+10/+15/+20（上限）
    const streakBonus = drawStreak.value >= 2
      ? Math.min((drawStreak.value - 1) * STREAK_BONUS_PER_STEP, STREAK_BONUS_MAX)
      : 0

    const earned = base + rarityBonus + streakBonus
    totalPoints.value += earned

    const record: InkRecord = {
      points: earned,
      rarity,
      streakBonus,
      awardedAt: new Date().toISOString(),
    }
    records.value.unshift(record)
    // 保留最近 100 条记录
    if (records.value.length > 100) records.value = records.value.slice(0, 100)

    syncToStorage()
    return earned
  }

  /** 重置连抽计数（用户离开页面后重新进入时会话重置） */
  function resetStreak() {
    drawStreak.value = 0
    lastDrawTime = null
  }

  return {
    totalPoints,
    records,
    drawStreak,
    currentLevel,
    levelProgress,
    pointsToNextLevel,
    nextLevel,
    loadFromStorage,
    syncToStorage,
    awardInkForDraw,
    resetStreak,
  }
})

export function initInkValueStore() {
  const store = useInkValueStore()
  store.loadFromStorage()
}
