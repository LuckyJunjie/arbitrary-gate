import axios from 'axios'
import { filterManuscript } from './zhangyan'

// 配置实际 API Base URL（环境变量注入）
const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api'

export const api = axios.create({
  baseURL: BASE_URL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// 请求拦截器：注入 token
api.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截器：统一错误处理
api.interceptors.response.use(
  response => response.data,
  error => {
    if (error.response) {
      const { status, data } = error.response
      if (status === 401) {
        console.warn('[API] Unauthorized, redirect to login')
      }
      console.error(`[API] Error ${status}:`, data)
    } else {
      console.error('[API] Network error:', error.message)
    }
    return Promise.reject(error)
  }
)

// ===== 类型定义 =====

export interface KeywordCard {
  id: number
  name: string
  rarity: number // 1=凡 2=珍 3=奇 4=绝
  category: number // 1=器物 2=职人 3=风物 4=情绪 5=称谓
  imageUrl?: string
  inkFragrance?: number // 0-7
  resonanceCount?: number
  drawnAt?: string
}

export interface EventCard {
  id: number
  name: string
  rarity: number
  category: number
  description?: string
  drawnAt?: string
}

export interface EventDrawResult {
  cardId: number
  cardNo: string
  title: string
  dynasty: string
  location: string
  description: string
  era: string
  isGuaranteedRare: boolean
}

export interface DrawResponse {
  card: KeywordCard
  remainingFreeDraws: number
  isFree: boolean
}

export interface HistoryEvent {
  id: number
  year: number
  season: string
  title: string
  description: string
  keywordIds: number[]
}

export interface Option {
  id: number
  text: string
}

export interface EntryQuestion {
  id: number
  category: string
  question: string
  hint: string
}

export interface Chapter {
  chapterNo: number
  sceneText: string
  options: Option[]
  keywordResonance?: Record<number, number>
  ripples?: Array<{ target: string; status: string }>
}

export interface Story {
  id: string
  storyNo: string
  title: string
  status: number // 1=进行中 2=已完成
  currentChapter: number
  historyDeviation: number
  createdAt: string
  finishedAt?: string
}

export interface Manuscript {
  fullText: string
  wordCount: number
  annotations?: Array<{ chapterNo?: number; x: number; y: number; text: string; color?: string }>
  choiceMarks?: Array<{ chapterNo?: number; optionId?: number; text?: string }>
  epilogue?: string
  baiguanComment?: string
}

// ===== API 方法 =====

// 抽关键词卡（GET，查询免费次数等）
export async function fetchKeywordCard(): Promise<{ card: KeywordCard; remainingFreeDraws: number; isFree: boolean }> {
  return api.get('/card/keyword')
}

// 抽关键词卡（POST，实际执行抽卡）
export async function drawKeywordCard(): Promise<DrawResponse> {
  return api.post('/card/draw/keyword')
}

// 抽事件卡
export async function drawEventCard(): Promise<EventDrawResult> {
  return api.post('/card/draw/event')
}

// 墨迹占卜（今日运势）
export interface FortuneResult {
  fortune: string
  hint: string
}

export async function fetchFortune(): Promise<FortuneResult> {
  return api.get('/card/fortune')
}

// 获取历史事件列表
export async function fetchHistoryEvents(): Promise<HistoryEvent[]> {
  return api.get('/events')
}

// 开始新故事
export async function startNewStory(payload: { title?: string; keywords?: number[] }): Promise<Story> {
  return api.post('/story/start', payload)
}

// 提交章节选择
export async function submitChapterChoice(storyId: string, chapterNo: number, optionId: number): Promise<{ chapter: Chapter; deviation: number }> {
  return api.post(`/story/${storyId}/chapter/${chapterNo}/choose`, { optionId })
}

// 获取故事章节
export async function fetchChapter(storyId: string, chapterNo: number): Promise<Chapter> {
  return api.get(`/story/${storyId}/chapter/${chapterNo}`)
}

// 获取手稿
export async function fetchManuscript(storyId: string): Promise<Manuscript> {
  return api.get(`/story/${storyId}/manuscript`)
}

// 获取故事列表
export async function fetchStoryList(): Promise<Story[]> {
  return api.get('/story/list')
}

// 完成故事（触发 AI 生成手稿）
export async function finishStory(storyId: string): Promise<Manuscript> {
  return api.post(`/story/${storyId}/finish`)
}

// ─── Mock 降级层 ─────────────────────────────────────────────────────────────
// 当真实后端不可用时，mock 服务接管故事生成流程

let _mockStoryTeller: typeof import('./storyTeller').storyTeller | null = null
let _mockJudge: typeof import('./judge').judge | null = null

async function getMocks() {
  if (!_mockStoryTeller) {
    const m = await import('./storyTeller')
    _mockStoryTeller = m.storyTeller
    const j = await import('./judge')
    _mockJudge = j.judge
  }
  return { teller: _mockStoryTeller!, judge: _mockJudge! }
}

// 章节本地记录（用于 mock 生成）
const _chapterHistory: Array<{ chapterNo: number; optionId: number; deviation: number }> = []

function getStoryContext(storyId: string) {
  const savedValues = localStorage.getItem(`story_${storyId}_values`)
  const entryAnswers = savedValues ? JSON.parse(savedValues) : {义: '', 利: '', 情: ''}
  const savedKeywords = localStorage.getItem('selectedKeywordCards')
  const keywords = savedKeywords ? JSON.parse(savedKeywords).map((c: any) => c.name) : []
  const savedEvent = localStorage.getItem('selectedEventCard')
  const eventName = savedEvent ? JSON.parse(savedEvent).name : ''
  return {
    storyId,
    chapterCount: 3,
    keywords,
    eventName,
    entryAnswers,
    chapterDeviations: _chapterHistory.map(h => h.deviation),
  }
}

/**
 * Mock 版获取章节（API 失败时调用）
 */
async function mockFetchChapter(storyId: string, chapterNo: number): Promise<Chapter> {
  const { teller } = await getMocks()
  teller.initStory(getStoryContext(storyId))
  return teller.getChapter(chapterNo)
}

/**
 * Mock 版提交选择（API 失败时调用）
 */
async function mockSubmitChoice(
  storyId: string,
  chapterNo: number,
  optionId: number
): Promise<{ chapter: Chapter; deviation: number }> {
  const { teller, judge: j } = await getMocks()
  teller.initStory(getStoryContext(storyId))

  // 更新判官状态
  j.reset()
  _chapterHistory.forEach(h => { j.recordChoice(h.optionId, h.chapterNo) })

  const result = teller.submitChoice(chapterNo, optionId)
  const { deviationDelta } = j.recordChoice(optionId, chapterNo)

  _chapterHistory.push({ chapterNo, optionId, deviation: deviationDelta })

  return {
    chapter: result.chapter,
    deviation: deviationDelta,
  }
}

/**
 * Mock 版完成故事（API 失败时调用）
 */
async function mockFinishStory(storyId: string): Promise<Manuscript> {
  const { teller, judge: j } = await getMocks()
  teller.initStory(getStoryContext(storyId))

  const totalDeviation = _chapterHistory.reduce((sum, h) => sum + h.deviation, 0)
  j.reset()
  _chapterHistory.forEach(h => { j.recordChoice(h.optionId, h.chapterNo) })
  const judgment = j.getFinalJudgment(totalDeviation)

  const manuscript = teller.generateManuscript(_chapterHistory.length, totalDeviation)
  // 掌眼 Agent 过滤 AI 腔
  manuscript.fullText = filterManuscript(manuscript.fullText)
  _chapterHistory.length = 0 // 重置

  return {
    ...manuscript,
    baiguanComment: judgment.judgeQuote,
  }
}

/**
 * 重置章节历史（开始新故事时调用）
 */
export function resetChapterHistory(): void {
  _chapterHistory.length = 0
}

// ─── 带 Mock 降级的 API 函数 ──────────────────────────────────────────────────

export async function fetchChapterWithMock(storyId: string, chapterNo: number): Promise<Chapter> {
  try {
    return await fetchChapter(storyId, chapterNo)
  } catch {
    return mockFetchChapter(storyId, chapterNo)
  }
}

export async function submitChoiceWithMock(
  storyId: string,
  chapterNo: number,
  optionId: number
): Promise<{ chapter: Chapter; deviation: number }> {
  try {
    return await submitChapterChoice(storyId, chapterNo, optionId)
  } catch {
    return mockSubmitChoice(storyId, chapterNo, optionId)
  }
}

export async function finishStoryWithMock(storyId: string): Promise<Manuscript> {
  try {
    return await finishStory(storyId)
  } catch {
    return mockFinishStory(storyId)
  }
}

// ========== 入局三问 API ==========

export interface GenerateQuestionsRequest {
  keywordIds: number[]
  eventId?: number
}

export interface GenerateQuestionsResponse {
  questions: EntryQuestion[]
}

export interface SubmitAnswersRequest {
  keywordIds: number[]
  eventId?: number
  entryAnswers: Array<{
    questionId: number
    question: string
    answer: string
  }>
}

/**
 * POST /api/story/questions
 * 生成入局三问（基于关键词组合）
 */
export async function generateEntryQuestions(
  payload: GenerateQuestionsRequest
): Promise<GenerateQuestionsResponse> {
  return api.post('/story/questions', payload)
}

/**
 * POST /api/story/answers
 * 提交入局答案并开始故事
 */
export async function submitEntryAnswers(
  payload: SubmitAnswersRequest
): Promise<Story> {
  return api.post('/story/answers', payload)
}

export default api
