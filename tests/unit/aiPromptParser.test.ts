/**
 * AI Prompt 解析单元测试
 *
 * 测试覆盖：
 * - Prompt 模板变量替换
 * - 关键词融入检测
 * - AI腔词黑名单过滤
 * - 多Agent协作Prompt组装
 */

import { describe, it, expect, beforeEach } from 'vitest'

// ==================== 类型定义 ====================

interface PromptContext {
  chapter: number
  eventName: string
  identity: string
  keywords: string[]
  characters: Array<{ name: string; role: string; fate: number }>
  lastChoice?: string
  style?: string
  historyDeviation?: number
}

interface ParsedPrompt {
  systemPrompt: string
  userPrompt: string
  variables: Record<string, unknown>
}

interface AIWritingQuality {
  keywordIntegrationRate: number
  ai腔词List: string[]
  hasAI腔: boolean
  wordCount: number
}

// ==================== AI Prompt 解析器 ====================

// AI腔词黑名单
const AI_TROUBLE_WORDS = [
  '宛如',
  '仿佛',
  '仿若',
  '恰似',
  '如同',
  '宛若',
  '若隐若现',
  '无法言说',
  '难以言表',
  '不可名状',
  '别有一番滋味',
  '纵使',
  '纵然',
  '纵',
  '蓦然',
  '赫然',
  '已然',
  '悄然',
  '油然而生',
  '不自觉地',
  '不由自主地',
  '在不经意间',
  '时光荏苒',
  '岁月如梭',
  '斗转星移',
]

class AIPromptParser {
  /**
   * 构建说书人 Prompt
   */
  buildNarratorPrompt(context: PromptContext): ParsedPrompt {
    const systemPrompt = `你是一位温和的中年说书人，擅用白描语言。
请严格遵循以下要求：
1. 使用简洁的白描手法，避免华丽辞藻
2. 禁止使用以下词汇：${AI_TROUBLE_WORDS.join('、')}
3. 每段不超过3句话
4. 关键词必须自然融入场景描写`

    const userPrompt = `第${context.chapter}章
事件：${context.eventName}
身份：${context.identity}
关键词：${context.keywords.join('、')}
配角：${context.characters.map(c => `${c.name}(${c.role})`).join('、')}
上文选择：${context.lastChoice ?? '（暂无）'}
历史偏离度：${context.historyDeviation ?? 50}`

    return {
      systemPrompt,
      userPrompt,
      variables: context as Record<string, unknown>,
    }
  }

  /**
   * 构建判官 Prompt
   */
  buildJudgePrompt(context: PromptContext, optionText: string): ParsedPrompt {
    const systemPrompt = `你是一位冷面但公正的判官，擅长设置道德两难困境。
请评估当前选择对历史走向和人物命运的影响。`

    const userPrompt = `当前选择：${optionText}
配角命运：${context.characters.map(c => `${c.name}:${c.fate}`).join('、')}
关键词共鸣：${context.keywords.join('、')}
历史偏离度：${context.historyDeviation ?? 50}

请评估：
1. 该选择对配角命运的影响（-20到+20）
2. 历史偏离度变化
3. 生成下一场景描写`

    return {
      systemPrompt,
      userPrompt,
      variables: context as Record<string, unknown>,
    }
  }

  /**
   * 构建稗官后日谈 Prompt
   */
  buildBigaunPrompt(context: PromptContext, fullText: string): ParsedPrompt {
    const systemPrompt = `你是一位野史爱好者，擅长以"稗官曰"口吻点评历史故事。
请以简短幽默的笔调，写一段后日谈（200字以内）。`

    const userPrompt = `故事正文：${fullText.substring(0, 500)}...
配角：${context.characters.map(c => c.name).join('、')}
历史偏离度：${context.historyDeviation ?? 50}`

    return {
      systemPrompt,
      userPrompt,
      variables: context as Record<string, unknown>,
    }
  }

  /**
   * 解析 Prompt 中的变量
   */
  parseVariables(prompt: string): string[] {
    const regex = /\{(\w+)\}/g
    const variables: string[] = []
    let match

    while ((match = regex.exec(prompt)) !== null) {
      if (!variables.includes(match[1])) {
        variables.push(match[1])
      }
    }

    return variables
  }

  /**
   * 替换 Prompt 中的变量
   */
  replaceVariables(prompt: string, context: Record<string, unknown>): string {
    let result = prompt

    for (const [key, value] of Object.entries(context)) {
      const regex = new RegExp(`\\{${key}\\}`, 'g')
      result = result.replace(regex, String(value))
    }

    return result
  }

  /**
   * 检测文本中的 AI 腔词
   */
  detectAITroubleWords(text: string): string[] {
    const found: string[] = []

    for (const word of AI_TROUBLE_WORDS) {
      if (text.includes(word)) {
        found.push(word)
      }
    }

    return found
  }

  /**
   * 检查文本是否有 AI 腔
   */
  hasAITroubleWords(text: string): boolean {
    return this.detectAITroubleWords(text).length > 0
  }

