<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useStoryStore } from '@/stores/storyStore'
import { generateEntryQuestions, submitEntryAnswers } from '@/services/api'

// ==================== 类型定义 ====================

export interface EntryQuestion {
  id: number
  category: '角色背景' | '当下处境' | '内心渴望'
  question: string
  hint: string
}

export interface EntryAnswer {
  questionId: number
  question: string
  answer: string
}

// ==================== 风格选项 ====================

export interface StyleOption {
  value: number
  label: string
  example: string
}

const styleOptions: StyleOption[] = [
  {
    value: 1,
    label: '白描',
    example: '"他推门进来，袖口湿了一片，脸上什么表情也没有。"',
  },
  {
    value: 2,
    label: '江湖',
    example: '"好一条汉子！虎背熊腰，腰间斜挎一把鬼头刀，一进门就把掌柜的吓得缩进了柜台底下。"',
  },
  {
    value: 3,
    label: '笔记',
    example: '"此事说来寻常，然细察之下，颇有可异者。盖因当日天气阴晦，人皆闭户。"',
  },
  {
    value: 4,
    label: '话本',
    example: '"看官，你道这故事出在何处？话说大唐天宝年间，京兆府有一处所在，名唤醉仙楼。"',
  },
]

// ==================== 状态 ====================

const router = useRouter()
const storyStore = useStoryStore()

const isLoading = ref(true)
const isSubmitting = ref(false)
const loadError = ref<string | null>(null)
const submitError = ref<string | null>(null)

const questions = ref<EntryQuestion[]>([])
const answers = ref<Record<number, string>>({})

// 选中的关键词和事件卡（从 localStorage 读取）
const selectedKeywords = ref<Array<{ id: number; name: string }>>([])
const selectedEvent = ref<{ id: number; name: string } | null>(null)

// 风格选择（默认白描）
const selectedStyle = ref<number>(1)

// ==================== 计算属性 ====================

const canSubmit = computed(() => {
  // 所有问题都必须回答
  return questions.value.every(q => {
    const ans = answers.value[q.id]
    return ans && ans.trim().length > 0
  })
})

const filledCount = computed(() => {
  return questions.value.filter(q => {
    const ans = answers.value[q.id]
    return ans && ans.trim().length > 0
  }).length
})

// ==================== 生命周期 ====================

onMounted(async () => {
  loadSelectedCards()
  await loadQuestions()
})

// ==================== 方法 ====================

function loadSelectedCards() {
  const kwRaw = localStorage.getItem('selectedKeywordCards')
  const evRaw = localStorage.getItem('selectedEventCard')
  if (kwRaw) {
    try {
      selectedKeywords.value = JSON.parse(kwRaw)
    } catch {
      selectedKeywords.value = []
    }
  }
  if (evRaw) {
    try {
      selectedEvent.value = JSON.parse(evRaw)
    } catch {
      selectedEvent.value = null
    }
  }
}

async function loadQuestions() {
  isLoading.value = true
  loadError.value = null

  try {
    const keywordIds = selectedKeywords.value.map(c => c.id)
    const eventId = selectedEvent.value?.id

    const data = await generateEntryQuestions({
      keywordIds,
      eventId,
    })
    questions.value = data.questions
  } catch (err) {
    console.error('[EntryQuestionsView] loadQuestions failed:', err)
    // 如果 API 不可用，使用默认问题
    loadError.value = '问题加载失败，使用默认问题'
    questions.value = getDefaultQuestions()
  } finally {
    isLoading.value = false
  }
}

function getDefaultQuestions(): EntryQuestion[] {
  return [
    {
      id: 1,
      category: '角色背景',
      question: '你今日当值，袖中揣着什么？',
      hint: '这个问题将决定你的初始装备',
    },
    {
      id: 2,
      category: '当下处境',
      question: '你最怕见到什么人？',
      hint: '你的恐惧将影响故事走向',
    },
    {
      id: 3,
      category: '内心渴望',
      question: '你最大的心愿是什么？',
      hint: '你的渴望将成为故事的暗线',
    },
  ]
}

