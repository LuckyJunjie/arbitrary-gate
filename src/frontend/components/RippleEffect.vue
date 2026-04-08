<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'

defineProps<{
  active?: boolean
}>()

// 涟漪扩散动画（独立于墨池，作为背景特效）
// 由 Canvas 2D 绘制，支持多点同时扩散
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

const COLORS = [
  'rgba(232, 220, 200, 0.15)',
  'rgba(180, 160, 120, 0.1)',
  'rgba(139, 115, 85, 0.12)',
]

function spawnRipple(x: number, y: number) {
  ripples.push({
    x,
    y,
    radius: 0,
    maxRadius: 120 + Math.random() * 60,
    opacity: 1,
    color: COLORS[Math.floor(Math.random() * COLORS.length)],
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

  // 定时在随机位置产生涟漪
  intervalId = setInterval(() => {
    const x = Math.random() * canvas.width
    const y = Math.random() * canvas.height
    spawnRipple(x, y)
  }, 800)

  draw()
})

onUnmounted(() => {
  if (animFrameId !== null) cancelAnimationFrame(animFrameId)
  if (intervalId !== null) clearInterval(intervalId)
})
</script>

<template>
  <div data-testid="ink-ripple-animation">
    <canvas ref="canvasRef" class="ripple-canvas" />
  </div>
</template>

<style scoped>
.ripple-canvas {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 1;
}
</style>
