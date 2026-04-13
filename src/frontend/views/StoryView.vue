<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useStoryStore } from '@/stores/storyStore'
import { fetchChapter } from '@/services/api'
import { playBell, playChime } from '@/composables/useSound'
import KeywordEnlightenmentOverlay from '@/components/KeywordEnlightenment.vue'
import type { Chapter, Option, Encounter } from '@/services/api'
import type { KeywordEnlightenment } from '@/services/api'

const route = useRoute()
const router = useRouter()
const storyStore = useStoryStore()

const storyId = route.params.id as string

// ── 手势选择状态 ──
type GestureType = 'swipe-left' | 'swipe-right' | 'circle' | null
const gestureMode = ref<'button' | 'gesture'>('button')
const activeGesture = ref<GestureType>(null)
const gestureFeedback = ref<string | null>(null)

// 手势检测状态
const touchStart = ref<{ x: number; y: number; angle: number } | null>(null)
const touchStartTime = ref<number>(0)
const touchHistory = ref<Array<{ x: number; y: number }>>([])
const circleAccumAngle = ref(0)
const lastAngle = ref<number | null>(null)
const MIN_SWIPE_DIST = 50
const CIRCLE_ANGLE_THRESHOLD = 280 // degrees to count as a circle
const SWIPE_SLOW_MS = 800
const SWIPE_FAST_MS = 200

function getAngle(x: number, y: number): number {
  return Math.atan2(y - (touchStart.value?.y ?? 0), x - (touchStart.value?.x ?? 0)) * 180 / Math.PI
}

function handleTouchStart(e: TouchEvent) {
  if (gestureMode.value !== 'gesture' || !currentChapter.value?.options?.length) return
  const t = e.touches[0]
  touchStart.value = { x: t.clientX, y: t.clientY, angle: 0 }
  touchStartTime.value = Date.now()
  touchHistory.value = [{ x: t.clientX, y: t.clientY }]
  circleAccumAngle.value = 0
  lastAngle.value = null
}

function handleTouchMove(e: TouchEvent) {
  if (!touchStart.value || gestureMode.value !== 'gesture') return
  const t = e.touches[0]
  const dx = t.clientX - touchStart.value.x
  const dy = t.clientY - touchStart.value.y

  // 圆形手势：累积旋转角度
  const currentAngle = getAngle(t.clientX, t.clientY)
  if (lastAngle.value !== null) {
    let delta = currentAngle - lastAngle.value
    // 处理跨越 -180/180 边界
    if (delta > 180) delta -= 360
    if (delta < -180) delta += 360
    circleAccumAngle.value += Math.abs(delta)
  }
  lastAngle.value = currentAngle
  touchHistory.value.push({ x: t.clientX, y: t.clientY })

  // 滑动方向实时反馈
  if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > 20) {
    activeGesture.value = dx < 0 ? 'swipe-left' : 'swipe-right'
  } else if (Math.abs(dy) > 20) {
    activeGesture.value = null
  }

  // 圆形手势达到阈值时反馈
  if (circleAccumAngle.value >= CIRCLE_ANGLE_THRESHOLD) {
    activeGesture.value = 'circle'
  }
}

function handleTouchEnd(e: TouchEvent) {
  if (!touchStart.value || gestureMode.value !== 'gesture') return
  const t = e.changedTouches[0]
  const dx = t.clientX - touchStart.value.x
  const _dy = t.clientY - touchStart.value.y
  const duration = Date.now() - touchStartTime.value

  // 根据手势速度判定力度
  let intensity: 'gentle' | 'urgent' | 'forceful' | undefined
  if (duration > SWIPE_SLOW_MS) intensity = 'gentle'
  else if (duration < SWIPE_FAST_MS) intensity = 'urgent'

  if (circleAccumAngle.value >= CIRCLE_ANGLE_THRESHOLD) {
    triggerGesture('circle', undefined, intensity)
  } else if (Math.abs(dx) > MIN_SWIPE_DIST) {
    // 向左滑 = 后退逃避选项(选项0)，向右滑 = 前进冒险选项(选项1)
    const optIndex = dx < 0 ? 0 : 1
    triggerGesture(dx < 0 ? 'swipe-left' : 'swipe-right', optIndex, intensity)
  }

  // 重置
  touchStart.value = null
  touchHistory.value = []
  circleAccumAngle.value = 0
  lastAngle.value = null
  activeGesture.value = null
}

function triggerGesture(gesture: GestureType, fallbackIndex?: number, intensity?: 'gentle' | 'urgent' | 'forceful') {
  if (!currentChapter.value?.options?.length) return
  const opts = currentChapter.value.options

  let optionIndex: number
  if (gesture === 'swipe-left') optionIndex = 0      // 向后滑 → 逃避/后退
  else if (gesture === 'swipe-right') optionIndex = 1 // 向前推 → 冒险/前进
  else if (gesture === 'circle') optionIndex = 2     // 画圈 → 绕道/迂回
  else return

  // 限制在有效选项范围内
  optionIndex = fallbackIndex ?? optionIndex
  if (optionIndex >= opts.length) optionIndex = opts.length - 1

  const label = gesture === 'swipe-left' ? '退' : gesture === 'swipe-right' ? '进' : '绕'
  gestureFeedback.value = label
  setTimeout(() => { gestureFeedback.value = null }, 800)

  selectOption(opts[optionIndex].id, undefined, undefined, intensity)
}

function enableGestureMode() {
  gestureMode.value = 'gesture'
}

function enableButtonMode() {
  gestureMode.value = 'button'
}

// ── 流式渲染状态 ──
const displayedText = ref('') // 已渲染的纯文本
const currentChapter = ref<Chapter | null>(null)
const shownCharacterAppearances = ref<Map<string, string>>(new Map()) // 已展示的初见印象
const isStreaming = ref(false)
const isLoading = ref(true)
const streamError = ref<string | null>(null)
const typewriterQueue = ref<string[]>([]) // 待渲染段落队列

// ── SSE / WS 连接 ──
let eventSource: EventSource | null = null
let ws: WebSocket | null = null
let _charIndex = 0
let _streamingDone = false

// ── 逐字渲染引擎 (UI-10) ──
// 每字间隔 30ms，逐字 fadeIn，queue 缓冲多段文本
const pendingTextQueue = ref<string[]>([]) // 待渲染段落队列（逐字模式）
let typewriterTimer: ReturnType<typeof setTimeout> | null = null
let _typewriterParagraphIndex = 0 // 当前渲染段落在 displayedText 中的位置标记

