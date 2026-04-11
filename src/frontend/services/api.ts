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
  valueTag?: string
}

export interface EntryQuestion {
  id: number
  category: '角色背景' | '当下处境' | '内心渴望'
  question: string
  hint: string
}

export interface Chapter {
  chapterNo: number
  sceneText: string
  options: Option[]
  keywordResonance?: Record<number, number>
  ripples?: Array<{ target: string; status: string }>
  characterAppearances?: Array<{ name: string; firstImpression: string }>
}

/** S-13 关键词显灵数据 */
export interface KeywordEnlightenment {
  cardId: number
  cardName: string
  enlightenmentText: string
}

/** S-14 偶遇事件 */
export interface Encounter {
  encounterId: number
  encounterText: string
  optionA: string
  optionB: string
  chapterNo: number
}

/** S-14 偶遇选择响应 */
export interface EncounterChoiceResult {
  encounterId: number
  fateChange: number
}

export interface Story {
  id: string
  storyNo: string
  title: string
  candidateTitles?: string[]
  status: number // 1=进行中 2=已完成
  currentChapter: number
  historyDeviation: number
  createdAt: string
  finishedAt?: string
  /** E-07 关键词落位 */
  keywordPositions?: KeywordPosition[]
}

/** E-07 关键词落位 */
export interface KeywordPosition {
  keyword: string
  role: '核心意象' | '转折道具' | '人物关联'
  roleOwner?: string | null
}

