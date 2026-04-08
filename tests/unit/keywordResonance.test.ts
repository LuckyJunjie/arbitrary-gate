/**
 * 关键词共鸣计算单元测试
 *
 * 测试覆盖：
 * - 用户选择与关键词精神气质一致时，共鸣值+1
 * - 共鸣值满格后触发"显灵"
 * - 关键词共鸣状态正确追踪
 */

import { describe, it, expect, beforeEach } from 'vitest'

// ==================== 类型定义 ====================

type ResonantState = 'dormant' | 'resonating' | 'manifested' // 休眠 | 共鸣中 | 已显灵

interface KeywordCard {
  id: number
  name: string
  category: number // 1器物 2职人 3风物 4情绪 5称谓
  spiritSignature: string[] // 精神气质标签
  rarity: number
}

interface ResonanceStatus {
  keywordId: number
  resonanceCount: number
  maxResonance: number
  state: ResonantState
  manifestedAt?: number // 显灵时间戳
}

interface Choice {
  id: number
  text: string
  resonanceTags: string[] // 选择带来的共鸣标签
  valueOrientation: 'self' | 'duty' | 'chaos' // 自我 | 道义 | 自由
}

// ==================== 关键词共鸣引擎 ====================

class KeywordResonanceEngine {
  private resonanceMap: Map<number, ResonanceStatus> = new Map()
  private manifestedKeywords: Set<number> = new Set()

  /**
   * 初始化关键词共鸣状态
   */
  initializeKeywords(keywords: KeywordCard[]): void {
    keywords.forEach(kw => {
      this.resonanceMap.set(kw.id, {
        keywordId: kw.id,
        resonanceCount: 0,
        maxResonance: this.calculateMaxResonance(kw.rarity),
        state: 'dormant',
      })
    })
  }

  /**
   * 根据稀有度计算最大共鸣次数
   * 凡=3, 珍=4, 奇=5, 绝=6
   */
  private calculateMaxResonance(rarity: number): number {
    const resonanceTable: Record<number, number> = {
      1: 3, // 凡
      2: 4, // 珍
      3: 5, // 奇
      4: 6, // 绝
    }
    return resonanceTable[rarity] ?? 3
  }

  /**
   * 判断选择是否与关键词产生共鸣
   * 精神气质匹配：选择标签与关键词精神气质有交集即+1共鸣
   */
  checkResonance(keyword: KeywordCard, choice: Choice): boolean {
    // 检查标签交集
    const hasMatch = choice.resonanceTags.some(tag =>
      keyword.spiritSignature.includes(tag)
    )

    if (hasMatch) {
      this.addResonance(keyword.id)
      return true
    }

    return false
  }

  /**
   * 增加共鸣值
   */
  addResonance(keywordId: number): ResonanceStatus {
    const status = this.resonanceMap.get(keywordId)
    if (!status) {
      throw new Error(`Keyword ${keywordId} not found in resonance map`)
    }

    if (status.state === 'manifested') {
      // 已显灵的关键词不再增加共鸣
      return status
    }

    status.resonanceCount++

    // 检查是否触发显灵
    if (status.resonanceCount >= status.maxResonance) {
      status.state = 'manifested'
      status.manifestedAt = Date.now()
      this.manifestedKeywords.add(keywordId)
    } else if (status.resonanceCount > 0) {
      status.state = 'resonating'
    }

    return status
  }

  /**
   * 获取关键词共鸣状态
   */
  getResonanceStatus(keywordId: number): ResonanceStatus | undefined {
    return this.resonanceMap.get(keywordId)
  }

  /**
   * 获取所有共鸣状态
   */
  getAllResonanceStatuses(): ResonanceStatus[] {
    return Array.from(this.resonanceMap.values())
  }

  /**
   * 获取已显灵的关键词
   */
  getManifestedKeywords(): number[] {
    return Array.from(this.manifestedKeywords)
  }