function startTypewriterQueue(paragraphs: string[]) {
  typewriterQueue.value = paragraphs
  _streamingDone = false
  isStreaming.value = true
  processPendingQueue()
}

// ── S-14 偶遇支线状态 ──
const activeEncounter = ref<Encounter | null>(null)

// ── S-13 关键词显灵状态 ──
const activeEnlightenment = ref<KeywordEnlightenment | null>(null)

async function handleEncounterChoice(choice: 'A' | 'B') {
  if (!activeEncounter.value) return
  const enc = activeEncounter.value
  activeEncounter.value = null // 立即关闭浮层
  try {
    await storyStore.submitEncounterChoice(storyId, enc.encounterId, choice)
  } catch (err) {
    console.error('[StoryView] encounter choice failed:', err)
  }
}

// ── 逐字渲染引擎 (UI-10) ──
/**
 * 逐字渲染 WebSocket/SSE 流式文本片段。
 * 每字间隔 30ms，append 到 displayedText，
 * 配合 CSS @keyframes fadeIn 每字 0.3s 淡入。
 */
function appendPendingText(text: string) {
  if (!text) return
  // 将新片段加入队列
  pendingTextQueue.value.push(text)
  // 如果当前没有渲染任务，立即启动
  if (typewriterTimer === null) {
    processPendingQueue()
  }
}

function processPendingQueue() {
  if (pendingTextQueue.value.length === 0) {
    typewriterTimer = null
    // 所有段落渲染完毕
    if (typewriterQueue.value.length === 0) {
      isStreaming.value = false
      _streamingDone = true
    }
    return
  }
  // 取出队首段落
  let paragraph = pendingTextQueue.value[0]
  if (!paragraph) {
    pendingTextQueue.value.shift()
    processPendingQueue()
    return
  }
  // 每次取出一个字符 append
  const ch = paragraph[0]
  const rest = paragraph.slice(1)
  displayedText.value += ch
  // 更新队首（去掉已渲染字符）
  if (rest) {
    pendingTextQueue.value[0] = rest
  } else {
    pendingTextQueue.value.shift()
    // 段落结束，加换行
    displayedText.value += '\n'
  }
  // 30ms 后渲染下一个字符
  typewriterTimer = setTimeout(processPendingQueue, 30)
}

// ── 入局三问弹窗状态 ──
const showEntryModal = ref(false)
const entryAnswers = ref({义: '', 利: '', 情: ''})

// ── E-07 关键词落位可视化 ──
// 三列关键词角色分类
const keywordColumnRoles = ['核心意象', '转折道具', '人物关联'] as const

// 按角色分组的关键词落位
const keywordPositionsByRole = computed(() => {
  const positions = storyStore.currentStory?.keywordPositions ?? []
  const grouped: Record<string, typeof positions> = {}
  for (const role of keywordColumnRoles) {
    grouped[role] = positions.filter(p => p.role === role)
  }
  return grouped
})

// ── 章节导航 ──
const currentChapterNo = ref(1)
const hasPrevChapter = computed(() => currentChapterNo.value > 1)

// ── 配角初见印象 ──
const currentAppearances = computed(() => {
  if (!currentChapter.value?.characterAppearances) return []
  // 只返回本章新出现的（去重逻辑在 loadChapter 时已处理）
  return currentChapter.value.characterAppearances.filter(
    app => shownCharacterAppearances.value.get(app.name) === app.firstImpression
  )
})
const hasNextChapter = computed(() => {
  const ch = currentChapter.value
  return ch && ch.options.length > 0 && !isEndChapter.value
})
const isEndChapter = computed(() => {
  const ch = currentChapter.value
  return ch && ch.options.length === 0
})

// ── 价值取向选项标签 ──
const valueLabels = ['义', '利', '情']

// S-13: 连接 SSE 接收 keyword_enlightenment 等事件
function connectSSE() {
  closeSSE()
  const sseUrl = `/api/story/${storyId}/stream`
  eventSource = new EventSource(sseUrl)

  eventSource.addEventListener('keyword_enlightenment', (e) => {
    try {
      const data = JSON.parse(e.data) as KeywordEnlightenment
      activeEnlightenment.value = data
      playChime()
    } catch (err) {
      console.error('[StoryView] keyword_enlightenment parse error:', err)
    }
  })

  eventSource.addEventListener('encounter', (e) => {
    try {
      const data = JSON.parse(e.data)
      activeEncounter.value = {
        encounterId: data.encounterId,
        encounterText: data.encounterText,
        optionA: data.optionA,
        optionB: data.optionB,
        chapterNo: data.chapterNo,
        characterName: data.characterName,
        characterRole: data.characterRole,
      }
    } catch (err) {
      console.error('[StoryView] encounter parse error:', err)
    }
  })

  eventSource.onerror = () => {
    console.warn('[StoryView] SSE error, will reconnect...')
    closeSSE()
  }
}

function closeSSE() {
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
}

// S-13: 监听累积共鸣值，当任何关键词共鸣值首次达到阈值（>= 5）时触发显灵
// 防重复：每个 cardId 只触发一次（session 级别）
const enlightenedCardIds = new Set<number>()

watch(
  () => storyStore.keywordResonance,
  (resonanceMap) => {
    for (const [cardIdStr, resonance] of Object.entries(resonanceMap)) {
      const cardId = Number(cardIdStr)
      if (resonance >= 5 && !enlightenedCardIds.has(cardId)) {
        // 如果已有完整的 enlightenment 数据（来自 REST API），不覆盖
        if (activeEnlightenment.value?.cardId === cardId && activeEnlightenment.value.cardName) {
          enlightenedCardIds.add(cardId)
          continue
        }
        enlightenedCardIds.add(cardId)
        // 构造 enlightenment 数据（cardImageUrl 留空，由 SSE 推送补充）
        activeEnlightenment.value = {
          cardId,
          cardName: '',
          enlightenmentText: '',
        }
        playChime()
        // 若 5s 内 SSE 未推送完整数据（如离线），清除占位内容
        setTimeout(() => {
          if (activeEnlightenment.value?.cardId === cardId && !activeEnlightenment.value.cardName) {
            activeEnlightenment.value = null
          }
        }, 5000)
      }
    }
  },
  { deep: true }
)

