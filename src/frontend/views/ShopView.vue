<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { createPayOrder, invokeWxPay } from '@/services/api'

// ── 套餐定义 ──────────────────────────────────────────────────────────────────
interface Package {
  id: string
  name: string
  inkStone: number
  price: number
  tag?: string
  tagColor?: string
}

const packages: Package[] = [
  { id: 'inkstone_10', name: '墨晶10枚', inkStone: 10, price: 6 },
  { id: 'inkstone_50', name: '墨晶50枚', inkStone: 50, price: 28, tag: '限时特惠', tagColor: '#c9a84c' },
  { id: 'inkstone_200', name: '墨晶200枚', inkStone: 200, price: 98, tag: '超值套餐', tagColor: '#8b5e3c' },
]

// ── 状态 ──────────────────────────────────────────────────────────────────────
const router = useRouter()
const selectedPackage = ref<Package | null>(null)
const isPaying = ref(false)
const payStatus = ref<'idle' | 'success' | 'error'>('idle')
const errorMsg = ref('')
const currentInkStone = ref(0) // 当前墨晶数（从 localStorage 读取）

// ── 加载当前墨晶数 ────────────────────────────────────────────────────────────
function loadCurrentInkStone() {
  try {
    const saved = localStorage.getItem('arbitrary_gate_user')
    if (saved) {
      const user = JSON.parse(saved)
      currentInkStone.value = user.inkStone ?? 0
    }
  } catch { /* ignore */ }
}

loadCurrentInkStone()

// ── 选择套餐 ──────────────────────────────────────────────────────────────────
function selectPackage(pkg: Package) {
  if (isPaying.value) return
  selectedPackage.value = selectedPackage.value?.id === pkg.id ? null : pkg
  payStatus.value = 'idle'
  errorMsg.value = ''
}

// ── 充值说明（按充值金额递增折扣）────────────────────────────────────────────
const discountHint = computed(() => {
  if (!selectedPackage.value) return ''
  const { inkStone } = selectedPackage.value
  if (inkStone === 200) return '相当于 ¥0.49/枚，比单买省 ¥2'
  if (inkStone === 50) return '相当于 ¥0.56/枚'
  return '单买 ¥0.60/枚'
})

// ── 发起支付 ──────────────────────────────────────────────────────────────────
async function handlePay() {
  if (!selectedPackage.value || isPaying.value) return
  isPaying.value = true
  payStatus.value = 'idle'
  errorMsg.value = ''

  try {
    // 1. 创建订单
    const orderResp = await createPayOrder({
      packageId: selectedPackage.value.id,
      inkStoneCount: selectedPackage.value.inkStone,
      amount: selectedPackage.value.price,
    })

    // 2. 调起微信支付
    await invokeWxPay(orderResp.payParams)

    // 3. 支付成功（微信自动回调后端，此处仅做前端展示）
    payStatus.value = 'success'

    // 4. 更新本地墨晶数
    currentInkStone.value += selectedPackage.value.inkStone
    try {
      const saved = localStorage.getItem('arbitrary_gate_user')
      if (saved) {
        const user = JSON.parse(saved)
        user.inkStone = currentInkStone.value
        localStorage.setItem('arbitrary_gate_user', JSON.stringify(user))
      }
    } catch { /* ignore */ }

  } catch (err: any) {
    if (err.message === '用户取消支付') {
      // 用户主动取消，不显示错误
      payStatus.value = 'idle'
    } else {
      payStatus.value = 'error'
      errorMsg.value = err?.message || '支付失败，请稍后重试'
    }
  } finally {
    isPaying.value = false
  }
}

function goBack() {
  router.back()
}

function resetAndSelect() {
  payStatus.value = 'idle'
  selectedPackage.value = null
}
</script>

