import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  fetchChapterWithMock,
  submitChoiceWithMock,
  submitEncounterChoice as submitEncounterChoiceApi,
  triggerEncounter as triggerEncounterApi,
  fetchManuscript,
  fetchStoryList,
  finishStoryWithMock,
  startNewStory,
  resetChapterHistory,
  fetchChapterProgress,
  type Story,
  type Chapter,
  type Manuscript,
  type Encounter,
} from '@/services/api'
import type { CombinationAchievement } from './achievementStore'

// ===== 断线重连配置 =====
const RECONNECT_DELAYS = [1000, 2000, 4000, 8000] // 指数退避：1s/2s/4s/8s
const MAX_RECONNECT_ATTEMPTS = 5

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

  // P-03 稀有组合检测结果（入局时检测）
  const activeCombination = ref<CombinationAchievement | null>(null)

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

  // S-14 当前活跃偶遇
  const activeEncounter = ref<Encounter | null>(null)

  // ===== S-16 断线重连状态 =====
  const wsStatus = ref<'disconnected' | 'connecting' | 'connected'>('disconnected')
  const ws = ref<WebSocket | null>(null)
  const reconnectAttempts = ref(0)
  const reconnectTimer = ref<ReturnType<typeof setTimeout> | null>(null)

  // 当前正在监听的流
  const currentStream = ref<{
    storyId: string
    chapterNo: number
  } | null>(null)

  // ===== Draft Key =====
  function draftKey(storyId: string, chapterNo: number) {
    return `story_draft:${storyId}:${chapterNo}`
  }

  // ===== S-16 Actions: Draft 管理 =====

  /**
   * 将新收到的文本 append 到 localStorage 草稿
   */
  function saveDraft(storyId: string, chapterNo: number, newText: string) {
    const key = draftKey(storyId, chapterNo)
    const existing = localStorage.getItem(key) ?? ''
    localStorage.setItem(key, existing + newText)
  }

  /**
   * 读取 localStorage 草稿
   */
  function getDraft(storyId: string, chapterNo: number): string {
    return localStorage.getItem(draftKey(storyId, chapterNo)) ?? ''
  }

  /**
   * 清除 localStorage 草稿（章节完成后调用）
   */
  function clearDraft(storyId: string, chapterNo: number) {
    localStorage.removeItem(draftKey(storyId, chapterNo))
  }

  // ===== S-16 Actions: WebSocket 流式连接 =====

  /**
   * 连接 WebSocket 流式接口，并注册标准回调
   * - onChunk(text): 收到文本片段
   * - onOptions(options): 收到选项列表（流式结束）
   * - onReconnect(draft): 断线重连后调用，draft=草稿文本
   * - onError(): 连接失败
   */
  function connectStream(params: {
    storyId: string
    chapterNo: number
    onChunk: (text: string) => void
    onOptions: (options: Array<{ id: number; text: string }>) => void
    onReconnect: (draft: string, serverLength: number) => void
    onError: () => void
  }) {
    const { storyId, chapterNo, onChunk, onOptions, onReconnect, onError } = params

    // 记录当前流
    currentStream.value = { storyId, chapterNo }

    // 关闭已有连接
    disconnectStream()

    wsStatus.value = 'connecting'
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const wsUrl = `${protocol}//${window.location.host}/api/story/${storyId}/ws`
    const socket = new WebSocket(wsUrl)
    ws.value = socket

    socket.addEventListener('open', () => {
      console.log('[storyStore] WebSocket connected')
      wsStatus.value = 'connected'
      reconnectAttempts.value = 0

      // 重连后：对比服务端进度与本地草稿，决定是否需要补全
      fetchChapterProgress(storyId, chapterNo)
        .then(progress => {
          const serverLength = progress.generatedLength ?? 0
          const localDraft = getDraft(storyId, chapterNo)
          if (serverLength > localDraft.length) {
            // 服务端有更多内容，触发补全回调
            onReconnect(localDraft, serverLength)
          }
        })
        .catch(() => {
          // 无法获取进度，触发补全回调用本地草稿
          onReconnect(getDraft(storyId, chapterNo), 0)
        })
    })

    socket.addEventListener('message', (event) => {
      try {
        const msg = JSON.parse(event.data as string)
        if (msg.type === 'scene_text' && msg.text) {
          // 保存草稿
          saveDraft(storyId, chapterNo, msg.text)
          // 通知调用方
          onChunk(msg.text)
        } else if (msg.type === 'options') {
          // 流式结束，清理草稿
          clearDraft(storyId, chapterNo)
          onOptions(msg.options ?? [])
        }
      } catch (e) {
        // 非 JSON 文本片段直接追加（兼容旧协议）
        saveDraft(storyId, chapterNo, event.data as string)
        onChunk(event.data as string)
      }
    })

    socket.addEventListener('close', () => {
      console.log('[storyStore] WebSocket closed')
      if (currentStream.value?.storyId === storyId && currentStream.value?.chapterNo === chapterNo) {
        wsStatus.value = 'disconnected'
        scheduleReconnect(params)
      }
    })

    socket.addEventListener('error', () => {
      console.warn('[storyStore] WebSocket error')
      wsStatus.value = 'disconnected'
      socket.close()
      onError()
    })
  }

  /**
   * 断开 WebSocket 并取消重连
   */
  function disconnectStream() {
    if (reconnectTimer.value !== null) {
      clearTimeout(reconnectTimer.value)
      reconnectTimer.value = null
    }
    if (ws.value) {
      ws.value.close()
      ws.value = null
    }
    wsStatus.value = 'disconnected'
    currentStream.value = null
    reconnectAttempts.value = 0
  }

  /**
   * 指数退避重连调度
   */
  function scheduleReconnect(params: {
    storyId: string
    chapterNo: number
    onChunk: (text: string) => void
    onOptions: (options: Array<{ id: number; text: string }>) => void
    onReconnect: (draft: string, serverLength: number) => void
    onError: () => void
  }) {
    const { storyId, chapterNo } = params

    if (reconnectAttempts.value >= MAX_RECONNECT_ATTEMPTS) {
      console.warn(`[storyStore] Max reconnect attempts (${MAX_RECONNECT_ATTEMPTS}) reached for story=${storyId}, chapter=${chapterNo}`)
      reconnectAttempts.value = 0
      return
    }

    const delay = RECONNECT_DELAYS[reconnectAttempts.value] ?? RECONNECT_DELAYS[RECONNECT_DELAYS.length - 1]
    console.log(`[storyStore] Scheduling reconnect attempt ${reconnectAttempts.value + 1}/${MAX_RECONNECT_ATTEMPTS} in ${delay}ms`)
    reconnectAttempts.value++

    reconnectTimer.value = setTimeout(() => {
      reconnectTimer.value = null
      connectStream(params)
    }, delay)
  }

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

  async function submitChoice(storyId: string, chapterNo: number, optionId: number, intensity?: 'gentle' | 'urgent' | 'forceful') {
    isLoading.value = true
    error.value = null
    try {
      const res = await submitChoiceWithMock(storyId, chapterNo, optionId, intensity)

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

  // S-14 提交偶遇选择
  async function submitEncounterChoice(
    storyId: string,
    encounterId: number,
    choice: 'A' | 'B'
  ) {
    try {
      const res = await submitEncounterChoiceApi(storyId, encounterId, choice)
      // 更新命运值
      if (currentStory.value) {
        currentStory.value.historyDeviation = Math.max(
          0,
          Math.min(100, currentStory.value.historyDeviation + res.fateChange)
        )
      }
      return res
    } catch (err) {
      console.error('[storyStore] submitEncounterChoice failed:', err)
      throw err
    }
  }

  // S-14 手动触发偶遇（绕过30%概率）
  async function triggerEncounter(storyId: string, chapterNo: number) {
    try {
      const encounter = await triggerEncounterApi(storyId, chapterNo)
      if (encounter) {
        // 设置 activeEncounter 以触发 UI 显示
        activeEncounter.value = {
          encounterId: encounter.encounterId,
          encounterText: encounter.encounterText,
          optionA: encounter.optionA,
          optionB: encounter.optionB,
          chapterNo: encounter.chapterNo,
          characterName: encounter.characterName,
          characterRole: encounter.characterRole,
        }
      }
      return encounter
    } catch (err) {
      console.error('[storyStore] triggerEncounter failed:', err)
      throw err
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
    activeCombination.value = null
    disconnectStream()
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

  /**
   * 设置当前故事的稀有组合类型（P-03）
   */
  function setActiveCombination(combo: CombinationAchievement | null) {
    activeCombination.value = combo
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
    // P-03 稀有组合
    activeCombination,
    // S-16 Stream state
    wsStatus,
    ws,
    reconnectAttempts,
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
    setActiveCombination,
    // S-14 Actions
    submitEncounterChoice,
    triggerEncounter,
    activeEncounter,
    // S-16 Actions
    connectStream,
    disconnectStream,
    saveDraft,
    getDraft,
    clearDraft,
  }
})