  /**
   * 计算关键词融入率
   */
  calculateKeywordIntegrationRate(text: string, keywords: string[]): number {
    if (keywords.length === 0) return 100

    let foundCount = 0

    for (const keyword of keywords) {
      if (text.includes(keyword)) {
        foundCount++
      }
    }

    return (foundCount / keywords.length) * 100
  }

  /**
   * 评估 AI 生成质量
   */
  evaluateWritingQuality(text: string, keywords: string[]): AIWritingQuality {
    const ai腔词List = this.detectAITroubleWords(text)
    const keywordIntegrationRate = this.calculateKeywordIntegrationRate(text, keywords)
    const wordCount = this.countChineseWords(text)

    return {
      keywordIntegrationRate,
      ai腔词List,
      hasAI腔: ai腔词List.length > 0,
      wordCount,
    }
  }

  /**
   * 统计中文字数
   */
  countChineseWords(text: string): number {
    // 匹配中文字符（包括中文标点）
    const chineseRegex = /[\u4e00-\u9fa5\u3000-\u303f\uff00-\uffef]/g
    const matches = text.match(chineseRegex)
    return matches ? matches.length : 0
  }

  /**
   * 获取黑名单列表
   */
  getBlacklist(): string[] {
    return [...AI_TROUBLE_WORDS]
  }
}

// ==================== 测试用例 ====================

