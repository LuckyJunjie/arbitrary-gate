# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: story.spec.ts >> 关键词共鸣可视化 >> 共鸣进度应该可视化
- Location: tests/e2e/story.spec.ts:211:3

# Error details

```
Error: page.goto: net::ERR_CONNECTION_REFUSED at http://localhost:5175/story/1
Call log:
  - navigating to "http://localhost:5175/story/1", waiting until "load"

```

# Test source

```ts
  101 |       const nextChapterNo = currentChapter + 1
  102 |       const nextChapter = MOCK_CHAPTERS[nextChapterNo] || MOCK_CHAPTERS[1]
  103 |       route.fulfill({
  104 |         status: 200,
  105 |         contentType: 'application/json',
  106 |         body: JSON.stringify({ chapter: nextChapter, deviation: 5 }),
  107 |       })
  108 |     }
  109 |   )
  110 | 
  111 |   // Mock POST /api/story/:id/finish
  112 |   page.route(
  113 |     new RegExp(`/api/story/${storyId}/finish`),
  114 |     () => {
  115 |       // No-op — will be skipped in tests that don't reach this
  116 |     }
  117 |   )
  118 | 
  119 |   // Mock GET /api/story/:id/manuscript
  120 |   page.route(
  121 |     new RegExp(`/api/story/${storyId}/manuscript`),
  122 |     () => {}
  123 |   )
  124 | }
  125 | 
  126 | // ==================== E2E 测试用例 ====================
  127 | 
  128 | test.describe('卷轴阅读模块', () => {
  129 | 
  130 |   test.beforeEach(async ({ page }) => {
  131 |     // Mock 必须先于页面导航注册，才能拦截初始 API 调用
  132 |     setupStoryRouteMocks(page, '1')
  133 |     await page.goto(`${BASE_URL}/story/1`)
  134 |   })
  135 | 
  136 |   test('卷轴页面应该正确加载', async ({ page }) => {
  137 |     await expect(page.locator('[data-testid="scroll-container"]')).toBeVisible({ timeout: 10000 })
  138 |     await expect(page.locator('[data-testid="scroll-title"]')).toBeVisible()
  139 |   })
  140 | 
  141 |   test('卷轴应该使用竖向排版', async ({ page }) => {
  142 |     await expect(page.locator('[data-testid="scroll-content"]')).toBeVisible({ timeout: 10000 })
  143 |     const scrollContent = page.locator('[data-testid="scroll-content"]')
  144 |     const writingMode = await scrollContent.evaluate((el: Element) => {
  145 |       return window.getComputedStyle(el).writingMode
  146 |     })
  147 |     expect(writingMode).toMatch(/vertical-rl|vertical-lr/)
  148 |   })
  149 | 
  150 |   test('第一章内容应该正确显示', async ({ page }) => {
  151 |     await page.waitForSelector('[data-testid="chapter-text"]', { timeout: 10000 })
  152 |     const chapterText = await page.locator('[data-testid="chapter-text"]').textContent()
  153 |     expect(chapterText).toContain('第一章')
  154 |     expect(chapterText).toContain('赤壁')
  155 |   })
  156 | 
  157 |   test('选项应该正确展示且可点击', async ({ page }) => {
  158 |     await page.waitForSelector('[data-testid="chapter-options"]', { timeout: 10000 })
  159 |     const options = page.locator('[data-testid="option-item"]')
  160 |     await expect(options).toHaveCount(3)
  161 |     const firstOptionText = await options.first().textContent()
  162 |     expect(firstOptionText).toBeTruthy()
  163 |   })
  164 | })
  165 | 
  166 | test.describe('卷轴手势交互', () => {
  167 | 
  168 |   test.beforeEach(async ({ page }) => {
  169 |     setupStoryRouteMocks(page, '1')
  170 |     await page.goto(`${BASE_URL}/story/1`)
  171 |   })
  172 | 
  173 |   test('向上滑动应该显示下一章节选项', async ({ page }) => {
  174 |     await page.waitForSelector('[data-testid="chapter-options"]', { timeout: 10000 })
  175 |     const initialChapter = await page.locator('[data-testid="current-chapter"]').textContent()
  176 |     const scrollArea = page.locator('[data-testid="scroll-content"]')
  177 |     const box = await scrollArea.boundingBox()
  178 |     if (box) {
  179 |       await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2)
  180 |       await page.mouse.wheel(0, -200)
  181 |     }
  182 |     await page.waitForTimeout(500)
  183 |     await page.locator('[data-testid="option-item"]').first().click()
  184 |     await page.waitForTimeout(1500)
  185 |     const newChapter = await page.locator('[data-testid="current-chapter"]').textContent()
  186 |     expect(newChapter).not.toBe(initialChapter)
  187 |   })
  188 | 
  189 |   test('涟漪动画应该在选择后触发', async ({ page }) => {
  190 |     await page.waitForSelector('[data-testid="chapter-options"]', { timeout: 10000 })
  191 |     await page.locator('[data-testid="option-item"]').first().click()
  192 |     const ripple = page.locator('[data-testid="choice-ripple"]')
  193 |     await expect(ripple).toBeVisible({ timeout: 3000 })
  194 |   })
  195 | })
  196 | 
  197 | test.describe('关键词共鸣可视化', () => {
  198 | 
  199 |   test.beforeEach(async ({ page }) => {
  200 |     setupStoryRouteMocks(page, '1')
> 201 |     await page.goto(`${BASE_URL}/story/1`)
      |                ^ Error: page.goto: net::ERR_CONNECTION_REFUSED at http://localhost:5175/story/1
  202 |   })
  203 | 
  204 |   test('关键词共鸣条应该正确显示', async ({ page }) => {
  205 |     await page.waitForSelector('[data-testid="keyword-resonance-bar"]', { timeout: 10000 })
  206 |     const resonanceBars = page.locator('[data-testid="keyword-resonance-bar"]')
  207 |     const count = await resonanceBars.count()
  208 |     expect(count).toBe(3)
  209 |   })
  210 | 
  211 |   test('共鸣进度应该可视化', async ({ page }) => {
  212 |     await page.waitForSelector('[data-testid="keyword-resonance-bar"]', { timeout: 10000 })
  213 |     const firstBar = page.locator('[data-testid="keyword-resonance-fill"]').first()
  214 |     await expect(firstBar).toBeVisible()
  215 |     const fillWidth = await firstBar.evaluate((el: Element) => {
  216 |       return (el as HTMLElement).style.width || '0%'
  217 |     })
  218 |     expect(fillWidth).toMatch(/^\d+%$/)
  219 |   })
  220 | 
  221 |   test.skip('共鸣满时应该触发显灵效果', async ({ page }) => {
  222 |     // 修改 mock 数据使共鸣满（>=7），验证显灵效果
  223 |     const highResonanceChapter = {
  224 |       chapterNo: 1,
  225 |       sceneText: '共鸣测试场景...',
  226 |       options: [{ id: 1, text: '测试选项' }],
  227 |       keywordResonance: { 1: 7, 2: 7, 3: 7 }, // 共鸣满
  228 |     }
  229 |     page.removeAllRoutes?.() // 清除现有路由
  230 |     page.route(new RegExp('/api/story/1/chapter/\\d+'), route => {
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
```