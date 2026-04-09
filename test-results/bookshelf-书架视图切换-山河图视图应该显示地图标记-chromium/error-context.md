# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: bookshelf.spec.ts >> 书架视图切换 >> 山河图视图应该显示地图标记
- Location: tests/e2e/bookshelf.spec.ts:173:3

# Error details

```
Error: page.goto: net::ERR_CONNECTION_REFUSED at http://localhost:5175/bookshelf
Call log:
  - navigating to "http://localhost:5175/bookshelf", waiting until "load"

```

# Test source

```ts
  39  |       {
  40  |         id: 2,
  41  |         storyNo: 'SN002',
  42  |         title: '马嵬月下',
  43  |         eventName: '马嵬驿·杨贵妃缢死',
  44  |         status: 'completed',
  45  |         historyDeviation: 72,
  46  |         wordCount: 5200,
  47  |         createdAt: '2024-01-16T14:00:00Z',
  48  |         finishedAt: '2024-01-16T16:00:00Z',
  49  |         keywords: [
  50  |           { id: 3, name: '意难平', rarity: 4 },
  51  |           { id: 4, name: '青石板', rarity: 1 },
  52  |         ],
  53  |       },
  54  |       {
  55  |         id: 3,
  56  |         storyNo: 'SN003',
  57  |         title: '待续...',
  58  |         eventName: '玄武门·李世民射兄',
  59  |         status: 'in_progress',
  60  |         historyDeviation: 50,
  61  |         wordCount: 0,
  62  |         createdAt: '2024-01-17T09:00:00Z',
  63  |         finishedAt: null,
  64  |         keywords: [
  65  |           { id: 5, name: '铜锁芯', rarity: 2 },
  66  |         ],
  67  |         currentChapter: 2,
  68  |         totalChapters: 5,
  69  |       },
  70  |     ]
  71  | 
  72  |     localStorage.setItem('bookshelf_stories', JSON.stringify(mockStories))
  73  |     localStorage.setItem('currentUserId', '1')
  74  |   })
  75  | }
  76  | 
  77  | // ==================== E2E 测试用例 ====================
  78  | 
  79  | test.describe('书架管理模块', () => {
  80  | 
  81  |   test.beforeEach(async ({ page }) => {
  82  |     await page.goto(`${BASE_URL}/bookshelf`)
  83  |     await setupMockData(page)
  84  |     await page.reload()
  85  |   })
  86  | 
  87  |   test('书架页面应该正确加载', async ({ page }) => {
  88  |     // 等待书架容器加载
  89  |     await expect(page.locator('[data-testid="bookshelf-container"]')).toBeVisible({ timeout: 10000 })
  90  | 
  91  |     // 检查书架标题
  92  |     await expect(page.locator('[data-testid="bookshelf-title"]')).toContainText('书架')
  93  |   })
  94  | 
  95  |   test('应该显示故事卡片列表', async ({ page }) => {
  96  |     await page.waitForSelector('[data-testid="story-card"]', { timeout: 10000 })
  97  | 
  98  |     const storyCards = page.locator('[data-testid="story-card"]')
  99  |     const count = await storyCards.count()
  100 | 
  101 |     expect(count).toBeGreaterThan(0)
  102 |   })
  103 | 
  104 |   test('故事卡片应该显示关键信息', async ({ page }) => {
  105 |     await page.waitForSelector('[data-testid="story-card"]', { timeout: 10000 })
  106 | 
  107 |     // 检查第一张卡片的关键信息
  108 |     const firstCard = page.locator('[data-testid="story-card"]').first()
  109 | 
  110 |     await expect(firstCard.locator('[data-testid="story-title"]')).toBeVisible()
  111 |     await expect(firstCard.locator('[data-testid="story-event"]')).toBeVisible()
  112 |     await expect(firstCard.locator('[data-testid="story-status"]')).toBeVisible()
  113 |   })
  114 | 
  115 |   test('完成状态的故事应该标记为已完成', async ({ page }) => {
  116 |     await page.waitForSelector('[data-testid="story-card"]', { timeout: 10000 })
  117 | 
  118 |     // 查找已完成的故事
  119 |     const completedCards = page.locator('[data-testid="story-card"][data-status="completed"]')
  120 |     await expect(completedCards.first()).toBeVisible()
  121 | 
  122 |     const status = await completedCards.first().locator('[data-testid="story-status"]').textContent()
  123 |     expect(status).toContain('已完成')
  124 |   })
  125 | 
  126 |   test('进行中的故事应该标记为进行中', async ({ page }) => {
  127 |     await page.waitForSelector('[data-testid="story-card"]', { timeout: 10000 })
  128 | 
  129 |     const inProgressCards = page.locator('[data-testid="story-card"][data-status="in_progress"]')
  130 | 
  131 |     const count = await inProgressCards.count()
  132 |     expect(count).toBeGreaterThan(0)
  133 |   })
  134 | })
  135 | 
  136 | test.describe('书架视图切换', () => {
  137 | 
  138 |   test.beforeEach(async ({ page }) => {
> 139 |     await page.goto(`${BASE_URL}/bookshelf`)
      |                ^ Error: page.goto: net::ERR_CONNECTION_REFUSED at http://localhost:5175/bookshelf
  140 |     await setupMockData(page)
  141 |     await page.reload()
  142 |   })
  143 | 
  144 |   test('应该支持时光轴视图切换', async ({ page }) => {
  145 |     await page.waitForSelector('[data-testid="view-toggle"]', { timeout: 10000 })
  146 | 
  147 |     // 点击时光轴视图按钮
  148 |     await page.locator('[data-testid="view-toggle-timeline"]').click()
  149 | 
  150 |     // 验证时光轴视图显示
  151 |     await expect(page.locator('[data-testid="timeline-view"]')).toBeVisible({ timeout: 5000 })
  152 |   })
  153 | 
  154 |   test('应该支持山河图视图切换', async ({ page }) => {
  155 |     await page.waitForSelector('[data-testid="view-toggle"]', { timeout: 10000 })
  156 | 
  157 |     // 点击山河图视图按钮
  158 |     await page.locator('[data-testid="view-toggle-map"]').click()
  159 | 
  160 |     // 验证山河图视图显示
  161 |     await expect(page.locator('[data-testid="map-view"]')).toBeVisible({ timeout: 5000 })
  162 |   })
  163 | 
  164 |   test('时光轴视图应该显示时间线', async ({ page }) => {
  165 |     await page.locator('[data-testid="view-toggle-timeline"]').click()
  166 |     await page.waitForSelector('[data-testid="timeline-view"]', { timeout: 5000 })
  167 | 
  168 |     // 检查时间线节点
  169 |     const timelineNodes = page.locator('[data-testid="timeline-node"]')
  170 |     await expect(timelineNodes.first()).toBeVisible()
  171 |   })
  172 | 
  173 |   test('山河图视图应该显示地图标记', async ({ page }) => {
  174 |     await page.locator('[data-testid="view-toggle-map"]').click()
  175 |     await page.waitForSelector('[data-testid="map-view"]', { timeout: 5000 })
  176 | 
  177 |     // 检查地图标记点
  178 |     const mapMarkers = page.locator('[data-testid="map-marker"]')
  179 |     await expect(mapMarkers.first()).toBeVisible()
  180 |   })
  181 | })
  182 | 
  183 | test.describe('书架筛选功能', () => {
  184 | 
  185 |   test.beforeEach(async ({ page }) => {
  186 |     await page.goto(`${BASE_URL}/bookshelf`)
  187 |     await setupMockData(page)
  188 |     await page.reload()
  189 |     await page.waitForSelector('[data-testid="story-card"]', { timeout: 10000 })
  190 |   })
  191 | 
  192 |   test('应该支持按状态筛选', async ({ page }) => {
  193 |     // 点击"已完成"筛选
  194 |     await page.locator('[data-testid="filter-status-completed"]').click()
  195 |     await page.waitForTimeout(500)
  196 | 
  197 |     // 验证只显示已完成的故事
  198 |     const visibleCards = page.locator('[data-testid="story-card"]:visible')
  199 |     const count = await visibleCards.count()
  200 | 
  201 |     for (let i = 0; i < count; i++) {
  202 |       const status = await visibleCards.nth(i).getAttribute('data-status')
  203 |       expect(status).toBe('completed')
  204 |     }
  205 |   })
  206 | 
  207 |   test('应该支持按关键词筛选', async ({ page }) => {
  208 |     // 点击关键词筛选
  209 |     await page.locator('[data-testid="filter-keyword"]').click()
  210 |     await page.waitForTimeout(500)
  211 | 
  212 |     // 选择一个关键词
  213 |     await page.locator('[data-testid="keyword-option-旧船票"]').click()
  214 |     await page.waitForTimeout(500)
  215 | 
  216 |     // 验证筛选结果
  217 |     const visibleCards = page.locator('[data-testid="story-card"]:visible')
  218 |     const count = await visibleCards.count()
  219 | 
  220 |     expect(count).toBeGreaterThan(0)
  221 |   })
  222 | 
  223 |   test('应该支持清除筛选', async ({ page }) => {
  224 |     // 先应用筛选
  225 |     await page.locator('[data-testid="filter-status-completed"]').click()
  226 |     await page.waitForTimeout(500)
  227 | 
  228 |     // 清除筛选
  229 |     await page.locator('[data-testid="filter-clear"]').click()
  230 |     await page.waitForTimeout(500)
  231 | 
  232 |     // 验证显示所有故事
  233 |     const allCards = page.locator('[data-testid="story-card"]')
  234 |     const totalCount = await allCards.count()
  235 | 
  236 |     expect(totalCount).toBe(3) // 总共3个故事
  237 |   })
  238 | })
  239 | 
```