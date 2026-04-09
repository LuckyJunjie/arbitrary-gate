/**
 * AI 画师服务 - AIPainterService
 * 负责生成关键词卡配图、场景图、分享故事卡
 *
 * 支持：通义万相 / Stable Diffusion / 备用占位图
 */

import axios from 'axios'

// ─── Types ────────────────────────────────────────────────────────────────────

export type Rarity = '凡' | '珍' | '奇' | '绝'
export type CardType = 'keyword' | 'event'

export interface CardImageParams {
  cardName: string
  cardType: CardType
  rarity: Rarity
  /** 可选风格描述 */
  style?: string
}

export interface SceneImageParams {
  storyTitle: string
  chapterNo: number
  sceneDescription: string
  keywords: string[]
}

export interface ShareCardParams {
  storyTitle: string
  judgeQuote: string
  firstParagraph: string
  coreKeyword: string
  /** 正面场景图 URL */
  sceneImageUrl?: string
}

export interface GenerationResult {
  imageUrl: string
  cached: boolean
}

// ─── Style Presets ────────────────────────────────────────────────────────────

const RARITY_COLORS: Record<Rarity, string> = {
  '凡': '素雅淡墨',
  '珍': '暖调赭石',
  '奇': '冷调黛青',
  '绝': '玉质泥金',
}

const CARD_TYPE_SCENE: Record<CardType, string> = {
  keyword: '器物近景，岁月痕迹，使用感',
  event: '历史场景，叙事氛围，重大时刻',
}

// ─── Prompt Builders ─────────────────────────────────────────────────────────

function buildKeywordPrompt(params: CardImageParams): string {
  const { cardName, cardType, rarity } = params
  return `水墨淡彩风格，浮世绘版画感，中国历史题材。
器物：${cardName}的特写，有岁月感，有使用痕迹。
背景：淡墨渲染的${CARD_TYPE_SCENE[cardType]}相关场景。
色调：${RARITY_COLORS[rarity]}，符合${rarity}品调性。
笔触：细腻工笔，淡彩渲染，避免CG厚涂，不要现代感，不要鲜艳色彩。
专注主体，背景留白，构图简洁。`
}

function buildScenePrompt(params: SceneImageParams): string {
  const { sceneDescription, keywords } = params
  const kw = keywords.slice(0, 2).join('，')
  return `水墨淡彩风格，中国古代场景。
场景描述：${sceneDescription}
氛围关键词：${kw}
人物：留白处理，不画具体五官，只画人物轮廓或背影。
色调：黛青、牙色、赭石为主，偏灰低饱和。
构图：全景叙事感，有故事张力。
不要CG感，不要3D渲染，不要厚涂。`
}

function buildShareCardPrompt(params: ShareCardParams): string {
  const { storyTitle, judgeQuote } = params
  return `水墨淡彩风格，中国古代题材的故事卡片。
标题：《${storyTitle}》
判词：${judgeQuote}
氛围：文人书房感，有书卷气，温暖怀旧。
色调：赭石、牙色为主。
构图：上方留白给标题，下方给判词，中间大面积给场景图。
不要CG感。`
}

// ─── Default Placeholder SVGs ─────────────────────────────────────────────────

function buildPlaceholderSvg(text: string, color = '#8B5E3C'): string {
  const lines = text.length > 8 ? [text.slice(0, 4), text.slice(4)] : [text]
  return `data:image/svg+xml,${encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" width="280" height="400" viewBox="0 0 280 400">
  <rect width="280" height="400" fill="#E8E0D5"/>
  <rect x="20" y="20" width="240" height="360" fill="none" stroke="${color}" stroke-width="2" rx="4"/>
  <text x="140" y="200" font-family="serif" font-size="32" fill="${color}" text-anchor="middle" dominant-baseline="middle">${lines[0] || ''}</text>
  ${lines[1] ? `<text x="140" y="240" font-family="serif" font-size="32" fill="${color}" text-anchor="middle">${lines[1]}</text>` : ''}
</svg>`)} }

// ─── AIPainterService ────────────────────────────────────────────────────────

class AIPainterService {
  /** 本地缓存：描述hash → 图片URL */
  private cache = new Map<string, GenerationResult>()
  private cacheExpiry = new Map<string, number>()
  private readonly CACHE_TTL = 1000 * 60 * 60 * 24 // 24h