async function handleSubmit() {
  if (!canSubmit.value || isSubmitting.value) return

  isSubmitting.value = true
  submitError.value = null

  try {
    const keywordIds = selectedKeywords.value.map(c => c.id)
    const eventId = selectedEvent.value?.id

    const entryAnswers: EntryAnswer[] = questions.value.map(q => ({
      questionId: q.id,
      question: q.question,
      answer: answers.value[q.id] || '',
    }))

    // 调用开始故事 API，同时提交入局答案
    const story = await submitEntryAnswers({
      keywordIds,
      eventId,
      entryAnswers,
      style: selectedStyle.value,
    })

    // 保存到 storyStore
    storyStore.setCurrentStory(story)
    storyStore.entryAnswers = entryAnswers

    // 跳转到故事页
    router.push(`/story/${story.id}`)
  } catch (err) {
    console.error('[EntryQuestionsView] submit failed:', err)
    submitError.value = '提交失败，请重试'
  } finally {
    isSubmitting.value = false
  }
}

function handleSkip() {
  // 用户选择跳过，直接开始故事
  const keywordIds = selectedKeywords.value.map(c => c.id)
  const eventId = selectedEvent.value?.id

  storyStore.startStory({ keywords: keywordIds }).then(story => {
    router.push(`/story/${story.id}`)
  })
}
</script>

