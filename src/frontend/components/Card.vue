<script setup lang="ts">
import { ref, computed } from 'vue'
import type { KeywordCard } from '@/services/api'
import { hapticLight } from '@/composables/useHaptic'
import { getInkBleedLevel } from '@/stores/inkValueStore'
import { vLazy } from '@/composables/useLazyLoad'

const props = defineProps<{
  card: KeywordCard | Record<string, unknown> | null
  /** K-07 墨迹晕染等级（0-3），默认由卡牌 inkFragrance 计算得出 */
  inkBleedLevel?: 0 | 1 | 2 | 3
  /** S-13 关键词显灵状态：当卡牌共鸣值达到5次以上时为 true，卡面变为彩色+金色光晕 */
  enlightened?: boolean
}>()

const emit = defineEmits<{
  flip: []
}>()

const isFlipped = ref(false)

// I-10: 图片懒加载 — v-lazy 指令自动处理 data-src → src 替换
const artImageLoaded = ref(false)

function handleClick() {
  if (!isFlipped.value) {
    isFlipped.value = true
    hapticLight() // UI-11: 触感反馈 - 卡片翻转
  } else {
    emit('flip')
  }
}

// 稀有度配置
const rarityConfig: Record<number, { label: string; borderColor: string; bgGradient: string }> = {
  1: { label: '凡', borderColor: '#8b7355', bgGradient: 'linear-gradient(135deg, #2c1f14 0%, #3a2a1a 100%)' },
  2: { label: '珍', borderColor: '#6b8e6b', bgGradient: 'linear-gradient(135deg, #1a2a1a 0%, #2a3a2a 100%)' },
  3: { label: '奇', borderColor: '#7b6ba0', bgGradient: 'linear-gradient(135deg, #1f1a2a 0%, #2a253a 100%)' },
  4: { label: '绝', borderColor: '#c9a84c', bgGradient: 'linear-gradient(135deg, #2a2010 0%, #3a3020 100%)' },
}

// 分类配置
const categoryConfig: Record<number, string> = {
  1: '器物',
  2: '职人',
  3: '风物',
  4: '情绪',
  5: '称谓',
}

const rarity = computed(() => (props.card as KeywordCard)?.rarity ?? 1)
const category = computed(() => (props.card as KeywordCard)?.category ?? 0)
const inkFragrance = computed(() => (props.card as KeywordCard)?.inkFragrance ?? 0)
const resonanceCount = computed(() => (props.card as KeywordCard)?.resonanceCount ?? 0)

// K-07 墨迹晕染等级（0-3），墨香值越高晕染越浓
// 优先使用 props.inkBleedLevel，否则由卡牌 inkFragrance 计算
const inkBleedLevel = computed(() => {
  if (props.inkBleedLevel !== undefined) return props.inkBleedLevel
  return getInkBleedLevel(inkFragrance.value)
})

// C-11 墨香状态（浓/淡/将尽），基于墨香值
const inkFragranceStatus = computed(() => {
  const val = inkFragrance.value
  if (val >= 5) return '浓'
  if (val >= 2) return '淡'
  return '将尽'
})

</script>