  /**
   * 生成关键词卡配图
   * @param params 卡牌参数
   * @returns 图片URL（base64或http）
   */
  async generateKeywordCard(params: CardImageParams): Promise<GenerationResult> {
    const prompt = buildKeywordPrompt(params);
    const cacheKey = `kw:${params.cardName}:${params.cardType}:${params.rarity}`;

    const cached = this.getCached(cacheKey)
    if (cached) return cached

    try {
      const imageUrl = await this.callTongyiWanxiang(prompt)
      this.setCache(cacheKey, imageUrl)
      return { imageUrl, cached: false }
    } catch (err) {
      console.warn('[AIPainter] 关键词卡生成失败，使用占位图:', err)
      const placeholder = buildPlaceholderSvg(params.cardName)
      this.setCache(cacheKey, placeholder)
      return { imageUrl: placeholder, cached: false }
    }
  }

  /**
   * 生成场景图（故事卡分享用）
   */
  async generateSceneImage(params: SceneImageParams): Promise<GenerationResult> {
    const prompt = buildScenePrompt(params)
    const cacheKey = `scene:${params.storyTitle}:${params.chapterNo}:${params.keywords.join(',')}`

    const cached = this.getCached(cacheKey)
    if (cached) return cached

    try {
      const imageUrl = await this.callTongyiWanxiang(prompt)
      this.setCache(cacheKey, imageUrl)
      return { imageUrl, cached: false }
    } catch (err) {
      console.warn('[AIPainter] 场景图生成失败，使用占位图:', err)
      const placeholder = buildPlaceholderSvg(
        `${params.storyTitle} 第${params.chapterNo}章`,
        '#4A6B6B'
      )
      this.setCache(cacheKey, placeholder)
      return { imageUrl: placeholder, cached: false }
    }
  }

  /**
   * 生成分享故事卡（Canvas合成正面图）
   * 返回一个可用于 <img src> 的 data URL
   */
  async generateShareCard(params: ShareCardParams): Promise<string> {
    const { storyTitle, judgeQuote, firstParagraph, coreKeyword } = params

    // 如果有场景图，先获取
    let sceneUrl = ''
    if (params.sceneImageUrl) {
      sceneUrl = params.sceneImageUrl
    } else {
      const scene = await this.generateSceneImage({
        storyTitle,
        chapterNo: 1,
        sceneDescription: judgeQuote,
        keywords: [coreKeyword],
      })
      sceneUrl = scene.imageUrl
    }

    // Canvas 合成
    return this.composeShareCard({
      sceneUrl,
      storyTitle,
      judgeQuote,
      firstParagraph: firstParagraph.slice(0, 100),
      coreKeyword,
    })
  }

  /** 清除过期缓存 */
  clearExpiredCache(): void {
    const now = Date.now()
    for (const [key, expiry] of this.cacheExpiry) {
      if (now > expiry) {
        this.cache.delete(key)
        this.cacheExpiry.delete(key)
      }
    }
  }

  // ─── Private ────────────────────────────────────────────────────────────

  private getCached(key: string): GenerationResult | null {
    const url = this.cache.get(key)
    const expiry = this.cacheExpiry.get(key)
    if (url && expiry && Date.now() < expiry) {
      return { imageUrl: url, cached: true }
    }
    return null
  }

  private setCache(key: string, url: string): void {
    this.cache.set(key, { imageUrl: url, cached: false })
    this.cacheExpiry.set(key, Date.now() + this.CACHE_TTL)
  }

  /**
   * 调用通义万相 API
   * 环境变量: VITE_TONGYI_API_KEY
   */
  private async callTongyiWanxiang(prompt: string): Promise<string> {
    const apiKey = import.meta.env.VITE_TONGYI_API_KEY as string | undefined
    if (!apiKey) {
      throw new Error('VITE_TONGYI_API_KEY not set')
    }

    const response = await axios.post(
      'https://dashscope.aliyuncs.com/api/v1/services/aigc/text2image/image-synthesis',
      {
        model: 'wanx2.1',
        input: { prompt },
        parameters: {
          size: '1024*1024',
          steps: 20,
          n: 1,
        },
      },
      {
        headers: {
          Authorization: `Bearer ${apiKey}`,
          'Content-Type': 'application/json',
        },
        timeout: 30000,
      }
    )

    const imageUrl: string = response.data?.data?.result_url
    if (!imageUrl) throw new Error('No image URL in response')
    return imageUrl
  }

