# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: entry-questions.spec.ts >> 入局三问模块 >> 提交后应跳转到故事页
- Location: tests/e2e/entry-questions.spec.ts:155:3

# Error details

```
Error: page.goto: net::ERR_CONNECTION_REFUSED at http://localhost:5175/entry-questions
Call log:
  - navigating to "http://localhost:5175/entry-questions", waiting until "load"

```

# Test source

```ts
  15  | 
  16  | // ==================== Mock 数据 ====================
  17  | 
  18  | const MOCK_QUESTIONS = [
  19  |   {
  20  |     id: 1,
  21  |     category: '角色背景',
  22  |     question: '你今日当值，袖中揣着什么？',
  23  |     hint: '初始装备',
  24  |   },
  25  |   {
  26  |     id: 2,
  27  |     category: '当下处境',
  28  |     question: '你最怕见到什么人？',
  29  |     hint: '影响走向',
  30  |   },
  31  |   {
  32  |     id: 3,
  33  |     category: '内心渴望',
  34  |     question: '你最大的心愿是什么？',
  35  |     hint: '故事暗线',
  36  |   },
  37  | ]
  38  | 
  39  | const MOCK_STORY_START = {
  40  |   id: '1',
  41  |   storyNo: 'ST-001',
  42  |   title: '赤壁·东风',
  43  |   status: 1,
  44  |   currentChapter: 1,
  45  |   historyDeviation: 0,
  46  |   createdAt: new Date().toISOString(),
  47  | }
  48  | 
  49  | const MOCK_CHAPTER_1 = {
  50  |   chapterNo: 1,
  51  |   sceneText: '赤壁江边，东风骤起。你站在船头，望着远处的曹营...',
  52  |   options: [
  53  |     { id: 1, text: '下令火攻', valueTag: '义' },
  54  |     { id: 2, text: '坚守不出', valueTag: '利' },
  55  |     { id: 3, text: '派人求和', valueTag: '情' },
  56  |   ],
  57  |   keywordResonance: { 1: 0, 2: 0, 3: 0 },
  58  | }
  59  | 
  60  | // ==================== API Mock 辅助函数 ====================
  61  | 
  62  | function setupEntryQuestionsMocks(page: any, storyId: string = '1') {
  63  |   // Extract origin from BASE_URL for reliable route matching
  64  |   const baseOrigin = BASE_URL.replace(/\/$/, '')
  65  | 
  66  |   // Mock POST /api/story/questions — AI 生成问题
  67  |   page.route(
  68  |     new RegExp(`${baseOrigin}/api/story/questions`),
  69  |     route => {
  70  |       route.fulfill({
  71  |         status: 200,
  72  |         contentType: 'application/json',
  73  |         body: JSON.stringify({ questions: MOCK_QUESTIONS }),
  74  |       })
  75  |     }
  76  |   )
  77  | 
  78  |   // Mock POST /api/story/answers — 提交答案并开始故事
  79  |   page.route(
  80  |     new RegExp(`${baseOrigin}/api/story/answers`),
  81  |     route => {
  82  |       const body = route.request().postData()
  83  |       const payload = body ? JSON.parse(body) : {}
  84  |       if (payload.entryAnswers) {
  85  |         console.log('[mock] submitEntryAnswers received entryAnswers:', payload.entryAnswers)
  86  |       }
  87  |       route.fulfill({
  88  |         status: 200,
  89  |         contentType: 'application/json',
  90  |         body: JSON.stringify(MOCK_STORY_START),
  91  |       })
  92  |     }
  93  |   )
  94  | 
  95  |   // Mock GET /api/story/:id/chapter/1 — 获取第一章
  96  |   page.route(
  97  |     new RegExp(`${baseOrigin}/api/story/${storyId}/chapter/\\d+`),
  98  |     () => {
  99  |       return {
  100 |         status: 200,
  101 |         contentType: 'application/json',
  102 |         body: JSON.stringify(MOCK_CHAPTER_1),
  103 |       }
  104 |     }
  105 |   )
  106 | }
  107 | 
  108 | // ==================== E2E 测试用例 ====================
  109 | 
  110 | test.describe('入局三问模块', () => {
  111 | 
  112 |   test.beforeEach(async ({ page }) => {
  113 |     // Mock 必须先于页面导航注册，才能拦截初始 API 调用
  114 |     setupEntryQuestionsMocks(page, '1')
> 115 |     await page.goto(`${BASE_URL}/entry-questions`)
      |                ^ Error: page.goto: net::ERR_CONNECTION_REFUSED at http://localhost:5175/entry-questions
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
  213 |     await page.goto(`${BASE_URL}/cards`)
  214 |     await page.evaluate(() => {
  215 |       const mockCards = [
```