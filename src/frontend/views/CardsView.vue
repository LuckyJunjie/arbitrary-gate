<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useCardStore } from '@/stores/cardStore'
import { useInkValueStore } from '@/stores/inkValueStore'
import { aiPainter, previewJudgment, recycleCard } from '@/services/api'
import Card from '@/components/Card.vue'
import InkLevelBadge from '@/components/InkLevelBadge.vue'
import JudgmentPreview from '@/components/JudgmentPreview.vue'
import type { KeywordCard } from '@/services/api'

const router = useRouter()
const cardStore = useCardStore()
const activeTab = ref<'keyword' | 'event'>('keyword')
const activeRarity = ref<number | null>(null)

// ── 卡牌选择状态 ──
const selectedKeywordCards = ref<Array<{ id: number; name: string; rarity: number; category: number }>>([])
const selectedEventCard = ref<{ id: number; name: string; rarity: number; category: number } | null>(null)
const MAX_KEYWORD_SELECT = 3

// ── P-01 组合判词预览 ──
const showJudgmentPreview = ref(false)
const judgmentText = ref('')
const isLoadingJudgment = ref(false)

// ── C-12 陈卡回炉 ──
const showRecycleDialog = ref(false)
const recycleTargetCard = ref<{ id: number; name: string; inkFragrance?: number } | null>(null)
const isRecycling = ref(false)
const recycleSuccessMsg = ref('')

/** 长按计时器（500ms触发回炉确认） */
let longPressTimer: ReturnType<typeof setTimeout> | null = null
const LONG_PRESS_DURATION = 500

function handleCardTouchStart(card: typeof recycleTargetCard.value) {
  if (activeTab.value !== 'keyword') return
  longPressTimer = setTimeout(() => {
    recycleTargetCard.value = card
    showRecycleDialog.value = true
  }, LONG_PRESS_DURATION)
}

function handleCardTouchEnd() {
  if (longPressTimer) {
    clearTimeout(longPressTimer)
    longPressTimer = null
  }
}

async function confirmRecycle() {
  if (!recycleTargetCard.value) return
  isRecycling.value = true
  try {
    const res = await recycleCard(recycleTargetCard.value.id)
    // 从列表中移除该卡（淡出动画）
    const idx = cardStore.keywordCards.findIndex(c => c.id === recycleTargetCard.value!.id)
    if (idx !== -1) {
      cardStore.keywordCards.splice(idx, 1)
      cardStore.syncToStorage()
    }
    showRecycleDialog.value = false
    recycleSuccessMsg.value = `墨已回归墨池，今日获得${res.freeDrawsRemaining}次免费抽卡机会`
    setTimeout(() => { recycleSuccessMsg.value = '' }, 4000)
  } catch (err) {
    console.error('[CardsView] recycle failed:', err)
    alert('回炉失败：' + (err instanceof Error ? err.message : String(err)))
  } finally {
    isRecycling.value = false
    recycleTargetCard.value = null
  }
}

function cancelRecycle() {
  showRecycleDialog.value = false
  recycleTargetCard.value = null
  handleCardTouchEnd()
}

function openRecycleDialog(card: typeof recycleTargetCard.value) {
  recycleTargetCard.value = card
  showRecycleDialog.value = true
}

const canStartStory = computed(() =>
  selectedKeywordCards.value.length === MAX_KEYWORD_SELECT && selectedEventCard.value !== null
)

function toggleKeywordCard(card: KeywordCard | Record<string, unknown>) {
  const id = card.id
  const idx = selectedKeywordCards.value.findIndex(c => c.id === id)
  if (idx >= 0) {
    selectedKeywordCards.value.splice(idx, 1)
  } else if (selectedKeywordCards.value.length < MAX_KEYWORD_SELECT) {
    selectedKeywordCards.value.push({
      id: card.id as number,
      name: (card as any).name || String(id),
      rarity: (card as any).rarity || 1,
      category: (card as any).category || 1,
    })
  }
}

