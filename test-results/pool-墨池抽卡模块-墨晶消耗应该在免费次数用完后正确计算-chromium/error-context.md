# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: pool.spec.ts >> 墨池抽卡模块 >> 墨晶消耗应该在免费次数用完后正确计算
- Location: tests/e2e/pool.spec.ts:121:3

# Error details

```
Error: expect(received).toBe(expected) // Object.is equality

Expected: 490
Received: 500
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
        - generic [ref=e11]:
          - generic [ref=e12]:
            - generic [ref=e13]: 墨香
            - generic [ref=e14]: 入墨
            - generic [ref=e15]: Lv.2
          - generic [ref=e18]:
            - generic [ref=e19]: "220"
            - generic [ref=e20]: 距 渐墨 还差 80 点
        - generic [ref=e21]:
          - generic [ref=e22]: 今日免费
          - generic [ref=e23]: 0/3
    - generic [ref=e25]:
      - button "笺 时光笺 说 说书人 绝 称谓 墨香 点击卡面翻转" [ref=e26] [cursor=pointer]:
        - generic [ref=e28]:
          - generic [ref=e29]: 笺
          - generic [ref=e30]: 时光笺
        - generic [ref=e31]:
          - generic [ref=e33]: 说
          - generic [ref=e34]: 说书人
          - generic [ref=e35]:
            - generic [ref=e36]: 绝
            - generic [ref=e37]: 称谓
          - generic [ref=e39]: 墨香
          - generic [ref=e48]: 点击卡面翻转
      - button "再抽一张" [ref=e49] [cursor=pointer]
    - generic [ref=e50]:
      - heading "已收集 (4)" [level=3] [ref=e51]
      - generic [ref=e52]:
        - generic [ref=e53]:
          - generic [ref=e54]: 春雷
          - generic [ref=e55]: 绝
        - generic [ref=e56]:
          - generic [ref=e57]: 老邮差
          - generic [ref=e58]: 奇
        - generic [ref=e59]:
          - generic [ref=e60]: 老邮差
          - generic [ref=e61]: 奇
        - generic [ref=e62]:
          - generic [ref=e63]: 说书人
          - generic [ref=e64]: 绝
```

# Test source

