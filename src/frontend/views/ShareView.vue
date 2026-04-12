<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchShareInfo, jointShare, fetchSpecialCards, aiPainter, type ShareInfoResponse, type SpecialCard } from '../services/api'
import { playJadeClick } from '@/composables/useSound'
import { initWeChatJSSDK, isWeChatBrowser } from '@/services/wechat'
import { useWeChatShare } from '@/composables/useWeChatShare'

const route = useRoute()
const router = useRouter()
const shareCode = route.params.code as string

// U-03: 检查是否为游客
function isGuestUser(): boolean {
  try {
    const saved = localStorage.getItem('arbitrary_gate_user')
    if (saved) {
      const user = JSON.parse(saved)
      return user.isGuest === 1
    }
  } catch { /* ignore */ }
  return !localStorage.getItem('token')
}

// 分享信息
const shareInfo = ref<ShareInfoResponse | null>(null)
const isLoading = ref(true)
const loadError = ref<string | null>(null)

// 缺角卡Canvas引用
const leftCanvasRef = ref<HTMLCanvasElement | null>(null)
const rightCanvasRef = ref<HTMLCanvasElement | null>(null)

// 合券面板
const verifyDialogOpen = ref(false)
const userCards = ref<Array<{ cardId: number; name: string; category: number }>>([])
const selectedCardId = ref<number | null>(null)
const isVerifying = ref(false)
const verifyResult = ref<'success' | 'fail' | null>(null)
const jointResultData = ref<{ message: string; storyTitle: string; specialCardName: string } | null>(null)

// 匹配动画状态
const showMergeAnimation = ref(false)
const mergeProgress = ref(0)

// 纪念卡弹层
const showSpecialCardModal = ref(false)
const specialCardData = ref<SpecialCard | null>(null)
const commemorativeCardId = ref<number | null>(null)
const commemorativeCardNo = ref<string | null>(null)

// C-14: AI 画师分享卡图片
const shareCardImageUrl = ref<string>('')
const isGeneratingShareCard = ref(false)

// 分类名称映射
const categoryNames: Record<number, string> = {
  1: '器物', 2: '职人', 3: '风物', 4: '情绪', 5: '称谓'
}

// SH-05: 初始化微信分享
const { setupShare } = useWeChatShare()

async function initWeChatShare() {
  if (!isWeChatBrowser()) return
  try {
    await initWeChatJSSDK()
    if (shareInfo.value) {
      const shareData = {
        title: `《${shareInfo.value.storyTitle}》- 时光笺`,
        desc: `我在时光笺经历了一段 ${shareInfo.value.cardName} 的故事，期待与你合券共续前缘！`,
        link: `${window.location.origin}/share/${shareInfo.value.shareCode}`,
        imgUrl: `${window.location.origin}/assets/share-card.jpg`,
      }
      setupShare(shareData)
    }
  } catch (err) {
    console.error('[ShareView] 微信 JSSDK 初始化失败', err)
  }
}

// 获取分享信息
async function loadShareInfo() {
  if (!shareCode) {
    loadError.value = '无效的分享码'
    isLoading.value = false
    return
  }
  try {
    shareInfo.value = await fetchShareInfo(shareCode)
    await nextTick()
    if (shareInfo.value?.status === 'jointed') {
      // 已合券，提示
      loadError.value = null
    } else {
      // 绘制缺角卡
      await renderMissingCornerCards()
      // 加载用户可选卡
      await loadUserCards()
    }
  } catch (err: any) {
    console.error('加载分享内容失败:', err)
    if (err?.response?.data?.code === 400) {
      loadError.value = err.response.data.message || '分享码已失效'
    } else {
      loadError.value = '加载失败，请检查分享码'
    }
  } finally {
    isLoading.value = false
  }

  // SH-05: 分享信息加载完毕后，初始化微信分享
  await initWeChatShare()
}

// 加载用户可选的关键词卡（用于合券）
async function loadUserCards() {
  try {
    const cards = await fetchSpecialCards()
    // 筛选同类卡或高稀有度卡
    if (shareInfo.value) {
      userCards.value = cards
        .filter((_: SpecialCard) => {
          // 这里应该用用户拥有的关键词卡，但目前接口是 special-cards
          // 暂时显示所有纪念卡，实际应调用户关键词卡接口
          return true
        })
        .slice(0, 5)
        .map(c => ({ cardId: c.id, name: c.name, category: c.rarity }))
    }
  } catch {
    // 无卡时忽略
  }
}

