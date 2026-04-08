/**
 * 入局三问 E2E 测试
 *
 * 测试覆盖：
 * - 入局三问页面加载（卷轴样式）
 * - AI 生成的问题正确显示（3道）
 * - 用户可以填写答案
 * - 提交后跳转到故事页
 * - 答案存储在 storyStore / localStorage
 */

import { test, expect } from '@playwright/test'

const BASE_URL = process.env.E2E_BASE_URL || 'http://localhost:5175'

// ==================== Mock 数据 ====================

const MOCK_QUESTIONS = [
  {
    id: 1,
    category: '角色背景',
    question: '你今日当值，袖中揣着什么？',
    hint: '初始装备',
  },
  {
    id: 2,
    category: '当下处境',
    question: '你最怕见到什么人？',
    hint: '影响走向',
  },
  {
    id: 3,
    category: '内心渴望',
    question: '你最大的心愿是什么？',
    hint: '故事暗线',
  },
]

const MOCK_STORY_START = {
  id: '1',
  storyNo: 'ST-001',
  title: '赤壁·东风',
  status: 1,
  currentChapter: 1,
  historyDeviation: 0,
  createdAt: new Date().toISOString(),
}

const MOCK_CHAPTER_1 = {
  chapterNo: 1,
  sceneText: '赤壁江边，东风骤起。你站在船头，望着远处的曹营...',
  options: [
    { id: 1, text: '下令火攻', valueTag: '义' },
    { id: 2, text: '坚守不出', valueTag: '利' },
    { id: 3, text: '派人求和', valueTag: '情' },
  ],
  keywordResonance: { 1: 0, 2: 0, 3: 0 },
}

// ==================== API Mock 辅助函数 ====================

function setupEntryQuestionsMocks(page: any, storyId: string = '1') {
  // Extract origin from BASE_URL for reliable route matching
  const baseOrigin = BASE_URL.replace(/\/$/, '')

  // Mock POST /api/story/questions — AI 生成问题
  page.route(
    new RegExp(`${baseOrigin}/api/story/questions`),
    route => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ questions: MOCK_QUESTIONS }),
      })
    }
  )

  // Mock POST /api/story/answers — 提交答案并开始故事
  page.route(
    new RegExp(`${baseOrigin}/api/story/answers`),
    route => {
      const body = route.request().postData()
      const payload = body ? JSON.parse(body) : {}
      if (payload.entryAnswers) {
        console.log('[mock] submitEntryAnswers received entryAnswers:', payload.entryAnswers)
      }
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(MOCK_STORY_START),
      })
    }
  )

  // Mock GET /api/story/:id/chapter/1 — 获取第一章
  page.route(
    new RegExp(`${baseOrigin}/api/story/${storyId}/chapter/\\d+`),
    () => {
      return {
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(MOCK_CHAPTER_1),
      }
    }
  )
}

// ==================== E2E 测试用例 ====================

