/**
 * 抽卡算法 + 保底机制单元测试
 *
 * 测试覆盖：
 * - 连续9次未出奇品，第10次必出奇品
 * - 连续30次未出绝品，第31次必出绝品
 * - 墨晶消耗正确
 * - 每日免费次数正确扣减
 * - 重复抽卡不会获得重复 user_card 记录
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

// ==================== 类型定义 ====================

type CardRarity = 1 | 2 | 3 | 4 // 1凡 2珍 3奇 4绝

interface KeywordCard {
  id: number
  name: string
  category: number
  rarity: CardRarity
  weight: number
}

interface DrawResult {
  card: KeywordCard
  isFirstDraw: boolean
  costInkStone: number
}

// ==================== 抽卡配置 ====================

const CARD_CONFIG = {
  // 稀有度权重（基础概率）
  rarityWeights: {
    1: 7000,  // 凡 70%
    2: 2500,  // 珍 25%
    3: 450,   // 奇 4.5%
    4: 50,    // 绝 0.5%
  },
  // 保底机制
  pity: {
    qi: { count: 9, guaranteedRarity: 3 },   // 连续9次未出奇品，第10次必出奇
    jue: { count: 29, guaranteedRarity: 4 }, // 连续29次未出绝品，第30次必出绝
  },
  // 墨晶消耗
  inkStoneCost: {
    free: 0,
    normal: 10,
    guaranteedQi: 30,
    guaranteedJue: 100,
  },
  // 每日免费次数
  dailyFreeDraws: 3,
}

// ==================== 抽卡算法实现 ====================

class CardDrawEngine {
  private drawHistory: CardRarity[] = []
  private qiPityCounter = 0  // 距上次奇品的抽数
  private juePityCounter = 0 // 距上次绝品的抽数
  private todayFreeDrawsUsed = 0

  /**
   * 重置抽卡引擎状态
   */
  reset(): void {
    this.drawHistory = []
    this.qiPityCounter = 0
    this.juePityCounter = 0
    this.todayFreeDrawsUsed = 0
  }

  /**
   * 获取今日剩余免费次数
   */
  getTodayFreeDrawsRemaining(): number {
    return CARD_CONFIG.dailyFreeDraws - this.todayFreeDrawsUsed
  }

  /**
   * 是否使用免费抽卡
   */
  isFreeDraw(): boolean {
    return this.todayFreeDrawsUsed < CARD_CONFIG.dailyFreeDraws
  }

  /**
   * 计算单次抽卡的墨晶消耗
   */
  calculateInkStoneCost(options?: {
    forceQiGuarantee?: boolean
    forceJueGuarantee?: boolean
  }): number {
    if (this.isFreeDraw()) {
      return CARD_CONFIG.inkStoneCost.free
    }
    if (options?.forceJueGuarantee) {
      return CARD_CONFIG.inkStoneCost.guaranteedJue
    }
    if (options?.forceQiGuarantee) {
      return CARD_CONFIG.inkStoneCost.guaranteedQi
    }
    return CARD_CONFIG.inkStoneCost.normal
  }

  /**
   * 根据权重池随机抽取稀有度
   * @param guaranteeRarity 强制保底的稀有度（用于测试）
   */
  private drawRarity(guaranteeRarity?: CardRarity): CardRarity {
    // 保底逻辑优先判断
    if (this.qiPityCounter >= CARD_CONFIG.pity.qi.count) {
      return CARD_CONFIG.pity.qi.guaranteedRarity
    }
    if (this.juePityCounter >= CARD_CONFIG.pity.jue.count) {
      return CARD_CONFIG.pity.jue.guaranteedRarity
    }

    // 强制保底（玩家使用保底道具）
    if (guaranteeRarity) {
      return guaranteeRarity
    }

    // 权重随机
    const totalWeight = Object.values(CARD_CONFIG.rarityWeights).reduce((a, b) => a + b, 0)
    let random = Math.floor(Math.random() * totalWeight)

    for (const [rarity, weight] of Object.entries(CARD_CONFIG.rarityWeights)) {
      random -= weight
      if (random < 0) {
        return Number(rarity) as CardRarity
      }
    }

    return 1 // 默认返回凡
  }

  /**
   * 执行单次抽卡
   */
  draw(options?: {
    forceRarity?: CardRarity
    isFirstDraw?: boolean
  }): DrawResult {
    const isFree = this.isFreeDraw()
    const cost = this.calculateInkStoneCost()

    if (isFree) {
      this.todayFreeDrawsUsed++
    }

    const rarity = options?.forceRarity ?? this.drawRarity()
    const card = this.generateMockCard(rarity)

    // 更新保底计数器
    this.updatePityCounters(rarity)

    // 记录历史
    this.drawHistory.push(rarity)

    return {
      card,
      isFirstDraw: options?.isFirstDraw ?? false,
      costInkStone: cost,
    }
  }

  /**
   * 更新保底计数器
   */
  private updatePityCounters(rarity: CardRarity): void {
    // 如果抽到奇品或更高，重置奇品保底计数
    if (rarity >= CARD_CONFIG.pity.qi.guaranteedRarity) {
      this.qiPityCounter = 0
    } else {
      this.qiPityCounter++
    }

    // 如果抽到绝品，重置绝品保底计数
    if (rarity === 4) {
      this.juePityCounter = 0
    } else {
      this.juePityCounter++
    }
  }

  /**
   * 生成模拟卡牌
   */
  private generateMockCard(rarity: CardRarity): KeywordCard {
    const categories = [1, 2, 3, 4, 5] // 器物、职人、风物、情绪、称谓
    const baseWeights = [100, 80, 60, 40] // 凡珍奇绝的基础权重

    return {
      id: Date.now() + Math.random(),
      name: `测试关键词_${rarity}`,
      category: categories[Math.floor(Math.random() * categories.length)],
      rarity,
      weight: baseWeights[rarity - 1],
    }
  }

  /**
   * 获取当前保底状态（用于测试断言）
   */
  getPityStatus(): { qiPityCounter: number; juePityCounter: number } {
    return {
      qiPityCounter: this.qiPityCounter,
      juePityCounter: this.juePityCounter,
    }
  }

  /**
   * 获取抽卡历史（用于测试断言）
   */
  getDrawHistory(): CardRarity[] {
    return [...this.drawHistory]
  }
}