// 绘制缺角分享卡（两半卡）
function drawMissingCornerCard(
  canvas: HTMLCanvasElement,
  data: ShareInfoResponse,
  side: 'left' | 'right'
) {
  const ctx = canvas.getContext('2d')
  if (!ctx) return

  const W = canvas.width
  const H = canvas.height
  const cornerSize = 56 // 缺角尺寸

  // 高清渲染
  const dpr = window.devicePixelRatio || 1
  canvas.width = W * dpr
  canvas.height = H * dpr
  ctx.scale(dpr, dpr)
  const w = W
  const h = H

  // 古纸背景
  const gradient = ctx.createLinearGradient(0, 0, w, h)
  gradient.addColorStop(0, '#3d2b1a')
  gradient.addColorStop(0.5, '#2c1f14')
  gradient.addColorStop(1, '#1f150d')
  ctx.fillStyle = gradient
  ctx.fillRect(0, 0, w, h)

  // 绘制缺角
  ctx.save()
  if (side === 'right') {
    // 右侧：右上角缺角
    ctx.beginPath()
    ctx.moveTo(0, 0)
    ctx.lineTo(w - cornerSize, 0)
    ctx.lineTo(w, cornerSize)
    ctx.lineTo(w, h)
    ctx.lineTo(0, h)
    ctx.closePath()
    ctx.clip()
  } else {
    // 左侧：左下角缺角
    ctx.beginPath()
    ctx.moveTo(0, 0)
    ctx.lineTo(w, 0)
    ctx.lineTo(w, h)
    ctx.lineTo(0, h - cornerSize)
    ctx.lineTo(cornerSize, h)
    ctx.closePath()
    ctx.clip()
  }
  ctx.fillStyle = '#2c1f14'
  ctx.fillRect(0, 0, w, h)
  ctx.restore()

  // 内边框装饰
  const pad = 14
  ctx.strokeStyle = '#8b7355'
  ctx.lineWidth = 1.5
  ctx.beginPath()
  if (side === 'right') {
    ctx.moveTo(pad, pad)
    ctx.lineTo(w - cornerSize - pad, pad)
    ctx.lineTo(w - pad, cornerSize + pad)
    ctx.lineTo(w - pad, h - pad)
    ctx.lineTo(pad, h - pad)
    ctx.closePath()
  } else {
    ctx.moveTo(pad, pad)
    ctx.lineTo(w - pad, pad)
    ctx.lineTo(w - pad, h - pad)
    ctx.lineTo(pad, h - cornerSize - pad)
    ctx.lineTo(cornerSize + pad, h - pad)
    ctx.closePath()
  }
  ctx.stroke()

  // 类别标签
  const categoryName = categoryNames[data.cardCategory] || '器物'
  ctx.fillStyle = 'rgba(201, 168, 76, 0.2)'
  ctx.font = 'bold 10px serif'
  ctx.textAlign = 'center'
  ctx.fillText(categoryName, w / 2, 30)

  // 分割线
  ctx.strokeStyle = 'rgba(139, 115, 85, 0.3)'
  ctx.lineWidth = 1
  ctx.beginPath()
  ctx.moveTo(20, 42)
  ctx.lineTo(w - 20, 42)
  ctx.stroke()

  // 缺角卡名称
  ctx.fillStyle = '#c9a84c'
  ctx.font = 'bold 20px serif'
  ctx.textAlign = 'center'
  ctx.fillText(data.cardName, w / 2, 72)

  // 小装饰线
  ctx.strokeStyle = 'rgba(139, 115, 85, 0.25)'
  ctx.lineWidth = 1
  ctx.beginPath()
  ctx.moveTo(w / 2 - 30, 82)
  ctx.lineTo(w / 2 + 30, 82)
  ctx.stroke()

  // 故事标题
  ctx.fillStyle = '#e8dcc8'
  ctx.font = '14px serif'
  ctx.textAlign = 'center'
  const titleLines = wrapText(ctx, data.storyTitle, w - 40)
  let y = 110
  for (const line of titleLines.slice(0, 2)) {
    ctx.fillText(line, w / 2, y)
    y += 22
  }

  // 缺角位置图标
  ctx.fillStyle = 'rgba(201, 168, 76, 0.12)'
  ctx.beginPath()
  if (side === 'right') {
    ctx.moveTo(w - cornerSize, 0)
    ctx.lineTo(w - cornerSize, cornerSize)
    ctx.lineTo(w, cornerSize)
    ctx.closePath()
  } else {
    ctx.moveTo(0, h - cornerSize)
    ctx.lineTo(cornerSize, h - cornerSize)
    ctx.lineTo(cornerSize, h)
    ctx.closePath()
  }
  ctx.fill()

  ctx.fillStyle = 'rgba(201, 168, 76, 0.5)'
  ctx.font = 'bold 22px serif'
  ctx.textAlign = 'center'
  ctx.fillText('?残', w / 2, h / 2 + 8)

  // 分享码
  ctx.fillStyle = 'rgba(201, 168, 76, 0.4)'
  ctx.font = '10px monospace'
  ctx.textAlign = 'center'
  ctx.fillText(`# ${data.shareCode}`, w / 2, h - 18)

  // 故事印记
  ctx.fillStyle = 'rgba(201, 168, 76, 0.1)'
  ctx.beginPath()
  if (side === 'right') {
    ctx.arc(w - 30, 30, 22, 0, Math.PI * 2)
  } else {
    ctx.arc(30, h - 30, 22, 0, Math.PI * 2)
  }
  ctx.fill()
  ctx.fillStyle = 'rgba(201, 168, 76, 0.4)'
  ctx.font = 'bold 12px serif'
  ctx.textAlign = 'center'
  if (side === 'right') {
    ctx.fillText('缺', w - 30, 35)
  } else {
    ctx.fillText('残', 30, h - 25)
  }
}

