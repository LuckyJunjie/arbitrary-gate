# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: story.spec.ts >> 故事章节流转 >> 章节进度指示器应该正确更新
- Location: tests/e2e/story.spec.ts:243:3

# Error details

```
TimeoutError: page.waitForSelector: Timeout 10000ms exceeded.
Call log:
  - waiting for locator('[data-testid="chapter-progress"]') to be visible

```

# Page snapshot

```yaml
- generic [active] [ref=e1]:
  - generic [ref=e3]:
    - generic [ref=e4]:
      - generic [ref=e5]: 游客模式 ·
      - button "获取关键词" [ref=e6] [cursor=pointer]
    - generic [ref=e7]:
      - banner [ref=e8]:
        - button "← 书房" [ref=e9] [cursor=pointer]
        - generic [ref=e11]: 第1章
      - generic [ref=e16]: 第一章场景：赤壁江边，东风骤起...
      - generic [ref=e17]:
        - generic [ref=e18]: "1"
        - generic [ref=e19]: "2"
        - generic [ref=e20]: "3"
      - generic [ref=e21]:
        - generic [ref=e22]:
          - button "‹ 上一章" [disabled] [ref=e23]
          - button "下一章 ›" [ref=e24] [cursor=pointer]
        - generic [ref=e25]:
          - button "下令火攻" [ref=e26] [cursor=pointer]:
            - generic [ref=e28]: 下令火攻
          - button "坚守不出" [ref=e29] [cursor=pointer]:
            - generic [ref=e31]: 坚守不出
          - button "派人求和" [ref=e32] [cursor=pointer]:
            - generic [ref=e34]: 派人求和
  - generic [ref=e36]:
    - heading "入局三问" [level=2] [ref=e37]
    - paragraph [ref=e38]: 请回答三个问题，确定你的处世取向
    - generic [ref=e39]:
      - generic [ref=e40]:
        - generic [ref=e41]: 义：当面对利益冲突时
        - textbox "你的义之选择..." [ref=e42]
      - generic [ref=e43]:
        - generic [ref=e44]: 利：当面对利益冲突时
        - textbox "你的利之选择..." [ref=e45]
      - generic [ref=e46]:
        - generic [ref=e47]: 情：当面对利益冲突时
        - textbox "你的情之选择..." [ref=e48]
    - button "确认入局" [ref=e49] [cursor=pointer]
```

# Test source

```ts
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
  201 |     await page.goto(`${BASE_URL}/story/1`)
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
  222 |     // 此测试需要后端支持关键词共鸣数据，当前跳过
  223 |   })
  224 | })
  225 | 
  226 | test.describe('故事章节流转', () => {
  227 | 
  228 |   test.beforeEach(async ({ page }) => {
  229 |     setupStoryRouteMocks(page, '1')
  230 |     await page.goto(`${BASE_URL}/story/1`)
  231 | 
  232 |     // Mock finishStory — 第三章完成后调用
  233 |     await page.route(
  234 |       new RegExp('/api/story/1/finish'),
  235 |       () => {}
  236 |     )
  237 |   })
  238 | 
  239 |   test.skip('完整故事流程：3章全部完成', async ({ page }) => {
  240 |     // 需要后端完成故事生成流程的支持，暂时跳过
  241 |   })
  242 | 
  243 |   test('章节进度指示器应该正确更新', async ({ page }) => {
> 244 |     await page.waitForSelector('[data-testid="chapter-progress"]', { timeout: 10000 })
      |                ^ TimeoutError: page.waitForSelector: Timeout 10000ms exceeded.
  245 |     const progressIndicator = page.locator('[data-testid="chapter-progress-dot"]')
  246 |     await expect(progressIndicator.first()).toHaveClass(/active/)
  247 |     await page.locator('[data-testid="option-item"]').first().click()
  248 |     await page.waitForTimeout(1500)
  249 |     await expect(progressIndicator.nth(1)).toHaveClass(/active/)
  250 |   })
  251 | })
  252 | 
  253 | test.describe('卷轴阅读 - 历史偏离度', () => {
  254 | 
  255 |   test.beforeEach(async ({ page }) => {
  256 |     setupStoryRouteMocks(page, '1')
  257 |     await page.goto(`${BASE_URL}/story/1`)
  258 |   })
  259 | 
  260 |   test('历史偏离度指示器应该可见', async ({ page }) => {
  261 |     await page.waitForSelector('[data-testid="deviation-indicator"]', { timeout: 10000 })
  262 |     await expect(page.locator('[data-testid="deviation-indicator"]')).toBeVisible()
  263 |   })
  264 | 
  265 |   test('选择后偏离度应该更新', async ({ page }) => {
  266 |     await page.waitForSelector('[data-testid="deviation-indicator"]', { timeout: 10000 })
  267 |     const initialDeviation = await page.locator('[data-testid="deviation-value"]').textContent()
  268 |     await page.locator('[data-testid="option-item"]').first().click()
  269 |     await page.waitForTimeout(1500)
  270 |     const newDeviation = await page.locator('[data-testid="deviation-value"]').textContent()
  271 |     expect(newDeviation).toBeTruthy()
  272 |   })
  273 | })
  274 | 
```