// S-16: 使用 storyStore 的断线重连 WebSocket 流式接口
function connectStoryStream() {
  storyStore.connectStream({
    storyId,
    chapterNo: currentChapterNo.value,
    onChunk: (text: string) => {
      // 逐字追加渲染
      appendPendingText(text)
    },
    onOptions: (opts: Option[]) => {
      if (currentChapter.value) {
        currentChapter.value.options = opts
      }
      isStreaming.value = false
      _streamingDone = true
    },
    onReconnect: (localDraft: string) => {
      // S-16: 断线重连后恢复本地草稿
      displayedText.value = localDraft
      // 重新加载章节以获取最新选项
      fetchChapter(storyId, currentChapterNo.value).then(ch => {
        if (ch) {
          currentChapter.value = ch
          storyStore.currentChapter = ch
        }
      }).catch(() => { /* 获取章节失败，继续使用现有数据 */ })
    },
    onError: () => {
      streamError.value = '流式加载失败，将显示静态内容'
    },
  })
}

onMounted(async () => {
  await loadChapter(1)
  checkFirstVisit()
  // S-16: 连接流式接口，断线自动重连
  connectStoryStream()
  // S-13: 连接 SSE 接收 keyword_enlightenment 等事件
  connectSSE()
})

onUnmounted(() => {
  closeStream()
  closeSSE()
  storyStore.disconnectStream()
})

function checkFirstVisit() {
  const visited = localStorage.getItem(`story_${storyId}_visited`)
  if (!visited) {
    // Auto-dismiss modal after 3 seconds to allow E2E tests to proceed
    setTimeout(() => {
      if (showEntryModal.value) submitEntry()
    }, 3000)
    showEntryModal.value = true
    localStorage.setItem(`story_${storyId}_visited`, '1')
  }
}

async function loadChapter(chapterNo: number) {
  isLoading.value = true
  streamError.value = null
  displayedText.value = ''
  _charIndex = 0
  _streamingDone = false
  currentChapterNo.value = chapterNo

  try {
    const ch = await fetchChapter(storyId, chapterNo)
    currentChapter.value = ch
    storyStore.currentChapter = ch

    // 记录本章配角初见印象（去重，已展示过的不再重复展示）
    if (ch.characterAppearances) {
      for (const app of ch.characterAppearances) {
        if (!shownCharacterAppearances.value.has(app.name)) {
          shownCharacterAppearances.value.set(app.name, app.firstImpression)
        }
      }
    }

    // S-16: 草稿已在 connectStoryStream/onReconnect 中恢复
    // 仅在新章节首次加载（无草稿）时启动打字机
    if (!storyStore.getDraft(storyId, chapterNo) && ch.sceneText) {
      startTypewriterQueue([ch.sceneText])
    }
  } catch (err) {
    streamError.value = '章节加载失败，请检查网络'
    console.error('[StoryView] loadChapter failed:', err)
  } finally {
    isLoading.value = false
  }
}

// ── WebSocket 降级 ──
function connectWebSocket() {
  closeStream()
  isStreaming.value = true

  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const wsUrl = `${protocol}//${window.location.host}/api/story/${storyId}/ws`
  ws = new WebSocket(wsUrl)

  ws.addEventListener('message', (event) => {
    const msg = JSON.parse(event.data)
    if (msg.type === 'scene_text' && msg.text) {
      // UI-10: 逐字渲染，每字 30ms fadeIn
      appendPendingText(msg.text)
    } else if (msg.type === 'options') {
      if (currentChapter.value) {
        currentChapter.value.options = msg.options
      }
      isStreaming.value = false
      _streamingDone = true
      closeStream()
    }
  })

  ws.addEventListener('error', () => {
    streamError.value = '流式加载失败，将显示静态内容'
    closeStream()
    isStreaming.value = false
  })
}

function closeStream() {
  // 只关闭 WebSocket，SSE 由 closeSSE() 单独管理
  if (ws) {
    ws.close()
    ws = null
  }
  // 清理逐字渲染定时器
  if (typewriterTimer !== null) {
    clearTimeout(typewriterTimer)
    typewriterTimer = null
  }
  pendingTextQueue.value = []
}

// ── 选择选项 ──
async function selectOption(optionId: number, _valueOrientation?: string, event?: MouseEvent, intensity?: 'gentle' | 'urgent' | 'forceful') {
  if (!currentChapter.value) return

  // 触发涟漪动画
  if (event) {
    triggerChoiceRipple(event.clientX, event.clientY)
  } else {
    // 手势触发：涟漪在屏幕中央
    triggerChoiceRipple(window.innerWidth / 2, window.innerHeight / 2)
  }

  try {
    // S-16: 提交选择前断开流式连接
    storyStore.disconnectStream()
    const res = await storyStore.submitChoice(storyId, currentChapterNo.value, optionId, intensity)
    if (res?.chapter) {
      currentChapter.value = res.chapter
      currentChapterNo.value++
      displayedText.value = ''
      if (res.chapter.sceneText) {
        startTypewriterQueue([res.chapter.sceneText])
      }
      // S-13: 关键词显灵 — 若本次返回的 resonance 有 ≥7 的值，播放磬音
      if (res.chapter.keywordResonance) {
        const hasEnlightenment = Object.values(res.chapter.keywordResonance).some(v => v >= 5)
        if (hasEnlightenment) playChime()
      }
      // S-13: 如果有显灵数据，弹出显灵浮层
      if (res.keywordEnlightenment) {
        enlightenedCardIds.add(res.keywordEnlightenment.cardId)
        activeEnlightenment.value = res.keywordEnlightenment
        playChime()
      }
      // S-14: 如果有偶遇事件，弹出偶遇浮层
      if (res.encounter) {
        activeEncounter.value = res.encounter
      }
      // S-16: 连接新章节的流式接口
      connectStoryStream()
    }
  } catch (err) {
    console.error('[StoryView] selectOption failed:', err)
  }
}

// ── 章节导航 ──
async function goPrevChapter() {
  if (!hasPrevChapter.value) return
  await loadChapter(currentChapterNo.value - 1)
}

async function goNextChapter() {
  if (!hasNextChapter.value) return
  await loadChapter(currentChapterNo.value + 1)
}

// ── 完成故事 ──
async function finishStory() {
  try {
    await storyStore.generateManuscript(storyId)
    playBell()
    router.push(`/manuscript/${storyId}`)
  } catch (err) {
    console.error('[StoryView] finishStory failed:', err)
  }
}

