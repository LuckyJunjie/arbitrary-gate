<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import type { KeywordCard } from '@/services/api'
import { playPaperRub, playWindChime } from '@/composables/useSound'

const props = defineProps<{
  card: KeywordCard
}>()

const emit = defineEmits<{
  revealed: []
}>()

// ---------- DOM refs ----------
const containerRef = ref<HTMLDivElement | null>(null)
const canvasRef = ref<HTMLCanvasElement | null>(null)

// ---------- State ----------
type Phase = 'idle' | 'scratching' | 'revealing' | 'done'
const phase = ref<Phase>('idle')
const eraseProgress = ref(0) // 0~100

// ---------- Canvas + drawing state ----------
let ctx: CanvasRenderingContext2D | null = null
let isPointerDown = false
// track total pixels for progress calculation
let totalPixels = 0
let erasedPixels = 0
let lastPaperRubTime = 0

const ERASE_THRESHOLD = 80 // 擦除百分比阈值

// ---------- Lifecycle ----------
onMounted(async () => {
  await nextTick()
  initCanvas()
  // Start with card slightly visible (1/5 top reveal via clip-path already handled in parent)
  // Here we paint the full ink overlay on canvas
  paintInkLayer()
})

onUnmounted(() => {
  destroyCanvas()
})

// ---------- Canvas setup ----------
function initCanvas() {
  const canvas = canvasRef.value
  if (!canvas || !containerRef.value) return

  const rect = containerRef.value.getBoundingClientRect()
  const dpr = window.devicePixelRatio || 1

  canvas.width = rect.width * dpr
  canvas.height = rect.height * dpr
  canvas.style.width = rect.width + 'px'
  canvas.style.height = rect.height + 'px'

  ctx = canvas.getContext('2d')
  if (!ctx) return
  ctx.scale(dpr, dpr)

  totalPixels = rect.width * rect.height
}

function destroyCanvas() {
  ctx = null
}

// Paint a full ink layer with brush-stroke texture
function paintInkLayer() {
  if (!ctx || !canvasRef.value) return
  const canvas = canvasRef.value
  const w = canvas.offsetWidth
  const h = canvas.offsetHeight

  // Base ink fill
  ctx.globalCompositeOperation = 'source-over'
  ctx.fillStyle = '#1a1208'
  ctx.fillRect(0, 0, w, h)

  // Add ink texture via noise dots
  for (let i = 0; i < 800; i++) {
    const x = Math.random() * w
    const y = Math.random() * h
    const r = Math.random() * 3 + 1
    ctx.beginPath()
    ctx.arc(x, y, r, 0, Math.PI * 2)
    ctx.fillStyle = `rgba(${20 + Math.random() * 20}, ${10 + Math.random() * 10}, 0, ${0.4 + Math.random() * 0.5})`
    ctx.fill()
  }

  // Darker streaks for ink feel
  for (let i = 0; i < 30; i++) {
    const x1 = Math.random() * w
    const y1 = Math.random() * h
    const x2 = x1 + (Math.random() - 0.5) * 60
    const y2 = y1 + (Math.random() - 0.5) * 60
    ctx.beginPath()
    ctx.moveTo(x1, y1)
    ctx.bezierCurveTo(
      x1 + (Math.random() - 0.5) * 40,
      y1 + (Math.random() - 0.5) * 40,
      x2 + (Math.random() - 0.5) * 40,
      y2 + (Math.random() - 0.5) * 40,
      x2, y2
    )
    ctx.strokeStyle = `rgba(5, 3, 1, ${0.3 + Math.random() * 0.4})`
    ctx.lineWidth = Math.random() * 4 + 1
    ctx.stroke()
  }

  // Center soft glow hint (very subtle)
  const cx = w / 2
  const cy = h / 2
  const gradient = ctx.createRadialGradient(cx, cy, 0, cx, cy, Math.min(w, h) * 0.35)
  gradient.addColorStop(0, 'rgba(40, 30, 15, 0.3)')
  gradient.addColorStop(1, 'rgba(0, 0, 0, 0)')
  ctx.fillStyle = gradient
  ctx.fillRect(0, 0, w, h)
}

// ---------- Pointer/touch events ----------
function getPointerPos(e: MouseEvent | TouchEvent): { x: number; y: number } {
  const canvas = canvasRef.value!
  const rect = canvas.getBoundingClientRect()
  if ('touches' in e) {
    const touch = e.touches[0] || e.changedTouches[0]
    return {
      x: touch.clientX - rect.left,
      y: touch.clientY - rect.top,
    }
  }
  return {
    x: e.clientX - rect.left,
    y: e.clientY - rect.top,
  }
}

