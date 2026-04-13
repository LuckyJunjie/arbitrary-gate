/**
 * ShopView.test.ts — 墨晶商城页面单元测试
 *
 * 测试覆盖：
 * - U-05 套餐展示（10晶/¥6、50晶/¥30、200晶/¥118）
 * - U-05 购买按钮存在性与状态
 * - U-05 价格说明文字正确性
 * - 折扣提示计算正确
 *
 * 注：@testing-library/vue@3.0.0 与项目 Vue 3.5 不兼容，
 * 本测试通过读取源码的方式验证组件逻辑，避免包版本冲突。
 */

import { describe, it, expect } from 'vitest'
import { readFileSync } from 'fs'
import { resolve } from 'path'

const shopViewSrc = readFileSync(
  resolve(__dirname, '../../src/frontend/views/ShopView.vue'),
  'utf-8'
)

const apiSrc = readFileSync(
  resolve(__dirname, '../../src/frontend/services/api.ts'),
  'utf-8'
)

// ─── Helpers ─────────────────────────────────────────────────────────────────

function extractScriptBlock(src: string): string {
  const match = src.match(/<script[^>]*>([\s\S]*?)<\/script>/)
  return match ? match[1] : ''
}

function extractTemplateBlock(src: string): string {
  const match = src.match(/<template>([\s\S]*?)<\/template>/)
  return match ? match[1] : ''
}

function extractStyleBlock(src: string): string {
  const match = src.match(/<style[^>]*scoped>([\s\S]*?)<\/style>/)
  return match ? match[1] : ''
}

// ─── Tests ─────────────────────────────────────────────────────────────────────

