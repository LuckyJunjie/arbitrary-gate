import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  fetchChapterWithMock,
  submitChoiceWithMock,
  fetchManuscript,
  fetchStoryList,
  finishStoryWithMock,
  startNewStory,
  resetChapterHistory,
  type Story,
  type Chapter,
  type Manuscript,
} from '@/services/api'

export const useStoryStore = defineStore('story', () => {
  // ===== State =====
  const currentStory = ref<Story | null>(null)
  const currentChapter = ref<Chapter | null>(null)
  const manuscript = ref<Manuscript | null>(null)
  const storyList = ref<Story[]>([])
  const isLoading = ref(false)
  const isLoadingChapter = ref(false)
  const isLoadingManuscript = ref(false)
  const error = ref<string | null>(null)

  // 历史偏离度追踪
  const historyDeviation = computed(() => currentStory.value?.historyDeviation ?? 0)

  // 章节列表（每章的选择记录）
  const chapters = ref<Array<{
    chapterNo: number
    selectedOptionId: number
    deviationDelta: number
  }>>([])

  // 入局三问答案
  const entryAnswers = ref<Array<{
    questionId: number
    question: string
    answer: string
  }>>([])

  // 关键词共鸣值追踪
  const keywordResonance = ref<Record<number, number>>({})

  // ===== Actions =====

  async function fetchChapterAction(storyId: string, chapterNo: number) {
    isLoadingChapter.value = true
    error.value = null
    try {
      const data = await fetchChapterWithMock(storyId, chapterNo)
      currentChapter.value = data
      return data
    } catch (err) {
      error.value = '获取章节失败'
      console.error('[storyStore] fetchChapter failed:', err)
      throw err
    } finally {
      isLoadingChapter.value = false
    }
  }

  async function submitChoice(chapterId: string, chapterNo: number, optionId: number) {
    isLoading.value = true
    error.value = null
    try {
      const res = await submitChoiceWithMock(chapterId, chapterNo, optionId)

      // 记录选择
      chapters.value.push({
        chapterNo,
        selectedOptionId: optionId,
        deviationDelta: res.deviation ?? 0,
      })

      // 更新当前故事偏离度
      if (currentStory.value) {
        currentStory.value.historyDeviation += (res.deviation ?? 0)
        currentStory.value.currentChapter = chapterNo + 1
      }

      // 更新当前章节
      currentChapter.value = res.chapter

      // 更新关键词共鸣值
      if (res.chapter.keywordResonance) {
        updateKeywordResonance(res.chapter.keywordResonance)
      }

      return res
    } catch (err) {
      error.value = '提交选择失败'
      console.error('[storyStore] submitChoice failed:', err)
      throw err
    } finally {
      isLoading.value = false
    }
  }

  async function generateManuscript(storyId: string) {
    isLoadingManuscript.value = true
    error.value = null
    try {
      // 先调用 finishStory 触发 AI 生成
      const data = await finishStoryWithMock(storyId)
      manuscript.value = data

      // 更新故事状态
      if (currentStory.value) {
        currentStory.value.status = 2 // 已完成
        currentStory.value.finishedAt = new Date().toISOString()
      }

      return data
    } catch (err) {
      error.value = '生成手稿失败'
      console.error('[storyStore] generateManuscript failed:', err)
      throw err
    } finally {
      isLoadingManuscript.value = false
    }
  }

  async function fetchManuscriptAction(storyId: string) {
    isLoadingManuscript.value = true
    error.value = null
    try {
      const data = await fetchManuscript(storyId)
      manuscript.value = data
      return data
    } catch (err) {
      error.value = '获取手稿失败'
      console.error('[storyStore] fetchManuscript failed:', err)
      throw err
    } finally {
      isLoadingManuscript.value = false
    }
  }

  async function fetchStoryListAction() {
    isLoading.value = true
    error.value = null
    try {
      const data = await fetchStoryList()
      storyList.value = data
      return data
    } catch (err) {
      error.value = '获取故事列表失败'
      console.error('[storyStore] fetchStoryList failed:', err)
      throw err
    } finally {
      isLoading.value = false
    }
  }

  async function startStory(payload?: { title?: string; keywords?: number[] }) {
    isLoading.value = true
    error.value = null
    try {
      resetChapterHistory()
      const data = await startNewStory(payload ?? {})
      currentStory.value = data
      chapters.value = []
      manuscript.value = null
      return data
    } catch (err) {
      error.value = '开始故事失败'
      console.error('[storyStore] startStory failed:', err)
      throw err
    } finally {
      isLoading.value = false
    }
  }

  function setCurrentStory(story: Story) {
    currentStory.value = story
    chapters.value = []
    manuscript.value = null
    entryAnswers.value = []
    keywordResonance.value = {}
  }

  function clearCurrentStory() {
    currentStory.value = null
    currentChapter.value = null
    manuscript.value = null
    chapters.value = []
    entryAnswers.value = []
    keywordResonance.value = {}
    error.value = null
  }

  /**
   * 更新关键词共鸣值（每次选择后调用）
   */
  function updateKeywordResonance(resonanceData: Record<number, number>) {
    // 合并新的共鸣值
    Object.entries(resonanceData).forEach(([kid, val]) => {
      const key = Number(kid)
      keywordResonance.value[key] = (keywordResonance.value[key] ?? 0) + val
    })
  }

  return {
    // State
    currentStory,
    currentChapter,
    manuscript,
    storyList,
    chapters,
    entryAnswers,
    keywordResonance,
    historyDeviation,
    isLoading,
    isLoadingChapter,
    isLoadingManuscript,
    error,
    // Actions
    fetchChapter: fetchChapterAction,
    submitChoice,
    generateManuscript,
    fetchManuscript: fetchManuscriptAction,
    fetchStoryList: fetchStoryListAction,
    startStory,
    setCurrentStory,
    clearCurrentStory,
    updateKeywordResonance,
  }
})
