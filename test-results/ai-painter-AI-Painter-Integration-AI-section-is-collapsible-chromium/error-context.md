# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: ai-painter.spec.ts >> AI Painter Integration >> AI section is collapsible
- Location: tests/e2e/ai-painter.spec.ts:53:3

# Error details

```
TimeoutError: page.waitForSelector: Timeout 10000ms exceeded.
Call log:
  - waiting for locator('.card-slot') to be visible

```

# Page snapshot

```yaml
- generic [ref=e3]:
  - generic [ref=e4]: "[plugin:vite:esbuild] Transform failed with 1 error: /Users/jay/Projects/arbitrary-gate/src/frontend/services/aiPainter.ts:119:22: ERROR: Expected \";\" but found \"kw\""
  - generic [ref=e5]: /Users/jay/Projects/arbitrary-gate/src/frontend/services/aiPainter.ts:119:22
  - generic [ref=e6]: "Expected \";\" but found \"kw\" 117| async generateKeywordCard(params: CardImageParams): Promise<GenerationResult> { 118| const prompt = buildKeywordPrompt(params) 119| const cacheKey = `kw:${params.cardName}:${params.cardType}:${params.rarity}` | ^ 120| 121| const cached = this.getCached(cacheKey)"
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
  5  | async function seedCards(page: any): Promise<void> {
  6  |   await page.evaluate(() => {
  7  |     const keywordCards = [
  8  |       { id: 1, name: '旧船票', rarity: 2, category: 1, imageUrl: undefined },
  9  |       { id: 2, name: '说书匠', rarity: 3, category: 2, imageUrl: undefined },
  10 |       { id: 3, name: '青石板', rarity: 1, category: 3, imageUrl: undefined },
  11 |     ]
  12 |     const eventCards = [
  13 |       { id: 101, name: '赤壁·东风骤起', rarity: 3, category: 1 },
  14 |     ]
  15 |     localStorage.setItem('arbitrary_gate_keyword_cards', JSON.stringify(keywordCards))
  16 |     localStorage.setItem('arbitrary_gate_event_cards', JSON.stringify(eventCards))
  17 |     localStorage.setItem('arbitrary_gate_ink_stone', JSON.stringify(500))
  18 |   })
  19 | }
  20 | 
  21 | test.describe('AI Painter Integration', () => {
  22 |   test.beforeEach(async ({ page }) => {
  23 |     await page.goto(`${BASE_URL}/cards`)
  24 |     await seedCards(page)
  25 |     await page.reload()
  26 |     // Wait for card grid to render
> 27 |     await page.waitForSelector('.card-slot', { timeout: 10000 })
     |                ^ TimeoutError: page.waitForSelector: Timeout 10000ms exceeded.
  28 |   })
  29 | 
  30 |   test('AI Painter section appears when a keyword card is selected in CardsView', async ({ page }) => {
  31 |     // Click on a keyword card to select it
  32 |     await page.locator('.card-slot').first().click()
  33 |     // Check that AI Painter section appears
  34 |     await expect(page.locator('.ai-painter-section')).toBeVisible()
  35 |   })
  36 | 
  37 |   test('generate button is disabled when no card selected', async ({ page }) => {
  38 |     // No cards selected, AI section should not be visible
  39 |     await expect(page.locator('.ai-painter-section')).not.toBeVisible()
  40 |   })
  41 | 
  42 |   test('shows generated image after clicking generate', async ({ page }) => {
  43 |     // Select a card
  44 |     await page.locator('.card-slot').first().click()
  45 |     // Wait for AI section to appear
  46 |     await expect(page.locator('.ai-painter-section')).toBeVisible()
  47 |     // Click generate (will use mock/placeholder since no API key)
  48 |     await page.locator('.ai-generate-btn').click()
  49 |     // Wait for image to appear (placeholder or real)
  50 |     await expect(page.locator('.ai-result-image')).toBeVisible({ timeout: 10000 })
  51 |   })
  52 | 
  53 |   test('AI section is collapsible', async ({ page }) => {
  54 |     await page.locator('.card-slot').first().click()
  55 |     await expect(page.locator('.ai-painter-section')).toBeVisible()
  56 |     // Section body should be visible by default
  57 |     await expect(page.locator('.ai-painter-body')).toBeVisible()
  58 |     // Click header to collapse
  59 |     await page.locator('.ai-painter-header').click()
  60 |     // Body should be hidden (v-show)
  61 |     await expect(page.locator('.ai-painter-body')).not.toBeVisible()
  62 |   })
  63 | 
  64 |   test('preview shows selected card name', async ({ page }) => {
  65 |     await page.locator('.card-slot').first().click()
  66 |     await expect(page.locator('.ai-painter-section')).toBeVisible()
  67 |     // Preview should show card name "旧船票" (first seeded card)
  68 |     await expect(page.locator('.preview-name')).toContainText('旧船票')
  69 |   })
  70 | 
  71 |   test('style prompt textarea is functional', async ({ page }) => {
  72 |     await page.locator('.card-slot').first().click()
  73 |     await expect(page.locator('.ai-painter-section')).toBeVisible()
  74 |     const textarea = page.locator('.ai-prompt-input')
  75 |     await textarea.fill('雨天、古巷、夜晚氛围')
  76 |     await expect(textarea).toHaveValue('雨天、古巷、夜晚氛围')
  77 |   })
  78 | })
  79 | 
```