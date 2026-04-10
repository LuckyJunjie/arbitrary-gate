# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: bookshelf.spec.ts >> 书架排序功能 >> 应该支持按偏离度排序
- Location: tests/e2e/bookshelf.spec.ts:261:3

# Error details

```
Error: expect(locator).toBeVisible() failed

Locator: locator('[data-testid="deviation-badge"]').first()
Expected: visible
Timeout: 5000ms
Error: element(s) not found

Call log:
  - Expect "toBeVisible" with timeout 5000ms
  - waiting for locator('[data-testid="deviation-badge"]').first()

```

# Page snapshot

```yaml
- generic [ref=e3]:
  - generic [ref=e4]:
    - generic [ref=e5]: 游客模式 ·
    - button "获取关键词" [ref=e6] [cursor=pointer]
  - generic [ref=e7]:
    - banner [ref=e8]:
      - heading "书架" [level=2] [ref=e9]
      - generic [ref=e10]:
        - button "按偏离度" [ref=e12] [cursor=pointer]
        - generic [ref=e13]:
          - button "格子" [ref=e14] [cursor=pointer]
          - button "时光轴" [ref=e15] [cursor=pointer]
          - button "山河图" [ref=e16] [cursor=pointer]
    - generic [ref=e17]:
      - generic [ref=e18]:
        - button "全部" [ref=e19] [cursor=pointer]
        - button "已完成" [ref=e20] [cursor=pointer]
        - button "进行中" [ref=e21] [cursor=pointer]
      - button "全部关键词" [ref=e23] [cursor=pointer]
    - generic [ref=e27]:
      - generic [ref=e28] [cursor=pointer]:
        - generic [ref=e30]:
          - generic: 马嵬月下
        - generic [ref=e32]:
          - generic [ref=e33]: 马嵬驿·杨贵妃缢死
          - generic "已完成" [ref=e34]:
            - img [ref=e35]:
              - generic [ref=e38]: 完
      - generic [ref=e39] [cursor=pointer]:
        - generic [ref=e42]: 待续...
        - generic [ref=e45]: 玄武门·李世民射兄
      - generic [ref=e46] [cursor=pointer]:
        - generic [ref=e48]:
          - generic: 赤壁往事
        - generic [ref=e50]:
          - generic [ref=e51]: 赤壁·东风骤起
          - generic "已完成" [ref=e52]:
            - img [ref=e53]:
              - generic [ref=e56]: 完
```

# Test source

