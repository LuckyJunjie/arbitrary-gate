# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: story.spec.ts >> 卷轴阅读模块 >> 选项应该正确展示且可点击
- Location: tests/e2e/story.spec.ts:157:3

# Error details

```
Error: page.goto: net::ERR_CONNECTION_REFUSED at http://localhost:5175/story/1
Call log:
  - navigating to "http://localhost:5175/story/1", waiting until "load"

```

# Test source

```ts
  33  |     chapterNo: 2,
  34  |     sceneText: '第二章场景：火光冲天，敌舰尽焚...',
  35  |     options: [
  36  |       { id: 1, text: '乘胜追击' },
  37  |       { id: 2, text: '收兵回营' },
  38  |       { id: 3, text: '安抚伤兵' },
  39  |     ],
  40  |     keywordResonance: { 1: 2, 2: 1, 3: 3 },
  41  |     ripples: [],
  42  |   },
  43  |   3: {
  44  |     chapterNo: 3,
  45  |     sceneText: '第三章场景：战后余烬，历史分叉...',
  46  |     options: [
  47  |       { id: 1, text: '载入史册' },
  48  |       { id: 2, text: '隐姓埋名' },
  49  |       { id: 3, text: '继续前行' },
  50  |     ],
  51  |     keywordResonance: { 1: 3, 2: 2, 3: 4 },
  52  |     ripples: [],
  53  |   },
  54  |   4: {
  55  |     chapterNo: 4,
  56  |     sceneText: '尾声：故事落幕，星河长明。',
  57  |     options: [],
  58  |     keywordResonance: { 1: 4, 2: 3, 3: 6 },
  59  |     ripples: [],
  60  |   },
  61  | }
  62  | 
  63  | const MOCK_MANUSCRIPT = {
  64  |   fullText: '赤壁之战尘埃落定...这是一段完整的手稿全文。',
  65  |   wordCount: 1234,
  66  |   annotations: [],
  67  |   epilogue: '尾声',
  68  | }
  69  | 
  70  | // ==================== API Mock 辅助函数 ====================
  71  | 
  72  | /**
  73  |  * 为 StoryView 的 API 调用模式设置 route mock。
  74  |  * StoryView 在 onMount 时调用 GET /api/story/:id/chapter/:no，
  75  |  * 选择选项时调用 POST /api/story/:id/chapter/:no/choose。
  76  |  */
  77  | function setupStoryRouteMocks(page: any, storyId: string = '1') {
  78  |   // Mock GET /api/story/:id/chapter/:no
  79  |   page.route(
  80  |     new RegExp(`/api/story/${storyId}/chapter/\\d+`),
  81  |     route => {
  82  |       const url = route.request().url()
  83  |       const match = url.match(new RegExp(`/api/story/${storyId}/chapter/(\\d+)`))
  84  |       const chapterNo = match ? parseInt(match[1]) : 1
  85  |       const chapterData = MOCK_CHAPTERS[chapterNo] || MOCK_CHAPTERS[1]
  86  |       route.fulfill({
  87  |         status: 200,
  88  |         contentType: 'application/json',
  89  |         body: JSON.stringify(chapterData),
  90  |       })
  91  |     }
  92  |   )
  93  | 
  94  |   // Mock POST /api/story/:id/chapter/:no/choose
  95  |   page.route(
  96  |     new RegExp(`/api/story/${storyId}/chapter/\\d+/choose`),
  97  |     route => {
  98  |       const url = route.request().url()
  99  |       const match = url.match(new RegExp(`/api/story/${storyId}/chapter/(\\d+)/choose`))
  100 |       const currentChapter = match ? parseInt(match[1]) : 1
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
> 133 |     await page.goto(`${BASE_URL}/story/1`)
      |                ^ Error: page.goto: net::ERR_CONNECTION_REFUSED at http://localhost:5175/story/1
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
```