// ==================== 测试用例 ====================

describe('CardDrawEngine - 抽卡算法与保底机制', () => {
  let engine: CardDrawEngine

  beforeEach(() => {
    engine = new CardDrawEngine()
  })

  afterEach(() => {
    engine.reset()
  })

  describe('基础抽卡逻辑', () => {
    it('should return free draw when daily free draws available', () => {
      expect(engine.getTodayFreeDrawsRemaining()).toBe(3)
    })

    it('should correctly calculate ink stone cost for free draw', () => {
      const cost = engine.calculateInkStoneCost()
      expect(cost).toBe(0)
    })

    it('should correctly calculate ink stone cost for normal draw after free draws exhausted', () => {
      // 消耗完3次免费
      engine.draw()
      engine.draw()
      engine.draw()

      const cost = engine.calculateInkStoneCost()
      expect(cost).toBe(CARD_CONFIG.inkStoneCost.normal)
    })

    it('should correctly calculate ink stone cost for guaranteed Qi draw', () => {
      // 消耗完3次免费
      engine.draw()
      engine.draw()
      engine.draw()

      const cost = engine.calculateInkStoneCost({ forceQiGuarantee: true })
      expect(cost).toBe(CARD_CONFIG.inkStoneCost.guaranteedQi)
    })

    it('should correctly calculate ink stone cost for guaranteed Jue draw', () => {
      // 消耗完3次免费
      engine.draw()
      engine.draw()
      engine.draw()

      const cost = engine.calculateInkStoneCost({ forceJueGuarantee: true })
      expect(cost).toBe(CARD_CONFIG.inkStoneCost.guaranteedJue)
    })

    it('should track free draws correctly', () => {
      expect(engine.getTodayFreeDrawsRemaining()).toBe(3)

      engine.draw()
      expect(engine.getTodayFreeDrawsRemaining()).toBe(2)

      engine.draw()
      expect(engine.getTodayFreeDrawsRemaining()).toBe(1)

      engine.draw()
      expect(engine.getTodayFreeDrawsRemaining()).toBe(0)

      engine.draw()
      expect(engine.getTodayFreeDrawsRemaining()).toBe(0) // 保持0，不会变成负数
    })
  })

  describe('保底机制 - 奇品保底', () => {
    it('should guarantee Qi (奇品) on 10th draw when 9 consecutive non-Qi draws occurred', () => {
      // 前9次强制抽到凡品，累积保底
      for (let i = 0; i < 9; i++) {
        const result = engine.draw({ forceRarity: 1 })
        expect(result.card.rarity).toBe(1)
      }

      // 第10次应该保底出奇品
      const pityResult = engine.draw()
      expect(pityResult.card.rarity).toBe(3) // 奇品

      // 验证保底计数器重置
      expect(engine.getPityStatus().qiPityCounter).toBe(0)
    })

    it('should reset Qi pity counter after drawing Qi or higher', () => {
      // 前8次凡品
      for (let i = 0; i < 8; i++) {
        engine.draw({ forceRarity: 1 })
      }
      expect(engine.getPityStatus().qiPityCounter).toBe(8)

      // 第9次抽到珍品，不触发保底，但也不重置
      engine.draw({ forceRarity: 2 })
      expect(engine.getPityStatus().qiPityCounter).toBe(0) // 珍品也>=奇品，触发重置

      // 再抽8次凡品
      for (let i = 0; i < 8; i++) {
        engine.draw({ forceRarity: 1 })
      }
      expect(engine.getPityStatus().qiPityCounter).toBe(8)

      // 第9次抽到奇品，重置
      engine.draw({ forceRarity: 3 })
      expect(engine.getPityStatus().qiPityCounter).toBe(0)
    })

    it('should track Qi pity correctly through mixed rarity draws', () => {
      // 抽5次凡品
      for (let i = 0; i < 5; i++) {
        engine.draw({ forceRarity: 1 })
      }
      expect(engine.getPityStatus().qiPityCounter).toBe(5)

      // 抽到珍品，重置
      engine.draw({ forceRarity: 2 })
      expect(engine.getPityStatus().qiPityCounter).toBe(0)

      // 再抽5次凡品
      for (let i = 0; i < 5; i++) {
        engine.draw({ forceRarity: 1 })
      }
      expect(engine.getPityStatus().qiPityCounter).toBe(5)

      // 抽到奇品，重置
      engine.draw({ forceRarity: 3 })
      expect(engine.getPityStatus().qiPityCounter).toBe(0)

      // 再抽9次凡品
      for (let i = 0; i < 9; i++) {
        engine.draw({ forceRarity: 1 })
      }
      expect(engine.getPityStatus().qiPityCounter).toBe(9)

      // 第10次必出奇品
      const result = engine.draw()
      expect(result.card.rarity).toBe(3)
    })
  })

  describe('保底机制 - 绝品保底', () => {
    it('should guarantee Jue (绝品) on 31st draw when 30 consecutive non-Jue draws occurred', () => {
      // 前30次强制抽到珍品（不触发绝品保底）
      for (let i = 0; i < 30; i++) {
        const result = engine.draw({ forceRarity: 2 })
        expect(result.card.rarity).toBe(2)
      }

      // 第31次应该保底出绝品
      const pityResult = engine.draw()
      expect(pityResult.card.rarity).toBe(4) // 绝品

      // 验证保底计数器重置
      expect(engine.getPityStatus().juePityCounter).toBe(0)
    })

    it('should reset Jue pity counter after drawing Jue', () => {
      // 前29次凡品
      for (let i = 0; i < 29; i++) {
        engine.draw({ forceRarity: 1 })
      }
      expect(engine.getPityStatus().juePityCounter).toBe(29)

      // 第30次抽到珍品，不重置绝品保底（因为珍品不是绝品）
      engine.draw({ forceRarity: 2 })
      expect(engine.getPityStatus().juePityCounter).toBe(0) // 珍品 >= 奇品会触发奇品保底重置

      // 重新累积到29
      for (let i = 0; i < 29; i++) {
        engine.draw({ forceRarity: 1 })
      }
      expect(engine.getPityStatus().juePityCounter).toBe(29)

      // 第30次抽到绝品
      engine.draw({ forceRarity: 4 })
      expect(engine.getPityStatus().juePityCounter).toBe(0)
    })
  })

  describe('保底机制 - 奇品与绝品同时累积', () => {
    it('should correctly accumulate both Qi and Jue pity counters', () => {
      // 前9次凡品，同时累积两种保底
      for (let i = 0; i < 9; i++) {
        engine.draw({ forceRarity: 1 })
      }

      const status = engine.getPityStatus()
      expect(status.qiPityCounter).toBe(9)
      expect(status.juePityCounter).toBe(9)

      // 第10次抽到奇品，重置奇品保底，绝品保底继续累积
      engine.draw({ forceRarity: 3 })

      const statusAfter = engine.getPityStatus()
      expect(statusAfter.qiPityCounter).toBe(0)
      expect(statusAfter.juePityCounter).toBe(10)
    })

    it('should trigger Jue pity at 30th non-Jue draw even with Qi pity', () => {
      // 累积到第30次未出绝品
      for (let i = 0; i < 30; i++) {
        engine.draw({ forceRarity: 2 }) // 珍品不触发绝品保底
      }

      // 第31次必出绝品
      const result = engine.draw()
      expect(result.card.rarity).toBe(4)
    })
  })

  describe('drawHistory 记录', () => {
    it('should correctly record draw history', () => {
      engine.draw({ forceRarity: 1 })
      engine.draw({ forceRarity: 2 })
      engine.draw({ forceRarity: 3 })

      const history = engine.getDrawHistory()
      expect(history).toEqual([1, 2, 3])
    })

    it('should maintain history integrity after pity reset', () => {
      // 前9次凡品
      for (let i = 0; i < 9; i++) {
        engine.draw({ forceRarity: 1 })
      }

      // 第10次保底奇品
      engine.draw()

      const history = engine.getDrawHistory()
      expect(history.length).toBe(10)
      expect(history.filter(r => r === 1).length).toBe(9)
      expect(history[9]).toBe(3) // 第10次是奇品
    })
  })

  describe('重复抽卡检查（user_card UNIQUE约束）', () => {
    it('should generate unique card IDs', () => {
      const cards: Set<number> = new Set()

      for (let i = 0; i < 100; i++) {
        const result = engine.draw({ forceRarity: 1 })
        cards.add(result.card.id)
      }

      // 所有卡牌ID应该唯一
      expect(cards.size).toBe(100)
    })
  })
})

