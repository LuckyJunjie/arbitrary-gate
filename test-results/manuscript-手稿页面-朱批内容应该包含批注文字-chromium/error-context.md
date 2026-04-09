# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: manuscript.spec.ts >> 手稿页面 >> 朱批内容应该包含批注文字
- Location: tests/e2e/manuscript.spec.ts:127:3

# Error details

```
Error: page.goto: net::ERR_CONNECTION_REFUSED at http://localhost:5175/manuscript/1
Call log:
  - navigating to "http://localhost:5175/manuscript/1", waiting until "load"

```

# Test source

```ts
  28  |   choiceMarks: [
  29  |     { chapterNo: 0, optionId: 1, text: '火攻' },
  30  |     { chapterNo: 1, optionId: 2, text: '观望' },
  31  |   ],
  32  |   epilogue: '后日谈：战火平息，岁月如梭。',
  33  |   baiguanComment: '此故事偏离正史35%，尚在可接受范围。',
  34  | }
  35  | 
  36  | const MOCK_STORY = {
  37  |   id: 'story-1',
  38  |   storyNo: '001',
  39  |   title: '赤壁·东风骤起',
  40  |   status: 2,
  41  |   currentChapter: 3,
  42  |   historyDeviation: 35,
  43  |   createdAt: '2026-01-01T00:00:00Z',
  44  |   finishedAt: '2026-01-01T00:10:00Z',
  45  | }
  46  | 
  47  | // ==================== API Mock 辅助函数 ====================
  48  | 
  49  | function setupManuscriptMocks(page: any, storyId: string = '1') {
  50  |   // Mock GET /api/story/:id
  51  |   page.route(
  52  |     new RegExp(`/api/story/${storyId}$`),
  53  |     route => {
  54  |       route.fulfill({
  55  |         status: 200,
  56  |         contentType: 'application/json',
  57  |         body: JSON.stringify(MOCK_STORY),
  58  |       })
  59  |     }
  60  |   )
  61  |   // Mock GET /api/story/:id/manuscript
  62  |   page.route(
  63  |     new RegExp(`/api/story/${storyId}/manuscript`),
  64  |     route => {
  65  |       route.fulfill({
  66  |         status: 200,
  67  |         contentType: 'application/json',
  68  |         body: JSON.stringify(MOCK_MANUSCRIPT),
  69  |       })
  70  |     }
  71  |   )
  72  | }
  73  | 
  74  | // ==================== E2E 测试用例 ====================
  75  | 
  76  | test.describe('手稿页面', () => {
  77  | 
  78  |   test.beforeEach(async ({ page }) => {
  79  |     setupManuscriptMocks(page, '1')
  80  |   })
  81  | 
  82  |   test('手稿页面应该正确加载', async ({ page }) => {
  83  |     await page.goto(`${BASE_URL}/manuscript/1`)
  84  |     await expect(page.locator('[data-testid="manuscript-title"]')).toBeVisible({ timeout: 10000 })
  85  |   })
  86  | 
  87  |   test('手稿页面应该显示宣纸底色背景', async ({ page }) => {
  88  |     await page.goto(`${BASE_URL}/manuscript/1`)
  89  |     await page.waitForSelector('[data-testid="manuscript-scroll"]', { timeout: 10000 })
  90  |     const scroll = page.locator('[data-testid="manuscript-scroll"]')
  91  |     await expect(scroll).toBeVisible()
  92  |   })
  93  | 
  94  |   test('手稿正文应该竖向排版', async ({ page }) => {
  95  |     await page.goto(`${BASE_URL}/manuscript/1`)
  96  |     await page.waitForSelector('[data-testid="manuscript-body"]', { timeout: 10000 })
  97  |     const body = page.locator('[data-testid="manuscript-body"]')
  98  |     const writingMode = await body.evaluate((el: Element) => {
  99  |       return window.getComputedStyle(el).writingMode
  100 |     })
  101 |     expect(writingMode).toMatch(/vertical-rl|vertical-lr/)
  102 |   })
  103 | 
  104 |   test('手稿页面应该显示字数统计', async ({ page }) => {
  105 |     await page.goto(`${BASE_URL}/manuscript/1`)
  106 |     await expect(page.locator('[data-testid="manuscript-word-count"]')).toBeVisible({ timeout: 10000 })
  107 |     const wordCount = await page.locator('[data-testid="manuscript-word-count"]').textContent()
  108 |     expect(wordCount).toContain('1024')
  109 |   })
  110 | 
  111 |   test('手稿页面应该显示正文段落', async ({ page }) => {
  112 |     await page.goto(`${BASE_URL}/manuscript/1`)
  113 |     await page.waitForSelector('[data-testid="manuscript-para-0"]', { timeout: 10000 })
  114 |     const firstPara = page.locator('[data-testid="manuscript-para-0"]')
  115 |     await expect(firstPara).toBeVisible()
  116 |     const text = await firstPara.textContent()
  117 |     expect(text).toContain('赤壁之战')
  118 |   })
  119 | 
  120 |   test('朱批注释应该可见', async ({ page }) => {
  121 |     await page.goto(`${BASE_URL}/manuscript/1`)
  122 |     await page.waitForSelector('[data-testid="manuscript-para-0"]', { timeout: 10000 })
  123 |     const annotations = page.locator('[data-testid="zhub-annotation"]')
  124 |     await expect(annotations.first()).toBeVisible()
  125 |   })
  126 | 
  127 |   test('朱批内容应该包含批注文字', async ({ page }) => {
> 128 |     await page.goto(`${BASE_URL}/manuscript/1`)
      |                ^ Error: page.goto: net::ERR_CONNECTION_REFUSED at http://localhost:5175/manuscript/1
  129 |     await page.waitForSelector('[data-testid="zhub-annotation"]', { timeout: 10000 })
  130 |     const zhubText = page.locator('.zhub-text').first()
  131 |     await expect(zhubText).toBeVisible()
  132 |     const text = await zhubText.textContent()
  133 |     expect(text).toBeTruthy()
  134 |   })
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
```