<template>
  <div class="shop-view">
    <!-- 背景墨迹装饰 -->
    <div class="ink-bg">
      <div class="ink-blob ink-blob-1"></div>
      <div class="ink-blob ink-blob-2"></div>
    </div>

    <!-- 顶部导航 -->
    <header class="shop-header">
      <button class="back-btn" @click="goBack">‹</button>
      <div class="header-center">
        <h1>墨晶商店</h1>
        <p class="header-sub">以墨为介，书写命运</p>
      </div>
      <div class="ink-stone-badge">
        <span class="ink-icon">墨</span>
        <span class="ink-count">{{ currentInkStone }}</span>
      </div>
    </header>

    <!-- 充值成功浮层 -->
    <Transition name="fade">
      <div v-if="payStatus === 'success'" class="pay-success-overlay">
        <div class="pay-success-card">
          <div class="success-ink-drop"></div>
          <h2>墨晶已到账</h2>
          <p class="success-detail">
            +{{ selectedPackage?.inkStone }} 墨晶
          </p>
          <div class="current-balance">
            当前余额：<strong>{{ currentInkStone }}</strong> 墨晶
          </div>
          <div class="success-actions">
            <button class="btn-primary" @click="goBack">返回</button>
            <button class="btn-secondary" @click="resetAndSelect">继续充值</button>
          </div>
        </div>
      </div>
    </Transition>

    <!-- 充值失败提示 -->
    <Transition name="fade">
      <div v-if="payStatus === 'error'" class="pay-error-toast">
        <span>⚠ {{ errorMsg }}</span>
        <button class="dismiss-btn" @click="payStatus = 'idle'">✕</button>
      </div>
    </Transition>

    <!-- 套餐列表 -->
    <main class="shop-main">
      <p class="section-title">选择充值套餐</p>

      <div class="package-list">
        <div
          v-for="pkg in packages"
          :key="pkg.id"
          :class="[
            'package-card',
            {
              'package-selected': selectedPackage?.id === pkg.id,
              'package-disabled': isPaying,
            }
          ]"
          @click="selectPackage(pkg)"
        >
          <!-- 标签 -->
          <div
            v-if="pkg.tag"
            class="package-tag"
            :style="{ background: pkg.tagColor }"
          >{{ pkg.tag }}</div>

          <!-- 墨晶图标 -->
          <div class="package-ink-visual">
            <div class="ink-crystal" :style="{ '--size': `${20 + pkg.inkStone / 10}px` }">
              <span class="crystal-count">{{ pkg.inkStone }}</span>
            </div>
          </div>

          <!-- 文字信息 -->
          <div class="package-info">
            <div class="package-name">{{ pkg.name }}</div>
            <div class="package-price">¥{{ pkg.price.toFixed(2) }}</div>
            <div class="package-unit">≈ ¥{{ (pkg.price / pkg.inkStone).toFixed(2) }}/枚</div>
          </div>

          <!-- 选中状态 -->
          <div v-if="selectedPackage?.id === pkg.id" class="selected-indicator">
            <span>✓</span>
          </div>
        </div>
      </div>

      <!-- 充值说明 -->
      <div v-if="selectedPackage" class="discount-hint">
        {{ discountHint }}
      </div>

      <!-- 充值按钮 -->
      <button
        class="pay-btn"
        :disabled="!selectedPackage || isPaying"
        @click="handlePay"
      >
        <span v-if="isPaying" class="loading-dots">
          <span></span><span></span><span></span>
        </span>
        <span v-else-if="selectedPackage">
          微信支付 ¥{{ selectedPackage.price.toFixed(2) }}
        </span>
        <span v-else>请选择套餐</span>
      </button>

      <!-- 底部说明 -->
      <div class="shop-footer">
        <p class="footer-note">
          · 墨晶用于抽取命运卡牌<br>
          · 支付成功后墨晶即时到账<br>
          · 如有疑问请联系客服
        </p>
      </div>
    </main>
  </div>
</template>

