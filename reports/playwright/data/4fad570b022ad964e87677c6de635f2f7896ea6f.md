# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: story.spec.ts >> 手势选择交互 >> 手势模式下按钮模式切换回去
- Location: tests/e2e/story.spec.ts:360:3

# Error details

```
Error: page.goto: net::ERR_CONNECTION_REFUSED at http://localhost:5175/story/1
Call log:
  - navigating to "http://localhost:5175/story/1", waiting until "load"

```

# Test source

```ts
  231 |       route.fulfill({
  232 |         status: 200,
  233 |         contentType: 'application/json',
  234 |         body: JSON.stringify(highResonanceChapter),
  235 |       })
  236 |     })
  237 |     await page.reload()
  238 |     await page.waitForSelector('[data-testid="keyword-resonance-bar"]', { timeout: 10000 })
  239 |     const resonanceChip = page.locator('[data-testid="keyword-resonance-bar"]').first()
  240 |     await expect(resonanceChip).toHaveClass(/resonance-full/)
  241 |     const achieved = page.locator('.resonance-achieved')
  242 |     await expect(achieved).toBeVisible({ timeout: 5000 })
  243 |   })
  244 | })
  245 | 
  246 | test.describe('故事章节流转', () => {
  247 | 
  248 |   test.beforeEach(async ({ page }) => {
  249 |     setupStoryRouteMocks(page, '1')
  250 |     await page.goto(`${BASE_URL}/story/1`)
  251 | 
  252 |     // Mock finishStory — 第三章完成后调用
  253 |     await page.route(
  254 |       new RegExp('/api/story/1/finish'),
  255 |       () => {
  256 |         // no-op
  257 |       }
  258 |     )
  259 |     // Mock manuscript API
  260 |     await page.route(
  261 |       new RegExp('/api/story/1/manuscript'),
  262 |       () => {}
  263 |     )
  264 |   })
  265 | 
  266 |   test('完整故事流程：3章全部完成', async ({ page }) => {
  267 |     // 第一章
  268 |     await page.waitForSelector('[data-testid="chapter-text"]', { timeout: 10000 })
  269 |     await page.waitForTimeout(500) // 等待打字机开始
  270 |     await page.waitForSelector('[data-testid="option-item"]', { timeout: 10000 })
  271 |     await page.locator('[data-testid="option-item"]').first().click()
  272 |     await page.waitForTimeout(1500)
  273 |     expect(await page.locator('[data-testid="current-chapter"]').textContent()).toContain('2')
  274 | 
  275 |     // 第二章
  276 |     await page.waitForSelector('[data-testid="option-item"]', { timeout: 10000 })
  277 |     await page.locator('[data-testid="option-item"]').first().click()
  278 |     await page.waitForTimeout(1500)
  279 |     expect(await page.locator('[data-testid="current-chapter"]').textContent()).toContain('3')
  280 | 
  281 |     // 第三章 — 应该显示"完结此篇"按钮
  282 |     await page.waitForSelector('[data-testid="option-item"]', { timeout: 10000 })
  283 |     await page.waitForTimeout(500)
  284 |     // 选项列表应该为空或只有一个终章按钮，验证完结按钮出现
  285 |     const finishBtn = page.locator('.finish-btn')
  286 |     await expect(finishBtn).toBeVisible({ timeout: 5000 })
  287 | 
  288 |     // 点击完结
  289 |     await finishBtn.click()
  290 |     await page.waitForTimeout(2000)
  291 |     // 应该跳转到手稿页
  292 |     expect(page.url()).toContain('/manuscript/1')
  293 |   })
  294 | 
  295 |   test('章节进度指示器应该正确更新', async ({ page }) => {
  296 |     await page.waitForSelector('[data-testid="chapter-progress"]', { timeout: 10000 })
  297 |     const progressIndicator = page.locator('[data-testid="chapter-progress-dot"]')
  298 |     await expect(progressIndicator.first()).toHaveClass(/active/)
  299 |     await page.locator('[data-testid="option-item"]').first().click()
  300 |     await page.waitForTimeout(1500)
  301 |     await expect(progressIndicator.nth(1)).toHaveClass(/active/)
  302 |   })
  303 | })
  304 | 
  305 | test.describe('卷轴阅读 - 历史偏离度', () => {
  306 | 
  307 |   test.beforeEach(async ({ page }) => {
  308 |     setupStoryRouteMocks(page, '1')
  309 |     await page.goto(`${BASE_URL}/story/1`)
  310 |   })
  311 | 
  312 |   test('历史偏离度指示器应该可见', async ({ page }) => {
  313 |     await page.waitForSelector('[data-testid="deviation-indicator"]', { timeout: 10000 })
  314 |     await expect(page.locator('[data-testid="deviation-indicator"]')).toBeVisible()
  315 |   })
  316 | 
  317 |   test('选择后偏离度应该更新', async ({ page }) => {
  318 |     await page.waitForSelector('[data-testid="deviation-indicator"]', { timeout: 10000 })
  319 |     const initialDeviation = await page.locator('[data-testid="deviation-value"]').textContent()
  320 |     await page.locator('[data-testid="option-item"]').first().click()
  321 |     await page.waitForTimeout(1500)
  322 |     const newDeviation = await page.locator('[data-testid="deviation-value"]').textContent()
  323 |     expect(newDeviation).toBeTruthy()
  324 |   })
  325 | })
  326 | 
  327 | test.describe('手势选择交互', () => {
  328 | 
  329 |   test.beforeEach(async ({ page }) => {
  330 |     setupStoryRouteMocks(page, '1')
> 331 |     await page.goto(`${BASE_URL}/story/1`)
      |                ^ Error: page.goto: net::ERR_CONNECTION_REFUSED at http://localhost:5175/story/1
  332 |   })
  333 | 
  334 |   test('手势模式切换按钮应该可见', async ({ page }) => {
  335 |     await page.waitForSelector('[data-testid="chapter-options"]', { timeout: 10000 })
  336 |     const toggleBtn = page.locator('[data-testid="gesture-mode-toggle"]')
  337 |     await expect(toggleBtn).toBeVisible()
  338 |   })
  339 | 
  340 |   test('点击切换按钮进入手势模式，显示三个手势引导图标', async ({ page }) => {
  341 |     await page.waitForSelector('[data-testid="chapter-options"]', { timeout: 10000 })
  342 |     const toggleBtn = page.locator('[data-testid="gesture-mode-toggle"]')
  343 |     await toggleBtn.click()
  344 |     const gesturePanel = page.locator('[data-testid="gesture-panel"]')
  345 |     await expect(gesturePanel).toBeVisible()
  346 |     // 三个手势图标都应可见
  347 |     await expect(page.locator('[data-testid="gesture-swipe-left"]')).toBeVisible()
  348 |     await expect(page.locator('[data-testid="gesture-swipe-right"]')).toBeVisible()
  349 |     await expect(page.locator('[data-testid="gesture-circle"]')).toBeVisible()
  350 |   })
  351 | 
  352 |   test('手势模式切换按钮 active 状态应该正确', async ({ page }) => {
  353 |     await page.waitForSelector('[data-testid="gesture-mode-toggle"]', { timeout: 10000 })
  354 |     const toggleBtn = page.locator('[data-testid="gesture-mode-toggle"]')
  355 |     await expect(toggleBtn).not.toHaveClass(/active/)
  356 |     await toggleBtn.click()
  357 |     await expect(toggleBtn).toHaveClass(/active/)
  358 |   })
  359 | 
  360 |   test('手势模式下按钮模式切换回去', async ({ page }) => {
  361 |     await page.waitForSelector('[data-testid="gesture-mode-toggle"]', { timeout: 10000 })
  362 |     const toggleBtn = page.locator('[data-testid="gesture-mode-toggle"]')
  363 |     await toggleBtn.click() // 进入手势模式
  364 |     await expect(page.locator('[data-testid="gesture-panel"]')).toBeVisible()
  365 |     await toggleBtn.click() // 退回按钮模式
  366 |     await expect(page.locator('[data-testid="gesture-panel"]')).not.toBeVisible()
  367 |     await expect(page.locator('[data-testid="option-item"]').first()).toBeVisible()
  368 |   })
  369 | 
  370 |   test('手势模式下向左滑触发涟漪动画', async ({ page }) => {
  371 |     await page.waitForSelector('[data-testid="gesture-mode-toggle"]', { timeout: 10000 })
  372 |     await page.locator('[data-testid="gesture-mode-toggle"]').click()
  373 |     await page.waitForSelector('[data-testid="gesture-panel"]', { timeout: 5000 })
  374 |     // 模拟向左滑动
  375 |     await page.touchscreen.tap(200, 400)
  376 |     // 触发手势需要实际touch事件，这里简化为通过断言确认手势面板存在
  377 |     await expect(page.locator('[data-testid="gesture-panel"]')).toBeVisible()
  378 |   })
  379 | })
  380 | 
```