# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: ai-painter.spec.ts >> AI Painter Integration >> shows generated image after clicking generate
- Location: tests/e2e/ai-painter.spec.ts:41:3

# Error details

```
Test timeout of 30000ms exceeded.
```

```
Error: locator.click: Test timeout of 30000ms exceeded.
Call log:
  - waiting for locator('.ai-generate-btn')
    - locator resolved to <button data-v-498173d0="" class="ai-generate-btn">🖌️ 挥毫作画</button>
  - attempting click action
    2 × waiting for element to be visible, enabled and stable
      - element is not visible
    - retrying click action
    - waiting 20ms
    2 × waiting for element to be visible, enabled and stable
      - element is not visible
    - retrying click action
      - waiting 100ms
    55 × waiting for element to be visible, enabled and stable
       - element is not visible
     - retrying click action
       - waiting 500ms

```

# Page snapshot

```yaml
- generic [ref=e3]:
  - generic [ref=e4]:
    - generic [ref=e5]: 游客模式 ·
    - button "获取关键词" [ref=e6] [cursor=pointer]
  - generic [ref=e7]:
    - banner [ref=e8]:
      - generic [ref=e9]:
        - heading "卡匣" [level=2] [ref=e10]
        - paragraph [ref=e11]: 共 4 张
      - generic [ref=e13]:
        - generic [ref=e14]:
          - generic [ref=e15]: 墨香
          - generic [ref=e16]: 初墨
          - generic [ref=e17]: Lv.1
        - generic [ref=e19]:
          - generic [ref=e20]: "0"
          - generic [ref=e21]: 距 入墨 还差 100 点
    - navigation [ref=e22]:
      - button "关键词" [ref=e23] [cursor=pointer]
      - button "事件" [ref=e24] [cursor=pointer]
    - generic [ref=e25]:
      - button "全部" [ref=e26] [cursor=pointer]
      - button "凡" [ref=e27] [cursor=pointer]
      - button "珍" [ref=e28] [cursor=pointer]
      - button "奇" [ref=e29] [cursor=pointer]
      - button "绝" [ref=e30] [cursor=pointer]
    - generic [ref=e31]:
      - generic [ref=e32] [cursor=pointer]:
        - button "🅾️" [ref=e33]
        - button "笺 时光笺 旧 旧船票 珍 器物 墨香 点击卡面翻转" [active] [ref=e34]:
          - generic [ref=e36]:
            - generic [ref=e37]: 笺
            - generic [ref=e38]: 时光笺
          - generic [ref=e39]:
            - generic [ref=e41]: 旧
            - generic [ref=e42]: 旧船票
            - generic [ref=e43]:
              - generic [ref=e44]: 珍
              - generic [ref=e45]: 器物
            - generic [ref=e47]: 墨香
            - generic [ref=e56]: 点击卡面翻转
        - generic [ref=e57]: ✓
      - generic [ref=e58] [cursor=pointer]:
        - button "🅾️" [ref=e59]
        - button "笺 时光笺 说 说书匠 奇 职人 墨香 点击卡面翻转" [ref=e60]:
          - generic [ref=e62]:
            - generic [ref=e63]: 笺
            - generic [ref=e64]: 时光笺
          - generic [ref=e65]:
            - generic [ref=e67]: 说
            - generic [ref=e68]: 说书匠
            - generic [ref=e69]:
              - generic [ref=e70]: 奇
              - generic [ref=e71]: 职人
            - generic [ref=e73]: 墨香
            - generic [ref=e82]: 点击卡面翻转
      - generic [ref=e83] [cursor=pointer]:
        - button "🅾️" [ref=e84]
        - button "笺 时光笺 青 青石板 凡 风物 墨香 点击卡面翻转" [ref=e85]:
          - generic [ref=e87]:
            - generic [ref=e88]: 笺
            - generic [ref=e89]: 时光笺
          - generic [ref=e90]:
            - generic [ref=e92]: 青
            - generic [ref=e93]: 青石板
            - generic [ref=e94]:
              - generic [ref=e95]: 凡
              - generic [ref=e96]: 风物
            - generic [ref=e98]: 墨香
            - generic [ref=e107]: 点击卡面翻转
    - generic [ref=e109] [cursor=pointer]:
      - generic [ref=e110]: 🖌️ AI 画师
      - generic [ref=e111]: ▶
    - generic [ref=e112]:
      - generic [ref=e113]:
        - generic [ref=e114]: 已选关键词
        - generic [ref=e115]: 1/3
        - generic [ref=e116]: "|"
        - generic [ref=e117]: 事件
        - generic [ref=e118]: 未选
      - button "还需选2个" [disabled] [ref=e119]
```

