<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import InkPool from '@/components/InkPool.vue'
import Card from '@/components/Card.vue'
import RippleEffect from '@/components/RippleEffect.vue'
import InkLevelBadge from '@/components/InkLevelBadge.vue'
import { useCardStore } from '@/stores/cardStore'
import { useInkValueStore } from '@/stores/inkValueStore'
import { drawKeywordCard, fetchKeywordCard } from '@/services/api'
import type { KeywordCard } from '@/services/api'

const cardStore = useCardStore()
const inkValueStore = useInkValueStore()

const hasDrawn = ref(false)
const drawnCard = ref<KeywordCard | null>(null)
const isDrawing = ref(false)
const drawError = ref<string | null>(null)
const remainingFreeDraws = ref(0)
const drawId = ref(0) // 用于取消进行中的抽卡动画
const revealPhase = ref<'partial' | 'full'>('partial') // 残片拼接阶段

// 今日运势预兆文本
const fortuneLines = [
  '今日墨色偏青，似有旧物来寻。',
  '池中落了一片桂花，或有故人将至。',
  '墨浓欲凝，宜静待时机。',
  '池水微漾，珍奇隐于其下。',
  '砚中墨浅，然大机缘将至。',
  '窗外有风过堂，今日宜得一奇字。',
  '墨迹未干，池底似有微光透出。',
  '月色入水，宜候一纸良缘。',
  '落笔之处，香气隐隐似曾相识。',
  '墨香四溢，今夜当有巧遇。',
]
const fortuneText = ref('')
const currentFortune = computed(() => fortuneText.value)

// 每日免费次数上限
const DAILY_FREE_LIMIT = 3

const dailyFreeLeft = computed(() => remainingFreeDraws.value)

// 从 localStorage 读取已有卡牌
onMounted(() => {
  inkValueStore.loadFromStorage()
  loadDailyFreeDraws()
  loadSavedCards()
  // 随机选取一条今日运势
  fortuneText.value = fortuneLines[Math.floor(Math.random() * fortuneLines.length)]
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
      remainingFreeDraws.value = DAILY_FREE_LIMIT
      saveDailyFreeDraws()
    }
  } else {
    remainingFreeDraws.value = DAILY_FREE_LIMIT
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

async function onCardDrawn(card: Record<string, unknown> | null) {
  // card 从 InkPool emit 传来
  // 若 card 为 null（InkPool API 调用失败），使用 fallback 数据
  if (!card) {
    await applyMockDraw()
    return
  }
  // 正常卡片数据处理
  const cardData: KeywordCard = {
    id: card.id as number,
    name: card.name as string,
    rarity: card.rarity as number,
    category: card.category as number,
    inkFragrance: (card.inkFragrance as number) ?? 7,
  }
  drawnCard.value = { ...cardData, drawnAt: new Date().toISOString() }
  hasDrawn.value = true
  revealPhase.value = 'partial'

  const exists = cardStore.keywordCards.find(c => c.id === cardData.id)
  if (!exists) {
    cardStore.keywordCards.push(drawnCard.value)
    saveCardsToStorage()
    inkValueStore.awardInkForDraw(cardData.rarity)
  }
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
  revealPhase.value = 'partial'

  const exists = cardStore.keywordCards.find(c => c.id === cardData.id)
  if (!exists) {
    cardStore.keywordCards.push(drawnCard.value)
    saveCardsToStorage()
    inkValueStore.awardInkForDraw(cardData.rarity)
  }
  saveDailyFreeDraws()
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
    } catch {
      // API 不可用时使用本地模拟（带 drawId 检查）
      console.warn('[PoolView] API unavailable, using mock data')
      await applyMockDraw(currentDrawId)
      return
    }

    if (cardData) {
      drawnCard.value = { ...cardData, drawnAt: new Date().toISOString() }
      hasDrawn.value = true
      revealPhase.value = 'partial'

      // 保存到 store 和 localStorage
      const exists = cardStore.keywordCards.find(c => c.id === cardData!.id)
      if (!exists) {
        cardStore.keywordCards.push(drawnCard.value)
        saveCardsToStorage()
        // 记录墨香值
        inkValueStore.awardInkForDraw(cardData!.rarity)
      }
    }

    saveDailyFreeDraws()
  } catch (err) {
    drawError.value = '抽卡失败，请稍后再试'
    console.error('[PoolView] draw failed:', err)
  } finally {
    isDrawing.value = false
  }
}

function reset() {
  // 递增 drawId 以取消进行中的抽卡
  ++drawId.value
  hasDrawn.value = false
  drawnCard.value = null
  drawError.value = null
  revealPhase.value = 'partial'
}

