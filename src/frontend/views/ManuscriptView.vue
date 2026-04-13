<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useStoryStore } from '@/stores/storyStore'
import { useInkValueStore } from '@/stores/inkValueStore'
import { useCardStore } from '@/stores/cardStore'
import RippleEffect from '@/components/RippleEffect.vue'
import TitleSelectModal from '@/components/TitleSelectModal.vue'
import { updateStoryTitle, aiPainter, createShare } from '@/services/api'

const route = useRoute()
const router = useRouter()
const storyStore = useStoryStore()
const inkValueStore = useInkValueStore()

const storyId = route.params.id as string
const isLoading = ref(true)
const loadError = ref<string | null>(null)
const showRipple = ref(false)

// 标题选择浮层
const showTitleModal = ref(false)

const candidateTitles = computed<string[]>(() => {
  // 优先从 finishStory 返回的 manuscript.candidateTitles 获取
  const fromManuscript = storyStore.manuscript?.candidateTitles
  if (fromManuscript?.length) return fromManuscript
  // 其次从 currentStory.candidateTitles 获取
  const fromStory = (storyStore.currentStory as any)?.candidateTitles
  if (fromStory?.length) return fromStory
  return []
})

const hasTitleSelected = computed(() => {
  // 用户已选过标题（localStorage 持久化）
  if (!storyId) return true
  const selected = localStorage.getItem(`title_selected:${storyId}`)
  return selected === 'true'
})

function openTitleModal() {
  showTitleModal.value = true
}

async function onTitleSelected(title: string) {
  showTitleModal.value = false
  try {
    await updateStoryTitle(storyId, title)
    // 更新 store 中的 title
    if (storyStore.currentStory) {
      storyStore.currentStory.title = title
    }
    // 标记已选
    localStorage.setItem(`title_selected:${storyId}`, 'true')
  } catch (err) {
    console.error('[ManuscriptView] onTitleSelected failed:', err)
    // 即使失败也关闭浮层，保留默认标题
  }
}

// ── SH-01: 分享创建 ──
const cardStore = useCardStore()

const showShareModal = ref(false)
const selectedKeywordCardId = ref<number | null>(null)
const isCreatingShare = ref(false)
const shareCreationError = ref<string | null>(null)

const categoryNames: Record<number, string> = {
  1: '器物', 2: '职人', 3: '风物', 4: '情绪', 5: '称谓'
}

const rarityNames: Record<number, string> = {
  1: '凡', 2: '珍', 3: '奇', 4: '绝'
}

// 可用于分享的关键词卡（从卡匣加载）
const selectableKeywordCards = computed(() => {
  return cardStore.keywordCards
})

function openShareModal() {
  showShareModal.value = true
  selectedKeywordCardId.value = null
  shareCreationError.value = null
}

async function handleCreateShare() {
  if (!selectedKeywordCardId.value || !storyStore.currentStory?.id) return
  isCreatingShare.value = true
  shareCreationError.value = null
  try {
    const result = await createShare({
      storyId: Number(storyStore.currentStory.id),
      cardId: selectedKeywordCardId.value,
    })
    showShareModal.value = false
    // 跳转到分享页
    router.push(`/share/${result.shareCode}`)
  } catch (err: any) {
    shareCreationError.value = err?.response?.data?.message || err.message || '生成分享码失败'
  } finally {
    isCreatingShare.value = false
  }
}

// ── 加载手稿数据 ──
onMounted(async () => {
  // 墨香值时间衰减检查
  inkValueStore.checkAndDecayOnAppStart()

  // 触发入场涟漪动画
  showRipple.value = true
  setTimeout(() => { showRipple.value = false }, 2000)

  if (storyStore.manuscript && storyStore.currentStory?.id === storyId) {
    // 已有缓存
    isLoading.value = false
  } else {
    try {
      await storyStore.fetchManuscript(storyId)
    } catch (err) {
      loadError.value = '手稿加载失败'
      console.error('[ManuscriptView] fetchManuscript failed:', err)
    } finally {
      isLoading.value = false
    }
  }

  // C-14: 手稿加载后生成场景图（用于分享卡/纪念卡）
  await generateSceneImage()

  // 首次查看时，检查是否需要弹出标题选择浮层
  // 有备选标题 且 用户尚未选择过标题 → 弹出浮层
  if (candidateTitles.value.length > 0 && !hasTitleSelected.value) {
    openTitleModal()
  }
})

