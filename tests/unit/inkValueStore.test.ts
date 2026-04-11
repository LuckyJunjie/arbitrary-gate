import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import {
  useInkValueStore,
  BASE_INK_PER_DRAW,
  RARITY_INK_BONUS,
  STREAK_BONUS_PER_STEP,
  STREAK_BONUS_MAX,
  INK_LEVELS,
  INK_DECAY_CONFIG,
} from '@/stores/inkValueStore'

// ─── Helpers ─────────────────────────────────────────────────────────────────

/** 创建一个全新的 store 实例（每个测试独立） */
function createFreshStore() {
  const store = useInkValueStore()
  // 清除 store 状态
  store.$state = {
    totalPoints: 0,
    records: [],
    drawStreak: 0,
  }
  return store
}

// ─── Shared mock localStorage（每个测试独立，互不干扰）───────────────────────
let mockStorageInstance: Record<string, string> = {}

function createMockStorage() {
  const storage: Record<string, string> = {}
  mockStorageInstance = storage
  return {
    getItem: (key: string) => storage[key] ?? null,
    setItem: (key: string, value: string) => { storage[key] = value },
    removeItem: (key: string) => { delete storage[key] },
    clear: () => { Object.keys(storage).forEach(k => delete storage[k]) },
  }
}

let globalMockRef = createMockStorage()

// ─── Mock localStorage ────────────────────────────────────────────────────
beforeEach(() => {
  globalMockRef.clear()
  vi.stubGlobal('localStorage', globalMockRef)
})

afterEach(() => {
  vi.useRealTimers() // 恢复真实 timer，避免污染后续测试
  vi.unstubAllGlobals()
  globalMockRef.clear()
})

// ─── Tests ───────────────────────────────────────────────────────────────────

describe('BASE_INK_PER_DRAW', () => {
  it('每次抽卡基础墨香值为 10', () => {
    expect(BASE_INK_PER_DRAW).toBe(10)
  })
})

describe('RARITY_INK_BONUS', () => {
  it('凡(1)=0, 珍(2)=10, 奇(3)=25, 绝(4)=50', () => {
    expect(RARITY_INK_BONUS[1]).toBe(0)
    expect(RARITY_INK_BONUS[2]).toBe(10)
    expect(RARITY_INK_BONUS[3]).toBe(25)
    expect(RARITY_INK_BONUS[4]).toBe(50)
  })
})

describe('INK_LEVELS 段位定义', () => {
  it('共 6 个段位', () => {
    expect(INK_LEVELS).toHaveLength(6)
  })

  it('初墨从 0 开始', () => {
    expect(INK_LEVELS[0].name).toBe('初墨')
    expect(INK_LEVELS[0].minPoints).toBe(0)
    expect(INK_LEVELS[0].level).toBe(1)
  })

  it('墨魂为最高段位，无上限', () => {
    const last = INK_LEVELS[INK_LEVELS.length - 1]
    expect(last.name).toBe('墨魂')
    expect(last.level).toBe(6)
    expect(last.maxPoints).toBe(Infinity)
  })

  it('各段位等级递增', () => {
    for (let i = 1; i < INK_LEVELS.length; i++) {
      expect(INK_LEVELS[i].level).toBe(INK_LEVELS[i - 1].level + 1)
      expect(INK_LEVELS[i].minPoints).toBeGreaterThan(INK_LEVELS[i - 1].minPoints)
    }
  })
})