function wrapText(ctx: CanvasRenderingContext2D, text: string, maxWidth: number): string[] {
  const lines: string[] = []
  let current = ''
  for (const char of text) {
    const test = current + char
    if (ctx.measureText(test).width > maxWidth) {
      lines.push(current)
      current = char
    } else {
      current = test
    }
  }
  if (current) lines.push(current)
  return lines
}

async function renderMissingCornerCards() {
  if (!shareInfo.value) return
  await nextTick()
  const left = leftCanvasRef.value
  const right = rightCanvasRef.value
  const canvasW = 148
  const canvasH = 200
  if (left) {
    left.style.width = canvasW + 'px'
    left.style.height = canvasH + 'px'
    drawMissingCornerCard(left, shareInfo.value, 'left')
  }
  if (right) {
    right.style.width = canvasW + 'px'
    right.style.height = canvasH + 'px'
    drawMissingCornerCard(right, shareInfo.value, 'right')
  }
}

// 处理合券
async function handleJoint() {
  // U-03: 游客不能合券
  if (isGuestUser()) {
    jointResultData.value = {
      message: '游客无法合券，请先绑定正式账号',
      storyTitle: '',
      specialCardName: ''
    }
    verifyResult.value = 'fail'
    return
  }
  if (!selectedCardId.value || !shareCode) return
  isVerifying.value = true
  verifyResult.value = null
  jointResultData.value = null
  try {
    const result = await jointShare(shareCode, { cardId: selectedCardId.value })
    verifyResult.value = 'success'
    jointResultData.value = {
      message: result.message,
      storyTitle: result.storyTitle,
      specialCardName: result.specialCardName
    }
    // SH-04: 记录纪念卡ID
    commemorativeCardId.value = result.commemorativeCardId ?? null
    commemorativeCardNo.value = result.commemorativeCardNo ?? null
    playJadeClick()
    // 播放合券动画
    await playMergeAnimation()
    // 显示纪念卡弹层
    specialCardData.value = {
      id: result.specialCardId,
      cardNo: 'SC-' + shareCode,
      name: result.specialCardName,
      description: '记录一段跨越时空的相遇',
      rarity: 3,
      sourceStoryId: result.storyId,
      sourceShareCode: shareCode,
      acquiredAt: new Date().toISOString()
    }
    // C-14: 生成 AI 分享卡图片
    await generateShareCardImage(result.storyTitle, result.specialCardName)
    setTimeout(() => {
      showSpecialCardModal.value = true
    }, 800)
  } catch (err: any) {
    verifyResult.value = 'fail'
    console.error('合券失败:', err)
  } finally {
    isVerifying.value = false
  }
}