function toggleEventCard(card: KeywordCard | Record<string, unknown>) {
  const id = card.id
  if (selectedEventCard.value?.id === id) {
    selectedEventCard.value = null
  } else {
    selectedEventCard.value = {
      id: card.id as number,
      name: (card as any).name || String(id),
      rarity: (card as any).rarity || 1,
      category: (card as any).category || 1,
    }
  }
}

function isKeywordSelected(id: number) {
  return selectedKeywordCards.value.some(c => c.id === id)
}

function isEventSelected(id: number) {
  return selectedEventCard.value?.id === id
}

async function goToEntryQuestions() {
  if (!canStartStory.value) return

  // 保存已选卡牌到 localStorage，供 EntryQuestionsView 使用
  localStorage.setItem('selectedKeywordCards', JSON.stringify(selectedKeywordCards.value))
  localStorage.setItem('selectedEventCard', JSON.stringify(selectedEventCard.value))

  // P-01: 调用 AI 生成组合判词
  isLoadingJudgment.value = true
  try {
    const res = await previewJudgment({
      keywordIds: selectedKeywordCards.value.map(c => c.id),
      eventId: selectedEventCard.value?.id,
    })
    judgmentText.value = res.judgment ?? '墨中藏命，缘起无形。'
  } catch (err) {
    console.warn('[CardsView] previewJudgment failed, using fallback:', err)
    judgmentText.value = '墨中藏命，缘起无形。'
  } finally {
    isLoadingJudgment.value = false
  }

  // 展示判词浮层
  showJudgmentPreview.value = true
}

function onJudgmentConfirm() {
  showJudgmentPreview.value = false
  router.push('/entry-questions')
}

function onJudgmentCancel() {
  showJudgmentPreview.value = false
}

const filteredCards = computed(() => {
  const cards = activeTab.value === 'keyword'
    ? cardStore.keywordCards
    : cardStore.eventCards
  if (activeRarity.value === null) return cards
  return cards.filter(c => c.rarity === activeRarity.value)
})

const isEmpty = computed(() => filteredCards.value.length === 0)

const _emptyMessage = computed(() => {
  if (cardStore.totalCount === 0) return '卡匣空空如也，去抽一张命运之卡吧'
  if (activeRarity.value !== null) {
    const rarityLabel = activeRarity.value === 1 ? '凡' : activeRarity.value === 2 ? '珍' : activeRarity.value === 3 ? '奇' : '绝'
    return `没有${rarityLabel}级卡牌`
  }
  return '此类中暂无卡牌'
})

function handleCardFlip(_card: KeywordCard | Record<string, unknown> | null) {
  // 翻转时不做额外处理，Card 组件自行管理
}

// ── AI 画师状态 ──
const aiExpanded = ref(false)
const aiStylePrompt = ref('')
const isGenerating = ref(false)
const aiResultImage = ref<string | null>(null)
const aiResultCached = ref(false)

async function generateAICardImage() {
  if (!selectedKeywordCards.value.length) return
  const card = selectedKeywordCards.value[0]
  isGenerating.value = true
  try {
    const result = await aiPainter.generateKeywordCard({
      cardName: card.name,
      cardType: 'keyword',
      rarity: (['凡', '珍', '奇', '绝'] as const)[card.rarity - 1] ?? '凡',
      style: aiStylePrompt.value || undefined,
    })
    aiResultImage.value = result.imageUrl
    aiResultCached.value = result.cached
    // Save to cardStore if not already set
    const existing = cardStore.keywordCards.find(c => c.id === card.id)
    if (existing && !existing.imageUrl) {
      existing.imageUrl = result.imageUrl
      cardStore.syncToStorage()
    }
  } finally {
    isGenerating.value = false
  }
}
</script>

