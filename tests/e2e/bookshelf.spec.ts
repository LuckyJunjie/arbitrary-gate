/**
 * 书架管理 E2E 测试
 *
 * 测试覆盖：
 * - 书架页面加载
 * - 时光轴视图
 * - 山河图视图
 * - 故事卡管理
 * - 筛选和排序
 */

import { test, expect } from '@playwright/test'

// ==================== 测试配置 ====================

const BASE_URL = process.env.E2E_BASE_URL || 'http://localhost:5175'

// ==================== 辅助函数 ====================

async function setupMockData(page: any): Promise<void> {
  await page.evaluate(() => {
    // 模拟书架数据
    const mockStories = [
      {
        id: 1,
        storyNo: 'SN001',
        title: '赤壁往事',
        eventName: '赤壁·东风骤起',
        status: 'completed',
        historyDeviation: 35,
        wordCount: 4500,
        createdAt: '2024-01-15T10:00:00Z',
        finishedAt: '2024-01-15T11:30:00Z',
        keywords: [
          { id: 1, name: '旧船票', rarity: 2 },
          { id: 2, name: '说书匠', rarity: 3 },
        ],
      },
      {
        id: 2,
        storyNo: 'SN002',
        title: '马嵬月下',
        eventName: '马嵬驿·杨贵妃缢死',
        status: 'completed',
        historyDeviation: 72,
        wordCount: 5200,
        createdAt: '2024-01-16T14:00:00Z',
        finishedAt: '2024-01-16T16:00:00Z',
        keywords: [
          { id: 3, name: '意难平', rarity: 4 },
          { id: 4, name: '青石板', rarity: 1 },
        ],
      },
      {
        id: 3,
        storyNo: 'SN003',
        title: '待续...',
        eventName: '玄武门·李世民射兄',
        status: 'in_progress',
        historyDeviation: 50,
        wordCount: 0,
        createdAt: '2024-01-17T09:00:00Z',
        finishedAt: null,
        keywords: [
          { id: 5, name: '铜锁芯', rarity: 2 },
        ],
        currentChapter: 2,
        totalChapters: 5,
      },
    ]

    localStorage.setItem('bookshelf_stories', JSON.stringify(mockStories))
    localStorage.setItem('currentUserId', '1')
  })
}

// ==================== E2E 测试用例 ====================

test.describe('书架管理模块', () => {

  test.beforeEach(async ({ page }) => {
    await page.goto(`${BASE_URL}/bookshelf`)
    await setupMockData(page)
    await page.reload()
  })

  test('书架页面应该正确加载', async ({ page }) => {
    // 等待书架容器加载
    await expect(page.locator('[data-testid="bookshelf-container"]')).toBeVisible({ timeout: 10000 })

    // 检查书架标题
    await expect(page.locator('[data-testid="bookshelf-title"]')).toContainText('书架')
  })

  test('应该显示故事卡片列表', async ({ page }) => {
    await page.waitForSelector('[data-testid="story-card"]', { timeout: 10000 })

    const storyCards = page.locator('[data-testid="story-card"]')
    const count = await storyCards.count()

    expect(count).toBeGreaterThan(0)
  })

  test('故事卡片应该显示关键信息', async ({ page }) => {
    await page.waitForSelector('[data-testid="story-card"]', { timeout: 10000 })

    // 检查第一张卡片的关键信息
    const firstCard = page.locator('[data-testid="story-card"]').first()

    await expect(firstCard.locator('[data-testid="story-title"]')).toBeVisible()
    await expect(firstCard.locator('[data-testid="story-event"]')).toBeVisible()
    await expect(firstCard.locator('[data-testid="story-status"]')).toBeVisible()
  })

  test('完成状态的故事应该标记为已完成', async ({ page }) => {
    await page.waitForSelector('[data-testid="story-card"]', { timeout: 10000 })

    // 查找已完成的故事
    const completedCards = page.locator('[data-testid="story-card"][data-status="completed"]')
    await expect(completedCards.first()).toBeVisible()

    const status = await completedCards.first().locator('[data-testid="story-status"]').textContent()
    expect(status).toContain('已完成')
  })

  test('进行中的故事应该标记为进行中', async ({ page }) => {
    await page.waitForSelector('[data-testid="story-card"]', { timeout: 10000 })

    const inProgressCards = page.locator('[data-testid="story-card"][data-status="in_progress"]')

    const count = await inProgressCards.count()
    expect(count).toBeGreaterThan(0)
  })
})