// 合券动画：两半卡合拢
async function playMergeAnimation() {
  showMergeAnimation.value = true
  mergeProgress.value = 0

  return new Promise<void>(resolve => {
    let start: number | null = null
    const duration = 1200

    function step(timestamp: number) {
      if (!start) start = timestamp
      const elapsed = timestamp - start
      const progress = Math.min(elapsed / duration, 1)
      // easeOutCubic
      mergeProgress.value = 1 - Math.pow(1 - progress, 3)

      if (progress < 1) {
        requestAnimationFrame(step)
      } else {
        showMergeAnimation.value = false
        resolve()
      }
    }

    requestAnimationFrame(step)
  })
}

// C-14: AI 画师生成分享卡图片
async function generateShareCardImage(storyTitle: string, specialCardName: string) {
  isGeneratingShareCard.value = true
  try {
    // 从 shareInfo 获取判词（如果有）
    const judgeQuote = shareInfo.value?.cardName || specialCardName
    const result = await aiPainter.generateShareCard({
      storyTitle,
      judgeQuote,
      firstParagraph: '一段跨越时空的相遇，始于缺角卡的重逢。',
      coreKeyword: specialCardName,
    })
    shareCardImageUrl.value = result
  } catch (err) {
    console.warn('[ShareView] 分享卡图片生成失败:', err)
  } finally {
    isGeneratingShareCard.value = false
  }
}

// 跳转到故事页
function goToStory() {
  if (shareInfo.value?.storyId) {
    router.push(`/story/${shareInfo.value.storyId}`)
  }
}

// SH-04: 跳转到纪念卡页面
function goToCommemorativeCard() {
  showSpecialCardModal.value = false
  if (commemorativeCardId.value) {
    router.push(`/commemorative-card/${commemorativeCardId.value}`)
  }
}

onMounted(loadShareInfo)
</script>

