<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import InkPool from '@/components/InkPool.vue'
import Card from '@/components/Card.vue'
import ScratchCard from '@/components/ScratchCard.vue'
import RippleEffect from '@/components/RippleEffect.vue'
import InkLevelBadge from '@/components/InkLevelBadge.vue'
import { useCardStore } from '@/stores/cardStore'
import { useInkValueStore } from '@/stores/inkValueStore'
import { useAchievementStore } from '@/stores/achievementStore'
import { drawKeywordCard, fetchFortune, drawEventCard, fetchExpansions, ensureLogin, type EventDrawResult, type CardExpansion } from '@/services/api'
import { playInkDrop, playWindChime } from '@/composables/useSound'
import { useAchievementToast } from '@/composables/useAchievementToast'
import type { KeywordCard } from '@/services/api'

const cardStore = useCardStore()
const inkValueStore = useInkValueStore()
const achievementStore = useAchievementStore()
const router = useRouter()

// ── 成就解锁通知 ──
useAchievementToast(achievementStore)

const hasDrawn = ref(false)
const drawnCard = ref<KeywordCard | null>(null)
const isDrawing = ref(false)
const drawError = ref<string | null>(null)
const remainingFreeDraws = ref(0)
const drawId = ref(0) // 用于取消进行中的抽卡动画
const cardRevealed = ref(false) // ScratchCard擦墨完成后为 true

// ========== C-08 墨迹占卜：运势库 & 本地计算 ========== //

interface FortuneEntry {
  fortune: string   // 主文，如 "今日宜·静观其变"
  hint: string      // 副文，如 "墨香悠远，凡事缓成"
}

const FORTUNE_LIBRARY: FortuneEntry[] = [
  { fortune: '今日宜·静观其变', hint: '墨池微澜，谋定后动' },
  { fortune: '今日宜·顺势而为', hint: '水到渠成，勿逆天行' },
  { fortune: '今日宜·守拙待时', hint: '藏锋守锐，静待佳音' },
  { fortune: '今日宜·破旧立新', hint: '墨渍化蝶，宜革新局' },
  { fortune: '今日宜·沉淀内观', hint: '墨香入定，返照心源' },
  { fortune: '今日宜·广结善缘', hint: '笔墨结缘，四方来贺' },
  { fortune: '今日宜·谨慎抉择', hint: '一字落笔，重若千钧' },
  { fortune: '今日宜·轻装前行', hint: '卸下方寸，天地自宽' },
  { fortune: '今日忌·急功近利', hint: '墨迹未干，切忌妄动' },
  { fortune: '今日忌·犹豫徘徊', hint: '池边踌躇，失之交臂' },
  { fortune: '今日宜·以退为进', hint: '退一步看，海阔天空' },
  { fortune: '今日宜·独处深思', hint: '一人静坐，灵感自生' },
  { fortune: '今日宜·坦诚相待', hint: '以诚待人，人亦诚待' },
  { fortune: '今日宜·见微知著', hint: '一叶知秋，察于未萌' },
  { fortune: '今日宜·随遇而安', hint: '随缘自在，心静自凉' },
  { fortune: '今日宜·细水长流', hint: '墨香绵长，久久为功' },
  { fortune: '今日忌·固执己见', hint: '闭门造车，难得出路' },
  { fortune: '今日宜·借力前行', hint: '顺势而为，四两拨千斤' },
  { fortune: '今日宜·韬光养晦', hint: '含蓄内敛，养精蓄锐' },
  { fortune: '今日宜·蓦然回首', hint: '回首来路，悟已往之谏' },
  { fortune: '今日宜·活在当下', hint: '此时此刻，最是真实' },
  { fortune: '今日宜·回归本真', hint: '返璞归真，不忘初心' },
  { fortune: '今日忌·多言惹事', hint: '言多必失，守口如瓶' },
  { fortune: '今日宜·静待花开', hint: '耐心守候，终有回响' },
  { fortune: '今日宜·断舍离', hint: '放下执念，轻装上阵' },
  { fortune: '今日宜·审时度势', hint: '察言观色，相机而动' },
  { fortune: '今日宜·蓄势待发', hint: '养精蓄锐，一鸣惊人' },
  { fortune: '今日宜·以和为贵', hint: '和气生财，友善待人' },
  { fortune: '今日忌·鲁莽行事', hint: '三思而后行，慎之又慎' },
]

