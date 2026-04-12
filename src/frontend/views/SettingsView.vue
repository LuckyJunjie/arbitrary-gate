<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useSettingsStore } from '@/stores/settingsStore'
import { useInkValueStore } from '@/stores/inkValueStore'
import { playBrushTap, playJadeClick } from '@/composables/useSound'

const router = useRouter()
const settingsStore = useSettingsStore()
const inkValueStore = useInkValueStore()

const APP_VERSION = '0.1.0'
const CONTACT_WECHAT = '时光笺小助手'
const CONTACT_EMAIL = 'timespace@example.com'

interface StoredUser {
  nickname?: string
  avatarUrl?: string
  inkStone?: number
  isGuest?: number
}

const storedUser = ref<StoredUser>({})
try {
  const raw = localStorage.getItem('arbitrary_gate_user')
  if (raw) storedUser.value = JSON.parse(raw)
} catch { /* ignore */ }

const userNickname = computed(() => storedUser.value.nickname || '时光旅人')
const userAvatar = computed(() => storedUser.value.avatarUrl || '')
const inkStone = computed(() => storedUser.value.inkStone ?? inkValueStore.totalPoints)
const isGuest = computed(() => storedUser.value.isGuest === 1)
const wechatBound = computed(() => !isGuest.value && !!storedUser.value.avatarUrl)

type Speed = 'slow' | 'medium' | 'fast'
const speedOptions: { value: Speed; label: string; sub: string }[] = [
  { value: 'slow',   label: '慢', sub: '从容品味' },
  { value: 'medium', label: '中', sub: '平衡之道' },
  { value: 'fast',   label: '快', sub: '紧凑跌宕' },
]

function selectSpeed(speed: Speed) {
  settingsStore.setNarrativeSpeed(speed)
  playBrushTap()
}

const showLogoutConfirm = ref(false)
const showAgreement = ref(false)
const showPrivacy = ref(false)

function goBack() {
  playJadeClick()
  router.back()
}

function handleLogout() {
  showLogoutConfirm.value = false
  playJadeClick()
  localStorage.removeItem('token')
  localStorage.removeItem('arbitrary_gate_user')
  localStorage.removeItem('guest_device_id')
  if (isGuest.value) {
    ;[
      'selectedKeywordCards', 'selectedEventCard', 'recentStories',
      'ownedKeywordCards', 'story_values', 'arbitrary_gate_ink_value', 'dailyFreeDraws',
    ].forEach(k => localStorage.removeItem(k))
  }
  router.replace('/')
}

function handleToggle(field: 'sound' | 'music' | 'vibration' | 'storyUpdate' | 'dailyFortune') {
  playBrushTap()
  switch (field) {
    case 'sound':        settingsStore.toggleSound(); break
    case 'music':        settingsStore.toggleMusic(); break
    case 'vibration':    settingsStore.toggleVibration(); break
    case 'storyUpdate':  settingsStore.toggleStoryUpdatePush(); break
    case 'dailyFortune': settingsStore.toggleDailyFortuneReminder(); break
  }
}

const soundFillPct = computed(() => settingsStore.soundVolume * 100)
const musicFillPct = computed(() => settingsStore.musicVolume * 100)

function onSoundVolumeChange(e: Event) {
  settingsStore.soundVolume = parseFloat((e.target as HTMLInputElement).value)
  playBrushTap()
}

function onMusicVolumeChange(e: Event) {
  settingsStore.musicVolume = parseFloat((e.target as HTMLInputElement).value)
  playBrushTap()
}
</script>

