<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const shareCode = route.params.code as string

interface ShareContent {
  title: string
  summary: string
  author: string
  createdAt: string
  cardCode: string
}

const shareData = ref<ShareContent | null>(null)
const isLoading = ref(true)
const canvasRef = ref<HTMLCanvasElement | null>(null)
const verifyDialogOpen = ref(false)
const couponCode = ref('')
const verifyResult = ref<'success' | 'fail' | null>(null)
const isVerifying = ref(false)

// Mock API call - replace with real API
async function fetchShareContent(code: string): Promise<ShareContent> {
  // Simulate API delay
  await new Promise(resolve => setTimeout(resolve, 600))
  // Mock data
  return {
    title: '长安十二时辰·上元夜',
    summary: '天宝三载，上元夜。长安城灯火通明，万国来朝。我随父亲入宫赴宴，却不知这一夜将改变我一生......',
    author: '时光旅人',
    createdAt: new Date().toISOString(),
    cardCode: code,
  }
}

// Draw the missing-corner story card on canvas
function drawMissingCornerCard(canvas: HTMLCanvasElement, data: ShareContent) {
  const ctx = canvas.getContext('2d')
  if (!ctx) return

  const W = canvas.width
  const H = canvas.height
  const cornerSize = 48 // missing corner size

  // Background
  ctx.fillStyle = '#2c1f14'
  ctx.fillRect(0, 0, W, H)

  // Draw the missing corner (top-right) - clip path
  ctx.save()
  ctx.beginPath()
  ctx.moveTo(0, 0)
  ctx.lineTo(W - cornerSize, 0)
  ctx.lineTo(W, cornerSize)
  ctx.lineTo(W, H)
  ctx.lineTo(0, H)
  ctx.closePath()
  ctx.clip()
  ctx.fillStyle = '#2c1f14'
  ctx.fillRect(0, 0, W, H)
  ctx.restore()

  // Inner border
  const innerPad = 16
  ctx.strokeStyle = '#8b7355'
  ctx.lineWidth = 1
  ctx.beginPath()
  ctx.moveTo(innerPad, innerPad)
  ctx.lineTo(W - cornerSize - innerPad, innerPad)
  ctx.lineTo(W - innerPad, cornerSize + innerPad)
  ctx.lineTo(W - innerPad, H - innerPad)
  ctx.lineTo(innerPad, H - innerPad)
  ctx.closePath()
  ctx.stroke()

  // Gold corner decoration (the missing part indicator)
  ctx.fillStyle = '#c9a84c'
  ctx.beginPath()
  ctx.moveTo(W - cornerSize, 0)
  ctx.lineTo(W - cornerSize, cornerSize)
  ctx.lineTo(W, cornerSize)
  ctx.closePath()
  ctx.fill()

  // Title
  ctx.fillStyle = '#c9a84c'
  ctx.font = 'bold 18px serif'
  ctx.textAlign = 'center'
  ctx.fillText(data.title, W / 2, 64)

  // Separator line
  ctx.strokeStyle = 'rgba(139, 115, 85, 0.4)'
  ctx.lineWidth = 1
  ctx.beginPath()
  ctx.moveTo(24, 80)
  ctx.lineTo(W - 24, 80)
  ctx.stroke()

  // Summary text (wrapped)
  ctx.fillStyle = '#e8dcc8'
  ctx.font = '13px serif'
  ctx.textAlign = 'left'
  const lines = wrapText(ctx, data.summary, W - 48)
  let y = 110
  for (const line of lines) {
    ctx.fillText(line, 24, y)
    y += 20
    if (y > H - 80) break
  }

  // Author
  ctx.fillStyle = 'rgba(139, 115, 85, 0.7)'
  ctx.font = '11px serif'
  ctx.textAlign = 'right'
  const dateStr = new Date(data.createdAt).toLocaleDateString('zh-CN')
  ctx.fillText(`— ${data.author}  ${dateStr}`, W - 24, H - 24)

  // Card code at bottom center
  ctx.fillStyle = 'rgba(201, 168, 76, 0.5)'
  ctx.font = '10px monospace'
  ctx.textAlign = 'center'
  ctx.fillText(`# ${data.cardCode}`, W / 2, H - 24)

  // Card seal mark
  ctx.fillStyle = 'rgba(201, 168, 76, 0.15)'
  ctx.beginPath()
  ctx.arc(W - 36, 36, 24, 0, Math.PI * 2)
  ctx.fill()
  ctx.fillStyle = 'rgba(201, 168, 76, 0.4)'
  ctx.font = 'bold 14px serif'
  ctx.textAlign = 'center'
  ctx.fillText('笺', W - 36, 41)
}

