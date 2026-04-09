<script setup lang="ts">
import { ref } from 'vue'
import { updateStoryTitle } from '@/services/api'

const props = defineProps<{
  storyId: string
  titles: string[]
}>()

const emit = defineEmits<{
  selected: [title: string]
  close: []
}>()

const selectedTitle = ref<string | null>(null)
const isSubmitting = ref(false)
const errorMsg = ref<string | null>(null)

async function confirmSelection() {
  if (!selectedTitle.value) {
    errorMsg.value = '请先选择一个标题'
    return
  }
  isSubmitting.value = true
  errorMsg.value = null
  try {
    await updateStoryTitle(props.storyId, selectedTitle.value)
    emit('selected', selectedTitle.value)
  } catch (err) {
    console.error('[TitleSelectModal] updateStoryTitle failed:', err)
    errorMsg.value = '标题保存失败，请重试'
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <div class="title-modal-overlay" @click.self="emit('close')">
    <div class="title-modal" role="dialog" aria-modal="true" aria-labelledby="title-modal-title">
      <!-- 标题区域 -->
      <div class="modal-header">
        <span class="modal-label">卷</span>
        <h2 id="title-modal-title" class="modal-title">请为此卷题名</h2>
        <span class="modal-label">完</span>
      </div>

      <!-- 三个备选标题 -->
      <div class="title-list">
        <button
          v-for="(title, idx) in titles"
          :key="idx"
          class="title-option"
          :class="{ selected: selectedTitle === title }"
          @click="selectedTitle = title"
          :data-testid="`title-option-${idx}`"
        >
          <span class="option-index">{{ idx + 1 }}</span>
          <span class="option-text">{{ title }}</span>
          <span v-if="selectedTitle === title" class="option-check">✓</span>
        </button>
      </div>

      <!-- 确认按钮 -->
      <div class="modal-footer">
        <p v-if="errorMsg" class="error-text">{{ errorMsg }}</p>
        <button
          class="confirm-btn"
          :disabled="!selectedTitle || isSubmitting"
          @click="confirmSelection"
          data-testid="title-confirm-btn"
        >
          <span v-if="isSubmitting">正在题名...</span>
          <span v-else>确认题名</span>
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.title-modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(44, 44, 42, 0.6);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  animation: overlay-in 0.3s ease;
}

@keyframes overlay-in {
  from { opacity: 0; }
  to { opacity: 1; }
}

.title-modal {
  background: #f5f0e6;
  border: 2px solid rgba(139, 94, 60, 0.4);
  border-radius: 4px;
  width: min(480px, 90vw);
  box-shadow: 0 8px 32px rgba(44, 44, 42, 0.3);
  animation: modal-in 0.3s ease;
}

@keyframes modal-in {
  from {
    opacity: 0;
    transform: translateY(16px) scale(0.97);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

/* ── 顶部卷轴装饰 ── */
.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.8rem 1.2rem;
  border-bottom: 2px solid rgba(139, 94, 60, 0.3);
  background: rgba(139, 94, 60, 0.06);
}

.modal-label {
  font-family: 'Noto Serif SC', serif;
  font-size: 0.75rem;
  color: rgba(139, 94, 60, 0.5);
  letter-spacing: 0.3em;
}

.modal-title {
  font-family: 'Noto Serif SC', 'Source Han Serif CN', serif;
  font-size: 1.1rem;
  font-weight: 700;
  color: #2c2c2a;
  letter-spacing: 0.15em;
  margin: 0;
  text-align: center;
}

/* ── 标题列表 ── */
.title-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding: 1.5rem 1.5rem;
}

.title-option {
  display: flex;
  align-items: center;
  gap: 0.8rem;
  padding: 0.9rem 1.2rem;
  border: 1px solid rgba(139, 94, 60, 0.3);
  border-radius: 3px;
  background: rgba(255, 255, 255, 0.4);
  cursor: pointer;
  transition: all 0.2s;
  text-align: left;
  font-family: inherit;
}

.title-option:hover {
  background: rgba(139, 94, 60, 0.08);
  border-color: rgba(139, 94, 60, 0.5);
}

.title-option.selected {
  background: rgba(139, 94, 60, 0.12);
  border-color: #8b5e3c;
  box-shadow: 0 0 0 1px rgba(139, 94, 60, 0.3);
}

.option-index {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 1.6rem;
  height: 1.6rem;
  border: 1px solid rgba(139, 94, 60, 0.4);
  border-radius: 50%;
  font-family: 'Noto Serif SC', serif;
  font-size: 0.75rem;
  color: #8b5e3c;
  flex-shrink: 0;
}

.title-option.selected .option-index {
  background: #8b5e3c;
  color: #f5f0e6;
  border-color: #8b5e3c;
}

.option-text {
  flex: 1;
  font-family: 'Noto Serif SC', 'Source Han Serif CN', serif;
  font-size: 1rem;
  color: #2c2c2a;
  letter-spacing: 0.1em;
}

.option-check {
  color: #8b5e3c;
  font-size: 1rem;
  flex-shrink: 0;
}

/* ── 底部确认 ── */
.modal-footer {
  padding: 1rem 1.5rem 1.5rem;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.75rem;
}

.error-text {
  font-size: 0.8rem;
  color: #c0392b;
  margin: 0;
  text-align: center;
}

.confirm-btn {
  width: 100%;
  padding: 0.8rem;
  background: #8b5e3c;
  color: #f5f0e6;
  border: none;
  border-radius: 3px;
  font-family: 'Noto Serif SC', serif;
  font-size: 1rem;
  letter-spacing: 0.2em;
  cursor: pointer;
  transition: background 0.2s;
}

.confirm-btn:hover:not(:disabled) {
  background: #7a5235;
}

.confirm-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
