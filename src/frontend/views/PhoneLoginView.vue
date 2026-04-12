<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { sendPhoneCode, phoneLogin as phoneLoginApi } from '@/services/api'
import { playBrushTap, playJadeClick } from '@/composables/useSound'

const router = useRouter()

const phone = ref('')
const code = ref('')
const countdown = ref(0) // 0=未发送，>0=倒计时中
const loading = ref(false)
const errorMsg = ref('')

const countdownText = computed(() =>
  countdown.value > 0 ? `${countdown.value}s` : '获取验证码'
)

const canSendCode = computed(() =>
  phone.value.length === 11 && countdown.value === 0 && !loading.value
)

const canLogin = computed(() =>
  phone.value.length === 11 && code.value.length === 6 && !loading.value
)

function isValidPhone(val: string) {
  return /^1[3-9]\d{9}$/.test(val)
}

async function handleSendCode() {
  playJadeClick()
  errorMsg.value = ''
  if (!isValidPhone(phone.value)) {
    errorMsg.value = '请输入正确的手机号'
    return
  }
  try {
    loading.value = true
    await sendPhoneCode(phone.value)
    countdown.value = 60
    const timer = setInterval(() => {
      countdown.value--
      if (countdown.value <= 0) clearInterval(timer)
    }, 1000)
  } catch (e: any) {
    errorMsg.value = e?.response?.data?.message || e?.message || '发送失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function handleLogin() {
  if (!canLogin.value) return
  playBrushTap()
  errorMsg.value = ''
  try {
    loading.value = true
    // U-03: 读取本地存储的游客设备标识，用于账号升级时迁移数据
    const guestDeviceId = localStorage.getItem('guest_device_id') || undefined
    const res = await phoneLoginApi(phone.value, code.value, guestDeviceId)
    // 存储 token 和用户信息
    localStorage.setItem('token', res.token)
    localStorage.setItem('arbitrary_gate_user', JSON.stringify(res.user))

    // U-03: 如果是游客升级成功，清除游客相关的临时 localStorage 数据
    // （保留 token 和用户信息，让用户感觉是同一个账号）
    if (guestDeviceId) {
      localStorage.removeItem('guest_device_id')
      console.info('[PhoneLogin] 游客账号升级成功, userId=', res.user.id, 'guestDeviceId=', guestDeviceId)
    } else {
      console.info('[PhoneLogin] 新用户注册成功, userId=', res.user.id)
    }

    router.replace('/')
  } catch (e: any) {
    errorMsg.value = e?.response?.data?.message || e?.message || '登录失败，请检查验证码'
  } finally {
    loading.value = false
  }
}

function goBack() {
  playJadeClick()
  router.back()
}
</script>

<template>
  <div class="phone-login-view">
    <!-- 背景纹理 -->
    <div class="phone-bg" aria-hidden="true">
      <div class="paper-texture" />
    </div>

    <!-- 顶部导航 -->
    <header class="phone-header">
      <button class="back-btn" @click="goBack" aria-label="返回">
        <span class="back-arrow">‹</span>
      </button>
      <h2 class="phone-title">手机号登录</h2>
      <div class="header-spacer" />
    </header>

    <!-- 登录表单 -->
    <div class="phone-content">
      <!-- Logo / 标题区 -->
      <div class="login-brand">
        <div class="brand-icon">📜</div>
        <p class="brand-slogan">时光笺 · AI说书人</p>
      </div>

      <!-- 手机号输入 -->
      <div class="input-group">
        <label class="input-label">手机号</label>
        <div class="input-row">
          <input
            v-model="phone"
            class="phone-input"
            type="tel"
            maxlength="11"
            placeholder="请输入手机号"
            :disabled="loading"
            autocomplete="tel"
          />
        </div>
      </div>

      <!-- 验证码输入 -->
      <div class="input-group">
        <label class="input-label">验证码</label>
        <div class="input-row code-row">
          <input
            v-model="code"
            class="code-input"
            type="text"
            maxlength="6"
            placeholder="请输入6位验证码"
            :disabled="loading"
            autocomplete="one-time-code"
            inputmode="numeric"
          />
          <button
            class="send-btn"
            :class="{ disabled: !canSendCode, counting: countdown > 0 }"
            :disabled="!canSendCode || loading"
            @click="handleSendCode"
          >
            {{ countdownText }}
          </button>
        </div>
      </div>

      <!-- 错误提示 -->
      <Transition name="error-slide">
        <div v-if="errorMsg" class="error-tip" role="alert">
          <span class="error-dot">⚠️</span>
          <span>{{ errorMsg }}</span>
        </div>
      </Transition>

      <!-- 登录按钮 -->
      <button
        class="login-btn"
        :class="{ loading }"
        :disabled="!canLogin || loading"
        @click="handleLogin"
      >
        <span v-if="loading" class="loading-dots">登录中<span>.</span><span>.</span><span>.</span></span>
        <span v-else>登录 / 注册</span>
      </button>

      <!-- 底部说明 -->
      <p class="login-note">
        未注册手机号将自动创建账号<br/>
        登录即表示同意
        <button class="link-btn" @click="router.push('/settings')">《用户协议》</button>
      </p>
    </div>
  </div>
</template>

<style scoped>
.phone-login-view {
  min-height: 100vh;
  background: radial-gradient(ellipse at center, #1a1510 0%, #0d0a08 100%);
  color: #e8dcc8;
  position: relative;
}

/* 宣纸纹理 */
.phone-bg {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 0;
}
.paper-texture {
  position: absolute;
  inset: 0;
  background-color: #f5efe0;
  background-image:
    radial-gradient(ellipse at 20% 30%, rgba(200, 180, 140, 0.06) 0%, transparent 60%),
    radial-gradient(ellipse at 80% 70%, rgba(180, 160, 120, 0.05) 0%, transparent 50%);
  opacity: 0.9;
}

/* 顶部导航 */
.phone-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1.5rem 1.25rem 1rem;
  border-bottom: 1px solid rgba(201, 169, 110, 0.12);
  position: sticky;
  top: 0;
  background: radial-gradient(ellipse at center, #1a1510 0%, #0d0a08 100%);
  z-index: 10;
}

.back-btn {
  width: 36px;
  height: 36px;
  background: rgba(232, 220, 200, 0.05);
  border: 1px solid rgba(232, 220, 200, 0.12);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 0.2s;
  flex-shrink: 0;
}
.back-btn:hover { background: rgba(232, 220, 200, 0.1); }

.back-arrow {
  font-size: 1.5rem;
  color: #c4a882;
  line-height: 1;
  margin-top: -2px;
}

.phone-title {
  font-size: 1.1rem;
  letter-spacing: 0.25em;
  color: #c4a882;
  font-weight: 400;
}

.header-spacer { width: 36px; }

/* 内容区 */
.phone-content {
  position: relative;
  z-index: 1;
  padding: 2.5rem 1.5rem 2rem;
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
  max-width: 420px;
  margin: 0 auto;
}

/* 品牌区 */
.login-brand {
  text-align: center;
  margin-bottom: 0.5rem;
}
.brand-icon {
  font-size: 3rem;
  margin-bottom: 0.5rem;
}
.brand-slogan {
  font-size: 1.05rem;
  color: #8b7355;
  letter-spacing: 0.15em;
}

/* 输入框组 */
.input-group {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}
.input-label {
  font-size: 0.78rem;
  letter-spacing: 0.18em;
  color: #8b7355;
  text-transform: uppercase;
}
.input-row {
  display: flex;
  gap: 0.5rem;
}
.code-row {
  align-items: stretch;
}

.phone-input,
.code-input {
  flex: 1;
  background: rgba(232, 220, 200, 0.05);
  border: 1px solid rgba(139, 115, 85, 0.4);
  border-radius: 4px;
  padding: 0.85rem 1rem;
  font-size: 1rem;
  color: #e8dcc8;
  font-family: inherit;
  outline: none;
  transition: border-color 0.2s, box-shadow 0.2s;
  width: 100%;
}
.phone-input::placeholder,
.code-input::placeholder {
  color: #5a4a3a;
}
.phone-input:focus,
.code-input:focus {
  border-color: rgba(201, 169, 110, 0.6);
  box-shadow: 0 0 0 2px rgba(201, 169, 110, 0.1);
}
.phone-input:disabled,
.code-input:disabled {
  opacity: 0.5;
}

/* 发送验证码按钮 */
.send-btn {
  background: rgba(201, 169, 110, 0.12);
  border: 1px solid rgba(201, 169, 110, 0.4);
  border-radius: 4px;
  padding: 0 1rem;
  color: #c9a84c;
  font-family: inherit;
  font-size: 0.88rem;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.2s, color 0.2s, border-color 0.2s;
  letter-spacing: 0.05em;
}
.send-btn:hover:not(.disabled) {
  background: rgba(201, 169, 110, 0.2);
  border-color: rgba(201, 169, 110, 0.6);
}
.send-btn.disabled,
.send-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.send-btn.counting {
  color: #8b7355;
  border-color: rgba(139, 115, 85, 0.3);
}

/* 错误提示 */
.error-tip {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.6rem 0.85rem;
  background: rgba(196, 90, 60, 0.12);
  border: 1px solid rgba(196, 90, 60, 0.3);
  border-radius: 4px;
  color: #e8a090;
  font-size: 0.85rem;
}
.error-dot { font-size: 0.9rem; }

.error-slide-enter-active,
.error-slide-leave-active {
  transition: all 0.25s ease;
}
.error-slide-enter-from,
.error-slide-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

/* 登录按钮 */
.login-btn {
  background: linear-gradient(135deg, #c9a84c 0%, #a07e30 100%);
  border: none;
  border-radius: 4px;
  padding: 0.95rem 1.5rem;
  color: #1a1510;
  font-size: 1.05rem;
  font-family: inherit;
  font-weight: 600;
  letter-spacing: 0.2em;
  cursor: pointer;
  transition: opacity 0.2s, transform 0.15s, box-shadow 0.2s;
  box-shadow: 0 2px 12px rgba(201, 169, 110, 0.25);
  width: 100%;
}
.login-btn:hover:not(:disabled) {
  opacity: 0.9;
  transform: translateY(-1px);
  box-shadow: 0 4px 18px rgba(201, 169, 110, 0.35);
}
.login-btn:active:not(:disabled) {
  transform: translateY(0);
}
.login-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

/* 加载动画 */
.loading-dots span {
  animation: dot-blink 1.2s infinite;
}
.loading-dots span:nth-child(2) { animation-delay: 0.2s; }
.loading-dots span:nth-child(3) { animation-delay: 0.4s; }

@keyframes dot-blink {
  0%, 80%, 100% { opacity: 0; }
  40% { opacity: 1; }
}

/* 底部说明 */
.login-note {
  text-align: center;
  font-size: 0.75rem;
  color: #5a4a3a;
  line-height: 1.7;
  letter-spacing: 0.03em;
}
.link-btn {
  background: none;
  border: none;
  color: #8b7355;
  font-family: inherit;
  font-size: 0.75rem;
  cursor: pointer;
  text-decoration: underline;
  padding: 0;
}
.link-btn:hover { color: #c4a882; }
</style>
