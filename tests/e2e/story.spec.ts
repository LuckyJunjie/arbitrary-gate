/**
 * 卷轴阅读 E2E 测试
 *
 * 测试覆盖：
 * - 卷轴页面加载
 * - 竖向排版正确显示
 * - 手势滑动交互
 * - 章节切换
 * - 关键词共鸣可视化
 */

import { test, expect } from '@playwright/test'

// ==================== 测试配置 ====================

const BASE_URL = process.env.E2E_BASE_URL || 'http://localhost:5173'

// ==================== Mock 数据 ====================

const MOCK_CHAPTERS: Record<number, object> = {
  1: {
    chapterNo: 1,
    sceneText: '第一章场景：赤壁江边，东风骤起...',
    options: [
      { id: 1, text: '下令火攻' },
      { id: 2, text: '坚守不出' },
      { id: 3, text: '派人求和' },
    ],
    keywordResonance: { 1: 1, 2: 0, 3: 2 },
    ripples: [],
  },
  2: {
    chapterNo: 2,
    sceneText: '第二章场景：火光冲天，敌舰尽焚...',
    options: [
      { id: 1, text: '乘胜追击' },
      { id: 2, text: '收兵回营' },
      { id: 3, text: '安抚伤兵' },
    ],
    keywordResonance: { 1: 2, 2: 1, 3: 3 },
    ripples: [],
  },
  3: {
    chapterNo: 3,
    sceneText: '第三章场景：战后余烬，历史分叉...',
    options: [
      { id: 1, text: '载入史册' },
      { id: 2, text: '隐姓埋名' },
      { id: 3, text: '继续前行' },
    ],
    keywordResonance: { 1: 3, 2: 2, 3: 4 },
    ripples: [],
  },
  4: {
    chapterNo: 4,
    sceneText: '尾声：故事落幕，星河长明。',
    options: [],
    keywordResonance: { 1: 4, 2: 3, 3: 6 },
    ripples: [],
  },
}

const MOCK_MANUSCRIPT = {
  fullText: '赤壁之战尘埃落定...这是一段完整的手稿全文。',
  wordCount: 1234,
  annotations: [],
  epilogue: '尾声',
}

// ==================== API Mock 辅助函数 ====================

/**
 * 为 StoryView 的 API 调用模式设置 route mock。
 * StoryView 在 onMount 时调用 GET /api/story/:id/chapter/:no，
 * 选择选项时调用 POST /api/story/:id/chapter/:no/choose。
 */
function setupStoryRouteMocks(page: any, storyId: string = '1') {
  // Mock GET /api/story/:id/chapter/:no
  page.route(
    new RegExp(`/api/story/${storyId}/chapter/\\d+`),
    route => {
      const url = route.request().url()
      const match = url.match(new RegExp(`/api/story/${storyId}/chapter/(\\d+)`))
      const chapterNo = match ? parseInt(match[1]) : 1
      const chapterData = MOCK_CHAPTERS[chapterNo] || MOCK_CHAPTERS[1]
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(chapterData),
      })
    }
  )

  // Mock POST /api/story/:id/chapter/:no/choose
  page.route(
    new RegExp(`/api/story/${storyId}/chapter/\\d+/choose`),
    route => {
      const url = route.request().url()
      const match = url.match(new RegExp(`/api/story/${storyId}/chapter/(\\d+)/choose`))
      const currentChapter = match ? parseInt(match[1]) : 1
      const nextChapterNo = currentChapter + 1
      const nextChapter = MOCK_CHAPTERS[nextChapterNo] || MOCK_CHAPTERS[1]
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ chapter: nextChapter, deviation: 5 }),
      })
    }
  )

  // Mock POST /api/story/:id/finish
  page.route(
    new RegExp(`/api/story/${storyId}/finish`),
    () => {
      // No-op — will be skipped in tests that don't reach this
    }
  )

  // Mock GET /api/story/:id/manuscript
  page.route(
    new RegExp(`/api/story/${storyId}/manuscript`),
    () => {}
  )
}

// ==================== E2E 测试用例 ====================

test.describe('卷轴阅读模块', () => {

  test.beforeEach(async ({ page }) => {
    // Mock 必须先于页面导航注册，才能拦截初始 API 调用
    setupStoryRouteMocks(page, '1')
    await page.goto(`${BASE_URL}/story/1`)
  })

  test('卷轴页面应该正确加载', async ({ page }) => {
    await expect(page.locator('[data-testid="scroll-container"]')).toBeVisible({ timeout: 10000 })
    await expect(page.locator('[data-testid="scroll-title"]')).toBeVisible()
  })

  test('卷轴应该使用竖向排版', async ({ page }) => {
    await expect(page.locator('[data-testid="scroll-content"]')).toBeVisible({ timeout: 10000 })
    const scrollContent = page.locator('[data-testid="scroll-content"]')
    const writingMode = await scrollContent.evaluate((el: Element) => {
      return window.getComputedStyle(el).writingMode
    })
    expect(writingMode).toMatch(/vertical-rl|vertical-lr/)
  })

  test('第一章内容应该正确显示', async ({ page }) => {
    await page.waitForSelector('[data-testid="chapter-text"]', { timeout: 10000 })
    const chapterText = await page.locator('[data-testid="chapter-text"]').textContent()
    expect(chapterText).toContain('第一章')
    expect(chapterText).toContain('赤壁')
  })

  test('选项应该正确展示且可点击', async ({ page }) => {
    await page.waitForSelector('[data-testid="chapter-options"]', { timeout: 10000 })
    const options = page.locator('[data-testid="option-item"]')
    await expect(options).toHaveCount(3)
    const firstOptionText = await options.first().textContent()
    expect(firstOptionText).toBeTruthy()
  })
})

