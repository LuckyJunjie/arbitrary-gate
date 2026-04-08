import { describe, it, expect, beforeEach } from 'vitest'

// ─── KeywordCard & Resonance Types ──────────────────────────────────────────────

type Rarity = 1 | 2 | 3 | 4 // 凡珍奇绝

interface KeywordCard {
  id: number
  name: string
  rarity: Rarity
  category: number
  spiritSignature: string[]
}

interface ResonanceStatus {
  resonanceCount: number
  resonanceLevel: number
  lastTriggeredAt?: number
}

const RESONANCE_THRESHOLD = 3 // 3次共鸣触发显灵

// ─── KeywordResonanceEngine (Implementation under test) ─────────────────────────

class KeywordResonanceEngine {
  private keyword: KeywordCard
  private resonanceCount = 0
  private resonanceHistory: number[] = []
  private level = 1 // 1-3

  constructor(keyword: KeywordCard) {
    this.keyword = keyword
  }

  /** 添加一次共鸣 */
  addResonance(): void {
    this.resonanceCount++
    this.resonanceHistory.push(Date.now())
    this.level = Math.min(4, Math.floor(this.resonanceCount / RESONANCE_THRESHOLD) + 1)
  }

  /** 获取共鸣状态 */
  getResonanceStatus(_keywordId: number): ResonanceStatus | null {
    if (this.resonanceCount === 0) return null
    return {
      resonanceCount: this.resonanceCount,
      resonanceLevel: this.level,
      lastTriggeredAt: this.resonanceHistory[this.resonanceHistory.length - 1],
    }
  }

  /** 是否已触发显灵 */
  isManifested(): boolean {
    return this.resonanceCount >= RESONANCE_THRESHOLD * 3
  }

  /** 获取共鸣历史 */
  getHistory(): number[] {
    return [...this.resonanceHistory]
  }

  /** 重置 */
  reset(): void {
    this.resonanceCount = 0
    this.resonanceHistory = []
    this.level = 1
  }

  /** 计算当前共鸣能量（百分比） */
  getResonanceEnergy(): number {
    const threshold = this.level * RESONANCE_THRESHOLD
    return Math.min(1, (this.resonanceCount % RESONANCE_THRESHOLD) / RESONANCE_THRESHOLD)
  }
}

// ─── KeywordResonanceEngine Class ──────────────────────────────────────────────

class ResonanceEngine {
  private cards: Map<number, KeywordResonanceEngine> = new Map()

  initializeKeywords(keywords: KeywordCard[]): void {
    keywords.forEach((kw) => {
      this.cards.set(kw.id, new KeywordResonanceEngine(kw))
    })
  }

  addResonance(keywordId: number): void {
    const engine = this.cards.get(keywordId)
    if (!engine) throw new Error(`Keyword ${keywordId} not found`)
    engine.addResonance()
  }

  getResonanceStatus(keywordId: number): ResonanceStatus | null {
    const engine = this.cards.get(keywordId)
    return engine ? engine.getResonanceStatus(keywordId) : null
  }

  isManifested(keywordId: number): boolean {
    const engine = this.cards.get(keywordId)
    return engine ? engine.isManifested() : false
  }

  getAllManifestedKeywordIds(): number[] {
    const manifested: number[] = []
    this.cards.forEach((engine, id) => {
      if (engine.isManifested()) manifested.push(id)
    })
    return manifested
  }

  getEnergy(keywordId: number): number {
    const engine = this.cards.get(keywordId)
    return engine ? engine.getResonanceEnergy() : 0
  }
}

// ─── Tests ──────────────────────────────────────────────────────────────────────

