import { ref, computed } from 'vue'
import { api } from '@/services/api'
import { useStoryStore } from '@/stores/storyStore'

export function useStory() {
  const storyStore = useStoryStore()

  const currentChapter = computed(() => storyStore.currentChapter)
  const isLoading = ref(false)

  async function loadChapter(storyId: string, chapterNo: number) {
    isLoading.value = true
    try {
      // TODO: 调用 API 获取章节
      // await storyStore.fetchChapter(storyId, chapterNo)
    } catch (err) {
      console.error('[useStory] loadChapter failed:', err)
    } finally {
      isLoading.value = false
    }
  }

  async function submitChoice(optionId: number) {
    if (!storyStore.currentStory) return
    try {
      // TODO: 调用 API 提交选择，推进故事
      // await storyStore.submitChoice(storyStore.currentStory.id, optionId)
    } catch (err) {
      console.error('[useStory] submitChoice failed:', err)
    }
  }

  async function finishStory() {
    if (!storyStore.currentStory) return
    try {
      // TODO: 触发 AI 生成完整手稿
      // await storyStore.finishStory(storyStore.currentStory.id)
    } catch (err) {
      console.error('[useStory] finishStory failed:', err)
    }
  }

  return {
    currentChapter,
    isLoading,
    loadChapter,
    submitChoice,
    finishStory,
  }
}
