import { ref, computed } from 'vue'
import { useStoryStore } from '@/stores/storyStore'

const STORY_STATE_KEY = 'arbitrary_gate_story_state'

export function useStory() {
  const storyStore = useStoryStore()

  const currentChapter = computed(() => storyStore.currentChapter)
  const isLoading = ref(false)
  const storyError = ref<string | null>(null)

  // SSE reconnect state (保留结构，后续 SSE 流式章节时可扩展)
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null
  const maxReconnectAttempts = 3
  let reconnectAttempts = 0

  function saveStoryState() {
    try {
      const state = {
        story: storyStore.currentStory,
        chapter: storyStore.currentChapter,
        chapters: storyStore.chapters,
        manuscript: storyStore.manuscript,
        savedAt: new Date().toISOString(),
      }
      localStorage.setItem(STORY_STATE_KEY, JSON.stringify(state))
    } catch {
      // ignore storage errors
    }
  }

  async function loadChapter(storyId: string, chapterNo: number) {
    isLoading.value = true
    storyError.value = null
    reconnectAttempts = 0

    try {
      await storyStore.fetchChapter(storyId, chapterNo)
      saveStoryState()
    } catch (err: any) {
      storyError.value = err?.message ?? '加载章节失败'
      console.error('[useStory] loadChapter failed:', err)

      // SSE reconnect 逻辑（未来流式章节可用）
      if (reconnectAttempts < maxReconnectAttempts) {
        reconnectAttempts++
        const delay = Math.min(1000 * Math.pow(2, reconnectAttempts - 1), 8000)
        console.warn(`[useStory] reconnecting in ${delay}ms (attempt ${reconnectAttempts})`)
        reconnectTimer = setTimeout(() => {
          loadChapter(storyId, chapterNo)
        }, delay)
      }
    } finally {
      isLoading.value = false
    }
  }

  async function submitChoice(optionId: number) {
    if (!storyStore.currentStory || !storyStore.currentChapter) return
    storyError.value = null

    try {
      await storyStore.submitChoice(
        storyStore.currentStory.id,
        storyStore.currentChapter.chapterNo,
        optionId
      )
      saveStoryState()
    } catch (err: any) {
      storyError.value = err?.message ?? '提交选择失败'
      console.error('[useStory] submitChoice failed:', err)
    }
  }

  async function finishStory() {
    if (!storyStore.currentStory) return
    storyError.value = null

    try {
      // 触发 AI 生成完整手稿
      await storyStore.generateManuscript(storyStore.currentStory.id)
      saveStoryState()
    } catch (err: any) {
      storyError.value = err?.message ?? '生成手稿失败'
      console.error('[useStory] finishStory failed:', err)
    }
  }

  return {
    currentChapter,
    isLoading,
    storyError,
    loadChapter,
    submitChoice,
    finishStory,
  }
}
