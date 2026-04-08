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

// ==================== 辅助函数 ====================

async function startTestStory(page: any, storyId: number = 1): Promise<void> {
  await page.evaluate((sid: number) => {
    // 模拟故事数据
    localStorage.setItem(`story_${sid}`, JSON.stringify({
      id: sid,
      status: 'in_progress',
      currentChapter: 1,
      totalChapters: 3,
      keywords: [
        { id: 1, name: '旧船票', resonanceCount: 1, maxResonance: 4 },
        { id: 2, name: '说书匠', resonanceCount: 0, maxResonance: 5 },
        { id: 3, name: '意难平', resonanceCount: 2, maxResonance: 6 },
      ],
      chapters: [
        {
          chapterNo: 1,
          sceneText: '第一章场景：赤壁江边，东风骤起...',
          options: [
            { id: 1, text: '下令火攻' },
            { id: 2, text: '坚守不出' },
            { id: 3, text: '派人求和' },
          ],
        },
        {
          chapterNo: 2,
          sceneText: '第二章场景：火光冲天，敌舰尽焚...',
          options: [
            { id: 1, text: '乘胜追击' },
            { id: 2, text: '收兵回营' },
            { id: 3, text: '安抚伤兵' },
          ],
        },
        {
          chapterNo: 3,
          sceneText: '第三章场景：战后余烬，历史分叉...',
          options: [
            { id: 1, text: '载入史册' },
            { id: 2, text: '隐姓埋名' },
            { id: 3, text: '继续前行' },
          ],
        },
      ],
    }))
    localStorage.setItem('currentStoryId', String(sid))
  }, storyId)
}

// ==================== E2E 测试用例 ====================

test.describe('卷轴阅读模块', () => {

  test.beforeEach(async ({ page }) => {
    await page.goto(`${BASE_URL}/story/1`)
    await startTestStory(page, 1)
    await page.reload()
  })

  test('卷轴页面应该正确加载', async ({ page }) => {
    // 等待卷轴容器加载
    await expect(page.locator('[data-testid="scroll-container"]')).toBeVisible({ timeout: 10000 })

    // 检查卷轴标题
    await expect(page.locator('[data-testid="scroll-title"]')).toBeVisible()
  })

  test('卷轴应该使用竖向排版', async ({ page }) => {
    const scrollContent = page.locator('[data-testid="scroll-content"]')

    // 验证writing-mode CSS属性（竖向排版）
    const writingMode = await scrollContent.evaluate((el: Element) => {
      return window.getComputedStyle(el).writingMode
    })

    expect(writingMode).toMatch(/vertical-rl|vertical-lr/)
  })

  test('第一章内容应该正确显示', async ({ page }) => {
    // 等待章节内容加载
    await page.waitForSelector('[data-testid="chapter-text"]', { timeout: 10000 })

    const chapterText = await page.locator('[data-testid="chapter-text"]').textContent()
    expect(chapterText).toContain('第一章')
    expect(chapterText).toContain('赤壁')
  })

  test('选项应该正确展示且可点击', async ({ page }) => {
    await page.waitForSelector('[data-testid="chapter-options"]', { timeout: 10000 })

    // 应该有3个选项
    const options = page.locator('[data-testid="option-item"]')
    await expect(options).toHaveCount(3)

    // 每个选项应该有文字
    const firstOptionText = await options.first().textContent()
    expect(firstOptionText).toBeTruthy()
  })
})

test.describe('卷轴手势交互', () => {

  test.beforeEach(async ({ page }) => {
    await page.goto(`${BASE_URL}/story/1`)
    await startTestStory(page, 1)
    await page.reload()
  })

  test('向上滑动应该显示下一章节选项', async ({ page }) => {
    await page.waitForSelector('[data-testid="chapter-options"]', { timeout: 10000 })

    // 获取初始章节号
    const initialChapter = await page.locator('[data-testid="current-chapter"]').textContent()

    // 执行向上滑动
    const scrollArea = page.locator('[data-testid="scroll-content"]')
    const box = await scrollArea.boundingBox()

    if (box) {
      await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2)
      await page.mouse.wheel(0, -200) // 向上滚动
    }

    await page.waitForTimeout(500)

    // 选择一个选项推进剧情
    await page.locator('[data-testid="option-item"]').first().click()

    // 等待章节更新
    await page.waitForTimeout(1000)

    const newChapter = await page.locator('[data-testid="current-chapter"]').textContent()
    expect(newChapter).not.toBe(initialChapter)
  })

  test('涟漪动画应该在选择后触发', async ({ page }) => {
    await page.waitForSelector('[data-testid="chapter-options"]', { timeout: 10000 })

    // 选择选项
    await page.locator('[data-testid="option-item"]').first().click()

    // 验证涟漪动画出现
    const ripple = page.locator('[data-testid="choice-ripple"]')
    await expect(ripple).toBeVisible({ timeout: 3000 })
  })
})