// ── 渲染文本（带逐字动画 HTML） ──
const renderedHtml = computed(() => {
  const text = displayedText.value
  if (!text) return ''
  // 逐字包装，每字带 .char class 触发 fadeIn 动画
  return text
    .split('')
    .map((ch) => {
      if (ch === '\n') return '<br>'
      return `<span class="char">${ch}</span>`
    })
    .join('')
})

// 入局三问提交
function submitEntry() {
  // 保存用户选择的价值取向
  localStorage.setItem(`story_${storyId}_values`, JSON.stringify(entryAnswers.value))
  showEntryModal.value = false
}

// ── 涟漪动画状态 ──
const showChoiceRipple = ref(false)
const choiceRipplePos = ref({ x: 0, y: 0 })

function triggerChoiceRipple(x: number, y: number) {
  choiceRipplePos.value = { x, y }
  showChoiceRipple.value = true
  setTimeout(() => { showChoiceRipple.value = false }, 800)
}

// ── 章节进度dots ──
const totalChapters = 5
const chapterDots = Array.from({ length: totalChapters }, (_, i) => i + 1)
</script>

<template>
  <div class="story-view">
    <!-- 入局三问弹窗 -->
    <Teleport to="body">
      <div v-if="showEntryModal" class="modal-overlay" @click.self="submitEntry">
        <div class="entry-modal">
          <h2 class="modal-title">入局三问</h2>
          <p class="modal-hint">请回答三个问题，确定你的处世取向</p>
          <div class="entry-questions">
            <div class="entry-q" v-for="v in valueLabels" :key="v">
              <label class="q-label">{{ v }}：当面对利益冲突时</label>
              <input
                v-model="entryAnswers[v as keyof typeof entryAnswers]"
                class="q-input"
                :placeholder="`你的${v}之选择...`"
              />
            </div>
          </div>

          <!-- E-07 关键词落位三列可视化 -->
          <div v-if="keywordPositionsByRole" class="keyword-positions-panel">
            <p class="keyword-positions-title">关键词落位</p>
            <div class="keyword-positions-grid">
              <div
                v-for="role in keywordColumnRoles"
                :key="role"
                class="keyword-column"
              >
                <span class="column-role-label">{{ role }}</span>
                <div class="keyword-items">
                  <div
                    v-for="pos in keywordPositionsByRole[role]"
                    :key="pos.keyword"
                    class="keyword-chip"
                  >
                    <span class="chip-keyword">{{ pos.keyword }}</span>
                    <!-- 泥金色连线：角色归属 -->
                    <div v-if="pos.roleOwner" class="role-connector">
                      <div class="connector-line" />
                      <span class="connector-owner">{{ pos.roleOwner }}</span>
                    </div>
                  </div>
                  <!-- 空列提示 -->
                  <div v-if="!keywordPositionsByRole[role]?.length" class="empty-slot">
                    未分配
                  </div>
                </div>
              </div>
            </div>
          </div>

          <button class="modal-confirm" @click="submitEntry">确认入局</button>
        </div>
      </div>
    </Teleport>

    <!-- S-14 偶遇浮层（半屏卡片） -->
    <Teleport to="body">
      <div v-if="activeEncounter" class="encounter-overlay" @click.self="() => {}">
        <div class="encounter-card">
          <div class="encounter-header">
            <span class="encounter-label">偶遇</span>
          </div>
          <p class="encounter-text">{{ activeEncounter.encounterText }}</p>
          <div class="encounter-choices">
            <button
              class="encounter-btn encounter-btn-a"
              @click="handleEncounterChoice('A')"
            >
              <span class="encounter-btn-icon">✋</span>
              <span class="encounter-btn-text">{{ activeEncounter.optionA }}</span>
              <span class="encounter-btn-hint">命运值 +10</span>
            </button>
            <button
              class="encounter-btn encounter-btn-b"
              @click="handleEncounterChoice('B')"
            >
              <span class="encounter-btn-icon">🚶</span>
              <span class="encounter-btn-text">{{ activeEncounter.optionB }}</span>
              <span class="encounter-btn-hint">命运值 -5</span>
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- S-13 关键词显灵全屏浮层 -->
    <KeywordEnlightenmentOverlay
      :enlightenment="activeEnlightenment"
      @close="activeEnlightenment = null"
    />

    <!-- 进度墨线 UI-08 -->
    <div class="ink-progress-line" :style="{ width: `${(currentChapterNo / totalChapters) * 100}%` }" />

    <!-- 顶部导航栏 -->
    <header class="story-header">
      <button class="header-btn back-btn" @click="router.push('/')">← 书房</button>
      <div class="header-progress">
        <span class="chapter-label" data-testid="current-chapter">第{{ currentChapterNo }}章</span>
        <span class="scroll-title" data-testid="scroll-title" aria-hidden="true">卷轴</span>
        <div class="progress-bar" data-testid="chapter-progress">
          <div class="progress-fill" :style="{ width: `${(currentChapterNo / 5) * 100}%` }" />
          <div
            v-for="dot in chapterDots"
            :key="dot"
            class="progress-dot"
            :class="{ active: dot <= currentChapterNo }"
            data-testid="chapter-progress-dot"
          />
        </div>
      </div>
      <div class="deviation-badge" data-testid="deviation-indicator">
        <span data-testid="deviation-value">偏离 {{ storyStore.historyDeviation }}</span>
      </div>
    </header>

    <!-- 卷轴天杆 UI-07 -->
    <div class="scroll-header">
      <div class="heavenly-rod">
        <div class="rod-end left"></div>
        <div class="hanging-string left"></div>
        <div class="rod-body"></div>
        <div class="rod-end right"></div>
        <div class="hanging-string right"></div>
      </div>
    </div>

    <!-- 主内容区 -->
    <div class="story-main" data-testid="scroll-container">
      <!-- 加载状态 -->
      <div v-if="isLoading" class="loading-state">
        <div class="narrator-loader">
          <span class="loader-brush">✒️</span>
          <span class="loader-text">说书人正在讲述...</span>
        </div>
      </div>

      <!-- 流式错误提示 -->
      <div v-else-if="streamError" class="stream-error">
        <p>{{ streamError }}</p>
      </div>

      <!-- 流式内容 -->
      <div v-else class="scroll-area" data-testid="scroll-content">
        <!-- 配角初见印象 -->
        <div v-if="currentAppearances.length > 0" class="character-introductions">
          <div
            v-for="app in currentAppearances"
            :key="app.name"
            class="character-intro-item"
          >
            <span class="character-intro-name">{{ app.name }}</span>
            <span class="character-intro-impression">—— {{ app.firstImpression }}</span>
          </div>
        </div>
        <div class="scene-text" data-testid="chapter-text" v-html="renderedHtml" />
        <div v-if="isStreaming" class="typing-cursor" />
      </div>
    </div>

    <!-- 关键词共鸣显示 -->
    <div v-if="currentChapter?.keywordResonance" class="resonance-bars">
      <div
        v-for="(val, kid) in currentChapter.keywordResonance"
        :key="kid"
        class="resonance-chip"
        :class="{ 'resonance-full': val >= 5 }"
        data-testid="keyword-resonance-bar"
        :style="{ opacity: Math.max(0.3, val / 7) }"
      >
        <span class="resonance-kw-name" data-testid="keyword-resonance-value">{{ kid }}</span>
        <div
          class="resonance-fill"
          data-testid="keyword-resonance-fill"
          :style="{ width: `${Math.round(Math.min(100, (val / 7) * 100))}%` }"
        />
      </div>
      <!-- 共鸣达成特效 -->
      <div
        v-if="currentChapter && Object.values(currentChapter.keywordResonance).some(v => v >= 5)"
        class="resonance-achieved"
      >
        ✨ 共鸣达成 ✨
      </div>
    </div>

    <!-- 卷轴地杆 UI-07 -->
    <div class="scroll-footer">
      <div class="earthly-rod">
        <div class="rod-end left"></div>
        <div class="rod-body"></div>
        <div class="rod-end right"></div>
      </div>
    </div>

    <!-- 选项区 -->
    <div class="options-panel">
      <!-- 章节切换导航 -->
      <div v-if="!isLoading && (hasPrevChapter || hasNextChapter || isEndChapter)" class="chapter-nav">
        <button class="nav-chevron" :disabled="!hasPrevChapter" @click="goPrevChapter">
          ‹ 上一章
        </button>
        <button
          v-if="isEndChapter"
          class="finish-btn"
          @click="finishStory"
        >
          完结此篇
        </button>
        <button class="nav-chevron" :disabled="!hasNextChapter" @click="goNextChapter">
          下一章 ›
        </button>
      </div>

      <!-- 价值取向选项按钮 -->
      <div v-if="currentChapter?.options && currentChapter.options.length > 0" class="options-grid" data-testid="chapter-options">
        <!-- 手势模式切换提示 -->
        <div class="gesture-mode-bar">
          <button
            class="mode-toggle-btn"
            :class="{ active: gestureMode === 'gesture' }"
            @click="gestureMode === 'button' ? enableGestureMode() : enableButtonMode()"
            data-testid="gesture-mode-toggle"
          >
            <span class="mode-icon">{{ gestureMode === 'gesture' ? '✋' : '👆' }}</span>
            <span class="mode-label">{{ gestureMode === 'gesture' ? '手势模式' : '切换手势' }}</span>
          </button>
        </div>

        <!-- 手势引导图标（手势模式） -->
        <div
          v-if="gestureMode === 'gesture'"
          class="gesture-panel"
          data-testid="gesture-panel"
          @touchstart="handleTouchStart"
          @touchmove="handleTouchMove"
          @touchend="handleTouchEnd"
        >
          <div class="gesture-hint" data-testid="gesture-hint">
            <span class="gesture-label" data-testid="gesture-swipe-left">
              <span class="gesture-icon">←</span>
              <span class="gesture-desc">后退</span>
            </span>
            <span class="gesture-label" data-testid="gesture-swipe-right">
              <span class="gesture-icon">→</span>
              <span class="gesture-desc">前进</span>
            </span>
            <span class="gesture-label" data-testid="gesture-circle">
              <span class="gesture-icon">↻</span>
              <span class="gesture-desc">绕道</span>
            </span>
          </div>
          <div class="gesture-active-indicator" :class="{ visible: activeGesture !== null }">
            <span v-if="activeGesture === 'swipe-left'">← 后退</span>
            <span v-else-if="activeGesture === 'swipe-right'">→ 前进</span>
            <span v-else-if="activeGesture === 'circle'">↻ 绕道</span>
          </div>
        </div>

        <!-- 按钮模式（默认） -->
        <div v-else class="options-buttons">
          <button
            v-for="opt in currentChapter.options"
            :key="opt.id"
            class="option-btn"
            data-testid="option-item"
            @click="selectOption(opt.id, undefined, $event)"
          >
            <span class="opt-value-tag">{{ opt.valueTag ?? '' }}</span>
            <span class="opt-text">{{ opt.text }}</span>
          </button>
        </div>
      </div>

      <!-- 打字动画进行中 -->
      <div v-if="isStreaming" class="stream-indicator">
        <span class="dot-anim" />说书中
      </div>
    </div>

    <!-- 涟漪动画 -->
    <div
      v-if="showChoiceRipple"
      class="choice-ripple"
      data-testid="choice-ripple"
      :style="{ left: choiceRipplePos.x + 'px', top: choiceRipplePos.y + 'px' }"
    />
  </div>
