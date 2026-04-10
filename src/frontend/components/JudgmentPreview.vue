<script setup lang="ts">
import { ref, watch } from 'vue'

const props = defineProps<{
  judgment: string
  visible: boolean
}>()

const emit = defineEmits<{
  confirm: []
  cancel: []
}>()

const isAnimating = ref(false)

watch(() => props.visible, (val) => {
  if (val) {
    // 触发入场动画
    isAnimating.value = false
    requestAnimationFrame(() => {
      requestAnimationFrame(() => {
        isAnimating.value = true
      })
    })
  }
})

function handleConfirm() {
  emit('confirm')
}

function handleCancel() {
  emit('cancel')
}
</script>

<template>
  <Teleport to="body">
    <Transition name="judgment-fade">
      <div v-if="visible" class="judgment-overlay" data-testid="judgment-overlay">
        <div class="judgment-scrim" @click="handleCancel" />

        <div class="judgment-panel" :class="{ entered: isAnimating }">
          <!-- 装饰线 -->
          <div class="deco-line top" />
          <div class="deco-line bottom" />

          <!-- 判词标题 -->
          <div class="judgment-label">判</div>

          <!-- 竖排判词 -->
          <div class="judgment-text-wrapper">
            <p class="judgment-text" data-testid="judgment-text">{{ judgment }}</p>
          </div>

          <!-- 底部操作 -->
          <div class="judgment-actions">
            <button class="cancel-btn" data-testid="judgment-cancel-btn" @click="handleCancel">
              返回修改
            </button>
            <button class="confirm-btn" data-testid="judgment-confirm-btn" @click="handleConfirm">
              入局
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.judgment-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem;
}

.judgment-scrim {
  position: absolute;
  inset: 0;
  background: rgba(13, 10, 8, 0.85);
  backdrop-filter: blur(4px);
  -webkit-backdrop-filter: blur(4px);
}

.judgment-panel {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1.5rem;
  padding: 3rem 2.5rem 2.5rem;
  max-width: 340px;
  width: 100%;
  background: linear-gradient(160deg, rgba(44, 36, 28, 0.98), rgba(30, 24, 18, 0.99));
  border: 1px solid rgba(180, 120, 60, 0.4);
  border-radius: 6px;
  box-shadow:
    0 0 40px rgba(180, 120, 60, 0.15),
    0 20px 60px rgba(0, 0, 0, 0.6);
  opacity: 0;
  transform: scale(0.92) translateY(16px);
  transition: opacity 0.5s ease, transform 0.5s ease;
}

.judgment-panel.entered {
  opacity: 1;
  transform: scale(1) translateY(0);
}

/* 装饰线 */
.deco-line {
  position: absolute;
  left: 1.5rem;
  right: 1.5rem;
  height: 1px;
  background: linear-gradient(
    90deg,
    transparent 0%,
    rgba(180, 120, 60, 0.5) 30%,
    rgba(180, 120, 60, 0.5) 70%,
    transparent 100%
  );
}

.deco-line.top {
  top: 1.5rem;
}

.deco-line.bottom {
  bottom: 1.5rem;
}

/* 判字标签 */
.judgment-label {
  font-size: 0.65rem;
  letter-spacing: 0.4em;
  color: rgba(180, 120, 60, 0.6);
  text-transform: uppercase;
  text-align: center;
}

/* 竖排判词区 */
.judgment-text-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 120px;
  padding: 1rem 0;
}

.judgment-text {
  writing-mode: vertical-rl;
  text-orientation: upright;
  direction: ltr;
  font-family: '方正清刻本悦宋', 'FZQingKeBenYueSong', 'STKaiti', 'KaiTi', 'SimKai', serif;
  font-size: 1.5rem;
  color: #b46030; /* 赭石色 */
  letter-spacing: 0.3em;
  line-height: 2.2;
  margin: 0;
  text-align: center;
  /* 墨迹质感 */
  text-shadow: 0 0 8px rgba(180, 96, 48, 0.3);
}

/* 操作按钮 */
.judgment-actions {
  display: flex;
  gap: 1rem;
  width: 100%;
  justify-content: center;
}

.cancel-btn {
  flex: 1;
  max-width: 120px;
  padding: 0.65rem 1rem;
  background: transparent;
  border: 1px solid rgba(139, 115, 85, 0.4);
  border-radius: 3px;
  color: rgba(232, 220, 200, 0.6);
  font-family: inherit;
  font-size: 0.85rem;
  cursor: pointer;
  letter-spacing: 0.1em;
  transition: all 0.25s;
}

.cancel-btn:hover {
  border-color: rgba(139, 115, 85, 0.7);
  color: rgba(232, 220, 200, 0.85);
}

.confirm-btn {
  flex: 1;
  max-width: 120px;
  padding: 0.65rem 1rem;
  background: linear-gradient(135deg, #4a2e18, #2c1a0e);
  border: 1px solid rgba(180, 120, 60, 0.6);
  border-radius: 3px;
  color: #c9a84c;
  font-family: inherit;
  font-size: 0.9rem;
  cursor: pointer;
  letter-spacing: 0.2em;
  transition: all 0.25s;
}

.confirm-btn:hover {
  background: linear-gradient(135deg, #6b4520, #4a2e18);
  border-color: rgba(201, 168, 76, 0.8);
  color: #e8dcc8;
  box-shadow: 0 0 12px rgba(201, 168, 76, 0.2);
}

/* Transition */
.judgment-fade-enter-active,
.judgment-fade-leave-active {
  transition: opacity 0.4s ease;
}

.judgment-fade-enter-from,
.judgment-fade-leave-to {
  opacity: 0;
}
</style>
