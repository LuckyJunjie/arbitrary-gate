<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useSettingsStore } from '@/stores/settingsStore'
import { playBrushTap } from '@/composables/useSound'

const router = useRouter()
const settingsStore = useSettingsStore()

// 弹窗状态
const showAgreement = ref(false)
const showAbout = ref(false)
const showLogoutConfirm = ref(false)
const showBindTip = ref(false)

// App 信息
const APP_NAME = '时光笺·任意门'
const APP_VERSION = '0.1.0'
const CONTACT = 'timespace@example.com'

// ── U-03 游客状态 ──
function getUserIsGuest(): boolean {
  try {
    const saved = localStorage.getItem('arbitrary_gate_user')
    if (saved) {
      const user = JSON.parse(saved)
      return user.isGuest === 1
    }
  } catch { /* ignore */ }
  return false
}

const isGuest = ref(getUserIsGuest())
const userNickname = ref('')
const guestDeviceId = ref('')

function loadUserInfo() {
  try {
    const saved = localStorage.getItem('arbitrary_gate_user')
    if (saved) {
      const user = JSON.parse(saved)
      userNickname.value = user.nickname || ''
    }
    const deviceId = localStorage.getItem('guest_device_id')
    guestDeviceId.value = deviceId || ''
  } catch { /* ignore */ }
}

loadUserInfo()

// 清除缓存
function clearCache() {
  localStorage.clear()
  // 重新加载页面以重置状态
  window.location.reload()
}

// 用户协议（简化文本，实际项目应从后端或文档获取）
const agreementText = `时光笺·任意门 用户服务协议

【首部及导言】

欢迎使用"时光笺·任意门"（以下简称"本应用"）。

本应用致力于为用户提供古风故事体验服务。请在使用前仔细阅读本协议的全部内容。

【服务内容】

本应用通过抽卡收集关键词，结合 AI 技术生成个性化古风故事。

【用户权利与义务】

1. 用户需保证上传、输入的内容不违反法律法规，不侵犯他人合法权益。
2. 用户不得利用本应用从事任何违法活动。
3. 用户应妥善保管个人账户信息，因个人保管不善造成的损失由用户自行承担。

【知识产权】

本应用内的所有内容（包括但不限于文字、图片、音频）版权归本团队所有，未经授权不得进行任何形式的转载、复制或传播。

【免责声明】

本应用生成的故事情节为 AI 随机生成，不保证内容的真实性和准确性。`

function goBack() {
  router.back()
}

function handleLogout() {
  showLogoutConfirm.value = false
  localStorage.removeItem('token')
  localStorage.removeItem('arbitrary_gate_user')
  localStorage.removeItem('guest_device_id')
  // U-03: 游客退出时清除所有本地数据（游客数据不可保留）
  if (isGuest.value) {
    localStorage.removeItem('selectedKeywordCards')
    localStorage.removeItem('selectedEventCard')
    localStorage.removeItem('recentStories')
    localStorage.removeItem('ownedKeywordCards')
    localStorage.removeItem('story_values')
    localStorage.removeItem('arbitrary_gate_ink_value')
    localStorage.removeItem('dailyFreeDraws')
  }
  router.replace('/')
}
</script>