  /**
   * 检查是否有关键词显灵（触发特殊事件）
   */
  hasManifestation(): boolean {
    return this.manifestedKeywords.size > 0
  }

  /**
   * 获取共鸣进度（百分比）
   */
  getResonanceProgress(keywordId: number): number {
    const status = this.resonanceMap.get(keywordId)
    if (!status) return 0
    return (status.resonanceCount / status.maxResonance) * 100
  }

  /**
   * 重置引擎状态
   */
  reset(): void {
    this.resonanceMap.clear()
    this.manifestedKeywords.clear()
  }
}

// ==================== 测试用例 ====================

describe('KeywordResonanceEngine - 关键词共鸣计算', () => {
  let engine: KeywordResonanceEngine

  // 测试用关键词卡
  const testKeywords: KeywordCard[] = [
    {
      id: 1,
      name: '旧船票',
      category: 1, // 器物
      spiritSignature: ['离别', '等待', '归乡'],
      rarity: 2, // 珍
    },
    {
      id: 2,
      name: '说书匠',
      category: 2, // 职人
      spiritSignature: ['讲述', '传承', '真相'],
      rarity: 3, // 奇
    },
    {
      id: 3,
      name: '意难平',
      category: 4, // 情绪
      spiritSignature: ['遗憾', '执念', '放下'],
      rarity: 4, // 绝
    },
    {
      id: 4,
      name: '青石板',
      category: 3, // 风物
      spiritSignature: ['岁月', '沉默', '见证'],
      rarity: 1, // 凡
    },
  ]

  // 测试用选项
  const testChoices: Choice[] = [
    {
      id: 1,
      text: '登船离去，前路未知',
      resonanceTags: ['离别', '勇气'],
      valueOrientation: 'self',
    },
    {
      id: 2,
      text: '留下继续讲述真相',
      resonanceTags: ['讲述', '道义'],
      valueOrientation: 'duty',
    },
    {
      id: 3,
      text: '放下执念，归于平静',
      resonanceTags: ['放下', '释然'],
      valueOrientation: 'chaos',
    },
    {
      id: 4,
      text: '沉默以对',
      resonanceTags: ['沉默'],
      valueOrientation: 'self',
    },
  ]

  beforeEach(() => {
    engine = new KeywordResonanceEngine()
    engine.initializeKeywords(testKeywords)
  })

  describe('初始化与基础状态', () => {
    it('should initialize all keywords to dormant state', () => {
      const statuses = engine.getAllResonanceStatuses()

      expect(statuses.length).toBe(4)
      statuses.forEach(status => {
        expect(status.state).toBe('dormant')
        expect(status.resonanceCount).toBe(0)
      })
    })

    it('should set correct max resonance based on rarity', () => {
      // 凡=3, 珍=4, 奇=5, 绝=6
      expect(engine.getResonanceStatus(1)?.maxResonance).toBe(4) // 旧船票(珍)
      expect(engine.getResonanceStatus(2)?.maxResonance).toBe(5) // 说书匠(奇)
      expect(engine.getResonanceStatus(3)?.maxResonance).toBe(6) // 意难平(绝)
      expect(engine.getResonanceStatus(4)?.maxResonance).toBe(3) // 青石板(凡)
    })

    it('should throw error for non-existent keyword', () => {
      expect(() => {
        engine.checkResonance({ id: 999, name: '不存在', category: 1, spiritSignature: [], rarity: 1 }, testChoices[0])
      }).toThrow('Keyword 999 not found')
    })
  })

  describe('共鸣触发逻辑', () => {
    it('should add resonance when choice tags match keyword spirit signature', () => {
      const keyword = testKeywords[0] // 旧船票: ['离别', '等待', '归乡']
      const choice = testChoices[0] // 登船离去: ['离别', '勇气']

      const matched = engine.checkResonance(keyword, choice)

      expect(matched).toBe(true)
      expect(engine.getResonanceStatus(keyword.id)?.resonanceCount).toBe(1)
    })

    it('should not add resonance when choice tags do not match', () => {
      const keyword = testKeywords[0] // 旧船票: ['离别', '等待', '归乡']
      const choice = testChoices[2] // 放下执念: ['放下', '释然']

      const matched = engine.checkResonance(keyword, choice)

      expect(matched).toBe(false)
      expect(engine.getResonanceStatus(keyword.id)?.resonanceCount).toBe(0)
    })

    it('should transition from dormant to resonating state', () => {
      const keyword = testKeywords[0] // 旧船票
      const choice = testChoices[0] // 登船离去

      engine.checkResonance(keyword, choice)

      const status = engine.getResonanceStatus(keyword.id)
      expect(status?.state).toBe('resonating')
      expect(status?.resonanceCount).toBe(1)
    })

    it('should correctly track multiple resonances from different choices', () => {
      const keyword = testKeywords[0] // 旧船票

      // 第一次共鸣（离别）
      engine.checkResonance(keyword, testChoices[0])
      expect(engine.getResonanceStatus(keyword.id)?.resonanceCount).toBe(1)

      // 第二次共鸣（等待 - 假设有个选项包含等待标签）
      const choiceWithWait: Choice = {
        id: 5,
        text: '在渡口等待',
        resonanceTags: ['等待', '坚守'],
        valueOrientation: 'self',
      }
      engine.checkResonance(keyword, choiceWithWait)
      expect(engine.getResonanceStatus(keyword.id)?.resonanceCount).toBe(2)
    })
  })

  describe('显灵机制', () => {
    it('should trigger manifestation when resonance count reaches max', () => {
      const keyword = testKeywords[3] // 青石板(凡), maxResonance=3

      // 触发3次共鸣
      engine.checkResonance(keyword, testChoices[0]) // 离别 -> 青石板无共鸣
      // 手动添加共鸣来测试显灵
      for (let i = 0; i < 3; i++) {
        engine.addResonance(keyword.id)
      }

      const status = engine.getResonanceStatus(keyword.id)
      expect(status?.state).toBe('manifested')
      expect(engine.hasManifestation()).toBe(true)
      expect(engine.getManifestedKeywords()).toContain(keyword.id)
    })

    it('should record manifestation timestamp', () => {
      const keyword = testKeywords[3] // 青石板(凡)
      const beforeTime = Date.now()

      // 触发显灵
      for (let i = 0; i < 3; i++) {
        engine.addResonance(keyword.id)
      }

      const afterTime = Date.now()
      const status = engine.getResonanceStatus(keyword.id)

      expect(status?.manifestedAt).toBeDefined()
      expect(status?.manifestedAt).toBeGreaterThanOrEqual(beforeTime)
      expect(status?.manifestedAt).toBeLessThanOrEqual(afterTime)
    })

    it('should not add resonance after manifestation', () => {
      const keyword = testKeywords[3] // 青石板(凡)

      // 触发显灵
      for (let i = 0; i < 3; i++) {
        engine.addResonance(keyword.id)
      }

      const statusBefore = engine.getResonanceStatus(keyword.id)

      // 尝试再增加共鸣
      engine.addResonance(keyword.id)

      const statusAfter = engine.getResonanceStatus(keyword.id)
      expect(statusAfter?.resonanceCount).toBe(statusBefore?.resonanceCount)
    })

    it('should track multiple manifested keywords', () => {
      // 两个凡品关键词都显灵
      const keyword1 = testKeywords[3] // 青石板(凡), max=3
      const keyword2: KeywordCard = { ...testKeywords[3], id: 5, name: '老槐树' }
      engine.initializeKeywords([keyword2])

      for (let i = 0; i < 3; i++) {
        engine.addResonance(keyword1.id)
        engine.addResonance(keyword2.id)
      }

      expect(engine.getManifestedKeywords().length).toBe(2)
      expect(engine.hasManifestation()).toBe(true)
    })
  })

  describe('共鸣进度计算', () => {
    it('should calculate correct progress percentage', () => {
      const keyword = testKeywords[3] // 青石板(凡), max=3

      expect(engine.getResonanceProgress(keyword.id)).toBe(0)

      engine.addResonance(keyword.id)
      expect(engine.getResonanceProgress(keyword.id)).toBeCloseTo(33.33, 1)

      engine.addResonance(keyword.id)
      expect(engine.getResonanceProgress(keyword.id)).toBeCloseTo(66.67, 1)

      engine.addResonance(keyword.id)
      expect(engine.getResonanceProgress(keyword.id)).toBe(100)
    })

    it('should cap progress at 100% after manifestation', () => {
      const keyword = testKeywords[3] // 青石板(凡), max=3

      for (let i = 0; i < 5; i++) {
        engine.addResonance(keyword.id)
      }

      expect(engine.getResonanceProgress(keyword.id)).toBe(100)
    })
  })

  describe('精神气质匹配算法', () => {
    it('should match partial tags correctly', () => {
      const keyword = testKeywords[1] // 说书匠: ['讲述', '传承', '真相']
      const choice = testChoices[1] // 留下讲述真相: ['讲述', '道义']

      const matched = engine.checkResonance(keyword, choice)

      expect(matched).toBe(true)
      expect(engine.getResonanceStatus(keyword.id)?.resonanceCount).toBe(1)
    })

    it('should not match unrelated tags', () => {
      const keyword = testKeywords[2] // 意难平: ['遗憾', '执念', '放下']
      const choice = testChoices[0] // 登船离去: ['离别', '勇气']

      const matched = engine.checkResonance(keyword, choice)

      expect(matched).toBe(false)
    })

    it('should match multiple keywords in single choice', () => {
      // 假设一个选择同时匹配多个关键词
      const choiceWithMultipleMatches: Choice = {
        id: 6,
        text: '放下离别之苦',
        resonanceTags: ['放下', '离别'],
        valueOrientation: 'chaos',
      }

      // 关键词1: 旧船票 ['离别', '等待', '归乡']
      engine.checkResonance(testKeywords[0], choiceWithMultipleMatches)
      expect(engine.getResonanceStatus(testKeywords[0].id)?.resonanceCount).toBe(1)

      // 关键词3: 意难平 ['遗憾', '执念', '放下']
      engine.checkResonance(testKeywords[2], choiceWithMultipleMatches)
      expect(engine.getResonanceStatus(testKeywords[2].id)?.resonanceCount).toBe(1)
    })
  })
})