// C-14: AI 画师场景图
const sceneImageUrl = ref<string>('')
const isGeneratingScene = ref(false)

async function generateSceneImage() {
  const ms = storyStore.manuscript
  if (!ms || !storyStore.currentStory) return
  isGeneratingScene.value = true
  try {
    const title = storyStore.currentStory.title || '时光笺'
    const description = ms.baiguanComment || ms.fullText?.slice(0, 100) || title
    const keywords = ms.inscription ? [ms.inscription] : ['水墨', '古风']
    const result = await aiPainter.generateSceneImage({
      storyTitle: title,
      chapterNo: 1,
      sceneDescription: description,
      keywords,
    })
    sceneImageUrl.value = result.imageUrl
  } catch (err) {
    console.warn('[ManuscriptView] 场景图生成失败:', err)
  } finally {
    isGeneratingScene.value = false
  }
}

const manuscript = computed(() => storyStore.manuscript)
const storyTitle = computed(() => storyStore.currentStory?.title ?? '时光笺')

// 题记
const inscription = computed(() => manuscript.value?.inscription)

// 分割正文为段落（按换行或句号分割）
const paragraphs = computed(() => {
  if (!manuscript.value?.fullText) return []
  return manuscript.value.fullText
    .split(/\n/)
    .map(p => p.trim())
    .filter(p => p.length > 0)
})

// 获取后日谈
const epilogue = computed(() => manuscript.value?.epilogue)

// 朱批注释（按段落索引分组）
// M-10: 每个批注包含 text 和 type ('normal' | 'easter_egg')
const annotationsByPara = computed(() => {
  if (!manuscript.value?.annotations?.length) return {}
  const grouped: Record<number, Array<{ text: string; type: string }>> = {}
  manuscript.value.annotations.forEach(ann => {
    const idx = ann.chapterNo ?? 0
    if (!grouped[idx]) grouped[idx] = []
    grouped[idx].push({ text: ann.text, type: ann.type ?? 'normal' })
  })
  return grouped
})

// 获取选择标记
const choiceMarksByPara = computed(() => {
  if (!manuscript.value?.choiceMarks?.length) return {}
  const marks: Record<number, string> = {}
  manuscript.value.choiceMarks.forEach(m => {
    marks[m.chapterNo ?? 0] = m.text ?? '·'
  })
  return marks
})

// 字数统计
const wordCount = computed(() => manuscript.value?.wordCount ?? 0)

// 朱砂色（#8B3E3C 或 #8B5E3C）
const sealColor = computed(() => {
  // 根据历史偏离度决定印鉴颜色
  const dev = storyStore.historyDeviation
  return dev > 60 ? '#8B5E3C' : '#8B3E3C'
})

// 返回书架
function goBack() {
  router.push('/bookshelf')
}
</script>