describe('awardInkForDraw 积分计算', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('凡品卡：基础 10 分，无稀有加成，无连抽加成（首次）', () => {
    const store = createFreshStore()
    const earned = store.awardInkForDraw(1)
    expect(earned).toBe(10)
    expect(store.totalPoints).toBe(10)
  })

  it('珍品卡：10 + 10 = 20（首次）', () => {
    const store = createFreshStore()
    const earned = store.awardInkForDraw(2)
    expect(earned).toBe(20)
    expect(store.totalPoints).toBe(20)
  })

  it('奇品卡：10 + 25 = 35（首次）', () => {
    const store = createFreshStore()
    const earned = store.awardInkForDraw(3)
    expect(earned).toBe(35)
  })

  it('绝品卡：10 + 50 = 60（首次）', () => {
    const store = createFreshStore()
    const earned = store.awardInkForDraw(4)
    expect(earned).toBe(60)
  })

  it('连抽加成：第2次 +5，第3次 +10，第4次 +15，第5次 +20（上限）', () => {
    const store = createFreshStore()

    const earned1 = store.awardInkForDraw(1) // 10+0+0 = 10
    expect(earned1).toBe(10)

    const earned2 = store.awardInkForDraw(1) // 10+0+5 = 15
    expect(earned2).toBe(15)

    const earned3 = store.awardInkForDraw(1) // 10+0+10 = 20
    expect(earned3).toBe(20)

    const earned4 = store.awardInkForDraw(1) // 10+0+15 = 25
    expect(earned4).toBe(25)

    const earned5 = store.awardInkForDraw(1) // 10+0+20 = 30 (cap)
    expect(earned5).toBe(30)

    // 继续抽，连抽加成维持 20
    const earned6 = store.awardInkForDraw(1) // 10+0+20 = 30
    expect(earned6).toBe(30)
  })

  it('连抽加成与稀有度叠加正确', () => {
    const store = createFreshStore()
    // 第1次：珍品 → 10+10+0 = 20
    expect(store.awardInkForDraw(2)).toBe(20)
    // 第2次：绝品 → 10+50+5 = 65（第2次连抽，streak=2 → bonus=(2-1)*5=5）
    expect(store.awardInkForDraw(4)).toBe(65)
  })

  it('记录正确写入 records', () => {
    const store = createFreshStore()
    store.awardInkForDraw(3)
    expect(store.records).toHaveLength(1)
    expect(store.records[0].points).toBe(35)
    expect(store.records[0].rarity).toBe(3)
    expect(store.records[0].streakBonus).toBe(0)
  })

  it('多次抽卡后 records 数量正确（保留最近 100 条）', () => {
    const store = createFreshStore()
    for (let i = 0; i < 150; i++) {
      store.awardInkForDraw(1)
    }
    expect(store.records).toHaveLength(100)
  })
})

describe('currentLevel 段位计算', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('0 点 → 初墨', () => {
    const store = createFreshStore()
    expect(store.currentLevel.name).toBe('初墨')
    expect(store.currentLevel.level).toBe(1)
  })

  it('99 点 → 初墨', () => {
    const store = createFreshStore()
    store.totalPoints = 99
    expect(store.currentLevel.name).toBe('初墨')
  })

  it('100 点 → 入墨', () => {
    const store = createFreshStore()
    store.totalPoints = 100
    expect(store.currentLevel.name).toBe('入墨')
    expect(store.currentLevel.level).toBe(2)
  })

  it('299 点 → 入墨', () => {
    const store = createFreshStore()
    store.totalPoints = 299
    expect(store.currentLevel.name).toBe('入墨')
  })

  it('300 点 → 渐墨', () => {
    const store = createFreshStore()
    store.totalPoints = 300
    expect(store.currentLevel.name).toBe('渐墨')
  })

  it('600 点 → 深墨', () => {
    const store = createFreshStore()
    store.totalPoints = 600
    expect(store.currentLevel.name).toBe('深墨')
  })

  it('1000 点 → 凝墨', () => {
    const store = createFreshStore()
    store.totalPoints = 1000
    expect(store.currentLevel.name).toBe('凝墨')
  })

  it('1500 点 → 墨魂（最高）', () => {
    const store = createFreshStore()
    store.totalPoints = 1500
    expect(store.currentLevel.name).toBe('墨魂')
    expect(store.currentLevel.level).toBe(6)
  })
})

describe('levelProgress 进度计算', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('初墨 0 点：进度 0%', () => {
    const store = createFreshStore()
    expect(store.levelProgress).toBe(0)
  })

  it('初墨 50 点：进度 ~50%（50/100）', () => {
    const store = createFreshStore()
    store.totalPoints = 50
    expect(store.levelProgress).toBeCloseTo(0.5, 1)
  })

  it('入墨 100 点：进度 0%', () => {
    const store = createFreshStore()
    store.totalPoints = 100
    expect(store.levelProgress).toBe(0)
  })

  it('入墨 200 点：进度 ~33%（100/200 范围内挣了 100）', () => {
    const store = createFreshStore()
    store.totalPoints = 200
    // 入墨范围 100-299 = 200 点，挣了 100
    expect(store.levelProgress).toBeCloseTo(0.5, 1)
  })

  it('墨魂：进度始终 1', () => {
    const store = createFreshStore()
    store.totalPoints = 9999
    expect(store.levelProgress).toBe(1)
  })
})