// ==================== 边界测试 ====================

describe('KeywordResonanceEngine - 边界条件', () => {
  let engine: KeywordResonanceEngine

  beforeEach(() => {
    engine = new KeywordResonanceEngine()
  })

  it('should handle empty keywords array', () => {
    engine.initializeKeywords([])

    const statuses = engine.getAllResonanceStatuses()
    expect(statuses.length).toBe(0)
    expect(engine.hasManifestation()).toBe(false)
  })

  it('should handle keyword with empty spirit signature', () => {
    const emptyKeyword: KeywordCard = {
      id: 1,
      name: '无标签词',
      category: 1,
      spiritSignature: [],
      rarity: 1,
    }

    engine.initializeKeywords([emptyKeyword])

    const choice: Choice = {
      id: 1,
      text: '任何选择',
      resonanceTags: ['任意标签'],
      valueOrientation: 'self',
    }

    const matched = engine.checkResonance(emptyKeyword, choice)
    expect(matched).toBe(false)
    expect(engine.getResonanceStatus(1)?.resonanceCount).toBe(0)
  })

  it('should handle rapid consecutive resonances', () => {
    const keyword: KeywordCard = {
      id: 1,
      name: '测试',
      category: 1,
      spiritSignature: ['标签1'],
      rarity: 1, // 凡, max=3
    }

    engine.initializeKeywords([keyword])

    for (let i = 0; i < 10; i++) {
      engine.addResonance(1)
    }

    const status = engine.getResonanceStatus(1)
    expect(status?.resonanceCount).toBeLess