<style scoped>
.shop-view {
  min-height: 100vh;
  background: linear-gradient(160deg, #f5efe6 0%, #e8dfd0 60%, #d9cdb8 100%);
  position: relative;
  overflow: hidden;
  font-family: 'Georgia', 'SimSun', serif;
}

/* ── 背景墨迹 ── */
.ink-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
  overflow: hidden;
}

.ink-blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(60px);
  opacity: 0.08;
  background: #2c2c2a;
}

.ink-blob-1 {
  width: 300px;
  height: 300px;
  top: -80px;
  right: -60px;
}

.ink-blob-2 {
  width: 200px;
  height: 200px;
  bottom: 100px;
  left: -40px;
}

/* ── 顶部导航 ── */
.shop-header {
  position: relative;
  display: flex;
  align-items: center;
  padding: 1rem 1.25rem;
  padding-top: max(1rem, env(safe-area-inset-top));
  background: linear-gradient(180deg, rgba(245, 239, 230, 0.95) 0%, rgba(245, 239, 230, 0) 100%);
  backdrop-filter: blur(8px);
  z-index: 10;
}

.back-btn {
  width: 36px;
  height: 36px;
  background: rgba(139, 115, 85, 0.12);
  border: 1px solid rgba(139, 115, 85, 0.25);
  border-radius: 50%;
  color: #4a3520;
  font-size: 1.4rem;
  line-height: 1;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: background 0.2s;
}

.back-btn:hover {
  background: rgba(139, 115, 85, 0.22);
}

.header-center {
  flex: 1;
  text-align: center;
}

.header-center h1 {
  font-size: 1.1rem;
  font-weight: 700;
  color: #2c1f14;
  letter-spacing: 0.12em;
  margin: 0;
}

.header-sub {
  font-size: 0.72rem;
  color: #8b7355;
  margin: 0.1rem 0 0;
  letter-spacing: 0.08em;
}

.ink-stone-badge {
  display: flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.3rem 0.7rem;
  background: rgba(44, 44, 42, 0.08);
  border: 1px solid rgba(139, 115, 85, 0.2);
  border-radius: 20px;
  flex-shrink: 0;
}

.ink-icon {
  font-size: 0.75rem;
  color: #2c2c2a;
  font-weight: 700;
  font-family: 'Georgia', serif;
}

.ink-count {
  font-size: 0.85rem;
  color: #2c2c2a;
  font-weight: 600;
}

/* ── 主内容 ── */
.shop-main {
  position: relative;
  z-index: 1;
  padding: 1.5rem 1.25rem 2rem;
}

.section-title {
  font-size: 0.85rem;
  color: #8b7355;
  letter-spacing: 0.1em;
  margin: 0 0 1rem;
  padding-left: 0.25rem;
}

/* ── 套餐卡片 ── */
.package-list {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
}

.package-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1rem 1.1rem;
  background: rgba(255, 255, 255, 0.7);
  border: 1.5px solid rgba(139, 115, 85, 0.2);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.22s ease;
  overflow: hidden;
}

.package-card::before {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, rgba(201, 168, 76, 0.06) 0%, transparent 60%);
  opacity: 0;
  transition: opacity 0.22s;
}

.package-card:hover::before {
  opacity: 1;
}

.package-card:hover {
  border-color: rgba(139, 115, 85, 0.4);
  transform: translateY(-1px);
  box-shadow: 0 4px 16px rgba(44, 31, 20, 0.08);
}

.package-selected {
  border-color: #c9a84c;
  background: rgba(255, 250, 238, 0.85);
  box-shadow: 0 0 0 3px rgba(201, 168, 76, 0.15), 0 4px 16px rgba(44, 31, 20, 0.1);
}

.package-selected::before {
  opacity: 1;
}

.package-disabled {
  opacity: 0.6;
  pointer-events: none;
}