describe('AIPromptParser - AI Prompt 解析', () => {
  let parser: AIPromptParser
  let testContext: PromptContext

  beforeEach(() => {
    parser = new AIPromptParser()

    testContext = {
      chapter: 3,
      eventName: '赤壁·东风骤起',
      identity: '东吴水军将领',
      keywords: ['旧船票', '说书匠', '意难平'],
      characters: [
        { name: '周瑜', role: '命运羁绊', fate: 70 },
        { name: '黄盖', role: '历史节点', fate: 60 },
        { name: '老艄公', role: '市井过客', fate: 50 },
      ],
      lastChoice: '选择火攻',
      style: '白描',
      historyDeviation: 55,
    }
  })

  describe('Prompt 构建', () => {
    it('should build narrator prompt correctly', () => {
      const result = parser.buildNarratorPrompt(testContext)

      expect(result.systemPrompt).toContain('说书人')
      expect(result.userPrompt).toContain('第3章')
      expect(result.userPrompt).toContain('赤壁·东风骤起')
      expect(result.userPrompt).toContain('旧船票')
    })

    it('should build judge prompt correctly', () => {
      const result = parser.buildJudgePrompt(testContext, '下令火攻')

      expect(result.systemPrompt).toContain('判官')
      expect(result.userPrompt).toContain('下令火攻')
      expect(result.userPrompt).toContain('周瑜')
    })

    it('should build bigaun prompt correctly', () => {
      const fullText = '这是完整的故事正文...'
      const result = parser.buildBigaunPrompt(testContext, fullText)

      expect(result.systemPrompt).toContain('稗官曰')
      expect(result.userPrompt).toContain('周瑜')
    })

    it('should handle context without optional fields', () => {
      const minimalContext: PromptContext = {
        chapter: 1,
        eventName: '测试事件',
        identity: '测试身份',
        keywords: [],
        characters: [],
      }

      const result = parser.buildNarratorPrompt(minimalContext)

      expect(result.userPrompt).toContain('第1章')
      expect(result.userPrompt).toContain('（暂无）') // lastChoice默认值
    })
  })

  describe('变量解析', () => {
    it('should parse variables from prompt template', () => {
      const template = '第{chapter}章，事件：{eventName}，关键词：{keywords}'
      const variables = parser.parseVariables(template)

      expect(variables).toContain('chapter')
      expect(variables).toContain('eventName')
      expect(variables).toContain('keywords')
      expect(variables.length).toBe(3)
    })

    it('should not duplicate variables', () => {
      const template = '{name}对{name}说：{name}你很棒'
      const variables = parser.parseVariables(template)

      expect(variables).toEqual(['name'])
    })

    it('should replace variables correctly', () => {
      const template = '第{chapter}章，事件：{eventName}'
      const context = { chapter: 5, eventName: '测试' }
      const result = parser.replaceVariables(template, context)

      expect(result).toBe('第5章，事件：测试')
    })

    it('should keep unreplaced variables as-is', () => {
      const template = '第{chapter}章，未知：{unknown}'
      const context = { chapter: 5 }
      const result = parser.replaceVariables(template, context)

      expect(result).toBe('第5章，未知：{unknown}')
    })
  })

  describe('AI 腔词检测', () => {
    it('should detect single AI trouble word', () => {
      const text = '她的笑容宛如春风'
      const found = parser.detectAITroubleWords(text)

      expect(found).toContain('宛如')
    })

    it('should detect multiple AI trouble words', () => {
      const text = '纵使时光荏苒，岁月如梭，一切仿佛如梦'
      const found = parser.detectAITroubleWords(text)

      expect(found).toContain('纵使')
      expect(found).toContain('时光荏苒')
      expect(found).toContain('岁月如梭')
      expect(found).toContain('仿佛')
    })

    it('should return empty array for clean text', () => {
      const cleanText = '他站在江边，看着东去的江水，心中平静。'
      const found = parser.detectAITroubleWords(cleanText)

      expect(found).toEqual([])
    })

    it('should correctly identify hasAI腔', () => {
      const cleanText = '他走进村子，看见人们在劳作。'
      expect(parser.hasAITroubleWords(cleanText)).toBe(false)

      const dirtyText = '那景象仿若仙境'
      expect(parser.hasAITroubleWords(dirtyText)).toBe(true)
    })
  })

  describe('关键词融入率计算', () => {
    it('should calculate 100% when all keywords are present', () => {
      const text = '那张旧船票还留着，说书匠的故事总是意难平。'
      const keywords = ['旧船票', '说书匠', '意难平']

      const rate = parser.calculateKeywordIntegrationRate(text, keywords)

      expect(rate).toBe(100)
    })

    it('should calculate partial integration rate', () => {
      const text = '那张旧船票还留着。'
      const keywords = ['旧船票', '说书匠', '意难平']

      const rate = parser.calculateKeywordIntegrationRate(text, keywords)

      expect(rate).toBeCloseTo(33.33, 1)
    })

    it('should calculate 0% when no keywords present', () => {
      const text = '这是一个普通的故事。'
      const keywords = ['旧船票', '说书匠', '意难平']

      const rate = parser.calculateKeywordIntegrationRate(text, keywords)

      expect(rate).toBe(0)
    })

    it('should handle empty keywords array', () => {
      const text = '任何文本'
      const keywords: string[] = []

      const rate = parser.calculateKeywordIntegrationRate(text, keywords)

      expect(rate).toBe(100)
    })
  })

  describe('中文字数统计', () => {
    it('should count Chinese characters correctly', () => {
      const text = '这是一个测试。共十二个字。'

      const count = parser.countChineseWords(text)

      expect(count).toBe(12)
    })

    it('should handle text with English and numbers', () => {
      const text = '第1章：这是测试。Chapter 1'

      const count = parser.countChineseWords(text)

      expect(count).toBe(6) // 这是测试
    })

    it('should handle pure English text', () => {
      const text = 'Hello World'

      const count = parser.countChineseWords(text)

      expect(count).toBe(0)
    })
  })

  describe('AI 生成质量评估', () => {
    it('should evaluate good writing quality', () => {
      const goodText = '他站在江边，望着远去的船帆。旧船票还在口袋里，说书匠的故事还在耳边回响。意难平，却也只能向前走去。'
      const keywords = ['旧船票', '说书匠', '意难平']

      const quality = parser.evaluateWritingQuality(goodText, keywords)

      expect(quality.keywordIntegrationRate).toBe(100)
      expect(quality.hasAI腔).toBe(false)
      expect(quality.ai腔词List).toEqual([])
    })

    it('should detect AI trouble words in poor writing', () => {
      const poorText = '那笑容宛如春风，仿佛梦境一般纵然时光荏苒，岁月如梭，一切仿若隔世'
      const keywords = ['旧船票', '说书匠']

      const quality = parser.evaluateWritingQuality(poorText, keywords)

      expect(quality.hasAI腔).toBe(true)
      expect(quality.ai腔词List.length).toBeGreaterThan(0)
      expect(quality.keywordIntegrationRate).toBe(0)
    })

    it('should include word count in quality assessment', () => {
      const text = '这是一个完整的故事段落。'
      const keywords: string[] = []

      const quality = parser.evaluateWritingQuality(text, keywords)

      expect(quality.wordCount).toBeGreaterThan(0)
    })
  })

  describe('黑名单管理', () => {
    it('should return copy of blacklist', () => {
      const blacklist = parser.getBlacklist()

      expect(blacklist).toContain('宛如')
      expect(blacklist).toContain('仿佛')
      expect(blacklist.length).toBeGreaterThan(20)
    })

    it('should not allow modification of original blacklist', () => {
      const blacklist1 = parser.getBlacklist()
      blacklist1.push('测试词')

      const blacklist2 = parser.getBlacklist()

      expect(blacklist2).not.toContain('测试词')
    })
  })
})

// ==================== 边界测试 ====================

describe('AIPromptParser - 边界条件', () => {
  let parser: AIPromptParser

  beforeEach(() => {
    parser = new AIPromptParser()
  })

  it('should handle empty context', () => {
    const emptyContext: PromptContext = {
      chapter: 1,
      eventName: '',
      identity: '',
      keywords: [],
      characters: [],
    }

    const result = parser.buildNarratorPrompt(emptyContext)

    expect(result.systemPrompt).toBeDefined()
    expect(result.userPrompt).toContain('第1章')
  })

  it('should handle very long keywords list', () => {
    const longContext: PromptContext = {
      chapter: 1,
      eventName: '测试',
      identity: '测试',
      keywords: Array(100).fill('关键词'),
      characters: [],
    }

    const result = parser.buildNarratorPrompt(longContext)

    expect(result.userPrompt).toContain('关键词')
  })

  it('should handle text with special characters', () => {
    const specialText = '<script>alert("xss")</script> 和 "引号" 还有 '

    const troubleWords = parser.detectAITroubleWords(specialText)

    expect(Array.isArray(troubleWords)).toBe(true)
  })
})