function wrapText(ctx: CanvasRenderingContext2D, text: string, maxWidth: number): string[] {
  const words = text.split('')
  const lines: string[] = []
  let current = ''
  for (const char of words) {
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

async function renderCanvas(data: ShareContent) {
  await nextTick()
  const canvas = canvasRef.value
  if (!canvas) return
  // Set canvas size for high-DPI
  const rect = canvas.getBoundingClientRect()
  const dpr = window.devicePixelRatio || 1
  canvas.width = rect.width * dpr
  canvas.height = rect.height * dpr
  const ctx = canvas.getContext('2d')
  if (ctx) {
    ctx.scale(dpr, dpr)
    drawMissingCornerCard(canvas, data)
  }
}

async function handleVerify() {
  if (!couponCode.value.trim()) return
  isVerifying.value = true
  verifyResult.value = null
  // Mock verification
  await new Promise(resolve => setTimeout(resolve, 800))
  // Simple mock: accept if code has at least 6 chars
  verifyResult.value = couponCode.value.trim().length >= 6 ? 'success' : 'fail'
  isVerifying.value = false
}

onMounted(async () => {
  if (!shareCode) {
    isLoading.value = false
    return
  }
  try {
    shareData.value = await fetchShareContent(shareCode)
    await renderCanvas(shareData.value)
  } catch (err) {
    console.error('Failed to load share content:', err)
  } finally {
    isLoading.value = false
  }
})
</script>

<template>
  <div class="share-view">
    <header class="share-header">
      <h2>时光笺 · 故事卡</h2>
    </header>

    <div class="share-card">
      <canvas
        v-if="!isLoading && shareData"
        ref="canvasRef"
        class="story-canvas"
      ></canvas>
      <div v-else class="card-placeholder">
        <p>{{ isLoading ? '故事卡加载中...' : '无效的分享码' }}</p>
      </div>
    </div>

    <div class="share-actions" v-if="shareData">
      <!-- 缺角码显示 -->
      <div class="share-code-display">
        <span class="code-label">缺角码</span>
        <span class="code-value">{{ shareData.cardCode }}</span>
      </div>

      <!-- 合券验证入口 -->
      <button
        class="verify-btn"
        @click="verifyDialogOpen = !verifyDialogOpen"
      >
        {{ verifyDialogOpen ? '收起合券入口' : '合券验证' }}
      </button>

      <div v-if="verifyDialogOpen" class="verify-panel">
        <div class="verify-input-row">
          <input
            v-model="couponCode"
            class="coupon-input"
            placeholder="请输入合券码"
            @keydown.enter="handleVerify"
          />
          <button
            class="coupon-submit"
            @click="handleVerify"
            :disabled="isVerifying"
          >
            {{ isVerifying ? '验证中...' : '验证' }}
          </button>
        </div>
        <p v-if="verifyResult === 'success'" class="verify-msg success">
          合券成功！故事已收录
        </p>
        <p v-else-if="verifyResult === 'fail'" class="verify-msg fail">
          合券码无效，请检查后重试
        </p>
      </div>
    </div>

    <p class="copyright">由 时光笺 · 任意门 生成</p>
  </div>
</template>

<style scoped>
.share-view {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: radial-gradient(ellipse at center, #f5efe0 0%, #ede0c8 100%);
  padding: 2rem 1rem;
}

.share-header h2 {
  font-size: 1.25rem;
  color: #2c1f14;
  letter-spacing: 0.2em;
  margin-bottom: 2rem;
}

.share-card {
  width: 100%;
  max-width: 360px;
  aspect-ratio: 3 / 4;
  border: 2px solid #8b7355;
  border-radius: 4px;
  overflow: hidden;
  margin-bottom: 1.5rem;
  background: #2c1f14;
  box-shadow: 0 8px 32px rgba(44, 31, 20, 0.4);
}

.story-canvas {
  width: 100%;
  height: 100%;
  display: block;
}

.card-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(200, 180, 140, 0.1);
  color: #8b7355;
}

.share-actions {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  width: 100%;
  max-width: 280px;
}

.share-code-display {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.5rem 0.75rem;
  border: 1px solid rgba(139, 115, 85, 0.3);
  border-radius: 2px;
  background: rgba(44, 31, 20, 0.05);
}

.code-label {
  font-size: 0.75rem;
  color: #8b7355;
  letter-spacing: 0.1em;
}

.code-value {
  font-size: 0.85rem;
  color: #2c1f14;
  font-family: monospace;
  letter-spacing: 0.15em;
}

.verify-btn {
  padding: 0.5rem;
  border: 1px solid #8b7355;
  border-radius: 2px;
  background: transparent;
  font-family: inherit;
  font-size: 0.85rem;
  color: #8b7355;
  cursor: pointer;
  transition: all 0.2s;
}

.verify-btn:hover {
  background: rgba(139, 115, 85, 0.1);
}

.verify-panel {
  border: 1px solid rgba(139, 115, 85, 0.2);
  border-radius: 2px;
  padding: 0.75rem;
  background: rgba(255, 255, 255, 0.4);
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.verify-input-row {
  display: flex;
  gap: 0.5rem;
}

.coupon-input {
  flex: 1;
  padding: 0.35rem 0.5rem;
  border: 1px solid #8b7355;
  border-radius: 2px;
  background: rgba(255, 255, 255, 0.6);
  font-family: inherit;
  font-size: 0.85rem;
  color: #2c1f14;
  outline: none;
}

.coupon-input:focus {
  border-color: #2c1f14;
}

.coupon-submit {
  padding: 0.35rem 0.75rem;
  border: 1px solid #2c1f14;
  border-radius: 2px;
  background: #2c1f14;
  font-family: inherit;
  font-size: 0.8rem;
  color: #f5efe0;
  cursor: pointer;
  transition: opacity 0.2s;
}

.coupon-submit:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.verify-msg {
  font-size: 0.8rem;
  text-align: center;
  margin: 0;
}

.verify-msg.success {
  color: #6b8e6b;
}

.verify-msg.fail {
  color: #a05050;
}

.copyright {
  margin-top: 2rem;
  font-size: 0.75rem;
  color: #8b7355;
}
</style>