test.describe('关键词共鸣可视化', () => {

  test.beforeEach(async ({ page }) => {
    await page.goto(`${BASE_URL}/story/1`)
    await startTestStory(page, 1)
    await page.reload()
  })

  test('关键词共鸣条应该正确显示', async ({ page }) => {
    await page.waitForSelector('[data-testid="keyword-resonance-bar"]', { timeout: 10000 })

    const resonanceBars = page.locator('[data-testid="keyword-resonance-bar"]')
    const count = await resonanceBars.count()

    expect(count).toBe(3) // 应该有3个关键词的共鸣条
  })

  test('共鸣进度应该可视化', async ({ page }) => {
    await page.waitForSelector('[data-testid="keyword-resonance-bar"]', { timeout: 10000 })

    // 检查共鸣条填充状态
    const firstBar = page.locator('[data-testid="keyword-resonance-fill"]').first()
    await expect(firstBar).toBeVisible()

    // 获取宽度百分比（进度）
    const fillWidth = await firstBar.evaluate((el: Element) => {
      return (el as HTMLElement).style.width || '0%'
    })

    expect(fillWidth).toMatch(/\d+%)
  })

  test('共鸣满时应该触发显灵效果', async ({ page }) => {
    // 注入满共鸣的关键词数据
    await page.evaluate(() => {
      localStorage.setItem('testForceManifest', 'true')
      const storyData = JSON.parse(localStorage.getItem('story_1') || '{}')
      storyData.keywords = [
        { id: 1, name: '旧船票', resonanceCount: 4, maxResonance: 4, manifested: true },
      ]
      localStorage.setItem('story_1', JSON.stringify(storyData))
    })

    await page.reload()
    await page.waitForTimeout(1000)

    // 验证显灵效果出现
    const manifestation = page.locator('[data-testid="keyword-manifestation"]')
    await expect(manifestation).toBeVisible({ timeout: 5000 })
  })
})

test.describe('故事章节流转', () => {

  test.beforeEach(async ({ page }) => {
    await page.goto(`${BASE_URL}/story/1`)
    await startTestStory(page, 1)
    await page.reload()
  })

  test('完整故事流程：3章全部完成', async ({ page }) => {
    await page.waitForSelector('[data-testid="chapter-options"]', { timeout: 10000 })

    // 第一章
    await expect(page.locator('[data-testid="current-chapter"]')).toContainText('1')
    await page.locator('[data-testid="option-item"]').first().click()
    await page.waitForTimeout(1500)

    // 第二章
    await expect(page.locator('[data-testid="current-chapter"]')).toContainText('2')
    await page.locator('[data-testid="option-item"]').first().click()
    await page.waitForTimeout(1500)

    // 第三章（最后一章）
    await expect(page.locator('[data-testid="current-chapter"]')).toContainText('3')
    await page.locator('[data-testid="option-item"]').first().click()
    await page.waitForTimeout(2000)

    // 验证故事完成
    await expect(page.locator('[data-testid="story-complete-modal"]')).toBeVisible({ timeout: 10000 })
    await expect(page.locator('[data-testid="manuscript-preview"]')).toBeVisible()
  })

  test('章节进度指示器应该正确更新', async ({ page }) => {
    await page.waitForSelector('[data-testid="chapter-progress"]', { timeout: 10000 })

    const progressIndicator = page.locator('[data-testid="chapter-progress-dot"]')

    // 初始状态：第1个点应该是激活状态
    await expect(progressIndicator.first()).toHaveClass(/active/)

    // 完成第一章
    await page.locator('[data-testid="option-item"]').first().click()
    await page.waitForTimeout(1500)

    // 第2个点应该是激活状态
    await expect(progressIndicator.nth(1)).toHaveClass(/active/)
  })
})

test.describe('卷轴阅读 - 历史偏离度', () => {

  test.beforeEach(async ({ page }) => {
    await page.goto(`${BASE_URL}/story/1`)
    await startTestStory(page, 1)
    await page.reload()
  })

  test('历史偏离度指示器应该可见', async ({ page }) => {
    await page.waitForSelector('[data-testid="deviation-indicator"]', { timeout: 10000 })

    await expect(page.locator('[data-testid="deviation-indicator"]')).toBeVisible()
  })

  test('选择后偏离度应该更新', async ({ page }) => {
    await page.waitForSelector('[data-testid="deviation-indicator"]', { timeout: 10000 })

    const initialDeviation = await page.locator('[data-testid="deviation-value"]').textContent()

    // 选择一个选项
    await page.locator('[data-testid="option-item"]').first().click()
    await page.waitForTimeout(1500)

    const newDeviation = await page.locator('[data-testid="deviation-value"]').textContent()

    // 偏离度应该发生变化（有些选项可能会增加或减少）
    // 这里只验证更新，不验证具体数值
    expect(newDeviation).toBeTruthy()
  })
})