function onPointerDown(e: MouseEvent | TouchEvent) {
  if (phase.value !== 'idle' && phase.value !== 'scratching') return
  isPointerDown = true
  phase.value = 'scratching'
  e.preventDefault()
  erase(ctx, getPointerPos(e), 20)
}

function onPointerMove(e: MouseEvent | TouchEvent) {
  if (!isPointerDown || phase.value !== 'scratching') return
  e.preventDefault()
  erase(ctx, getPointerPos(e), 22)
  // Throttle paperRub sound to every 200ms
  const now = Date.now()
  if (now - lastPaperRubTime > 200) {
    lastPaperRubTime = now
    playPaperRub()
  }
}

function onPointerUp() {
  if (!isPointerDown) return
  isPointerDown = false
  checkReveal()
}

// Erase (destination-out) at position
function erase(
  context: CanvasRenderingContext2D | null,
  pos: { x: number; y: number },
  brushSize: number
) {
  if (!context) return
  context.globalCompositeOperation = 'destination-out'
  context.globalAlpha = 1

  // Soft brush: radial gradient circle
  const gradient = context.createRadialGradient(pos.x, pos.y, 0, pos.x, pos.y, brushSize)
  gradient.addColorStop(0, 'rgba(0, 0, 0, 1)')
  gradient.addColorStop(0.5, 'rgba(0, 0, 0, 0.8)')
  gradient.addColorStop(1, 'rgba(0, 0, 0, 0)')

  context.fillStyle = gradient
  context.beginPath()
  context.arc(pos.x, pos.y, brushSize, 0, Math.PI * 2)
  context.fill()

  // Count erased area
  const imgData = context.getImageData(0, 0, context.canvas.width, context.canvas.height)
  // Approximate: use alpha channel. If alpha < 128, consider it erased
  let cleared = 0
  for (let i = 3; i < imgData.data.length; i += 4) {
    if (imgData.data[i] < 128) cleared++
  }
  erasedPixels = Math.max(erasedPixels, cleared)
  eraseProgress.value = Math.min(100, Math.round((erasedPixels / totalPixels) * 100 * 4))

  checkReveal()
}

// Check if enough has been erased
function checkReveal() {
  if (eraseProgress.value >= ERASE_THRESHOLD && phase.value === 'scratching') {
    triggerReveal()
  }
}

// Trigger full reveal animation
async function triggerReveal() {
  phase.value = 'revealing'
  // Scratch off remaining ink rapidly
  await eraseAll()
  // Wait for CSS animations
  await delay(600)
  phase.value = 'done'
  playWindChime()
  emit('revealed')
}

// Quickly erase all remaining ink
function eraseAll(): Promise<void> {
  return new Promise(resolve => {
    if (!ctx || !canvasRef.value) { resolve(); return }
    ctx.globalCompositeOperation = 'destination-out'
    ctx.globalAlpha = 1
    ctx.fillStyle = 'rgba(0,0,0,1)'
    ctx.fillRect(0, 0, canvasRef.value.offsetWidth, canvasRef.value.offsetHeight)
    eraseProgress.value = 100
    resolve()
  })
}

function delay(ms: number) {
  return new Promise(resolve => setTimeout(resolve, ms))
}

// Touch event passive: false for preventDefault
function onTouchStart(e: TouchEvent) { onPointerDown(e) }
function onTouchMove(e: TouchEvent) { onPointerMove(e) }
function onTouchEnd() { onPointerUp() }
function onMouseDown(e: MouseEvent) { onPointerDown(e) }
function onMouseMove(e: MouseEvent) { onPointerMove(e) }
function onMouseUp() { onPointerUp() }
function onMouseLeave() { onPointerUp() }
</script>

