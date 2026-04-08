import { ref } from 'vue'
import { api } from '@/services/api'
import { useCardStore } from '@/stores/cardStore'

interface DrawResult {
  id: number
  name: string
  rarity: number
  category: number
  imageUrl?: string
}

export function useCardDraw() {
  const isDrawing = ref(false)
  const cardStore = useCardStore()

  async function drawCard(type: 'keyword' | 'event'): Promise<DrawResult | null> {
    if (isDrawing.value) return null
    isDrawing.value = true

    try {
      // TODO: 调用实际 API
      // const data = await api.post(`/card/draw/${type}`)
      // 模拟返回
      await new Promise(resolve => setTimeout(resolve, 1500))

      const mockCards: DrawResult[] = [
        { id: 1, name: '铜锁芯', rarity: 1, category: 1 },
        { id: 2, name: '竹骨伞', rarity: 2, category: 2 },
        { id: 3, name: '旧书页', rarity: 1, category: 3 },
        { id: 4, name: '黄昏', rarity: 3, category: 4 },
        { id: 5, name: '说书人', rarity: 4, category: 5 },
      ]

      const card = mockCards[Math.floor(Math.random() * mockCards.length)]

      // 保存到 store
      if (type === 'keyword') {
        cardStore.addKeywordCard(card)
      } else {
        cardStore.addEventCard(card)
      }

      return card
    } catch (err) {
      console.error('[useCardDraw] draw failed:', err)
      return null
    } finally {
      isDrawing.value = false
    }
  }

  return { drawCard, isDrawing }
}
