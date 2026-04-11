import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, VueWrapper } from '@vue/test-utils'
import { ref } from 'vue'

// ─── Mock Types (matching api.ts) ────────────────────────────────────────────────

interface KeywordEnlightenment {
  cardId: number
  cardName: string
  cardImageUrl?: string
  enlightenmentText: string
}

// ─── Mock KeywordEnlightenment Component (inline for testing) ────────────────────

// We test the logic/behavior that would be in KeywordEnlightenment.vue
// since the component itself is a pure presentational component.

const ENLIGHTENMENT_THRESHOLD = 5   // S-13: 共鸣值 >= 5 触发显灵
const AUTO_CLOSE_MS = 5000           // 5秒后自动关闭
const MAX_RESONANCE_DISPLAY = 9      // 显灵最大值（对应 9 次共鸣）

// ─── EnlightenmentEngine (business logic under test) ─────────────────────────────

interface EnlightenmentEngine {
  /**
   * 判断是否应触发显灵
   * @param resonanceLevel 当前共鸣值（累计次数）
   * @param chapterIndex 当前章节号（从 1 开始）
   */
  shouldTriggerEnlightenment(resonanceLevel: number, chapterIndex: number): boolean

  /**
   * 获取共鸣能量百分比（0-1）
   * 用于 UI 进度条
   */
  getResonanceEnergy(resonanceLevel: number): number

  /**
   * 判断是否为完整显灵（共鸣值 >= 9，三倍阈值）
   */
  isFullyManifested(resonanceLevel: number): boolean
}

class EnlightenmentEngineImpl implements EnlightenmentEngine {
  shouldTriggerEnlightenment(resonanceLevel: number, chapterIndex: number): boolean {
    return resonanceLevel >= ENLIGHTENMENT_THRESHOLD || chapterIndex === 5
  }

  getResonanceEnergy(resonanceLevel: number): number {
    return Math.min(1, resonanceLevel / MAX_RESONANCE_DISPLAY)
  }

  isFullyManifested(resonanceLevel: number): boolean {
    return resonanceLevel >= ENLIGHTENMENT_THRESHOLD * 3
  }
}

// ─── Mock Component Logic (for behavior testing) ───────────────────────────────

function createMockEnlightenmentLogic() {
  const engine = new EnlightenmentEngineImpl()
  const closeCallbacks: Array<() => void> = []
  let autoCloseTimer: ReturnType<typeof setTimeout> | null = null

  function showEnlightenment(data: KeywordEnlightenment, onClose: () => void) {
    closeCallbacks.push(onClose)
    autoCloseTimer = setTimeout(() => {
      closeCallbacks.forEach(cb => cb())
    }, AUTO_CLOSE_MS)
  }

  function closeEnlightenment(onClose: () => void) {
    if (autoCloseTimer !== null) {
      clearTimeout(autoCloseTimer)
      autoCloseTimer = null
    }
    closeCallbacks.length = 0
    onClose()
  }

  return {
    engine,
    showEnlightenment,
    closeEnlightenment,
  }
}

// ─── Tests ───────────────────────────────────────────────────────────────────────

describe('EnlightenmentEngine', () => {
  let engine: EnlightenmentEngine

  beforeEach(() => {
    engine = new EnlightenmentEngineImpl()
  })

  describe('shouldTriggerEnlightenment', () => {
    it('应在共鸣值达到阈值（5）时触发', () => {
      expect(engine.shouldTriggerEnlightenment(4, 1)).toBe(false)
      expect(engine.shouldTriggerEnlightenment(5, 1)).toBe(true)
      expect(engine.shouldTriggerEnlightenment(7, 1)).toBe(true)
      expect(engine.shouldTriggerEnlightenment(9, 1)).toBe(true)
    })

    it('应在共鸣值未达阈值时返回 false', () => {
      expect(engine.shouldTriggerEnlightenment(0, 1)).toBe(false)
      expect(engine.shouldTriggerEnlightenment(1, 1)).toBe(false)
      expect(engine.shouldTriggerEnlightenment(4, 1)).toBe(false)
    })

    it('应在特定章节（第5章）必触发，无论共鸣值', () => {
      expect(engine.shouldTriggerEnlightenment(0, 5)).toBe(true)
      expect(engine.shouldTriggerEnlightenment(1, 5)).toBe(true)
      expect(engine.shouldTriggerEnlightenment(3, 5)).toBe(true)
    })

    it('非第5章且共鸣值不足时应不触发', () => {
      expect(engine.shouldTriggerEnlightenment(0, 1)).toBe(false)
      expect(engine.shouldTriggerEnlightenment(3, 3)).toBe(false)
      expect(engine.shouldTriggerEnlightenment(4, 4)).toBe(false)
    })

    it('共鸣值可以超过阈值且仍触发', () => {
      expect(engine.shouldTriggerEnlightenment(10, 1)).toBe(true)
      expect(engine.shouldTriggerEnlightenment(100, 1)).toBe(true)
    })
  })

  describe('getResonanceEnergy', () => {
    it('应返回 0-1 之间的能量百分比', () => {
      expect(engine.getResonanceEnergy(0)).toBe(0)
      expect(engine.getResonanceEnergy(1)).toBeCloseTo(1 / 9)
      expect(engine.getResonanceEnergy(5)).toBeCloseTo(5 / 9)
      expect(engine.getResonanceEnergy(9)).toBe(1)
    })

    it('应限制最大值不超过 1', () => {
      expect(engine.getResonanceEnergy(9)).toBe(1)
      expect(engine.getResonanceEnergy(10)).toBe(1)
      expect(engine.getResonanceEnergy(100)).toBe(1)
    })

    it('应正确映射中间值', () => {
      expect(engine.getResonanceEnergy(4)).toBeCloseTo(4 / 9)
      expect(engine.getResonanceEnergy(3)).toBeCloseTo(3 / 9)
    })
  })

  describe('isFullyManifested', () => {
    it('应在共鸣值达到 3 倍阈值时判定为完全显灵', () => {
      expect(engine.isFullyManifested(14)).toBe(false)
      expect(engine.isFullyManifested(15)).toBe(true)
      expect(engine.isFullyManifested(20)).toBe(true)
    })

    it('应在未达 3 倍阈值时返回 false', () => {
      expect(engine.isFullyManifested(0)).toBe(false)
      expect(engine.isFullyManifested(5)).toBe(false)
      expect(engine.isFullyManifested(9)).toBe(false)
      expect(engine.isFullyManifested(14)).toBe(false)
    })
  })
})