<template>
  <div class="manuscript-view">
    <!-- 背景涟漪动画 -->
    <RippleEffect v-if="showRipple" />

    <!-- 顶部导航 -->
    <header class="manuscript-header">
      <button class="header-back" @click="goBack" data-testid="manuscript-back">
        ← 书架
      </button>
      <h1 class="manuscript-title" data-testid="manuscript-title">{{ storyTitle }}</h1>
      <div class="header-meta">
        <span class="word-count" data-testid="manuscript-word-count">{{ wordCount }} 字</span>
      </div>
    </header>

    <!-- 加载状态 -->
    <div v-if="isLoading" class="manuscript-loading">
      <p class="loading-text">说书人正在誊写...</p>
    </div>

    <!-- 错误状态 -->
    <div v-else-if="loadError" class="manuscript-error">
      <p>{{ loadError }}</p>
      <button class="retry-btn" @click="() => { loadError = null; isLoading = true; storyStore.fetchManuscript(storyId).finally(() => { isLoading = false }) }">
        重试
      </button>
    </div>

    <!-- C-14: AI 生成场景图（横幅装饰） -->
    <div v-if="sceneImageUrl || isGeneratingScene" class="scene-image-banner">
      <img
        v-if="sceneImageUrl"
        :src="sceneImageUrl"
        class="scene-image"
        alt="故事场景"
        @error="sceneImageUrl = ''"
      />
      <div v-else-if="isGeneratingScene" class="scene-image-placeholder">
        <span>墨痕浮现中...</span>
      </div>
    </div>

    <!-- 手稿正文 -->
    <div v-else-if="manuscript" class="manuscript-scroll" data-testid="manuscript-scroll">
      <!-- 天杆 UI-07 -->
      <div class="scroll-header">
        <div class="heavenly-rod">
          <div class="rod-end left"></div>
          <div class="hanging-string left"></div>
          <div class="rod-body"></div>
          <div class="rod-end right"></div>
          <div class="hanging-string right"></div>
        </div>
      </div>

      <!-- 卷面 -->
      <div class="scroll-content">
        <div class="manuscript-content">
          <!-- 题记 -->
          <div v-if="inscription" class="manuscript-inscription" data-testid="manuscript-inscription">
            <span class="inscription-text">{{ inscription }}</span>
          </div>

          <!-- 正文区域 -->
          <div class="manuscript-body" data-testid="manuscript-body">
            <div
              v-for="(para, idx) in paragraphs"
              :key="idx"
              class="manuscript-paragraph"
              :data-testid="`manuscript-para-${idx}`"
            >
              <!-- 选择标记 -->
              <span
                v-if="choiceMarksByPara[idx]"
                class="choice-mark"
                :title="`第${idx + 1}章选择：${choiceMarksByPara[idx]}`"
              >·</span>

              <!-- 正文 -->
              <span class="para-text">{{ para }}</span>

              <!-- 朱批 -->
              <div
                v-if="annotationsByPara[idx]?.length"
                class="zhub-annotation"
                data-testid="zhub-annotation"
              >
                <div class="zhub-marks">〰️</div>
                <div class="zhub-content">
                  <span class="zhub-label">批</span>
                  <p
                    v-for="(ann, ai) in annotationsByPara[idx]"
                    :key="ai"
                    class="zhub-text"
                    :class="{ 'annotation-easter-egg': ann.type === 'easter_egg' }"
                  >{{ ann.text }}</p>
                </div>
              </div>
            </div>
          </div>

          <!-- 后日谈 -->
          <div v-if="epilogue" class="epilogue-section" data-testid="manuscript-epilogue">
            <div class="epilogue-divider">✦ ✦ ✦</div>
            <p class="epilogue-text">{{ epilogue }}</p>
          </div>

          <!-- 稗官评语 -->
          <div
            v-if="manuscript.baiguanComment"
            class="baiguan-comment"
            data-testid="baiguan-comment"
          >
            <p class="baiguan-text">{{ manuscript.baiguanComment }}</p>
          </div>

          <!-- 印鉴 -->
          <div class="manuscript-seal" :style="{ color: sealColor }" data-testid="manuscript-seal">
            <span class="seal-char">笺</span>
          </div>

          <!-- SH-01: 分享此笺按钮 -->
          <div v-if="manuscript" class="manuscript-share-action">
            <button class="share-story-btn" @click="openShareModal">
              分享此笺
            </button>
          </div>
        </div>
      </div>

      <!-- 地杆 UI-07 -->
      <div class="scroll-footer">
        <div class="earthly-rod">
          <div class="rod-end left"></div>
          <div class="rod-body"></div>
          <div class="rod-end right"></div>
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-else class="manuscript-empty">
      <p>暂无手稿内容</p>
    </div>

    <!-- 标题选择浮层 -->
    <TitleSelectModal
      v-if="showTitleModal && candidateTitles.length > 0"
      :story-id="storyId"
      :titles="candidateTitles"
      @selected="onTitleSelected"
      @close="showTitleModal = false"
    />

    <!-- SH-01: 分享创建浮层 -->
    <transition name="modal">
      <div v-if="showShareModal" class="modal-overlay" @click.self="showShareModal = false">
        <div class="share-create-modal">
          <div class="modal-header">
            <p class="modal-title">选择缺角卡</p>
            <p class="modal-subtitle">选择一张关键词卡作为缺角，与同类卡主合券后可解锁完整故事</p>
          </div>

          <!-- 关键词卡列表 -->
          <div class="keyword-card-list">
            <button
              v-for="card in selectableKeywordCards"
              :key="card.id"
              class="keyword-card-item"
              :class="{
                selected: selectedKeywordCardId === card.id,
                [`rarity-${card.rarity}`]: true
              }"
              @click="selectedKeywordCardId = card.id"
            >
              <span class="kc-name">{{ card.name }}</span>
              <span class="kc-category">{{ categoryNames[card.category] || '器物' }}</span>
              <span class="kc-rarity">{{ rarityNames[card.rarity] || '凡' }}</span>
            </button>
          </div>

          <p v-if="selectableKeywordCards.length === 0" class="no-cards-hint">
            暂无关键词卡，请先去抽卡
          </p>

          <p v-if="shareCreationError" class="share-error">{{ shareCreationError }}</p>

          <div class="modal-actions">
            <button
              class="modal-btn cancel"
              @click="showShareModal = false"
            >取消</button>
            <button
              class="modal-btn confirm"
              :disabled="!selectedKeywordCardId || isCreatingShare"
              @click="handleCreateShare"
            >
              {{ isCreatingShare ? '生成中...' : '生成分享码' }}
            </button>
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<style scoped>
.manuscript-view {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background-color: #f5f0e6;
  background-image:
    url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='400' height='400'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.65' numOctaves='3' stitchTiles='stitch'/%3E%3CfeColorMatrix type='saturate' values='0'/%3E%3C/filter%3E%3Crect width='400' height='400' filter='url(%23n)' opacity='0.04'/%3E%3C/svg%3E");
  color: #2c2c2a;
  position: relative;
}