export interface Manuscript {
  fullText: string
  wordCount: number
  inscription?: string
  annotations?: Array<{ chapterNo?: number; x: number; y: number; text: string; color?: string; type?: string }>
  choiceMarks?: Array<{ chapterNo?: number; optionId?: number; text?: string }>
  epilogue?: string
  baiguanComment?: string
  candidateTitles?: string[]
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

// ========== C-12 陈卡回炉 ==========

export interface RecycleResult {
  success: boolean
  freeDrawsRemaining: number
}

/**
 * C-12 陈卡回炉
 * POST /api/card/recycle
 * 将关键词卡投入墨池回炉，每日限1次，返还1次免费抽卡机会
 *
 * @param userCardId user_keyword_card.id（不是 keyword_card.id）
 */
export async function recycleCard(userCardId: number): Promise<RecycleResult> {
  return api.post('/card/recycle', { cardId: userCardId })
}

// 墨迹占卜（今日运势）
export interface FortuneResult {
  fortune: string
  hint: string
}

export async function fetchFortune(): Promise<FortuneResult> {
  return api.get('/card/fortune')
}

// ========== P-01 组合判词生成 ==========

export interface PreviewJudgmentRequest {
  keywordIds: number[]
  eventId?: number
}

export interface PreviewJudgmentResult {
  judgment: string
}

/**
 * POST /api/card/preview
 * 选完3张关键词+1事件后，点击"入局"前，调用 AI 生成一句古文判词
 */
export async function previewJudgment(payload: PreviewJudgmentRequest): Promise<PreviewJudgmentResult> {
  return api.post('/card/preview', payload)
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
export async function submitChapterChoice(
  storyId: string,
  chapterNo: number,
  optionId: number,
  gestureIntensity?: 'gentle' | 'urgent' | 'forceful'
): Promise<{ chapter: Chapter; deviation: number; encounter?: Encounter; keywordEnlightenment?: KeywordEnlightenment }> {
  return api.post(`/story/${storyId}/chapter/${chapterNo}/choose`, { optionId, gestureIntensity })
}

/** S-14 提交偶遇选择（'A'=搭话, 'B'=装作没看见） */
export async function submitEncounterChoice(
  storyId: string,
  encounterId: number,
  choice: 'A' | 'B'
): Promise<EncounterChoiceResult> {
  return api.post(`/story/${storyId}/encounter/choice`, { encounterId, choice })
}

// 获取故事章节
export async function fetchChapter(storyId: string, chapterNo: number): Promise<Chapter> {
  return api.get(`/story/${storyId}/chapter/${chapterNo}`)
}

/**
 * GET /api/story/{id}/chapter/{no}/progress
 * 返回当前已生成的文本长度（用于断线重连）
 */
export async function fetchChapterProgress(storyId: string, chapterNo: number): Promise<{ chapterNo: number; generatedLength: number }> {
  return api.get(`/story/${storyId}/chapter/${chapterNo}/progress`)
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

// 更新故事标题
export async function updateStoryTitle(storyId: string, title: string): Promise<void> {
  return api.post(`/story/${storyId}/title`, { title })
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
  optionId: number,
  _gestureIntensity?: string
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
    candidateTitles: ['旧梦重温', '时光渡口', '一段往事'],
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
  optionId: number,
  gestureIntensity?: 'gentle' | 'urgent' | 'forceful'
): Promise<{ chapter: Chapter; deviation: number; encounter?: Encounter; keywordEnlightenment?: KeywordEnlightenment }> {
  try {
    return await submitChapterChoice(storyId, chapterNo, optionId, gestureIntensity)
  } catch {
    return mockSubmitChoice(storyId, chapterNo, optionId, gestureIntensity)
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
  style?: number // 1白描 2江湖 3笔记 4话本
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

// ========== 分享合券 API ==========

export interface CreateShareRequest {
  storyId: number
  cardId: number
}

export interface CreateShareResponse {
  shareCode: string
  cardName: string
  cardCategory: number
  expiresAt: string
  storyTitle: string
}

export interface ShareInfoResponse {
  shareCode: string
  cardName: string
  cardCategory: number
  storyTitle: string
  storyId: number
  status: 'pending' | 'jointed'
  expiresAt: string
}

export interface JointShareRequest {
  cardId: number
}

export interface JointShareResponse {
  success: boolean
  message: string
  storyTitle: string
  storyId: number
  specialCardId: number
  specialCardName: string
  grantedReadPermission: boolean
}

export interface SpecialCard {
  id: number
  cardNo: string
  name: string
  description: string
  imageUrl?: string
  rarity: number
  sourceStoryId: number
  sourceShareCode: string
  acquiredAt: string
}

/**
 * POST /api/share/create
 * 生成分享码
 */
export async function createShare(payload: CreateShareRequest): Promise<CreateShareResponse> {
  return api.post('/share/create', payload)
}

/**
 * GET /api/share/{code}
 * 根据分享码获取分享信息
 */
export async function fetchShareInfo(code: string): Promise<ShareInfoResponse> {
  return api.get(`/share/${code}`)
}

/**
 * POST /api/share/{code}/joint
 * 合券
 */
export async function jointShare(code: string, payload: JointShareRequest): Promise<JointShareResponse> {
  return api.post(`/share/${code}/joint`, payload)
}

/**
 * GET /api/share/special-cards
 * 获取用户的合券纪念卡列表
 */
export async function fetchSpecialCards(): Promise<SpecialCard[]> {
  return api.get('/share/special-cards')
}

// ========== C-14 AI画师对接 API ==========

export interface ImageGenerateResult {
  imageUrl: string
  cached: boolean
}

/**
 * POST /api/image/generate
 * 调用后端 AI 画师生成图片（通义万相）
 *
 * @param prompt 英文 prompt（由 aiPainter.ts 的 prompt builder 构建）
 * @param size   图片尺寸，默认 "512*768"
 */
export async function generateImage(
  prompt: string,
  size: string = '512*768'
): Promise<ImageGenerateResult> {
  return api.post('/image/generate', { prompt, size })
}

// ========== 墨晶充值 API ==========

export interface WxPayParams {
  appId: string
  timeStamp: string
  nonceStr: string
  package_: string
  signType: string
  paySign: string
}

export interface CreateOrderResponse {
  orderNo: string
  payParams: WxPayParams
}

/**
 * POST /api/pay/create-order
 * 创建墨晶充值订单并获取微信支付参数
 */
export async function createPayOrder(request: {
  packageId: string
  inkStoneCount: number
  amount: number
}): Promise<CreateOrderResponse> {
  return api.post('/pay/create-order', request)
}

/**
 * 调起微信支付
 * 需要先引入微信 JSSDK: <script src="//res.wx.qq.com/open/js/jweixin-1.6.0.js"></script>
 */
export async function invokeWxPay(payParams: WxPayParams): Promise<void> {
  return new Promise((resolve, reject) => {
    if (typeof (window as any).wx === 'undefined') {
      // 微信 JSSDK 未加载，模拟支付成功（开发环境）
      console.warn('[Pay] 微信 JSSDK 未加载，模拟支付成功')
      setTimeout(() => resolve(), 500)
      return
    }
    ;(window as any).wx.chooseWXPay({
      appId: payParams.appId,
      timestamp: payParams.timeStamp,
      nonceStr: payParams.nonceStr,
      package: payParams.package_,
      signType: payParams.signType,
      paySign: payParams.paySign,
      success: () => resolve(),
      fail: (err: any) => reject(err),
      cancel: () => reject(new Error('用户取消支付')),
    })
  })
}

// Re-export aiPainter for CardsView compatibility
export { aiPainter } from './aiPainter'

// ========== U-03 游客登录 ==========

export interface GuestLoginResponse {
  token: string
  user: {
    id: number
    nickname: string
    avatarUrl?: string
    inkStone: number
    dailyFreeDraws: number
    totalStories: number
    completedStories: number
    isGuest: number // 0=正式用户，1=游客
  }
}

/**
 * U-03 POST /api/user/guest-login
 * 游客登录：自动创建临时账号，获取 token
 * 前端在未登录时调用此方法
 */
export async function guestLogin(): Promise<GuestLoginResponse> {
  return api.post('/user/guest-login')
}

/**
 * 初始化登录（自动判别游客或正式登录）
 * 若 localStorage 中已有 token：使用现有 token
 * 若没有 token：自动调用 guest-login
 *
 * @returns true=登录成功，false=登录失败（未登录）
 */
export async function ensureLogin(): Promise<boolean> {
  const existingToken = localStorage.getItem('token')
  if (existingToken) {
    // 已有 token，视为已登录（可能是正式用户或游客）
    return true
  }
  try {
    const res = await guestLogin()
    localStorage.setItem('token', res.token)
    // 存储用户信息（含 isGuest 标志）
    localStorage.setItem('arbitrary_gate_user', JSON.stringify(res.user))
    console.info('[Auth] Guest login success, userId=', res.user.id, 'isGuest=', res.user.isGuest)
    return true
  } catch (err) {
    console.error('[Auth] Guest login failed:', err)
    return false
  }
}

export default api
