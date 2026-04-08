/**
 * 历史偏离度计算单元测试
 *
 * 测试覆盖：
 * - 历史偏离度初始值为50
 * - 选择导致偏离度正确增减
 * - 偏离度范围限制在0-100
 * - 不同价值观选择对偏离度的影响
 */

import { describe, it, expect, beforeEach } from 'vitest'

// ==================== 类型定义 ====================

type ChoiceValue = 'self' | 'duty' | 'chaos'
type HistoricalVerdict = 'aligned' | 'deviated' | 'wildly_deviated'

interface StoryChoice {
  id: number
  text: string
  valueOrientation: ChoiceValue
  deviationImpact: number // -20 到 +20，正值表示偏离历史
  historicalVerdict: HistoricalVerdict
}

interface ChoiceConsequence {
  choiceId: number
  deviationChange: number
  narrativeNote: string
}

// ==================== 历史偏离度引擎 ====================

class HistoryDeviationEngine {
  private deviation: number = 50
  private history: ChoiceConsequence[] = []

  /**
   * 获取当前偏离度
   */
  getDeviation(): number {
    return this.deviation
  }

  /**
   * 获取偏离状态描述
   */
  getDeviationStatus(): { value: number; label: string; description: string } {
    let label: string
    let description: string

    if (this.deviation < 20) {
      label = '亦步亦趋'
      description = '与历史主线高度一致'
    } else if (this.deviation < 40) {
      label = '略有偏移'
      description = '小处改动，大势不变'
    } else if (this.deviation <= 60) {
      label = '历史分叉'
      description = '已产生明显的平行世界效应'
    } else if (this.deviation <= 80) {
      label = '全然改写'
      description = '与史书记载大相径庭'
    } else {
      label = '荒诞不经'
      description = '完全脱离史实，创造新历史'
    }

    return { value: this.deviation, label, description }
  }

  /**
   * 根据判官评估计算偏离度变化
   * @param verdict 判官给出的历史裁决
   */
  applyVerdict(verdict: HistoricalVerdict): number {
    let change = 0

    switch (verdict) {
      case 'aligned':
        // 与历史一致，偏离度降低
        change = -10
        break
      case 'deviated':
        // 轻度偏离
        change = +5
        break
      case 'wildly_deviated':
        // 严重偏离
        change = +15
        break
    }

    return this.applyChange(change)
  }

  /**
   * 应用选择带来的偏离度变化
   */
  applyChoice(choice: StoryChoice): number {
    // 基本偏离值（由判官预先计算的综合影响）
    let change = choice.deviationImpact

    // 全局边界由 applyChange 的 Math.max(0, Math.min(100,...)) 保证
    const actualChange = this.applyChange(change)

    this.history.push({
      choiceId: choice.id,
      deviationChange: actualChange,
      narrativeNote: this.generateNarrativeNote(choice, actualChange),
    })

    return actualChange
  }

  /**
   * 获取裁决影响值
   */
  private getVerdictImpact(verdict: HistoricalVerdict): number {
    switch (verdict) {
      case 'aligned':
        return -5
      case 'deviated':
        return +5
      case 'wildly_deviated':
        return +15
    }
  }

  /**
   * 应用偏离度变化（带边界限制）
   */
  private applyChange(change: number): number {
    this.deviation = Math.max(0, Math.min(100, this.deviation + change))
    return change
  }

  /**
   * 生成叙事备注
   */
  private generateNarrativeNote(choice: StoryChoice, actualChange: number): string {
    if (actualChange < 0) {
      return `历史进程: ${choice.text}，与史书记载吻合`
    } else if (actualChange === 0) {
      return `历史进程: ${choice.text}，未影响历史走向`
    } else {
      return `历史偏移: ${choice.text}，平行世界效应增强`
    }
  }

  /**
   * 获取选择历史
   */
  getHistory(): ChoiceConsequence[] {
    return [...this.history]
  }

  /**
   * 重置引擎状态
   */
  reset(): void {
    this.deviation = 50
    this.history = []
  }

  /**
   * 获取故事的最终历史评价
   */
  getFinalVerdict(): { rating: string; description: string } {
    if (this.deviation <= 20) {
      return {
        rating: '史官楷模',
        description: '完美还原历史现场，字字有据可查',
      }
    } else if (this.deviation <= 50) {
      return {
        rating: '稗官野史',
        description: '七分史实三分虚构，别有风味',
      }
    } else if (this.deviation <= 80) {
      return {
        rating: '民间传奇',
        description: '历史人物新编，茶余饭后谈资',
      }
    } else {
      return {
        rating: '纯属虚构',
        description: '如有雷同纯属巧合的原创故事',
      }
    }
  }
}

