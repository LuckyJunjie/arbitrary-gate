# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: entry-questions.spec.ts >> 入局三问 - 从卡牌选择页进入 >> 从卡牌页选择3张卡后应进入入局三问
- Location: tests/e2e/entry-questions.spec.ts:209:3

# Error details

```
Error: page.goto: net::ERR_CONNECTION_REFUSED at http://localhost:5175/cards
Call log:
  - navigating to "http://localhost:5175/cards", waiting until "load"

```

# Test source

```ts
  113 |     // Mock 必须先于页面导航注册，才能拦截初始 API 调用
  114 |     setupEntryQuestionsMocks(page, '1')
  115 |     await page.goto(`${BASE_URL}/entry-questions`)
  116 |   })
  117 | 
  118 |   test('页面应该使用卷轴样式（竖排布局）', async ({ page }) => {
  119 |     await expect(page.locator('[data-testid="entry-questions-container"]')).toBeVisible({ timeout: 10000 })
  120 |     // 检查问题区域使用竖排
  121 |     const scrollArea = page.locator('[data-testid="entry-questions-container"] .questions-vertical')
  122 |     const writingMode = await scrollArea.evaluate((el: Element) => {
  123 |       return window.getComputedStyle(el).writingMode
  124 |     })
  125 |     expect(writingMode).toMatch(/vertical-rl|vertical-lr/)
  126 |   })
  127 | 
  128 |   test('应该显示3个问题，每个有竖排输入框', async ({ page }) => {
  129 |     await expect(page.locator('[data-testid="question-item"]')).toHaveCount(3, { timeout: 10000 })
  130 |   })
  131 | 
  132 |   test('问题应该包含题目和提示文字', async ({ page }) => {
  133 |     const firstQuestion = page.locator('[data-testid="question-item"]').first()
  134 |     await expect(firstQuestion.locator('[data-testid="question-text"]')).toBeVisible()
  135 |     await expect(firstQuestion.locator('[data-testid="question-hint"]')).toBeVisible()
  136 |   })
  137 | 
  138 |   test('用户可以填写答案', async ({ page }) => {
  139 |     await page.waitForSelector('[data-testid="question-input"]', { timeout: 10000 })
  140 |     const inputs = page.locator('[data-testid="question-input"]')
  141 | 
  142 |     await inputs.nth(0).fill('半块干饼')
  143 |     await inputs.nth(1).fill('禁军的人')
  144 |     await inputs.nth(2).fill('平平安安活过今年')
  145 | 
  146 |     const input1Value = await inputs.nth(0).inputValue()
  147 |     const input2Value = await inputs.nth(1).inputValue()
  148 |     const input3Value = await inputs.nth(2).inputValue()
  149 | 
  150 |     expect(input1Value).toBe('半块干饼')
  151 |     expect(input2Value).toBe('禁军的人')
  152 |     expect(input3Value).toBe('平平安安活过今年')
  153 |   })
  154 | 
  155 |   test('提交后应跳转到故事页', async ({ page }) => {
  156 |     await page.waitForSelector('[data-testid="question-input"]', { timeout: 10000 })
  157 |     const inputs = page.locator('[data-testid="question-input"]')
  158 |     await inputs.nth(0).fill('半块干饼')
  159 |     await inputs.nth(1).fill('禁军的人')
  160 |     await inputs.nth(2).fill('平平安安活过今年')
  161 | 
  162 |     // 点击确认入局
  163 |     await page.locator('[data-testid="confirm-entry-btn"]').click()
  164 | 
  165 |     // 应该跳转到故事页
  166 |     await page.waitForURL(`**/story/**`, { timeout: 10000 })
  167 |     const url = page.url()
  168 |     expect(url).toContain('/story/')
  169 |   })
  170 | 
  171 |   test('提交时验证所有答案已填写', async ({ page }) => {
  172 |     await page.waitForSelector('[data-testid="question-input"]', { timeout: 10000 })
  173 | 
  174 |     // 只填第一个答案就提交
  175 |     await page.locator('[data-testid="question-input"]').first().fill('半块干饼')
  176 | 
  177 |     // 提交按钮应该被禁用
  178 |     const confirmBtn = page.locator('[data-testid="confirm-entry-btn"]')
  179 |     await expect(confirmBtn).toBeDisabled()
  180 |   })
  181 | 
  182 |   test('问题加载中状态应该显示', async ({ page }) => {
  183 |     const baseOrigin = BASE_URL.replace(/\/$/, '')
  184 |     // 重新注册 mock，延迟返回（先注册延迟的，再注册beforeEach的）
  185 |     page.route(
  186 |       new RegExp(`${baseOrigin}/api/story/questions`),
  187 |       async route => {
  188 |         await new Promise(resolve => setTimeout(resolve, 2000))
  189 |         route.fulfill({
  190 |           status: 200,
  191 |           contentType: 'application/json',
  192 |           body: JSON.stringify({ questions: MOCK_QUESTIONS }),
  193 |         })
  194 |       }
  195 |     )
  196 | 
  197 |     await page.goto(`${BASE_URL}/entry-questions`)
  198 | 
  199 |     // 初始应该显示加载状态
  200 |     await expect(page.locator('[data-testid="questions-loading"]')).toBeVisible({ timeout: 3000 })
  201 | 
  202 |     // 加载完成后显示问题
  203 |     await expect(page.locator('[data-testid="question-item"]')).toHaveCount(3, { timeout: 10000 })
  204 |   })
  205 | })
  206 | 
  207 | test.describe('入局三问 - 从卡牌选择页进入', () => {
  208 | 
  209 |   test('从卡牌页选择3张卡后应进入入局三问', async ({ page }) => {
  210 |     setupEntryQuestionsMocks(page, '1')
  211 | 
  212 |     // 模拟已选择3张卡的状态
> 213 |     await page.goto(`${BASE_URL}/cards`)
      |                ^ Error: page.goto: net::ERR_CONNECTION_REFUSED at http://localhost:5175/cards
  214 |     await page.evaluate(() => {
  215 |       const mockCards = [
  216 |         { id: 1, name: '旧船票', rarity: 2, category: 1 },
  217 |         { id: 2, name: '意难平', rarity: 3, category: 4 },
  218 |         { id: 3, name: '摆渡人', rarity: 2, category: 2 },
  219 |       ]
  220 |       localStorage.setItem('selectedKeywordCards', JSON.stringify(mockCards))
  221 |       localStorage.setItem('selectedEventCard', JSON.stringify({
  222 |         id: 101,
  223 |         name: '赤壁崖·东风骤起',
  224 |         rarity: 2,
  225 |         category: 1,
  226 |       }))
  227 |     })
  228 | 
  229 |     // 导航到入局三问
  230 |     await page.goto(`${BASE_URL}/entry-questions`)
  231 | 
  232 |     await expect(page.locator('[data-testid="entry-questions-container"]')).toBeVisible({ timeout: 10000 })
  233 |     await expect(page.locator('[data-testid="question-item"]')).toHaveCount(3, { timeout: 10000 })
  234 |   })
  235 | })
  236 | 
```