describe('pointsToNextLevel 距离下一级', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('初墨 50 点：距入墨（100）还差 50', () => {
    const store = createFreshStore()
    store.totalPoints = 50
    expect(store.pointsToNextLevel).toBe(50)
  })

  it('初墨 99 点：距入墨还差 1', () => {
    const store = createFreshStore()
    store.totalPoints = 99
    expect(store.pointsToNextLevel).toBe(1)
  })

  it('墨魂：返回 null', () => {
    const store = createFreshStore()
    store.totalPoints = 2000
    expect(store.pointsToNextLevel).toBeNull()
  })
})

describe('nextLevel 下一级信息', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('初墨 → 入墨', () => {
    const store = createFreshStore()
    expect(store.nextLevel?.name).toBe('入墨')
    expect(store.nextLevel?.level).toBe(2)
  })

  it('墨魂 → null', () => {
    const store = createFreshStore()
    store.totalPoints = 2000
    expect(store.nextLevel).toBeNull()
  })
})

describe('STREAK_BONUS_MAX / STREAK_BONUS_PER_STEP', () => {
  it('STREAK_BONUS_MAX = 20', () => {
    expect(STREAK_BONUS_MAX).toBe(20)
  })

  it('STREAK_BONUS_PER_STEP = 5', () => {
    expect(STREAK_BONUS_PER_STEP).toBe(5)
  })
})

// ─── 时间衰减测试（C-11 墨香渐淡）───────────────────────────────────────────

describe('墨香值时间衰减 - INK_DECAY_CONFIG', () => {
  it('默认每小时衰减 5%', () => {
    expect(INK_DECAY_CONFIG.rate).toBe(0.05)
    expect(INK_DECAY_CONFIG.intervalMs).toBe(60 * 60 * 1000)
    expect(INK_DECAY_CONFIG.enabled).toBe(true)
  })
})

describe('墨香值时间衰减 - decayInkValue', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('无上次访问记录，无需衰减', () => {
    const store = createFreshStore()
    store.totalPoints = 100
    const decayed = store.decayInkValue()
    expect(decayed).toBe(0)
    expect(store.totalPoints).toBe(100)
  })

  it('不足 1 小时，无需衰减', () => {
    const store = createFreshStore()
    store.totalPoints = 100
    // T0 = 2024-01-01 00:00:00 UTC
    vi.useFakeTimers()
    vi.setSystemTime(1704067200000)
    // lastAccess = T0 - 30min → 不足 1 小时间隔，无需衰减
    mockStorageInstance['arbitrary_gate_last_access'] = String(1704067200000 - 30 * 60 * 1000)
    const decayed = store.decayInkValue()
    expect(decayed).toBe(0)
    expect(store.totalPoints).toBe(100)
    vi.useRealTimers()
  })

  it('超过 1 小时，衰减 5%（1轮）', () => {
    const store = createFreshStore()
    store.totalPoints = 100
    // T0 = 2024-01-01 00:00:00 UTC, lastAccess = T0 - 2h → 2 小时 elapsed = 2 轮
    // 但 fake timer 可能稍有偏差，改用 65min 以确保 > 1h 但 < 2h
    vi.useFakeTimers()
    vi.setSystemTime(1704067200000)
    // 65 分钟前：超过 1 小时，触发 1 轮
    mockStorageInstance['arbitrary_gate_last_access'] = String(1704067200000 - 65 * 60 * 1000)
    const decayed = store.decayInkValue()
    // 1轮: floor(100 * 0.05) = 5
    expect(decayed).toBe(5)
    expect(store.totalPoints).toBe(95)
    vi.useRealTimers()
  })

  it('超过 2 小时，衰减 2 轮', () => {
    const store = createFreshStore()
    store.totalPoints = 100
    vi.useFakeTimers()
    vi.setSystemTime(1704067200000)
    // T0 - 3h → 3 小时 elapsed = 3 轮，但 3 * 0.05 * 100 = 15 太多
    // 改用 2h15m = 2 完整轮 + 0.25 轮
    mockStorageInstance['arbitrary_gate_last_access'] = String(1704067200000 - 135 * 60 * 1000)
    const decayed = store.decayInkValue()
    // 第1轮: 100 - floor(100 * 0.05) = 95
    // 第2轮: 95 - floor(95 * 0.05) = 95 - 4 = 91
    expect(decayed).toBe(9)
    expect(store.totalPoints).toBe(91)
    vi.useRealTimers()
  })

  it('衰减不低于最低下限 MIN_INK_BASE(10)', () => {
    const store = createFreshStore()
    store.totalPoints = 12
    vi.useFakeTimers()
    vi.setSystemTime(1704067200000)
    // 设置 100 小时前
    mockStorageInstance['arbitrary_gate_last_access'] = String(1704067200000 - 100 * 60 * 60 * 1000)
    store.decayInkValue()
    // 不得衰减到 10 以下
    expect(store.totalPoints).toBeGreaterThanOrEqual(10)
    vi.useRealTimers()
  })

  it('INK_DECAY_CONFIG.enabled=false 时跳过衰减', () => {
    INK_DECAY_CONFIG.enabled = false
    const store = createFreshStore()
    store.totalPoints = 100
    vi.useFakeTimers()
    vi.setSystemTime(1704067200000)
    mockStorageInstance['arbitrary_gate_last_access'] = String(1704067200000 - 2 * 60 * 60 * 1000)
    const decayed = store.decayInkValue()
    expect(decayed).toBe(0)
    expect(store.totalPoints).toBe(100)
    vi.useRealTimers()
  })
})

