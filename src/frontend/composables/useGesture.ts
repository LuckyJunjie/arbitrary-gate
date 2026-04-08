import { ref, onMounted, onUnmounted, Ref } from 'vue'

interface SwipeState {
  direction: 'up' | 'down' | 'left' | 'right' | null
  deltaX: number
  deltaY: number
  duration: number
}

export function useSwipe(
  el: Ref<HTMLElement | null>,
  onSwipe?: (state: SwipeState) => void
) {
  const isSwiping = ref(false)
  const swipeState = ref<SwipeState>({
    direction: null,
    deltaX: 0,
    deltaY: 0,
    duration: 0,
  })

  let startX = 0
  let startY = 0
  let startTime = 0

  // 手势判定阈值（像素）
  const THRESHOLD = 50
  // 慢速滑动判定时间（ms）
  const SLOW_THRESHOLD = 400

  function getDirection(dx: number, dy: number): SwipeState['direction'] {
    if (Math.abs(dx) > Math.abs(dy)) {
      return dx > 0 ? 'right' : 'left'
    }
    return dy > 0 ? 'down' : 'up'
  }

  function onPointerDown(e: PointerEvent) {
    startX = e.clientX
    startY = e.clientY
    startTime = Date.now()
    isSwiping.value = true
  }

  function onPointerMove(e: PointerEvent) {
    if (!isSwiping.value) return
    const deltaX = e.clientX - startX
    const deltaY = e.clientY - startY
    swipeState.value.deltaX = deltaX
    swipeState.value.deltaY = deltaY
  }

  function onPointerUp(e: PointerEvent) {
    if (!isSwiping.value) return
    const deltaX = e.clientX - startX
    const deltaY = e.clientY - startY
    const duration = Date.now() - startTime

    const absX = Math.abs(deltaX)
    const absY = Math.abs(deltaY)
    const direction = getDirection(deltaX, deltaY)

    // 满足阈值才触发
    const distance = Math.max(absX, absY)
    const isSlow = duration > SLOW_THRESHOLD

    if ((distance > THRESHOLD || isSlow) && direction) {
      swipeState.value = { direction, deltaX, deltaY, duration }
      onSwipe?.(swipeState.value)
    }

    isSwiping.value = false
  }

  function onPointerCancel() {
    isSwiping.value = false
  }

  onMounted(() => {
    const element = el.value
    if (!element) return
    element.addEventListener('pointerdown', onPointerDown)
    element.addEventListener('pointermove', onPointerMove)
    element.addEventListener('pointerup', onPointerUp)
    element.addEventListener('pointercancel', onPointerCancel)
  })

  onUnmounted(() => {
    const element = el.value
    if (!element) return
    element.removeEventListener('pointerdown', onPointerDown)
    element.removeEventListener('pointermove', onPointerMove)
    element.removeEventListener('pointerup', onPointerUp)
    element.removeEventListener('pointercancel', onPointerCancel)
  })

  return { isSwiping, swipeState }
}