<template>
  <div
    ref="containerRef"
    class="scratch-card-container"
    :class="[`phase-${phase}`]"
  >
    <!-- Progress bar -->
    <div v-if="phase === 'scratching'" class="erase-progress-bar">
      <div class="erase-progress-fill" :style="{ width: eraseProgress + '%' }"></div>
    </div>

    <!-- Card (behind canvas) — C-09: clip-path reveals card progressively as ink is erased -->
    <div
      class="card-layer"
      :style="{
        clipPath: `inset(${Math.round((100 - eraseProgress) * 0.80)}% 0 0 0)`,
      }"
    >
      <slot :card="card" />
    </div>

    <!-- Canvas ink overlay — C-09: fades out as eraseProgress increases, revealing clipped card -->
    <canvas
      ref="canvasRef"
      class="ink-canvas"
      :class="{ 'ink-canvas--hidden': phase === 'done' }"
      :style="{ opacity: 1 - eraseProgress / 100 }"
      @mousedown="onMouseDown"
      @mousemove="onMouseMove"
      @mouseup="onMouseUp"
      @mouseleave="onMouseLeave"
      @touchstart.prevent="onTouchStart"
      @touchmove.prevent="onTouchMove"
      @touchend.prevent="onTouchEnd"
    />

    <!-- Reveal animations: ink drops falling back to pool -->
    <div v-if="phase === 'revealing'" class="ink-drops-container">
      <div
        v-for="i in 12"
        :key="i"
        class="ink-drop"
        :style="{
          left: (8 + (i - 1) * 8) + '%',
          animationDelay: (i * 0.04) + 's',
          width: (4 + Math.random() * 4) + 'px',
          height: (4 + Math.random() * 6) + 'px',
        }"
      ></div>
    </div>

    <!-- Hint text — C-09: 方正清刻本悦宋 font -->
    <p v-if="phase === 'idle' || phase === 'scratching'" class="scratch-hint">
      墨中有物，拂去墨迹
    </p>

    <!-- Progress text -->
    <p v-if="phase === 'scratching'" class="erase-percent">
      {{ eraseProgress }}%
    </p>
  </div>
</template>

<style scoped>
.scratch-card-container {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.8rem;
  width: 220px; /* slightly wider than card to accommodate canvas */
}

/* Card layer sits behind the canvas */
.card-layer {
  position: relative;
  z-index: 1;
  animation: none;
}

/* Canvas sits on top, capturing pointer events */
.ink-canvas {
  position: absolute;
  top: 0;
  left: 0;
  z-index: 10;
  cursor: crosshair;
  border-radius: 8px;
  transition: opacity 0.5s ease;
  touch-action: none;
  user-select: none;
}

.ink-canvas--hidden {
  opacity: 0;
  pointer-events: none;
}

/* Erase progress bar */
.erase-progress-bar {
  position: absolute;
  top: -20px;
  left: 0;
  right: 0;
  height: 3px;
  background: rgba(232, 220, 200, 0.1);
  border-radius: 2px;
  overflow: hidden;
  z-index: 20;
}

.erase-progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #c9a84c, #8b6040);
  border-radius: 2px;
  transition: width 0.1s linear;
}

/* Hint text — C-09: 方正清刻本悦宋 font */
.scratch-hint {
  font-size: 0.8rem;
  color: rgba(232, 220, 200, 0.4);
  letter-spacing: 0.15em;
  margin: 0;
  font-style: italic;
  animation: hintPulse 2.5s var(--ease-loop) infinite;
  z-index: 20;
  position: relative;
  font-family: '方正清刻本悦宋', 'FZQingKeBenYueSong', 'STKaiti', 'KaiTi', serif;
}

@keyframes hintPulse {
  0%, 100% { opacity: 0.4; }
  50% { opacity: 0.7; }
}

/* Erase percent */
.erase-percent {
  font-size: 0.7rem;
  color: rgba(201, 168, 76, 0.6);
  margin: 0;
  letter-spacing: 0.1em;
  z-index: 20;
  position: relative;
}

/* Ink drops animation */
.ink-drops-container {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 15;
  overflow: hidden;
}

.ink-drop {
  position: absolute;
  top: -10px;
  border-radius: 50%;
  background: rgba(42, 31, 20, 0.8);
  animation: inkDrop 0.8s var(--ease-out) forwards;
}

@keyframes inkDrop {
  0% {
    transform: translateY(0) scale(1);
    opacity: 0.9;
  }
  70% {
    opacity: 0.7;
  }
  100% {
    transform: translateY(320px) scale(0.4);
    opacity: 0;
  }
}

/* Phase: revealing — card floats up */
.phase-revealing .card-layer,
.phase-done .card-layer {
  animation: cardFloatUp 0.6s var(--ease-spring) forwards;
}

@keyframes cardFloatUp {
  0% {
    transform: translateY(0) scale(1);
    filter: brightness(1);
  }
  30% {
    transform: translateY(-12px) scale(1.03);
    filter: brightness(1.2);
  }
  100% {
    transform: translateY(0) scale(1);
    filter: brightness(1);
  }
}

/* Phase: done */
.phase-done .card-layer {
  animation: cardRevealFinal 0.5s var(--ease-out) forwards;
}

@keyframes cardRevealFinal {
  from {
    filter: brightness(1.3) saturate(1.2);
  }
  to {
    filter: brightness(1) saturate(1);
  }
}
</style>