<template>
  <div class="settings-view">
    <!-- 标题栏 -->
    <header class="settings-header">
      <button class="back-btn" @click="goBack" aria-label="返回">
        <span class="back-arrow">‹</span>
      </button>
      <h2 class="settings-title">设置</h2>
      <div class="header-spacer" />
    </header>

    <!-- 设置列表 -->
    <div class="settings-list">

      <!-- U-03: 账号信息（区分游客和正式用户） -->
      <div class="account-section">
        <div class="account-avatar">
          <span class="avatar-icon">{{ isGuest ? '👤' : '🏮' }}</span>
        </div>
        <div class="account-info">
          <div class="account-nickname">{{ userNickname || (isGuest ? '旅人' : '时光旅人') }}</div>
          <div v-if="isGuest" class="account-guest-tag">
            <span class="guest-dot" />
            <span>游客身份</span>
          </div>
          <div v-else class="account-normal-tag">正式账号</div>
        </div>
      </div>

      <!-- U-03: 游客绑定提示 -->
      <div v-if="isGuest" class="guest-bind-card">
        <div class="guest-bind-card__icon">🔗</div>
        <div class="guest-bind-card__content">
          <div class="guest-bind-card__title">绑定微信账号</div>
          <div class="guest-bind-card__desc">绑定后可解锁完整功能，保留当前游客数据</div>
        </div>
        <button class="guest-bind-card__btn" @click="showBindTip = true">
          绑定
        </button>
      </div>
      <!-- 音效开关 -->
      <div class="setting-item">
        <div class="setting-left">
          <span class="setting-icon">🔔</span>
          <span class="setting-label">音效</span>
        </div>
        <button
          class="toggle-btn"
          :class="{ active: settingsStore.soundEnabled }"
          @click="settingsStore.toggleSound(); playBrushTap()"
          :aria-pressed="settingsStore.soundEnabled"
          role="switch"
          :aria-checked="settingsStore.soundEnabled"
        >
          <span class="toggle-thumb" />
        </button>
      </div>

      <div class="divider" />

      <!-- 清除缓存 -->
      <button class="setting-item setting-item--btn" @click="clearCache">
        <div class="setting-left">
          <span class="setting-icon">🗑️</span>
          <span class="setting-label">清除缓存</span>
        </div>
        <span class="setting-arrow">›</span>
      </button>

      <!-- 关于我们 -->
      <button class="setting-item setting-item--btn" @click="showAbout = true">
        <div class="setting-left">
          <span class="setting-icon">📜</span>
          <span class="setting-label">关于我们</span>
        </div>
        <span class="setting-arrow">›</span>
      </button>

      <!-- 用户协议 -->
      <button class="setting-item setting-item--btn" @click="showAgreement = true">
        <div class="setting-left">
          <span class="setting-icon">📋</span>
          <span class="setting-label">用户协议</span>
        </div>
        <span class="setting-arrow">›</span>
      </button>

      <div class="divider" />

      <!-- 退出登录 / 退出体验 -->
      <button class="setting-item setting-item--btn setting-item--danger" @click="showLogoutConfirm = true">
        <div class="setting-left">
          <span class="setting-icon">🚪</span>
          <span class="setting-label">{{ isGuest ? '退出体验' : '退出登录' }}</span>
        </div>
        <span class="setting-arrow">›</span>
      </button>
    </div>

    <!-- 关于我们弹窗 -->
    <Teleport to="body">
      <Transition name="modal-fade">
        <div v-if="showAbout" class="modal-overlay" @click.self="showAbout = false">
          <div class="modal-dialog">
            <div class="modal-header">
              <h3 class="modal-title">关于我们</h3>
              <button class="modal-close" @click="showAbout = false">×</button>
            </div>
            <div class="modal-body">
              <div class="about-app-name">{{ APP_NAME }}</div>
              <div class="about-version">版本 {{ APP_VERSION }}</div>
              <div class="about-divider" />
              <div class="about-section">
                <p class="about-desc">
                  时光笺·任意门是一款古风故事抽卡游戏，融合传统文化元素与现代 AI 技术，为你编织独一无二的命运故事。
                </p>
              </div>
              <div class="about-divider" />
              <div class="about-section">
                <div class="about-row">
                  <span class="about-label">联系邮箱</span>
                  <span class="about-value">{{ CONTACT }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- 用户协议弹窗 -->
    <Teleport to="body">
      <Transition name="modal-fade">
        <div v-if="showAgreement" class="modal-overlay" @click.self="showAgreement = false">
          <div class="modal-dialog modal-dialog--tall">
            <div class="modal-header">
              <h3 class="modal-title">用户协议</h3>
              <button class="modal-close" @click="showAgreement = false">×</button>
            </div>
            <div class="modal-body">
              <div class="agreement-text">{{ agreementText }}</div>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- 退出确认弹窗 -->
    <Teleport to="body">
      <Transition name="modal-fade">
        <div v-if="showLogoutConfirm" class="modal-overlay" @click.self="showLogoutConfirm = false">
          <div class="modal-dialog">
            <div class="modal-header">
              <h3 class="modal-title">{{ isGuest ? '确认退出体验' : '确认退出' }}</h3>
              <button class="modal-close" @click="showLogoutConfirm = false">×</button>
            </div>
            <div class="modal-body">
              <p class="confirm-text">{{ isGuest ? '退出后游客数据将被清除，确定要退出吗？' : '确定要退出当前账号吗？' }}</p>
              <div class="confirm-actions">
                <button class="confirm-btn confirm-btn--cancel" @click="showLogoutConfirm = false">
                  取消
                </button>
                <button class="confirm-btn confirm-btn--confirm" @click="handleLogout">
                  {{ isGuest ? '退出体验' : '确认退出' }}
                </button>
              </div>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- U-03: 绑定提示弹窗 -->
    <Teleport to="body">
      <Transition name="modal-fade">
        <div v-if="showBindTip" class="modal-overlay" @click.self="showBindTip = false">
          <div class="modal-dialog">
            <div class="modal-header">
              <h3 class="modal-title">绑定微信账号</h3>
              <button class="modal-close" @click="showBindTip = false">×</button>
            </div>
            <div class="modal-body">
              <p class="confirm-text">绑定微信账号后可解锁以下功能：</p>
              <ul class="bind-feature-list">
                <li>保留所有抽卡记录与故事数据</li>
                <li>每日免费抽卡次数提升至 3 次</li>
                <li>解锁手稿分享与合券功能</li>
                <li>支持墨晶充值与商城购买</li>
              </ul>
              <p class="confirm-text" style="margin-top:1rem; font-size:0.78rem; color:#8b7355;">
                绑定功能即将上线，敬请期待。
              </p>
              <div class="confirm-actions" style="margin-top:1.5rem;">
                <button class="confirm-btn confirm-btn--cancel" style="flex:1;" @click="showBindTip = false">
                  知道了
                </button>
              </div>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<style scoped>
.settings-view {
  min-height: 100vh;
  background: radial-gradient(ellipse at center, #1a1510 0%, #0d0a08 100%);
  color: #e8dcc8;
  padding-bottom: 2rem;
}

/* ── 标题栏 ── */
.settings-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1.5rem 1.25rem 1rem;
  border-bottom: 1px solid rgba(232, 220, 200, 0.1);
}

.back-btn {
  width: 36px;
  height: 36px;
  background: rgba(232, 220, 200, 0.06);
  border: 1px solid rgba(232, 220, 200, 0.12);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s;
  flex-shrink: 0;
}

.back-btn:hover {
  background: rgba(232, 220, 200, 0.12);
}

.back-arrow {
  font-size: 1.5rem;
  color: #c4a882;
  line-height: 1;
  margin-top: -2px;
}

.settings-title {
  font-size: 1.1rem;
  letter-spacing: 0.25em;
  color: #c4a882;
  font-weight: 400;
}

.header-spacer {
  width: 36px;
}

/* ── 设置列表 ── */
.settings-list {
  margin: 1.5rem 1.25rem;
  border: 1px solid rgba(201, 168, 76, 0.15);
  border-radius: 6px;
  background: rgba(232, 220, 200, 0.03);
  overflow: hidden;
}

.setting-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1rem 1.25rem;
  background: transparent;
  transition: background 0.2s;
}