/** 根据日期 + 用户 ID 生成确定性随机索引（同一天同一用户固定结果） */
function computeLocalFortune(userId: string): FortuneEntry {
  const today = new Date()
  // 年月日拼合为数字 + userId 字符编码和 → 伪随机种子
  const seedBase = today.getFullYear() * 10000 + (today.getMonth() + 1) * 100 + today.getDate()
  let seed = seedBase
  for (let i = 0; i < userId.length; i++) {
    seed = (seed * 31 + userId.charCodeAt(i)) & 0xffffffff
  }
  const index = Math.abs(seed) % FORTUNE_LIBRARY.length
  return FORTUNE_LIBRARY[index]
}

// 今日运势预兆文本
const fortuneText = ref('')
const fortuneHint = ref('')
const fortuneVisible = ref(false) // 淡入动画控制
const currentFortune = computed(() => fortuneText.value)

// U-03: 从 localStorage 读取用户信息，获取 isGuest 标志
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

// 每日免费次数上限（游客 1 次，正式用户 3 次）
const isGuest = ref(getUserIsGuest())
const DAILY_FREE_LIMIT = computed(() => isGuest.value ? 1 : 3)

// ========== 事件卡池 ========== //
type PoolTab = 'keyword' | 'event'
const activeTab = ref<PoolTab>('keyword')

// 事件卡抽卡结果
const drawnEventCard = ref<EventDrawResult | null>(null)
const isDrawingEvent = ref(false)
const eventDrawError = ref<string | null>(null)
const eventDrawId = ref(0)
const eventCardRevealed = ref(false)
const hasDrawnEvent = ref(false)

// C-07: 手牌上限提示弹窗
const showLimitModal = ref(false)
const limitErrorMessage = ref('')

// ========== D-04 卡池分包扩展 ========== //
const expansions = ref<CardExpansion[]>([])
const selectedExpansion = ref<string>('') // 空字符串表示"全部"

const showExpansionTabs = computed(() => expansions.value.length > 1)

function selectExpansion(code: string) {
  selectedExpansion.value = code
}

// 从 localStorage 读取已有卡牌
onMounted(async () => {
  // U-03: 确保已登录（未登录时自动触发游客登录）
  await ensureLogin()
  // 重新检查 isGuest（ensureLogin 后 localStorage 已更新）
  isGuest.value = getUserIsGuest()

  inkValueStore.loadFromStorage()
  loadDailyFreeDraws()
  loadSavedCards()
  // D-04: 加载扩展包列表
  try {
    const expansionList = await fetchExpansions()
    expansions.value = expansionList
  } catch {
    expansions.value = []
  }
  // C-08: 今日运势本地缓存，同一天不重复请求
  const today = new Date().toDateString()
  const cacheKey = `fortune:${today}`
  const cached = localStorage.getItem(cacheKey)
  if (cached) {
    try {
      const parsed = JSON.parse(cached)
      fortuneText.value = parsed.fortune || ''
      fortuneHint.value = parsed.hint || ''
    } catch {
      fortuneText.value = ''
    }
  } else {
    // 调用后端 API 获取今日运势（基于日期+用户ID确定性选取）
    try {
      const res = await fetchFortune()
      fortuneText.value = res.fortune
      fortuneHint.value = res.hint
      // 缓存到 localStorage
      localStorage.setItem(cacheKey, JSON.stringify({
        fortune: res.fortune,
        hint: res.hint,
      }))
    } catch {
      // API 不可用时，使用本地运势库计算
      const userId = localStorage.getItem('userId') || localStorage.getItem('token') || 'guest'
      const local = computeLocalFortune(userId)
      fortuneText.value = local.fortune
      fortuneHint.value = local.hint
      localStorage.setItem(cacheKey, JSON.stringify({
        fortune: local.fortune,
        hint: local.hint,
      }))
    }
  }
  // 淡入动画：每日首次进入时触发
  if (fortuneText.value) {
    setTimeout(() => { fortuneVisible.value = true }, 300)
  }
})

function loadDailyFreeDraws() {
  const saved = localStorage.getItem('dailyFreeDraws')
  const today = new Date().toDateString()
  if (saved) {
    const data = JSON.parse(saved)
    if (data.date === today) {
      remainingFreeDraws.value = data.remaining
    } else {
      // 新的一天，重置
      remainingFreeDraws.value = DAILY_FREE_LIMIT.value
      saveDailyFreeDraws()
    }
  } else {
    remainingFreeDraws.value = DAILY_FREE_LIMIT.value
    saveDailyFreeDraws()
  }
}