  /**
   * Canvas 合成分享卡
   */
  private composeShareCard(params: {
    sceneUrl: string
    storyTitle: string
    judgeQuote: string
    firstParagraph: string
    coreKeyword: string
  }): Promise<string> {
    return new Promise((resolve) => {
      const canvas = document.createElement('canvas')
      canvas.width = 375
      canvas.height = 667
      const ctx = canvas.getContext('2d')!

      // 1. 背景
      ctx.fillStyle = '#F5F0E6'
      ctx.fillRect(0, 0, 375, 667)

      // 2. 场景图（居中，顶部）
      const img = new Image()
      img.crossOrigin = 'anonymous'
      img.onload = () => {
        // 画场景图（居中裁切）
        const aspectRatio = img.width / img.height
        const drawW = 335
        const drawH = drawW / aspectRatio
        const drawX = (375 - drawW) / 2
        ctx.drawImage(img, drawX, 20, drawW, Math.min(drawH, 280))

        // 3. 标题
        ctx.fillStyle = '#2C2C2A'
        ctx.font = 'bold 22px "Source Han Serif CN", serif'
        ctx.textAlign = 'center'
        ctx.fillText(params.storyTitle, 187, 330)

        // 4. 分隔线
        ctx.strokeStyle = '#8B5E3C'
        ctx.lineWidth = 1
        ctx.beginPath()
        ctx.moveTo(60, 340)
        ctx.lineTo(315, 340)
        ctx.stroke()

        // 5. 判词
        ctx.fillStyle = '#6B6B6B'
        ctx.font = '14px "Source Han Serif CN", serif'
        // 判词换行
        this.wrapText(ctx, `「${params.judgeQuote}」`, 60, 365, 255, 22)

        // 6. 正文第一段
        ctx.fillStyle = '#4A4A4A'
        ctx.font = '13px "Source Han Serif CN", serif'
        this.wrapText(ctx, params.firstParagraph + '…', 40, 430, 295, 20)

        // 7. 缺角（右下角，圆形缺角 = 核心关键词占位）
        ctx.fillStyle = '#F5F0E6'
        ctx.beginPath()
        ctx.arc(375, 667, 40, 0, Math.PI * 2)
        ctx.fill()
        ctx.strokeStyle = '#8B5E3C'
        ctx.lineWidth = 1
        ctx.stroke()

        // 缺角内画关键词
        ctx.fillStyle = '#8B5E3C'
        ctx.font = '10px serif'
        ctx.textAlign = 'center'
        ctx.save()
        ctx.translate(335, 627)
        ctx.rotate(Math.PI / 4)
        ctx.fillText(params.coreKeyword.slice(0, 2), 0, 0)
        ctx.restore()

        resolve(canvas.toDataURL('image/png'))
      }
      img.onerror = () => {
        // 图片加载失败，用纯色背景+文字代替
        ctx.fillStyle = '#E8E0D5'
        ctx.fillRect(0, 0, 375, 280)
        ctx.fillStyle = '#8B5E3C'
        ctx.font = '16px serif'
        ctx.textAlign = 'center'
        ctx.fillText('[ 场景图 ]', 187, 150)
        resolve(canvas.toDataURL('image/png'))
      }
      img.src = params.sceneUrl
    })
  }

  /** 简易换行文字 */
  private wrapText(
    ctx: CanvasRenderingContext2D,
    text: string,
    x: number,
    y: number,
    maxWidth: number,
    lineHeight: number
  ): void {
    let line = ''
    for (const char of text) {
      const test = line + char
      if (ctx.measureText(test).width > maxWidth && line) {
        ctx.fillText(line, x, y)
        line = char
        y += lineHeight
      } else {
        line = test
      }
    }
    if (line) ctx.fillText(line, x, y)
  }
}

// ─── Singleton ────────────────────────────────────────────────────────────────

export const aiPainter = new AIPainterService()
