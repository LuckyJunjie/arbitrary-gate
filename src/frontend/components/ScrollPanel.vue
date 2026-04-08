<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'

// Props
const props = defineProps<{
  itemHeight?: number     // 每项估算高度（用于虚拟滚动）
  bufferSize?: number     // 缓冲区大小（上下各渲染多少项）
}>()

const panelRef = ref<HTMLElement | null>(null)
const contentRef = ref<HTMLElement | null>(null)

// ============ 手势状态 ============
interface TouchState {
  startX: number
  startY: number
  startTime: number
  lastX: number
  lastY: number
  lastTime: number
  velocityX: number
  velocityY: number
}

const touchState = ref<TouchState>({
  startX: 0,
  startY: 0,
  startTime: 0,
  lastX: 0,
  lastY: 0,
  lastTime: 0,
  velocityX: 0,
  velocityY: 0,
})

const isTouching = ref(false)

// ============ 惯性滚动状态 ============
interface InertiaState {
  active: boolean
  velocity: number
  position: number
  startPosition: number
  startTime: number
  overscroll: number
  bounceBack: boolean
}

const inertia = ref<InertiaState>({
  active: false,
  velocity: 0,
  position: 0,
  startPosition: 0,
  startTime: 0,
  overscroll: 0,
  bounceBack: false,
})

let rafId: number | null = null
let lastFrameTime = 0

// ============ 弹性效果参数 ============
const BOUNCE_FACTOR = 0.35       // 回弹系数
const BOUNCE_THRESHOLD = 80      // 开始回弹的溢出阈值
const FRICTION = 0.92            // 摩擦系数
const MIN_VELOCITY = 0.1         // 最小速度阈值
const MAX_BOUNCE = 120           // 最大回弹距离

// ============ 虚拟滚动状态 ============
const scrollTop = ref(0)
const scrollLeft = ref(0)
const contentHeight = ref(0)
const contentWidth = ref(0)

const buffer = computed(() => props.bufferSize ?? 3)
const estimatedItemHeight = computed(() => props.itemHeight ?? 60)

// ============ 滚动位置（transform 实现）============
const transformY = ref(0)

// ============ 触摸事件处理 ============
function onTouchStart(e: TouchEvent) {
  if (e.touches.length !== 1) return
  const touch = e.touches[0]
  const now = Date.now()

  touchState.value = {
    startX: touch.clientX,
    startY: touch.clientY,
    startTime: now,
    lastX: touch.clientX,
    lastY: touch.clientY,
    lastTime: now,
    velocityX: 0,
    velocityY: 0,
  }
  isTouching.value = true

  // 停止惯性动画
  stopInertia()
}

function onTouchMove(e: TouchEvent) {
  if (!isTouching.value || e.touches.length !== 1) return
  const touch = e.touches[0]
  const now = Date.now()
  const dt = now - touchState.value.lastTime

  if (dt < 1) return

  const deltaX = touch.clientX - touchState.value.lastX
  const deltaY = touch.clientY - touchState.value.lastY

  // 计算速度（像素/毫秒）
  touchState.value.velocityX = deltaX / dt
  touchState.value.velocityY = deltaY / dt

  touchState.value.lastX = touch.clientX
  touchState.value.lastY = touch.clientY
  touchState.value.lastTime = now

  // 实时应用滚动（带弹性边界）
  applyScrollDelta(-deltaY, true)
}

function onTouchEnd(e: TouchEvent) {
  if (!isTouching.value) return
  isTouching.value = false

  // 计算最终速度
  const velocity = -touchState.value.velocityY * 16 // 转换为类似 px/frame

  // 如果有足够的速度，启动惯性滚动
  if (Math.abs(velocity) > MIN_VELOCITY) {
    startInertia(velocity)
  } else {
    // 检查是否需要弹回
    checkBounceBack()
  }
}

function onTouchCancel() {
  isTouching.value = false
  checkBounceBack()
}

// ============ 滚动逻辑 ============
function getScrollRect() {
  const el = panelRef.value
  if (!el) return { maxScroll: 0, current: 0 }
  return {
    maxScroll: el.scrollHeight - el.clientHeight,
    current: el.scrollTop,
  }
}