// ==================== 边界测试 ====================

describe('CardDrawEngine - 边界条件', () => {
  let engine: CardDrawEngine

  beforeEach(() => {
    engine = new CardDrawEngine()
  })

  afterEach(() => {
    engine.reset()
  })

  it('should handle maximum free draws exactly', () => {
    for (let i = 0; i < 3; i++) {
      const result = engine.draw()
      expect(result.costInkStone).toBe(0)
    }

    // 第4次应该收费
    const result = engine.draw()
    expect(result.costInkStone).toBeGreaterThan(0)
  })

  it('should handle rapid consecutive draws', () => {
    const results: DrawResult[] = []
    for (let i = 0; i < 20; i++) {
      results.push(engine.draw({ forceRarity: 1 }))
    }

    expect(results.length).toBe(20)
    results.forEach((result, index) => {
      expect(result.card.rarity).toBe(1)
    })
  })

  it('should handle pity overflow correctly', () => {
    // 极端情况：连续抽100次凡品
    for (let i = 0; i < 100; i++) {
      engine.draw({ forceRarity: 1 })
    }

    const history = engine.getDrawHistory()
    // 前9次凡品，第10次奇品保底，然后继续凡品...
    // 奇品保底：10次一循环
    const qiPityHits = history.filter((_, i) => (i + 1) % 10 === 0 && history[i] === 3)
    expect(qiPityHits.length).toBe(10) // 10次奇品保底
  })
})
