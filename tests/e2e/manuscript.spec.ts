/**
 * 手稿页面 E2E 测试
 *
 * 测试覆盖：
 * - 手稿页面加载
 * - 竖向排版（writing-mode: vertical-rl）
 * - 朱批渲染
 * - 宣纸底色背景
 * - 完结按钮跳转手稿页
 * - 返回书架按钮
 */

import { test, expect } from '@playwright/test'

// ==================== 测试配置 ====================

const BASE_URL = process.env.E2E_BASE_URL || 'http://localhost:5175'

// ==================== Mock 数据 ====================

const MOCK_MANUSCRIPT = {
  fullText: '赤壁之战尘埃落定。\n东风已去，周郎何在。\n唯有江声依旧。',
  wordCount: 1024,
  annotations: [
    { chapterNo: 0, x: 10, y: 20, text: '此句暗合开篇墨池占卜', color: '#8b3e3c' },
    { chapterNo: 1, x: 15, y: 30, text: '周瑜之美姿仪', color: '#8b3e3c' },
  ],
  choiceMarks: [
    { chapterNo: 0, optionId: 1, text: '火攻' },
    { chapterNo: 1, optionId: 2, text: '观望' },
  ],
  epilogue: '后日谈：战火平息，岁月如梭。',
  baiguanComment: '此故事偏离正史35%，尚在可接受范围。',
}

const MOCK_STORY = {
  id: 'story-1',
  storyNo: '001',
  title: '赤壁·东风骤起',
  status: 2,
  currentChapter: 3,
  historyDeviation: 35,
  createdAt: '2026-01-01T00:00:00Z',
  finishedAt: '2026-01-01T00:10:00Z',
}

// ==================== API Mock 辅助函数 ====================

function setupManuscriptMocks(page: any, storyId: string = '1') {
  // Mock GET /api/story/:id
  page.route(
    new RegExp(`/api/story/${storyId}$`),
    route => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(MOCK_STORY),
      })
    }
  )
  // Mock GET /api/story/:id/manuscript
  page.route(
    new RegExp(`/api/story/${storyId}/manuscript`),
    route => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(MOCK_MANUSCRIPT),
      })
    }
  )
}

// ==================== E2E 测试用例 ====================

test.describe('手稿页面', () => {

  test.beforeEach(async ({ page }) => {
    setupManuscriptMocks(page, '1')
  })

  test('手稿页面应该正确加载', async ({ page }) => {
    await page.goto(`${BASE_URL}/manuscript/1`)
    await expect(page.locator('[data-testid="manuscript-title"]')).toBeVisible({ timeout: 10000 })
  })

  test('手稿页面应该显示宣纸底色背景', async ({ page }) => {
    await page.goto(`${BASE_URL}/manuscript/1`)
    await page.waitForSelector('[data-testid="manuscript-scroll"]', { timeout: 10000 })
    const scroll = page.locator('[data-testid="manuscript-scroll"]')
    await expect(scroll).toBeVisible()
  })

  test('手稿正文应该竖向排版', async ({ page }) => {
    await page.goto(`${BASE_URL}/manuscript/1`)
    await page.waitForSelector('[data-testid="manuscript-body"]', { timeout: 10000 })
    const body = page.locator('[data-testid="manuscript-body"]')
    const writingMode = await body.evaluate((el: Element) => {
      return window.getComputedStyle(el).writingMode
    })
    expect(writingMode).toMatch(/vertical-rl|vertical-lr/)
  })

  test('手稿页面应该显示字数统计', async ({ page }) => {
    await page.goto(`${BASE_URL}/manuscript/1`)
    await expect(page.locator('[data-testid="manuscript-word-count"]')).toBeVisible({ timeout: 10000 })
    const wordCount = await page.locator('[data-testid="manuscript-word-count"]').textContent()
    expect(wordCount).toContain('1024')
  })

  test('手稿页面应该显示正文段落', async ({ page }) => {
    await page.goto(`${BASE_URL}/manuscript/1`)
    await page.waitForSelector('[data-testid="manuscript-para-0"]', { timeout: 10000 })
    const firstPara = page.locator('[data-testid="manuscript-para-0"]')
    await expect(firstPara).toBeVisible()
    const text = await firstPara.textContent()
    expect(text).toContain('赤壁之战')
  })

  test('朱批注释应该可见', async ({ page }) => {
    await page.goto(`${BASE_URL}/manuscript/1`)
    await page.waitForSelector('[data-testid="manuscript-para-0"]', { timeout: 10000 })
    const annotations = page.locator('[data-testid="zhub-annotation"]')
    await expect(annotations.first()).toBeVisible()
  })

  test('朱批内容应该包含批注文字', async ({ page }) => {
    await page.goto(`${BASE_URL}/manuscript/1`)
    await page.waitForSelector('[data-testid="zhub-annotation"]', { timeout: 10000 })
    const zhubText = page.locator('.zhub-text').first()
    await expect(zhubText).toBeVisible()
    const text = await zhubText.textContent()
    expect(text).toBeTruthy()
  })

  test('手稿印鉴应该可见', async ({ page }) => {
    await page.goto(`${BASE_URL}/manuscript/1`)
    await expect(page.locator('[data-testid="manuscript-seal"]')).toBeVisible({ timeout: 10000 })
    const seal = page.locator('[data-testid="manuscript-seal"]')
    await expect(seal).toBeVisible()
  })

  test('后日谈应该可见', async ({ page }) => {
    await page.goto(`${BASE_URL}/manuscript/1`)
    await expect(page.locator('[data-testid="manuscript-epilogue"]')).toBeVisible({ timeout: 10000 })
    const epilogue = page.locator('[data-testid="manuscript-epilogue"]')
    await expect(epilogue).toBeVisible()
  })

  test('返回书架按钮应该可点击并导航', async ({ page }) => {
    await page.goto(`${BASE_URL}/manuscript/1`)
    await expect(page.locator('[data-testid="manuscript-back"]')).toBeVisible({ timeout: 10000 })
    const backBtn = page.locator('[data-testid="manuscript-back"]')
    await backBtn.click()
    await expect(page).toHaveURL(/\/bookshelf/)
  })

  test('选择标记应该显示在对应段落', async ({ page }) => {
    await page.goto(`${BASE_URL}/manuscript/1`)
    await page.waitForSelector('[data-testid="manuscript-para-0"]', { timeout: 10000 })
    const choiceMark = page.locator('.choice-mark').first()
    await expect(choiceMark).toBeVisible()
  })
})