function applyScrollDelta(delta: number, allowElastic: boolean = false) {
  const el = panelRef.value
  if (!el) return

  const { maxScroll, current } = getScrollRect()
  const newPos = current + delta

  // 弹性边界效果
  if (allowElastic) {
    if (newPos < 0) {
      // 向上溢出（弹性拉伸）
      const overscroll = Math.abs(newPos)
      if (overscroll > BOUNCE_THRESHOLD) {
        // 限制拉伸
        el.scrollTop = -BOUNCE_THRESHOLD * BOUNCE_FACTOR
        inertia.value.overscroll = -BOUNCE_THRESHOLD * BOUNCE_FACTOR
      } else {
        el.scrollTop = newPos
        inertia.value.overscroll = newPos
      }
    } else if (newPos > maxScroll) {
      // 向下溢出
      const overscroll = newPos - maxScroll
      if (overscroll > BOUNCE_THRESHOLD) {
        el.scrollTop = maxScroll + BOUNCE_THRESHOLD * BOUNCE_FACTOR
        inertia.value.overscroll = maxScroll + BOUNCE_THRESHOLD * BOUNCE_FACTOR
      } else {
        el.scrollTop = newPos
        inertia.value.overscroll = newPos
      }
    } else {
      el.scrollTop = newPos
      inertia.value.overscroll = 0
    }
  } else {
    el.scrollTop = Math.max(0, Math.min(maxScroll, newPos))
  }

  updateVirtualScroll()
}

function checkBounceBack() {
  const { maxScroll, current } = getScrollRect()

  if (current < 0) {
    // 弹回到顶部
    startBounceBack(current, 0)
  } else if (current > maxScroll) {
    // 弹回到底部
    startBounceBack(current, maxScroll)
  }
}

// ============ 惯性滚动 ============
function startInertia(initialVelocity: number) {
  const el = panelRef.value
  if (!el) return

  inertia.value = {
    active: true,
    velocity: initialVelocity,
    position: el.scrollTop,
    startPosition: el.scrollTop,
    startTime: performance.now(),
    overscroll: 0,
    bounceBack: false,
  }

  lastFrameTime = performance.now()
  rafId = requestAnimationFrame(inertiaFrame)
}

function inertiaFrame(timestamp: number) {
  if (!inertia.value.active) return

  const dt = timestamp - lastFrameTime
  lastFrameTime = timestamp

  let { velocity, position } = inertia.value
  const el = panelRef.value
  if (!el) return

  const { maxScroll } = getScrollRect()

  // 应用速度
  position += velocity

  // 摩擦衰减
  velocity *= FRICTION

  // 边界检测
  if (position < 0) {
    // 上边界弹性
    const overscroll = Math.abs(position)
    velocity += overscroll * 0.1 // 反弹力
    position = Math.min(0, position * (1 - BOUNCE_FACTOR))
  } else if (position > maxScroll) {
    // 下边界弹性
    const overscroll = position - maxScroll
    velocity -= overscroll * 0.1
    position = Math.max(maxScroll, maxScroll + (position - maxScroll) * (1 - BOUNCE_FACTOR))
  }

  inertia.value.velocity = velocity
  inertia.value.position = position
  el.scrollTop = position

  updateVirtualScroll()

  // 停止条件
  if (Math.abs(velocity) < MIN_VELOCITY) {
    if (position < 0 || position > maxScroll) {
      // 需要弹回
      startBounceBack(position, position < 0 ? 0 : maxScroll)
    } else {
      stopInertia()
    }
    return
  }

  rafId = requestAnimationFrame(inertiaFrame)
}

function startBounceBack(from: number, to: number) {
  inertia.value = {
    active: true,
    velocity: (to - from) * 0.15,
    position: from,
    startPosition: from,
    startTime: performance.now(),
    overscroll: 0,
    bounceBack: true,
  }

  lastFrameTime = performance.now()
  rafId = requestAnimationFrame(bounceFrame)
}