// ==================== 测试用例 ====================

describe('HistoryDeviationEngine - 历史偏离度计算', () => {
  let engine: HistoryDeviationEngine

  beforeEach(() => {
    engine = new HistoryDeviationEngine()
  })

  describe('初始状态', () => {
    it('should start with deviation value of 50', () => {
      expect(engine.getDeviation()).toBe(50)
    })

    it('should have empty history initially', () => {
      expect(engine.getHistory()).toEqual([])
    })
  })

  describe('基础偏离度计算', () => {
    it('should increase deviation when choice deviates from history', () => {
      const choice: StoryChoice = {
        id: 1,
        text: '劝降而非死战',
        valueOrientation: 'self',
        deviationImpact: 15,
        historicalVerdict: 'deviated',
      }

      engine.applyChoice(choice)

      expect(engine.getDeviation()).toBeGreaterThan(50)
    })

    it('should decrease deviation when choice aligns with history', () => {
      const alignedChoice: StoryChoice = {
        id: 1,
        text: '按照史书记载行动',
        valueOrientation: 'duty',
        deviationImpact: -15,
        historicalVerdict: 'aligned',
      }

      engine.applyChoice(alignedChoice)

      expect(engine.getDeviation()).toBeLessThan(50)
    })

    it('should accumulate deviation changes correctly', () => {
      const choices: StoryChoice[] = [
        { id: 1, text: '选择A', valueOrientation: 'self', deviationImpact: 10, historicalVerdict: 'deviated' },
        { id: 2, text: '选择B', valueOrientation: 'self', deviationImpact: 10, historicalVerdict: 'deviated' },
        { id: 3, text: '选择C', valueOrientation: 'duty', deviationImpact: -10, historicalVerdict: 'aligned' },
      ]

      choices.forEach(choice => engine.applyChoice(choice))

      // applyChoice only uses deviationImpact, verdict adjustments not applied there
      // +10 + 10 - 10 = 10;  50 + 10 = 60
      const expectedDeviation = 60
      expect(engine.getDeviation()).toBe(expectedDeviation)
    })
  })

  describe('偏离度边界限制', () => {
    it('should not allow deviation below 0', () => {
      // 连续使用align选择，偏离度应该趋近0但不低于0
      for (let i = 0; i < 10; i++) {
        engine.applyChoice({
          id: i,
          text: '完全符合历史',
          valueOrientation: 'duty',
          deviationImpact: -20,
          historicalVerdict: 'aligned',
        })
      }

      expect(engine.getDeviation()).toBeGreaterThanOrEqual(0)
      expect(engine.getDeviation()).toBeLessThanOrEqual(100)
    })

    it('should not allow deviation above 100', () => {
      // 连续使用偏离选择，偏离度应该趋近100但不超过100
      for (let i = 0; i < 10; i++) {
        engine.applyChoice({
          id: i,
          text: '完全偏离历史',
          valueOrientation: 'chaos',
          deviationImpact: 20,
          historicalVerdict: 'wildly_deviated',
        })
      }

      expect(engine.getDeviation()).toBeLessThanOrEqual(100)
      expect(engine.getDeviation()).toBeGreaterThanOrEqual(0)
    })

    it('should clamp single large change within bounds', () => {
      const extremeChoice: StoryChoice = {
        id: 1,
        text: '极端选择',
        valueOrientation: 'chaos',
        deviationImpact: 100, // 超大值
        historicalVerdict: 'wildly_deviated',
      }

      engine.applyChoice(extremeChoice)

      // 应该被限制在100
      expect(engine.getDeviation()).toBe(100)
    })
  })

  describe('偏离度状态标签', () => {
    it('should return correct status for low deviation', () => {
      // 手动设置低偏离度
      for (let i = 0; i < 5; i++) {
        engine.applyChoice({
          id: i,
          text: 'align',
          valueOrientation: 'duty',
          deviationImpact: -15,
          historicalVerdict: 'aligned',
        })
      }

      const status = engine.getDeviationStatus()
      expect(['亦步亦趋', '略有偏移']).toContain(status.label)
    })

    it('should return correct status for medium deviation', () => {
      // 设置中等偏离度
      const status = engine.getDeviationStatus()
      expect(status.label).toBe('历史分叉')
    })

    it('should return correct status for high deviation', () => {
      // 设置高偏离度
      for (let i = 0; i < 5; i++) {
        engine.applyChoice({
          id: i,
          text: 'deviate',
          valueOrientation: 'chaos',
          deviationImpact: 15,
          historicalVerdict: 'wildly_deviated',
        })
      }

      const status = engine.getDeviationStatus()
      expect(['全然改写', '荒诞不经']).toContain(status.label)
    })
  })

  describe('裁决系统', () => {
    it('should apply negative change for aligned verdict', () => {
      const before = engine.getDeviation()
      engine.applyVerdict('aligned')
      const after = engine.getDeviation()

      expect(after).toBeLessThan(before)
    })

    it('should apply positive change for deviated verdict', () => {
      engine.reset()
      const before = engine.getDeviation()
      engine.applyVerdict('deviated')
      const after = engine.getDeviation()

      expect(after).toBeGreaterThan(before)
    })

    it('should apply larger positive change for wildly deviated verdict', () => {
      engine.reset()
      engine.applyVerdict('deviated')
      const deviation1 = engine.getDeviation()

      engine.reset()
      engine.applyVerdict('wildly_deviated')
      const deviation2 = engine.getDeviation()

      expect(deviation2).toBeGreaterThan(deviation1)
    })
  })

  describe('叙事历史记录', () => {
    it('should record each choice in history', () => {
      const choices: StoryChoice[] = [
        { id: 1, text: '选择1', valueOrientation: 'self', deviationImpact: 5, historicalVerdict: 'deviated' },
        { id: 2, text: '选择2', valueOrientation: 'duty', deviationImpact: -5, historicalVerdict: 'aligned' },
      ]

      choices.forEach(choice => engine.applyChoice(choice))

      const history = engine.getHistory()
      expect(history.length).toBe(2)
      expect(history[0].choiceId).toBe(1)
      expect(history[1].choiceId).toBe(2)
    })

    it('should generate narrative notes for each choice', () => {
      engine.applyChoice({
        id: 1,
        text: '测试选择',
        valueOrientation: 'self',
        deviationImpact: 10,
        historicalVerdict: 'deviated',
      })

      const history = engine.getHistory()
      expect(history[0].narrativeNote).toContain('测试选择')
    })
  })

  describe('最终历史评价', () => {
    it('should return "史官楷模" for very low deviation', () => {
      for (let i = 0; i < 6; i++) {
        engine.applyChoice({
          id: i,
          text: 'align',
          valueOrientation: 'duty',
          deviationImpact: -20,
          historicalVerdict: 'aligned',
        })
      }

      const verdict = engine.getFinalVerdict()
      expect(verdict.rating).toBe('史官楷模')
    })

    it('should return "纯属虚构" for very high deviation', () => {
      for (let i = 0; i < 5; i++) {
        engine.applyChoice({
          id: i,
          text: 'chaos',
          valueOrientation: 'chaos',
          deviationImpact: 20,
          historicalVerdict: 'wildly_deviated',
        })
      }

      const verdict = engine.getFinalVerdict()
      expect(verdict.rating).toBe('纯属虚构')
    })
  })

  describe('重置功能', () => {
    it('should reset deviation to 50', () => {
      // 先做些改变
      engine.applyChoice({
        id: 1,
        text: '偏离',
        valueOrientation: 'chaos',
        deviationImpact: 20,
        historicalVerdict: 'wildly_deviated',
      })

      engine.reset()

      expect(engine.getDeviation()).toBe(50)
      expect(engine.getHistory()).toEqual([])
    })
  })
})