<template>
  <div class="entry-questions-view" data-testid="entry-questions-container">
    <!-- 顶部标题区 -->
    <header class="entry-header">
      <div class="header-inner">
        <h1 class="entry-title">入局三问</h1>
        <p class="entry-subtitle">请回答三个问题，锚定你的角色</p>
      </div>

      <!-- 关键词预览 -->
      <div v-if="selectedKeywords.length > 0" class="keyword-preview">
        <span class="preview-label">你的关键词</span>
        <div class="preview-tags">
          <span
            v-for="kw in selectedKeywords"
            :key="kw.id"
            class="preview-tag"
          >
            {{ kw.name }}
          </span>
        </div>
      </div>

      <!-- 事件预览 -->
      <div v-if="selectedEvent" class="event-preview">
        <span class="preview-label">历史现场</span>
        <span class="preview-event">{{ selectedEvent.name }}</span>
      </div>
    </header>

    <!-- 加载状态 -->
    <div v-if="isLoading" class="loading-state" data-testid="questions-loading">
      <div class="loading-brush">✒️</div>
      <p class="loading-text">说书人正在构思问题...</p>
    </div>

    <!-- 错误提示 -->
    <div v-else-if="loadError" class="load-error">
      <p>{{ loadError }}</p>
    </div>

    <!-- 问题区域（卷轴竖排） -->
    <div v-else class="questions-scroll-area">
      <!-- 风格选择步骤 -->
      <div class="style-step">
        <h2 class="style-step-title">请选择文风</h2>
        <div class="style-tabs">
          <button
            v-for="opt in styleOptions"
            :key="opt.value"
            class="style-tab"
            :class="{ active: selectedStyle === opt.value }"
            @click="selectedStyle = opt.value"
          >
            <span class="style-tab-label">{{ opt.label }}</span>
          </button>
        </div>
        <p class="style-example">
          {{ styleOptions.find(o => o.value === selectedStyle)?.example }}
        </p>
      </div>

      <div class="questions-vertical">
        <div
          v-for="q in questions"
          :key="q.id"
          class="question-item"
          data-testid="question-item"
        >
          <!-- 题目 -->
          <div class="q-header">
            <span class="q-category">{{ q.category }}</span>
            <p class="q-text" data-testid="question-text">{{ q.question }}</p>
            <p class="q-hint" data-testid="question-hint">{{ q.hint }}</p>
          </div>

          <!-- 竖排输入框 -->
          <div class="q-input-wrapper">
            <textarea
              v-model="answers[q.id]"
              class="q-input"
              data-testid="question-input"
              :placeholder="`请回答...`"
              rows="3"
              maxlength="200"
            />
            <span class="q-char-count">{{ (answers[q.id] || '').length }}/200</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 底部操作区 -->
    <footer class="entry-footer">
      <!-- 进度指示 -->
      <div class="fill-progress">
        <span class="progress-text">已填写 {{ filledCount }}/3</span>
        <div class="progress-dots">
          <span
            v-for="q in questions"
            :key="q.id"
            class="dot"
            :class="{ filled: answers[q.id]?.trim() }"
          />
        </div>
      </div>

      <!-- 错误提示 -->
      <p v-if="submitError" class="submit-error" data-testid="entry-error">
        {{ submitError }}
      </p>

      <!-- 操作按钮 -->
      <div class="action-buttons">
        <button class="skip-btn" @click="handleSkip">跳过</button>
        <button
          class="confirm-btn"
          data-testid="confirm-entry-btn"
          :disabled="!canSubmit || isSubmitting"
          @click="handleSubmit"
        >
          <span v-if="isSubmitting">入局中...</span>
          <span v-else>确认入局</span>
        </button>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.entry-questions-view {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: linear-gradient(180deg, #1a1510 0%, #2a1f14 50%, #1a1510 100%);
  color: #e8dcc8;
  position: relative;
  overflow: hidden;
}

/* ── 顶部区域 ── */
.entry-header {
  padding: 1.5rem 2rem 1rem;
  border-bottom: 1px solid rgba(139, 115, 85, 0.2);
  background: rgba(26, 21, 16, 0.6);
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.header-inner {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}

.entry-title {
  font-size: 1.4rem;
  letter-spacing: 0.25em;
  color: #c9a84c;
  margin: 0;
  text-align: center;
}

.entry-subtitle {
  font-size: 0.8rem;
  color: rgba(232, 220, 200, 0.5);
  letter-spacing: 0.15em;
  text-align: center;
  margin: 0;
}

.keyword-preview,
.event-preview {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  flex-wrap: wrap;
}

.preview-label {
  font-size: 0.7rem;
  color: rgba(139, 115, 85, 0.8);
  letter-spacing: 0.1em;
  white-space: nowrap;
}

.preview-tags {
  display: flex;
  gap: 0.4rem;
  flex-wrap: wrap;
}

.preview-tag {
  padding: 0.15rem 0.5rem;
  background: rgba(139, 115, 85, 0.15);
  border: 1px solid rgba(139, 115, 85, 0.4);
  border-radius: 12px;
  font-size: 0.7rem;
  color: #c4a882;
}

.preview-event {
  font-size: 0.75rem;
  color: #c4a882;
  font-style: italic;
}

/* ── 加载状态 ── */
.loading-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1.5rem;
}

.loading-brush {
  font-size: 3rem;
  animation: brush-dip 1.5s ease-in-out infinite;
}

@keyframes brush-dip {
  0%, 100% { opacity: 0.4; transform: translateY(0) rotate(-5deg); }
  50% { opacity: 1; transform: translateY(-8px) rotate(5deg); }
}

.loading-text {
  font-size: 0.9rem;
  color: rgba(139, 115, 85, 0.8);
  letter-spacing: 0.2em;
}

.load-error {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c47c5a;
  font-size: 0.85rem;
}

/* ── 风格选择步骤 ── */
.style-step {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
  padding: 1.5rem 2rem;
  margin-bottom: 1rem;
  border: 1px solid rgba(139, 115, 85, 0.3);
  border-radius: 6px;
  background: rgba(245, 239, 224, 0.04);
}

.style-step-title {
  font-size: 0.9rem;
  letter-spacing: 0.2em;
  color: #c9a84c;
  margin: 0;
  text-align: center;
}

.style-tabs {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
  justify-content: center;
}

.style-tab {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 0.5rem 1.2rem;
  background: rgba(139, 115, 85, 0.1);
  border: 1px solid rgba(139, 115, 85, 0.4);
  border-radius: 4px;
  color: rgba(232, 220, 200, 0.7);
  font-family: inherit;
  font-size: 0.85rem;
  cursor: pointer;
  transition: all 0.25s;
}

.style-tab:hover {
  border-color: rgba(201, 168, 76, 0.6);
  color: #e8dcc8;
}

.style-tab.active {
  background: rgba(201, 168, 76, 0.15);
  border-color: #c9a84c;
  color: #c9a84c;
  box-shadow: 0 0 8px rgba(201, 168, 76, 0.2);
}

.style-tab-label {
  letter-spacing: 0.15em;
}

.style-example {
  font-size: 0.75rem;
  color: rgba(139, 115, 85, 0.75);
  font-style: italic;
  text-align: center;
  margin: 0;
  max-width: 360px;
  line-height: 1.6;
  min-height: 2.5em;
}

/* ── 问题区域（竖排卷轴） ── */
.questions-scroll-area {
  flex: 1;
  overflow-y: auto;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 1.5rem;
}

.questions-vertical {
  display: flex;
  flex-direction: column;
  gap: 2rem;
  writing-mode: vertical-rl;
  text-orientation: mixed;
  direction: ltr;
  max-height: 100%;
  overflow-y: hidden;
}

.question-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
  padding: 1.5rem 1rem;
  border: 1px solid rgba(139, 115, 85, 0.3);
  border-radius: 6px;
  background: rgba(245, 239, 224, 0.04);
  min-width: 280px;
  max-height: calc(100vh - 220px);
  transition: border-color 0.3s;
}