test.describe('书架视图切换', () => {

  test.beforeEach(async ({ page }) => {
    await page.goto(`${BASE_URL}/bookshelf`)
    await setupMockData(page)
    await page.reload()
  })

  test('应该支持时光轴视图切换', async ({ page }) => {
    await page.waitForSelector('[data-testid="view-toggle"]', { timeout: 10000 })

    // 点击时光轴视图按钮
    await page.locator('[data-testid="view-toggle-timeline"]').click()

    // 验证时光轴视图显示
    await expect(page.locator('[data-testid="timeline-view"]')).toBeVisible({ timeout: 5000 })
  })

  test('应该支持山河图视图切换', async ({ page }) => {
    await page.waitForSelector('[data-testid="view-toggle"]', { timeout: 10000 })

    // 点击山河图视图按钮
    await page.locator('[data-testid="view-toggle-map"]').click()

    // 验证山河图视图显示
    await expect(page.locator('[data-testid="map-view"]')).toBeVisible({ timeout: 5000 })
  })

  test('时光轴视图应该显示时间线', async ({ page }) => {
    await page.locator('[data-testid="view-toggle-timeline"]').click()
    await page.waitForSelector('[data-testid="timeline-view"]', { timeout: 5000 })

    // 检查时间线节点
    const timelineNodes = page.locator('[data-testid="timeline-node"]')
    await expect(timelineNodes.first()).toBeVisible()
  })

  test('山河图视图应该显示地图标记', async ({ page }) => {
    await page.locator('[data-testid="view-toggle-map"]').click()
    await page.waitForSelector('[data-testid="map-view"]', { timeout: 5000 })

    // 检查地图标记点
    const mapMarkers = page.locator('[data-testid="map-marker"]')
    await expect(mapMarkers.first()).toBeVisible()
  })
})

test.describe('书架筛选功能', () => {

  test.beforeEach(async ({ page }) => {
    await page.goto(`${BASE_URL}/bookshelf`)
    await setupMockData(page)
    await page.reload()
    await page.waitForSelector('[data-testid="story-card"]', { timeout: 10000 })
  })

  test('应该支持按状态筛选', async ({ page }) => {
    // 点击"已完成"筛选
    await page.locator('[data-testid="filter-status-completed"]').click()
    await page.waitForTimeout(500)

    // 验证只显示已完成的故事
    const visibleCards = page.locator('[data-testid="story-card"]:visible')
    const count = await visibleCards.count()

    for (let i = 0; i < count; i++) {
      const status = await visibleCards.nth(i).getAttribute('data-status')
      expect(status).toBe('completed')
    }
  })

  test('应该支持按关键词筛选', async ({ page }) => {
    // 点击关键词筛选
    await page.locator('[data-testid="filter-keyword"]').click()
    await page.waitForTimeout(500)

    // 选择一个关键词
    await page.locator('[data-testid="keyword-option-旧船票"]').click()
    await page.waitForTimeout(500)

    // 验证筛选结果
    const visibleCards = page.locator('[data-testid="story-card"]:visible')
    const count = await visibleCards.count()

    expect(count).toBeGreaterThan(0)
  })

  test('应该支持清除筛选', async ({ page }) => {
    // 先应用筛选
    await page.locator('[data-testid="filter-status-completed"]').click()
    await page.waitForTimeout(500)

    // 清除筛选
    await page.locator('[data-testid="filter-clear"]').click()
    await page.waitForTimeout(500)

    // 验证显示所有故事
    const allCards = page.locator('[data-testid="story-card"]')
    const totalCount = await allCards.count()

    expect(totalCount).toBe(3) // 总共3个故事
  })
})

