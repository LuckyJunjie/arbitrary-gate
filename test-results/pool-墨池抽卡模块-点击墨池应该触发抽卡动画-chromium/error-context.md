# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: pool.spec.ts >> 墨池抽卡模块 >> 点击墨池应该触发抽卡动画
- Location: tests/e2e/pool.spec.ts:80:3

# Error details

```
TimeoutError: page.waitForSelector: Timeout 5000ms exceeded.
Call log:
  - waiting for locator('[data-testid="ink-ripple-animation"]') to be visible
    15 × locator resolved to hidden <div data-v-fc14a26f="" data-v-bbc97cbf="" data-testid="ink-ripple-animation">…</div>

```

# Page snapshot

```yaml
- generic [ref=e3]:
  - generic [ref=e4]:
    - generic [ref=e5]: 游客模式 ·
    - button "获取关键词" [ref=e6] [cursor=pointer]
  - generic [ref=e7]:
    - banner [ref=e8]:
      - heading "墨池" [level=2] [ref=e9]
      - generic [ref=e10]:
        - generic [ref=e11]: 今日免费
        - generic [ref=e12]: 3/3
    - generic [ref=e13]:
      - button "轻触墨池 · 抽取关键词" [active] [ref=e14] [cursor=pointer]:
        - generic: 轻触墨池 · 抽取关键词
      - button "轻触墨池抽取" [ref=e17] [cursor=pointer]
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
  17  | const BASE_URL = process.env.E2E_BASE_URL || 'http://localhost:5173'
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
  30  |     // 墨晶余额
  31  |     localStorage.setItem('inkStone', '500') // 初始500墨晶
  32  |   }, userId)
  33  | }
  34  | 
  35  | async function getInkStone(page: any): Promise<number> {
  36  |   return page.evaluate(() => {
  37  |     return parseInt(localStorage.getItem('inkStone') || '0')
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
  57  |     await page.goto(`${BASE_URL}/pool`)
  58  |     await loginAsUser(page)
  59  |   })
  60  | 
  61  |   test('墨池页面应该正确加载', async ({ page }) => {
  62  |     // 等待墨池动画容器加载
  63  |     await expect(page.locator('[data-testid="ink-pool-container"]')).toBeVisible({ timeout: 10000 })
  64  | 
  65  |     // 检查墨池标题
  66  |     await expect(page.locator('[data-testid="ink-pool-title"]')).toContainText('墨池')
  67  | 
  68  |     // 检查免费抽卡次数显示
  69  |     const freeDrawsText = await page.locator('[data-testid="free-draws-count"]').textContent()
  70  |     expect(freeDrawsText).toMatch(/\d+\/\d+/)
  71  |   })
  72  | 
  73  |   test('每日免费抽卡次数应该正确显示', async ({ page }) => {
  74  |     const freeDraws = await getTodayFreeDraws(page)
  75  | 
  76  |     // 应该显示3/3（每日免费3次）
  77  |     await expect(page.locator('[data-testid="free-draws-count"]')).toContainText('3/3')
  78  |   })
  79  | 
  80  |   test('点击墨池应该触发抽卡动画', async ({ page }) => {
  81  |     // 监听抽卡开始的动画
> 82  |     const animationPromise = page.waitForSelector(
      |                                   ^ TimeoutError: page.waitForSelector: Timeout 5000ms exceeded.
  83  |       '[data-testid="ink-ripple-animation"]',
  84  |       { state: 'visible', timeout: 5000 }
  85  |     )
  86  | 
  87  |     // 点击墨池
  88  |     await page.locator('[data-testid="ink-pool-surface"]').click()
  89  | 
  90  |     // 等待涟漪动画出现
  91  |     await animationPromise
  92  | 
  93  |     // 验证动画元素可见
  94  |     await expect(page.locator('[data-testid="ink-ripple-animation"]')).toBeVisible()
  95  |   })
  96  | 
  97  |   test('抽卡完成后卡片应该正确展示', async ({ page }) => {
  98  |     // 点击墨池触发抽卡
  99  |     await page.locator('[data-testid="ink-pool-surface"]').click()
  100 | 
  101 |     // 等待卡片展示（动画结束后）
  102 |     await page.waitForSelector('[data-testid="card-reveal-container"]', { state: 'visible', timeout: 15000 })
  103 | 
  104 |     // 验证卡片信息展示
  105 |     await expect(page.locator('[data-testid="card-name"]')).toBeVisible()
  106 |     await expect(page.locator('[data-testid="card-rarity"]')).toBeVisible()
  107 | 
  108 |     // 验证稀有度标签正确显示
  109 |     const rarityBadge = page.locator('[data-testid="card-rarity-badge"]')
  110 |     await expect(rarityBadge).toBeVisible()
  111 |     const rarityText = await rarityBadge.textContent()
  112 |     expect(['凡', '珍', '奇', '绝']).toContain(rarityText)
  113 |   })
  114 | 
  115 |   test('墨晶消耗应该在免费次数用完后正确计算', async ({ page }) => {
  116 |     // 消耗免费次数
  117 |     for (let i = 0; i < 3; i++) {
  118 |       await page.locator('[data-testid="ink-pool-surface"]').click()
  119 |       await page.waitForTimeout(2000) // 等待动画完成
  120 | 
  121 |       // 关闭结果弹窗（如果有）
  122 |       const closeBtn = page.locator('[data-testid="card-modal-close"]')
  123 |       if (await closeBtn.isVisible()) {
  124 |         await closeBtn.click()
  125 |         await page.waitForTimeout(500)
  126 |       }
  127 |     }
  128 | 
  129 |     const inkStone = await getInkStone(page)
  130 | 
  131 |     // 免费次数用完后，墨晶应该未消耗
  132 |     expect(inkStone).toBe(500)
  133 | 
  134 |     // 第四次抽卡应该消耗墨晶
  135 |     await page.locator('[data-testid="ink-pool-surface"]').click()
  136 |     await page.waitForTimeout(2000)
  137 | 
  138 |     const inkStoneAfter = await getInkStone(page)
  139 |     expect(inkStoneAfter).toBe(490) // 消耗10墨晶
  140 |   })
  141 | 
  142 |   test('保底机制应该正确触发（模拟连续9次凡品）', async ({ page }) => {
  143 |     // 注入测试数据：模拟保底计数
  144 |     await page.evaluate(() => {
  145 |       localStorage.setItem('testMode', 'true')
  146 |       localStorage.setItem('qiPityCounter', '9')
  147 |       localStorage.setItem('forceRarity', '3') // 强制第10次出奇品
  148 |     })
  149 | 
  150 |     // 重新加载页面使设置生效
  151 |     await page.reload()
  152 |     await loginAsUser(page)
  153 | 
  154 |     // 注入保底数据
  155 |     await page.evaluate(() => {
  156 |       localStorage.setItem('testMode', 'true')
  157 |       localStorage.setItem('qiPityCounter', '9')
  158 |     })
  159 | 
  160 |     // 点击抽卡
  161 |     await page.locator('[data-testid="ink-pool-surface"]').click()
  162 |     await page.waitForTimeout(3000)
  163 | 
  164 |     // 验证保底提示出现
  165 |     const pityToast = page.locator('[data-testid="pity-triggered-toast"]')
  166 |     await expect(pityToast).toBeVisible({ timeout: 5000 })
  167 |     await expect(pityToast).toContainText('共鸣触发')
  168 |   })
  169 | })
  170 | 
  171 | test.describe('墨池抽卡 - 保底机制详细测试', () => {
  172 | 
  173 |   test('连续9次未出奇品，第10次必出奇品', async ({ page }) => {
  174 |     await page.goto(`${BASE_URL}/pool`)
  175 |     await loginAsUser(page)
  176 | 
  177 |     // 注入保底计数器（模拟已抽9次凡品）
  178 |     await page.evaluate(() => {
  179 |       localStorage.setItem('qiPityCounter', '9')
  180 |       localStorage.setItem('testForceNextRarity', '3')
  181 |     })
  182 | 
```