/* ── 顶部导航 ── */
.manuscript-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1rem 1.5rem;
  border-bottom: 1px solid rgba(139, 94, 60, 0.2);
  background: rgba(245, 240, 230, 0.95);
  position: sticky;
  top: 0;
  z-index: 10;
}

.header-back {
  background: none;
  border: 1px solid rgba(139, 94, 60, 0.3);
  border-radius: 3px;
  padding: 0.3rem 0.6rem;
  font-family: inherit;
  font-size: 0.85rem;
  color: #8b5e3c;
  cursor: pointer;
  transition: all 0.2s;
}

.header-back:hover {
  background: rgba(139, 94, 60, 0.1);
}

.manuscript-title {
  font-family: 'Source Han Serif CN', 'Noto Serif SC', serif;
  font-size: 1.1rem;
  font-weight: 700;
  color: #2c2c2a;
  letter-spacing: 0.1em;
  margin: 0;
}

.header-meta {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}

.word-count {
  font-size: 0.75rem;
  color: #8b7355;
  letter-spacing: 0.05em;
}

/* ── 手稿卷轴 ── */
.manuscript-scroll {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 0;
  flex: 1;
  overflow: hidden;
}

.scroll-header,
.scroll-footer {
  position: relative;
  z-index: 1;
  width: 100%;
  display: flex;
  justify-content: center;
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

/* 地杆 UI-07 — 比天杆略粗 */
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
  /* 木纹效果 */
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

/* ── 卷面 - 宣纸质感 ── */
.scroll-content {
  background: linear-gradient(180deg, #f5efe0 0%, #faf8f2 50%, #f5efe0 100%);
  width: 85%;
  min-height: 500px;
  box-shadow:
    inset 0 0 20px rgba(139, 90, 43, 0.1),
    0 0 30px rgba(0, 0, 0, 0.2);
  overflow-y: auto;
}

/* ── 加载/错误/空状态 ── */
.manuscript-loading,
.manuscript-error,
.manuscript-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1rem;
  padding: 2rem;
}

.loading-text {
  font-family: 'Noto Serif SC', serif;
  font-size: 1rem;
  color: #8b7355;
  letter-spacing: 0.15em;
  animation: text-pulse 1.5s var(--ease-loop) infinite;
}

@keyframes text-pulse {
  0%, 100% { opacity: 0.5; }
  50% { opacity: 1; }
}

.retry-btn {
  padding: 0.5rem 1.2rem;
  border: 1px solid #8b5e3c;
  border-radius: 3px;
  background: transparent;
  font-family: inherit;
  font-size: 0.9rem;
  color: #8b5e3c;
  cursor: pointer;
  transition: all 0.2s;
}

.retry-btn:hover {
  background: rgba(139, 94, 60, 0.1);
}

/* ── 手稿内容 ── */
.manuscript-content {
  max-width: 680px;
  margin: 0 auto;
  padding: 2rem 3rem 4rem;
}

/* ── 题记 ── */
.manuscript-inscription {
  writing-mode: vertical-rl;
  text-orientation: mixed;
  direction: ltr;
  padding: 1.5rem 1rem;
  text-align: center;
  border-bottom: 1px solid rgba(139, 94, 60, 0.15);
  margin-bottom: 0.5rem;
}

.inscription-text {
  font-family: 'Noto Serif SC', 'Songti SC', serif;
  font-size: 1.15rem;
  color: #4A6B6B;
  font-style: italic;
  letter-spacing: 0.2em;
  line-height: 1.8;
}

/* ── 正文主体 ── */
.manuscript-body {
  writing-mode: vertical-rl;
  text-orientation: mixed;
  direction: ltr;
  min-height: 400px;
  max-height: 70vh;
  overflow-y: auto;
  padding: 0 1rem;
  position: relative;
}

/* ── 段落 ── */
.manuscript-paragraph {
  position: relative;
  display: inline-flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.5rem;
  margin-right: 2.5rem;
  padding: 0.5rem 0;
  min-height: 200px;
}

.choice-mark {
  position: absolute;
  top: 0;
  right: -0.5rem;
  color: #8b3e3c;
  font-size: 1rem;
  line-height: 1;
}

.para-text {
  font-family: 'Noto Serif SC', 'Source Han Serif CN', serif;
  font-size: 1.05rem;
  line-height: 2.2;
  color: #2c2c2a;
  letter-spacing: 0.05em;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.08);
}

