import { defineStore } from 'pinia'
import { ref } from 'vue'

interface Option {
  id: number
  text: string
}

interface Chapter {
  chapterNo: number
  sceneText: string
  options: Option[]
  keywordResonance?: Record<number, number>
  ripples?: Array<{ target: string; status: string }>
}

interface Story {
  id: string
  storyNo: string
  title: string
  status: number // 1=进行中 2=已完成
  currentChapter: number
  historyDeviation: number
  createdAt: string
  finishedAt?: string
}

interface Manuscript {
  fullText: string
  wordCount: number
  annotations?: Array<{ x: number; y: number; text: string }>
  epilogue?: string
}

export const useStoryStore = defineStore('story', () => {
  // ===== State =====
  const currentStory = ref<Story | null>(null)
  const currentChapter = ref<Chapter | null>(null)
  const manuscript = ref<Manuscript | null>(null)
  const storyList = ref<Story[]>([])

  // ===== Actions =====
  async function fetchChapter(storyId: string, chapterNo: number) {
    // TODO: 调用 API
    // const data = await api.get(`/story/${storyId}/chapter/${chapterNo}`)
    // currentChapter.value = data
  }

  async function submitChoice(storyId: string, chapterNo: number, optionId: number) {
    // TODO: 调用 API，保存选择，推进到下一章
  }

  async function finishStory(storyId: string) {
    // TODO: 调用 API，触发 AI 生成完整手稿
  }

  async function fetchManuscript(storyId: string) {
    // TODO: 调用 API 获取手稿
  }

  async function fetchStoryList() {
    // TODO: 调用 API 获取书架故事列表
  }

  function setCurrentStory(story: Story) {
    currentStory.value = story
  }

  function clearCurrentStory() {
    currentStory.value = null
    currentChapter.value = null
    manuscript.value = null
  }

  return {
    currentStory,
    currentChapter,
    manuscript,
    storyList,
    fetchChapter,
    submitChoice,
    finishStory,
    fetchManuscript,
    fetchStoryList,
    setCurrentStory,
    clearCurrentStory,
  }
})
