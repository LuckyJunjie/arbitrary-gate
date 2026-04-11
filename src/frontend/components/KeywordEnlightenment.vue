<script setup lang="ts">
/**
 * KeywordEnlightenment.vue — S-13 关键词显灵全屏特写组件
 *
 * 触发条件：关键词共鸣值达到阈值（>= 5 次共鸣）时，
 * 全屏展示关键词卡从灰暗变彩色，并显示 AI 生成的显灵描写。
 *
 * 使用方式：
 *   <KeywordEnlightenment
 *     :enlightenment="activeEnlightenment"
 *     @close="activeEnlightenment = null"
 *   />
 *
 * 动画时序：
 *   0ms   - 浮层淡入，背景模糊
 *   0-800ms - 关键词卡从灰暗（grayscale）变彩色（grayscale(0)）
 *   200ms  - 金色外发光脉冲动画开始
 *   500ms  - 显灵文字从下方浮入
 *   5000ms - 自动关闭（或用户点击背景）
 */
import { ref, computed } from 'vue'
import type { KeywordEnlightenment } from '@/services/api'
import { vLazy } from '@/composables/useLazyLoad'

const props = defineProps<{
  enlightenment: KeywordEnlightenment | null
}>()

const emit = defineEmits<{
  close: []
}>()

// I-10: 图片懒加载 — v-lazy 指令自动处理 data-src → src
const cardImageLoaded = ref(false)
let autoCloseTimer: ReturnType<typeof setTimeout> | null = null

function scheduleAutoClose() {
  cancelAutoClose()
  autoCloseTimer = setTimeout(() => {
    emit('close')
  }, 5000)
}

function cancelAutoClose() {
  if (autoCloseTimer !== null) {
    clearTimeout(autoCloseTimer)
    autoCloseTimer = null
  }
}

// 监听 enlightenment prop 变化，自动开启计时器
watch(
  () => props.enlightenment,
  (newVal) => {
    if (newVal !== null) {
      scheduleAutoClose()
    } else {
      cancelAutoClose()
    }
  },
  { immediate: true }
)

onUnmounted(() => {
  cancelAutoClose()
})

function handleOverlayClick() {
  cancelAutoClose()
  emit('close')
}
</script>

<template>
  <Teleport to="body">
    <Transition name="enlightenment">
      <div
        v-if="enlightenment"
        class="keyword-enlightenment-overlay"
        role="dialog"
        aria-modal="true"
        aria-label="关键词显灵"
        @click="handleOverlayClick"
      >
        <!-- 金色外发光脉冲层 -->
        <div class="enlightenment-glow" />

        <!-- 粒子光点（CSS 动画模拟） -->
        <div class="enlightenment-particles">
          <span
            v-for="i in 12"
            :key="i"
            class="particle"
            :style="{ '--delay': `${(i * 0.15) % 1}s`, '--angle': `${i * 30}deg` }"
          />
        </div>

        <!-- 关键词卡 + 文字卡片 -->
        <div class="enlightenment-card" @click.stop>
          <!-- 卡片区域 -->
          <div class="enlightenment-card-image">
            <!-- I-10: 懒加载图片（v-lazy 指令自动处理 data-src → src） -->
            <img
              v-if="enlightenment.cardImageUrl"
              v-lazy
              :data-src="enlightenment.cardImageUrl"
              :alt="`「${enlightenment.cardName}」`"
              class="keyword-card-img"
              @load="cardImageLoaded = true"
            />
            <!-- 占位符：图片未加载或无图片时 -->
            <div
              v-if="!enlightenment.cardImageUrl || !cardImageLoaded"
              class="keyword-card-placeholder"
            >
              <span class="placeholder-kanji">靈</span>
            </div>
            <!-- 外层金色光晕 -->
            <div class="card-glow" />
          </div>

          <!-- 文字区域 -->
          <div class="enlightenment-text-container">
            <h2 class="enlightenment-card-name">「{{ enlightenment.cardName }}」</h2>
            <p class="enlightenment-text">{{ enlightenment.enlightenmentText }}</p>
          </div>
        </div>

        <!-- 底部提示 -->
        <div class="enlightenment-hint">点击任意处关闭</div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
/* ── 全屏浮层 ───────────────────────────────────────────────────────────── */
.keyword-enlightenment-overlay {
  position: fixed;
  inset: 0;
  z-index: 200;
  background: radial-gradient(
    ellipse at center,
    rgba(10, 8, 5, 0.92) 0%,
    rgba(20, 14, 8, 0.97) 100%
  );
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  overflow: hidden;
}

/* ── Transition ──────────────────────────────────────────────────────────── */
.enlightenment-enter-active {
  animation: enlightenment-fade-in 0.5s ease forwards;
}
.enlightenment-leave-active {
  animation: enlightenment-fade-out 0.6s ease forwards;
}

@keyframes enlightenment-fade-in {
  from { opacity: 0; }
  to   { opacity: 1; }
}
@keyframes enlightenment-fade-out {
  from { opacity: 1; }
  to   { opacity: 0; }
}

/* ── 金色外发光 ──────────────────────────────────────────────────────────── */
.enlightenment-glow {
  position: absolute;
  inset: 0;
  background: radial-gradient(
    circle at 50% 45%,
    rgba(212, 175, 55, 0.25) 0%,
    rgba(180, 140, 60, 0.08) 40%,
    transparent 70%
  );
  animation: glow-expand 2s ease-in-out infinite;
  pointer-events: none;
}

@keyframes glow-expand {
  0%, 100% { opacity: 0.7; transform: scale(1); }
  50%       { opacity: 1;   transform: scale(1.08); }
}

