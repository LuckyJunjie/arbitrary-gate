/**
 * 掌眼 Agent 单元测试
 *
 * 测试覆盖：
 * - 黑名单词汇过滤（删除/替换）
 * - 正常文本不受影响
 * - findBlacklistWords 正确识别
 * - 连续空格清理
 * - 句首残留标点清理
 */

import { describe, it, expect } from 'vitest'
import {
  BLACKLIST,
  filterLocal,
  findBlacklistWords,
  filterManuscript,
  filterWithReport,
} from '../../src/frontend/services/zhangyan'

describe('掌眼 Agent - filterLocal', () => {
  it('应删除"不禁"', () => {
    expect(filterLocal('他不禁流下眼泪')).toBe('他流下眼泪')
  })

  it('应替换"宛如"为"像"', () => {
    expect(filterLocal('水面宛如镜子')).toBe('水面像镜子')
  })

  it('应替换"仿佛"为"好像"', () => {
    expect(filterLocal('远处仿佛有灯火')).toBe('远处好像有灯火')
  })

  it('应替换"无法言说"为"说不清"', () => {
    expect(filterLocal('心中无法言说的惆怅')).toBe('心中说不清的惆怅')
  })

  it('应替换"缓缓说道"为"说"', () => {
    expect(filterLocal('老人缓缓说道')).toBe('老人说')
  })

  it('应替换"轻声说道"为"低声道"', () => {
    expect(filterLocal('她轻声说道')).toBe('她低声道')
  })

  it('应替换"目光中满是"为"眼里尽是"', () => {
    expect(filterLocal('目光中满是期待')).toBe('眼里尽是期待')
  })

  it('应替换"心中一动"为"心头一紧"', () => {
    expect(filterLocal('他心中一动')).toBe('他心头一紧')
  })

  it('应替换"似乎在诉说"为"像是在说"', () => {
    expect(filterLocal('远山似乎在诉说')).toBe('远山像是在说')
  })

  it('应处理多个黑名单词共存的情况', () => {
    const input = '他目光中满是期待，不禁轻声说道："宛如梦境。"'
    const output = filterLocal(input)
    expect(output).not.toContain('不禁')
    expect(output).not.toContain('宛如')
    expect(output).not.toContain('轻声说道')
    expect(output).not.toContain('目光中满是')
  })

  it('应清理连续空格', () => {
    // \s{2,} 将连续空格归并为单个空格
    expect(filterLocal('老人说，  他')).toBe('老人说， 他')
    expect(filterLocal('word   word')).toBe('word word')
  })

  it('应清理句首残留标点', () => {
    expect(filterLocal('，他说')).toBe('他说')
    expect(filterLocal('。他说')).toBe('他说')
  })

  it('不应影响正常文本内容', () => {
    const normal = '江风扑面，带着水草与焦木的气息。你站在赤壁对岸的山崖上，眼前是连绵的曹营。三日前，这里还是樯橹如林、帆影蔽日。'
    const filtered = filterLocal(normal)
    expect(filtered.length).toBeGreaterThan(0)
    expect(filtered).toContain('江风扑面')
    expect(filtered).toContain('赤壁')
  })

  it('应处理空字符串', () => {
    expect(filterLocal('')).toBe('')
  })

  it('应处理 null/undefined 边界', () => {
    expect(filterLocal(null as unknown as string)).toBeNull()
    expect(filterLocal(undefined as unknown as string)).toBeUndefined()
  })
})

describe('掌眼 Agent - findBlacklistWords', () => {
  it('应正确识别单个黑名单词', () => {
    const result = findBlacklistWords('他目光中满是期待')
    expect(result).toContain('目光中满是')
  })

  it('应识别多个黑名单词', () => {
    const result = findBlacklistWords('他不禁轻声说道，宛如梦境')
    expect(result).toContain('不禁')
    expect(result).toContain('轻声说道')
    expect(result).toContain('宛如')
  })

  it('应返回空数组当无黑名单词', () => {
    const result = findBlacklistWords('江风扑面，水草气息')
    expect(result).toHaveLength(0)
  })

  it('应处理空字符串', () => {
    expect(findBlacklistWords('')).toHaveLength(0)
  })
})

describe('掌眼 Agent - filterWithReport', () => {
  it('应返回替换词列表', () => {
    const { filtered, replacedWords } = filterWithReport('他不禁轻声说道')
    expect(replacedWords).toContain('不禁')
    expect(replacedWords).toContain('轻声说道')
    expect(filtered).not.toContain('不禁')
    expect(filtered).not.toContain('轻声说道')
  })
})

describe('掌眼 Agent - filterManuscript', () => {
  it('filterManuscript 应为 filterLocal 的别名', () => {
    const input = '水面宛如镜子，远处仿佛有灯火'
    expect(filterManuscript(input)).toBe(filterLocal(input))
  })
})

describe('掌眼 Agent - 黑名单词表完整性', () => {
  const expectedWords = [
    '宛如',
    '仿佛',
    '无法言说',
    '不禁',
    '缓缓说道',
    '轻声说道',
    '目光中满是',
    '心中一动',
    '似乎在诉说',
  ]

  it('黑名单应包含所有预期词汇', () => {
    expectedWords.forEach(word => {
      expect(BLACKLIST).toContain(word)
    })
  })

  it('黑名单数量应为 9', () => {
    expect(BLACKLIST).toHaveLength(9)
  })
})
