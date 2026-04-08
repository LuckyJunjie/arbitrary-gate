<script setup lang="ts">
import { ref } from 'vue'

type ViewMode = 'grid' | 'timeline' | 'map'
const viewMode = ref<ViewMode>('grid')

// TODO: 从 storyStore 加载故事列表
// TODO: 时光轴视图（按时间线排列）
// TODO: 山河图视图（按地理/朝代分类）
</script>

<template>
  <div class="bookshelf-view">
    <header class="shelf-header">
      <h2>书架</h2>
      <div class="view-toggle">
        <button
          v-for="mode in (['grid', 'timeline', 'map'] as ViewMode[])"
          :key="mode"
          :class="['toggle-btn', { active: viewMode === mode }]"
          @click="viewMode = mode"
        >
          {{ { grid: '格子', timeline: '时光轴', map: '山河图' }[mode] }}
        </button>
      </div>
    </header>

    <!-- 格子视图 -->
    <div v-if="viewMode === 'grid'" class="story-grid">
      <!-- TODO: 故事卡片列表 -->
      <p class="empty-hint">暂无故事记录</p>
    </div>

    <!-- 时光轴视图 -->
    <div v-else-if="viewMode === 'timeline'" class="timeline-view">
      <!-- TODO: 时间线排列 -->
    </div>

    <!-- 山河图视图 -->
    <div v-else class="map-view">
      <!-- TODO: 地理/朝代地图 -->
    </div>
  </div>
</template>

<style scoped>
.bookshelf-view {
  min-height: 100vh;
  background: #f5efe0;
  padding-bottom: 2rem;
}

.shelf-header {
  padding: 1.5rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid rgba(44, 31, 20, 0.1);
}

.shelf-header h2 {
  font-size: 1.5rem;
  color: #2c1f14;
}

.view-toggle {
  display: flex;
  gap: 0.5rem;
}

.toggle-btn {
  padding: 0.25rem 0.75rem;
  border: 1px solid #8b7355;
  border-radius: 2px;
  background: transparent;
  font-family: inherit;
  font-size: 0.8rem;
  color: #8b7355;
  cursor: pointer;
  transition: all 0.2s;
}

.toggle-btn.active {
  background: #2c1f14;
  color: #f5efe0;
  border-color: #2c1f14;
}

.story-grid {
  padding: 1rem;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 1rem;
}

.empty-hint {
  grid-column: 1 / -1;
  text-align: center;
  color: #8b7355;
  padding: 3rem;
  font-size: 0.9rem;
}
</style>