function saveDailyFreeDraws() {
  const today = new Date().toDateString()
  localStorage.setItem('dailyFreeDraws', JSON.stringify({
    date: today,
    remaining: remainingFreeDraws.value,
  }))
}

function loadSavedCards() {
  const saved = localStorage.getItem('ownedKeywordCards')
  if (saved) {
    const cards: KeywordCard[] = JSON.parse(saved)
    // 恢复到 store
    cards.forEach(card => {
      if (!cardStore.keywordCards.find(c => c.id === card.id)) {
        cardStore.keywordCards.push(card)
      }
    })
  }
}

function saveCardsToStorage() {
  localStorage.setItem('ownedKeywordCards', JSON.stringify(cardStore.keywordCards))
}

async function applyMockDraw(currentDrawId = -1) {
  // 使用本地 mock 数据（模拟抽卡动画 1.5s）
  await new Promise(resolve => setTimeout(resolve, 1500))
  // 如果 drawId 已变化（用户已重置），忽略本次结果
  if (drawId.value !== currentDrawId) return
  const mockCards: KeywordCard[] = [
    { id: Date.now(), name: '铜锁芯', rarity: 1, category: 1, inkFragrance: 7 },
    { id: Date.now() + 1, name: '竹骨伞', rarity: 2, category: 2, inkFragrance: 5 },
    { id: Date.now() + 2, name: '旧书页', rarity: 1, category: 3, inkFragrance: 3 },
    { id: Date.now() + 3, name: '黄昏', rarity: 3, category: 4, inkFragrance: 6 },
    { id: Date.now() + 4, name: '说书人', rarity: 4, category: 5, inkFragrance: 7 },
    { id: Date.now() + 5, name: '青花瓷', rarity: 2, category: 1, inkFragrance: 4 },
    { id: Date.now() + 6, name: '老邮差', rarity: 3, category: 2, inkFragrance: 5 },
    { id: Date.now() + 7, name: '春雷', rarity: 4, category: 3, inkFragrance: 7 },
    { id: Date.now() + 8, name: '乡愁', rarity: 3, category: 4, inkFragrance: 6 },
    { id: Date.now() + 9, name: '旅人', rarity: 2, category: 5, inkFragrance: 5 },
  ]
  const cardData = mockCards[Math.floor(Math.random() * mockCards.length)]
  remainingFreeDraws.value = Math.max(0, remainingFreeDraws.value - 1)
  drawnCard.value = { ...cardData, drawnAt: new Date().toISOString() }
  hasDrawn.value = true
  cardRevealed.value = false

  const exists = cardStore.keywordCards.find(c => c.id === cardData.id)
  if (!exists) {
    cardStore.keywordCards.push(drawnCard.value)
    saveCardsToStorage()
    inkValueStore.awardInkForDraw(cardData.rarity)
    // P-02: check combination achievements when new card is collected
    achievementStore.checkCombinationAchievements(cardStore.keywordCards, cardStore.eventCards)
  }
  saveDailyFreeDraws()
  playInkDrop()
}