test.describe('书架排序功能', () => {

  test.beforeEach(async ({ page }) => {
    await page.goto(`${BASE_URL}/bookshelf`)
    await setupMockData(page)
    await page.reload()
    await page.waitForSelector('[data-testid="story-card"]', { timeout: 10000 })
  })

  test('应该支持按时间排序', async ({ page }) => {
    await page.locator('[data-testid="sort-select"]').click()
    await page.locator('[data-testid="sort-option-date"]').click()
    await page.waitForTimeout(500)

    // 验证排序（最新在前）
    const dates = page.locator('[data-testid="story-date"]')
    const firstDate = await dates.first().textContent()

    expect(firstDate).toBeTruthy()
  })

  test('应该支持按偏离度排序', async ({ page }) => {
    await page.locator('[data-testid="sort-select"]').click()
    await page.locator('[data-testid="sort-option-deviation"]').click()
    await page.waitForTimeout(500)

    // 验证偏离度指示器显示
    await expect(page.locator('[data-testid="deviation-badge"]').first()).toBeVisible()
  })

  test('应该支持按字数排序', async ({ page }) => {
    await page.locator('[data-testid="sort-select"]').click()
    await page.locator('[data-testid="sort-option-words"]').click()
    await page.waitForTimeout(500)

    // 验证字数显示
    await expect(page.locator('[data-testid="word-count-badge"]').first()).toBeVisible()
  })
})

test.describe('故事卡操作', () => {

  test.beforeEach(async ({ page }) => {
    await page.goto(`${BASE_URL}/bookshelf`)
    await setupMockData(page)
    await page.reload()
    await page.waitForSelector('[data-testid="story-card"]', { timeout: 10000 })
  })

  test('点击故事卡应该打开详情', async ({ page }) => {
    // 点击第一张卡片
    await page.locator('[data-testid="story-card"]').first().click()
    await page.waitForTimeout(1000)

    // 验证详情面板打开
    await expect(page.locator('[data-testid="story-detail-panel"]')).toBeVisible({ timeout: 5000 })
  })

  test('已完成故事应该可以分享', async ({ page }) => {
    // 点击已完成的故事卡片
    await page.locator('[data-testid="story-card"][data-status="completed"]').first().click()
    await page.waitForTimeout(1000)

    // 验证分享按钮可见
    await expect(page.locator('[data-testid="share-button"]')).toBeVisible()
  })

  test('进行中故事应该可以继续阅读', async ({ page }) => {
    // 点击进行中的故事卡片
    await page.locator('[data-testid="story-card"][data-status="in_progress"]').first().click()
    await page.waitForTimeout(1000)

    // 验证继续阅读按钮可见
    await expect(page.locator('[data-testid="continue-reading-button"]')).toBeVisible()
  })

  test('应该可以删除故事', async ({ page }) => {
    // 点击第一张卡片的菜单
    await page.locator('[data-testid="story-card"]').first().locator('[data-testid="card-menu-button"]').click()
    await page.waitForTimeout(500)

    // 点击删除选项
    await page.locator('[data-testid="delete-option"]').click()
    await page.waitForTimeout(500)

    // 确认删除
    await page.locator('[data-testid="confirm-delete-button"]').click()
    await page.waitForTimeout(1000)

    // 验证故事被删除（列表数量减1）
    const cards = page.locator('[data-testid="story-card"]')
    await expect(cards).toHaveCount(2) // 原来3个，删除1个后剩2个
  })
})

test.describe('书架空状态', () => {

  test('空书架应该显示引导提示', async ({ page }) => {
    await page.goto(`${BASE_URL}/bookshelf`)
    // 清空数据（在已加载页面上操作localStorage）
    await page.evaluate(() => {
      localStorage.setItem('bookshelf_stories', JSON.stringify([]))
    })
    await page.reload()
    await page.waitForTimeout(1000)

    // 验证空状态提示
    await expect(page.locator('[data-testid="empty-bookshelf-message"]')).toBeVisible()
    await expect(page.locator('[data-testid="empty-bookshelf-message"]')).toContainText('暂无故事')
  })

  test('空书架应该显示开始按钮', async ({ page }) => {
    await page.goto(`${BASE_URL}/bookshelf`)
    await page.evaluate(() => {
      localStorage.setItem('bookshelf_stories', JSON.stringify([]))
    })
    await page.reload()
    await page.waitForTimeout(1000)

    await expect(page.locator('[data-testid="start-new-story-button"]')).toBeVisible()
  })
})