<template>
  <div
    class="card"
    :class="[`rarity-${rarity}`, `ink-bleed-${inkBleedLevel}`, { flipped: isFlipped, 'keyword-enlightened': enlightened }]"
    @click="handleClick"
    role="button"
    tabindex="0"
    @keydown.enter="handleClick"
  >
    <!-- 卡片背面（翻转前显示） -->
    <div class="card-back">
      <div class="back-pattern">
        <div class="back-seal">笺</div>
        <span class="back-title">时光笺</span>
      </div>
      <div class="back-border"></div>
    </div>

    <!-- 卡片正面 -->
    <div class="card-front">
      <!-- 卡面装饰边框 -->
      <div class="card-inner-border" :style="{ borderColor: rarityConfig[rarity].borderColor }"></div>

      <!-- 卡面图案区 -->
      <div class="card-art" :style="{ borderColor: rarityConfig[rarity].borderColor }">
        <!-- I-10: 懒加载图片（v-lazy 指令自动处理 data-src → src） -->
        <img
          v-if="(card as any)?.imageUrl"
          v-lazy
          :data-src="(card as any).imageUrl"
          class="art-image"
          alt=""
          @load="artImageLoaded = true"
        />
        <!-- 占位符：图片未加载时或无 AI 图时显示首字 -->
        <div
          v-if="!(card as any)?.imageUrl || !artImageLoaded"
          class="art-placeholder"
          :style="{ color: rarityConfig[rarity].borderColor }"
        >
          {{ (card?.name as string)?.charAt(0) ?? '?' }}
        </div>
        <!-- 稀有度光效 -->
        <div v-if="rarity >= 3" class="rarity-glow" :class="`glow-${rarity}`"></div>
      </div>

      <!-- 卡名 -->
      <div class="card-name" data-testid="card-name" :style="{ color: rarityConfig[rarity].borderColor }">
        {{ (card?.name as string) ?? '未知' }}
      </div>

      <!-- 稀有度 + 分类标签 -->
      <div class="card-meta" data-testid="card-rarity">
        <span
          class="meta-tag rarity-tag"
          data-testid="card-rarity-badge"
          :style="{ borderColor: rarityConfig[rarity].borderColor, color: rarityConfig[rarity].borderColor }"
        >
          {{ rarityConfig[rarity].label }}
        </span>
        <span v-if="category" class="meta-tag category-tag">
          {{ categoryConfig[category] ?? '' }}
        </span>
      </div>

      <!-- 墨香值显示 (0-7) -->
      <div class="ink-fragrance">
        <span class="fragrance-label">墨香</span>
        <div class="fragrance-dots">
          <span
            v-for="i in 7"
            :key="i"
            class="fragrance-dot"
            :class="{ filled: i <= inkFragrance, 'ink-fade': i <= inkFragrance }"
            :style="i <= inkFragrance
              ? { background: rarityConfig[rarity].borderColor, animationDelay: ((i - 1) * 0.5) + 's' }
              : {}"
          ></span>
        </div>
        <!-- C-11 墨香状态文字（浓/淡/将尽） -->
        <span class="fragrance-status" :class="`status-${inkFragranceStatus}`">
          {{ inkFragranceStatus }}
        </span>
      </div>

      <!-- K-06 累计共鸣次数显示 -->
      <div v-if="resonanceCount > 0" class="resonance-count">
        <span class="resonance-text">共鸣 ×{{ resonanceCount }}</span>
      </div>

      <!-- 翻转提示 -->
      <div class="flip-hint">点击卡面翻转</div>
    </div>
  </div>
</template>

<style scoped>
.card {
  width: 180px;
  aspect-ratio: 3 / 4;
  perspective: 1000px;
  cursor: pointer;
  position: relative;
}