function revealInk() {
  revealPhase.value = 'full'
}
</script>

<template>
  <div class="pool-view" data-testid="ink-pool-container">
    <header class="pool-header">
      <h2 data-testid="ink-pool-title">墨池</h2>
      <div class="header-right">
        <InkLevelBadge />
        <div class="free-draws">
          <span class="free-label">今日免费</span>
          <span class="free-count" data-testid="free-draws-count">{{ remainingFreeDraws }}/{{ DAILY_FREE_LIMIT }}</span>
        </div>
      </div>
    </header>

    <div class="pool-stage">
      <RippleEffect :active="!hasDrawn" data-testid="ink-ripple-animation" />
      <InkPool v-if="!hasDrawn" @draw="handleDraw" data-testid="ink-pool-surface" />

      <!-- 今日运势预兆文字 -->
      <p
        v-if="!hasDrawn && currentFortune"
        class="fortune-text"
        data-testid="fortune-text"
      >{{ currentFortune }}</p>

      <div v-if="hasDrawn && drawnCard" class="card-reveal" data-testid="card-reveal-container">
        <!-- 残片拼接：墨迹遮罩层 -->
        <div
          v-if="revealPhase === 'partial'"
          class="ink-overlay"
          data-testid="reveal-ink-overlay"
        >
          <!-- 中心透明圆孔，只露出卡名首字 -->
          <div class="ink-hollow">
            <span class="hint-char">{{ drawnCard.name.charAt(0) }}</span>
          </div>
        </div>

        <Card :card="drawnCard" />

        <!-- 拂去墨迹按钮（残片拼接阶段） -->
        <button
          v-if="revealPhase === 'partial'"
          class="reset-btn brush-ink-btn"
          data-testid="brush-ink-button"
          @click="revealInk"
        >
          <span class="brush-icon">💧</span>拂去墨迹
        </button>

        <!-- 免费次数剩余时，点击关闭卡片再点击墨池继续免费抽卡 -->
        <button
          v-if="revealPhase === 'full' && remainingFreeDraws > 0"
          class="reset-btn"
          data-testid="card-modal-close"
          @click="reset"
        >关闭</button>
        <!-- 免费次数用尽时，点击触发付费抽卡 -->
        <button
          v-if="revealPhase === 'full' && remainingFreeDraws <= 0"
          class="reset-btn"
          data-testid="draw-again-button"
          @click="handleDraw"
        >再抽一张</button>
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

/* 今日运势预兆文字 */
.fortune-text {
  font-size: 0.85rem;
  color: rgba(232, 220, 200, 0.35);
  font-style: italic;
  letter-spacing: 0.1em;
  text-align: center;
  margin: 0;
  padding: 0 1rem;
  animation: fortuneFadeIn 1s ease-out;
}

@keyframes fortuneFadeIn {
  from { opacity: 0; transform: translateY(4px); }
  to   { opacity: 1; transform: translateY(0); }
}

/* 残片拼接墨迹遮罩 */
.ink-overlay {
  position: absolute;
  inset: 0;
  background: radial-gradient(ellipse at center, rgba(15, 12, 8, 0.85) 0%, rgba(5, 3, 2, 0.95) 100%);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 10;
  animation: inkOverlayIn 0.3s ease-out;
  transition: opacity 0.5s ease;
}

.ink-overlay.fading {
  opacity: 0;
}

/* 中心透明圆孔 */
.ink-hollow {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: radial-gradient(circle, transparent 0%, transparent 60%, rgba(30, 20, 10, 0.8) 100%);
  box-shadow: 0 0 0 9999px rgba(10, 8, 5, 0.7);
  display: flex;
  align-items: center;
  justify-content: center;
}

.hint-char {
  font-size: 2.5rem;
  color: rgba(232, 220, 200, 0.2);
  font-weight: 700;
  letter-spacing: 0.05em;
  user-select: none;
  position: relative;
  z-index: 1;
}

@keyframes inkOverlayIn {
  from { opacity: 0; }
  to   { opacity: 1; }
}

/* 拂去墨迹按钮 */
.brush-ink-btn {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.6rem 1.5rem;
  background: rgba(201, 168, 76, 0.1);
  border: 1px solid rgba(201, 168, 76, 0.4);
  border-radius: 2px;
  color: rgba(201, 168, 76, 0.8);
  font-family: inherit;
  font-size: 0.85rem;
  cursor: pointer;
  letter-spacing: 0.1em;
  transition: all 0.3s ease;
}

.brush-ink-btn:hover {
  background: rgba(201, 168, 76, 0.2);
  border-color: rgba(201, 168, 76, 0.7);
  color: #c9a84c;
}

.brush-icon {
  font-size: 1rem;
}
</style>