/* ── 朱批 ── */
.zhub-annotation {
  display: flex;
  align-items: flex-start;
  gap: 0.3rem;
  margin-top: 0.5rem;
  padding: 0.3rem 0.5rem;
  border-left: 2px solid #8b3e3c;
  background: rgba(139, 62, 60, 0.05);
  max-width: 140px;
}

.zhub-marks {
  font-size: 0.7rem;
  color: #8b3e3c;
  flex-shrink: 0;
  opacity: 0.7;
  line-height: 2;
}

.zhub-content {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.zhub-label {
  display: inline-block;
  font-size: 0.6rem;
  color: #8b3e3c;
  border: 1px solid #8b3e3c;
  border-radius: 2px;
  padding: 0 0.2rem;
  line-height: 1.4;
  margin-bottom: 0.2rem;
}

.zhub-text {
  font-family: 'Noto Serif SC', 'FangZhengQingKeBenYueSong', serif;
  font-size: 0.8rem;
  line-height: 1.6;
  color: #8b3e3c;
  margin: 0;
  letter-spacing: 0.05em;
}

/* M-10 批注彩蛋：黛青色区分 */
.annotation-easter-egg {
  color: #4A6B6B !important;
  font-style: italic;
}

/* ── 后日谈 ── */
.epilogue-section {
  margin-top: 2rem;
  padding-top: 1.5rem;
  text-align: center;
}

.epilogue-divider {
  font-size: 0.8rem;
  color: rgba(139, 94, 60, 0.4);
  letter-spacing: 0.5em;
  margin-bottom: 1rem;
}

.epilogue-text {
  font-family: 'Noto Serif SC', serif;
  font-size: 0.95rem;
  line-height: 2;
  color: #6b5e50;
  letter-spacing: 0.05em;
  font-style: italic;
  margin: 0;
}

/* ── 稗官评语 ── */
.baiguan-comment {
  margin-top: 1.5rem;
  padding: 0.75rem 1rem;
  border-top: 1px dashed rgba(139, 94, 60, 0.3);
  border-bottom: 1px dashed rgba(139, 94, 60, 0.3);
}

.baiguan-text {
  font-family: 'Noto Serif SC', serif;
  font-size: 0.85rem;
  line-height: 1.8;
  color: #8b7355;
  margin: 0;
  text-align: right;
  letter-spacing: 0.05em;
}

/* ── 印鉴 ── */
.manuscript-seal {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.1rem;
  opacity: 0.7;
}

.seal-char {
  font-family: 'Noto Serif SC', serif;
  font-size: 1.4rem;
  font-weight: 700;
  line-height: 1;
  border: 2px solid currentColor;
  border-radius: 3px;
  padding: 0.2rem 0.3rem;
}

/* ── C-14: AI 场景图横幅 ── */
.scene-image-banner {
  width: 100%;
  max-height: 180px;
  overflow: hidden;
  position: relative;
  background: #e8e0d5;
}

.scene-image {
  width: 100%;
  height: 180px;
  object-fit: cover;
  object-position: center;
  display: block;
}

.scene-image-placeholder {
  width: 100%;
  height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #e8e0d5 0%, #d4c8b8 100%);
  color: #8b7355;
  font-size: 0.85rem;
  letter-spacing: 0.1em;
}

/* ── SH-01: 分享此笺按钮 ── */
.manuscript-share-action {
  display: flex;
  justify-content: center;
  margin-top: 2rem;
  padding-bottom: 1rem;
}

.share-story-btn {
  padding: 0.5rem 1.8rem;
  border: 1px solid #c9a84c;
  border-radius: 2px;
  background: rgba(201, 168, 76, 0.1);
  font-family: inherit;
  font-size: 0.85rem;
  color: #8b6914;
  cursor: pointer;
  letter-spacing: 0.1em;
  transition: all 0.2s var(--ease-smooth);
}

.share-story-btn:hover {
  background: rgba(201, 168, 76, 0.25);
}

/* ── SH-01: 分享创建浮层 ── */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(44, 31, 20, 0.65);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
  padding: 1rem;
  backdrop-filter: blur(4px);
}

