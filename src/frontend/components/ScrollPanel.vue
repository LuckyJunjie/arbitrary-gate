<script setup lang="ts">
import { ref } from 'vue'
import { useSwipe } from '@/composables/useGesture'

// TODO: 手势滑动 + 惯性滚动
// TODO: 竖向排版（writing-mode: vertical-rl）
</script>

<template>
  <div class="scroll-panel" ref="panelRef">
    <div class="scroll-content">
      <slot />
    </div>

    <!-- 上下卷轴装饰 -->
    <div class="scroll-rail rail-top"></div>
    <div class="scroll-rail rail-bottom"></div>
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

/* 卷轴上下边缘装饰 */
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
</style>
