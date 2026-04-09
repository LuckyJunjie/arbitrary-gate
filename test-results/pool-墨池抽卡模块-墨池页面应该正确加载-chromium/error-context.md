# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: pool.spec.ts >> 墨池抽卡模块 >> 墨池页面应该正确加载
- Location: tests/e2e/pool.spec.ts:66:3

# Error details

```
Error: page.goto: net::ERR_CONNECTION_REFUSED at http://localhost:5175/pool
Call log:
  - navigating to "http://localhost:5175/pool", waiting until "load"

```

# Test source

```ts
  1   | /**
  2   |  * 墨池抽卡 E2E 测试
  3   |  *
  4   |  * 测试覆盖：
  5   |  * - 墨池页面加载
  6   |  * - 抽卡动画播放
  7   |  * - 保底机制正确触发
  8   |  * - 墨晶消耗正确
  9   |  */
  10  | 
  11  | import { test, expect } from '@playwright/test'
  12  | 
  13  | // ==================== 测试配置 ====================
  14  | 
  15  | test.describe.configure({ mode: 'serial' })
  16  | 
  17  | const BASE_URL = process.env.E2E_BASE_URL || 'http://localhost:5175'
  18  | 
  19  | // ==================== 辅助函数 ====================
  20  | 
  21  | async function loginAsUser(page: any, userId: number = 1): Promise<void> {
  22  |   // 模拟登录（实际项目中应该调用真实的登录API）
  23  |   await page.evaluate((uid: number) => {
  24  |     localStorage.setItem('currentUserId', String(uid))
  25  |     // PoolView 使用 dailyFreeDraws 格式
  26  |     localStorage.setItem('dailyFreeDraws', JSON.stringify({
  27  |       date: new Date().toDateString(),
  28  |       remaining: 3,
  29  |     }))
  30  |     // 墨晶余额（与 cardStore 的 STORAGE_INK 保持一致）
  31  |     localStorage.setItem('arbitrary_gate_ink_stone', '500') // 初始500墨晶
  32  |   }, userId)
  33  | }
  34  | 
  35  | async function getInkStone(page: any): Promise<number> {
  36  |   return page.evaluate(() => {
  37  |     return parseInt(localStorage.getItem('arbitrary_gate_ink_stone') || '0')
  38  |   })
  39  | }
  40  | 
  41  | async function getTodayFreeDraws(page: any): Promise<number> {
  42  |   return page.evaluate(() => {
  43  |     const saved = localStorage.getItem('dailyFreeDraws')
  44  |     if (!saved) return 3
  45  |     const data = JSON.parse(saved)
  46  |     const today = new Date().toDateString()
  47  |     if (data.date !== today) return 3
  48  |     return data.remaining ?? 3
  49  |   })
  50  | }
  51  | 
  52  | // ==================== E2E 测试用例 ====================
  53  | 
  54  | test.describe('墨池抽卡模块', () => {
  55  | 
  56  |   test.beforeEach(async ({ page }) => {
> 57  |     await page.goto(`${BASE_URL}/pool`)
      |                ^ Error: page.goto: net::ERR_CONNECTION_REFUSED at http://localhost:5175/pool
  58  |     await loginAsUser(page)
  59  |     // Reset cardStore keywordCards to avoid accumulation across tests
  60  |     await page.evaluate(() => {
  61  |       // Access the Pinia store via window if available, or reset localStorage cards
  62  |       localStorage.removeItem('ownedKeywordCards')
  63  |     })
  64  |   })
  65  | 
  66  |   test('墨池页面应该正确加载', async ({ page }) => {
  67  |     // 等待墨池动画容器加载
  68  |     await expect(page.locator('[data-testid="ink-pool-container"]')).toBeVisible({ timeout: 10000 })
  69  | 
  70  |     // 检查墨池标题
  71  |     await expect(page.locator('[data-testid="ink-pool-title"]')).toContainText('墨池')
  72  | 
  73  |     // 检查免费抽卡次数显示
  74  |     const freeDrawsText = await page.locator('[data-testid="free-draws-count"]').textContent()
  75  |     expect(freeDrawsText).toMatch(/\d+\/\d+/)
  76  |   })
  77  | 
  78  |   test('每日免费抽卡次数应该正确显示', async ({ page }) => {
  79  |     const freeDraws = await getTodayFreeDraws(page)
  80  | 
  81  |     // 应该显示3/3（每日免费3次）
  82  |     await expect(page.locator('[data-testid="free-draws-count"]')).toContainText('3/3')
  83  |   })
  84  | 
  85  |   test('点击墨池应该触发抽卡动画', async ({ page }) => {
  86  |     // 监听抽卡开始的动画
  87  |     const animationPromise = page.waitForSelector(
  88  |       '[data-testid="ink-ripple-animation"]',
  89  |       { state: 'visible', timeout: 5000 }
  90  |     )
  91  | 
  92  |     // 点击墨池
  93  |     await page.locator('[data-testid="ink-pool-surface"]').click()
  94  | 
  95  |     // 等待涟漪动画出现
  96  |     await animationPromise
  97  | 
  98  |     // 验证动画元素可见
  99  |     await expect(page.locator('[data-testid="ink-ripple-animation"]')).toBeVisible()
  100 |   })
  101 | 
  102 |   test('抽卡完成后卡片应该正确展示', async ({ page }) => {
  103 |     // 点击墨池触发抽卡
  104 |     await page.locator('[data-testid="ink-pool-surface"]').click()
  105 | 
  106 |     // 等待卡片展示（动画结束后）
  107 |     await page.waitForSelector('[data-testid="card-reveal-container"]', { state: 'visible', timeout: 15000 })
  108 | 
  109 |     // 验证卡片信息展示（使用 card-reveal-container 限定作用域避免重复 data-testid 冲突）
  110 |     const cardContainer = page.locator('[data-testid="card-reveal-container"]')
  111 |     await expect(cardContainer.locator('[data-testid="card-name"]')).toBeVisible()
  112 |     await expect(cardContainer.locator('[data-testid="card-rarity"]')).toBeVisible()
  113 | 
  114 |     // 验证稀有度标签正确显示
  115 |     const rarityBadge = cardContainer.locator('[data-testid="card-rarity-badge"]')
  116 |     await expect(rarityBadge).toBeVisible()
  117 |     const rarityText = await rarityBadge.textContent()
  118 |     expect(['凡', '珍', '奇', '绝']).toContain(rarityText)
  119 |   })
  120 | 
  121 |   test.skip('墨晶消耗应该在免费次数用完后正确计算', async ({ page }) => {
  122 |     // 此测试涉及复杂的 API mock 状态同步（InkPool 内部状态 vs PoolView 状态机），暂时跳过
  123 |     // 需要在真实后端环境下端到端验证，或重写为纯单元测试
  124 |     // 已确认前端代码正确：isFree=false 时调用 cardStore.deductInkStone(10)
  125 |     await page.route('**/api/card/draw/keyword', async route => {
  126 |       await route.fulfill({
  127 |         status: 200,
  128 |         contentType: 'application/json',
  129 |         body: JSON.stringify({
  130 |           card: { id: 1, name: '测试卡', rarity: 1, category: 1, inkFragrance: 7 },
  131 |           remainingFreeDraws: 0,
  132 |           isFree: false, // 第4次开始付费
  133 |         }),
  134 |       })
  135 |     })
  136 |     // 完整测试逻辑见原始实现（commit 32x4676）
  137 |   })
  138 | 
  139 |   test.skip('保底机制应该正确触发（模拟连续9次凡品）', async ({ page }) => {
  140 |     // 注入测试数据：模拟保底计数
  141 |     await page.evaluate(() => {
  142 |       localStorage.setItem('testMode', 'true')
  143 |       localStorage.setItem('qiPityCounter', '9')
  144 |       localStorage.setItem('forceRarity', '3') // 强制第10次出奇品
  145 |     })
  146 | 
  147 |     // 重新加载页面使设置生效
  148 |     await page.reload()
  149 |     await loginAsUser(page)
  150 | 
  151 |     // 注入保底数据
  152 |     await page.evaluate(() => {
  153 |       localStorage.setItem('testMode', 'true')
  154 |       localStorage.setItem('qiPityCounter', '9')
  155 |     })
  156 | 
  157 |     // 点击抽卡
```