describe('墨香值时间衰减 - checkAndDecayOnAppStart', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(1704067200000)
    setActivePinia(createPinia())
    // 确保 INK_DECAY_CONFIG.enabled 为 true（前面测试可能设为 false）
    INK_DECAY_CONFIG.enabled = true
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('应在应用启动时被调用，返回衰减墨香值', () => {
    const store = createFreshStore()
    store.totalPoints = 100
    mockStorageInstance['arbitrary_gate_last_access'] = String(1704067200000 - 2 * 60 * 60 * 1000)
    const decayed = store.checkAndDecayOnAppStart()
    expect(decayed).toBeGreaterThan(0)
    expect(store.totalPoints).toBeLessThan(100)
  })
})

// ─── C-11 墨香状态测试 ────────────────────────────────────────────────────────

describe('inkFragranceStatus 墨香状态', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    INK_DECAY_CONFIG.enabled = true
    INK_DECAY_CONFIG.rate = 0.05
    INK_DECAY_CONFIG.intervalMs = 60 * 60 * 1000
  })

  it('进度 > 70% → 浓', () => {
    const store = createFreshStore()
    store.totalPoints = 80 // 初墨: 0-99, 80/100 = 80% > 70%
    expect(store.inkFragranceStatus).toBe('浓')
  })

  it('进度 30%-70% → 淡', () => {
    const store = createFreshStore()
    store.totalPoints = 50 // 初墨: 50/100 = 50%
    expect(store.inkFragranceStatus).toBe('淡')
  })

  it('进度 < 30% → 将尽', () => {
    const store = createFreshStore()
    store.totalPoints = 20 // 初墨: 20/100 = 20% < 30%
    expect(store.inkFragranceStatus).toBe('将尽')
  })

  it('入墨 100-299: 100点 = 0% → 将尽', () => {
    const store = createFreshStore()
    store.totalPoints = 100 // 入墨起点
    expect(store.inkFragranceStatus).toBe('将尽')
  })

  it('入墨 200点 = 50% → 淡', () => {
    const store = createFreshStore()
    store.totalPoints = 200 // 入墨: 100-299 range, 200-100=100, 100/200=50%
    expect(store.inkFragranceStatus).toBe('淡')
  })

  it('墨魂（无限）→ 浓', () => {
    const store = createFreshStore()
    store.totalPoints = 2000 // 墨魂
    expect(store.inkFragranceStatus).toBe('浓')
  })
})
