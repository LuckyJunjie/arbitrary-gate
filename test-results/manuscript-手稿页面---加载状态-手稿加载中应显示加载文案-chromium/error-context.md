# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: manuscript.spec.ts >> 手稿页面 - 加载状态 >> 手稿加载中应显示加载文案
- Location: tests/e2e/manuscript.spec.ts:222:3

# Error details

```
Error: page.goto: net::ERR_CONNECTION_REFUSED at http://localhost:5175/manuscript/loading-test
Call log:
  - navigating to "http://localhost:5175/manuscript/loading-test", waiting until "load"

```

# Test source

```ts
  135 | 
  136 |   test('手稿印鉴应该可见', async ({ page }) => {
  137 |     await page.goto(`${BASE_URL}/manuscript/1`)
  138 |     await expect(page.locator('[data-testid="manuscript-seal"]')).toBeVisible({ timeout: 10000 })
  139 |     const seal = page.locator('[data-testid="manuscript-seal"]')
  140 |     await expect(seal).toBeVisible()
  141 |   })
  142 | 
  143 |   test('后日谈应该可见', async ({ page }) => {
  144 |     await page.goto(`${BASE_URL}/manuscript/1`)
  145 |     await expect(page.locator('[data-testid="manuscript-epilogue"]')).toBeVisible({ timeout: 10000 })
  146 |     const epilogue = page.locator('[data-testid="manuscript-epilogue"]')
  147 |     await expect(epilogue).toBeVisible()
  148 |   })
  149 | 
  150 |   test('返回书架按钮应该可点击并导航', async ({ page }) => {
  151 |     await page.goto(`${BASE_URL}/manuscript/1`)
  152 |     await expect(page.locator('[data-testid="manuscript-back"]')).toBeVisible({ timeout: 10000 })
  153 |     const backBtn = page.locator('[data-testid="manuscript-back"]')
  154 |     await backBtn.click()
  155 |     await expect(page).toHaveURL(/\/bookshelf/)
  156 |   })
  157 | 
  158 |   test('选择标记应该显示在对应段落', async ({ page }) => {
  159 |     await page.goto(`${BASE_URL}/manuscript/1`)
  160 |     await page.waitForSelector('[data-testid="manuscript-para-0"]', { timeout: 10000 })
  161 |     const choiceMark = page.locator('.choice-mark').first()
  162 |     await expect(choiceMark).toBeVisible()
  163 |   })
  164 | })
  165 | 
  166 | test.describe('手稿页面 - 从故事完结跳转', () => {
  167 | 
  168 |   test('完结此篇按钮应导航至手稿页', async ({ page }) => {
  169 |     // 修复: StoryView → ManuscriptView 导航后的 manuscript 数据加载
  170 |     setupManuscriptMocks(page, '1')
  171 | 
  172 |     // Mock chapter endpoint - return an end chapter (no options) so finish button appears
  173 |     await page.route(
  174 |       new RegExp('/api/story/1/chapter/\\d+'),
  175 |       route => {
  176 |         route.fulfill({
  177 |           status: 200,
  178 |           contentType: 'application/json',
  179 |           body: JSON.stringify({
  180 |             chapterNo: 1,
  181 |             sceneText: '尾声：故事落幕，星河长明。',
  182 |             options: [],
  183 |             keywordResonance: { 1: 4, 2: 3, 3: 6 },
  184 |             ripples: [],
  185 |           }),
  186 |         })
  187 |       }
  188 |     )
  189 | 
  190 |     // Mock finishStory
  191 |     await page.route(
  192 |       new RegExp('/api/story/1/finish'),
  193 |       route => {
  194 |         route.fulfill({
  195 |           status: 200,
  196 |           contentType: 'application/json',
  197 |           body: JSON.stringify(MOCK_MANUSCRIPT),
  198 |         })
  199 |       }
  200 |     )
  201 | 
  202 |     await page.goto(`${BASE_URL}/story/1`)
  203 | 
  204 |     // 等待故事加载完成（出现完结此篇按钮）
  205 |     await page.waitForSelector('.finish-btn', { timeout: 15000 })
  206 |     await page.locator('.finish-btn').click()
  207 | 
  208 |     // 验证跳转到了手稿页
  209 |     await expect(page).toHaveURL(/\/manuscript\/1/, { timeout: 10000 })
  210 |     // 等待手稿内容加载
  211 |     await page.waitForSelector('[data-testid="manuscript-title"]', { timeout: 10000 })
  212 |     await expect(page.locator('[data-testid="manuscript-title"]')).toBeVisible({ timeout: 10000 })
  213 |   })
  214 | })
  215 | 
  216 | test.describe('手稿页面 - 加载状态', () => {
  217 | 
  218 |   test.beforeEach(async ({ page }) => {
  219 |     setupManuscriptMocks(page, 'loading-test')
  220 |   })
  221 | 
  222 |   test('手稿加载中应显示加载文案', async ({ page }) => {
  223 |     // 延迟响应模拟加载中
  224 |     await page.route(
  225 |       new RegExp('/api/story/loading-test/manuscript'),
  226 |       async route => {
  227 |         await new Promise(resolve => setTimeout(resolve, 2000))
  228 |         route.fulfill({
  229 |           status: 200,
  230 |           contentType: 'application/json',
  231 |           body: JSON.stringify(MOCK_MANUSCRIPT),
  232 |         })
  233 |       }
  234 |     )
> 235 |     await page.goto(`${BASE_URL}/manuscript/loading-test`)
      |                ^ Error: page.goto: net::ERR_CONNECTION_REFUSED at http://localhost:5175/manuscript/loading-test
  236 |     // 加载状态应该在 DOM 中短暂出现
  237 |     const loadingText = page.locator('.loading-text')
  238 |     // 不使用 toBeVisible 因为可能已经加载完成
  239 |     const count = await page.locator('.manuscript-loading, [data-testid="manuscript-scroll"]').count()
  240 |     expect(count).toBeGreaterThan(0)
  241 |   })
  242 | })
  243 | 
```