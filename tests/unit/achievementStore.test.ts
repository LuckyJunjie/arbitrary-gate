/**
 * Achievement Store 单元测试
 *
 * 测试覆盖：
 * - unlockedAchievements / lockedAchievements computed
 * - hasUnlocked(id)
 * - unlockAchievement(id) + localStorage persistence
 * - checkCollectionAchievements
 * - checkDrawAchievements
 * - checkStoryAchievements
 * - checkCombinationAchievements
 * - loadFromStorage / syncToStorage
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAchievementStore, ALL_UNLOCKABLES } from '@/stores/achievementStore'

// ─── Helpers ─────────────────────────────────────────────────────────────────

function makeKeywordCard(id: number, name: string, rarity: number, category = 1) {
  return { id, name, rarity, category }
}

function makeEventCard(id: number, name: string, rarity: number, category = 2) {
  return { id: id + 1000, name, rarity, category }
}

const STORAGE_KEY = 'arbitrary_gate_achievements'

function getStoredIds(): string[] {
  const raw = localStorage.getItem(STORAGE_KEY)
  return raw ? JSON.parse(raw) : []
}

// ─── Setup ───────────────────────────────────────────────────────────────────

beforeEach(() => {
  vi.clearAllMocks()
  setActivePinia(createPinia())
  // Clear localStorage
  localStorage.removeItem(STORAGE_KEY)
  ALL_UNLOCKABLES.forEach(a => localStorage.removeItem(`${STORAGE_KEY}_time_${a.id}`))
})

// ─── Tests: Initial State ────────────────────────────────────────────────────

describe('useAchievementStore — 初始状态', () => {
  it('unlockedAchievements 初始为空数组', () => {
    const store = useAchievementStore()
    expect(store.unlockedAchievements).toEqual([])
  })

  it('lockedAchievements 初始包含所有 achievements', () => {
    const store = useAchievementStore()
    expect(store.lockedAchievements).toHaveLength(ALL_UNLOCKABLES.length)
  })

  it('hasUnlocked 返回 false', () => {
    const store = useAchievementStore()
    expect(store.hasUnlocked('first_card')).toBe(false)
    expect(store.hasUnlocked('story_1')).toBe(false)
  })
})

// ─── Tests: unlockAchievement ────────────────────────────────────────────────

describe('useAchievementStore — unlockAchievement', () => {
  it('unlockAchievement 将 achievement 移入 unlockedAchievements', () => {
    const store = useAchievementStore()
    store.unlockAchievement('first_card')

    expect(store.unlockedAchievements).toHaveLength(1)
    expect(store.unlockedAchievements[0].id).toBe('first_card')
    expect(store.hasUnlocked('first_card')).toBe(true)
  })

  it('unlockAchievement 设置 unlockedAt 时间戳', () => {
    const store = useAchievementStore()
    store.unlockAchievement('first_card')

    const achievement = store.unlockedAchievements.find(a => a.id === 'first_card')
    expect(achievement?.unlockedAt).toBeDefined()
    expect(new Date(achievement!.unlockedAt!)).toBeInstanceOf(Date)
  })

  it('unlockAchievement 将 id 持久化到 localStorage', () => {
    const store = useAchievementStore()
    store.unlockAchievement('first_card')

    const stored = getStoredIds()
    expect(stored).toContain('first_card')
  })

  it('重复 unlockAchievement 同一个 id 不重复添加', () => {
    const store = useAchievementStore()
    store.unlockAchievement('first_card')
    store.unlockAchievement('first_card')

    expect(store.unlockedAchievements).toHaveLength(1)
  })

  it('unlockAchievement 多个 achievements 正确追踪', () => {
    const store = useAchievementStore()
    store.unlockAchievement('first_card')
    store.unlockAchievement('story_1')
    store.unlockAchievement('draw_50')

    expect(store.unlockedAchievements).toHaveLength(3)
    expect(store.hasUnlocked('first_card')).toBe(true)
    expect(store.hasUnlocked('story_1')).toBe(true)
    expect(store.hasUnlocked('draw_50')).toBe(true)
    expect(store.hasUnlocked('collect_10')).toBe(false)
  })

  it('unlockAchievement 更新 lockedAchievements', () => {
    const store = useAchievementStore()
    store.unlockAchievement('first_card')

    expect(store.lockedAchievements.find(a => a.id === 'first_card')).toBeUndefined()
    expect(store.lockedAchievements).toHaveLength(ALL_UNLOCKABLES.length - 1)
  })
})

// ─── Tests: checkCollectionAchievements ──────────────────────────────────────

describe('useAchievementStore — checkCollectionAchievements', () => {
  it('收集1张卡解锁 first_card', () => {
    const store = useAchievementStore()
    const cards = [makeKeywordCard(1, '测试卡', 1)]

    store.checkCollectionAchievements(cards, [])

    expect(store.hasUnlocked('first_card')).toBe(true)
  })

  it('收集10张卡解锁 collect_10', () => {
    const store = useAchievementStore()
    const cards = Array.from({ length: 10 }, (_, i) => makeKeywordCard(i, `卡${i}`, 1))

    store.checkCollectionAchievements(cards, [])

    expect(store.hasUnlocked('collect_10')).toBe(true)
  })

  it('收集30张卡解锁 collect_30', () => {
    const store = useAchievementStore()
    const cards = Array.from({ length: 30 }, (_, i) => makeKeywordCard(i, `卡${i}`, 1))

    store.checkCollectionAchievements(cards, [])

    expect(store.hasUnlocked('collect_30')).toBe(true)
  })

  it('混合 keywordCards 和 eventCards 合计', () => {
    const store = useAchievementStore()
    const kw = [makeKeywordCard(1, 'KW1', 1)]
    const ev = [makeEventCard(2, 'EV1', 1)]
    // total = 2 < 10, so collect_10 should NOT unlock
    store.checkCollectionAchievements(kw, ev)

    expect(store.hasUnlocked('first_card')).toBe(true)
    expect(store.hasUnlocked('collect_10')).toBe(false)
  })
})

// ─── Tests: checkDrawAchievements ────────────────────────────────────────────

describe('useAchievementStore — checkDrawAchievements', () => {
  it('抽卡50次解锁 draw_50', () => {
    const store = useAchievementStore()
    const cards: ReturnType<typeof makeKeywordCard>[] = []

    store.checkDrawAchievements(50, cards)

    expect(store.hasUnlocked('draw_50')).toBe(true)
  })

  it('获得 rarity>=3 的卡解锁 first_rare', () => {
    const store = useAchievementStore()
    const cards = [makeKeywordCard(1, '珍品卡', 3)]

    store.checkDrawAchievements(1, cards)

    expect(store.hasUnlocked('first_rare')).toBe(true)
  })

  it('rarity=4 绝卡解锁 first_legendary', () => {
    const store = useAchievementStore()
    const cards = [makeKeywordCard(1, '绝品卡', 4)]

    store.checkDrawAchievements(1, cards)

    expect(store.hasUnlocked('first_legendary')).toBe(true)
    expect(store.hasUnlocked('first_rare')).toBe(true) // also should unlock rare
  })

  it('rarity=2 佳卡不解锁 first_rare', () => {
    const store = useAchievementStore()
    const cards = [makeKeywordCard(1, '佳品卡', 2)]

    store.checkDrawAchievements(1, cards)

    expect(store.hasUnlocked('first_rare')).toBe(false)
    expect(store.hasUnlocked('first_legendary')).toBe(false)
  })
})

// ─── Tests: checkStoryAchievements ───────────────────────────────────────────

describe('useAchievementStore — checkStoryAchievements', () => {
  it('完成1个故事解锁 story_1', () => {
    const store = useAchievementStore()
    store.checkStoryAchievements(1)
    expect(store.hasUnlocked('story_1')).toBe(true)
  })

  it('完成5个故事解锁 story_5', () => {
    const store = useAchievementStore()
    store.checkStoryAchievements(5)
    expect(store.hasUnlocked('story_5')).toBe(true)
  })

  it('完成3个故事不解锁 story_5', () => {
    const store = useAchievementStore()
    store.checkStoryAchievements(3)
    expect(store.hasUnlocked('story_1')).toBe(true)
    expect(store.hasUnlocked('story_5')).toBe(false)
  })
})

// ─── Tests: checkCombinationAchievements ─────────────────────────────────────

describe('useAchievementStore — checkCombinationAchievements', () => {
  it('同时拥有"说书人"和"铜锁芯"解锁 combo_speaker_lock', () => {
    const store = useAchievementStore()
    const kw = [
      makeKeywordCard(1, '说书人', 2),
      makeKeywordCard(2, '铜锁芯', 3),
    ]

    store.checkCombinationAchievements(kw, [])

    expect(store.hasUnlocked('combo_speaker_lock')).toBe(true)
  })

  it('只有"说书人"不解锁 combo_speaker_lock', () => {
    const store = useAchievementStore()
    const kw = [makeKeywordCard(1, '说书人', 2)]

    store.checkCombinationAchievements(kw, [])

    expect(store.hasUnlocked('combo_speaker_lock')).toBe(false)
  })

  it('拥有所有稀有度(1-5)解锁 combo_all_rarity', () => {
    const store = useAchievementStore()
    const kw = [
      makeKeywordCard(1, '凡卡', 1),
      makeKeywordCard(2, '佳卡', 2),
      makeKeywordCard(3, '珍卡', 3),
      makeKeywordCard(4, '奇卡', 4),
      makeKeywordCard(5, '绝卡', 5),
    ]

    store.checkCombinationAchievements(kw, [])

    expect(store.hasUnlocked('combo_all_rarity')).toBe(true)
  })

  it('缺少任一稀有度不解锁 combo_all_rarity', () => {
    const store = useAchievementStore()
    const kw = [
      makeKeywordCard(1, '凡卡', 1),
      makeKeywordCard(2, '佳卡', 2),
      makeKeywordCard(3, '珍卡', 3),
      // missing 奇(4) and 绝(5)
    ]

    store.checkCombinationAchievements(kw, [])

    expect(store.hasUnlocked('combo_all_rarity')).toBe(false)
  })

  // P-02: 稀有组合成就

  it('拥有3张器物类卡牌（category=1）解锁 combo_three_objects（物是人非）', () => {
    const store = useAchievementStore()
    const kw = [
      makeKeywordCard(1, '旧船票', 3, 1),
      makeKeywordCard(2, '半块玉佩', 3, 1),
      makeKeywordCard(3, '铜锁芯', 2, 1),
    ]

    store.checkCombinationAchievements(kw, [])

    expect(store.hasUnlocked('combo_three_objects')).toBe(true)
  })

  it('拥有2张器物类卡牌不解锁 combo_three_objects', () => {
    const store = useAchievementStore()
    const kw = [
      makeKeywordCard(1, '旧船票', 3, 1),
      makeKeywordCard(2, '半块玉佩', 3, 1),
    ]

    store.checkCombinationAchievements(kw, [])

    expect(store.hasUnlocked('combo_three_objects')).toBe(false)
  })

  it('拥有3张情绪类卡牌（category=4）解锁 combo_three_emotions（百感交集）', () => {
    const store = useAchievementStore()
    const kw = [
      makeKeywordCard(1, '意难平', 3, 4),
      makeKeywordCard(2, '亡国恨', 4, 4),
      makeKeywordCard(3, '乡愁', 3, 4),
    ]

    store.checkCombinationAchievements(kw, [])

    expect(store.hasUnlocked('combo_three_emotions')).toBe(true)
  })

  it('拥有2张情绪类卡牌不解锁 combo_three_emotions', () => {
    const store = useAchievementStore()
    const kw = [
      makeKeywordCard(1, '意难平', 3, 4),
      makeKeywordCard(2, '亡国恨', 4, 4),
    ]

    store.checkCombinationAchievements(kw, [])

    expect(store.hasUnlocked('combo_three_emotions')).toBe(false)
  })

  it('拥有旧船票+摆渡人解锁 combo_departing_person（离人）', () => {
    const store = useAchievementStore()
    const kw = [
      makeKeywordCard(1, '旧船票', 3, 1),
      makeKeywordCard(2, '摆渡人', 2, 2),
    ]

    store.checkCombinationAchievements(kw, [])

    expect(store.hasUnlocked('combo_departing_person')).toBe(true)
  })

  it('拥有旧船票+乌江渡解锁 combo_departing_person（离人）', () => {
    const store = useAchievementStore()
    const kw = [
      makeKeywordCard(1, '旧船票', 3, 1),
      makeKeywordCard(2, '乌江渡', 3, 3),
    ]

    store.checkCombinationAchievements(kw, [])

    expect(store.hasUnlocked('combo_departing_person')).toBe(true)
  })

  it('只有旧船票不解锁 combo_departing_person', () => {
    const store = useAchievementStore()
    const kw = [makeKeywordCard(1, '旧船票', 3, 1)]

    store.checkCombinationAchievements(kw, [])

    expect(store.hasUnlocked('combo_departing_person')).toBe(false)
  })

  it('拥有摆渡人但无旧船票不解锁 combo_departing_person', () => {
    const store = useAchievementStore()
    const kw = [makeKeywordCard(1, '摆渡人', 2, 2)]

    store.checkCombinationAchievements(kw, [])

    expect(store.hasUnlocked('combo_departing_person')).toBe(false)
  })
})

// ─── Tests: Storage Persistence ──────────────────────────────────────────────

describe('useAchievementStore — 存储持久化', () => {
  it('loadFromStorage 从 localStorage 恢复 unlockedIds', () => {
    // Pre-populate localStorage as if we had unlocked some achievements
    localStorage.setItem(STORAGE_KEY, JSON.stringify(['first_card', 'story_1']))
    localStorage.setItem(`${STORAGE_KEY}_time_first_card`, '2026-01-01T00:00:00.000Z')

    const store = useAchievementStore()
    store.loadFromStorage()

    expect(store.hasUnlocked('first_card')).toBe(true)
    expect(store.hasUnlocked('story_1')).toBe(true)
    expect(store.hasUnlocked('collect_10')).toBe(false)
  })

  it('unlockAchievement 同步到 localStorage', () => {
    const store = useAchievementStore()
    store.unlockAchievement('first_card')

    const stored = getStoredIds()
    expect(stored).toEqual(['first_card'])
  })

  it('多个 achievements 正确持久化', () => {
    const store = useAchievementStore()
    store.unlockAchievement('first_card')
    store.unlockAchievement('story_1')
    store.unlockAchievement('draw_50')

    const stored = getStoredIds()
    expect(stored).toContain('first_card')
    expect(stored).toContain('story_1')
    expect(stored).toContain('draw_50')
    expect(stored).toHaveLength(3)
  })

  it('loadFromStorage 处理损坏数据不抛异常', () => {
    localStorage.setItem(STORAGE_KEY, 'not valid json')

    const store = useAchievementStore()
    expect(() => store.loadFromStorage()).not.toThrow()
    expect(store.unlockedAchievements).toEqual([])
  })
})

// ─── Tests: ALL_UNLOCKABLES pre-defined list ─────────────────────────────────

describe('useAchievementStore — ALL_UNLOCKABLES 预定义列表', () => {
  it('包含所有要求的 achievements', () => {
    const ids = ALL_UNLOCKABLES.map(a => a.id)
    expect(ids).toContain('first_card')
    expect(ids).toContain('collect_10')
    expect(ids).toContain('collect_30')
    expect(ids).toContain('first_rare')
    expect(ids).toContain('first_legendary')
    expect(ids).toContain('draw_50')
    expect(ids).toContain('story_1')
    expect(ids).toContain('story_5')
    expect(ids).toContain('combo_speaker_lock')
    expect(ids).toContain('combo_all_rarity')
    // P-02 稀有组合成就
    expect(ids).toContain('combo_three_objects')
    expect(ids).toContain('combo_three_emotions')
    expect(ids).toContain('combo_departing_person')
  })

  it('所有 achievements 有 icon', () => {
    ALL_UNLOCKABLES.forEach(a => {
      expect(a.icon).toBeDefined()
      expect(typeof a.icon).toBe('string')
    })
  })

  it('所有 achievements 有 name 和 description', () => {
    ALL_UNLOCKABLES.forEach(a => {
      expect(a.name).toBeDefined()
      expect(typeof a.name).toBe('string')
      expect(a.description).toBeDefined()
      expect(typeof a.description).toBe('string')
    })
  })
})
