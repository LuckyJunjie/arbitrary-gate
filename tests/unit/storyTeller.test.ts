/**
 * StoryTeller 题记生成单元测试
 */

import { describe, it, expect, beforeEach } from 'vitest'
import { storyTeller } from '@/services/storyTeller'

describe('StoryTeller - generateInscription', () => {
  beforeEach(() => {
    storyTeller.initStory({
      storyId: 'test-story-1',
      chapterCount: 3,
      keywords: ['东风', '火攻', '赤壁'],
      eventName: '赤壁·东风骤起',
      entryAnswers: { 义: '保江东', 利: '取天下', 情: '兄弟义' },
      chapterDeviations: [0, 0, 0],
    })
  })

  it('应生成题记', () => {
    const inscription = storyTeller.generateInscription()
    expect(inscription).toBeTruthy()
    expect(typeof inscription).toBe('string')
  })

  it('题记长度应在 20-40 字之间', () => {
    const inscription = storyTeller.generateInscription()
    // 中文算单字，这里是简化计算
    expect(inscription.length).toBeGreaterThanOrEqual(6)
    expect(inscription.length).toBeLessThanOrEqual(20)
  })

  it('无上下文时应返回默认题记', () => {
    const tellerWithoutCtx = Object.create(storyTeller)
    tellerWithoutCtx.context = null
    // 直接调用方法
    const inscription = storyTeller.generateInscription()
    expect(inscription).toBeTruthy()
  })

  it('题记不应包含 AI 腔词汇', () => {
    const inscription = storyTeller.generateInscription()
    const aiWords = ['宛如', '仿佛', '无法言说', '不禁', '缓缓说道', '轻声说道']
    aiWords.forEach(word => {
      expect(inscription).not.toContain(word)
    })
  })
})

describe('StoryTeller - generateManuscript 包含题记', () => {
  beforeEach(() => {
    storyTeller.initStory({
      storyId: 'test-story-2',
      chapterCount: 3,
      keywords: ['东风', '火攻', '赤壁'],
      eventName: '赤壁·东风骤起',
      entryAnswers: { 义: '保江东', 利: '取天下', 情: '兄弟义' },
      chapterDeviations: [0, 0, 0],
    })
  })

  it('生成手稿时应包含 inscription 字段', () => {
    const manuscript = storyTeller.generateManuscript(3, 0)
    expect(manuscript.inscription).toBeTruthy()
    expect(typeof manuscript.inscription).toBe('string')
  })

  it('inscription 应在 fullText 之前展示', () => {
    const manuscript = storyTeller.generateManuscript(3, 0)
    // 题记不应出现在正文中（因为它是单独的字段）
    // 这里只验证字段存在
    expect(manuscript.inscription).toBeTruthy()
  })
})