.setting-item--btn {
  width: 100%;
  border: none;
  cursor: pointer;
  font-family: inherit;
  color: #e8dcc8;
  text-align: left;
}

.setting-item--btn:hover {
  background: rgba(232, 220, 200, 0.05);
}

.setting-item--danger .setting-label {
  color: #c96c6c;
}

.setting-item--danger .setting-icon {
  filter: grayscale(0.3) brightness(0.8);
}

.setting-left {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.setting-icon {
  font-size: 1.1rem;
  flex-shrink: 0;
}

.setting-label {
  font-size: 0.9rem;
  letter-spacing: 0.08em;
  color: #e8dcc8;
}

.setting-arrow {
  font-size: 1.2rem;
  color: rgba(201, 168, 76, 0.4);
  transition: color 0.2s;
}

.setting-item--btn:hover .setting-arrow {
  color: #c9a84c;
}

/* ── 音效开关 ── */
.toggle-btn {
  width: 44px;
  height: 26px;
  border-radius: 13px;
  border: 1px solid rgba(201, 168, 76, 0.3);
  background: rgba(0, 0, 0, 0.3);
  cursor: pointer;
  position: relative;
  transition: all 0.3s ease;
  padding: 0;
  flex-shrink: 0;
}

.toggle-btn.active {
  background: rgba(201, 168, 76, 0.25);
  border-color: #c9a84c;
}

.toggle-thumb {
  position: absolute;
  top: 3px;
  left: 3px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #8b7355;
  transition: all 0.3s ease;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.4);
}

