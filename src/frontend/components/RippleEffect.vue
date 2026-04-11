<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue'

const props = withDefaults(defineProps<{
  /** 控制是否显示涟漪动画 */
  active?: boolean
  /** 配色方案：default=暖色墨迹，golden=金色显灵 */
  colorScheme?: 'default' | 'golden'
}>(), {
  active: false,
  colorScheme: 'default',
})

// 涟漪扩散动画（独立于墨池，作为背景特效）
// 由 Canvas 2D 绘制，支持多点同时扩散
// active prop 控制是否显示涟漪动画
interface Ripple {
  x: number
  y: number
  radius: number
  maxRadius: number
  opacity: number
  color: string
}

const canvasRef = ref<HTMLCanvasElement | null>(null)
let animFrameId: number | null = null
const ripples: Ripple[] = []

const DEFAULT_COLORS = [
  'rgba(232, 220, 200, 0.15)',
  'rgba(180, 160, 120, 0.1)',
  'rgba(139, 115, 85, 0.12)',
]

// S-13 显灵金色涟漪配色（关键词共鸣满5次时使用）
const GOLDEN_COLORS = [
  'rgba(212, 175, 55, 0.45)',
  'rgba(201, 168, 76, 0.35)',
  'rgba(180, 140, 60, 0.28)',
  'rgba(232, 220, 180, 0.2)',
]

function getColorPalette() {
  return props.colorScheme === 'golden' ? GOLDEN_COLORS : DEFAULT_COLORS
}

function spawnRipple(x: number, y: number) {
  const colors = getColorPalette()
  ripples.push({
    x,
    y,
    radius: 0,
    maxRadius: 120 + Math.random() * 60,
    opacity: 1,
    color: colors[Math.floor(Math.random() * colors.length)],
  })
}

function draw() {
  const canvas = canvasRef.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  if (!ctx) return

  ctx.clearRect(0, 0, canvas.width, canvas.height)

  for (let i = ripples.length - 1; i >= 0; i--) {
    const r = ripples[i]
    r.radius += 2
    r.opacity = Math.max(0, 1 - r.radius / r.maxRadius)

    if (r.opacity <= 0) {
      ripples.splice(i, 1)
      continue
    }

    ctx.beginPath()
    ctx.arc(r.x, r.y, r.radius, 0, Math.PI * 2)
    ctx.strokeStyle = r.color
    ctx.lineWidth = 1.5
    ctx.globalAlpha = r.opacity
    ctx.stroke()
    ctx.globalAlpha = 1
  }

  animFrameId = requestAnimationFrame(draw)
}

let intervalId: ReturnType<typeof setInterval> | null = null

function startAnimation() {
  if (!canvasRef.value) return
  const canvas = canvasRef.value

  // 定时在随机位置产生涟漪（仅 active 时）
  intervalId = setInterval(() => {
    if (!canvas) return
    const x = Math.random() * canvas.width
    const y = Math.random() * canvas.height
    spawnRipple(x, y)
  }, 800)

  if (animFrameId === null) {
    draw()
  }
}

function stopAnimation() {
  if (intervalId !== null) {
    clearInterval(intervalId)
    intervalId = null
  }
  // 保留已有涟漪让其扩散完毕，但停止生成新涟漪
}

// 监听 active prop：true=显示涟漪，false=停止生成
watch(
  () => props.active,
  (isActive) => {
    if (isActive) {
      startAnimation()
    } else {
      stopAnimation()
    }
  },
  { immediate: true }
)

onMounted(() => {
  const canvas = canvasRef.value
  if (!canvas) return

  // 自适应尺寸
  const resize = () => {
    canvas.width = window.innerWidth
    canvas.height = window.innerHeight
  }
  resize()
  window.addEventListener('resize', resize)

  // 初始状态根据 active 决定
  if (props.active !== false) {
    startAnimation()
  }
})

onUnmounted(() => {
  if (animFrameId !== null) cancelAnimationFrame(animFrameId)
  if (intervalId !== null) clearInterval(intervalId)
})
</script>

<template>
  <div data-testid="ink-ripple-animation" class="ripple-container">
    <canvas ref="canvasRef" class="ripple-canvas" />
  </div>
</template>

<style scoped>
.ripple-container {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 1;
}
.ripple-canvas {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}
</style>
