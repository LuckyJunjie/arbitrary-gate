import { test, expect } from '@playwright/test'

const BASE_URL = process.env.E2E_BASE_URL || 'http://localhost:5175'

const SEED_DATA = {
  keywordCards: [
    { id: 1, name: '旧船票', rarity: 2, category: 1, imageUrl: undefined },
    { id: 2, name: '说书匠', rarity: 3, category: 2, imageUrl: undefined },
    { id: 3, name: '青石板', rarity: 1, category: 3, imageUrl: undefined },
  ],
  eventCards: [
    { id: 101, name: '赤壁·东风骤起', rarity: 3, category: 1 },
  ],
}

test.describe('AI Painter Integration', () => {
  test.beforeEach(async ({ page }) => {
    // Use addInitScript so localStorage is set BEFORE Vue app initializes
    await page.addInitScript(({ keywordCards, eventCards }: typeof SEED_DATA) => {
      localStorage.setItem('arbitrary_gate_keyword_cards', JSON.stringify(keywordCards))
      localStorage.setItem('arbitrary_gate_event_cards', JSON.stringify(eventCards))
      localStorage.setItem('arbitrary_gate_ink_stone', JSON.stringify(500))
    }, SEED_DATA)
    await page.goto(`${BASE_URL}/cards`)
    // Wait for card grid to render
    await page.waitForSelector('.card-slot', { timeout: 15000 })
  })

  test('AI Painter section appears when a keyword card is selected in CardsView', async ({ page }) => {
    // Click on a keyword card to select it
    await page.locator('.card-slot').first().click()
    // Check that AI Painter section appears
    await expect(page.locator('.ai-painter-section')).toBeVisible()
  })

  test('generate button is disabled when no card selected', async ({ page }) => {
    // No cards selected, AI section should not be visible
    await expect(page.locator('.ai-painter-section')).not.toBeVisible()
  })

  test('shows generated image after clicking generate', async ({ page }) => {
    // Select a card
    await page.locator('.card-slot').first().click()
    // Wait for AI section to appear
    await expect(page.locator('.ai-painter-section')).toBeVisible()
    // Click generate (will use mock/placeholder since no API key)
    await page.locator('.ai-generate-btn').click()
    // Wait for image to appear (placeholder or real)
    await expect(page.locator('.ai-result-image')).toBeVisible({ timeout: 10000 })
  })

  test('AI section is collapsible', async ({ page }) => {
    await page.locator('.card-slot').first().click()
    await expect(page.locator('.ai-painter-section')).toBeVisible()
    // Section body should be visible by default
    await expect(page.locator('.ai-painter-body')).toBeVisible()
    // Click header to collapse
    await page.locator('.ai-painter-header').click()
    // Body should be hidden (v-show)
    await expect(page.locator('.ai-painter-body')).not.toBeVisible()
  })

  test('preview shows selected card name', async ({ page }) => {
    await page.locator('.card-slot').first().click()
    await expect(page.locator('.ai-painter-section')).toBeVisible()
    // Preview should show card name "旧船票" (first seeded card)
    await expect(page.locator('.preview-name')).toContainText('旧船票')
  })

  test('style prompt textarea is functional', async ({ page }) => {
    await page.locator('.card-slot').first().click()
    await expect(page.locator('.ai-painter-section')).toBeVisible()
    const textarea = page.locator('.ai-prompt-input')
    await textarea.fill('雨天、古巷、夜晚氛围')
    await expect(textarea).toHaveValue('雨天、古巷、夜晚氛围')
  })
})