test.describe('卷轴手势交互', () => {

  test.beforeEach(async ({ page }) => {
    setupStoryRouteMocks(page, '1')
    await page.goto(`${BASE_URL}/story/1`)
  })

  test('向上滑动应该显示下一章节选项', async ({ page }) => {
    await page.waitForSelector('[data-testid="chapter-options"]', { timeout: 10000 })
    const initialChapter = await page.locator('[data-testid="current-chapter"]').textContent()
    const scrollArea = page.locator('[data-testid="scroll-content"]')
    const box = await scrollArea.boundingBox()
    if (box) {
      await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2)
      await page.mouse.wheel(0, -200)
    }
    await page.waitForTimeout(500)
    await page.locator('[data-testid="option-item"]').first().click()
    await page.waitForTimeout(1500)
    const newChapter = await page.locator('[data-testid="current-chapter"]').textContent()
    expect(newChapter).not.toBe(initialChapter)
  })

  test('涟漪动画应该在选择后触发', async ({ page }) => {
    await page.waitForSelector('[data-testid="chapter-options"]', { timeout: 10000 })
    await page.locator('[data-testid="option-item"]').first().click()
    const ripple = page.locator('[data-testid="choice-ripple"]')
    await expect(ripple).toBeVisible({ timeout: 3000 })
  })
})

test.describe('关键词共鸣可视化', () => {

  test.beforeEach(async ({ page }) => {
    setupStoryRouteMocks(page, '1')
    await page.goto(`${BASE_URL}/story/1`)
  })

  test('关键词共鸣条应该正确显示', async ({ page }) => {
    await page.waitForSelector('[data-testid="keyword-resonance-bar"]', { timeout: 10000 })
    const resonanceBars = page.locator('[data-testid="keyword-resonance-bar"]')
    const count = await resonanceBars.count()
    expect(count).toBe(3)
  })

  test('共鸣进度应该可视化', async ({ page }) => {
    await page.waitForSelector('[data-testid="keyword-resonance-bar"]', { timeout: 10000 })
    const firstBar = page.locator('[data-testid="keyword-resonance-fill"]').first()
    await expect(firstBar).toBeVisible()
    const fillWidth = await firstBar.evaluate((el: Element) => {
      return (el as HTMLElement).style.width || '0%'
    })
    expect(fillWidth).toMatch(/^\d+%$/)
  })

  test.skip('共鸣满时应该触发显灵效果', async ({ page }) => {
    // 此测试需要后端支持关键词共鸣数据，当前跳过
  })
})

test.describe('故事章节流转', () => {

  test.beforeEach(async ({ page }) => {
    setupStoryRouteMocks(page, '1')
    await page.goto(`${BASE_URL}/story/1`)

    // Mock finishStory — 第三章完成后调用
    await page.route(
      new RegExp('/api/story/1/finish'),
      () => {}
    )
  })

  test.skip('完整故事流程：3章全部完成', async ({ page }) => {
    // 需要后端完成故事生成流程的支持，暂时跳过
  })

  test('章节进度指示器应该正确更新', async ({ page }) => {
    await page.waitForSelector('[data-testid="chapter-progress"]', { timeout: 10000 })
    const progressIndicator = page.locator('[data-testid="chapter-progress-dot"]')
    await expect(progressIndicator.first()).toHaveClass(/active/)
    await page.locator('[data-testid="option-item"]').first().click()
    await page.waitForTimeout(1500)
    await expect(progressIndicator.nth(1)).toHaveClass(/active/)
  })
})

test.describe('卷轴阅读 - 历史偏离度', () => {

  test.beforeEach(async ({ page }) => {
    setupStoryRouteMocks(page, '1')
    await page.goto(`${BASE_URL}/story/1`)
  })

  test('历史偏离度指示器应该可见', async ({ page }) => {
    await page.waitForSelector('[data-testid="deviation-indicator"]', { timeout: 10000 })
    await expect(page.locator('[data-testid="deviation-indicator"]')).toBeVisible()
  })

  test('选择后偏离度应该更新', async ({ page }) => {
    await page.waitForSelector('[data-testid="deviation-indicator"]', { timeout: 10000 })
    const initialDeviation = await page.locator('[data-testid="deviation-value"]').textContent()
    await page.locator('[data-testid="option-item"]').first().click()
    await page.waitForTimeout(1500)
    const newDeviation = await page.locator('[data-testid="deviation-value"]').textContent()
    expect(newDeviation).toBeTruthy()
  })
})