.question-item:hover {
  border-color: rgba(201, 168, 76, 0.5);
}

.q-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  text-align: center;
}

.q-category {
  font-size: 0.65rem;
  letter-spacing: 0.15em;
  color: rgba(201, 168, 76, 0.7);
  text-transform: uppercase;
}

.q-text {
  font-size: 1.1rem;
  color: #e8dcc8;
  line-height: 1.6;
  margin: 0;
  writing-mode: horizontal-tb;
  text-orientation: mixed;
  letter-spacing: 0.05em;
}

.q-hint {
  font-size: 0.7rem;
  color: rgba(139, 115, 85, 0.7);
  margin: 0;
  writing-mode: horizontal-tb;
  font-style: italic;
}

.q-input-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.3rem;
  writing-mode: horizontal-tb;
}

.q-input {
  width: 100%;
  max-width: 240px;
  padding: 0.6rem 0.75rem;
  background: rgba(245, 239, 224, 0.08);
  border: 1px solid rgba(139, 115, 85, 0.4);
  border-radius: 3px;
  color: #e8dcc8;
  font-family: inherit;
  font-size: 0.9rem;
  line-height: 1.5;
  resize: none;
  transition: border-color 0.2s, background 0.2s;
}

.q-input::placeholder {
  color: rgba(139, 115, 85, 0.5);
  font-style: italic;
}

.q-input:focus {
  outline: none;
  border-color: #c9a84c;
  background: rgba(245, 239, 224, 0.12);
}

.q-char-count {
  font-size: 0.6rem;
  color: rgba(139, 115, 85, 0.5);
  align-self: flex-end;
}

/* ── 底部操作区 ── */
.entry-footer {
  padding: 1rem 2rem 1.5rem;
  border-top: 1px solid rgba(139, 115, 85, 0.2);
  background: rgba(26, 21, 16, 0.8);
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.fill-progress {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.progress-text {
  font-size: 0.75rem;
  color: rgba(139, 115, 85, 0.7);
  letter-spacing: 0.1em;
}

.progress-dots {
  display: flex;
  gap: 0.4rem;
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: rgba(139, 115, 85, 0.3);
  border: 1px solid rgba(139, 115, 85, 0.5);
  transition: all 0.3s;
}

.dot.filled {
  background: #c9a84c;
  border-color: #e8dcc8;
  box-shadow: 0 0 4px rgba(201, 168, 76, 0.5);
}

.submit-error {
  font-size: 0.8rem;
  color: #c96c6c;
  text-align: center;
  margin: 0;
}

.action-buttons {
  display: flex;
  gap: 1rem;
  justify-content: center;
}

.skip-btn {
  padding: 0.65rem 1.5rem;
  background: transparent;
  border: 1px solid rgba(139, 115, 85, 0.4);
  border-radius: 3px;
  color: rgba(232, 220, 200, 0.6);
  font-family: inherit;
  font-size: 0.85rem;
  cursor: pointer;
  letter-spacing: 0.1em;
  transition: all 0.2s;
}

.skip-btn:hover {
  border-color: rgba(139, 115, 85, 0.7);
  color: #e8dcc8;
}

.confirm-btn {
  flex: 1;
  max-width: 200px;
  padding: 0.75rem 2rem;
  background: linear-gradient(135deg, #4a3520, #2c1f14);
  border: 1px solid #c9a84c;
  border-radius: 3px;
  color: #c9a84c;
  font-family: inherit;
  font-size: 0.95rem;
  cursor: pointer;
  letter-spacing: 0.15em;
  transition: all 0.25s;
}

.confirm-btn:hover:not(:disabled) {
  background: linear-gradient(135deg, #6b4c30, #4a3520);
  border-color: #e8dcc8;
  color: #e8dcc8;
  box-shadow: 0 0 16px rgba(201, 168, 76, 0.2);
}

.confirm-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
</style>