<template>
  <div class="settings-view">
    <header class="settings-header">
      <button class="back-btn" @click="goBack" aria-label="返回">
        <span class="back-arrow">‹</span>
      </button>
      <h2 class="settings-title">设置</h2>
      <div class="header-spacer" />
    </header>

    <div class="settings-content">

      <!-- ══ 个人信息区 ══ -->
      <section class="settings-section">
        <div class="section-label">个人信息</div>
        <div class="scroll-card">
          <div class="profile-row">
            <div class="avatar-wrap">
              <img v-if="userAvatar" :src="userAvatar" class="avatar-img" alt="用户头像" />
              <div v-else class="avatar-placeholder"><span>🏮</span></div>
              <div v-if="wechatBound" class="avatar-badge">微</div>
            </div>
            <div class="profile-info">
              <div class="profile-nickname">{{ userNickname }}</div>
              <div class="profile-ink">
                <span class="ink-icon">◆</span>
                <span class="ink-value">{{ inkStone }}</span>
                <span class="ink-unit">墨晶</span>
              </div>
              <div class="bind-status" :class="{ guest: isGuest }">
                <span class="bind-dot" />
                <span>{{ wechatBound ? '微信已绑定' : isGuest ? '游客身份' : '已登录' }}</span>
              </div>
              <!-- U-02 手机号登录入口 -->
              <button class="phone-bind-btn" @click="router.push('/phone-login')">
                <span class="phone-bind-icon">📱</span>
                <span class="phone-bind-text">{{ isGuest ? '升级正式账号' : '绑定手机号' }}</span>
                <span class="phone-bind-arrow">›</span>
              </button>
            </div>
          </div>
        </div>
      </section>

      <!-- ══ 游戏设置区 ══ -->
      <section class="settings-section">
        <div class="section-label">游戏设置</div>
        <div class="scroll-card">

          <div class="setting-row">
            <div class="setting-row__label">
              <span class="setting-icon">🔔</span>
              <span class="label-main">音效</span>
            </div>
            <div class="setting-row__control">
              <input type="range" class="volume-slider" min="0" max="1" step="0.01"
                :value="settingsStore.soundVolume"
                :style="{ '--fill': soundFillPct + '%' }"
                @input="onSoundVolumeChange" />
              <button class="toggle-btn" :class="{ active: settingsStore.soundEnabled }"
                @click="handleToggle('sound')" role="switch" :aria-checked="settingsStore.soundEnabled">
                <span class="toggle-thumb" />
              </button>
            </div>
          </div>

          <div class="card-divider" />

          <div class="setting-row">
            <div class="setting-row__label">
              <span class="setting-icon">🎵</span>
              <span class="label-main">音乐</span>
            </div>
            <div class="setting-row__control">
              <input type="range" class="volume-slider" min="0" max="1" step="0.01"
                :value="settingsStore.musicVolume"
                :style="{ '--fill': musicFillPct + '%' }"
                @input="onMusicVolumeChange" />
              <button class="toggle-btn" :class="{ active: settingsStore.musicEnabled }"
                @click="handleToggle('music')" role="switch" :aria-checked="settingsStore.musicEnabled">
                <span class="toggle-thumb" />
              </button>
            </div>
          </div>

          <div class="card-divider" />

          <div class="setting-row">
            <div class="setting-row__label">
              <span class="setting-icon">📳</span>
              <span class="label-main">震动反馈</span>
            </div>
            <button class="toggle-btn" :class="{ active: settingsStore.vibrationEnabled }"
              @click="handleToggle('vibration')" role="switch" :aria-checked="settingsStore.vibrationEnabled">
              <span class="toggle-thumb" />
            </button>
          </div>

          <div class="card-divider" />

          <div class="setting-row setting-row--col">
            <div class="setting-row__label">
              <span class="setting-icon">📜</span>
              <span class="label-main">叙事语速</span>
            </div>
            <div class="speed-cards">
              <button v-for="opt in speedOptions" :key="opt.value"
                class="speed-card" :class="{ active: settingsStore.narrativeSpeed === opt.value }"
                @click="selectSpeed(opt.value)">
                <span class="speed-card__label">{{ opt.label }}</span>
                <span class="speed-card__sub">{{ opt.sub }}</span>
              </button>
            </div>
          </div>

        </div>
      </section>

      <!-- ══ 通知设置区 ══ -->
      <section class="settings-section">
        <div class="section-label">通知设置</div>
        <div class="scroll-card">

          <div class="setting-row">
            <div class="setting-row__label">
              <span class="setting-icon">📖</span>
              <div class="label-text">
                <span class="label-main">故事更新推送</span>
                <span class="label-sub">新章节完成后推送提醒</span>
              </div>
            </div>
            <button class="toggle-btn" :class="{ active: settingsStore.storyUpdatePushEnabled }"
              @click="handleToggle('storyUpdate')" role="switch" :aria-checked="settingsStore.storyUpdatePushEnabled">
              <span class="toggle-thumb" />
            </button>
          </div>

          <div class="card-divider" />

          <div class="setting-row">
            <div class="setting-row__label">
              <span class="setting-icon">🔮</span>
              <div class="label-text">
                <span class="label-main">每日运势提醒</span>
                <span class="label-sub">每日辰时推送今日运势</span>
              </div>
            </div>
            <button class="toggle-btn" :class="{ active: settingsStore.dailyFortuneReminderEnabled }"
              @click="handleToggle('dailyFortune')" role="switch" :aria-checked="settingsStore.dailyFortuneReminderEnabled">
              <span class="toggle-thumb" />
            </button>
          </div>

        </div>
      </section>

      <!-- ══ 关于区 ══ -->
      <section class="settings-section">
        <div class="section-label">关于</div>
        <div class="scroll-card">

          <div class="about-row">
            <span class="about-label">版本号</span>
            <span class="about-value">v{{ APP_VERSION }}</span>
          </div>

          <div class="card-divider" />

          <button class="about-link" @click="showAgreement = true">
            <span class="about-link__icon">📋</span>
            <span class="about-link__text">用户协议</span>
            <span class="about-link__arrow">›</span>
          </button>

          <div class="card-divider" />

          <button class="about-link" @click="showPrivacy = true">
            <span class="about-link__icon">🔐</span>
            <span class="about-link__text">隐私政策</span>
            <span class="about-link__arrow">›</span>
          </button>

          <div class="card-divider" />

          <div class="about-row">
            <span class="about-label">微信公众号</span>
            <span class="about-value about-value--gold">{{ CONTACT_WECHAT }}</span>
          </div>

          <div class="card-divider" />

          <div class="about-row">
            <span class="about-label">联系邮箱</span>
            <span class="about-value">{{ CONTACT_EMAIL }}</span>
          </div>

        </div>
      </section>

      <!-- ══ 退出登录 ══ -->
      <section class="settings-section">
        <button class="logout-btn" @click="showLogoutConfirm = true">
          <span class="logout-icon">🚪</span>
          <span>{{ isGuest ? '退出体验' : '退出登录' }}</span>
        </button>
      </section>

    </div>

    <!-- 退出确认弹窗 -->
    <Teleport to="body">
      <Transition name="modal-fade">
        <div v-if="showLogoutConfirm" class="modal-overlay" @click.self="showLogoutConfirm = false">
          <div class="modal-dialog">
            <div class="modal-header">
              <h3 class="modal-title">确认{{ isGuest ? '退出体验' : '退出登录' }}</h3>
              <button class="modal-close" @click="showLogoutConfirm = false">×</button>
            </div>
            <div class="modal-body">
              <p class="confirm-text">
                {{ isGuest ? '退出后游客数据将被清除，确定要退出吗？' : '确定要退出当前账号吗？' }}
              </p>
              <div class="confirm-actions">
                <button class="confirm-btn confirm-btn--cancel" @click="showLogoutConfirm = false">取消</button>
                <button class="confirm-btn confirm-btn--danger" @click="handleLogout">
                  {{ isGuest ? '退出体验' : '确认退出' }}
                </button>
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
              <div class="scroll-text">
                <p>【首部及导言】</p>
                <p>欢迎使用"时光笺·任意门"（以下简称"本应用"）。</p>
                <p>本应用致力于为用户提供古风故事体验服务。请在使用前仔细阅读本协议的全部内容。</p>
                <br/>
                <p>【服务内容】</p>
                <p>本应用通过抽卡收集关键词，结合 AI 技术生成个性化古风故事。</p>
                <br/>
                <p>【用户权利与义务】</p>
                <p>1. 用户需保证上传、输入的内容不违反法律法规，不侵犯他人合法权益。</p>
                <p>2. 用户不得利用本应用从事任何违法活动。</p>
                <p>3. 用户应妥善保管个人账户信息，因个人保管不善造成的损失由用户自行承担。</p>
                <br/>
                <p>【知识产权】</p>
                <p>本应用内的所有内容版权归本团队所有，未经授权不得进行任何形式的转载、复制或传播。</p>
                <br/>
                <p>【免责声明】</p>
                <p>本应用生成的故事情节为 AI 随机生成，不保证内容的真实性和准确性。</p>
              </div>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- 隐私政策弹窗 -->
    <Teleport to="body">
      <Transition name="modal-fade">
        <div v-if="showPrivacy" class="modal-overlay" @click.self="showPrivacy = false">
          <div class="modal-dialog modal-dialog--tall">
            <div class="modal-header">
              <h3 class="modal-title">隐私政策</h3>
              <button class="modal-close" @click="showPrivacy = false">×</button>
            </div>
            <div class="modal-body">
              <div class="scroll-text">
                <p>【信息收集】</p>
                <p>本应用不会收集您的个人敏感信息。当您使用微信登录时，我们仅获取您的昵称和头像，用于展示用户身份。</p>
                <br/>
                <p>【信息使用】</p>
                <p>您的个人信息仅用于：提供和改进本应用的服务、账户安全和身份验证、向您推送故事更新等通知（如您已开启通知权限）。</p>
                <br/>
                <p>【信息保护】</p>
                <p>我们采用行业标准的安全措施保护您的个人信息，防止数据遭到未经授权的访问、使用或泄露。</p>
                <br/>
                <p>【信息共享】</p>
                <p>未经您的同意，我们不会与任何第三方共享您的个人信息，法律法规要求的情况除外。</p>
                <br/>
                <p>【联系我们】</p>
                <p>如您对本隐私政策有任何疑问，请通过 {{ CONTACT_EMAIL }} 与我们联系。</p>
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
  padding-bottom: 3rem;
}