.toggle-btn.active .toggle-thumb {
  left: 21px;
  background: #c9a84c;
}

/* ── 分隔线 ── */
.divider {
  height: 1px;
  background: rgba(232, 220, 200, 0.07);
  margin: 0;
}

/* ── U-03 账号信息区 ── */
.account-section {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1.25rem 1.5rem;
  background: rgba(201, 168, 76, 0.04);
  border-bottom: 1px solid rgba(201, 168, 76, 0.1);
}

.account-avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: rgba(201, 168, 76, 0.1);
  border: 1px solid rgba(201, 168, 76, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.avatar-icon {
  font-size: 1.4rem;
}

.account-info {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.account-nickname {
  font-size: 1rem;
  color: #e8dcc8;
  letter-spacing: 0.08em;
}

.account-guest-tag {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  font-size: 0.7rem;
  color: #c9a84c;
  letter-spacing: 0.06em;
}

.guest-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #c9a84c;
  animation: guest-blink 2s ease-in-out infinite;
}

@keyframes guest-blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

.account-normal-tag {
  font-size: 0.7rem;
  color: #6b8e6b;
  letter-spacing: 0.06em;
}

/* ── U-03 游客绑定卡片 ── */
.guest-bind-card {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 1rem 1.25rem;
  background: rgba(201, 168, 76, 0.06);
  border-top: 1px solid rgba(201, 168, 76, 0.08);
  border-bottom: 1px solid rgba(201, 168, 76, 0.08);
}

.guest-bind-card__icon {
  font-size: 1.3rem;
  flex-shrink: 0;
}

.guest-bind-card__content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.guest-bind-card__title {
  font-size: 0.88rem;
  color: #c9a84c;
  letter-spacing: 0.08em;
}

.guest-bind-card__desc {
  font-size: 0.72rem;
  color: #8b7355;
  letter-spacing: 0.04em;
  line-height: 1.4;
}

.guest-bind-card__btn {
  padding: 0.4rem 1rem;
  background: rgba(201, 168, 76, 0.15);
  border: 1px solid rgba(201, 168, 76, 0.35);
  border-radius: 4px;
  color: #c9a84c;
  font-family: inherit;
  font-size: 0.8rem;
  letter-spacing: 0.08em;
  cursor: pointer;
  transition: all 0.2s;
  flex-shrink: 0;
}

.guest-bind-card__btn:hover {
  background: rgba(201, 168, 76, 0.25);
}

/* ── U-03 绑定功能列表 ── */
.bind-feature-list {
  margin-top: 0.75rem;
  padding-left: 1.2rem;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.bind-feature-list li {
  font-size: 0.82rem;
  color: rgba(232, 220, 200, 0.7);
  letter-spacing: 0.05em;
  line-height: 1.5;
}

/* ── 弹窗 ── */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(13, 10, 8, 0.75);
  backdrop-filter: blur(6px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 200;
  padding: 1.5rem;
}

.modal-dialog {
  background: #1a1510;
  border: 1px solid rgba(201, 168, 76, 0.25);
  border-radius: 8px;
  width: 100%;
  max-width: 360px;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  animation: dialog-in 0.3s cubic-bezier(0.22, 1, 0.36, 1);
}

.modal-dialog--tall {
  max-height: 85vh;
}

@keyframes dialog-in {
  from { transform: scale(0.92); opacity: 0; }
  to { transform: scale(1); opacity: 1; }
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1.25rem 1.5rem;
  border-bottom: 1px solid rgba(232, 220, 200, 0.1);
  flex-shrink: 0;
}

.modal-title {
  font-size: 1rem;
  letter-spacing: 0.15em;
  color: #c4a882;
  font-weight: 400;
}

.modal-close {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  border: 1px solid rgba(232, 220, 200, 0.15);
  background: transparent;
  color: #8b7355;
  font-size: 1.1rem;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
  transition: all 0.2s;
  flex-shrink: 0;
}

.modal-close:hover {
  background: rgba(232, 220, 200, 0.08);
  color: #e8dcc8;
}

.modal-body {
  padding: 1.5rem;
  overflow-y: auto;
  flex: 1;
}

/* ── 关于我们 ── */
.about-app-name {
  font-size: 1.15rem;
  letter-spacing: 0.15em;
  color: #c9a84c;
  text-align: center;
  margin-bottom: 0.4rem;
}

.about-version {
  font-size: 0.75rem;
  color: #8b7355;
  letter-spacing: 0.1em;
  text-align: center;
  margin-bottom: 0;
}

.about-divider {
  height: 1px;
  background: rgba(232, 220, 200, 0.08);
  margin: 1.25rem 0;
}

.about-desc {
  font-size: 0.85rem;
  line-height: 1.8;
  color: rgba(232, 220, 200, 0.75);
  letter-spacing: 0.05em;
}

.about-section {
  margin-bottom: 0;
}

.about-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.about-label {
  font-size: 0.8rem;
  color: #8b7355;
  letter-spacing: 0.08em;
}

.about-value {
  font-size: 0.8rem;
  color: #c4a882;
  letter-spacing: 0.05em;
}

/* ── 用户协议 ── */
.agreement-text {
  font-size: 0.82rem;
  line-height: 2;
  color: rgba(232, 220, 200, 0.7);
  letter-spacing: 0.04em;
  white-space: pre-wrap;
  font-family: 'Noto Serif SC', 'Source Han Serif SC', 'SimSun', serif;
}

/* ── 退出确认 ── */
.confirm-text {
  font-size: 0.9rem;
  color: rgba(232, 220, 200, 0.8);
  letter-spacing: 0.08em;
  text-align: center;
  margin-bottom: 1.5rem;
}

.confirm-actions {
  display: flex;
  gap: 0.75rem;
}

.confirm-btn {
  flex: 1;
  padding: 0.65rem;
  border-radius: 4px;
  font-family: inherit;
  font-size: 0.9rem;
  letter-spacing: 0.1em;
  cursor: pointer;
  transition: all 0.2s;
}

.confirm-btn--cancel {
  background: transparent;
  border: 1px solid rgba(139, 115, 85, 0.4);
  color: #8b7355;
}

.confirm-btn--cancel:hover {
  background: rgba(139, 115, 85, 0.1);
  color: #e8dcc8;
}

.confirm-btn--confirm {
  background: rgba(196, 90, 90, 0.15);
  border: 1px solid rgba(196, 90, 90, 0.4);
  color: #c96c6c;
}

.confirm-btn--confirm:hover {
  background: rgba(196, 90, 90, 0.25);
  color: #e8a090;
}

/* ── 弹窗过渡 ── */
.modal-fade-enter-active,
.modal-fade-leave-active {
  transition: opacity 0.25s ease;
}

.modal-fade-enter-from,
.modal-fade-leave-to {
  opacity: 0;
}
</style>
