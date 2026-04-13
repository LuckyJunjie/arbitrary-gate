<script setup lang="ts">
import { computed } from 'vue'
import type { Achievement } from '@/stores/achievementStore'

const props = withDefaults(defineProps<{
  achievement: Achievement
  size?: 'sm' | 'md' | 'lg'
}>(), {
  size: 'md',
})

const isUnlocked = computed(() => !!props.achievement.unlockedAt)

const sizeClass = computed(() => `badge--${props.size}`)
</script>

<template>
  <div
    class="achievement-badge"
    :class="[sizeClass, { 'badge--unlocked': isUnlocked, 'badge--locked': !isUnlocked }]"
    :data-testid="`achievement-badge-${achievement.id}`"
    :title="achievement.description"
  >
    <div class="badge-icon" data-testid="badge-icon">
      <span v-if="isUnlocked" class="icon-emoji">{{ achievement.icon }}</span>
      <span v-else class="icon-lock">?</span>
    </div>
    <div class="badge-body">
      <span class="badge-name" data-testid="badge-name">{{ achievement.name }}</span>
    </div>
  </div>
</template>

<style scoped>
.achievement-badge {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 0.5rem 0.3rem;
  border-radius: 8px;
  border: 1.5px solid transparent;
  min-width: 60px;
  max-width: 80px;
  cursor: default;
  transition: all 0.3s ease;
  flex-shrink: 0;
  position: relative;
  overflow: hidden;
}

/* Size variants */
.badge--sm {
  padding: 0.35rem 0.2rem;
  min-width: 50px;
  max-width: 65px;
}

.badge--md {
  padding: 0.5rem 0.3rem;
  min-width: 60px;
  max-width: 75px;
}

.badge--lg {
  padding: 0.6rem 0.4rem;
  min-width: 72px;
  max-width: 90px;
}

/* Unlocked state */
.badge--unlocked {
  background: linear-gradient(135deg, rgba(196, 168, 130, 0.25) 0%, rgba(139, 115, 85, 0.15) 100%);
  border-color: rgba(139, 115, 85, 0.4);
  animation: badge-unlock 0.5s var(--ease-bounce);
}

@keyframes badge-unlock {
  0% {
    opacity: 0;
    transform: scale(0.7);
  }
  60% {
    transform: scale(1.08);
  }
  100% {
    opacity: 1;
    transform: scale(1);
  }
}

/* Locked state */
.badge--locked {
  background: rgba(200, 190, 175, 0.3);
  border-color: rgba(150, 140, 125, 0.3);
  filter: grayscale(0.6);
  opacity: 0.65;
}

/* Icon */
.badge-icon {
  font-size: 1.5rem;
  line-height: 1;
  margin-bottom: 0.25rem;
  display: flex;
  align-items: center;
  justify-content: center;
}

.badge--sm .badge-icon { font-size: 1.2rem; }
.badge--lg .badge-icon { font-size: 1.8rem; }

.icon-emoji {
  display: block;
  filter: drop-shadow(0 1px 2px rgba(44, 31, 20, 0.2));
}

.icon-lock {
  display: block;
  font-size: 0.9em;
  opacity: 0.5;
}

/* Name */
.badge-body {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.badge-name {
  font-size: 0.65rem;
  color: #5a4530;
  text-align: center;
  line-height: 1.2;
  letter-spacing: 0.04em;
}

.badge--sm .badge-name { font-size: 0.58rem; }
.badge--lg .badge-name { font-size: 0.72rem; }

/* Tooltip via title attribute - subtle polish */
.achievement-badge::after {
  content: attr(title);
  position: absolute;
  bottom: calc(100% + 4px);
  left: 50%;
  transform: translateX(-50%);
  background: rgba(44, 31, 20, 0.88);
  color: #e8dcc8;
  font-size: 0.6rem;
  padding: 0.2rem 0.4rem;
  border-radius: 4px;
  white-space: nowrap;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.2s;
  z-index: 10;
  font-family: inherit;
}

.achievement-badge:hover::after {
  opacity: 1;
}
</style>
