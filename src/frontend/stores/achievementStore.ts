import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

// ─── Types ────────────────────────────────────────────────────────────────────

export type AchievementCategory = 'collection' | 'draw' | 'story' | 'combination'

export interface Achievement {
  id: string
  name: string
  description: string
  category: AchievementCategory
  icon: string          // emoji
  unlockedAt?: string
  threshold?: number    // for count-based achievements
  requiredCards?: string[] // card names for combination achievements
}

// ─── Pre-defined Achievements ─────────────────────────────────────────────────

export const ALL_UNLOCKABLES: Achievement[] = [
  // Collection achievements
  {
    id: 'first_card',
    name: '初集',
    description: '收集第一张卡牌',
    category: 'collection',
    icon: '✨',
    threshold: 1,
  },
  {
    id: 'collect_10',
    name: '小有收藏',
    description: '收集10张以上卡牌',
    category: 'collection',
    icon: '📚',
    threshold: 10,
  },
  {
    id: 'collect_30',
    name: '收藏家',
    description: '收集30张以上卡牌',
    category: 'collection',
    icon: '🏆',
    threshold: 30,
  },
  // Draw achievements
  {
    id: 'first_rare',
    name: '珍品猎手',
    description: '获得第一张珍/奇/绝稀有度卡牌',
    category: 'draw',
    icon: '💎',
    threshold: 1,
  },
  {
    id: 'first_legendary',
    name: '欧皇',
    description: '获得第一张绝稀有度卡牌',
    category: 'draw',
    icon: '👑',
    threshold: 1,
  },
  {
    id: 'draw_50',
    name: '抽卡达人',
    description: '累计抽卡50次',
    category: 'draw',
    icon: '🎰',
    threshold: 50,
  },
  // Story achievements
  {
    id: 'story_1',
    name: '说书人',
    description: '完成第一个故事',
    category: 'story',
    icon: '📖',
    threshold: 1,
  },
  {
    id: 'story_5',
    name: '故事收藏家',
    description: '完成5个故事',
    category: 'story',
    icon: '📚',
    threshold: 5,
  },
  // Combination achievements
  {
    id: 'combo_speaker_lock',
    name: '铜墨兼备',
    description: '同时拥有"说书人"与"铜锁芯"卡牌',
    category: 'combination',
    icon: '🔐',
    requiredCards: ['说书人', '铜锁芯'],
  },
  {
    id: 'combo_all_rarity',
    name: '全部珍奇',
    description: '集齐所有稀有度的卡牌',
    category: 'combination',
    icon: '🌈',
    requiredCards: ['全稀有'],
  },
  // P-02 稀有组合成就
  {
    id: 'combo_three_objects',
    name: '物是人非',
    description: '收集三张器物类卡牌（category=1），旧物仍在，人事已非',
    category: 'combination',
    icon: '🏺',
    requiredCards: ['三器物'],
  },
  {
    id: 'combo_three_emotions',
    name: '百感交集',
    description: '收集三张情绪类卡牌（category=4），悲欢离合，尽集于此',
    category: 'combination',
    icon: '💠',
    requiredCards: ['三情绪'],
  },
  {
    id: 'combo_departing_person',
    name: '离人',
    description: '同时拥有"旧船票"与渡口相关卡牌（摆渡人/乌江渡），持票者终须别',
    category: 'combination',
    icon: '⛵',
    requiredCards: ['旧船票', '渡口'],
  },
]

const STORAGE_KEY = 'arbitrary_gate_achievements'

// ─── Store ───────────────────────────────────────────────────────────────────

