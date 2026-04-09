import { ref, onMounted, onUnmounted, Ref } from 'vue'

// 手势力度类型
export type GestureIntensity = 'gentle' | 'urgent' | 'forceful'

interface SwipeState {
  direction: 'up' | 'down' | 'left' | 'right' | null
  deltaX: number
  deltaY: number
  duration: number
  gestureIntensity: GestureIntensity | null
}

export interface UseSwipeOptions {
  /** 滑动超过此时间(ms)判定为慢速 → gentle */
  slowThresholdMs?: number
  /** 滑动小于此时间(ms)判定为快速 → urgent */
  fastThresholdMs?: number
  /** 长按超过此时间(ms)判定为用力 → forceful */
  longPressThresholdMs?: number
  /** 手势触发回调 */
  onSwipe?: (state: SwipeState) => void
  /** 长按触发回调（时长达到阈值时触发一次） */
  onLongPress?: (state: SwipeState) => void
}

export function useSwipe(
  el: Ref<HTMLElement | null>,
  options: UseSwipeOptions = {}
) {
  const {
    slowThresholdMs = 800,
    fastThresholdMs = 200,
    longPressThresholdMs = 1500,
    onSwipe,
    onLongPress,
  } = options

  const isSwiping = ref(false)
  const isLongPressing = ref(false)
  const swipeState = ref<SwipeState>({
    direction: null,
    deltaX: 0,
    deltaY: 0,
    duration: 0,
    gestureIntensity: null,
  })

  let startX = 0
  let startY = 0
  let startTime = 0
  let longPressTimer: ReturnType<typeof setTimeout> | null = null
  let longPressFired = false

  // 手势判定阈值（像素）
  const THRESHOLD = 50

  /**
   * 根据滑动时长判定力度类型
   * - 慢速 > slowThresholdMs → gentle（悄悄）
   * - 快速 < fastThresholdMs  → urgent（紧急）
   * - 否则 → null（普通力度）
   */
  function detectIntensity(duration: number): GestureIntensity | null {
    if (duration > slowThresholdMs) return 'gentle'
    if (duration < fastThresholdMs) return 'urgent'
    return null
  }

  function getDirection(dx: number, dy: number): SwipeState['direction'] {
    if (Math.abs(dx) > Math.abs(dy)) {
      return dx > 0 ? 'right' : 'left'
    }
    return dy > 0 ? 'down' : 'up'
  }

  function clearLongPressTimer() {
    if (longPressTimer !== null) {
      clearTimeout(longPressTimer)
      longPressTimer = null
    }
  }

  function onPointerDown(e: PointerEvent) {
    startX = e.clientX
    startY = e.clientY
    startTime = Date.now()
    isSwiping.value = true
    isLongPressing.value = false
    longPressFired = false

    // 启动长按检测计时器
    longPressTimer = setTimeout(() => {
      if (isSwiping.value && !longPressFired) {
        longPressFired = true
        isLongPressing.value = true
        const duration = Date.now() - startTime
        const intensity: GestureIntensity = 'forceful'
        swipeState.value = {
          direction: null,
          deltaX: 0,
          deltaY: 0,
          duration,
          gestureIntensity: intensity,
        }
        onLongPress?.(swipeState.value)
      }
    }, longPressThresholdMs)
  }

  function onPointerMove(e: PointerEvent) {
    if (!isSwiping.value) return
    const deltaX = e.clientX - startX
    const deltaY = e.clientY - startY
    swipeState.value.deltaX = deltaX
    swipeState.value.deltaY = deltaY

    // 移动距离超过阈值时取消长按（用户是在滑动而非长按）
    const distance = Math.max(Math.abs(deltaX), Math.abs(deltaY))
    if (distance > THRESHOLD) {
      clearLongPressTimer()
      longPressFired = true
    }
  }

  function onPointerUp(e: PointerEvent) {
    if (!isSwiping.value) return
    clearLongPressTimer()

    const deltaX = e.clientX - startX
    const deltaY = e.clientY - startY
    const duration = Date.now() - startTime

    const absX = Math.abs(deltaX)
    const absY = Math.abs(deltaY)
    const distance = Math.max(absX, absY)
    const direction = getDirection(deltaX, deltaY)

    // 若是长按已触发，不再触发普通滑动
    if (longPressFired && isLongPressing.value && distance < THRESHOLD) {
      isSwiping.value = false
      isLongPressing.value = false
      return
    }

    // 满足阈值或快速/慢速才触发
    const intensity = detectIntensity(duration)
    const isSlow = duration > slowThresholdMs

    if ((distance > THRESHOLD || isSlow) && direction) {
      swipeState.value = {
        direction,
        deltaX,
        deltaY,
        duration,
        gestureIntensity: intensity,
      }
      onSwipe?.(swipeState.value)
    }

    isSwiping.value = false
    isLongPressing.value = false
  }

  function onPointerCancel() {
    clearLongPressTimer()
    isSwiping.value = false
    isLongPressing.value = false
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
    clearLongPressTimer()
  })

  return { isSwiping, isLongPressing, swipeState }
}