.settings-header {
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

.settings-title {
  font-size: 1.1rem;
  letter-spacing: 0.25em;
  color: #c4a882;
  font-weight: 400;
}

.header-spacer { width: 36px; }

.settings-content {
  margin: 1.25rem 1rem;
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.section-label {
  font-size: 0.68rem;
  letter-spacing: 0.22em;
  color: #8b7355;
  text-transform: uppercase;
  margin-bottom: 0.45rem;
  padding-left: 0.25rem;
}

.scroll-card {
  border: 1px solid rgba(201, 169, 110, 0.18);
  border-radius: 6px;
  background: rgba(232, 220, 200, 0.025);
  overflow: hidden;
  position: relative;
}

.scroll-card::before,
.scroll-card::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  height: 1px;
  background: linear-gradient(90deg,
    transparent 0%,
    rgba(201, 169, 110, 0.4) 30%,
    rgba(201, 169, 110, 0.4) 70%,
    transparent 100%
  );
}
.scroll-card::before { top: 0; }
.scroll-card::after  { bottom: 0; }

.card-divider {
  height: 1px;
  background: rgba(232, 220, 200, 0.06);
}

/* ── 个人信息 ── */
.profile-row {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1.25rem;
}

.avatar-wrap {
  position: relative;
  flex-shrink: 0;
}

.avatar-img {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  border: 2px solid rgba(201, 169, 110, 0.35);
  object-fit: cover;
  display: block;
}

.avatar-placeholder {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: rgba(201, 169, 110, 0.08);
  border: 2px solid rgba(201, 169, 110, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.5rem;
}

.avatar-badge {
  position: absolute;
  bottom: -2px;
  right: -2px;
  width: 18px;
  height: 18px;
  background: #27ae60;
  border-radius: 50%;
  border: 1.5px solid #1a1510;
  font-size: 0.58rem;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: 'Noto Serif SC', serif;
}

.profile-info {
  display: flex;
  flex-direction: column;
  gap: 0.28rem;
  flex: 1;
  min-width: 0;
}

.profile-nickname {
  font-size: 1rem;
  color: #e8dcc8;
  letter-spacing: 0.08em;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.profile-ink {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.85rem;
}

.ink-icon { color: #c9a96e; font-size: 0.7rem; }
.ink-value { color: #c9a96e; font-weight: 600; letter-spacing: 0.05em; }
.ink-unit { color: #8b7355; font-size: 0.75rem; }

.bind-status {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  font-size: 0.68rem;
  color: #6b8e6b;
  letter-spacing: 0.06em;
}
.bind-status.guest { color: #c9a84c; }
.bind-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
}
.bind-status.guest .bind-dot {
  animation: guest-blink 2s ease-in-out infinite;
}
@keyframes guest-blink {
  0%, 100% { opacity: 1; }
  50%       { opacity: 0.3; }
}

/* ── U-02 手机号绑定按钮 ── */
.phone-bind-btn {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  margin-top: 0.6rem;
  padding: 0.45rem 0.75rem;
  background: rgba(201, 169, 110, 0.08);
  border: 1px solid rgba(201, 169, 110, 0.3);
  border-radius: 4px;
  color: #c9a84c;
  font-family: inherit;
  font-size: 0.82rem;
  cursor: pointer;
  transition: background 0.2s, border-color 0.2s;
  width: fit-content;
}
.phone-bind-btn:hover {
  background: rgba(201, 169, 110, 0.15);
  border-color: rgba(201, 169, 110, 0.5);
}
.phone-bind-icon { font-size: 0.9rem; }
.phone-bind-text { letter-spacing: 0.05em; }
.phone-bind-arrow { font-size: 1rem; opacity: 0.6; }

/* ── 设置行 ── */
.setting-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.88rem 1.25rem;
  gap: 0.75rem;
}

.setting-row--col {
  flex-direction: column;
  align-items: flex-start;
  gap: 0.7rem;
}

.setting-row__label {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  flex-shrink: 0;
}

.setting-icon { font-size: 1rem; flex-shrink: 0; }

.label-main {
  font-size: 0.88rem;
  color: #e8dcc8;
  letter-spacing: 0.05em;
}

.label-text {
  display: flex;
  flex-direction: column;
  gap: 0.08rem;
}

.label-sub {
  font-size: 0.68rem;
  color: #8b7355;
  letter-spacing: 0.04em;
}

.setting-row__control {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  flex-shrink: 0;
}

/* ── 音量滑块 ── */
.volume-slider {
  -webkit-appearance: none;
  appearance: none;
  width: 88px;
  height: 4px;
  border-radius: 2px;
  background: linear-gradient(
    to right,
    #c9a96e 0%,
    #c9a96e var(--fill, 80%),
    rgba(232, 220, 200, 0.12) var(--fill, 80%),
    rgba(232, 220, 200, 0.12) 100%
  );
  outline: none;
  cursor: pointer;
}

.volume-slider::-webkit-slider-thumb {
  -webkit-appearance: none;
  appearance: none;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #c9a96e;
  cursor: pointer;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.5);
  border: 1px solid rgba(201, 169, 110, 0.5);
  transition: transform 0.15s;
}
.volume-slider::-webkit-slider-thumb:hover { transform: scale(1.2); }

.volume-slider::-moz-range-thumb {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #c9a96e;
  cursor: pointer;
  border: 1px solid rgba(201, 169, 110, 0.5);
}

/* ── 开关按钮 ── */
.toggle-btn {
  width: 42px;
  height: 24px;
  border-radius: 12px;
  border: 1px solid rgba(201, 169, 110, 0.25);
  background: rgba(0, 0, 0, 0.3);
  cursor: pointer;
  position: relative;
  transition: background 0.3s, border-color 0.3s;
  padding: 0;
  flex-shrink: 0;
}
.toggle-btn.active {
  background: rgba(201, 169, 110, 0.2);
  border-color: #c9a84c;
}
.toggle-thumb {
  position: absolute;
  top: 3px;
  left: 3px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #6b5a45;
  transition: left 0.3s ease, background 0.3s ease;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.4);
}
.toggle-btn.active .toggle-thumb {
  left: 21px;
  background: #c9a84c;
}

/* ── 叙事语速卡片 ── */
.speed-cards {
  display: flex;
  gap: 0.5rem;
  width: 100%;
}

.speed-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.18rem;
  padding: 0.55rem 0.25rem;
  background: rgba(232, 220, 200, 0.03);
  border: 1px solid rgba(232, 220, 200, 0.1);
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
  color: #e8dcc8;
  font-family: inherit;
}
.speed-card:hover {
  background: rgba(201, 169, 110, 0.06);
  border-color: rgba(201, 169, 110, 0.25);
}
.speed-card.active {
  background: rgba(201, 169, 110, 0.12);
  border-color: #c9a84c;
  box-shadow: 0 0 8px rgba(201, 169, 110, 0.15);
}
.speed-card__label {
  font-size: 1rem;
  color: #c9a84c;
  letter-spacing: 0.1em;
  font-family: 'Noto Serif SC', 'Source Han Serif SC', 'SimSun', serif;
}
.speed-card__sub {
  font-size: 0.62rem;
  color: #8b7355;
  letter-spacing: 0.05em;
}
.speed-card.active .speed-card__sub { color: #c4a882; }

/* ── 关于区 ── */
.about-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.82rem 1.25rem;
  gap: 0.5rem;
}

.about-label {
  font-size: 0.85rem;
  color: #8b7355;
  letter-spacing: 0.06em;
  flex-shrink: 0;
}

.about-value {
  font-size: 0.85rem;
  color: #c4a882;
  letter-spacing: 0.04em;
  text-align: right;
}
.about-value--gold { color: #c9a84c; }

.about-link {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0.82rem 1.25rem;
  background: transparent;
  border: none;
  width: 100%;
  text-align: left;
  color: #e8dcc8;
  cursor: pointer;
  transition: background 0.2s;
  font-family: inherit;
}
.about-link:hover { background: rgba(232, 220, 200, 0.04); }
.about-link__icon { font-size: 0.95rem; flex-shrink: 0; }
.about-link__text { flex: 1; font-size: 0.85rem; letter-spacing: 0.06em; }
.about-link__arrow {
  color: rgba(201, 169, 110, 0.4);
  font-size: 1.1rem;
  transition: color 0.2s;
}
.about-link:hover .about-link__arrow { color: #c9a84c; }

/* ── 退出登录 ── */
.logout-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 0.82rem;
  background: rgba(196, 90, 90, 0.06);
  border: 1px solid rgba(196, 90, 90, 0.2);
  border-radius: 6px;
  color: #c96c6c;
  font-family: inherit;
  font-size: 0.9rem;
  letter-spacing: 0.1em;
  cursor: pointer;
  transition: all 0.2s;
}
.logout-btn:hover {
  background: rgba(196, 90, 90, 0.12);
  border-color: rgba(196, 90, 90, 0.35);
}
.logout-icon { font-size: 0.9rem; }

/* ── 弹窗 ── */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(13, 10, 8, 0.78);
  backdrop-filter: blur(6px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 200;
  padding: 1.5rem;
}

.modal-dialog {
  background: #1a1510;
  border: 1px solid rgba(201, 169, 110, 0.25);
  border-radius: 8px;
  width: 100%;
  max-width: 360px;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  animation: dialog-in 0.28s cubic-bezier(0.22, 1, 0.36, 1);
}
.modal-dialog--tall { max-height: 85vh; }

@keyframes dialog-in {
  from { transform: scale(0.92); opacity: 0; }
  to   { transform: scale(1);    opacity: 1; }
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1.1rem 1.25rem;
  border-bottom: 1px solid rgba(232, 220, 200, 0.08);
  flex-shrink: 0;
}

.modal-title {
  font-size: 0.95rem;
  letter-spacing: 0.15em;
  color: #c4a882;
  font-weight: 400;
}

.modal-close {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  border: 1px solid rgba(232, 220, 200, 0.12);
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
  padding: 1.25rem;
  overflow-y: auto;
  flex: 1;
}

.scroll-text {
  font-size: 0.82rem;
  line-height: 2;
  color: rgba(232, 220, 200, 0.72);
  letter-spacing: 0.04em;
}
.scroll-text p { margin: 0; }

.confirm-text {
  font-size: 0.88rem;
  color: rgba(232, 220, 200, 0.78);
  letter-spacing: 0.06em;
  text-align: center;
  margin-bottom: 1.5rem;
  line-height: 1.7;
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
  font-size: 0.88rem;
  letter-spacing: 0.1em;
  cursor: pointer;
  transition: all 0.2s;
}

.confirm-btn--cancel {
  background: transparent;
  border: 1px solid rgba(139, 115, 85, 0.35);
  color: #8b7355;
}
.confirm-btn--cancel:hover {
  background: rgba(139, 115, 85, 0.1);
  color: #e8dcc8;
}

.confirm-btn--danger {
  background: rgba(196, 90, 90, 0.12);
  border: 1px solid rgba(196, 90, 90, 0.35);
  color: #c96c6c;
}
.confirm-btn--danger:hover {
  background: rgba(196, 90, 90, 0.2);
  color: #e8a090;
}

.modal-fade-enter-active,
.modal-fade-leave-active {
  transition: opacity 0.25s ease;
}
.modal-fade-enter-from,
.modal-fade-leave-to {
  opacity: 0;
}

@media (max-width: 375px) {
  .settings-content { margin: 1rem 0.75rem; }
  .speed-cards { gap: 0.35rem; }
  .volume-slider { width: 72px; }
}
</style>