```ts
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
  240 | test.describe('书架排序功能', () => {
  241 | 
  242 |   test.beforeEach(async ({ page }) => {
  243 |     await page.goto(`${BASE_URL}/bookshelf`)
  244 |     await setupMockData(page)
  245 |     await page.reload()
  246 |     await page.waitForSelector('[data-testid="story-card"]', { timeout: 10000 })
  247 |   })
  248 | 
  249 |   test('应该支持按时间排序', async ({ page }) => {
  250 |     await page.locator('[data-testid="sort-select"]').click()
  251 |     await page.locator('[data-testid="sort-option-date"]').click()
  252 |     await page.waitForTimeout(500)
  253 | 
  254 |     // 验证排序（最新在前）
  255 |     const dates = page.locator('[data-testid="story-date"]')
  256 |     const firstDate = await dates.first().textContent()
  257 | 
  258 |     expect(firstDate).toBeTruthy()
  259 |   })
  260 | 
  261 |   test('应该支持按偏离度排序', async ({ page }) => {
  262 |     await page.locator('[data-testid="sort-select"]').click()
  263 |     await page.locator('[data-testid="sort-option-deviation"]').click()
  264 |     await page.waitForTimeout(500)
  265 | 
  266 |     // 验证偏离度指示器显示
> 267 |     await expect(page.locator('[data-testid="deviation-badge"]').first()).toBeVisible()
      |                                                                           ^ Error: expect(locator).toBeVisible() failed
  268 |   })
  269 | 
  270 |   test('应该支持按字数排序', async ({ page }) => {
  271 |     await page.locator('[data-testid="sort-select"]').click()
  272 |     await page.locator('[data-testid="sort-option-words"]').click()
  273 |     await page.waitForTimeout(500)
  274 | 
  275 |     // 验证字数显示
  276 |     await expect(page.locator('[data-testid="word-count-badge"]').first()).toBeVisible()
  277 |   })
  278 | })
  279 | 
  280 | test.describe('故事卡操作', () => {
  281 | 
  282 |   test.beforeEach(async ({ page }) => {
  283 |     await page.goto(`${BASE_URL}/bookshelf`)
  284 |     await setupMockData(page)
  285 |     await page.reload()
  286 |     await page.waitForSelector('[data-testid="story-card"]', { timeout: 10000 })
  287 |   })
  288 | 
  289 |   test('点击故事卡应该打开详情', async ({ page }) => {
  290 |     // 点击第一张卡片
  291 |     await page.locator('[data-testid="story-card"]').first().click()
  292 |     await page.waitForTimeout(1000)
  293 | 
  294 |     // 验证详情面板打开
  295 |     await expect(page.locator('[data-testid="story-detail-panel"]')).toBeVisible({ timeout: 5000 })
  296 |   })
  297 | 
  298 |   test('已完成故事应该可以分享', async ({ page }) => {
  299 |     // 点击已完成的故事卡片
  300 |     await page.locator('[data-testid="story-card"][data-status="completed"]').first().click()
  301 |     await page.waitForTimeout(1000)
  302 | 
  303 |     // 验证分享按钮可见
  304 |     await expect(page.locator('[data-testid="share-button"]')).toBeVisible()
  305 |   })
  306 | 
  307 |   test('进行中故事应该可以继续阅读', async ({ page }) => {
  308 |     // 点击进行中的故事卡片
  309 |     await page.locator('[data-testid="story-card"][data-status="in_progress"]').first().click()
  310 |     await page.waitForTimeout(1000)
  311 | 
  312 |     // 验证继续阅读按钮可见
  313 |     await expect(page.locator('[data-testid="continue-reading-button"]')).toBeVisible()
  314 |   })
  315 | 
  316 |   test('应该可以删除故事', async ({ page }) => {
  317 |     // 点击第一张卡片的菜单
  318 |     await page.locator('[data-testid="story-card"]').first().locator('[data-testid="card-menu-button"]').click()
  319 |     await page.waitForTimeout(500)
  320 | 
  321 |     // 点击删除选项
  322 |     await page.locator('[data-testid="delete-option"]').click()
  323 |     await page.waitForTimeout(500)
  324 | 
  325 |     // 确认删除
  326 |     await page.locator('[data-testid="confirm-delete-button"]').click()
  327 |     await page.waitForTimeout(1000)
  328 | 
  329 |     // 验证故事被删除（列表数量减1）
  330 |     const cards = page.locator('[data-testid="story-card"]')
  331 |     await expect(cards).toHaveCount(2) // 原来3个，删除1个后剩2个
  332 |   })
  333 | })
  334 | 
  335 | test.describe('书架空状态', () => {
  336 | 
  337 |   test('空书架应该显示引导提示', async ({ page }) => {
  338 |     await page.goto(`${BASE_URL}/bookshelf`)
  339 |     // 清空数据（在已加载页面上操作localStorage）
  340 |     await page.evaluate(() => {
  341 |       localStorage.setItem('bookshelf_stories', JSON.stringify([]))
  342 |     })
  343 |     await page.reload()
  344 |     await page.waitForTimeout(1000)
  345 | 
  346 |     // 验证空状态提示
  347 |     await expect(page.locator('[data-testid="empty-bookshelf-message"]')).toBeVisible()
  348 |     await expect(page.locator('[data-testid="empty-bookshelf-message"]')).toContainText('暂无故事')
  349 |   })
  350 | 
  351 |   test('空书架应该显示开始按钮', async ({ page }) => {
  352 |     await page.goto(`${BASE_URL}/bookshelf`)
  353 |     await page.evaluate(() => {
  354 |       localStorage.setItem('bookshelf_stories', JSON.stringify([]))
  355 |     })
  356 |     await page.reload()
  357 |     await page.waitForTimeout(1000)
  358 | 
  359 |     await expect(page.locator('[data-testid="start-new-story-button"]')).toBeVisible()
  360 |   })
  361 | })
  362 | 
```