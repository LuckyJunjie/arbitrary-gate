<script setup lang="ts">
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import ScrollPanel from '@/components/ScrollPanel.vue'
import { useStory } from '@/composables/useStory'

const route = useRoute()
const { currentChapter, loadChapter } = useStory()

const storyId = route.params.id as string
loadChapter(storyId, 1)

// TODO: 入局三问弹窗（首次进入）
// TODO: AI 流式文字渲染
// TODO: 选项展示与选择
</script>

<template>
  <div class="story-view">
    <!-- TODO: 章节进度条 -->
    <!-- TODO: 关键词共鸣度显示 -->
    <ScrollPanel class="scroll-panel">
      <!-- AI 流式渲染场景文字 -->
      <div class="story-content" v-html="currentChapter?.sceneText ?? ''" />
    </ScrollPanel>

    <!-- 选项区 -->
    <div class="options-panel">
      <button
        v-for="opt in currentChapter?.options ?? []"
        :key="opt.id"
        class="option-btn"
      >
        {{ opt.text }}
      </button>
    </div>

    <!-- TODO: 手势滑动区域 -->
    <!-- TODO: 涟漪动画叠加层 -->
  </div>
</template>

<style scoped>
.story-view {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f5efe0;
}

.scroll-panel {
  flex: 1;
  overflow: hidden;
}

.story-content {
  padding: 2rem;
  font-size: 1.1rem;
  line-height: 2;
  color: #2c1f14;
}

.options-panel {
  padding: 1rem;
  border-top: 1px solid rgba(44, 31, 20, 0.1);
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.option-btn {
  padding: 0.75rem 1rem;
  border: 1px solid #8b7355;
  border-radius: 2px;
  background: rgba(245, 239, 224, 0.9);
  font-family: inherit;
  font-size: 0.95rem;
  color: #2c1f14;
  text-align: left;
  cursor: pointer;
  transition: all 0.2s ease;
}

.option-btn:hover {
  background: rgba(139, 115, 85, 0.15);
  border-color: #2c1f14;
}
</style>