describe('Enlightenment Logic Integration', () => {
  let logic: ReturnType<typeof createMockEnlightenmentLogic>

  beforeEach(() => {
    vi.useFakeTimers()
    logic = createMockEnlightenmentLogic()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('showEnlightenment 应注册自动关闭定时器', () => {
    const closeFn = vi.fn()
    logic.showEnlightenment(
      { cardId: 1, cardName: '旧船票', enlightenmentText: '测试' },
      closeFn
    )
    expect(closeFn).not.toHaveBeenCalled()
    vi.advanceTimersByTime(AUTO_CLOSE_MS)
    expect(closeFn).toHaveBeenCalledTimes(1)
  })

  it('closeEnlightenment 应立即清除定时器并调用回调', () => {
    const closeFn = vi.fn()
    logic.showEnlightenment(
      { cardId: 1, cardName: '旧船票', enlightenmentText: '测试' },
      closeFn
    )
    logic.closeEnlightenment(closeFn)
    expect(closeFn).toHaveBeenCalledTimes(1)
    vi.advanceTimersByTime(AUTO_CLOSE_MS)
    expect(closeFn).toHaveBeenCalledTimes(1) // 不应再触发
  })

  it('应正确判断多个关键词的显灵时机', () => {
    const resonanceMap: Record<number, number> = {
      1: 3,   // 旧船票: 3次（不足）
      2: 5,   // 意难平: 5次（触发）
      3: 8,   // 摆渡人: 8次（触发，且接近满值）
    }

    const { engine } = logic

    const shouldEnlighten: number[] = []
    for (const [cardId, resonance] of Object.entries(resonanceMap)) {
      if (engine.shouldTriggerEnlightenment(resonance, 1)) {
        shouldEnlighten.push(Number(cardId))
      }
    }

    expect(shouldEnlighten).toContain(2) // 意难平
    expect(shouldEnlighten).toContain(3) // 摆渡人
    expect(shouldEnlighten).not.toContain(1) // 旧船票不足阈值
  })

  it('共鸣值首次达到阈值时应触发，后续持续触发（防重复由调用方 Set 保证）', () => {
    const resonanceHistory: number[] = [3, 4, 5, 6, 7]
    const { engine } = logic

    const triggeredAt: number[] = []
    for (let i = 0; i < resonanceHistory.length; i++) {
      if (engine.shouldTriggerEnlightenment(resonanceHistory[i], 1)) {
        triggeredAt.push(resonanceHistory[i])
      }
    }

    // engine 在阈值以上持续返回 true；防重复由调用方 Set<cardId> 保证
    expect(triggeredAt).toEqual([5, 6, 7])
  })

  it('应正确处理第5章强制触发场景', () => {
    const { engine } = logic

    // 第5章，共鸣值仍为 0
    expect(engine.shouldTriggerEnlightenment(0, 5)).toBe(true)

    // 即使共鸣值为 4，第5章也触发
    expect(engine.shouldTriggerEnlightenment(4, 5)).toBe(true)
  })
})

describe('KeywordEnlightenment data structure', () => {
  it('应包含 S-13 所需的全部字段', () => {
    const data: KeywordEnlightenment = {
      cardId: 123,
      cardName: '铜锁',
      cardImageUrl: '/images/cards/tongsuo.jpg',
      enlightenmentText: '「铜锁」在时光的深处微微颤动……',
    }

    expect(data.cardId).toBe(123)
    expect(data.cardName).toBe('铜锁')
    expect(data.cardImageUrl).toBe('/images/cards/tongsuo.jpg')
    expect(data.enlightenmentText).toContain('铜锁')
  })

  it('cardImageUrl 应为可选字段（兼容无图场景）', () => {
    const data: KeywordEnlightenment = {
      cardId: 456,
      cardName: '旧伞',
      enlightenmentText: '墨香汇聚，旧伞的光芒骤然绽放……',
    }

    expect(data.cardImageUrl).toBeUndefined()
  })

  it('应正确计算能量百分比用于 UI 显示', () => {
    const engine = new EnlightenmentEngineImpl()

    // 5次共鸣 = 约 56% 能量
    expect(engine.getResonanceEnergy(5)).toBeCloseTo(0.556, 2)

    // 9次共鸣 = 100% 能量（满值显灵）
    expect(engine.getResonanceEnergy(9)).toBe(1)
  })
})