/* ── 粒子光点 ────────────────────────────────────────────────────────────── */
.enlightenment-particles {
  position: absolute;
  inset: 0;
  pointer-events: none;
  overflow: hidden;
}

.particle {
  position: absolute;
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: radial-gradient(circle, #d4af78 0%, transparent 70%);
  top: 50%;
  left: 50%;
  transform-origin: center;
  animation: particle-orbit 3s linear infinite;
  animation-delay: var(--delay);
  opacity: 0;
}

@keyframes particle-orbit {
  0% {
    transform: rotate(var(--angle)) translateX(0px) translateY(0px);
    opacity: 0;
  }
  20% {
    opacity: 0.8;
  }
  80% {
    opacity: 0.6;
  }
  100% {
    transform: rotate(calc(var(--angle) + 360deg)) translateX(280px) translateY(0px);
    opacity: 0;
  }
}

/* ── 主卡片容器 ──────────────────────────────────────────────────────────── */
.enlightenment-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2rem;
  animation: card-rise 1.5s cubic-bezier(0.22, 1, 0.36, 1) forwards;
  max-width: 600px;
  width: 100%;
  padding: 0 2rem;
  position: relative;
  z-index: 1;
}

@keyframes card-rise {
  from {
    opacity: 0;
    transform: translateY(40px) scale(0.95);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

/* ── 关键词卡图 ──────────────────────────────────────────────────────────── */
.enlightenment-card-image {
  position: relative;
  width: 180px;
  height: 260px;
  border-radius: 8px;
  overflow: visible;
  flex-shrink: 0;
}

.keyword-card-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 8px;
  /* S-13: 核心动画 — 灰暗 → 彩色，伴随缩放 */
  filter: grayscale(1) brightness(0.4);
  animation: card-reveal 1.8s ease forwards;
  box-shadow:
    0 0 0 rgba(212, 175, 55, 0),
    0 20px 60px rgba(0, 0, 0, 0.5);
  opacity: 0;
  transition: opacity 0.3s ease;
}

.keyword-card-img.keyword-card-img-loaded {
  opacity: 1;
}

@keyframes card-reveal {
  0% {
    filter: grayscale(1) brightness(0.4);
    transform: scale(0.9);
    box-shadow: 0 0 0 rgba(212, 175, 55, 0);
  }
  60% {
    filter: grayscale(0.3) brightness(0.8);
    transform: scale(1.05);
    box-shadow: 0 0 40px rgba(212, 175, 55, 0.4);
  }
  100% {
    filter: grayscale(0) brightness(1);
    transform: scale(1);
    box-shadow:
      0 0 60px rgba(212, 175, 55, 0.7),
      0 20px 60px rgba(0, 0, 0, 0.5);
  }
}

/* 纯色占位卡片（无图片时） */
.keyword-card-placeholder {
  width: 100%;
  height: 100%;
  border-radius: 8px;
  background: linear-gradient(145deg, #2a2018, #1a1510);
  border: 1px solid rgba(212, 175, 55, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  animation: card-reveal 1.8s ease forwards;
  box-shadow:
    0 0 40px rgba(212, 175, 55, 0.3),
    0 20px 60px rgba(0, 0, 0, 0.5);
}

.placeholder-kanji {
  font-size: 4rem;
  color: rgba(212, 175, 55, 0.5);
  font-family: 'FZQingKeBenYueSong', serif;
  animation: card-reveal 1.8s ease forwards;
}

/* 卡片外层光晕 */
.card-glow {
  position: absolute;
  inset: -30%;
  background: radial-gradient(
    ellipse at center,
    rgba(212, 175, 120, 0.35) 0%,
    rgba(180, 140, 80, 0.1) 40%,
    transparent 70%
  );
  animation: glow-pulse 2s ease-in-out infinite;
  pointer-events: none;
  z-index: -1;
  border-radius: 50%;
}

@keyframes glow-pulse {
  0%, 100% { opacity: 0.6; transform: scale(1); }
  50%       { opacity: 1;   transform: scale(1.1); }
}

/* ── 文字区域 ────────────────────────────────────────────────────────────── */
.enlightenment-text-container {
  text-align: center;
  animation: text-rise 1.2s ease 0.5s both;
}

@keyframes text-rise {
  from {
    opacity: 0;
    transform: translateY(30px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.enlightenment-card-name {
  font-size: 1.8rem;
  font-weight: 700;
  color: #d4af78;
  letter-spacing: 0.15em;
  margin: 0 0 1rem;
  text-shadow:
    0 0 20px rgba(212, 175, 55, 0.6),
    0 2px 8px rgba(0, 0, 0, 0.5);
  font-family: 'FZQingKeBenYueSong', 'Noto Serif SC', serif;
}

.enlightenment-text {
  font-size: 1.05rem;
  line-height: 2;
  color: #e8dcc8;
  letter-spacing: 0.08em;
  font-style: italic;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.4);
  max-width: 480px;
  font-family: 'FZQingKeBenYueSong', 'Noto Serif SC', serif;
}

/* ── 底部提示 ────────────────────────────────────────────────────────────── */
.enlightenment-hint {
  position: absolute;
  bottom: 2rem;
  font-size: 0.72rem;
  color: rgba(139, 115, 85, 0.5);
  letter-spacing: 0.15em;
  animation: hint-fade 1s ease 2s both;
  pointer-events: none;
}

@keyframes hint-fade {
  from { opacity: 0; }
  to   { opacity: 1; }
}
</style>