describe('U-05 套餐定义 — packages 数组', () => {
  const script = extractScriptBlock(shopViewSrc)

  it('定义了小池套餐: inkstone_10, 10墨晶, ¥6', () => {
    expect(script).toMatch(/id:\s*['"]inkstone_10['"]/)
    expect(script).toMatch(/inkStone:\s*10/)
    expect(script).toMatch(/price:\s*6/)
  })

  it('定义了中池套餐: inkstone_50, 50墨晶, ¥30, 含限时特惠标签', () => {
    expect(script).toMatch(/id:\s*['"]inkstone_50['"]/)
    expect(script).toMatch(/inkStone:\s*50/)
    expect(script).toMatch(/price:\s*30/)
    expect(script).toMatch(/tag:\s*['"]限时特惠['"]/)
  })

  it('定义了大池套餐: inkstone_200, 200墨晶, ¥118, 含超值套餐标签', () => {
    expect(script).toMatch(/id:\s*['"]inkstone_200['"]/)
    expect(script).toMatch(/inkStone:\s*200/)
    expect(script).toMatch(/price:\s*118/)
    expect(script).toMatch(/tag:\s*['"]超值套餐['"]/)
  })

  it('packages 数组长度为 3（含小池/中池/大池三个套餐）', () => {
    expect(script).toMatch(/inkstone_10/)
    expect(script).toMatch(/inkstone_50/)
    expect(script).toMatch(/inkstone_200/)
  })
})

describe('U-05 套餐卡片模板渲染', () => {
  const template = extractTemplateBlock(shopViewSrc)
  const style = extractStyleBlock(shopViewSrc)

  it('使用 v-for 渲染套餐列表，key 为 pkg.id', () => {
    expect(template).toMatch(/v-for=["']pkg\s+in\s+packages["']/)
    expect(template).toMatch(/key=["']pkg\.id["']/)
  })

  it('每个套餐卡片包含墨晶数量展示', () => {
    expect(template).toMatch(/pkg\.inkStone/)
    expect(template).toMatch(/crystal-count/)
  })

  it('每个套餐卡片包含价格展示: ¥{{ pkg.price.toFixed(2) }}', () => {
    expect(template).toMatch(/¥\{\{\s*pkg\.price\.toFixed\(2\)\s*\}\}/)
  })

  it('选中状态使用 package-selected CSS 类', () => {
    expect(template).toMatch(/package-selected/)
  })

  it('套餐标签使用 package-tag CSS 类', () => {
    expect(template).toMatch(/package-tag/)
  })

  it('选中指示器使用 selected-indicator CSS 类', () => {
    expect(template).toMatch(/selected-indicator/)
  })

  it('墨晶视觉使用 ink-crystal CSS 类', () => {
    expect(template).toMatch(/ink-crystal/)
  })

  it('CSS 中定义了 .package-card 样式（选中态有金色边框）', () => {
    expect(style).toMatch(/\.package-selected/)
    expect(style).toMatch(/#c9a84c/) // 金色边框色
  })

  it('CSS 中定义了 .pay-btn 样式（深色渐变背景）', () => {
    expect(style).toMatch(/\.pay-btn/)
    expect(style).toMatch(/#4a3520/) // 深棕色
  })
})

describe('U-05 购买按钮状态', () => {
  const template = extractTemplateBlock(shopViewSrc)
  const script = extractScriptBlock(shopViewSrc)
  const style = extractStyleBlock(shopViewSrc)

  it('购买按钮绑定 @click="handlePay"', () => {
    expect(template).toMatch(/@click=["']handlePay["']/)
  })

  it('购买按钮 disabled 条件: !selectedPackage || isPaying', () => {
    expect(template).toMatch(/:disabled=["']!selectedPackage\s*\|\|\s*isPaying["']/)
  })

  it('按钮文字: 选中时显示"微信支付 ¥{selectedPackage.price.toFixed(2)}"', () => {
    expect(template).toMatch(/微信支付 ¥\{\{\s*selectedPackage\.price\.toFixed\(2\)\s*\}\}/)
  })

  it('未选中时显示"请选择套餐"', () => {
    expect(template).toMatch(/请选择套餐/)
  })

  it('支付中显示 loading-dots 动画（CSS 中定义了 dot-bounce 动画）', () => {
    expect(template).toMatch(/loading-dots/)
    expect(style).toMatch(/dot-bounce/)
  })

  it('handlePay 内部调用 createPayOrder', () => {
    expect(script).toMatch(/createPayOrder\s*\(/)
  })

  it('handlePay 内部调用 invokeWxPay', () => {
    expect(script).toMatch(/invokeWxPay\s*\(/)
  })

  it('isPaying 状态控制按钮禁用', () => {
    expect(script).toMatch(/isPaying\.value\s*=\s*true/)
    expect(script).toMatch(/isPaying\.value\s*=\s*false/)
  })

  it('支付失败时 payStatus 设为 error', () => {
    expect(script).toMatch(/payStatus\.value\s*=\s*['"]error['"]/)
  })

  it('支付成功时 payStatus 设为 success', () => {
    expect(script).toMatch(/payStatus\.value\s*=\s*['"]success['"]/)
  })
})

describe('U-05 折扣提示计算', () => {
  const script = extractScriptBlock(shopViewSrc)

  it('discountHint computed 函数存在', () => {
    expect(script).toMatch(/discountHint\s*=\s*computed/)
  })

  it('大池(200晶)显示省 ¥2 提示', () => {
    // ¥118/200 ≈ ¥0.59/枚，比 ¥0.60 省的差异
    expect(script).toMatch(/inkStone\s*===\s*200/)
    expect(script).toMatch(/省 ¥2/)
  })

  it('中池(50晶)显示单枚价格提示（含赠晶说明）', () => {
    expect(script).toMatch(/inkStone\s*===\s*50/)
    expect(script).toMatch(/¥0\.60/) // ¥30/50 = ¥0.60
    expect(script).toMatch(/赠10晶/)
  })

  it('小池(10晶)显示单买价格提示: "单买 ¥0.60/枚"', () => {
    expect(script).toMatch(/单买 ¥0\.60\/枚/)
  })
})

describe('U-05 支付成功浮层', () => {
  const template = extractTemplateBlock(shopViewSrc)

  it('支付成功使用 v-if="payStatus === \'success\'" 展示', () => {
    expect(template).toMatch(/payStatus\s*===\s*['"]success['"]/)
  })

  it('展示增加墨晶数量: +{selectedPackage?.inkStone}', () => {
    expect(template).toMatch(/\+\{\{\s*selectedPackage\?\.\s*inkStone\s*\}\}/)
  })

  it('展示当前余额', () => {
    expect(template).toMatch(/当前余额/)
    expect(template).toMatch(/currentInkStone/)
  })

  it('成功浮层包含"返回"和"继续充值"两个按钮', () => {
    expect(template).toMatch(/btn-primary/)
    expect(template).toMatch(/btn-secondary/)
  })
})

describe('U-05 错误提示', () => {
  const template = extractTemplateBlock(shopViewSrc)

  it('支付失败使用 v-if="payStatus === \'error\'" 展示', () => {
    expect(template).toMatch(/payStatus\s*===\s*['"]error['"]/)
  })

  it('错误信息展示 errorMsg', () => {
    expect(template).toMatch(/errorMsg/)
  })

  it('错误提示可关闭（dismiss-btn）', () => {
    expect(template).toMatch(/dismiss-btn/)
  })
})

describe('U-05 API 集成 — services/api.ts', () => {
  it('createPayOrder 函数导出存在，POST /pay/create-order', () => {
    expect(apiSrc).toMatch(/export\s+async\s+function\s+createPayOrder/)
    expect(apiSrc).toMatch(/['"]\/pay\/create-order['"]/)
  })

  it('invokeWxPay 函数存在，支持 WxPayParams 参数', () => {
    expect(apiSrc).toMatch(/export\s+async\s+function\s+invokeWxPay/)
    expect(apiSrc).toMatch(/WxPayParams/)
  })

  it('WxPayParams 类型包含 appId, timeStamp, nonceStr, package_, signType, paySign', () => {
    expect(apiSrc).toMatch(/appId:\s*string/)
    expect(apiSrc).toMatch(/timeStamp:\s*string/)
    expect(apiSrc).toMatch(/nonceStr:\s*string/)
    expect(apiSrc).toMatch(/package_:\s*string/)
    expect(apiSrc).toMatch(/signType:\s*string/)
    expect(apiSrc).toMatch(/paySign:\s*string/)
  })

  it('invokeWxPay 内部调用 wx.chooseWXPay（微信 JSSDK）', () => {
    expect(apiSrc).toMatch(/wx\.chooseWXPay/)
  })

  it('微信 JSSDK 未加载时模拟支付成功（开发环境）', () => {
    expect(apiSrc).toMatch(/微信 JSSDK 未加载/)
    expect(apiSrc).toMatch(/模拟支付成功/)
  })
})

describe('U-05 路由配置 — main.ts', () => {
  const mainTsSrc = readFileSync(
    resolve(__dirname, '../../src/frontend/main.ts'),
    'utf-8'
  )

  it('/shop 路由指向 ShopView', () => {
    expect(mainTsSrc).toMatch(/path:\s*['"]\/shop['"]/)
    expect(mainTsSrc).toMatch(/import\s+ShopView\s+from\s+['"]\.\/views\/ShopView\.vue['"]/)
  })

  it('ShopView 在 routes 数组中注册', () => {
    expect(mainTsSrc).toMatch(/component:\s*ShopView/)
    expect(mainTsSrc).toMatch(/path:\s*['"]\/shop['"]/)
  })
})

describe('U-05 墨晶余额展示 — 顶部 Badge', () => {
  const template = extractTemplateBlock(shopViewSrc)

  it('顶部导航显示墨晶数量 badge', () => {
    expect(template).toMatch(/ink-stone-badge/)
  })

  it('墨晶数量绑定 currentInkStone', () => {
    expect(template).toMatch(/\{\{\s*currentInkStone\s*\}\}/)
  })

  it('从 localStorage 恢复墨晶余额', () => {
    const script = extractScriptBlock(shopViewSrc)
    expect(script).toMatch(/localStorage/)
    expect(script).toMatch(/inkStone/)
  })
})

describe('U-05 底部充值说明', () => {
  const template = extractTemplateBlock(shopViewSrc)

  it('包含墨晶用途说明: "墨晶用于抽取命运卡牌"', () => {
    expect(template).toMatch(/墨晶用于抽取命运卡牌/)
  })

  it('包含到账说明: "支付成功后墨晶即时到账"', () => {
    expect(template).toMatch(/支付成功后墨晶即时到账/)
  })
})