.share-create-modal {
  background: #f5efe0;
  border-radius: 6px;
  padding: 1.5rem;
  width: 100%;
  max-width: 320px;
  display: flex;
  flex-direction: column;
  gap: 1rem;
  box-shadow: 0 8px 40px rgba(44, 31, 20, 0.5);
}

.modal-header {
  text-align: center;
}

.modal-title {
  font-size: 1.1rem;
  color: #2c1f14;
  letter-spacing: 0.15em;
  margin: 0 0 0.4rem;
}

.modal-subtitle {
  font-size: 0.75rem;
  color: #8b7355;
  margin: 0;
  line-height: 1.5;
}

.keyword-card-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  max-height: 240px;
  overflow-y: auto;
}

.keyword-card-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.6rem 0.75rem;
  border: 1px solid rgba(139, 115, 85, 0.3);
  border-radius: 2px;
  background: rgba(255, 255, 255, 0.4);
  cursor: pointer;
  transition: all 0.2s;
  text-align: left;
  font-family: inherit;
}

.keyword-card-item.selected {
  border-color: #c9a84c;
  background: rgba(201, 168, 76, 0.15);
}

.kc-name {
  flex: 1;
  font-size: 0.9rem;
  color: #2c1f14;
}

.kc-category {
  font-size: 0.7rem;
  color: #8b7355;
}

.kc-rarity {
  font-size: 0.7rem;
  padding: 0.1rem 0.3rem;
  border-radius: 2px;
  background: rgba(201, 168, 76, 0.2);
  color: #8b6914;
}

.keyword-card-item.rarity-3 .kc-rarity,
.keyword-card-item.rarity-4 .kc-rarity {
  background: rgba(139, 62, 60, 0.15);
  color: #8b3e3c;
}

.no-cards-hint {
  font-size: 0.8rem;
  color: #8b7355;
  text-align: center;
  margin: 0;
}

.share-error {
  font-size: 0.78rem;
  color: #a05050;
  text-align: center;
  margin: 0;
}

.modal-actions {
  display: flex;
  gap: 0.5rem;
}

.modal-btn {
  flex: 1;
  padding: 0.6rem;
  border: 1px solid #8b7355;
  border-radius: 2px;
  background: transparent;
  font-family: inherit;
  font-size: 0.85rem;
  color: #8b7355;
  cursor: pointer;
  transition: all 0.2s;
}

.modal-btn.cancel {
  background: transparent;
}

.modal-btn.confirm {
  background: #2c1f14;
  color: #f5efe0;
  border-color: #2c1f14;
}

.modal-btn.confirm:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.modal-btn:not(:disabled):hover {
  opacity: 0.85;
}

/* 过渡动画 */
.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.3s;
}
.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}
</style>