function bounceFrame(timestamp: number) {
  if (!inertia.value.active || !inertia.value.bounceBack) return

  const dt = timestamp - lastFrameTime
  lastFrameTime = timestamp

  let { velocity, position } = inertia.value
  const el = panelRef.value
  if (!el) return

  const { maxScroll } = getScrollRect()
  const target = position < 0 ? 0 : maxScroll

  // 弹性系数
  const elastic = 0.12
  velocity = (target - position) * elastic
  position += velocity

  inertia.value.velocity = velocity
  inertia.value.position = position
  el.scrollTop = position

  updateVirtualScroll()

  // 停止条件
  if (Math.abs(velocity) < 0.5 && Math.abs(position - target) < 0.5) {
    el.scrollTop = target
    updateVirtualScroll()
    stopInertia()
    return
  }

  rafId = requestAnimationFrame(bounceFrame)
}

function stopInertia() {
  if (rafId !== null) {
    cancelAnimationFrame(rafId)
    rafId = null
  }
  inertia.value.active = false
  inertia.value.overscroll = 0
}

// ============ 虚拟滚动 ============
function updateVirtualScroll() {
  const el = panelRef.value
  const content = contentRef.value
  if (!el || !content) return

  // For vertical-rl mode, scrollTop corresponds to horizontal movement
  scrollTop.value = el.scrollTop
  scrollLeft.value = el.scrollLeft

  // Update content dimensions
  contentHeight.value = content.scrollHeight
  contentWidth.value = content.scrollWidth
}

// Virtual scroll computed visible range
const visibleRange = computed(() => {
  const top = scrollTop.value
  const bottom = top + (panelRef.value?.clientHeight ?? 0)
  const itemH = estimatedItemHeight.value
  const buf = buffer.value

  const startIdx = Math.max(0, Math.floor(top / itemH) - buf)
  const endIdx = Math.ceil(bottom / itemH) + buf

  return { startIdx, endIdx }
})

// ============ 滚动条样式（墨迹风格）============
const scrollbarProgress = computed(() => {
  const { maxScroll, current } = getScrollRect()
  if (maxScroll <= 0) return 0
  return current / maxScroll
})

const thumbStyle = computed(() => {
  const el = panelRef.value
  if (!el) return {}
  const { maxScroll } = getScrollRect()
  if (maxScroll <= 0) return { display: 'none' }

  const thumbHeight = Math.max(30, (el.clientHeight / (el.scrollHeight || 1)) * el.clientHeight)
  const maxThumbTop = el.clientHeight - thumbHeight
  const thumbTop = scrollbarProgress.value * maxThumbTop

  return {
    height: `${thumbHeight}px`,
    top: `${thumbTop}px`,
  }
})

// ============ 生命周期 ============
onMounted(() => {
  const el = panelRef.value
  if (!el) return

  // 触摸事件
  el.addEventListener('touchstart', onTouchStart, { passive: true })
  el.addEventListener('touchmove', onTouchMove, { passive: false })
  el.addEventListener('touchend', onTouchEnd, { passive: true })
  el.addEventListener('touchcancel', onTouchCancel, { passive: true })

  // 初始化虚拟滚动
  nextTick(updateVirtualScroll)
})

onUnmounted(() => {
  const el = panelRef.value
  if (!el) return

  el.removeEventListener('touchstart', onTouchStart)
  el.removeEventListener('touchmove', onTouchMove)
  el.removeEventListener('touchend', onTouchEnd)
  el.removeEventListener('touchcancel', onTouchCancel)

  stopInertia()
})

// 暴露方法给父组件
defineExpose({
  scrollTo: (position: number) => {
    const el = panelRef.value
    if (el) {
      el.scrollTop = Math.max(0, position)
      updateVirtualScroll()
    }
  },
  scrollBy: (delta: number) => {
    const el = panelRef.value
    if (el) {
      el.scrollTop = Math.max(0, el.scrollTop + delta)
      updateVirtualScroll()
    }
  },
})
</script>