describe('KeywordResonanceEngine', () => {
  let engine: KeywordResonanceEngine
  const keyword: KeywordCard = {
    id: 1,
    name: '测试',
    rarity: 1,
    category: 1,
    spiritSignature: ['标签1'],
  }

  beforeEach(() => {
    engine = new KeywordResonanceEngine(keyword)
  })

  it('should initialize with zero resonance', () => {
    expect(engine.getResonanceStatus(1)).toBeNull()
    expect(engine.isManifested()).toBe(false)
  })

  it('should increment resonance count on addResonance', () => {
    engine.addResonance()
    const status = engine.getResonanceStatus(1)
    expect(status?.resonanceCount).toBe(1)
    expect(status?.resonanceLevel).toBe(1)
  })

  it('should accumulate multiple resonances', () => {
    for (let i = 0; i < 5; i++) engine.addResonance()
    const status = engine.getResonanceStatus(1)
    expect(status?.resonanceCount).toBe(5)
    expect(status?.resonanceLevel).toBe(2) // floor(5/3)+1 = 2
  })

  it('should track resonance history', () => {
    engine.addResonance()
    engine.addResonance()
    const history = engine.getHistory()
    expect(history.length).toBe(2)
  })

  it('should level up at resonance thresholds', () => {
    // level 1: 0-2, level 2: 3-5, level 3: 6-8
    for (let i = 0; i < 3; i++) engine.addResonance()
    expect(engine.getResonanceStatus(1)?.resonanceLevel).toBe(2)

    for (let i = 0; i < 3; i++) engine.addResonance()
    expect(engine.getResonanceStatus(1)?.resonanceLevel).toBe(3)
  })

  it('should detect manifestation', () => {
    for (let i = 0; i < 9; i++) engine.addResonance()
    expect(engine.isManifested()).toBe(true)
  })

  it('should reset correctly', () => {
    engine.addResonance()
    engine.reset()
    expect(engine.getResonanceStatus(1)).toBeNull()
    expect(engine.isManifested()).toBe(false)
  })

  it('should calculate resonance energy', () => {
    engine.addResonance()
    engine.addResonance()
    const energy = engine.getResonanceEnergy()
    expect(energy).toBeGreaterThan(0)
    expect(energy).toBeLessThanOrEqual(1)
  })

  it('should handle rapid consecutive resonances', () => {
    for (let i = 0; i < 10; i++) engine.addResonance()
    const status = engine.getResonanceStatus(1)
    expect(status?.resonanceCount).toBe(10)
    expect(status?.resonanceLevel).toBe(4)
  })
})

describe('ResonanceEngine (Multi-keyword)', () => {
  let engine: ResonanceEngine

  const keywords: KeywordCard[] = [
    { id: 1, name: '旧船票', rarity: 3, category: 1, spiritSignature: ['水', '离别'] },
    { id: 2, name: '意难平', rarity: 2, category: 4, spiritSignature: ['情绪', '遗憾'] },
    { id: 3, name: '摆渡人', rarity: 1, category: 2, spiritSignature: ['职人'] },
  ]

  beforeEach(() => {
    engine = new ResonanceEngine()
    engine.initializeKeywords(keywords)
  })

  it('should initialize multiple keywords', () => {
    expect(engine.getResonanceStatus(1)).toBeNull()
    expect(engine.getResonanceStatus(2)).toBeNull()
    expect(engine.getResonanceStatus(3)).toBeNull()
  })

  it('should track resonance per keyword independently', () => {
    engine.addResonance(1)
    engine.addResonance(1)
    engine.addResonance(2)
    expect(engine.getResonanceStatus(1)?.resonanceCount).toBe(2)
    expect(engine.getResonanceStatus(2)?.resonanceCount).toBe(1)
    expect(engine.getResonanceStatus(3)?.resonanceCount).toBeUndefined() // keyword 3 never resonated, count=0 → returns null → undefined
  })

  it('should report all manifested keywords', () => {
    for (let i = 0; i < 9; i++) engine.addResonance(1) // 触发显灵
    for (let i = 0; i < 2; i++) engine.addResonance(2)
    const manifested = engine.getAllManifestedKeywordIds()
    expect(manifested).toContain(1)
    expect(manifested).not.toContain(2)
    expect(manifested).not.toContain(3)
  })

  it('should calculate energy per keyword', () => {
    engine.addResonance(1)
    engine.addResonance(1)
    expect(engine.getEnergy(1)).toBe(2 / 3)
    expect(engine.getEnergy(2)).toBe(0)
  })

  it('should throw for unknown keyword', () => {
    expect(() => engine.addResonance(999)).toThrow('Keyword 999 not found')
  })

  it('should handle zero energy for non-existent keyword', () => {
    expect(engine.getEnergy(999)).toBe(0)
  })
})
