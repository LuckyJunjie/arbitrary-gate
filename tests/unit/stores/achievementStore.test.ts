import { describe, it, expect, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAchievementStore, CombinationType } from '@/stores/achievementStore'

// 辅助：构造关键词卡
function kw(id: number, name: string, rarity: number, category: number) {
  return { id, name, rarity: rarity as 1 | 2 | 3 | 4, category: category as 1 | 2 | 3 | 4 | 5 }
}

describe('achievementStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  describe('checkKeywordCombination', () => {
    it('应返回 null 当关键词不足 3 张', () => {
      const store = useAchievementStore()
      const result = store.checkKeywordCombination([
        kw(1, '旧船票', 3, 1),
        kw(2, '半块玉佩', 3, 1),
      ])
      expect(result).toBeNull()
    })

    it('应触发 三器物【物是人非】当三张都是器物', () => {
      const store = useAchievementStore()
      const cards = [
        kw(1, '旧船票', 3, 1),
        kw(2, '半块玉佩', 3, 1),
        kw(3, '铜锁芯', 2, 1),
      ]
      const result = store.checkKeywordCombination(cards)
      expect(result).not.toBeNull()
      expect(result!.type).toBe(CombinationType.THREE_OBJECTS)
      expect(result!.name).toBe('物是人非')
      expect(result!.bonus).toContain('咏物诗')
      expect(store.hasUnlocked('combo_three_objects')).toBe(true)
    })

    it('应触发 三情绪【百感交集】当三张都是情绪', () => {
      const store = useAchievementStore()
      const cards = [
        kw(1, '意难平', 3, 4),
        kw(2, '亡国恨', 4, 4),
        kw(3, '离别苦', 3, 4),
      ]
      const result = store.checkKeywordCombination(cards)
      expect(result).not.toBeNull()
      expect(result!.type).toBe(CombinationType.THREE_EMOTIONS)
      expect(result!.name).toBe('百感交集')
      expect(result!.bonus).toContain('感伤')
      expect(store.hasUnlocked('combo_three_emotions')).toBe(true)
    })

    it('应触发 三称谓【三生石上】当三张都是称谓', () => {
      const store = useAchievementStore()
      const cards = [
        kw(1, '阶下囚', 3, 5),
        kw(2, '末代王朝', 4, 5),
        kw(3, '说书人', 2, 5),
      ]
      const result = store.checkKeywordCombination(cards)
      expect(result).not.toBeNull()
      expect(result!.type).toBe(CombinationType.THREE_TITLES)
      expect(result!.name).toBe('三生石上')
      expect(result!.bonus).toContain('配角')
      expect(store.hasUnlocked('combo_three_titles')).toBe(true)
    })

    it('应触发 三水意象【水过三秋】当三张都含水意象', () => {
      const store = useAchievementStore()
      const cards = [
        kw(1, '乌江渡', 3, 3),
        kw(2, '雨纷纷', 2, 3),
        kw(3, '泪如雨', 3, 4),
      ]
      const result = store.checkKeywordCombination(cards)
      expect(result).not.toBeNull()
      expect(result!.type).toBe(CombinationType.THREE_WATER)
      expect(result!.name).toBe('水过三秋')
      expect(result!.bonus).toContain('雨')
      expect(store.hasUnlocked('combo_three_water')).toBe(true)
    })

    it('应触发 绝品汇聚【凤毛麟角】当三张都是绝稀有度', () => {
      const store = useAchievementStore()
      const cards = [
        kw(1, '咸阳宫阙', 4, 3),
        kw(2, '亡国恨', 4, 4),
        kw(3, '末代王朝', 4, 5),
      ]
      const result = store.checkKeywordCombination(cards)
      expect(result).not.toBeNull()
      expect(result!.type).toBe(CombinationType.THREE_LEGENDARY)
      expect(result!.name).toBe('凤毛麟角')
      expect(result!.bonus).toContain('藏头诗')
      expect(store.hasUnlocked('combo_three_legendary')).toBe(true)
    })

    it('应返回 null 当无稀有组合', () => {
      const store = useAchievementStore()
      const cards = [
        kw(1, '旧船票', 3, 1),
        kw(2, '摆渡人', 2, 2),
        kw(3, '乌江渡', 3, 3),
      ]
      const result = store.checkKeywordCombination(cards)
      expect(result).toBeNull()
    })

    it('优先级：三器物 > 三情绪（前者先定义）', () => {
      // checkKeywordCombination 按顺序检测，先命中哪个返回哪个
      const store = useAchievementStore()
      const cards = [
        kw(1, '旧船票', 3, 1),
        kw(2, '半块玉佩', 3, 1),
        kw(3, '铜锁芯', 2, 1),
      ]
      const result = store.checkKeywordCombination(cards)
      // 三器物优先级高于三情绪，所以返回物是人非
      expect(result!.type).toBe(CombinationType.THREE_OBJECTS)
    })

    it('应只解锁一个组合成就（先命中优先）', () => {
      const store = useAchievementStore()
      // 三器物同时满足三绝品的条件？不对，器物的稀有度不都是4
      // 但三绝品可以是 category=1 的器物
      const cards = [
        kw(1, '绝品器物A', 4, 1),
        kw(2, '绝品器物B', 4, 1),
        kw(3, '绝品器物C', 4, 1),
      ]
      const result = store.checkKeywordCombination(cards)
      // 三器物在前，优先命中
      expect(result!.type).toBe(CombinationType.THREE_OBJECTS)
      expect(store.hasUnlocked('combo_three_objects')).toBe(true)
    })
  })

  describe('checkCombinationAchievements', () => {
    it('应解锁 铜墨兼备 当拥有说书人和铜锁芯', () => {
      const store = useAchievementStore()
      store.checkCombinationAchievements(
        [kw(1, '说书人', 2, 2)],
        [kw(2, '铜锁芯', 2, 1)]
      )
      expect(store.hasUnlocked('combo_speaker_lock')).toBe(true)
    })

    it('应解锁 物是人非 当关键词库中有3个器物', () => {
      const store = useAchievementStore()
      store.checkCombinationAchievements(
        [
          kw(1, '旧船票', 3, 1),
          kw(2, '半块玉佩', 3, 1),
          kw(3, '铜锁芯', 2, 1),
        ],
        []
      )
      expect(store.hasUnlocked('combo_three_objects')).toBe(true)
    })

    it('应解锁 百感交集 当关键词库中有3个情绪', () => {
      const store = useAchievementStore()
      store.checkCombinationAchievements(
        [
          kw(1, '意难平', 3, 4),
          kw(2, '亡国恨', 4, 4),
          kw(3, '离别苦', 3, 4),
        ],
        []
      )
      expect(store.hasUnlocked('combo_three_emotions')).toBe(true)
    })

    it('应解锁 离人 当拥有旧船票和摆渡人', () => {
      const store = useAchievementStore()
      store.checkCombinationAchievements(
        [kw(1, '旧船票', 3, 1)],
        [kw(2, '摆渡人', 2, 2)]
      )
      expect(store.hasUnlocked('combo_departing_person')).toBe(true)
    })

    it('应解锁 三水意象 当关键词库中有3个含水意象', () => {
      const store = useAchievementStore()
      store.checkCombinationAchievements(
        [
          kw(1, '乌江渡', 3, 3),
          kw(2, '雨纷纷', 2, 3),
          kw(3, '含泪', 3, 4),
        ],
        []
      )
      expect(store.hasUnlocked('combo_three_water')).toBe(true)
    })

    it('应解锁 三称谓 当关键词库中有3个称谓', () => {
      const store = useAchievementStore()
      store.checkCombinationAchievements(
        [
          kw(1, '阶下囚', 3, 5),
          kw(2, '末代王朝', 4, 5),
          kw(3, '说书人', 2, 5),
        ],
        []
      )
      expect(store.hasUnlocked('combo_three_titles')).toBe(true)
    })

    it('应解锁 三绝品 当关键词库中有3个绝稀有度', () => {
      const store = useAchievementStore()
      store.checkCombinationAchievements(
        [
          kw(1, '咸阳宫阙', 4, 3),
          kw(2, '亡国恨', 4, 4),
          kw(3, '末代王朝', 4, 5),
        ],
        []
      )
      expect(store.hasUnlocked('combo_three_legendary')).toBe(true)
    })
  })
})
