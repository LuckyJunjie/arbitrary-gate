import { ref } from 'vue'
import { drawKeywordCard } from '@/services/api'
import { useCardStore } from '@/stores/cardStore'

interface DrawResult {
  id: number
  name: string
  rarity: number
  category: number
  imageUrl?: string
}

const STORAGE_KEY = 'arbitrary_gate_draw_history'

export function useCardDraw() {
  const isDrawing = ref(false)
  const drawError = ref<string | null>(null)
  const cardStore = useCardStore()

  function saveDrawHistory(card: DrawResult, type: 'keyword' | 'event') {
    try {
      const raw = localStorage.getItem(STORAGE_KEY)
      const history: Array<DrawResult & { type: string; drawnAt: string }> = raw ? JSON.parse(raw) : []
      history.unshift({ ...card, type, drawnAt: new Date().toISOString() })
      // 保留最近 50 条
      localStorage.setItem(STORAGE_KEY, JSON.stringify(history.slice(0, 50)))
    } catch {
      // ignore storage errors
    }
  }

  async function drawCard(type: 'keyword' | 'event'): Promise<DrawResult | null> {
    if (isDrawing.value) return null
    isDrawing.value = true
    drawError.value = null

    try {
      if (type === 'keyword') {
        const data = await drawKeywordCard()
        const card: DrawResult = {
          id: data.card.id,
          name: data.card.name,
          rarity: data.card.rarity,
          category: data.card.category,
          imageUrl: data.card.imageUrl,
        }

        cardStore.addKeywordCard({
          ...card,
          inkFragrance: data.card.inkFragrance ?? 7,
          resonanceCount: data.card.resonanceCount ?? 0,
        })

        saveDrawHistory(card, type)
        return card
      } else {
        // event 卡暂时走原有逻辑（后端若无独立 endpoint，保留 mock）
        await new Promise(resolve => setTimeout(resolve, 1500))
        const mockCards: DrawResult[] = [
          { id: 101, name: '铜锁芯', rarity: 1, category: 1 },
          { id: 102, name: '竹骨伞', rarity: 2, category: 2 },
          { id: 103, name: '旧书页', rarity: 1, category: 3 },
          { id: 104, name: '黄昏', rarity: 3, category: 4 },
          { id: 105, name: '说书人', rarity: 4, category: 5 },
        ]
        const card = mockCards[Math.floor(Math.random() * mockCards.length)]
        cardStore.addEventCard(card)
        saveDrawHistory(card, type)
        return card
      }
    } catch (err: any) {
      console.error('[useCardDraw] draw failed:', err)
      drawError.value = err?.message ?? '抽卡失败，请重试'
      return null
    } finally {
      isDrawing.value = false
    }
  }

  return { drawCard, isDrawing, drawError }
}
