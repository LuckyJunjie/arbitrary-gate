import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

interface CardItem {
  id: number
  name: string
  rarity: number
  category: number
  imageUrl?: string
  inkFragrance?: number
  resonanceCount?: number
}

export const useCardStore = defineStore('card', () => {
  // ===== State =====
  const keywordCards = ref<CardItem[]>([])
  const eventCards = ref<CardItem[]>([])
  const inkStone = ref(100) // 墨晶余额

  // ===== Getters =====
  const totalCount = computed(() => keywordCards.value.length + eventCards.value.length)

  // ===== Actions =====
  function addKeywordCard(card: CardItem) {
    // 防止重复
    const exists = keywordCards.value.find(c => c.id === card.id)
    if (!exists) {
      keywordCards.value.push({ ...card, inkFragrance: 7, resonanceCount: 0 })
    }
  }

  function addEventCard(card: CardItem) {
    const exists = eventCards.value.find(c => c.id === card.id)
    if (!exists) {
      eventCards.value.push({ ...card })
    }
  }

  async function fetchOwnedCards() {
    // TODO: 调用 API 获取用户卡牌
  }

  function deductInkStone(amount: number) {
    if (inkStone.value >= amount) {
      inkStone.value -= amount
    }
  }

  return {
    keywordCards,
    eventCards,
    inkStone,
    totalCount,
    addKeywordCard,
    addEventCard,
    fetchOwnedCards,
    deductInkStone,
  }
})