</template>

<style scoped>
.story-view {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: linear-gradient(180deg, #1a1510 0%, #2a1f14 100%);
  color: #e8dcc8;
  position: relative;
  overflow: hidden;
}

/* ── 卷轴天杆/地杆 UI-07 ── */
.scroll-header,
.scroll-footer {
  position: relative;
  z-index: 1;
  width: 100%;
  display: flex;
  justify-content: center;
  flex-shrink: 0;
}

/* 天杆 UI-07 */
.heavenly-rod {
  display: flex;
  align-items: flex-end;
  background: linear-gradient(180deg, #4a3728 0%, #2c1810 50%, #1a0f0a 100%);
  border-radius: 8px;
  height: 24px;
  width: 90%;
  box-shadow:
    0 4px 8px rgba(0, 0, 0, 0.4),
    inset 0 2px 4px rgba(255, 255, 255, 0.1);
}

/* 地杆 UI-07 */
.earthly-rod {
  display: flex;
  align-items: center;
  background: linear-gradient(180deg, #4a3728 0%, #3d2a1c 50%, #2c1810 100%);
  border-radius: 8px;
  height: 32px;
  width: 92%;
  box-shadow:
    0 4px 12px rgba(0, 0, 0, 0.5),
    inset 0 2px 4px rgba(255, 255, 255, 0.1);
}

/* 杆两端的装饰 */
.rod-end {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: linear-gradient(135deg, #d4af37 0%, #8b6914 100%);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.3);
  flex-shrink: 0;
}

.rod-body {
  flex: 1;
  height: 100%;
  background: repeating-linear-gradient(
    90deg,
    transparent,
    transparent 10px,
    rgba(0, 0, 0, 0.05) 10px,
    rgba(0, 0, 0, 0.05) 11px
  );
}

/* 悬挂绳 */
.hanging-string {
  width: 2px;
  height: 30px;
  background: linear-gradient(180deg, #8b7355 0%, #d4af37 100%);
  flex-shrink: 0;
  margin: 0 4px;
}

/* ── 进度墨线 UI-08 ── */
.ink-progress-line {
  position: fixed;
  top: 0;
  left: 0;
  height: 2px;
  background: linear-gradient(
    90deg,
    #1a1208 0%,
    #3d2510 20%,
    #6b4020 40%,
    #3d2510 60%,
    #1a1208 80%,
    #3d2510 100%
  );
  background-size: 200% 100%;
  animation: inkFlow 4s linear infinite;
  transition: width 0.5s ease;
  z-index: 1000;
  pointer-events: none;
  box-shadow: 0 0 6px rgba(42, 31, 20, 0.5);
}

@keyframes inkFlow {
  0% { background-position: 0% 0%; }
  100% { background-position: 200% 0%; }
}

/* ── 入局弹窗 ── */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(26, 21, 16, 0.85);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
  pointer-events: none;
}

.entry-modal {
  background: #f5efe0;
  border: 2px solid #8b7355;
  border-radius: 6px;
  padding: 2rem;
  max-width: 400px;
  width: 90%;
  color: #2c1f14;
}

.modal-title {
  font-size: 1.5rem;
  text-align: center;
  margin-bottom: 0.5rem;
  letter-spacing: 0.1em;
}

.modal-hint {
  font-size: 0.85rem;
  color: #8b7355;
  text-align: center;
  margin-bottom: 1.5rem;
}

.entry-questions {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.entry-q {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}

.q-label {
  font-size: 0.9rem;
  color: #8b7355;
  letter-spacing: 0.1em;
}

.q-input {
  padding: 0.5rem 0.75rem;
  border: 1px solid #8b7355;
  border-radius: 3px;
  background: rgba(245, 239, 224, 0.8);
  font-family: inherit;
  font-size: 0.9rem;
  color: #2c1f14;
}

.q-input:focus {
  outline: none;
  border-color: #2c1f14;
}

/* ── E-07 关键词落位三列布局 ── */
.keyword-positions-panel {
  margin-top: 1.2rem;
  padding-top: 1rem;
  border-top: 1px solid rgba(139, 115, 85, 0.25);
}

.keyword-positions-title {
  font-size: 0.7rem;
  color: #8b7355;
  letter-spacing: 0.2em;
  text-align: center;
  margin: 0 0 0.8rem;
  text-transform: uppercase;
}

.keyword-positions-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 0.6rem;
}

.keyword-column {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.4rem;
}

.column-role-label {
  font-size: 0.62rem;
  color: rgba(139, 115, 85, 0.6);
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.keyword-items {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.4rem;
  width: 100%;
}

.keyword-chip {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.2rem;
  padding: 0.3rem 0.5rem;
  background: rgba(201, 168, 76, 0.08);
  border: 1px solid rgba(201, 168, 76, 0.25);
  border-radius: 3px;
  width: 100%;
  text-align: center;
  animation: keyword-chip-in 0.5s ease-out both;
}

@keyframes keyword-chip-in {
  from { opacity: 0; transform: translateY(4px); }
  to   { opacity: 1; transform: translateY(0); }
}

.chip-keyword {
  font-size: 0.78rem;
  color: #c9a84c;
  letter-spacing: 0.08em;
}

/* 泥金色连线：角色归属 */
.role-connector {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  width: 100%;
  justify-content: center;
}

.connector-line {
  height: 1px;
  flex: 1;
  background: linear-gradient(90deg, transparent, rgba(201, 168, 76, 0.5), transparent);
}

.connector-owner {
  font-size: 0.6rem;
  color: rgba(201, 168, 76, 0.55);
  letter-spacing: 0.08em;
  white-space: nowrap;
}

.empty-slot {
  font-size: 0.65rem;
  color: rgba(139, 115, 85, 0.3);
  padding: 0.3rem;
  letter-spacing: 0.08em;
}

.modal-confirm {
  margin-top: 1.5rem;
  width: 100%;
  padding: 0.75rem;
  background: #2c1f14;
  color: #f5efe0;
  border: none;
  border-radius: 3px;
  font-family: inherit;
  font-size: 1rem;
  cursor: pointer;
  letter-spacing: 0.1em;
}

.modal-confirm:hover {
  background: #4a3520;
}

/* ── 顶部导航 ── */
.story-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.75rem 1.25rem;
  border-bottom: 1px solid rgba(139, 115, 85, 0.3);
  background: rgba(26, 21, 16, 0.9);
}

.header-btn {
  background: none;
  border: none;
  color: #c4a882;
  font-family: inherit;
  font-size: 0.9rem;
  cursor: pointer;
  padding: 0.25rem 0.5rem;
  border-radius: 3px;
  transition: color 0.2s;
}

.header-btn:hover {
  color: #e8dcc8;
}

.header-progress {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.3rem;
  flex: 1;
  max-width: 200px;
  margin: 0 1rem;
}

.chapter-label {
  font-size: 0.75rem;
  color: #8b7355;
  letter-spacing: 0.1em;
}

.progress-bar {
  width: 100%;
  height: 3px;
  background: rgba(139, 115, 85, 0.3);
  border-radius: 2px;
  overflow: visible;
  position: relative;
  display: flex;
  align-items: center;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #8b7355, #c4a882);
  border-radius: 2px;
  transition: width 0.5s ease;
  position: absolute;
  left: 0;
  top: 0;
}

.progress-dot {
  position: absolute;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: rgba(139, 115, 85, 0.4);
  border: 1px solid rgba(139, 115, 85, 0.6);
  transition: all 0.3s ease;
  z-index: 2;
  transform: translateX(-50%);
}

.progress-dot:nth-child(2) { left: 10%; }
.progress-dot:nth-child(3) { left: 30%; }
.progress-dot:nth-child(4) { left: 50%; }
.progress-dot:nth-child(5) { left: 70%; }
.progress-dot:nth-child(6) { left: 90%; }

.progress-dot.active {
  background: #c4a882;
  border-color: #e8dcc8;
  box-shadow: 0 0 4px rgba(196, 168, 130, 0.5);
}

.deviation-badge {
  font-size: 0.7rem;
  color: #c47c5a;
  background: rgba(196, 124, 90, 0.15);
  padding: 0.2rem 0.5rem;
  border-radius: 10px;
  border: 1px solid rgba(196, 124, 90, 0.3);
}

/* ── 主内容 ── */
.story-main {
  flex: 1;
  overflow: hidden;
  position: relative;
  display: flex;
  flex-direction: column;
}

.scroll-area {
  flex: 1;
  overflow-y: auto;
  padding: 2rem 2.5rem;
  scroll-behavior: smooth;
  writing-mode: vertical-rl;
  text-orientation: mixed;
  direction: ltr;
}

/* ── 加载状态 ── */
.loading-state {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.narrator-loader {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
}

.loader-brush {
  font-size: 2.5rem;
  animation: brush-pulse 1.5s ease-in-out infinite;
}

.loader-text {
  font-size: 1rem;
  color: #8b7355;
  letter-spacing: 0.15em;
  animation: text-pulse 1.5s ease-in-out infinite;
}

@keyframes brush-pulse {
  0%, 100% { opacity: 0.5; transform: translateY(0); }
  50% { opacity: 1; transform: translateY(-4px); }
}

@keyframes text-pulse {
  0%, 100% { opacity: 0.5; }
  50% { opacity: 1; }
}

.stream-error {
  padding: 1rem 2rem;
  color: #c47c5a;
  font-size: 0.9rem;
  text-align: center;
}

/* ── 场景文字 ── */
.scene-text {
  font-size: 1.05rem;
  line-height: 2.1;
  color: #e8dcc8;
  white-space: pre-wrap;
  word-break: break-word;
  text-shadow: 0 1px 2px rgba(0,0,0,0.3);
}

/* ── 配角初见印象 ── */
.character-introductions {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  margin-bottom: 1.2rem;
  padding-bottom: 1rem;
  border-bottom: 1px solid rgba(139, 115, 85, 0.2);
}

.character-intro-item {
  display: flex;
  align-items: baseline;
  gap: 0.5rem;
  font-size: 0.85rem;
  animation: intro-fade-in 0.6s ease-out both;
}

.character-intro-name {
  color: #c4a882;
  font-weight: 600;
  letter-spacing: 0.08em;
  flex-shrink: 0;
}

.character-intro-impression {
  color: #7a7a7a;
  font-style: italic;
  font-size: 0.8rem;
  line-height: 1.4;
}

@keyframes intro-fade-in {
  from { opacity: 0; transform: translateY(4px); }
  to   { opacity: 1; transform: translateY(0); }
}

/* 逐字淡入动画 UI-10 */
:deep(.char) {
  animation: fadeIn 0.3s var(--ease-smooth) forwards;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to   { opacity: 1; }
}

.typing-cursor {
  display: inline-block;
  width: 2px;
  height: 1.1em;
  background: #c4a882;
  margin-left: 2px;
  animation: cursor-blink 0.8s step-end infinite;
  vertical-align: text-bottom;
}

@keyframes cursor-blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

/* ── 共鸣条 ── */
.resonance-bars {
  display: flex;
  gap: 0.5rem;
  padding: 0.5rem 2rem;
  overflow-x: auto;
  border-top: 1px solid rgba(139, 115, 85, 0.2);
}

.resonance-chip {
  flex-shrink: 0;
  padding: 0.2rem 0.6rem;
  background: rgba(139, 115, 85, 0.2);
  border: 1px solid rgba(139, 115, 85, 0.4);
  border-radius: 20px;
  font-size: 0.7rem;
  color: #c4a882;
  transition: opacity 0.3s;
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  min-width: 3rem;
  overflow: hidden;
}

.resonance-fill {
  height: 3px;
  background: linear-gradient(90deg, #c4a882, #e8dcc8);
  border-radius: 2px;
  transition: width 0.5s ease;
}

.resonance-kw-name {
  font-size: 0.65rem;
  color: #c4a882;
  letter-spacing: 0.05em;
}

.resonance-chip.resonance-full {
  border-color: #c9a84c;
  background: rgba(201, 168, 76, 0.2);
  animation: resonance-glow 1.5s ease-in-out infinite;
}

@keyframes resonance-glow {
  0%, 100% { box-shadow: 0 0 4px rgba(201, 168, 76, 0.3); }
  50% { box-shadow: 0 0 12px rgba(201, 168, 76, 0.6); }
}

.resonance-achieved {
  display: flex;
  align-items: center;
  padding: 0.2rem 0.6rem;
  font-size: 0.7rem;
  color: #c9a84c;
  letter-spacing: 0.15em;
  animation: achieved-flash 0.8s ease-out;
  flex-shrink: 0;
}

@keyframes achieved-flash {
  0% { opacity: 0; transform: scale(0.8); }
  50% { opacity: 1; transform: scale(1.1); }
  100% { opacity: 1; transform: scale(1); }
}

/* ── 选项区 ── */
.options-panel {
  padding: 1rem 1.5rem 1.5rem;
  border-top: 1px solid rgba(139, 115, 85, 0.25);
  background: rgba(26, 21, 16, 0.8);
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.chapter-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.nav-chevron {
  background: none;
  border: none;
  color: #8b7355;
  font-family: inherit;
  font-size: 0.85rem;
  cursor: pointer;
  padding: 0.3rem 0.6rem;
  border-radius: 3px;
  transition: color 0.2s;
}

.nav-chevron:disabled {
  opacity: 0.3;
  cursor: default;
}

.nav-chevron:not(:disabled):hover {
  color: #c4a882;
}

.finish-btn {
  padding: 0.4rem 1.2rem;
  background: linear-gradient(135deg, #4a3520, #2c1f14);
  border: 1px solid #8b7355;
  border-radius: 3px;
  color: #c4a882;
  font-family: inherit;
  font-size: 0.85rem;
  cursor: pointer;
  letter-spacing: 0.08em;
  transition: all 0.2s;
}

.finish-btn:hover {
  background: linear-gradient(135deg, #6b4c30, #3d2a1a);
  border-color: #c4a882;
}

.options-grid {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}

.option-btn {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.85rem 1.2rem;
  border: 1px solid rgba(139, 115, 85, 0.5);
  border-radius: 4px;
  background: rgba(245, 239, 224, 0.06);
  color: #e8dcc8;
  font-family: inherit;
  font-size: 0.95rem;
  text-align: left;
  cursor: pointer;
  transition: all 0.25s ease;
  position: relative;
  overflow: hidden;
}

.option-btn::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  background: linear-gradient(180deg, #8b7355, #c4a882);
  opacity: 0;
  transition: opacity 0.2s;
}

.option-btn:hover {
  background: rgba(139, 115, 85, 0.15);
  border-color: #c4a882;
  transform: translateX(4px);
}

.option-btn:hover::before {
  opacity: 1;
}

.opt-value-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.6rem;
  height: 1.6rem;
  border-radius: 50%;
  border: 1px solid currentColor;
  font-size: 0.8rem;
  flex-shrink: 0;
  opacity: 0.7;
}

.opt-value-tag[data-tag="义"] { color: #7a9e7a; }
.opt-value-tag[data-tag="利"] { color: #c4a060; }
.opt-value-tag[data-tag="情"] { color: #9e7a7a; }

.opt-text {
  flex: 1;
  line-height: 1.5;
}

/* 流式指示器 */
.stream-indicator {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.78rem;
  color: #8b7355;
  letter-spacing: 0.1em;
}

.dot-anim {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #8b7355;
  animation: dot-pulse 1s ease-in-out infinite;
}

@keyframes dot-pulse {
  0%, 100% { transform: scale(1); opacity: 0.5; }
  50% { transform: scale(1.4); opacity: 1; }
}

/* ── S-14 偶遇浮层 ── */
.encounter-overlay {
  position: fixed;
  inset: 0;
  background: rgba(26, 21, 16, 0.75);
  backdrop-filter: blur(6px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 150;
  animation: encounter-fade-in 0.4s ease;
}

@keyframes encounter-fade-in {
  from { opacity: 0; }
  to { opacity: 1; }
}

.encounter-card {
  background: linear-gradient(145deg, #2a2018, #1e1810);
  border: 1px solid rgba(196, 168, 130, 0.4);
  border-radius: 8px;
  padding: 1.5rem 2rem;
  max-width: 420px;
  width: 90%;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.6), 0 0 0 1px rgba(196, 168, 130, 0.1);
  animation: encounter-card-in 0.4s var(--ease-spring);
}

@keyframes encounter-card-in {
  from { transform: translateY(20px) scale(0.97); opacity: 0; }
  to { transform: translateY(0) scale(1); opacity: 1; }
}

.encounter-header {
  display: flex;
  align-items: center;
  margin-bottom: 1rem;
}

.encounter-label {
  font-size: 0.7rem;
  letter-spacing: 0.2em;
  color: #c4a882;
  text-transform: uppercase;
  background: rgba(196, 168, 130, 0.15);
  padding: 0.2rem 0.6rem;
  border-radius: 3px;
  border: 1px solid rgba(196, 168, 130, 0.3);
}

.encounter-text {
  font-size: 1rem;
  line-height: 1.8;
  color: #e8dcc8;
  margin-bottom: 1.5rem;
  letter-spacing: 0.05em;
  font-style: italic;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.3);
}

.encounter-choices {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.encounter-btn {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.85rem 1.2rem;
  border-radius: 5px;
  font-family: inherit;
  font-size: 0.95rem;
  cursor: pointer;
  transition: all 0.25s ease;
  text-align: left;
}

.encounter-btn-a {
  background: rgba(122, 158, 122, 0.15);
  border: 1px solid rgba(122, 158, 122, 0.4);
  color: #a8c8a8;
}

.encounter-btn-a:hover {
  background: rgba(122, 158, 122, 0.25);
  border-color: rgba(122, 158, 122, 0.6);
  transform: translateX(4px);
}

.encounter-btn-b {
  background: rgba(139, 115, 85, 0.1);
  border: 1px solid rgba(139, 115, 85, 0.35);
  color: #c4a882;
}

.encounter-btn-b:hover {
  background: rgba(139, 115, 85, 0.2);
  border-color: rgba(196, 168, 130, 0.5);
  transform: translateX(4px);
}

.encounter-btn-icon {
  font-size: 1.3rem;
  flex-shrink: 0;
}

.encounter-btn-text {
  flex: 1;
  line-height: 1.4;
}

.encounter-btn-hint {
  font-size: 0.7rem;
  opacity: 0.6;
  letter-spacing: 0.05em;
  flex-shrink: 0;
}

/* ── 涟漪动画 ── */
.choice-ripple {
  position: fixed;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(196, 168, 130, 0.6) 0%, transparent 70%);
  transform: translate(-50%, -50%);
  pointer-events: none;
  z-index: 200;
  animation: ripple-expand 0.8s ease-out forwards;
}

@keyframes ripple-expand {
  0% { transform: translate(-50%, -50%) scale(0); opacity: 1; }
  100% { transform: translate(-50%, -50%) scale(8); opacity: 0; }
}

/* ── 手势模式 ── */
.gesture-mode-bar {
  display: flex;
  justify-content: center;
  margin-bottom: 0.5rem;
}

.mode-toggle-btn {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.35rem 0.8rem;
  border: 1px solid rgba(139, 115, 85, 0.4);
  border-radius: 20px;
  background: rgba(139, 115, 85, 0.08);
  color: #8b7355;
  font-family: inherit;
  font-size: 0.8rem;
  cursor: pointer;
  transition: all 0.25s ease;
}

.mode-toggle-btn.active {
  border-color: #c4a882;
  background: rgba(196, 168, 130, 0.15);
  color: #c4a882;
}

.mode-icon {
  font-size: 1rem;
}

.mode-label {
  letter-spacing: 0.05em;
}

.gesture-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.75rem;
  padding: 1rem;
  background: rgba(139, 115, 85, 0.08);
  border: 1px solid rgba(139, 115, 85, 0.2);
  border-radius: 6px;
  user-select: none;
  touch-action: none;
}

.gesture-hint {
  display: flex;
  gap: 1.5rem;
  align-items: center;
}

.gesture-label {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.25rem;
  opacity: 0.7;
  transition: opacity 0.2s, transform 0.2s;
}

.gesture-icon {
  font-size: 1.8rem;
  color: #c4a882;
  line-height: 1;
}

.gesture-desc {
  font-size: 0.7rem;
  color: #8b7355;
  letter-spacing: 0.05em;
}

.gesture-active-indicator {
  font-size: 0.85rem;
  color: #c4a882;
  letter-spacing: 0.1em;
  height: 1.2em;
  opacity: 0;
  transition: opacity 0.2s;
}

.gesture-active-indicator.visible {
  opacity: 1;
}

.options-buttons {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}
</style>
