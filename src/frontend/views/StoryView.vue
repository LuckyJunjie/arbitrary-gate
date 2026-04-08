<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useStoryStore } from '@/stores/storyStore'
import { fetchChapter } from '@/services/api'
import type { Chapter, Option } from '@/services/api'

const route = useRoute()
const router = useRouter()
const storyStore = useStoryStore()

const storyId = route.params.id as string

// ── 流式渲染状态 ──
const displayedText = ref('') // 已渲染的纯文本
const currentChapter = ref<Chapter | null>(null)
const isStreaming = ref(false)
const isLoading = ref(true)
const streamError = ref<string | null>(null)
const typewriterQueue = ref<string[]>([]) // 待渲染段落队列

// ── SSE / WS 连接 ──
let eventSource: EventSource | null = null
let ws: WebSocket | null = null
let charIndex = 0
let streamingDone = false

// ── 入局三问弹窗状态 ──
const showEntryModal = ref(false)
const entryAnswers = ref({义: '', 利: '', 情: ''})

// ── 章节导航 ──
const currentChapterNo = ref(1)
const hasPrevChapter = computed(() => currentChapterNo.value > 1)
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

onMounted(async () => {
  await loadChapter(1)
  checkFirstVisit()
})

onUnmounted(() => {
  closeStream()
})

function checkFirstVisit() {
  const visited = localStorage.getItem(`story_${storyId}_visited`)
  if (!visited) {
    showEntryModal.value = true
    localStorage.setItem(`story_${storyId}_visited`, '1')
  }
}

async function loadChapter(chapterNo: number) {
  isLoading.value = true
  streamError.value = null
  displayedText.value = ''
  charIndex = 0
  streamingDone = false
  currentChapterNo.value = chapterNo

  try {
    const ch = await fetchChapter(storyId, chapterNo)
    currentChapter.value = ch
    storyStore.currentChapter = ch

    if (ch.sceneText) {
      // 非流式模式：直接渲染，带打字机效果
      startTypewriterQueue([ch.sceneText])
    }
  } catch (err) {
    streamError.value = '章节加载失败，请检查网络'
    console.error('[StoryView] loadChapter failed:', err)
  } finally {
    isLoading.value = false
  }
}

function startTypewriterQueue(paragraphs: string[]) {
  typewriterQueue.value = paragraphs
  streamingDone = false
  isStreaming.value = true
  processTypewriter()
}

function processTypewriter() {
  if (typewriterQueue.value.length === 0) {
    isStreaming.value = false
    streamingDone = true
    return
  }
  const paragraph = typewriterQueue.value.shift()!
  charIndex = 0
  animateParagraph(paragraph)
}

function animateParagraph(text: string) {
  // 每次渲染一小段（几个字符）实现平滑打字机效果
  const chunkSize = 3
  function addChunk() {
    if (charIndex >= text.length) {
      displayedText.value += '\n'
      processTypewriter()
      return
    }
    const end = Math.min(charIndex + chunkSize, text.length)
    displayedText.value += text.slice(charIndex, end)
    charIndex = end
    setTimeout(addChunk, 18)
  }
  addChunk()
}

// ── SSE 流式连接 ──
function connectSSE() {
  closeStream()
  isStreaming.value = true

  const url = `/api/story/${storyId}/stream`
  eventSource = new EventSource(url)

  eventSource.addEventListener('open', () => {
    streamError.value = null
    console.log('[StoryView] SSE connected')
  })

  eventSource.addEventListener('scene_text', (e: MessageEvent) => {
    const text = e.data
    if (text) {
      displayedText.value += text
    }
  })

  eventSource.addEventListener('scene_start', (e: MessageEvent) => {
    displayedText.value = ''
    const data = JSON.parse(e.data)
    currentChapter.value = data.chapter ?? currentChapter.value
  })

  eventSource.addEventListener('options', (e: MessageEvent) => {
    const opts: Option[] = JSON.parse(e.data)
    if (currentChapter.value) {
      currentChapter.value.options = opts
    }
    isStreaming.value = false
    streamingDone = true
    closeStream()
  })

  eventSource.addEventListener('error', () => {
    console.warn('[StoryView] SSE error, falling back to WebSocket')
    closeStream()
    connectWebSocket()
  })
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
      displayedText.value += msg.text
    } else if (msg.type === 'options') {
      if (currentChapter.value) {
        currentChapter.value.options = msg.options
      }
      isStreaming.value = false
      streamingDone = true
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
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
  if (ws) {
    ws.close()
    ws = null
  }
}

