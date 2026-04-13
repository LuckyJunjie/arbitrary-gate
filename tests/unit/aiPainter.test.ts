/**
 * aiPainter.ts 单元测试
 * C-14 AI画师后端对接完善
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { aiPainter, type CardImageParams, type SceneImageParams } from '../../src/frontend/services/aiPainter'

// Mock fetch globally
function mockFetch(response: unknown, ok = true) {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
    ok,
    json: () => Promise.resolve(response),
  }))
}

function mockFetchError(err: Error) {
  vi.stubGlobal('fetch', vi.fn().mockRejectedValue(err))
}

describe('AIPainterService', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', undefined)
    vi.stubGlobal('import', undefined)
    // Reset cache by clearing it
    aiPainter.clearExpiredCache()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  // ─── Prompt Builders ────────────────────────────────────────────────────

  describe('generateKeywordCard (prompt builder logic)', () => {
    it('生成关键词卡时不抛异常', async () => {
      mockFetch({ data: { imageUrl: 'https://example.com/img.png' } })
      const params: CardImageParams = { cardName: '青铜剑', cardType: 'keyword', rarity: '珍' }
      const result = await aiPainter.generateKeywordCard(params)
      expect(result.imageUrl).toBe('https://example.com/img.png')
      expect(result.cached).toBe(false)
    })

    it('缓存命中时直接返回不调API', async () => {
      let callCount = 0
      mockFetch({
        data: {
          imageUrl: 'https://example.com/cached.png',
        },
      })
      const params: CardImageParams = { cardName: '青铜剑', cardType: 'keyword', rarity: '珍' }
      await aiPainter.generateKeywordCard(params)
      await aiPainter.generateKeywordCard(params) // same params → cached
      // Second call should hit cache
      const result = await aiPainter.generateKeywordCard(params)
      expect(result.cached).toBe(true)
    })

    it('后端返回非2xx时降级到占位图', async () => {
      mockFetch({ error: 'rate limit' }, false)
      const params: CardImageParams = { cardName: '瓷器', cardType: 'event', rarity: '奇' }
      const result = await aiPainter.generateKeywordCard(params)
      expect(result.imageUrl).toMatch(/^data:image\/svg\+xml/)
      expect(result.cached).toBe(false)
    })

    it('后端抛出异常时降级到占位图', async () => {
      mockFetchError(new Error('network error'))
      const params: CardImageParams = { cardName: '古籍', cardType: 'keyword', rarity: '绝' }
      const result = await aiPainter.generateKeywordCard(params)
      expect(result.imageUrl).toMatch(/^data:image\/svg\+xml/)
      expect(result.cached).toBe(false)
    })
  })

  describe('generateSceneImage', () => {
    it('生成场景图成功', async () => {
      mockFetch({ data: { imageUrl: 'https://example.com/scene.png' } })
      const params: SceneImageParams = {
        storyTitle: '长安月',
        chapterNo: 1,
        sceneDescription: '灯火阑珊的夜市',
        keywords: ['月', '灯'],
      }
      const result = await aiPainter.generateSceneImage(params)
      expect(result.imageUrl).toBe('https://example.com/scene.png')
      expect(result.cached).toBe(false)
    })

    it('场景图网络异常时降级到占位SVG', async () => {
      mockFetchError(new Error('timeout'))
      const params: SceneImageParams = {
        storyTitle: '洛阳春',
        chapterNo: 2,
        sceneDescription: '牡丹花会',
        keywords: ['花', '春'],
      }
      const result = await aiPainter.generateSceneImage(params)
      expect(result.imageUrl).toMatch(/^data:image\/svg\+xml/)
    })
  })

  describe('clearExpiredCache', () => {
    it('不抛异常', () => {
      expect(() => aiPainter.clearExpiredCache()).not.toThrow()
    })
  })
})