<template>
  <div class="scroll-panel" ref="panelRef">
    <!-- 自定义滚动条（墨迹风格） -->
    <div class="scrollbar-track">
      <div class="scrollbar-thumb" :style="thumbStyle">
        <div class="scrollbar-ink"></div>
      </div>
    </div>

    <!-- 滚动内容 -->
    <div class="scroll-content" ref="contentRef">
      <slot />
    </div>

    <!-- 上下卷轴装饰 -->
    <div class="scroll-rail rail-top"></div>
    <div class="scroll-rail rail-bottom"></div>

    <!-- 边界回弹指示器 -->
    <div
      v-if="inertia.overscroll < 0"
      class="bounce-indicator bounce-top"
    ></div>
    <div
      v-if="inertia.overscroll > 0"
      class="bounce-indicator bounce-bottom"
    ></div>
  </div>
</template>

<style scoped>
.scroll-panel {
  position: relative;
  height: 100%;
  overflow: hidden;
  /* 竖向排版：文字从上到下，从右到左 */
  writing-mode: vertical-rl;
  text-orientation: mixed;
  direction: ltr;
  -webkit-overflow-scrolling: touch;
  touch-action: pan-x pan-y;
}

.scroll-content {
  height: 100%;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 1.5rem;
  /* 隐藏滚动条但保持功能 */
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.scroll-content::-webkit-scrollbar {
  display: none;
}

/* ============ 自定义墨迹风格滚动条 ============ */
.scrollbar-track {
  position: absolute;
  right: 2px;
  top: 12px;
  bottom: 12px;
  width: 6px;
  background: linear-gradient(
    180deg,
    rgba(90, 64, 48, 0.2) 0%,
    rgba(90, 64, 48, 0.4) 50%,
    rgba(90, 64, 48, 0.2) 100%
  );
  border-radius: 3px;
  z-index: 20;
  pointer-events: none;
  opacity: 0.6;
  transition: opacity 0.3s ease;
}

.scroll-panel:hover .scrollbar-track {
  opacity: 1;
}

.scrollbar-thumb {
  position: absolute;
  left: 0;
  right: 0;
  background: linear-gradient(
    180deg,
    #5a4030 0%,
    #8b7355 30%,
    #a08060 50%,
    #8b7355 70%,
    #5a4030 100%
  );
  border-radius: 3px;
  box-shadow:
    0 0 4px rgba(0, 0, 0, 0.3),
    inset 0 1px 2px rgba(255, 255, 255, 0.2);
  transition: transform 0.1s ease;
}

.scrollbar-ink {
  position: absolute;
  inset: 2px;
  background: linear-gradient(
    180deg,
    rgba(255, 255, 255, 0.15) 0%,
    transparent 50%,
    rgba(0, 0, 0, 0.1) 100%
  );
  border-radius: 2px;
}

/* ============ 卷轴边缘装饰 ============ */
.scroll-rail {
  position: absolute;
  left: 0;
  right: 0;
  height: 12px;
  background: linear-gradient(
    90deg,
    #5a4030 0%,
    #8b7355 30%,
    #a08060 50%,
    #8b7355 70%,
    #5a4030 100%
  );
  z-index: 10;
  pointer-events: none;
}

.rail-top {
  top: 0;
  border-radius: 0 0 6px 6px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
}

.rail-bottom {
  bottom: 0;
  border-radius: 6px 6px 0 0;
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.3);
}

/* ============ 边界回弹指示器 ============ */
.bounce-indicator {
  position: absolute;
  left: 0;
  right: 0;
  height: 40px;
  z-index: 15;
  pointer-events: none;
  opacity: 0;
  transition: opacity 0.15s ease;
}

.bounce-indicator.active {
  opacity: 1;
}

.bounce-top {
  top: 12px;
  background: linear-gradient(
    180deg,
    rgba(139, 115, 85, 0.4) 0%,
    transparent 100%
  );
}

.bounce-bottom {
  bottom: 12px;
  background: linear-gradient(
    0deg,
    rgba(139, 115, 85, 0.4) 0%,
    transparent 100%
  );
}

/* ============ 滚动动画优化 ============ */
.scroll-panel * {
  will-change: transform;
  backface-visibility: hidden;
}

/* 虚拟滚动优化 */
.scroll-content :deep(*) {
  transform: translateZ(0);
}
</style>