async function handleDraw() {
  if (isDrawing.value || hasDrawn.value) return

  isDrawing.value = true
  drawError.value = null
  // 递增 drawId，后续可检测是否已取消
  const currentDrawId = ++drawId.value

  try {
    // 尝试调用真实 API
    let cardData: KeywordCard | null = null

    try {
      const res = await drawKeywordCard()
      // 如果 drawId 已变化（用户已重置），忽略本次结果
      if (drawId.value !== currentDrawId) return
      cardData = res.card
      remainingFreeDraws.value = res.remainingFreeDraws ?? Math.max(0, remainingFreeDraws.value - 1)
      // 后端已处理墨晶扣减（isFree=false 时），前端需同步本地状态
      if (res.isFree === false) {
        cardStore.deductInkStone(10)
      }
    } catch (err: any) {
      // 如果后端返回了业务错误码（如卡匣已满），展示错误信息，不走 mock
      const serverMsg = err?.response?.data?.message
      if (serverMsg) {
        // C-07: 手牌上限超限 → 弹窗提示 + 前往卡匣按钮
        if (
          serverMsg.includes('关键词卡已达上限') ||
          serverMsg.includes('事件卡已达上限') ||
          serverMsg.includes('手牌已满')
        ) {
          limitErrorMessage.value = serverMsg
          showLimitModal.value = true
        } else {
          drawError.value = serverMsg
        }
        isDrawing.value = false
        return
      }
      // 无响应（网络错误）时使用本地模拟（带 drawId 检查）
      console.warn('[PoolView] API unavailable, using mock data')
      await applyMockDraw(currentDrawId)
      return
    }

    if (cardData) {
      drawnCard.value = { ...cardData, drawnAt: new Date().toISOString() }
      hasDrawn.value = true
      cardRevealed.value = false

      // 保存到 store 和 localStorage
      const exists = cardStore.keywordCards.find(c => c.id === cardData!.id)
      if (!exists) {
        cardStore.keywordCards.push(drawnCard.value)
        saveCardsToStorage()
        // 记录墨香值
        inkValueStore.awardInkForDraw(cardData!.rarity)
        // P-02: check combination achievements when new card is collected
        achievementStore.checkCombinationAchievements(cardStore.keywordCards, cardStore.eventCards)
      }
      playInkDrop()
    }

    saveDailyFreeDraws()
  } catch (err: any) {
    // 优先使用后端返回的错误信息
    const msg = err?.response?.data?.message
    drawError.value = msg || '抽卡失败，请稍后再试'
    console.error('[PoolView] draw failed:', err)
  } finally {
    isDrawing.value = false
  }
}

// ========== 事件卡抽卡 ========== //

async function handleEventDraw() {
  if (isDrawingEvent.value || hasDrawnEvent.value) return

  isDrawingEvent.value = true
  eventDrawError.value = null
  const currentId = ++eventDrawId.value

  try {
    const res = await drawEventCard()
    if (eventDrawId.value !== currentId) return
    drawnEventCard.value = res
    hasDrawnEvent.value = true
    eventCardRevealed.value = false
  } catch (err: any) {
    const msg = err?.response?.data?.message
    // C-07: 手牌上限超限 → 弹窗提示 + 前往卡匣按钮
    if (
      msg?.includes('关键词卡已达上限') ||
      msg?.includes('事件卡已达上限') ||
      msg?.includes('手牌已满')
    ) {
      limitErrorMessage.value = msg
      showLimitModal.value = true
    } else {
      eventDrawError.value = msg || '抽卡失败，请稍后再试'
    }
    console.error('[PoolView] event draw failed:', err)
  } finally {
    isDrawingEvent.value = false
  }
}

function resetEventDraw() {
  ++eventDrawId.value
  hasDrawnEvent.value = false
  drawnEventCard.value = null
  eventDrawError.value = null
  eventCardRevealed.value = false
}

function onEventCardRevealed() {
  eventCardRevealed.value = true
  playWindChime()
}

function switchTab(tab: PoolTab) {
  activeTab.value = tab
}

function reset() {
  // 递增 drawId 以取消进行中的抽卡
  ++drawId.value
  hasDrawn.value = false
  drawnCard.value = null
  drawError.value = null
  cardRevealed.value = false
}

function onCardRevealed() {
  cardRevealed.value = true
  playWindChime()
}

function goToCards() {
  showLimitModal.value = false
  router.push('/cards')
}

function closeLimitModal() {
  showLimitModal.value = false
}
</script>