<template>
  <div class="share-view">
    <!-- 加载状态 -->
    <div v-if="isLoading" class="loading-state">
      <div class="loading-card">
        <div class="loading-corner loading-corner-tl"></div>
        <div class="loading-corner loading-corner-tr"></div>
        <div class="loading-corner loading-corner-bl"></div>
        <div class="loading-corner loading-corner-br"></div>
        <p class="loading-text">墨中寻迹...</p>
      </div>
    </div>

    <!-- 错误状态 -->
    <div v-else-if="loadError" class="error-state">
      <div class="error-card">
        <p class="error-title">此笺已逝</p>
        <p class="error-msg">{{ loadError }}</p>
        <button class="error-btn" @click="router.push('/')">返回主页</button>
      </div>
    </div>

    <!-- 已合券状态 -->
    <div v-else-if="shareInfo?.status === 'jointed'" class="jointed-state">
      <div class="jointed-card">
        <p class="jointed-title">此笺已合</p>
        <p class="jointed-sub">故事已被先前的旅人收入匣中</p>
        <button class="jointed-btn" @click="goToStory">查阅故事</button>
      </div>
    </div>

    <!-- 正常分享内容 -->
    <template v-else-if="shareInfo">
      <header class="share-header">
        <h2>时光笺 · 缺角卡</h2>
        <p class="share-hint">与同类卡主合券，共获限定纪念卡</p>
      </header>

      <!-- 缺角卡展示 -->
      <div class="card-showcase">
        <!-- 左半卡 -->
        <div class="half-card left-card" :class="{ merged: showMergeAnimation }">
          <canvas ref="leftCanvasRef" class="half-canvas"></canvas>
        </div>

        <!-- 合拢动画遮罩 -->
        <transition name="fade">
          <div v-if="showMergeAnimation" class="merge-flash"></div>
        </transition>

        <!-- 右半卡 -->
        <div class="half-card right-card" :class="{ merged: showMergeAnimation }">
          <canvas ref="rightCanvasRef" class="half-canvas"></canvas>
        </div>
      </div>

      <!-- 缺角信息 -->
      <div class="share-info">
        <div class="info-row">
          <span class="info-label">缺角</span>
          <span class="info-value">{{ shareInfo.cardName }}（{{ categoryNames[shareInfo.cardCategory] || '器物' }}）</span>
        </div>
        <div class="info-row">
          <span class="info-label">故事</span>
          <span class="info-value">{{ shareInfo.storyTitle }}</span>
        </div>
        <div class="info-row">
          <span class="info-label">码</span>
          <span class="info-value code">{{ shareInfo.shareCode }}</span>
        </div>
      </div>

      <!-- 合券入口 -->
      <div class="share-actions">
        <button
          class="joint-btn"
          @click="verifyDialogOpen = !verifyDialogOpen"
        >
          {{ verifyDialogOpen ? '收起' : '我要合券' }}
        </button>

        <transition name="slide">
          <div v-if="verifyDialogOpen" class="joint-panel">
            <p class="panel-hint">选择你的关键词卡，与缺角匹配后即可合券</p>
            <div class="card-select-list">
              <button
                v-for="card in userCards"
                :key="card.cardId"
                class="card-select-item"
                :class="{ selected: selectedCardId === card.cardId }"
                @click="selectedCardId = card.cardId"
              >
                {{ card.name }}
              </button>
            </div>
            <p v-if="userCards.length === 0" class="no-cards-hint">
              暂无匹配的关键词卡
            </p>

            <button
              class="confirm-joint-btn"
              @click="handleJoint"
              :disabled="!selectedCardId || isVerifying"
            >
              {{ isVerifying ? '合券中...' : '确认合券' }}
            </button>

            <p v-if="verifyResult === 'success'" class="result-msg success">
              {{ jointResultData?.message }} 故事《{{ jointResultData?.storyTitle }}》已收录
            </p>
            <p v-else-if="verifyResult === 'fail'" class="result-msg fail">
              {{ jointResultData?.message || '合券失败，卡片不匹配' }}
            </p>
          </div>
        </transition>
      </div>

      <p class="copyright">由 时光笺 · 任意门 生成</p>
    </template>

    <!-- 合券纪念卡弹层 -->
    <transition name="modal">
      <div v-if="showSpecialCardModal" class="modal-overlay" @click.self="showSpecialCardModal = false">
        <div class="special-card-modal">
          <div class="modal-header">
            <p class="modal-title">合券成功！</p>
          </div>
          <div class="modal-card">
            <!-- C-14: AI 生成分享卡图片 -->
            <div v-if="shareCardImageUrl || isGeneratingShareCard" class="share-card-image-wrapper">
              <img
                v-if="shareCardImageUrl"
                :src="shareCardImageUrl"
                class="share-card-image"
                alt="分享卡"
                @error="shareCardImageUrl = ''"
              />
              <div v-else-if="isGeneratingShareCard" class="share-card-generating">
                <span>墨痕浮现中...</span>
              </div>
            </div>
            <div class="special-card-inner">
              <p class="sc-rarity">奇品</p>
              <p class="sc-name">{{ specialCardData?.name }}</p>
              <p class="sc-desc">{{ specialCardData?.description }}</p>
              <p class="sc-source">来源：{{ specialCardData?.sourceShareCode }}</p>
            </div>
          </div>
          <div class="modal-actions">
            <button v-if="commemorativeCardId" class="modal-btn gold" @click="goToCommemorativeCard">查看纪念卡</button>
            <button class="modal-btn primary" @click="goToStory">阅读故事</button>
            <button class="modal-btn" @click="showSpecialCardModal = false">收起</button>
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<style scoped>
.share-view {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-start;
  background: radial-gradient(ellipse at center, #f5efe0 0%, #ede0c8 100%);
  padding: 2.5rem 1rem 3rem;
}

.share-header {
  text-align: center;
  margin-bottom: 1.5rem;
}

.share-header h2 {
  font-size: 1.2rem;
  color: #2c1f14;
  letter-spacing: 0.2em;
  margin-bottom: 0.4rem;
}

.share-hint {
  font-size: 0.75rem;
  color: #8b7355;
  letter-spacing: 0.05em;
}

/* 缺角卡展示 */
.card-showcase {
  position: relative;
  display: flex;
  gap: 0.5rem;
  margin-bottom: 1.5rem;
  perspective: 1000px;
}

.half-card {
  width: 148px;
  height: 200px;
  border-radius: 4px;
  overflow: hidden;
  transition: transform 0.6s cubic-bezier(0.34, 1.56, 0.64, 1);
  transform-origin: center;
}

.half-card.left-card {
  transform-origin: right center;
}

.half-card.right-card {
  transform-origin: left center;
}

.half-card.merged.left-card {
  transform: translateX(calc(100% + 8px)) scaleX(0.01);
  opacity: 0;
}

.half-card.merged.right-card {
  transform: translateX(calc(-100% - 8px)) scaleX(0.01);
  opacity: 0;
}

.half-canvas {
  width: 100%;
  height: 100%;
  display: block;
  border-radius: 4px;
}

.merge-flash {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 60px;
  height: 60px;
  background: radial-gradient(circle, rgba(201, 168, 76, 0.8), transparent);
  border-radius: 50%;
  animation: flash-pulse 0.8s ease-out forwards;
  pointer-events: none;
  z-index: 10;
}

@keyframes flash-pulse {
  0% { transform: translate(-50%, -50%) scale(0.5); opacity: 1; }
  100% { transform: translate(-50%, -50%) scale(4); opacity: 0; }
}

/* 分享信息 */
.share-info {
  width: 100%;
  max-width: 300px;
  border: 1px solid rgba(139, 115, 85, 0.25);
  border-radius: 2px;
  padding: 0.75rem;
  background: rgba(255, 255, 255, 0.3);
  margin-bottom: 1.2rem;
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.25rem 0;
}

.info-row + .info-row {
  border-top: 1px solid rgba(139, 115, 85, 0.1);
}

.info-label {
  font-size: 0.7rem;
  color: #8b7355;
  letter-spacing: 0.1em;
}

.info-value {
  font-size: 0.8rem;
  color: #2c1f14;
}

.info-value.code {
  font-family: monospace;
  letter-spacing: 0.1em;
  color: #c9a84c;
}

/* 合券面板 */
.share-actions {
  width: 100%;
  max-width: 300px;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.joint-btn {
  padding: 0.6rem;
  border: 1.5px solid #2c1f14;
  border-radius: 2px;
  background: #2c1f14;
  font-family: inherit;
  font-size: 0.85rem;
  color: #f5efe0;
  cursor: pointer;
  transition: opacity 0.2s;
  letter-spacing: 0.1em;
}

.joint-btn:hover { opacity: 0.85; }

.joint-panel {
  border: 1px solid rgba(139, 115, 85, 0.25);
  border-radius: 2px;
  padding: 0.75rem;
  background: rgba(255, 255, 255, 0.4);
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}

.panel-hint {
  font-size: 0.75rem;
  color: #8b7355;
  margin: 0;
}

.card-select-list {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}

.card-select-item {
  padding: 0.3rem 0.6rem;
  border: 1px solid #8b7355;
  border-radius: 2px;
  background: transparent;
  font-family: inherit;
  font-size: 0.75rem;
  color: #8b7355;
  cursor: pointer;
  transition: all 0.2s;
}

.card-select-item.selected {
  background: #2c1f14;
  color: #f5efe0;
  border-color: #2c1f14;
}

.no-cards-hint {
  font-size: 0.75rem;
  color: #8b7355;
  margin: 0;
  text-align: center;
}

.confirm-joint-btn {
  padding: 0.5rem;
  border: 1px solid #c9a84c;
  border-radius: 2px;
  background: rgba(201, 168, 76, 0.15);
  font-family: inherit;
  font-size: 0.8rem;
  color: #8b6914;
  cursor: pointer;
  transition: all 0.2s;
  letter-spacing: 0.05em;
}

.confirm-joint-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.confirm-joint-btn:not(:disabled):hover {
  background: rgba(201, 168, 76, 0.3);
}

.result-msg {
  font-size: 0.78rem;
  text-align: center;
  margin: 0;
}

.result-msg.success { color: #6b8e6b; }
.result-msg.fail { color: #a05050; }

.copyright {
  margin-top: 2rem;
  font-size: 0.7rem;
  color: #8b7355;
}

/* 加载状态 */
.loading-state {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
}

.loading-card {
  width: 160px;
  height: 210px;
  background: #2c1f14;
  border-radius: 4px;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 20px rgba(44, 31, 20, 0.3);
}

.loading-corner {
  position: absolute;
  width: 20px;
  height: 20px;
  background: rgba(201, 168, 76, 0.3);
  animation: corner-pulse 1.5s ease-in-out infinite;
}

.loading-corner-tl { top: 8px; left: 8px; }
.loading-corner-tr { top: 8px; right: 8px; animation-delay: 0.3s; }
.loading-corner-bl { bottom: 8px; left: 8px; animation-delay: 0.6s; }
.loading-corner-br { bottom: 8px; right: 8px; animation-delay: 0.9s; }

@keyframes corner-pulse {
  0%, 100% { opacity: 0.3; }
  50% { opacity: 0.8; }
}

.loading-text {
  font-size: 0.85rem;
  color: #8b7355;
  letter-spacing: 0.1em;
  z-index: 1;
}

/* 错误状态 */
.error-state {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
}

.error-card {
  text-align: center;
  padding: 2rem;
}

.error-title {
  font-size: 1.2rem;
  color: #2c1f14;
  letter-spacing: 0.2em;
  margin-bottom: 0.5rem;
}

.error-msg {
  font-size: 0.8rem;
  color: #8b7355;
  margin-bottom: 1.5rem;
}

.error-btn {
  padding: 0.5rem 1.5rem;
  border: 1px solid #8b7355;
  border-radius: 2px;
  background: transparent;
  font-family: inherit;
  font-size: 0.8rem;
  color: #8b7355;
  cursor: pointer;
}

/* 已合券状态 */
.jointed-state {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
}

.jointed-card {
  text-align: center;
  padding: 2rem;
}

.jointed-title {
  font-size: 1.1rem;
  color: #2c1f14;
  letter-spacing: 0.2em;
  margin-bottom: 0.4rem;
}

.jointed-sub {
  font-size: 0.78rem;
  color: #8b7355;
  margin-bottom: 1.5rem;
}

.jointed-btn {
  padding: 0.5rem 1.5rem;
  border: 1px solid #2c1f14;
  border-radius: 2px;
  background: #2c1f14;
  font-family: inherit;
  font-size: 0.8rem;
  color: #f5efe0;
  cursor: pointer;
}

/* 纪念卡弹层 */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(44, 31, 20, 0.7);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
  padding: 1rem;
  backdrop-filter: blur(4px);
}

.special-card-modal {
  background: #f5efe0;
  border-radius: 6px;
  padding: 1.5rem;
  width: 100%;
  max-width: 300px;
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
  margin: 0;
}

.modal-card {
  background: #2c1f14;
  border-radius: 4px;
  padding: 1.5rem;
}

.special-card-inner {
  text-align: center;
}

.sc-rarity {
  font-size: 0.7rem;
  color: #c9a84c;
  letter-spacing: 0.1em;
  margin: 0 0 0.5rem;
}

.sc-name {
  font-size: 1.2rem;
  color: #c9a84c;
  font-family: serif;
  margin: 0 0 0.5rem;
}

.sc-desc {
  font-size: 0.8rem;
  color: #e8dcc8;
  margin: 0 0 0.5rem;
  line-height: 1.5;
}

.sc-source {
  font-size: 0.7rem;
  color: rgba(139, 115, 85, 0.6);
  margin: 0;
}

.modal-actions {
  display: flex;
  gap: 0.5rem;
}

.modal-btn {
  flex: 1;
  padding: 0.5rem;
  border: 1px solid #8b7355;
  border-radius: 2px;
  background: transparent;
  font-family: inherit;
  font-size: 0.8rem;
  color: #8b7355;
  cursor: pointer;
  transition: all 0.2s;
}

.modal-btn.primary {
  background: #2c1f14;
  color: #f5efe0;
  border-color: #2c1f14;
}

.modal-btn.gold {
  background: rgba(201, 168, 76, 0.15);
  color: #c9a84c;
  border-color: rgba(201, 168, 76, 0.5);
}

/* 过渡动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.slide-enter-active,
.slide-leave-active {
  transition: all 0.3s ease;
}
.slide-enter-from,
.slide-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.3s;
}
.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}

/* ── C-14: AI 分享卡图片 ── */
.share-card-image-wrapper {
  width: 100%;
  border-radius: 4px;
  overflow: hidden;
  margin-bottom: 1rem;
}

.share-card-image {
  width: 100%;
  height: auto;
  display: block;
  border-radius: 4px;
}

.share-card-generating {
  width: 100%;
  height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(201, 168, 76, 0.08);
  border: 1px dashed rgba(201, 168, 76, 0.3);
  border-radius: 4px;
  color: rgba(201, 168, 76, 0.6);
  font-size: 0.8rem;
  letter-spacing: 0.1em;
}
</style>