```ts
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
  121 |   test('墨晶消耗应该在免费次数用完后正确计算', async ({ page }) => {
  122 |     // 消耗免费次数
  123 |     for (let i = 0; i < 3; i++) {
  124 |       await page.locator('[data-testid="ink-pool-surface"]').click()
  125 |       await page.waitForTimeout(2000) // 等待动画完成
  126 | 
  127 |       // 关闭结果弹窗（如果有）
  128 |       const closeBtn = page.locator('[data-testid="card-modal-close"]')
  129 |       if (await closeBtn.isVisible()) {
  130 |         await closeBtn.click()
  131 |         await page.waitForTimeout(500)
  132 |       }
  133 |     }
  134 | 
  135 |     const inkStone = await getInkStone(page)
  136 | 
  137 |     // 免费次数用完后，墨晶应该未消耗
  138 |     expect(inkStone).toBe(500)
  139 | 
  140 |     // 第四次抽卡应该消耗墨晶
  141 |     await page.locator('[data-testid="ink-pool-surface"]').click()
  142 |     await page.waitForTimeout(2000)
  143 | 
  144 |     const inkStoneAfter = await getInkStone(page)
> 145 |     expect(inkStoneAfter).toBe(490) // 消耗10墨晶
      |                           ^ Error: expect(received).toBe(expected) // Object.is equality
  146 |   })
  147 | 
  148 |   test('保底机制应该正确触发（模拟连续9次凡品）', async ({ page }) => {
  149 |     // 注入测试数据：模拟保底计数
  150 |     await page.evaluate(() => {
  151 |       localStorage.setItem('testMode', 'true')
  152 |       localStorage.setItem('qiPityCounter', '9')
  153 |       localStorage.setItem('forceRarity', '3') // 强制第10次出奇品
  154 |     })
  155 | 
  156 |     // 重新加载页面使设置生效
  157 |     await page.reload()
  158 |     await loginAsUser(page)
  159 | 
  160 |     // 注入保底数据
  161 |     await page.evaluate(() => {
  162 |       localStorage.setItem('testMode', 'true')
  163 |       localStorage.setItem('qiPityCounter', '9')
  164 |     })
  165 | 
  166 |     // 点击抽卡
  167 |     await page.locator('[data-testid="ink-pool-surface"]').click()
  168 |     await page.waitForTimeout(3000)
  169 | 
  170 |     // 验证保底提示出现
  171 |     const pityToast = page.locator('[data-testid="pity-triggered-toast"]')
  172 |     await expect(pityToast).toBeVisible({ timeout: 5000 })
  173 |     await expect(pityToast).toContainText('共鸣触发')
  174 |   })
  175 | })
  176 | 
  177 | test.describe('墨池抽卡 - 保底机制详细测试', () => {
  178 | 
  179 |   test('连续9次未出奇品，第10次必出奇品', async ({ page }) => {
  180 |     await page.goto(`${BASE_URL}/pool`)
  181 |     await loginAsUser(page)
  182 | 
  183 |     // 注入保底计数器（模拟已抽9次凡品）
  184 |     await page.evaluate(() => {
  185 |       localStorage.setItem('qiPityCounter', '9')
  186 |       localStorage.setItem('testForceNextRarity', '3')
  187 |     })
  188 | 
  189 |     // 执行第10次抽卡
  190 |     await page.locator('[data-testid="ink-pool-surface"]').click()
  191 |     await page.waitForTimeout(3000)
  192 | 
  193 |     // 验证结果是奇品
  194 |     const cardRarity = await page.locator('[data-testid="card-rarity-badge"]').textContent()
  195 |     expect(cardRarity).toContain('奇')
  196 |   })
  197 | 
  198 |   test('连续30次未出绝品，第31次必出绝品', async ({ page }) => {
  199 |     await page.goto(`${BASE_URL}/pool`)
  200 |     await loginAsUser(page)
  201 | 
  202 |     // 注入保底计数器（模拟已抽30次非绝品）
  203 |     await page.evaluate(() => {
  204 |       localStorage.setItem('juePityCounter', '30')
  205 |       localStorage.setItem('testForceNextRarity', '4')
  206 |     })
  207 | 
  208 |     // 执行第31次抽卡
  209 |     await page.locator('[data-testid="ink-pool-surface"]').click()
  210 |     await page.waitForTimeout(3000)
  211 | 
  212 |     // 验证结果是绝品
  213 |     const cardRarity = await page.locator('[data-testid="card-rarity-badge"]').textContent()
  214 |     expect(cardRarity).toContain('绝')
  215 |   })
  216 | })
  217 | 
  218 | test.describe('墨池抽卡 - 墨晶管理', () => {
  219 | 
  220 |   test('墨晶不足时应该提示充值', async ({ page }) => {
  221 |     await page.goto(`${BASE_URL}/pool`)
  222 |     await loginAsUser(page)
  223 | 
  224 |     // 设置墨晶为0
  225 |     await page.evaluate(() => {
  226 |       localStorage.setItem('inkStone', '0')
  227 |     })
  228 | 
  229 |     await page.reload()
  230 |     await page.waitForSelector('[data-testid="ink-pool-container"]')
  231 | 
  232 |     // 点击抽卡
  233 |     await page.locator('[data-testid="ink-pool-surface"]').click()
  234 | 
  235 |     // 验证充值提示出现
  236 |     await expect(page.locator('[data-testid="insufficient-ink-modal"]')).toBeVisible({ timeout: 5000 })
  237 |     await expect(page.locator('[data-testid="insufficient-ink-modal"]')).toContainText('墨晶不足')
  238 |   })
  239 | 
  240 |   test('墨晶充足时正常抽卡', async ({ page }) => {
  241 |     await page.goto(`${BASE_URL}/pool`)
  242 |     await loginAsUser(page)
  243 | 
  244 |     // 确保墨晶充足
  245 |     await page.evaluate(() => {
```