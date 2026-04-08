import { describe, it, expect } from 'vitest'

// ─── Config ────────────────────────────────────────────────────────────────────

type CardRarity = 1 | 2 | 3 | 4

const RANKS = [
  { level: 1 as CardRarity, name: '凡', weight: 60 },
  { level: 2 as CardRarity, name: '珍', weight: 25 },
  { level: 3 as CardRarity, name: '奇', weight: 12 },
  { level: 4 as CardRarity, name: '绝', weight: 3  },
]
const PITY_QI  = 10
const PITY_JUE = 31
const WEIGHTS  = RANKS.map(r => r.weight) // [60, 85, 97, 100]

// ─── Correct DrawEngine ────────────────────────────────────────────────────────

class DrawEngine {
  private qiPity  = 0
  private juePity = 0

  draw(): { rarity: CardRarity; qiPity: number; juePity: number } {
    this.qiPity++
    this.juePity++

    let rarity: CardRarity

    if (this.qiPity >= PITY_QI) {
      rarity = 3
      this.qiPity = 0
    } else if (this.juePity >= PITY_JUE) {
      rarity = 4
      this.juePity = 0
    } else {
      const roll = Math.random() * 100
      let cum = 0
      for (let i = 0; i < WEIGHTS.length; i++) {
        cum += WEIGHTS[i]
        if (roll < cum) { rarity = RANKS[i].level; break }
      }
      if (!rarity) rarity = 1
    }

    // After a guarantee: triggered counter is 0; other counter keeps its incremented value
    return { rarity, qiPity: this.qiPity, juePity: this.juePity }
  }

  drawMany(n: number) { return Array.from({ length: n }, () => this.draw()) }
}

// ─── Tests ──────────────────────────────────────────────────────────────────────

describe('基础概率', () => {
  it('rarity always in [1..4]', () => {
    const eng = new DrawEngine()
    for (let i = 0; i < 100; i++) {
      const { rarity } = eng.draw()
      expect(rarity).toBeGreaterThanOrEqual(1)
      expect(rarity).toBeLessThanOrEqual(4)
    }
  })

  it('distribution approximates weights', () => {
    const eng = new DrawEngine()
    const cnt = { 1: 0, 2: 0, 3: 0, 4: 0 }
    for (let i = 0; i < 10000; i++) cnt[eng.draw().rarity]++
    expect(cnt[1]).toBeGreaterThan(5000)
    expect(cnt[2]).toBeGreaterThan(2000)
    expect(cnt[3]).toBeGreaterThan(600)
    expect(cnt[4]).toBeGreaterThan(150)
  })
})

describe('奇品保底 (10连非奇必出奇)', () => {
  it('第10次draw触发奇品保底', () => {
    const eng = new DrawEngine()
    for (let i = 0; i < 9; i++) eng.draw()
    const { rarity, qiPity } = eng.draw()
    expect(rarity).toBe(3)
    expect(qiPity).toBe(0)  // Reset to 0 after trigger
  })

  it('qiPity重置后从1重新累积', () => {
    const eng = new DrawEngine()
    for (let i = 0; i < 10; i++) eng.draw()
    const { qiPity } = eng.draw() // 11th draw
    expect(qiPity).toBe(1)
  })
})

describe('绝品保底 (31连非绝必出绝)', () => {
  it('第31次draw触发绝品保底', () => {
    const eng = new DrawEngine()
    for (let i = 0; i < 30; i++) eng.draw()
    const { rarity, juePity } = eng.draw()
    expect(rarity).toBe(4)
    expect(juePity).toBe(0)
  })
})

describe('保底独立累积', () => {
  it('Qi保底和Jue保底独立计数', () => {
    const eng = new DrawEngine()
    for (let i = 0; i < 30; i++) eng.draw()
    // 31st: qiPity=31, juePity=31 → Jue triggers (qiPity already reset by Qi at ~draw 21)
    //       So juePity gets checked and triggers Jue(4)
    const { rarity, juePity } = eng.draw()
    expect(rarity).toBe(4)   // Jue triggers at 31 (qiPity got reset by Qi earlier)
    expect(juePity).toBe(0)  // Jue triggered, reset to 0
  })
})

describe('drawMany', () => {
  it('drawMany(n) returns n results', () => {
    const eng = new DrawEngine()
    expect(eng.drawMany(5).length).toBe(5)
  })
})