<template>
  <div class="cards-view">
    <header class="cards-header">
      <div class="header-left">
        <h2>卡匣</h2>
        <p class="card-count">共 {{ cardStore.totalCount }} 张</p>
      </div>
      <div class="header-right">
        <InkLevelBadge />
      </div>
    </header>

    <nav class="filter-tabs">
      <button
        :class="['tab', { active: activeTab === 'keyword' }]"
        @click="activeTab = 'keyword'"
      >关键词</button>
      <button
        :class="['tab', { active: activeTab === 'event' }]"
        @click="activeTab = 'event'"
      >事件</button>
    </nav>

    <div class="rarity-filter">
      <button
        v-for="rarity in [null, 1, 2, 3, 4]"
        :key="rarity ?? 'all'"
        :class="['rarity-btn', { active: activeRarity === rarity }]"
        @click="activeRarity = rarity"
      >
        {{ rarity === null ? '全部' : ['凡', '珍', '奇', '绝'][rarity - 1] }}
      </button>
    </div>

    <!-- 多宝格卡片墙 -->
    <div class="card-grid" v-if="!isEmpty">
      <div
        v-for="card in filteredCards"
        :key="card.id"
        class="card-slot"
        :class="{
          'keyword-selected': activeTab === 'keyword' && isKeywordSelected(card.id),
          'event-selected': activeTab === 'event' && isEventSelected(card.id),
        }"
        @click="activeTab === 'keyword' ? toggleKeywordCard(card) : toggleEventCard(card)"
        @touchstart.passive="handleCardTouchStart(card as any)"
        @touchend="handleCardTouchEnd"
        @contextmenu.prevent="activeTab === 'keyword' ? openRecycleDialog(card as any) : null"
      >
        <!-- C-12 回炉按钮（关键词卡显示） -->
        <button
          v-if="activeTab === 'keyword'"
          class="recycle-btn"
          title="投入墨池回炉"
          @click.stop="openRecycleDialog(card as any)"
        >🅾️</button>
        <Card :card="card" @flip="handleCardFlip(card)" />
        <div
          v-if="(activeTab === 'keyword' && isKeywordSelected(card.id)) || (activeTab === 'event' && isEventSelected(card.id))"
          class="selected-check"
        >✓</div>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-else class="empty-state">
      <div class="empty-icon">笺</div>
      <p class="empty-message">{{ cardStore.totalCount === 0 ? '卡匣空空如也，去抽一张命运之卡吧' : '此类中暂无卡牌' }}</p>
    </div>

    <!-- AI 画师面板 -->
    <div class="ai-painter-section" v-if="selectedKeywordCards.length > 0">
      <div class="ai-painter-header" @click="aiExpanded = !aiExpanded">
        <span class="ai-painter-title">🖌️ AI 画师</span>
        <span class="ai-expand-icon">{{ aiExpanded ? '▼' : '▶' }}</span>
      </div>
      <div class="ai-painter-body" v-show="aiExpanded">
        <div class="ai-selected-preview">
          <span class="preview-label">当前卡牌：</span>
          <span class="preview-name">{{ selectedKeywordCards[0]?.name }}</span>
        </div>
        <div class="ai-prompt-area">
          <textarea
            v-model="aiStylePrompt"
            placeholder="可补充风格描述（如：雨天、夜晚、古巷…）"
            class="ai-prompt-input"
            rows="2"
          ></textarea>
        </div>
        <div class="ai-actions">
          <button
            class="ai-generate-btn"
            @click="generateAICardImage"
            :disabled="isGenerating"
          >
            {{ isGenerating ? '生成中…' : '🖌️ 挥毫作画' }}
          </button>
        </div>
        <div class="ai-result-area" v-if="aiResultImage">
          <img :src="aiResultImage" class="ai-result-image" alt="AI生成卡图" />
          <span class="ai-result-source">{{ aiResultCached ? '来源：缓存' : '来源：AI生成' }}</span>
        </div>
      </div>
    </div>

    <!-- 开始故事入口 -->
    <div class="start-story-bar" v-if="canStartStory || selectedKeywordCards.length > 0 || selectedEventCard">
      <div class="selection-summary">
        <span class="summary-label">已选关键词</span>
        <span class="summary-count">{{ selectedKeywordCards.length }}/{{ MAX_KEYWORD_SELECT }}</span>
        <span class="summary-sep">|</span>
        <span class="summary-label">事件</span>
        <span class="summary-event">{{ selectedEventCard?.name || '未选' }}</span>
      </div>
      <button
        class="start-btn"
        :disabled="!canStartStory"
        @click="goToEntryQuestions"
      >
        {{ canStartStory ? '入局三问' : `还需选${MAX_KEYWORD_SELECT - selectedKeywordCards.length}个` }}
      </button>
    </div>

    <!-- P-01 组合判词预览浮层 -->
    <JudgmentPreview
      :visible="showJudgmentPreview"
      :judgment="judgmentText"
      @confirm="onJudgmentConfirm"
      @cancel="onJudgmentCancel"
    />

    <!-- C-12 陈卡回炉确认对话框 -->
    <Teleport to="body">
      <div v-if="showRecycleDialog && recycleTargetCard" class="recycle-overlay" @click.self="cancelRecycle">
        <div class="recycle-dialog">
          <h3 class="recycle-title">投入墨池</h3>
          <p class="recycle-desc">
            确定要将「<strong>{{ recycleTargetCard.name }}</strong>」投入墨池回炉吗？
          </p>
          <p class="recycle-hint">回炉后可获得1次免费抽卡机会，今日限1次。</p>
          <div class="recycle-actions">
            <button class="recycle-confirm-btn" @click="confirmRecycle" :disabled="isRecycling">
              {{ isRecycling ? '回炉中…' : '确认回炉' }}
            </button>
            <button class="recycle-cancel-btn" @click="cancelRecycle">取消</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- C-12 回炉成功提示 -->
    <Teleport to="body">
      <div v-if="recycleSuccessMsg" class="recycle-toast">
        {{ recycleSuccessMsg }}
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.cards-view {
  min-height: 100vh;
  background: #f5efe0;
  padding-bottom: 2rem;
}

