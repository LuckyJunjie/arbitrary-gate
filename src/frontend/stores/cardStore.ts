import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { fetchKeywordCard } from '@/services/api'

const STORAGE_KEYWORD = 'arbitrary_gate_keyword_cards'
const STORAGE_EVENT = 'arbitrary_gate_event_cards'
const STORAGE_INK = 'arbitrary_gate_ink_stone'

interface CardItem {
  id: number
  name: string
  rarity: number
  category: number
  imageUrl?: string
  inkFragrance?: number
  resonanceCount?: number
  drawnAt?: string
}

export const useCardStore = defineStore('card', () => {
  // ===== State =====
  const keywordCards = ref<CardItem[]>([])
  const eventCards = ref<CardItem[]>([])
  const inkStone = ref(100)
  const isLoadingCards = ref(false)
  const cardsError = ref<string | null>(null)

  // ===== Getters =====
  const totalCount = computed(() => keywordCards.value.length + eventCards.value.length)

  // ===== Storage =====

  function loadFromStorage() {
    try {
      const kwRaw = localStorage.getItem(STORAGE_KEYWORD)
      if (kwRaw) keywordCards.value = JSON.parse(kwRaw)

      const evRaw = localStorage.getItem(STORAGE_EVENT)
      if (evRaw) eventCards.value = JSON.parse(evRaw)

      const inkRaw = localStorage.getItem(STORAGE_INK)
      if (inkRaw) inkStone.value = JSON.parse(inkRaw)
    } catch {
      // ignore corrupt data
    }
  }

  function syncToStorage() {
    try {
      localStorage.setItem(STORAGE_KEYWORD, JSON.stringify(keywordCards.value))
      localStorage.setItem(STORAGE_EVENT, JSON.stringify(eventCards.value))
      localStorage.setItem(STORAGE_INK, JSON.stringify(inkStone.value))
    } catch {
      // ignore storage errors
    }
  }

  // ===== Actions =====

  function addKeywordCard(card: CardItem) {
    const exists = keywordCards.value.find(c => c.id === card.id)
    if (!exists) {
      keywordCards.value.push({ ...card })
      syncToStorage()
    }
  }

  function addEventCard(card: CardItem) {
    const exists = eventCards.value.find(c => c.id === card.id)
    if (!exists) {
      eventCards.value.push({ ...card })
      syncToStorage()
    }
  }

  async function fetchOwnedCards() {
    isLoadingCards.value = true
    cardsError.value = null

    try {
      // 从后端拉取用户关键词卡（event 卡暂用本地）
      const data = await fetchKeywordCard()
      const card: CardItem = {
        id: data.card.id,
        name: data.card.name,
        rarity: data.card.rarity,
        category: data.card.category,
        imageUrl: data.card.imageUrl,
        inkFragrance: data.card.inkFragrance ?? 7,
        resonanceCount: data.card.resonanceCount ?? 0,
        drawnAt: data.card.drawnAt ?? new Date().toISOString(),
      }
      addKeywordCard(card)
    } catch (err: any) {
      cardsError.value = err?.message ?? '获取卡牌失败'
      console.error('[cardStore] fetchOwnedCards failed:', err)
      // 失败时回退到本地缓存
      loadFromStorage()
    } finally {
      isLoadingCards.value = false
    }
  }

  function deductInkStone(amount: number) {
    if (inkStone.value >= amount) {
      inkStone.value -= amount
      syncToStorage()
    }
  }

  return {
    keywordCards,
    eventCards,
    inkStone,
    totalCount,
    isLoadingCards,
    cardsError,
    loadFromStorage,
    syncToStorage,
    addKeywordCard,
    addEventCard,
    fetchOwnedCards,
    deductInkStone,
  }
})

// 供 main.ts 在 app mount 时调用的初始化函数
export async function initCardStore() {
  const store = useCardStore()
  store.loadFromStorage()
  // 静默预拉一次，失败不阻塞
  try {
    await store.fetchOwnedCards()
  } catch {
    // ignore
  }
}
