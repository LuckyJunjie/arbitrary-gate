/**
 * Story Store 单元测试
 *
 * 测试覆盖：
 * - submitChoice 状态流转（chapters 记录、deviation 累积、currentChapter 更新）
 * - historyDeviation 累积正确
 * - startStory 初始化状态
 * - fetchChapterAction 加载状态
 * - generateManuscript 完成状态
 * - clearCurrentStory 重置
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useStoryStore } from '@/stores/storyStore'

// ─── Mock API functions ─────────────────────────────────────────────────────────

const mockFetchChapterWithMock = vi.fn()
const mockSubmitChoiceWithMock = vi.fn()
const mockFetchManuscript = vi.fn()
const mockFetchStoryList = vi.fn()
const mockFinishStoryWithMock = vi.fn()
const mockStartNewStory = vi.fn()
const mockResetChapterHistory = vi.fn()

vi.mock('@/services/api', () => ({
  fetchChapterWithMock: (...args: any[]) => mockFetchChapterWithMock(...args),
  submitChoiceWithMock: (...args: any[]) => mockSubmitChoiceWithMock(...args),
  fetchManuscript: (...args: any[]) => mockFetchManuscript(...args),
  fetchStoryList: (...args: any[]) => mockFetchStoryList(...args),
  finishStoryWithMock: (...args: any[]) => mockFinishStoryWithMock(...args),
  startNewStory: (...args: any[]) => mockStartNewStory(...args),
  resetChapterHistory: (...args: any[]) => mockResetChapterHistory(...args),
}))

// ─── Fixtures ──────────────────────────────────────────────────────────────────

const mockStory = {
  id: 'story-1',
  storyNo: '001',
  title: '测试故事',
  status: 1 as const,
  currentChapter: 1,
  historyDeviation: 50,
  createdAt: '2026-01-01T00:00:00Z',
}

const mockChapter = {
  chapterNo: 1,
  sceneText: '你站在渡口，看着远处的摆渡人。',
  options: [
    { id: 1, text: '上前询问' },
    { id: 2, text: '远远观望' },
  ],
  keywordResonance: { 1: 1 },
  ripples: [{ target: '摆渡人', status: 'awakened' }],
}

const mockManuscript = {
  fullText: '这是一段手稿全文...',
  wordCount: 1234,
  annotations: [{ x: 10, y: 20, text: '批注1' }],
  epilogue: '尾声',
}

// ─── Setup ─────────────────────────────────────────────────────────────────────

beforeEach(() => {
  vi.clearAllMocks()
  setActivePinia(createPinia())
})

// ─── Tests ─────────────────────────────────────────────────────────────────────

describe('useStoryStore — 状态初始化', () => {
  it('初始状态所有字段为空/零', () => {
    const store = useStoryStore()
    expect(store.currentStory).toBeNull()
    expect(store.currentChapter).toBeNull()
    expect(store.manuscript).toBeNull()
    expect(store.storyList).toHaveLength(0)
    expect(store.isLoading).toBe(false)
    expect(store.error).toBeNull()
    expect(store.historyDeviation).toBe(0)
    expect(store.chapters).toHaveLength(0)
  })
})

describe('useStoryStore — startStory', () => {
  it('startStory 设置 currentStory 并重置 chapters', async () => {
    mockStartNewStory.mockResolvedValue(mockStory)
    const store = useStoryStore()

    await store.startStory({ title: '新故事' })

    expect(mockStartNewStory).toHaveBeenCalledWith({ title: '新故事' })
    expect(store.currentStory).toEqual(mockStory)
    expect(store.chapters).toHaveLength(0)
    expect(store.manuscript).toBeNull()
    expect(store.error).toBeNull()
  })

  it('startStory 设置 isLoading 并在完成时清除', async () => {
    mockStartNewStory.mockResolvedValue(mockStory)
    const store = useStoryStore()

    const promise = store.startStory()
    expect(store.isLoading).toBe(true)
    await promise
    expect(store.isLoading).toBe(false)
  })

  it('startStory 失败时设置 error', async () => {
    mockStartNewStory.mockRejectedValue(new Error('API Error'))
    const store = useStoryStore()

    await expect(store.startStory()).rejects.toThrow('API Error')
    expect(store.error).toBe('开始故事失败')
  })

  it('startStory 支持无参数调用', async () => {
    mockStartNewStory.mockResolvedValue(mockStory)
    const store = useStoryStore()

    await store.startStory()
    expect(mockStartNewStory).toHaveBeenCalledWith({})
  })
})

describe('useStoryStore — submitChoice 状态流转', () => {
  it('submitChoice 记录选择到 chapters', async () => {
    mockSubmitChoiceWithMock.mockResolvedValue({ chapter: mockChapter, deviation: 5 })
    const store = useStoryStore()
    store.currentStory = { ...mockStory }

    await store.submitChoice('story-1', 1, 1)

    expect(store.chapters).toHaveLength(1)
    expect(store.chapters[0]).toMatchObject({
      chapterNo: 1,
      selectedOptionId: 1,
      deviationDelta: 5,
    })
  })

  it('submitChoice 累积 historyDeviation', async () => {
    mockSubmitChoiceWithMock.mockResolvedValue({ chapter: mockChapter, deviation: 10 })
    const store = useStoryStore()
    store.currentStory = { ...mockStory, historyDeviation: 50 }

    await store.submitChoice('story-1', 1, 1)

    expect(store.currentStory!.historyDeviation).toBe(60) // 50 + 10
  })

  it('submitChoice 多次调用正确累积偏离度', async () => {
    mockSubmitChoiceWithMock
      .mockResolvedValueOnce({ chapter: { ...mockChapter, chapterNo: 1 }, deviation: 8 })
      .mockResolvedValueOnce({ chapter: { ...mockChapter, chapterNo: 2 }, deviation: -5 })

    const store = useStoryStore()
    store.currentStory = { ...mockStory, historyDeviation: 50 }

    await store.submitChoice('story-1', 1, 1)
    await store.submitChoice('story-1', 2, 2)

    expect(store.chapters).toHaveLength(2)
    expect(store.currentStory!.historyDeviation).toBe(53) // 50 + 8 - 5
  })

  it('submitChoice 更新 currentChapter', async () => {
    mockSubmitChoiceWithMock.mockResolvedValue({ chapter: { ...mockChapter, chapterNo: 2 }, deviation: 0 })
    const store = useStoryStore()
    store.currentStory = { ...mockStory, currentChapter: 1 }

    await store.submitChoice('story-1', 1, 1)

    expect(store.currentChapter!.chapterNo).toBe(2)
  })

  it('submitChoice 更新 currentChapter 为 chapterNo + 1', async () => {
    mockSubmitChoiceWithMock.mockResolvedValue({ chapter: { ...mockChapter, chapterNo: 2 }, deviation: 0 })
    const store = useStoryStore()
    store.currentStory = { ...mockStory, currentChapter: 1 }

    await store.submitChoice('story-1', 1, 1)

    expect(store.currentStory!.currentChapter).toBe(2)
  })

  it('submitChoice 设置 isLoading 并在完成时清除', async () => {
    mockSubmitChoiceWithMock.mockResolvedValue({ chapter: mockChapter, deviation: 0 })
    const store = useStoryStore()
    store.currentStory = { ...mockStory }

    const promise = store.submitChoice('story-1', 1, 1)
    expect(store.isLoading).toBe(true)
    await promise
    expect(store.isLoading).toBe(false)
  })

  it('submitChoice 失败时设置 error', async () => {
    mockSubmitChoiceWithMock.mockRejectedValue(new Error('API Error'))
    const store = useStoryStore()
    store.currentStory = { ...mockStory }

    await expect(store.submitChoice('story-1', 1, 1)).rejects.toThrow('API Error')
    expect(store.error).toBe('提交选择失败')
  })

  it('submitChoice 处理 deviation 为 undefined 时不报错', async () => {
    mockSubmitChoiceWithMock.mockResolvedValue({ chapter: mockChapter })
    const store = useStoryStore()
    store.currentStory = { ...mockStory, historyDeviation: 50 }

    await store.submitChoice('story-1', 1, 1)

    expect(store.currentStory!.historyDeviation).toBe(50) // 不变
  })
})

describe('useStoryStore — historyDeviation computed', () => {
  it('historyDeviation 从 currentStory 读取', () => {
    const store = useStoryStore()
    store.currentStory = { ...mockStory, historyDeviation: 75 }
    expect(store.historyDeviation).toBe(75)
  })

  it('currentStory 为 null 时 historyDeviation 为 0', () => {
    const store = useStoryStore()
    expect(store.historyDeviation).toBe(0)
  })

  it('historyDeviation 随 currentStory 更新而更新', async () => {
    mockSubmitChoiceWithMock.mockResolvedValue({ chapter: mockChapter, deviation: 12 })
    const store = useStoryStore()
    store.currentStory = { ...mockStory, historyDeviation: 50 }

    await store.submitChoice('story-1', 1, 1)

    expect(store.historyDeviation).toBe(62) // 50 + 12
  })
})

describe('useStoryStore — fetchChapter', () => {
  it('fetchChapterAction 设置 currentChapter', async () => {
    mockFetchChapterWithMock.mockResolvedValue(mockChapter)
    const store = useStoryStore()

    const result = await store.fetchChapter('story-1', 1)

    expect(mockFetchChapterWithMock).toHaveBeenCalledWith('story-1', 1)
    expect(store.currentChapter).toEqual(mockChapter)
    expect(result).toEqual(mockChapter)
  })

  it('fetchChapterAction 设置 isLoadingChapter', async () => {
    mockFetchChapterWithMock.mockResolvedValue(mockChapter)
    const store = useStoryStore()

    const promise = store.fetchChapter('story-1', 1)
    expect(store.isLoadingChapter).toBe(true)
    await promise
    expect(store.isLoadingChapter).toBe(false)
  })

  it('fetchChapterAction 失败时设置 error', async () => {
    mockFetchChapterWithMock.mockRejectedValue(new Error('Not found'))
    const store = useStoryStore()

    await expect(store.fetchChapter('story-1', 999)).rejects.toThrow()
    expect(store.error).toBe('获取章节失败')
  })
})

describe('useStoryStore — generateManuscript / finishStory', () => {
  it('generateManuscript 设置 manuscript 并更新 story status', async () => {
    mockFinishStoryWithMock.mockResolvedValue(mockManuscript)
    const store = useStoryStore()
    store.currentStory = { ...mockStory }

    const result = await store.generateManuscript('story-1')

    expect(mockFinishStoryWithMock).toHaveBeenCalledWith('story-1')
    expect(store.manuscript).toEqual(mockManuscript)
    expect(store.currentStory!.status).toBe(2) // 已完成
    expect(store.currentStory!.finishedAt).toBeDefined()
    expect(result).toEqual(mockManuscript)
  })

  it('generateManuscript 设置 isLoadingManuscript', async () => {
    mockFinishStoryWithMock.mockResolvedValue(mockManuscript)
    const store = useStoryStore()
    store.currentStory = { ...mockStory }

    const promise = store.generateManuscript('story-1')
    expect(store.isLoadingManuscript).toBe(true)
    await promise
    expect(store.isLoadingManuscript).toBe(false)
  })

  it('generateManuscript 失败时设置 error', async () => {
    mockFinishStoryWithMock.mockRejectedValue(new Error('AI Error'))
    const store = useStoryStore()
    store.currentStory = { ...mockStory }

    await expect(store.generateManuscript('story-1')).rejects.toThrow()
    expect(store.error).toBe('生成手稿失败')
  })
})

describe('useStoryStore — fetchManuscript', () => {
  it('fetchManuscriptAction 设置 manuscript', async () => {
    mockFetchManuscript.mockResolvedValue(mockManuscript)
    const store = useStoryStore()

    await store.fetchManuscript('story-1')

    expect(mockFetchManuscript).toHaveBeenCalledWith('story-1')
    expect(store.manuscript).toEqual(mockManuscript)
  })
})

describe('useStoryStore — fetchStoryList', () => {
  it('fetchStoryListAction 设置 storyList', async () => {
    const stories = [{ ...mockStory }, { ...mockStory, id: 'story-2' }]
    mockFetchStoryList.mockResolvedValue(stories)
    const store = useStoryStore()

    const result = await store.fetchStoryList()

    expect(mockFetchStoryList).toHaveBeenCalled()
    expect(store.storyList).toHaveLength(2)
    expect(result).toHaveLength(2)
  })

  it('fetchStoryListAction 设置 isLoading', async () => {
    mockFetchStoryList.mockResolvedValue([])
    const store = useStoryStore()

    const promise = store.fetchStoryList()
    expect(store.isLoading).toBe(true)
    await promise
    expect(store.isLoading).toBe(false)
  })
})

describe('useStoryStore — setCurrentStory / clearCurrentStory', () => {
  it('setCurrentStory 设置 currentStory 并重置 chapters 和 manuscript', () => {
    const store = useStoryStore()
    store.chapters = [{ chapterNo: 1, selectedOptionId: 1, deviationDelta: 5 }]
    store.manuscript = mockManuscript

    store.setCurrentStory(mockStory)

    expect(store.currentStory).toEqual(mockStory)
    expect(store.chapters).toHaveLength(0)
    expect(store.manuscript).toBeNull()
  })

  it('clearCurrentStory 重置当前故事相关状态', () => {
    const store = useStoryStore()
    store.currentStory = { ...mockStory }
    store.currentChapter = mockChapter
    store.manuscript = mockManuscript
    store.chapters = [{ chapterNo: 1, selectedOptionId: 1, deviationDelta: 5 }]
    store.error = 'some error'

    store.clearCurrentStory()

    expect(store.currentStory).toBeNull()
    expect(store.currentChapter).toBeNull()
    expect(store.manuscript).toBeNull()
    expect(store.chapters).toHaveLength(0)
    expect(store.error).toBeNull()
    // storyList 不受 clearCurrentStory 影响（由 fetchStoryListAction 管理）
  })
})

describe('useStoryStore — updateKeywordResonance', () => {
  it('updateKeywordResonance 累加共鸣值', () => {
    const store = useStoryStore()

    store.updateKeywordResonance({ 1: 2, 2: 1 })
    expect(store.keywordResonance).toEqual({ 1: 2, 2: 1 })

    store.updateKeywordResonance({ 1: 1, 3: 3 })
    expect(store.keywordResonance).toEqual({ 1: 3, 2: 1, 3: 3 })
  })

  it('keywordResonance 初始为空对象', () => {
    const store = useStoryStore()
    expect(store.keywordResonance).toEqual({})
  })

  it('submitChoice 更新 keywordResonance', async () => {
    mockSubmitChoiceWithMock.mockResolvedValue({
      chapter: { ...mockChapter, keywordResonance: { 1: 2 } },
      deviation: 5,
    })
    const store = useStoryStore()
    store.currentStory = { ...mockStory }

    await store.submitChoice('story-1', 1, 1)

    expect(store.keywordResonance).toEqual({ 1: 2 })
  })

  it('连续选择累加 keywordResonance', async () => {
    mockSubmitChoiceWithMock.mockResolvedValueOnce({
      chapter: { ...mockChapter, chapterNo: 1, keywordResonance: { 1: 2, 2: 1 } },
      deviation: 5,
    })
    mockSubmitChoiceWithMock.mockResolvedValueOnce({
      chapter: { ...mockChapter, chapterNo: 2, keywordResonance: { 1: 1, 3: 2 } },
      deviation: 3,
    })
    const store = useStoryStore()
    store.currentStory = { ...mockStory }

    await store.submitChoice('story-1', 1, 1)
    await store.submitChoice('story-1', 2, 2)

    expect(store.keywordResonance).toEqual({ 1: 3, 2: 1, 3: 2 })
  })
})

describe('useStoryStore — entryAnswers', () => {
  it('entryAnswers 初始为空数组', () => {
    const store = useStoryStore()
    expect(store.entryAnswers).toEqual([])
  })

  it('setCurrentStory 重置 entryAnswers', () => {
    const store = useStoryStore()
    store.entryAnswers = [
      { questionId: 1, question: '问题1', answer: '答案1' },
    ]

    store.setCurrentStory(mockStory)

    expect(store.entryAnswers).toEqual([])
  })

  it('clearCurrentStory 重置 entryAnswers', () => {
    const store = useStoryStore()
    store.entryAnswers = [
      { questionId: 1, question: '问题1', answer: '答案1' },
    ]

    store.clearCurrentStory()

    expect(store.entryAnswers).toEqual([])
  })
})
