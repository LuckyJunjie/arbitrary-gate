<script setup lang="ts">
import { ref } from 'vue'
import { useCardDraw } from '@/composables/useCardDraw'
import { hapticLight } from '@/composables/useHaptic'

const emit = defineEmits<{
  draw: [card: Record<string, unknown>]
}>()

const { drawCard, isDrawing } = useCardDraw()
const poolRef = ref<HTMLDivElement | null>(null)

async function handleDraw() {
  if (isDrawing.value) return
  hapticLight() // UI-11: 触感反馈
  const card = await drawCard('keyword')
  // 即使 API 失败也 emit，让 PoolView 决定如何处理（使用 fallback 数据）
  emit('draw', card ?? null)
}
</script>

<template>
  <div
    ref="poolRef"
    class="ink-pool"
    data-testid="ink-pool-surface"
    @click="handleDraw"
    role="button"
    :aria-disabled="isDrawing"
    tabindex="0"
    @keydown.enter="handleDraw"
  >
    <!-- 墨池底纹 -->
    <div class="pool-surface">
      <div class="ink-base"></div>

      <!-- 多层涟漪动画 -->
      <div class="ripple ripple-1"></div>
      <div class="ripple ripple-2"></div>
      <div class="ripple ripple-3"></div>

      <!-- 墨点漂浮 -->
      <div class="ink-dot dot-1"></div>
      <div class="ink-dot dot-2"></div>
      <div class="ink-dot dot-3"></div>
    </div>

    <!-- 中心提示文字 -->
    <div class="pool-hint" :class="{ drawing: isDrawing }">
      <span v-if="!isDrawing">轻触墨池 · 抽取关键词</span>
      <span v-else>墨迹涌动中...</span>
    </div>
  </div>
</template>

<style scoped>
.ink-pool {
  width: 280px;
  height: 280px;
  border-radius: 50%;
  cursor: pointer;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  user-select: none;
}

.ink-pool:active {
  cursor: wait;
}

.pool-surface {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  overflow: hidden;
}

/* 墨池底色 */
.ink-base {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: radial-gradient(
    ellipse at 40% 40%,
    #2a1f14 0%,
    #1a1208 50%,
    #0d0a05 100%
  );
  box-shadow:
    inset 0 4px 20px rgba(0, 0, 0, 0.6),
    0 0 40px rgba(42, 31, 20, 0.4);
}

/* ===== 涟漪动画 ===== */
.ripple {
  position: absolute;
  border-radius: 50%;
  border: 1px solid rgba(232, 220, 200, 0.25);
  animation: rippleExpand 3s ease-out infinite;
  pointer-events: none;
}

.ripple-1 {
  inset: 20%;
  animation-delay: 0s;
}

.ripple-2 {
  inset: 30%;
  animation-delay: 1s;
}

.ripple-3 {
  inset: 15%;
  animation-delay: 2s;
}

@keyframes rippleExpand {
  0% {
    transform: scale(0.85);
    opacity: 0.6;
    border-color: rgba(232, 220, 200, 0.4);
  }
  100% {
    transform: scale(1.3);
    opacity: 0;
    border-color: rgba(232, 220, 200, 0);
  }
}

/* ===== 漂浮墨点 ===== */
.ink-dot {
  position: absolute;
  border-radius: 50%;
  background: rgba(90, 70, 40, 0.5);
  animation: float 4s ease-in-out infinite;
  pointer-events: none;
}

.dot-1 {
  width: 6px;
  height: 6px;
  top: 30%;
  left: 25%;
  animation-delay: 0s;
}

.dot-2 {
  width: 4px;
  height: 4px;
  top: 55%;
  left: 65%;
  animation-delay: 1.5s;
}

.dot-3 {
  width: 5px;
  height: 5px;
  top: 70%;
  left: 40%;
  animation-delay: 3s;
}

@keyframes float {
  0%, 100% {
    transform: translateY(0) scale(1);
    opacity: 0.5;
  }
  50% {
    transform: translateY(-8px) scale(1.1);
    opacity: 0.8;
  }
}

/* ===== 提示文字 ===== */
.pool-hint {
  position: relative;
  z-index: 10;
  font-size: 0.9rem;
  color: rgba(232, 220, 200, 0.6);
  letter-spacing: 0.15em;
  text-align: center;
  transition: opacity 0.3s ease;
  pointer-events: none;
}

.pool-hint.drawing {
  opacity: 0.5;
}
</style>
