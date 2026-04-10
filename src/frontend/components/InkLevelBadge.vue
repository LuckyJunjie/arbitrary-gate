<script setup lang="ts">
import { computed } from 'vue'
import { useInkValueStore } from '@/stores/inkValueStore'

const inkStore = useInkValueStore()

const progressPercent = computed(() => Math.round(inkStore.levelProgress * 100))

const nextLvName = computed(() => inkStore.nextLevel?.name ?? null)
const nextLvPoints = computed(() => inkStore.pointsToNextLevel ?? null)
const currentLv = computed(() => inkStore.currentLevel)

// 进度条背景色渐变（从深到浅的同色系）
const progressStyle = computed(() => {
  const color = currentLv.value.color
  return {
    width: `${progressPercent.value}%`,
    background: `linear-gradient(90deg, ${color}cc 0%, ${color} 100%)`,
    boxShadow: `0 0 8px ${color}66`,
  }
})
</script>

<template>
  <div class="ink-level-badge" :style="{ '--lv-color': currentLv.color }">
    <!-- 等级标题 -->
    <div class="badge-header">
      <span class="lv-label">墨香</span>
      <span class="lv-name" :style="{ color: currentLv.color }">
        {{ currentLv.name }}
      </span>
      <span class="lv-level">Lv.{{ currentLv.level }}</span>
    </div>

    <!-- 进度条 -->
    <div class="progress-track">
      <div class="progress-fill" :style="progressStyle"></div>
    </div>

    <!-- 数值显示 -->
    <div class="badge-footer">
      <span class="total-points">{{ inkStore.totalPoints.toLocaleString() }}</span>
      <span v-if="nextLvName && nextLvPoints !== null" class="next-hint">
        距 {{ nextLvName }} 还差 {{ nextLvPoints }} 点
      </span>
      <span v-else-if="!nextLvName" class="next-hint max-level">
        已达巅峰
      </span>
    </div>
  </div>
</template>

<style scoped>
.ink-level-badge {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  padding: 0.6rem 0.9rem;
  border: 1px solid var(--lv-color, #8b7355);
  border-radius: 4px;
  background: rgba(0, 0, 0, 0.3);
  min-width: 160px;
}

.badge-header {
  display: flex;
  align-items: baseline;
  gap: 0.4rem;
}

.lv-label {
  font-size: 0.65rem;
  color: rgba(232, 220, 200, 0.5);
  letter-spacing: 0.15em;
}

.lv-name {
  font-size: 0.95rem;
  font-weight: 700;
  letter-spacing: 0.1em;
  flex: 1;
}

.lv-level {
  font-size: 0.65rem;
  color: rgba(232, 220, 200, 0.4);
  letter-spacing: 0.05em;
}

.progress-track {
  height: 4px;
  border-radius: 2px;
  background: rgba(255, 255, 255, 0.1);
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  border-radius: 2px;
  transition: width 0.6s ease;
}

.badge-footer {
  display: flex;
  align-items: baseline;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.total-points {
  font-size: 0.85rem;
  color: #e8dcc8;
  font-family: monospace;
  font-weight: 600;
}

.next-hint {
  font-size: 0.65rem;
  color: rgba(232, 220, 200, 0.4);
}

.next-hint.max-level {
  color: #FFD700;
  opacity: 0.8;
}
</style>
