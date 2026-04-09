# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: ai-painter.spec.ts >> AI Painter Integration >> shows generated image after clicking generate
- Location: tests/e2e/ai-painter.spec.ts:41:3

# Error details

```
TimeoutError: page.waitForSelector: Timeout 15000ms exceeded.
Call log:
  - waiting for locator('.card-slot') to be visible

```

# Page snapshot

```yaml
- generic [ref=e3]:
  - generic [ref=e4]: "[plugin:vite:esbuild] Transform failed with 1 error: /Users/jay/Projects/arbitrary-gate/src/frontend/services/aiPainter.ts:119:22: ERROR: Expected \";\" but found \"kw\""
  - generic [ref=e5]: /Users/jay/Projects/arbitrary-gate/src/frontend/services/aiPainter.ts:119:22
  - generic [ref=e6]: "Expected \";\" but found \"kw\" 117| async generateKeywordCard(params: CardImageParams): Promise<GenerationResult> { 118| const prompt = buildKeywordPrompt(params); 119| const cacheKey = `kw:${params.cardName}:${params.cardType}:${params.rarity}`; | ^ 120| 121| const cached = this.getCached(cacheKey);"
  - generic [ref=e7]: at failureErrorWithLog (/Users/jay/Projects/arbitrary-gate/node_modules/esbuild/lib/main.js:1472:15) at /Users/jay/Projects/arbitrary-gate/node_modules/esbuild/lib/main.js:755:50 at responseCallbacks.<computed> (/Users/jay/Projects/arbitrary-gate/node_modules/esbuild/lib/main.js:622:9) at handleIncomingPacket (/Users/jay/Projects/arbitrary-gate/node_modules/esbuild/lib/main.js:677:12) at Socket.readFromStdout (/Users/jay/Projects/arbitrary-gate/node_modules/esbuild/lib/main.js:600:7) at Socket.emit (node:events:508:28) at addChunk (node:internal/streams/readable:563:12) at readableAddChunkPushByteMode (node:internal/streams/readable:514:3) at Readable.push (node:internal/streams/readable:394:5) at Pipe.onStreamRead (node:internal/stream_base_commons:189:23
  - generic [ref=e8]:
    - text: Click outside, press Esc key, or fix the code to dismiss.
    - text: You can also disable this overlay by setting
    - code [ref=e9]: server.hmr.overlay
    - text: to
    - code [ref=e10]: "false"
    - text: in
    - code [ref=e11]: vite.config.ts
    - text: .
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
> 26 |     await page.waitForSelector('.card-slot', { timeout: 15000 })
     |                ^ TimeoutError: page.waitForSelector: Timeout 15000ms exceeded.
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
  47 |     await page.locator('.ai-generate-btn').click()
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