.cards-header {
  padding: 1.5rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid rgba(44, 31, 20, 0.1);
}

.header-left {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.cards-header h2 {
  font-size: 1.5rem;
  color: #2c1f14;
}

.card-count {
  font-size: 0.8rem;
  color: #8b7355;
}

.header-right {
  flex-shrink: 0;
}

.filter-tabs {
  display: flex;
  justify-content: center;
  gap: 2rem;
  padding: 1rem;
  border-bottom: 1px solid rgba(44, 31, 20, 0.1);
}

.tab {
  background: none;
  border: none;
  font-family: inherit;
  font-size: 1rem;
  color: #8b7355;
  cursor: pointer;
  padding: 0.25rem 0;
  border-bottom: 2px solid transparent;
  transition: all 0.2s;
}

.tab.active {
  color: #2c1f14;
  border-bottom-color: #8b7355;
}

.rarity-filter {
  display: flex;
  justify-content: center;
  gap: 0.5rem;
  padding: 0.75rem;
  flex-wrap: wrap;
}

.rarity-btn {
  padding: 0.25rem 0.75rem;
  border: 1px solid #8b7355;
  border-radius: 2px;
  background: transparent;
  font-family: inherit;
  font-size: 0.8rem;
  color: #8b7355;
  cursor: pointer;
  transition: all 0.2s;
}

.rarity-btn.active {
  background: #2c1f14;
  color: #f5efe0;
  border-color: #2c1f14;
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(100px, 1fr));
  gap: 0.75rem;
  padding: 1rem;
}

.card-slot {
  display: flex;
  align-items: center;
  justify-content: center;
}

/* Empty state */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 4rem 1rem;
  gap: 1rem;
}

.empty-icon {
  width: 64px;
  height: 64px;
  border: 2px solid rgba(139, 115, 85, 0.3);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 2rem;
  color: rgba(139, 115, 85, 0.5);
  background: rgba(139, 115, 85, 0.05);
}

.empty-message {
  font-size: 0.9rem;
  color: #8b7355;
  text-align: center;
  max-width: 260px;
  line-height: 1.6;
}

/* 选中状态 */
.card-slot {
  cursor: pointer;
  position: relative;
  transition: transform 0.2s;
}

.card-slot.keyword-selected,
.card-slot.event-selected {
  transform: scale(0.97);
}