export const useAchievementStore = defineStore('achievement', () => {
  // State
  const unlockedIds = ref<Set<string>>(new Set())

  // Getters
  const unlockedAchievements = computed<Achievement[]>(() =>
    ALL_UNLOCKABLES.filter(a => unlockedIds.value.has(a.id))
      .map(a => ({ ...a, unlockedAt: getUnlockTime(a.id) }))
  )

  const lockedAchievements = computed<Achievement[]>(() =>
    ALL_UNLOCKABLES.filter(a => !unlockedIds.value.has(a.id))
  )

  // Helper
  function getUnlockTime(id: string): string | undefined {
    const stored = localStorage.getItem(`${STORAGE_KEY}_time_${id}`)
    return stored ?? undefined
  }

  // Check if an achievement is unlocked
  function hasUnlocked(id: string): boolean {
    return unlockedIds.value.has(id)
  }

  // Unlock an achievement
  function unlockAchievement(id: string): void {
    if (unlockedIds.value.has(id)) return
    unlockedIds.value.add(id)
    localStorage.setItem(`${STORAGE_KEY}_time_${id}`, new Date().toISOString())
    syncToStorage()
  }

  // Check collection achievements
  function checkCollectionAchievements(
    keywordCards: Array<{ id: number; name: string; rarity: number }>,
    eventCards: Array<{ id: number; name: string; rarity: number }>
  ): void {
    const total = keywordCards.length + eventCards.length

    // First card
    if (total >= 1) unlockAchievement('first_card')
    // Collect 10
    if (total >= 10) unlockAchievement('collect_10')
    // Collect 30
    if (total >= 30) unlockAchievement('collect_30')
  }

  // Check draw achievements (call after getting a new card)
  function checkDrawAchievements(
    totalDrawCount: number,
    keywordCards: Array<{ id: number; name: string; rarity: number }>
  ): void {
    // Draw 50 times
    if (totalDrawCount >= 50) unlockAchievement('draw_50')

    // First rare+ card (rarity >= 3 means 珍/奇/绝)
    const hasRare = keywordCards.some(c => c.rarity >= 3)
    if (hasRare) unlockAchievement('first_rare')

    // First legendary card (rarity = 4 means 绝)
    const hasLegendary = keywordCards.some(c => c.rarity === 4)
    if (hasLegendary) unlockAchievement('first_legendary')
  }

  // Check story achievements
  function checkStoryAchievements(completedStoryCount: number): void {
    if (completedStoryCount >= 1) unlockAchievement('story_1')
    if (completedStoryCount >= 5) unlockAchievement('story_5')
  }

  // Check combination achievements
  function checkCombinationAchievements(
    keywordCards: Array<{ id: number; name: string; rarity: number; category: number }>,
    eventCards: Array<{ id: number; name: string; rarity: number; category: number }>
  ): void {
    const allCards = [...keywordCards, ...eventCards]
    const cardNames = new Set(allCards.map(c => c.name))

    // 铜墨兼备: have both 说书人 and 铜锁芯
    if (cardNames.has('说书人') && cardNames.has('铜锁芯')) {
      unlockAchievement('combo_speaker_lock')
    }

    // 全部珍奇: have at least one of each rarity (1=凡, 2=佳, 3=珍, 4=奇, 5=绝)
    const ownedRarities = new Set(allCards.map(c => c.rarity))
    const requiredRarities = new Set([1, 2, 3, 4, 5])
    const hasAllRarities = [...requiredRarities].every(r => ownedRarities.has(r))
    if (hasAllRarities) {
      unlockAchievement('combo_all_rarity')
    }

    // P-02 稀有组合成就
    // 物是人非: 三器物（category 全为 1）— 旧物仍在，人事已非
    const objectCards = allCards.filter(c => c.category === 1)
    if (objectCards.length >= 3) {
      unlockAchievement('combo_three_objects')
    }

    // 百感交集: 三情绪（category 全为 4）— 悲欢离合，尽集于此
    const emotionCards = allCards.filter(c => c.category === 4)
    if (emotionCards.length >= 3) {
      unlockAchievement('combo_three_emotions')
    }

    // 离人: 旧船票 + 渡口类事件（摆渡人/乌江渡）
    if (cardNames.has('旧船票') && (cardNames.has('摆渡人') || cardNames.has('乌江渡'))) {
      unlockAchievement('combo_departing_person')
    }
  }

  // Storage
  function loadFromStorage(): void {
    try {
      const raw = localStorage.getItem(STORAGE_KEY)
      if (raw) {
        const ids: string[] = JSON.parse(raw)
        unlockedIds.value = new Set(ids)
      }
    } catch {
      // ignore corrupt data
    }
  }

  function syncToStorage(): void {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify([...unlockedIds.value]))
    } catch {
      // ignore storage errors
    }
  }

  return {
    // State
    unlockedIds,
    // Getters
    unlockedAchievements,
    lockedAchievements,
    // Methods
    hasUnlocked,
    unlockAchievement,
    checkCollectionAchievements,
    checkDrawAchievements,
    checkStoryAchievements,
    checkCombinationAchievements,
    loadFromStorage,
    syncToStorage,
  }
})