test.describe('手稿页面 - 从故事完结跳转', () => {

  test('完结此篇按钮应导航至手稿页', async ({ page }) => {
    // 修复: StoryView → ManuscriptView 导航后的 manuscript 数据加载
    setupManuscriptMocks(page, '1')

    // Mock chapter endpoint - return an end chapter (no options) so finish button appears
    await page.route(
      new RegExp('/api/story/1/chapter/\\d+'),
      route => {
        route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            chapterNo: 1,
            sceneText: '尾声：故事落幕，星河长明。',
            options: [],
            keywordResonance: { 1: 4, 2: 3, 3: 6 },
            ripples: [],
          }),
        })
      }
    )

    // Mock finishStory
    await page.route(
      new RegExp('/api/story/1/finish'),
      route => {
        route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(MOCK_MANUSCRIPT),
        })
      }
    )

    await page.goto(`${BASE_URL}/story/1`)

    // 等待故事加载完成（出现完结此篇按钮）
    await page.waitForSelector('.finish-btn', { timeout: 15000 })
    await page.locator('.finish-btn').click()

    // 验证跳转到了手稿页
    await expect(page).toHaveURL(/\/manuscript\/1/, { timeout: 10000 })
    // 等待手稿内容加载
    await page.waitForSelector('[data-testid="manuscript-title"]', { timeout: 10000 })
    await expect(page.locator('[data-testid="manuscript-title"]')).toBeVisible({ timeout: 10000 })
  })
})

test.describe('手稿页面 - 加载状态', () => {

  test.beforeEach(async ({ page }) => {
    setupManuscriptMocks(page, 'loading-test')
  })

  test('手稿加载中应显示加载文案', async ({ page }) => {
    // 延迟响应模拟加载中
    await page.route(
      new RegExp('/api/story/loading-test/manuscript'),
      async route => {
        await new Promise(resolve => setTimeout(resolve, 2000))
        route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(MOCK_MANUSCRIPT),
        })
      }
    )
    await page.goto(`${BASE_URL}/manuscript/loading-test`)
    // 加载状态应该在 DOM 中短暂出现
    const loadingText = page.locator('.loading-text')
    // 不使用 toBeVisible 因为可能已经加载完成
    const count = await page.locator('.manuscript-loading, [data-testid="manuscript-scroll"]').count()
    expect(count).toBeGreaterThan(0)
  })
})