/* ── 套餐标签 ── */
.package-tag {
  position: absolute;
  top: 0;
  right: 12px;
  padding: 0.15rem 0.5rem;
  font-size: 0.65rem;
  color: #fff;
  border-radius: 0 0 4px 4px;
  letter-spacing: 0.05em;
}

/* ── 墨晶视觉 ── */
.package-ink-visual {
  flex-shrink: 0;
  width: 52px;
  height: 52px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.ink-crystal {
  width: var(--size, 40px);
  height: var(--size, 40px);
  background: linear-gradient(145deg, #3a3a38 0%, #1a1a18 100%);
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow:
    0 2px 8px rgba(0, 0, 0, 0.3),
    inset 0 1px 0 rgba(255, 255, 255, 0.1);
  transform: rotate(15deg);
  position: relative;
}

.ink-crystal::after {
  content: '';
  position: absolute;
  top: 2px;
  left: 2px;
  width: 40%;
  height: 40%;
  background: rgba(255, 255, 255, 0.12);
  border-radius: 2px;
}

.crystal-count {
  font-size: calc(var(--size, 40px) * 0.32);
  color: #c9a84c;
  font-weight: 700;
  letter-spacing: -0.02em;
  transform: rotate(-15deg);
  display: block;
}

/* ── 套餐文字信息 ── */
.package-info {
  flex: 1;
}

.package-name {
  font-size: 1rem;
  color: #2c1f14;
  font-weight: 600;
  letter-spacing: 0.04em;
  margin-bottom: 0.2rem;
}

.package-price {
  font-size: 1.2rem;
  color: #2c2c2a;
  font-weight: 700;
  letter-spacing: -0.01em;
}

.package-unit {
  font-size: 0.72rem;
  color: #8b7355;
  margin-top: 0.1rem;
}

/* ── 选中指示器 ── */
.selected-indicator {
  width: 22px;
  height: 22px;
  background: #c9a84c;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 0.75rem;
  flex-shrink: 0;
}

/* ── 折扣提示 ── */
.discount-hint {
  margin-top: 0.75rem;
  padding: 0.4rem 0.75rem;
  background: rgba(201, 168, 76, 0.1);
  border: 1px solid rgba(201, 168, 76, 0.25);
  border-radius: 4px;
  font-size: 0.78rem;
  color: #8b6e3c;
  text-align: center;
  letter-spacing: 0.03em;
}

/* ── 支付按钮 ── */
.pay-btn {
  width: 100%;
  margin-top: 1.25rem;
  padding: 0.9rem;
  background: linear-gradient(135deg, #4a3520 0%, #2c1f14 100%);
  border: 1px solid #c9a84c;
  border-radius: 6px;
  color: #c9a84c;
  font-family: inherit;
  font-size: 1rem;
  font-weight: 600;
  letter-spacing: 0.12em;
  cursor: pointer;
  transition: all 0.22s;
  position: relative;
  overflow: hidden;
}

.pay-btn::before {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, rgba(201, 168, 76, 0.1) 0%, transparent 60%);
}

.pay-btn:hover:not(:disabled) {
  background: linear-gradient(135deg, #6b4c30 0%, #4a3520 100%);
  border-color: #e8dcc8;
  color: #e8dcc8;
  transform: translateY(-1px);
  box-shadow: 0 6px 20px rgba(44, 31, 20, 0.2);
}

.pay-btn:active:not(:disabled) {
  transform: translateY(0);
}

.pay-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 加载动画 */
.loading-dots {
  display: inline-flex;
  gap: 4px;
  align-items: center;
}

.loading-dots span {
  width: 6px;
  height: 6px;
  background: #c9a84c;
  border-radius: 50%;
  animation: dot-bounce 1.2s infinite;
}

.loading-dots span:nth-child(2) { animation-delay: 0.2s; }
.loading-dots span:nth-child(3) { animation-delay: 0.4s; }

@keyframes dot-bounce {
  0%, 80%, 100% { transform: scale(0.8); opacity: 0.5; }
  40% { transform: scale(1.2); opacity: 1; }
}

/* ── 底部说明 ── */
.shop-footer {
  margin-top: 1.5rem;
  text-align: center;
}

.footer-note {
  font-size: 0.72rem;
  color: rgba(139, 115, 85, 0.7);
  line-height: 1.8;
  letter-spacing: 0.03em;
}

/* ── 支付成功浮层 ── */
.pay-success-overlay {
  position: fixed;
  inset: 0;
  background: rgba(44, 31, 20, 0.6);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
  padding: 1rem;
}

.pay-success-card {
  background: linear-gradient(160deg, #faf6ee 0%, #f0e6d2 100%);
  border: 1px solid rgba(201, 168, 76, 0.4);
  border-radius: 12px;
  padding: 2rem 1.5rem;
  text-align: center;
  max-width: 320px;
  width: 100%;
  position: relative;
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(44, 31, 20, 0.3);
}

.success-ink-drop {
  width: 50px;
  height: 50px;
  background: radial-gradient(circle at 35% 35%, #4a4a48, #1a1a18);
  border-radius: 50% 50% 50% 50% / 60% 60% 40% 40%;
  margin: 0 auto 1rem;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
  animation: ink-drop 0.6s ease-out;
}

@keyframes ink-drop {
  0% { transform: scale(0) translateY(-20px); opacity: 0; }
  60% { transform: scale(1.2) translateY(0); }
  100% { transform: scale(1) translateY(0); opacity: 1; }
}

.pay-success-card h2 {
  font-size: 1.3rem;
  color: #2c1f14;
  letter-spacing: 0.15em;
  margin: 0 0 0.5rem;
  font-weight: 700;
}

.success-detail {
  font-size: 2rem;
  color: #c9a84c;
  font-weight: 700;
  margin: 0.5rem 0;
  letter-spacing: 0.05em;
}

.current-balance {
  font-size: 0.82rem;
  color: #8b7355;
  margin-bottom: 1.5rem;
}

.current-balance strong {
  color: #2c1f14;
}

.success-actions {
  display: flex;
  gap: 0.75rem;
}

.btn-primary {
  flex: 1;
  padding: 0.7rem;
  background: linear-gradient(135deg, #4a3520, #2c1f14);
  border: 1px solid #c9a84c;
  border-radius: 4px;
  color: #c9a84c;
  font-family: inherit;
  font-size: 0.88rem;
  font-weight: 600;
  letter-spacing: 0.08em;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-primary:hover {
  background: linear-gradient(135deg, #6b4c30, #4a3520);
  color: #e8dcc8;
}

.btn-secondary {
  flex: 1;
  padding: 0.7rem;
  background: transparent;
  border: 1px solid rgba(139, 115, 85, 0.3);
  border-radius: 4px;
  color: #8b7355;
  font-family: inherit;
  font-size: 0.88rem;
  letter-spacing: 0.05em;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-secondary:hover {
  border-color: rgba(139, 115, 85, 0.6);
  color: #4a3520;
}

/* ── 错误提示 ── */
.pay-error-toast {
  position: fixed;
  top: max(1rem, env(safe-area-inset-top));
  left: 50%;
  transform: translateX(-50%);
  z-index: 100;
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.6rem 1rem;
  background: rgba(60, 20, 10, 0.92);
  color: #f5e6d0;
  border: 1px solid rgba(201, 168, 76, 0.3);
  border-radius: 6px;
  font-size: 0.82rem;
  max-width: 90vw;
  backdrop-filter: blur(8px);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.25);
}

.dismiss-btn {
  background: none;
  border: none;
  color: rgba(245, 230, 208, 0.6);
  cursor: pointer;
  font-size: 0.8rem;
  padding: 0;
  line-height: 1;
}

.dismiss-btn:hover {
  color: #f5e6d0;
}

/* ── 过渡动画 ── */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