# Test source

```ts
  1  | import { test, expect } from '@playwright/test'
  2  | 
  3  | const BASE_URL = process.env.E2E_BASE_URL || 'http://localhost:5175'
  4  | 
  5  | const SEED_DATA = {
  6  |   keywordCards: [
  7  |     { id: 1, name: '旧船票', rarity: 2, category: 1, imageUrl: undefined },
  8  |     { id: 2, name: '说书匠', rarity: 3, category: 2, imageUrl: undefined },
  9  |     { id: 3, name: '青石板', rarity: 1, category: 3, imageUrl: undefined },
  10 |   ],
  11 |   eventCards: [
  12 |     { id: 101, name: '赤壁·东风骤起', rarity: 3, category: 1 },
  13 |   ],
  14 | }
  15 | 
  16 | test.describe('AI Painter Integration', () => {
  17 |   test.beforeEach(async ({ page }) => {
  18 |     // Use addInitScript so localStorage is set BEFORE Vue app initializes
  19 |     await page.addInitScript(({ keywordCards, eventCards }: typeof SEED_DATA) => {
  20 |       localStorage.setItem('arbitrary_gate_keyword_cards', JSON.stringify(keywordCards))
  21 |       localStorage.setItem('arbitrary_gate_event_cards', JSON.stringify(eventCards))
  22 |       localStorage.setItem('arbitrary_gate_ink_stone', JSON.stringify(500))
  23 |     }, SEED_DATA)
  24 |     await page.goto(`${BASE_URL}/cards`)
  25 |     // Wait for card grid to render
  26 |     await page.waitForSelector('.card-slot', { timeout: 15000 })
  27 |   })
  28 | 
  29 |   test('AI Painter section appears when a keyword card is selected in CardsView', async ({ page }) => {
  30 |     // Click on a keyword card to select it
  31 |     await page.locator('.card-slot').first().click()
  32 |     // Check that AI Painter section appears
  33 |     await expect(page.locator('.ai-painter-section')).toBeVisible()
  34 |   })
  35 | 
  36 |   test('generate button is disabled when no card selected', async ({ page }) => {
  37 |     // No cards selected, AI section should not be visible
  38 |     await expect(page.locator('.ai-painter-section')).not.toBeVisible()
  39 |   })
  40 | 
  41 |   test('shows generated image after clicking generate', async ({ page }) => {
  42 |     // Select a card
  43 |     await page.locator('.card-slot').first().click()
  44 |     // Wait for AI section to appear
  45 |     await expect(page.locator('.ai-painter-section')).toBeVisible()
  46 |     // Click generate (will use mock/placeholder since no API key)
> 47 |     await page.locator('.ai-generate-btn').click()
     |                                            ^ Error: locator.click: Test timeout of 30000ms exceeded.
  48 |     // Wait for image to appear (placeholder or real)
  49 |     await expect(page.locator('.ai-result-image')).toBeVisible({ timeout: 10000 })
  50 |   })
  51 | 
  52 |   test('AI section is collapsible', async ({ page }) => {
  53 |     await page.locator('.card-slot').first().click()
  54 |     await expect(page.locator('.ai-painter-section')).toBeVisible()
  55 |     // Section body should be visible by default
  56 |     await expect(page.locator('.ai-painter-body')).toBeVisible()
  57 |     // Click header to collapse
  58 |     await page.locator('.ai-painter-header').click()
  59 |     // Body should be hidden (v-show)
  60 |     await expect(page.locator('.ai-painter-body')).not.toBeVisible()
  61 |   })
  62 | 
  63 |   test('preview shows selected card name', async ({ page }) => {
  64 |     await page.locator('.card-slot').first().click()
  65 |     await expect(page.locator('.ai-painter-section')).toBeVisible()
  66 |     // Preview should show card name "旧船票" (first seeded card)
  67 |     await expect(page.locator('.preview-name')).toContainText('旧船票')
  68 |   })
  69 | 
  70 |   test('style prompt textarea is functional', async ({ page }) => {
  71 |     await page.locator('.card-slot').first().click()
  72 |     await expect(page.locator('.ai-painter-section')).toBeVisible()
  73 |     const textarea = page.locator('.ai-prompt-input')
  74 |     await textarea.fill('雨天、古巷、夜晚氛围')
  75 |     await expect(textarea).toHaveValue('雨天、古巷、夜晚氛围')
  76 |   })
  77 | })
  78 | 
```