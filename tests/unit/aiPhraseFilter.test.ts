import { describe, it, expect } from 'vitest'
import { filter, containsBlockedPhrase, getBlockedPhrases, AI_BLACKLIST } from '../../src/frontend/services/aiPhraseFilter'

describe('AI 腔词过滤器', () => {
  describe('filter()', () => {
    it('基本过滤：包含"宛如"的句子中"宛如"被替换', () => {
      const input = '月光洒落，宛如银纱。'
      const output = filter(input)
      expect(output).not.toContain('宛如')
      expect(output).toContain('月光洒落')
    })

    it('多词过滤：一句中多个黑名单词都被替换', () => {
      const input = '他宛如天神，仿佛降临，不可言说。'
      const output = filter(input)
      expect(output).not.toContain('宛如')
      expect(output).not.toContain('仿佛')
      expect(output).not.toContain('不可言说')
    })

    it('无黑名单词时原文返回', () => {
      const input = '江风扑面，带着水草与焦木的气息。'
      const output = filter(input)
      expect(output).toBe(input)
    })

    it('边界情况：空字符串返回原值', () => {
      expect(filter('')).toBe('')
      expect(filter(null as any)).toBe('')
    })

    it('边界情况：纯黑名单词字符串', () => {
      expect(filter('宛如')).toBe('')
      expect(filter('仿佛 仿若')).toBe('')
    })

    it('过滤后文本不含任何黑名单词（集成测试）', () => {
      const dirty = '此时此刻，仿佛曾几何时，无法言说，总的来说，可以说大概或许。'
      const cleaned = filter(dirty)
      for (const word of AI_BLACKLIST) {
        expect(cleaned).not.toContain(word)
      }
    })

    it('多个连续黑名单词之间空格处理', () => {
      const input = '事实上 实际上 可以说'
      const output = filter(input)
      expect(output).not.toContain('事实上')
      expect(output).not.toContain('实际上')
      expect(output).not.toContain('可以说')
    })

    it('边界情况：黑名单词在句首', () => {
      const input = '首先，我们要说明这一点。'
      const output = filter(input)
      expect(output).not.toContain('首先')
    })

    it('边界情况：黑名单词在句尾', () => {
      const input = '答案就是如此。'
      const output = filter(input)
      expect(output).not.toContain('如此')
    })
  })

  describe('containsBlockedPhrase()', () => {
    it('包含黑名单词时返回 true', () => {
      expect(containsBlockedPhrase('宛如月光')).toBe(true)
      expect(containsBlockedPhrase('仿佛')).toBe(true)
      expect(containsBlockedPhrase('无法言说的痛')).toBe(true)
    })

    it('不包含黑名单词时返回 false', () => {
      expect(containsBlockedPhrase('江风扑面')).toBe(false)
      expect(containsBlockedPhrase('')).toBe(false)
    })

    it('边界情况：空字符串', () => {
      expect(containsBlockedPhrase('')).toBe(false)
    })
  })

  describe('getBlockedPhrases()', () => {
    it('找出文本中所有黑名单词', () => {
      const input = '他宛如天神，仿佛降临，无法言说。'
      const found = getBlockedPhrases(input)
      expect(found).toContain('宛如')
      expect(found).toContain('仿佛')
      expect(found).toContain('无法言说')
    })

    it('返回去重后的列表', () => {
      const input = '宛如仿佛，宛如仿佛。'
      const found = getBlockedPhrases(input)
      expect(found).toContain('宛如')
      expect(found).toContain('仿佛')
      expect(found.length).toBe(2)
    })

    it('无黑名单词时返回空数组', () => {
      expect(getBlockedPhrases('江风扑面')).toEqual([])
    })

    it('边界情况：空字符串', () => {
      expect(getBlockedPhrases('')).toEqual([])
    })
  })

  describe('AI_BLACKLIST', () => {
    it('包含所有核心黑名单词', () => {
      const core = ['宛如', '仿佛', '仿若', '似乎', '宛若', '恰如', '恰似',
        '犹如', '一如既往', '无法言说', '难以言说', '不可言说',
        '不由自主', '不约而同', '此时此刻', '曾几何时',
        '不难发现', '众所周知', '可以说', '总的来说',
        '想必', '毋庸置疑', '总之', '总而言之',
        '事实上', '实际上', '话说回来', '话虽如此',
        '首先', '其次', '最后', '一方面', '另一方面']
      for (const word of core) {
        expect(AI_BLACKLIST.has(word)).toBe(true)
      }
    })

    it('黑名单词数量至少 50 个', () => {
      expect(AI_BLACKLIST.size).toBeGreaterThanOrEqual(50)
    })
  })
})
