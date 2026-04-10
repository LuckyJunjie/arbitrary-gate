/**
 * useWindowLight.ts - 窗格光影 Composable
 *
 * 根据当前时间切换书房背景光影效果：
 * - 6-11 点：黛青冷调（清晨）
 * - 12-16 点：无光影（正午）
 * - 17-19 点：赭石暖调（黄昏）
 * - 20-5 点：烛光 flicker（夜间）
 *
 * @see docs/opus检查结果1-具体实现设计.md - UI-06
 */
import { ref, onMounted, onUnmounted } from 'vue'

export type LightPhase = 'cold' | 'none' | 'warm' | 'candle'

/** 窗格光影状态 */
export function useWindowLight() {
  const lightPhase = ref<LightPhase | null>(null)
  let timer: ReturnType<typeof setInterval> | null = null

  /** 根据小时数计算光影阶段 */
  function getLightPhase(hour: number): LightPhase | null {
    if (hour >= 6 && hour <= 11) return 'cold'
    if (hour >= 12 && hour <= 16) return 'none'
    if (hour >= 17 && hour <= 19) return 'warm'
    return 'candle'
  }

  /** 更新光影阶段（整点自动刷新） */
  function update() {
    lightPhase.value = getLightPhase(new Date().getHours())
  }

  onMounted(() => {
    update()
    // 每分钟检查一次（应对跨整点场景）
    timer = setInterval(update, 60 * 1000)
  })

  onUnmounted(() => {
    if (timer !== null) {
      clearInterval(timer)
      timer = null
    }
  })

  return { lightPhase }
}