// ── 选择选项 ──
async function selectOption(optionId: number, valueOrientation?: string) {
  if (!currentChapter.value) return

  try {
    const res = await storyStore.submitChoice(storyId, currentChapterNo.value, optionId)
    if (res?.chapter) {
      currentChapter.value = res.chapter
      currentChapterNo.value++
      displayedText.value = ''
      if (res.chapter.sceneText) {
        startTypewriterQueue([res.chapter.sceneText])
      }
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
    router.push(`/share/${storyId}`)
  } catch (err) {
    console.error('[StoryView] finishStory failed:', err)
  }
}

// ── 渲染文本（带逐字动画 HTML） ──
const renderedHtml = computed(() => {
  const text = displayedText.value
  if (!text) return ''
  // 将文本分割为可渲染的字符段，每段 wrapped in <span class="char">
  return text
    .split('')
    .map((ch, i) => {
      if (ch === '\n') return '<br>'
      return `<span class="char" style="animation-delay:${(i % 60) * 20}ms">${ch}</span>`
    })
    .join('')
})

// 入局三问提交
function submitEntry() {
  // 保存用户选择的价值取向
  localStorage.setItem(`story_${storyId}_values`, JSON.stringify(entryAnswers.value))
  showEntryModal.value = false
}
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
          <button class="modal-confirm" @click="submitEntry">确认入局</button>
        </div>
      </div>
    </Teleport>

    <!-- 顶部导航栏 -->
    <header class="story-header">
      <button class="header-btn back-btn" @click="router.push('/')">← 书房</button>
      <div class="header-progress">
        <span class="chapter-label">第{{ currentChapterNo }}章</span>
        <div class="progress-bar">
          <div class="progress-fill" :style="{ width: `${(currentChapterNo / 5) * 100}%` }" />
        </div>
      </div>
      <div class="deviation-badge" v-if="storyStore.historyDeviation > 0">
        偏离 {{ storyStore.historyDeviation }}
      </div>
    </header>

    <!-- 主内容区 -->
    <div class="story-main">
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
      <div v-else class="scroll-area">
        <div class="scene-text" v-html="renderedHtml" />
        <div v-if="isStreaming" class="typing-cursor" />
      </div>
    </div>

    <!-- 关键词共鸣显示 -->
    <div v-if="currentChapter?.keywordResonance" class="resonance-bar">
      <span
        v-for="(val, kid) in currentChapter.keywordResonance"
        :key="kid"
        class="resonance-chip"
        :style="{ opacity: Math.max(0.3, val / 7) }"
      >
        {{ kid }}
      </span>
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
      <div v-if="currentChapter?.options && currentChapter.options.length > 0" class="options-grid">
        <button
          v-for="opt in currentChapter.options"
          :key="opt.id"
          class="option-btn"
          @click="selectOption(opt.id)"
        >
          <span class="opt-value-tag">{{ opt.valueTag ?? '' }}</span>
          <span class="opt-text">{{ opt.text }}</span>
        </button>
      </div>

      <!-- 打字动画进行中 -->
      <div v-if="isStreaming" class="stream-indicator">
        <span class="dot-anim" />说书中
      </div>
    </div>
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
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #8b7355, #c4a882);
  border-radius: 2px;
  transition: width 0.5s ease;
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

/* 逐字淡入动画 */
:deep(.char) {
  animation: char-fade-in 0.4s ease-out both;
}

@keyframes char-fade-in {
  from { opacity: 0; transform: translateY(3px); }
  to   { opacity: 1; transform: translateY(0); }
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
.resonance-bar {
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
</style>
