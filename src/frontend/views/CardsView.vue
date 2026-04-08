<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useCardStore } from '@/stores/cardStore'
import { useInkValueStore } from '@/stores/inkValueStore'
import Card from '@/components/Card.vue'
import InkLevelBadge from '@/components/InkLevelBadge.vue'
import type { KeywordCard } from '@/services/api'

const router = useRouter()
const cardStore = useCardStore()
const inkValueStore = useInkValueStore()
const activeTab = ref<'keyword' | 'event'>('keyword')
const activeRarity = ref<number | null>(null)

// ── 卡牌选择状态 ──
const selectedKeywordCards = ref<Array<{ id: number; name: string; rarity: number; category: number }>>([])
const selectedEventCard = ref<{ id: number; name: string; rarity: number; category: number } | null>(null)
const MAX_KEYWORD_SELECT = 3

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

function goToEntryQuestions() {
  // 保存已选卡牌到 localStorage，供 EntryQuestionsView 使用
  localStorage.setItem('selectedKeywordCards', JSON.stringify(selectedKeywordCards.value))
  localStorage.setItem('selectedEventCard', JSON.stringify(selectedEventCard.value))
  router.push('/entry-questions')
}

const filteredCards = computed(() => {
  const cards = activeTab.value === 'keyword'
    ? cardStore.keywordCards
    : cardStore.eventCards
  if (activeRarity.value === null) return cards
  return cards.filter(c => c.rarity === activeRarity.value)
})

const isEmpty = computed(() => filteredCards.value.length === 0)

const emptyMessage = computed(() => {
  if (cardStore.totalCount === 0) return '卡匣空空如也，去抽一张命运之卡吧'
  if (activeRarity.value !== null) return `没有 {{ rarity }} 级的卡牌`[
    activeRarity.value === 1 ? '凡' : activeRarity.value === 2 ? '珍' : activeRarity.value === 3 ? '奇' : '绝'
  ] ? `没有${['', '凡', '珍', '奇', '绝'][activeRarity.value!]}级卡牌` : `没有${['', '凡', '珍', '奇', '绝'][activeRarity.value!]}级卡牌`
  return '此类中暂无卡牌'
})

function handleCardFlip(card: KeywordCard | Record<string, unknown> | null) {
  // 翻转时不做额外处理，Card 组件自行管理
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
      >
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
</style>