// ==================== 边界测试 ====================

describe('HistoryDeviationEngine - 边界条件', () => {
  let engine: HistoryDeviationEngine

  beforeEach(() => {
    engine = new HistoryDeviationEngine()
  })

  it('should handle zero impact choices', () => {
    const choice: StoryChoice = {
      id: 1,
      text: '无影响选择',
      valueOrientation: 'self',
      deviationImpact: 0,
      historicalVerdict: 'aligned',
    }

    engine.applyChoice(choice)
    expect(engine.getDeviation()).toBe(50)
  })

  it('should handle rapid consecutive choices', () => {
    for (let i = 0; i < 100; i++) {
      engine.applyChoice({
        id: i,
        text: `选择${i}`,
        valueOrientation: 'self',
        deviationImpact: 10,
        historicalVerdict: 'deviated',
      })
    }

    // 偏离度应该被限制在100
    expect(engine.getDeviation()).toBe(100)
  })

  it('should maintain history integrity under rapid choices', () => {
    for (let i = 0; i < 50; i++) {
      engine.applyChoice({
        id: i,
        text: `选择${i}`,
        valueOrientation: 'self',
        deviationImpact: 5,
        historicalVerdict: 'deviated',
      })
    }

    const history = engine.getHistory()
    expect(history.length).toBe(50)
  })
})