<template>
  <div class="pool-view" data-testid="ink-pool-container">
    <header class="pool-header">
      <div class="pool-title-row">
        <h2 data-testid="ink-pool-title">抽卡</h2>
        <div class="tab-switch">
          <button
            class="tab-btn"
            :class="{ active: activeTab === 'keyword' }"
            @click="switchTab('keyword')"
          >墨池</button>
          <button
            class="tab-btn"
            :class="{ active: activeTab === 'event' }"
            @click="switchTab('event')"
          >事件卡池</button>
        </div>
      </div>
      <div class="header-right">
        <InkLevelBadge />
        <div v-if="activeTab === 'keyword'" class="free-draws">
          <span class="free-label">今日免费</span>
          <span class="free-count" data-testid="free-draws-count">{{ remainingFreeDraws }}/{{ DAILY_FREE_LIMIT }}</span>
        </div>
      </div>
    </header>

    <!-- 墨池（关键词卡） -->
    <div v-if="activeTab === 'keyword'" class="pool-stage">
      <RippleEffect :active="!hasDrawn" data-testid="ink-ripple-animation" />
      <InkPool v-if="!hasDrawn" @draw="handleDraw" data-testid="ink-pool-surface" />

      <!-- 今日运势预兆文字（C-08 墨迹占卜） -->
      <div
        v-if="!hasDrawn && currentFortune"
        class="fortune-wrapper"
        :class="{ visible: fortuneVisible }"
      >
        <div class="fortune-paper">
          <p class="fortune-text" data-testid="fortune-text">{{ fortuneText }}</p>
          <p class="fortune-hint" data-testid="fortune-category">{{ fortuneHint }}</p>
        </div>
      </div>

      <div v-if="hasDrawn && drawnCard" class="card-reveal" data-testid="card-reveal-container">
        <!-- ScratchCard: handles ink-wipe reveal interaction -->
        <ScratchCard
          v-if="!cardRevealed"
          :card="drawnCard"
          @revealed="onCardRevealed"
        />

        <!-- After scratch-reveal: show plain card + action buttons -->
        <div v-if="cardRevealed" class="post-reveal">
          <Card :card="drawnCard" />
          <button
            v-if="remainingFreeDraws > 0"
            class="reset-btn"
            data-testid="card-modal-close"
            @click="reset"
          >关闭</button>
          <button
            v-else
            class="reset-btn"
            data-testid="draw-again-button"
            @click="handleDraw"
          >再抽一张</button>
        </div>
      </div>

      <!-- 抽卡按钮浮层 -->
      <button
        v-if="!hasDrawn"
        class="draw-btn"
        data-testid="draw-btn"
        :disabled="isDrawing || remainingFreeDraws <= 0"
        @click="handleDraw"
      >
        <span v-if="isDrawing">墨迹涌动中...</span>
        <span v-else-if="remainingFreeDraws <= 0">今日免费次数已用尽</span>
        <span v-else>轻触墨池抽取</span>
      </button>
    </div>

    <!-- 已有卡牌展示 -->
    <section v-if="cardStore.keywordCards.length > 0" class="owned-cards">
      <h3 class="section-title">已收集 ({{ cardStore.keywordCards.length }})</h3>
      <div class="card-list">
        <div
          v-for="card in cardStore.keywordCards"
          :key="card.id"
          class="owned-card-item"
          :class="`rarity-${card.rarity}`"
        >
          <span class="item-name">{{ card.name }}</span>
          <span class="item-rarity">{{ ['', '凡', '珍', '奇', '绝'][card.rarity] }}</span>
        </div>
      </div>
    </section>

    <p v-if="drawError" class="draw-error">{{ drawError }}</p>
  </div>

  <!-- 事件卡池 -->
  <div v-if="activeTab === 'event'" class="event-pool-stage">
    <!-- D-04: 扩展包切换 Tab（扩展包数量 > 1 时显示） -->
    <div v-if="showExpansionTabs" class="expansion-tabs">
      <button
        class="expansion-tab"
        :class="{ active: selectedExpansion === '' }"
        @click="selectExpansion('')"
      >全部</button>
      <button
        v-for="exp in expansions"
        :key="exp.expansionCode"
        class="expansion-tab"
        :class="{ active: selectedExpansion === exp.expansionCode }"
        @click="selectExpansion(exp.expansionCode)"
      >{{ exp.expansionName }}</button>
    </div>
    <RippleEffect :active="!hasDrawnEvent" data-testid="event-ripple-animation" />

    <!-- 事件卡展示 -->
    <div v-if="hasDrawnEvent && drawnEventCard" class="card-reveal" data-testid="event-card-reveal">
      <div v-if="!eventCardRevealed">
        <!-- 擦墨显现动画 -->
        <ScratchCard
          :card="drawnEventCard as any"
          @revealed="onEventCardRevealed"
        />
      </div>
      <div v-if="eventCardRevealed" class="post-reveal">
        <div class="event-card-display">
          <div class="event-card-inner">
            <div class="event-era">{{ drawnEventCard.era }}</div>
            <h3 class="event-title">{{ drawnEventCard.title }}</h3>
            <div class="event-meta">
              <span class="event-dynasty">{{ drawnEventCard.dynasty }}</span>
              <span class="event-location">{{ drawnEventCard.location }}</span>
            </div>
            <p class="event-description">{{ drawnEventCard.description }}</p>
            <div v-if="drawnEventCard.isGuaranteedRare" class="event-badge">珍品</div>
          </div>
        </div>
        <button class="reset-btn" data-testid="event-reset-btn" @click="resetEventDraw">再抽一张</button>
      </div>
    </div>

    <!-- 抽卡按钮 -->
    <button
      v-if="!hasDrawnEvent"
      class="draw-btn"
      data-testid="event-draw-btn"
      :disabled="isDrawingEvent"
      @click="handleEventDraw"
    >
      <span v-if="isDrawingEvent">墨迹涌动中...</span>
      <span v-else>轻触墨池抽取事件</span>
    </button>

    <p v-if="eventDrawError" class="draw-error">{{ eventDrawError }}</p>
  </div>

  <!-- C-07: 手牌上限提示弹窗 -->
  <Teleport to="body">
    <div v-if="showLimitModal" class="limit-modal-overlay" @click.self="closeLimitModal">
      <div class="limit-modal">
        <div class="limit-modal-icon">⚠</div>
        <p class="limit-modal-message">{{ limitErrorMessage }}</p>
        <div class="limit-modal-actions">
          <button class="limit-modal-btn limit-modal-btn--secondary" @click="closeLimitModal">取消</button>
          <button class="limit-modal-btn limit-modal-btn--primary" @click="goToCards">前往卡匣</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.pool-view {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: radial-gradient(ellipse at center, #1a1510 0%, #0d0a08 100%);
  color: #e8dcc8;
}

.pool-header {
  padding: 1.5rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid rgba(232, 220, 200, 0.1);
}

.pool-header h2 {
  font-size: 1.5rem;
  letter-spacing: 0.2em;
}

.pool-title-row {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.tab-switch {
  display: flex;
  border: 1px solid rgba(232, 220, 200, 0.2);
  border-radius: 2px;
  overflow: hidden;
}

.tab-btn {
  padding: 0.25rem 0.75rem;
  background: transparent;
  border: none;
  color: rgba(232, 220, 200, 0.5);
  font-family: inherit;
  font-size: 0.75rem;
  letter-spacing: 0.1em;
  cursor: pointer;
  transition: all 0.3s ease;
}

.tab-btn.active {
  background: rgba(201, 168, 76, 0.15);
  color: #c9a84c;
}

.tab-btn:not(.active):hover {
  color: rgba(232, 220, 200, 0.8);
}

/* D-04: 扩展包切换 Tab */
.expansion-tabs {
  display: flex;
  gap: 0.5rem;
  padding: 0.75rem 1.5rem;
  overflow-x: auto;
  scrollbar-width: none;
}

.expansion-tabs::-webkit-scrollbar {
  display: none;
}

.expansion-tab {
  padding: 0.3rem 0.75rem;
  background: rgba(232, 220, 200, 0.05);
  border: 1px solid rgba(232, 220, 200, 0.15);
  border-radius: 2px;
  color: rgba(232, 220, 200, 0.5);
  font-family: inherit;
  font-size: 0.75rem;
  letter-spacing: 0.1em;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.3s ease;
}

.expansion-tab.active {
  background: rgba(201, 168, 76, 0.15);
  border-color: rgba(201, 168, 76, 0.4);
  color: #c9a84c;
}

.expansion-tab:not(.active):hover {
  color: rgba(232, 220, 200, 0.8);
  border-color: rgba(232, 220, 200, 0.3);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.free-draws {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 0.1rem;
}

.free-label {
  font-size: 0.7rem;
  color: rgba(232, 220, 200, 0.5);
  letter-spacing: 0.1em;
}

.free-count {
  font-size: 0.9rem;
  color: #c9a84c;
  letter-spacing: 0.05em;
}

.pool-stage {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  gap: 1.5rem;
  padding: 2rem;
}

.card-reveal {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1.5rem;
  animation: cardReveal 0.5s ease-out;
  position: relative;
}

@keyframes cardReveal {
  from {
    opacity: 0;
    transform: scale(0.9) translateY(10px);
  }
  to {
    opacity: 1;
    transform: scale(1) translateY(0);
  }
}

.reset-btn {
  padding: 0.6rem 1.5rem;
  background: transparent;
  border: 1px solid rgba(232, 220, 200, 0.3);
  border-radius: 2px;
  color: rgba(232, 220, 200, 0.7);
  font-family: inherit;
  font-size: 0.85rem;
  cursor: pointer;
  letter-spacing: 0.1em;
  transition: all 0.3s ease;
}

.reset-btn:hover {
  border-color: rgba(232, 220, 200, 0.6);
  color: #e8dcc8;
}

.draw-btn {
  margin-top: 1.5rem;
  padding: 0.75rem 2rem;
  background: rgba(201, 168, 76, 0.15);
  border: 1px solid #c9a84c;
  border-radius: 2px;
  color: #c9a84c;
  font-family: inherit;
  font-size: 0.95rem;
  cursor: pointer;
  letter-spacing: 0.15em;
  transition: all 0.3s ease;
  z-index: 10;
}

.draw-btn:hover:not(:disabled) {
  background: rgba(201, 168, 76, 0.25);
  box-shadow: 0 0 20px rgba(201, 168, 76, 0.2);
}

.draw-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.owned-cards {
  padding: 1.5rem;
  border-top: 1px solid rgba(232, 220, 200, 0.1);
}

.section-title {
  font-size: 0.85rem;
  color: rgba(232, 220, 200, 0.5);
  letter-spacing: 0.15em;
  margin-bottom: 1rem;
}

.card-list {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.owned-card-item {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.3rem 0.75rem;
  border-radius: 2px;
  border: 1px solid;
  background: rgba(0, 0, 0, 0.2);
}

.owned-card-item.rarity-1 { border-color: #8b7355; }
.owned-card-item.rarity-2 { border-color: #6b8e6b; }
.owned-card-item.rarity-3 { border-color: #7b6ba0; }
.owned-card-item.rarity-4 { border-color: #c9a84c; }

.item-name {
  font-size: 0.8rem;
  color: #e8dcc8;
}

.item-rarity {
  font-size: 0.65rem;
  opacity: 0.6;
}

.draw-error {
  text-align: center;
  color: #c96c6c;
  font-size: 0.85rem;
  padding: 0.5rem;
}

/* 事件卡池 */
.event-pool-stage {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  gap: 1.5rem;
  padding: 2rem;
}

/* 事件卡展示 */
.event-card-display {
  width: 280px;
  background: linear-gradient(135deg, rgba(44, 44, 42, 0.9), rgba(30, 25, 20, 0.95));
  border: 1px solid rgba(201, 168, 76, 0.3);
  border-radius: 4px;
  padding: 2rem 1.5rem;
  text-align: center;
  animation: cardReveal 0.5s ease-out;
}

.event-card-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.75rem;
}

.event-era {
  font-size: 0.65rem;
  color: rgba(201, 168, 76, 0.6);
  letter-spacing: 0.2em;
  text-transform: uppercase;
}

.event-title {
  font-size: 1.1rem;
  color: #e8dcc8;
  letter-spacing: 0.1em;
  margin: 0;
  line-height: 1.4;
}

.event-meta {
  display: flex;
  gap: 0.5rem;
  font-size: 0.7rem;
  color: rgba(201, 168, 76, 0.5);
  letter-spacing: 0.1em;
}

.event-dynasty::after {
  content: '·';
  margin-left: 0.5rem;
}

.event-description {
  font-size: 0.8rem;
  color: rgba(232, 220, 200, 0.65);
  line-height: 1.6;
  letter-spacing: 0.05em;
  margin: 0.5rem 0 0;
}

.event-badge {
  margin-top: 0.5rem;
  padding: 0.2rem 0.6rem;
  background: rgba(201, 168, 76, 0.15);
  border: 1px solid rgba(201, 168, 76, 0.4);
  border-radius: 2px;
  font-size: 0.65rem;
  color: #c9a84c;
  letter-spacing: 0.15em;
}

/* 今日运势预兆文字（C-08 墨迹占卜） */
.fortune-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.4rem;
  /* 初始隐藏，由 .visible 触发淡入 */
  opacity: 0;
  transition: opacity 2s ease-out;
}

.fortune-wrapper.visible {
  opacity: 1;
}

/* 宣纸质感背景 */
.fortune-paper {
  position: relative;
  padding: 1rem 2rem;
  /* 宣纸底色：米黄暖调 */
  background:
    /* 墨渍/纤维纹理叠加 */
    url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='200' height='200'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.65' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='200' height='200' filter='url(%23n)' opacity='0.04'/%3E%3C/svg%3E"),
    /* 纵向纤维纹 */
    repeating-linear-gradient(
      90deg,
      transparent,
      transparent 3px,
      rgba(180, 160, 120, 0.04) 3px,
      rgba(180, 160, 120, 0.04) 4px
    ),
    /* 横向纸纹 */
    repeating-linear-gradient(
      0deg,
      transparent,
      transparent 6px,
      rgba(160, 140, 100, 0.03) 6px,
      rgba(160, 140, 100, 0.03) 7px
    ),
    /* 基础宣纸米色 */
    linear-gradient(135deg, rgba(245, 238, 220, 0.08) 0%, rgba(235, 228, 210, 0.05) 100%);
  border: 1px solid rgba(200, 185, 150, 0.18);
  border-radius: 2px;
  box-shadow:
    0 1px 8px rgba(0, 0, 0, 0.25),
    inset 0 0 12px rgba(0, 0, 0, 0.08);
}

/* 墨迹边框装饰（左上角） */
.fortune-paper::before {
  content: '❖';
  position: absolute;
  top: 0.3rem;
  left: 0.5rem;
  font-size: 0.5rem;
  color: rgba(160, 130, 80, 0.3);
  letter-spacing: 0;
}

/* 墨迹边框装饰（右下角） */
.fortune-paper::after {
  content: '❖';
  position: absolute;
  bottom: 0.3rem;
  right: 0.5rem;
  font-size: 0.5rem;
  color: rgba(160, 130, 80, 0.3);
  letter-spacing: 0;
}

.fortune-text {
  font-size: 0.95rem;
  color: rgba(148, 148, 145, 0.7); /* 烟灰色，主文稍浓 */
  font-family: '方正清刻本悦宋', 'FZQingKeBenYueSong', 'STKaiti', 'KaiTi', serif;
  letter-spacing: 0.12em;
  text-align: center;
  margin: 0;
  padding: 0 0.5rem;
  text-shadow: 0 0 8px rgba(180, 160, 120, 0.15);
}

.fortune-hint {
  font-size: 0.72rem;
  color: rgba(148, 148, 145, 0.45); /* 烟灰色，副文更淡 */
  font-family: '方正清刻本悦宋', 'FZQingKeBenYueSong', 'STKaiti', 'KaiTi', serif;
  letter-spacing: 0.22em;
  text-align: center;
  margin: 0;
}

/* 残片拼接墨迹遮罩 */
/* (removed: now handled by ScratchCard.vue) */

/* Post-reveal layout after scratch interaction */
.post-reveal {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
}

/* C-07: 手牌上限提示弹窗 */
.limit-modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.65);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
  backdrop-filter: blur(4px);
}

.limit-modal {
  background: linear-gradient(135deg, rgba(44, 38, 30, 0.98), rgba(28, 22, 16, 0.98));
  border: 1px solid rgba(201, 168, 76, 0.35);
  border-radius: 6px;
  padding: 2rem 1.75rem;
  max-width: 320px;
  width: 90%;
  text-align: center;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.5);
  animation: modalFadeIn 0.25s ease-out;
}

@keyframes modalFadeIn {
  from { opacity: 0; transform: scale(0.92) translateY(8px); }
  to   { opacity: 1; transform: scale(1) translateY(0); }
}

.limit-modal-icon {
  font-size: 2.5rem;
  margin-bottom: 0.75rem;
  opacity: 0.85;
}

.limit-modal-message {
  font-size: 0.9rem;
  color: #e8dcc8;
  line-height: 1.7;
  letter-spacing: 0.05em;
  margin: 0 0 1.5rem;
}

.limit-modal-actions {
  display: flex;
  gap: 0.75rem;
  justify-content: center;
}

.limit-modal-btn {
  flex: 1;
  padding: 0.6rem 1rem;
  border-radius: 3px;
  font-family: inherit;
  font-size: 0.85rem;
  letter-spacing: 0.1em;
  cursor: pointer;
  transition: all 0.2s ease;
}

.limit-modal-btn--secondary {
  background: transparent;
  border: 1px solid rgba(232, 220, 200, 0.3);
  color: rgba(232, 220, 200, 0.65);
}

.limit-modal-btn--secondary:hover {
  border-color: rgba(232, 220, 200, 0.55);
  color: rgba(232, 220, 200, 0.85);
}

.limit-modal-btn--primary {
  background: rgba(201, 168, 76, 0.15);
  border: 1px solid #c9a84c;
  color: #c9a84c;
}

.limit-modal-btn--primary:hover {
  background: rgba(201, 168, 76, 0.25);
  box-shadow: 0 0 12px rgba(201, 168, 76, 0.2);
}
</style>