test.describe('入局三问模块', () => {

  test.beforeEach(async ({ page }) => {
    // Mock 必须先于页面导航注册，才能拦截初始 API 调用
    setupEntryQuestionsMocks(page, '1')
    await page.goto(`${BASE_URL}/entry-questions`)
  })

  test('页面应该使用卷轴样式（竖排布局）', async ({ page }) => {
    await expect(page.locator('[data-testid="entry-questions-container"]')).toBeVisible({ timeout: 10000 })
    // 检查问题区域使用竖排
    const scrollArea = page.locator('[data-testid="entry-questions-container"] .questions-vertical')
    const writingMode = await scrollArea.evaluate((el: Element) => {
      return window.getComputedStyle(el).writingMode
    })
    expect(writingMode).toMatch(/vertical-rl|vertical-lr/)
  })

  test('应该显示3个问题，每个有竖排输入框', async ({ page }) => {
    await expect(page.locator('[data-testid="question-item"]')).toHaveCount(3, { timeout: 10000 })
  })

  test('问题应该包含题目和提示文字', async ({ page }) => {
    const firstQuestion = page.locator('[data-testid="question-item"]').first()
    await expect(firstQuestion.locator('[data-testid="question-text"]')).toBeVisible()
    await expect(firstQuestion.locator('[data-testid="question-hint"]')).toBeVisible()
  })

  test('用户可以填写答案', async ({ page }) => {
    await page.waitForSelector('[data-testid="question-input"]', { timeout: 10000 })
    const inputs = page.locator('[data-testid="question-input"]')

    await inputs.nth(0).fill('半块干饼')
    await inputs.nth(1).fill('禁军的人')
    await inputs.nth(2).fill('平平安安活过今年')

    const input1Value = await inputs.nth(0).inputValue()
    const input2Value = await inputs.nth(1).inputValue()
    const input3Value = await inputs.nth(2).inputValue()

    expect(input1Value).toBe('半块干饼')
    expect(input2Value).toBe('禁军的人')
    expect(input3Value).toBe('平平安安活过今年')
  })

  test('提交后应跳转到故事页', async ({ page }) => {
    await page.waitForSelector('[data-testid="question-input"]', { timeout: 10000 })
    const inputs = page.locator('[data-testid="question-input"]')
    await inputs.nth(0).fill('半块干饼')
    await inputs.nth(1).fill('禁军的人')
    await inputs.nth(2).fill('平平安安活过今年')

    // 点击确认入局
    await page.locator('[data-testid="confirm-entry-btn"]').click()

    // 应该跳转到故事页
    await page.waitForURL(`**/story/**`, { timeout: 10000 })
    const url = page.url()
    expect(url).toContain('/story/')
  })

  test('提交时验证所有答案已填写', async ({ page }) => {
    await page.waitForSelector('[data-testid="question-input"]', { timeout: 10000 })

    // 只填第一个答案就提交
    await page.locator('[data-testid="question-input"]').first().fill('半块干饼')

    // 提交按钮应该被禁用
    const confirmBtn = page.locator('[data-testid="confirm-entry-btn"]')
    await expect(confirmBtn).toBeDisabled()
  })

  test('问题加载中状态应该显示', async ({ page }) => {
    const baseOrigin = BASE_URL.replace(/\/$/, '')
    // 重新注册 mock，延迟返回（先注册延迟的，再注册beforeEach的）
    page.route(
      new RegExp(`${baseOrigin}/api/story/questions`),
      async route => {
        await new Promise(resolve => setTimeout(resolve, 2000))
        route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ questions: MOCK_QUESTIONS }),
        })
      }
    )

    await page.goto(`${BASE_URL}/entry-questions`)

    // 初始应该显示加载状态
    await expect(page.locator('[data-testid="questions-loading"]')).toBeVisible({ timeout: 3000 })

    // 加载完成后显示问题
    await expect(page.locator('[data-testid="question-item"]')).toHaveCount(3, { timeout: 10000 })
  })
})

test.describe('入局三问 - 从卡牌选择页进入', () => {

  test('从卡牌页选择3张卡后应进入入局三问', async ({ page }) => {
    setupEntryQuestionsMocks(page, '1')

    // 模拟已选择3张卡的状态
    await page.goto(`${BASE_URL}/cards`)
    await page.evaluate(() => {
      const mockCards = [
        { id: 1, name: '旧船票', rarity: 2, category: 1 },
        { id: 2, name: '意难平', rarity: 3, category: 4 },
        { id: 3, name: '摆渡人', rarity: 2, category: 2 },
      ]
      localStorage.setItem('selectedKeywordCards', JSON.stringify(mockCards))
      localStorage.setItem('selectedEventCard', JSON.stringify({
        id: 101,
        name: '赤壁崖·东风骤起',
        rarity: 2,
        category: 1,
      }))
    })

    // 导航到入局三问
    await page.goto(`${BASE_URL}/entry-questions`)

    await expect(page.locator('[data-testid="entry-questions-container"]')).toBeVisible({ timeout: 10000 })
    await expect(page.locator('[data-testid="question-item"]')).toHaveCount(3, { timeout: 10000 })
  })
})