.card-back,
.card-front {
  position: absolute;
  inset: 0;
  border-radius: 6px;
  backface-visibility: hidden;
  transition: transform 0.7s var(--ease-smooth);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

/* 背面样式 */
.card-back {
  background: linear-gradient(135deg, #2c1f14 0%, #4a3520 50%, #2c1f14 100%);
  border: 2px solid #8b7355;
  box-shadow:
    inset 0 0 30px rgba(0, 0, 0, 0.4),
    0 8px 32px rgba(0, 0, 0, 0.5);
}

.back-pattern {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
}

.back-seal {
  width: 60px;
  height: 60px;
  border: 2px solid #8b7355;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.8rem;
  color: #c9a84c;
  opacity: 0.7;
  background: rgba(0, 0, 0, 0.2);
}

.back-title {
  font-size: 1rem;
  color: #8b7355;
  letter-spacing: 0.3em;
  opacity: 0.5;
}

.back-border {
  position: absolute;
  inset: 8px;
  border: 1px solid rgba(139, 115, 85, 0.3);
  border-radius: 4px;
  pointer-events: none;
}

/* 正面样式 */
.card-front {
  transform: rotateY(180deg);
  padding: 0.75rem;
  gap: 0.4rem;
}

.card-inner-border {
  position: absolute;
  inset: 4px;
  border: 1px solid;
  border-radius: 4px;
  pointer-events: none;
  opacity: 0.4;
}

/* 翻转动画 */
.card.flipped .card-back {
  transform: rotateY(180deg);
}
.card.flipped .card-front {
  transform: rotateY(0deg);
}

/* 稀有度边框颜色（应用于整个卡背） */
.card.rarity-1 .card-back { border-color: #8b7355; }
.card.rarity-2 .card-back { border-color: #6b8e6b; }
.card.rarity-3 .card-back { border-color: #7b6ba0; }
.card.rarity-4 .card-back { border-color: #c9a84c; box-shadow: inset 0 0 30px rgba(201, 168, 76, 0.2), 0 8px 32px rgba(0, 0, 0, 0.5), 0 0 20px rgba(201, 168, 76, 0.15); }

/* 卡面图案区 */
.card-art {
  width: 100%;
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.15);
  border-radius: 3px;
  border: 1px solid;
  position: relative;
  overflow: hidden;
  min-height: 80px;
}

.art-placeholder {
  font-size: 3rem;
  font-weight: 700;
  opacity: 0.8;
  position: relative;
  z-index: 1;
}

.art-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 3px;
  opacity: 0;
  transition: opacity 0.4s ease;
}

.art-image.art-image-loaded {
  opacity: 1;
}

.rarity-glow {
  position: absolute;
  inset: 0;
  border-radius: 3px;
  pointer-events: none;
}

.glow-3 {
  background: radial-gradient(ellipse at center, rgba(123, 107, 160, 0.3) 0%, transparent 70%);
}

.glow-4 {
  background: radial-gradient(ellipse at center, rgba(201, 168, 76, 0.4) 0%, transparent 70%);
  animation: goldenPulse 2s var(--ease-loop) infinite;
}

@keyframes goldenPulse {
  0%, 100% { opacity: 0.6; }
  50% { opacity: 1; }
}

/* 卡名 */
.card-name {
  font-size: 1.1rem;
  font-weight: 700;
  text-align: center;
  letter-spacing: 0.1em;
  margin-top: 0.25rem;
}

/* 标签行 */
.card-meta {
  display: flex;
  gap: 0.4rem;
  flex-wrap: wrap;
  justify-content: center;
}

.meta-tag {
  font-size: 0.65rem;
  padding: 0.15rem 0.5rem;
  border-radius: 2px;
  border: 1px solid;
  letter-spacing: 0.1em;
}

.rarity-tag {
  background: rgba(0, 0, 0, 0.2);
}

.category-tag {
  border-color: rgba(139, 115, 85, 0.5);
  color: #a08060;
  background: rgba(0, 0, 0, 0.1);
}

/* 墨香值 */
.ink-fragrance {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  margin-top: 0.3rem;
}

.fragrance-label {
  font-size: 0.6rem;
  color: rgba(232, 220, 200, 0.4);
  letter-spacing: 0.1em;
}

.fragrance-dots {
  display: flex;
  gap: 3px;
}

.fragrance-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  border: 1px solid rgba(232, 220, 200, 0.3);
  background: transparent;
  transition: background 0.3s ease;
}

.fragrance-dot.filled {
  border-color: transparent;
  animation: inkFade 4s var(--ease-loop) infinite both;
}

/* 墨香星级渐淡动画 */
@keyframes inkFade {
  0%   { opacity: 0.8; }
  50%  { opacity: 0.4; }
  100% { opacity: 0.8; }
}

/* C-11 墨香状态文字 */
.fragrance-status {
  font-size: 0.55rem;
  padding: 0.05rem 0.3rem;
  border-radius: 2px;
  border: 1px solid;
  letter-spacing: 0.08em;
  margin-left: 0.2rem;
  flex-shrink: 0;
}

.fragrance-status.status-浓 {
  color: #FFD700;
  border-color: rgba(255, 215, 0, 0.5);
  background: rgba(255, 215, 0, 0.08);
}

.fragrance-status.status-淡 {
  color: #8b7355;
  border-color: rgba(139, 115, 85, 0.4);
  background: rgba(139, 115, 85, 0.08);
}

.fragrance-status.status-将尽 {
  color: #a07050;
  border-color: rgba(160, 112, 80, 0.4);
  background: rgba(160, 112, 80, 0.08);
}

/* K-06 累计共鸣次数 - 赭石色小字 */
.resonance-count {
  margin-top: 0.3rem;
  text-align: center;
}

.resonance-text {
  font-size: 0.6rem;
  color: #a07050; /* 赭石色 */
  letter-spacing: 0.1em;
}

/* 翻转提示 */
.flip-hint {
  font-size: 0.55rem;
  color: rgba(232, 220, 200, 0.25);
  margin-top: 0.3rem;
  letter-spacing: 0.05em;
}

/* K-07 卡片边缘墨迹晕染效果（class-based, 0-3级）
 * 墨迹浓度由 inkBleedLevel 决定：
 * - ink-bleed-3：浓重墨迹（inkFragrance 5-7）
 * - ink-bleed-2：中等墨迹（inkFragrance 3-4）
 * - ink-bleed-1：淡墨迹（inkFragrance 1-2）
 * - ink-bleed-0：无墨迹
 */

/* 3级：浓重墨迹 */
.card.ink-bleed-3 {
  box-shadow:
    10px 7px 14px rgba(30, 20, 10, 0.4),
    3px 3px 0 rgba(30, 20, 10, 0.25),
    0 4px 12px rgba(0, 0, 0, 0.45),
    inset 0 0 12px rgba(30, 20, 10, 0.08);
  transition: box-shadow 0.6s ease;
}

/* 2级：中等墨迹 */
.card.ink-bleed-2 {
  box-shadow:
    7px 5px 10px rgba(30, 20, 10, 0.28),
    2px 2px 0 rgba(30, 20, 10, 0.18),
    0 4px 10px rgba(0, 0, 0, 0.4);
  transition: box-shadow 0.6s ease;
}

/* 1级：淡墨迹 */
.card.ink-bleed-1 {
  box-shadow:
    3px 2px 5px rgba(30, 20, 10, 0.15),
    0 3px 6px rgba(0, 0, 0, 0.3);
  transition: box-shadow 0.6s ease;
}

/* 0级：无墨迹 */
.card.ink-bleed-0 {
  box-shadow:
    0 2px 6px rgba(0, 0, 0, 0.3),
    0 1px 3px rgba(0, 0, 0, 0.2);
  transition: box-shadow 0.6s ease;
}

/* 稀有度 3-4 的特殊墨染：浓墨时叠加金色光晕 */
.card.rarity-3.ink-bleed-3,
.card.rarity-4.ink-bleed-3 {
  box-shadow:
    10px 7px 14px rgba(30, 20, 10, 0.4),
    3px 3px 0 rgba(30, 20, 10, 0.25),
    0 4px 12px rgba(0, 0, 0, 0.45),
    inset 0 0 12px rgba(30, 20, 10, 0.08),
    0 0 18px rgba(201, 168, 76, 0.2);
}

.card.rarity-3.ink-bleed-2,
.card.rarity-4.ink-bleed-2 {
  box-shadow:
    7px 5px 10px rgba(30, 20, 10, 0.28),
    2px 2px 0 rgba(30, 20, 10, 0.18),
    0 4px 10px rgba(0, 0, 0, 0.4),
    0 0 12px rgba(201, 168, 76, 0.12);
}

/* S-13 关键词显灵样式：共鸣值 >= 5 时，卡面从黑白变为彩色+金色光晕 */
.card.keyword-enlightened {
  filter: saturate(1.5);
  animation: enlightened-glow 2s var(--ease-loop) infinite;
}

.card.keyword-enlightened .card-back {
  border-color: #d4af78;
  box-shadow:
    inset 0 0 30px rgba(0, 0, 0, 0.3),
    0 8px 32px rgba(0, 0, 0, 0.4),
    0 0 20px rgba(212, 175, 55, 0.4),
    0 0 40px rgba(212, 175, 55, 0.2);
}

.card.keyword-enlightened .card-front {
  background: radial-gradient(
    ellipse at 30% 20%,
    rgba(212, 175, 55, 0.12) 0%,
    transparent 50%
  ),
  linear-gradient(135deg, #2c1f14 0%, #3a2a1a 100%);
}

/* 显灵时卡面上的金色微光 */
.card.keyword-enlightened::after {
  content: '';
  position: absolute;
  inset: -2px;
  border-radius: 8px;
  background: radial-gradient(
    ellipse at 50% 50%,
    rgba(212, 175, 55, 0.15) 0%,
    transparent 70%
  );
  pointer-events: none;
  z-index: 10;
  animation: enlightened-aura 2s var(--ease-loop) infinite;
}

@keyframes enlightened-glow {
  0%, 100% {
    box-shadow:
      0 0 20px rgba(212, 175, 55, 0.4),
      0 0 40px rgba(212, 175, 55, 0.2),
      0 8px 32px rgba(0, 0, 0, 0.5);
  }
  50% {
    box-shadow:
      0 0 30px rgba(212, 175, 55, 0.6),
      0 0 60px rgba(212, 175, 55, 0.3),
      0 8px 32px rgba(0, 0, 0, 0.5);
  }
}

@keyframes enlightened-aura {
  0%, 100% { opacity: 0.6; }
  50% { opacity: 1; }
}
</style>