.card-slot.keyword-selected::after,
.card-slot.event-selected::after {
  content: '';
  position: absolute;
  inset: -2px;
  border: 2px solid #c9a84c;
  border-radius: 8px;
  pointer-events: none;
}

.selected-check {
  position: absolute;
  top: -6px;
  right: -6px;
  width: 20px;
  height: 20px;
  background: #c9a84c;
  color: #1a1510;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.7rem;
  font-weight: 700;
  z-index: 2;
  box-shadow: 0 1px 4px rgba(0,0,0,0.3);
}

/* 开始故事入口 */
.start-story-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 0.75rem 1rem;
  background: rgba(26, 21, 16, 0.95);
  backdrop-filter: blur(8px);
  border-top: 1px solid rgba(139, 115, 85, 0.3);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  z-index: 10;
}

.selection-summary {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.75rem;
  color: rgba(232, 220, 200, 0.7);
}

.summary-label {
  color: rgba(139, 115, 85, 0.8);
}

.summary-count {
  color: #c9a84c;
  font-weight: 600;
}

.summary-sep {
  color: rgba(139, 115, 85, 0.4);
  margin: 0 0.2rem;
}

.summary-event {
  color: #c4a882;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.start-btn {
  padding: 0.5rem 1.25rem;
  background: linear-gradient(135deg, #4a3520, #2c1f14);
  border: 1px solid #c9a84c;
  border-radius: 3px;
  color: #c9a84c;
  font-family: inherit;
  font-size: 0.85rem;
  cursor: pointer;
  letter-spacing: 0.1em;
  transition: all 0.2s;
}

.start-btn:hover:not(:disabled) {
  background: linear-gradient(135deg, #6b4c30, #4a3520);
  color: #e8dcc8;
  border-color: #e8dcc8;
}

.start-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

/* ── AI 画师面板 ── */
.ai-painter-section {
  margin: 1rem;
  border: 1px solid rgba(139, 115, 85, 0.35);
  border-radius: 8px;
  background: rgba(44, 31, 20, 0.06);
  overflow: hidden;
}

.ai-painter-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.75rem 1rem;
  cursor: pointer;
  user-select: none;
  background: rgba(44, 31, 20, 0.04);
  border-bottom: 1px solid rgba(139, 115, 85, 0.2);
  transition: background 0.2s;
}

.ai-painter-header:hover {
  background: rgba(44, 31, 20, 0.08);
}

.ai-painter-title {
  font-size: 0.95rem;
  color: #2c1f14;
  font-weight: 600;
  letter-spacing: 0.05em;
}

.ai-expand-icon {
  color: #8b7355;
  font-size: 0.7rem;
}

.ai-painter-body {
  padding: 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.ai-selected-preview {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.8rem;
}

.preview-label {
  color: #8b7355;
}

.preview-name {
  color: #2c1f14;
  font-weight: 600;
}

.ai-prompt-area {
  width: 100%;
}

.ai-prompt-input {
  width: 100%;
  padding: 0.5rem 0.75rem;
  background: rgba(255, 255, 255, 0.6);
  border: 1px solid rgba(139, 115, 85, 0.3);
  border-radius: 4px;
  font-family: inherit;
  font-size: 0.85rem;
  color: #2c1f14;
  resize: none;
  outline: none;
  transition: border-color 0.2s;
  box-sizing: border-box;
}

.ai-prompt-input::placeholder {
  color: rgba(139, 115, 85, 0.5);
}

.ai-prompt-input:focus {
  border-color: #8b7355;
}

.ai-actions {
  display: flex;
  gap: 0.5rem;
}

.ai-generate-btn {
  padding: 0.5rem 1.25rem;
  background: linear-gradient(135deg, #4a3520, #2c1f14);
  border: 1px solid #c9a84c;
  border-radius: 3px;
  color: #c9a84c;
  font-family: inherit;
  font-size: 0.85rem;
  cursor: pointer;
  letter-spacing: 0.08em;
  transition: all 0.2s;
}

.ai-generate-btn:hover:not(:disabled) {
  background: linear-gradient(135deg, #6b4c30, #4a3520);
  color: #e8dcc8;
  border-color: #e8dcc8;
}

.ai-generate-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.ai-result-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  margin-top: 0.5rem;
}

.ai-result-image {
  max-width: 180px;
  max-height: 240px;
  width: 100%;
  object-fit: contain;
  border-radius: 4px;
  border: 1px solid rgba(139, 115, 85, 0.25);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.ai-result-source {
  font-size: 0.75rem;
  color: #8b7355;
}

/* ── C-12 回炉按钮 ── */
.recycle-btn {
  position: absolute;
  top: 6px;
  right: 6px;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  border: 1px solid rgba(139, 115, 85, 0.4);
  background: rgba(245, 239, 224, 0.85);
  backdrop-filter: blur(4px);
  font-size: 0.85rem;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.2s, transform 0.2s;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: center;
}

.card-slot:hover .recycle-btn,
.card-slot:active .recycle-btn {
  opacity: 1;
}

.recycle-btn:hover {
  transform: scale(1.1);
  background: rgba(196, 124, 90, 0.2);
}

/* ── C-12 回炉确认对话框 ── */
.recycle-overlay {
  position: fixed;
  inset: 0;
  background: rgba(26, 21, 16, 0.7);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 200;
  animation: fade-in 0.2s ease;
}

@keyframes fade-in {
  from { opacity: 0; }
  to { opacity: 1; }
}

.recycle-dialog {
  background: #f5efe0;
  border: 1px solid #8b7355;
  border-radius: 8px;
  padding: 1.8rem 2rem;
  max-width: 360px;
  width: 90%;
  text-align: center;
  color: #2c1f14;
  animation: dialog-in 0.25s cubic-bezier(0.22, 1, 0.36, 1);
}

@keyframes dialog-in {
  from { transform: scale(0.92); opacity: 0; }
  to { transform: scale(1); opacity: 1; }
}

.recycle-title {
  font-size: 1.2rem;
  margin: 0 0 1rem;
  letter-spacing: 0.1em;
  color: #2c1f14;
}

.recycle-desc {
  font-size: 0.95rem;
  line-height: 1.6;
  margin-bottom: 0.5rem;
  color: #4a3520;
}

.recycle-hint {
  font-size: 0.8rem;
  color: #8b7355;
  margin-bottom: 1.5rem;
}

.recycle-actions {
  display: flex;
  gap: 0.75rem;
  justify-content: center;
}

.recycle-confirm-btn {
  flex: 1;
  padding: 0.65rem;
  background: #2c1f14;
  color: #f5efe0;
  border: none;
  border-radius: 4px;
  font-family: inherit;
  font-size: 0.95rem;
  cursor: pointer;
  transition: background 0.2s;
}

.recycle-confirm-btn:hover:not(:disabled) {
  background: #4a3520;
}

.recycle-confirm-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.recycle-cancel-btn {
  flex: 1;
  padding: 0.65rem;
  background: transparent;
  color: #8b7355;
  border: 1px solid #8b7355;
  border-radius: 4px;
  font-family: inherit;
  font-size: 0.95rem;
  cursor: pointer;
  transition: all 0.2s;
}

.recycle-cancel-btn:hover {
  background: rgba(139, 115, 85, 0.1);
  color: #2c1f14;
}

/* ── C-12 回炉成功 Toast ── */
.recycle-toast {
  position: fixed;
  bottom: 2rem;
  left: 50%;
  transform: translateX(-50%);
  background: #2c1f14;
  color: #c4a882;
  padding: 0.7rem 1.5rem;
  border-radius: 24px;
  font-size: 0.9rem;
  z-index: 300;
  white-space: nowrap;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.4);
  animation: toast-in 0.3s cubic-bezier(0.22, 1, 0.36, 1), toast-out 0.3s ease 3.7s forwards;
}

@keyframes toast-in {
  from { transform: translateX(-50%) translateY(16px); opacity: 0; }
  to { transform: translateX(-50%) translateY(0); opacity: 1; }
}

@keyframes toast-out {
  from { opacity: 1; }
  to { opacity: 0; }
}
</style>
