import { describe, it, expect, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAchievementStore, CombinationType } from '@/stores/achievementStore'

// 辅助：构造关键词卡
function kw(id: number, name: string, rarity: number, category: number) {
  return { id, name, rarity: rarity as 1 | 2 | 3 | 4, category: category as 1 | 2 | 3 | 4 | 5 }
}

describe('combo-achievement', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  describe('checkKeywordCombination - 入局组合检测', () => {
    it('应返回 null 当关键词不足 3 张', () => {
      const store = useAchievementStore()
      const result = store.checkKeywordCombination([
        kw(1, '旧船票', 3, 1),
        kw(2, '半块玉佩', 3, 1),
      ])
      expect(result).toBeNull()
    })

    it('应返回 null 当关键词超过 3 张', () => {
      const store = useAchievementStore()
      const result = store.checkKeywordCombination([
        kw(1, '旧船票', 3, 1),
        kw(2, '半块玉佩', 3, 1),
        kw(3, '铜锁芯', 2, 1),
        kw(4, '摆渡人', 2, 2),
      ])
      expect(result).toBeNull()
    })

    it('P-02: 三器物 - category 全为 1', () => {
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

    it('P-02: 三情绪 - category 全为 4', () => {
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

    it('P-03: 三称谓 - category 全为 5', () => {
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

    it('P-03: 三水意象 - 名称含水关键词', () => {
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

    it('P-03: 三绝品 - rarity 全为 4', () => {
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

    it('无稀有组合时返回 null', () => {
      const store = useAchievementStore()
      const cards = [
        kw(1, '旧船票', 3, 1),
        kw(2, '摆渡人', 2, 2),
        kw(3, '乌江渡', 3, 3),
      ]
      const result = store.checkKeywordCombination(cards)
      expect(result).toBeNull()
    })

    it('优先级: 三器物 > 三称谓 (代码顺序)', () => {
      // 如果同时满足多个组合条件，按代码顺序返回第一个
      const store = useAchievementStore()
      const cards = [
        kw(1, '旧船票', 3, 1),
        kw(2, '半块玉佩', 3, 1),
        kw(3, '铜锁芯', 2, 1),
      ]
      const result = store.checkKeywordCombination(cards)
      // 三器物在前，优先命中
      expect(result!.type).toBe(CombinationType.THREE_OBJECTS)
    })

    it('三器物同时满足时不触发三绝品 (按优先级)', () => {
      // 三器物但都是绝品 - 应该返回三器物而非三绝品
      const store = useAchievementStore()
      const cards = [
        kw(1, '绝品器物A', 4, 1),
        kw(2, '绝品器物B', 4, 1),
        kw(3, '绝品器物C', 4, 1),
      ]
      const result = store.checkKeywordCombination(cards)
      // 三器物在前，优先命中，返回三器物
      expect(result!.type).toBe(CombinationType.THREE_OBJECTS)
      expect(result!.type).not.toBe(CombinationType.THREE_LEGENDARY)
    })
  })

  describe('checkKeywordCombination - 水意象关键词列表', () => {
    const waterKeywords = ['雨', '泪', '江', '河', '湖', '海', '露', '霜', '溪', '涛', '泉', '沧', '浪', '潮', '沐']

    it('应识别 雨', () => {
      const store = useAchievementStore()
      const cards = [
        kw(1, '雨纷纷', 2, 3),
        kw(2, '春雨', 2, 3),
        kw(3, '雨巷', 2, 3),
      ]
      const result = store.checkKeywordCombination(cards)
      expect(result?.type).toBe(CombinationType.THREE_WATER)
    })

    it('应识别 泪', () => {
      const store = useAchievementStore()
      const cards = [
        kw(1, '泪如雨', 3, 4),
        kw(2, '独泪', 3, 4),
        kw(3, '含泪', 3, 4),
      ]
      const result = store.checkKeywordCombination(cards)
      expect(result?.type).toBe(CombinationType.THREE_WATER)
    })

    it('应识别 江', () => {
      const store = useAchievementStore()
      const cards = [
        kw(1, '乌江渡', 3, 3),
        kw(2, '长江', 2, 3),
        kw(3, '江雪', 2, 3),
      ]
      const result = store.checkKeywordCombination(cards)
      expect(result?.type).toBe(CombinationType.THREE_WATER)
    })
  })
})