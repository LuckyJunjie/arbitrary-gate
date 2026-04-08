<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'

type ViewMode = 'grid' | 'timeline' | 'map'

const viewMode = ref<ViewMode>('grid')

interface StoredStory {
  id: string
  title: string
  eventName?: string
  status: number
  currentChapter: number
  historyDeviation: number
  createdAt: string
  finishedAt?: string
  dynasty?: string
}

const stories = ref<StoredStory[]>([])

const statusLabel: Record<number, string> = {
  1: '进行中',
  2: '已完成',
}

const statusClass: Record<number, string> = {
  1: 'ongoing',
  2: 'finished',
}

// 按时间排序
const sortedByTime = computed(() =>
  [...stories.value].sort(
    (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
  )
)

// 按朝代分组（山河图视图）
const groupedByDynasty = computed(() => {
  const groups: Record<string, StoredStory[]> = {}
  for (const story of stories.value) {
    const dynasty = story.dynasty ?? '其他'
    if (!groups[dynasty]) groups[dynasty] = []
    groups[dynasty].push(story)
  }
  return groups
})

// 朝代顺序（固定排序）
const dynastyOrder = ['先秦', '秦汉', '魏晋', '南北朝', '隋唐', '五代', '宋', '元', '明', '清', '近代', '其他']

function formatDate(iso: string) {
  const d = new Date(iso)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function formatYear(iso: string) {
  return new Date(iso).getFullYear()
}

function formatTimelineYear(iso: string) {
  const d = new Date(iso)
  return `${d.getFullYear()}.${d.getMonth() + 1}.${d.getDate()}`
}

onMounted(() => {
  const raw = localStorage.getItem('bookshelf_stories')
  if (raw) {
    try {
      const parsed: StoredStory[] = JSON.parse(raw)
      // Normalize status: string ('completed','in_progress') -> number (2,1)
      // Normalize id: number -> string
      stories.value = parsed.map(s => ({
        ...s,
        id: String(s.id),
        status: typeof s.status === 'string'
          ? (s.status === 'completed' ? 2 : 1)
          : s.status,
      }))
    } catch {
      stories.value = []
    }
  }
})
</script>

<template>
  <div class="bookshelf-view" data-testid="bookshelf-container">
    <header class="shelf-header">
      <h2 data-testid="bookshelf-title">书架</h2>
      <div class="view-toggle" data-testid="view-toggle">
        <button
          v-for="mode in (['grid', 'timeline', 'map'] as ViewMode[])"
          :key="mode"
          :class="['toggle-btn', { active: viewMode === mode }]"
          :data-testid="`view-toggle-${mode === 'grid' ? 'grid' : mode}`"
          @click="viewMode = mode"
        >
          {{ { grid: '格子', timeline: '时光轴', map: '山河图' }[mode] }}
        </button>
      </div>
    </header>

    <!-- 格子视图 -->
    <div v-if="viewMode === 'grid'" class="story-grid">
      <template v-if="stories.length > 0">
        <div
          v-for="story in sortedByTime"
          :key="story.id"
          class="story-card"
          :data-testid="'story-card'"
          :data-status="story.status === 2 ? 'completed' : 'in_progress'"
        >
          <div class="story-card-inner">
            <h3 class="story-title" data-testid="story-title">{{ story.title }}</h3>
            <div class="story-event" data-testid="story-event">{{ story.eventName }}</div>
            <div class="story-meta">
              <span
                class="story-status"
                :class="statusClass[story.status]"
                data-testid="story-status"
              >
                {{ statusLabel[story.status] }}
              </span>
              <span class="story-chapters" data-testid="story-chapters">第 {{ story.currentChapter }} 章</span>
            </div>
            <div class="story-time">{{ formatDate(story.createdAt) }}</div>
          </div>
        </div>
      </template>
      <p v-else class="empty-hint" data-testid="empty-bookshelf-message">暂无故事记录</p>
    </div>

    <!-- 时光轴视图 -->
    <div v-else-if="viewMode === 'timeline'" class="timeline-view" data-testid="timeline-view">
      <template v-if="sortedByTime.length > 0">
        <div class="timeline">
          <div
            v-for="(story, idx) in sortedByTime"
            :key="story.id"
            class="timeline-item"
            data-testid="timeline-node"
          >
            <div class="timeline-marker">
              <div class="timeline-dot" :class="statusClass[story.status]"></div>
              <div v-if="idx < sortedByTime.length - 1" class="timeline-line"></div>
            </div>
            <div class="timeline-content">
              <div class="timeline-year">{{ formatTimelineYear(story.createdAt) }}</div>
              <h3 class="timeline-title">{{ story.title }}</h3>
              <div class="timeline-meta">
                <span
                  class="story-status"
                  :class="statusClass[story.status]"
                >
                  {{ statusLabel[story.status] }}
                </span>
                <span class="deviation" v-if="story.historyDeviation !== 0">
                  偏离度 {{ story.historyDeviation > 0 ? '+' : '' }}{{ story.historyDeviation }}
                </span>
              </div>
            </div>
          </div>
        </div>
      </template>
      <p v-else class="empty-hint">暂无故事记录</p>
    </div>

    <!-- 山河图视图 -->
    <div v-else class="map-view" data-testid="map-view">
      <template v-if="Object.keys(groupedByDynasty).length > 0">
        <div
          v-for="dynasty in dynastyOrder"
          :key="dynasty"
          v-show="groupedByDynasty[dynasty]?.length > 0"
          class="dynasty-group"
        >
          <div class="dynasty-label">{{ dynasty }}</div>
          <div class="dynasty-stories">
            <div
              v-for="story in groupedByDynasty[dynasty]"
              :key="story.id"
              class="story-card"
              data-testid="map-marker"
              :data-status="story.status === 2 ? 'completed' : 'in_progress'"
            >
              <div class="story-card-inner">
                <h3 class="story-title">{{ story.title }}</h3>
                <div class="story-meta">
                  <span
                    class="story-status"
                    :class="statusClass[story.status]"
                  >
                    {{ statusLabel[story.status] }}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </template>
      <p v-else class="empty-hint">暂无故事记录</p>
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

/* Grid view */
.story-grid {
  padding: 1rem;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 1rem;
}

.story-card {
  border: 1px solid rgba(44, 31, 20, 0.15);
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.5);
  overflow: hidden;
  transition: box-shadow 0.2s, transform 0.2s;
  cursor: pointer;
}

.story-card:hover {
  box-shadow: 0 4px 16px rgba(44, 31, 20, 0.15);
  transform: translateY(-2px);
}

.story-card-inner {
  padding: 0.75rem;
}

.story-title {
  font-size: 0.9rem;
  color: #2c1f14;
  margin-bottom: 0.5rem;
  line-height: 1.3;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.story-meta {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.4rem;
}

.story-status {
  font-size: 0.7rem;
  padding: 0.1rem 0.4rem;
  border-radius: 2px;
  border: 1px solid;
}

.story-status.ongoing {
  color: #6b8e6b;
  border-color: #6b8e6b;
  background: rgba(107, 142, 107, 0.08);
}

.story-status.finished {
  color: #8b7355;
  border-color: #8b7355;
  background: rgba(139, 115, 85, 0.08);
}

.story-chapters {
  font-size: 0.7rem;
  color: #a08060;
}

.story-time {
  font-size: 0.7rem;
  color: #a08060;
}

/* Timeline view */
.timeline-view {
  padding: 1rem 1.5rem;
}

.timeline {
  position: relative;
}

.timeline-item {
  display: flex;
  gap: 1rem;
  position: relative;
  padding-bottom: 1.5rem;
}

.timeline-marker {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex-shrink: 0;
  width: 16px;
}

.timeline-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  border: 2px solid #8b7355;
  background: #f5efe0;
  flex-shrink: 0;
  z-index: 1;
}

.timeline-dot.ongoing {
  border-color: #6b8e6b;
  background: rgba(107, 142, 107, 0.3);
}

.timeline-dot.finished {
  border-color: #8b7355;
}

.timeline-line {
  width: 2px;
  flex: 1;
  background: rgba(139, 115, 85, 0.3);
  margin-top: 4px;
}

.timeline-content {
  padding-bottom: 0.5rem;
}

.timeline-year {
  font-size: 0.7rem;
  color: #a08060;
  margin-bottom: 0.2rem;
  font-family: monospace;
}

.timeline-title {
  font-size: 1rem;
  color: #2c1f14;
  margin-bottom: 0.3rem;
}

.timeline-meta {
  display: flex;
  gap: 0.75rem;
  align-items: center;
}

.deviation {
  font-size: 0.7rem;
  color: #c9a84c;
}

/* Map view (山河图) */
.map-view {
  padding: 1rem 1.5rem;
}

.dynasty-group {
  margin-bottom: 1.5rem;
}

.dynasty-label {
  font-size: 0.75rem;
  color: #8b7355;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  margin-bottom: 0.75rem;
  padding-bottom: 0.3rem;
  border-bottom: 1px solid rgba(139, 115, 85, 0.2);
}

.dynasty-stories {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 0.75rem;
}

.empty-hint {
  grid-column: 1 / -1;
  text-align: center;
  color: #8b7355;
  padding: 3rem;
  font-size: 0.9rem;
}
</style>
