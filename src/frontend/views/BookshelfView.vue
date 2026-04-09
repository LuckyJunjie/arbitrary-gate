<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'

type ViewMode = 'grid' | 'timeline' | 'map'
type SortMode = 'date' | 'deviation' | 'words'
type FilterStatus = 'all' | 'completed' | 'in_progress'

const router = useRouter()

const viewMode = ref<ViewMode>('grid')

// 书脊最小/最大高度（px），书脊高度按 total_words/1000 缩放
const SPINE_MIN_H = 80
const SPINE_MAX_H = 220
const BOOKS_PER_SHELF = 6  // 每行书架最大书本数

// 计算书脊高度
function spineHeight(wordCount: number = 0): number {
  const raw = wordCount / 1000
  return Math.min(SPINE_MAX_H, Math.max(SPINE_MIN_H, raw))
}

// 计算行高（书架高度 = 最长书脊高度 + 题签高度 + 印鉴区）
function shelfHeight(bookWordCounts: number[]): number {
  if (bookWordCounts.length === 0) return SPINE_MIN_H
  const maxWords = Math.max(...bookWordCounts)
  return spineHeight(maxWords) + 52 // 36px题签 + 16px印鉴区
}

// 分组成书架行
const bookshelfRows = computed(() => {
  const list = sortedStories.value
  const rows: StoredStory[][] = []
  for (let i = 0; i < list.length; i += BOOKS_PER_SHELF) {
    rows.push(list.slice(i, i + BOOKS_PER_SHELF))
  }
  return rows
})

interface Keyword {
  id: number
  name: string
  rarity: number
}

interface StoredStory {
  id: string
  title: string
  storyNo?: string
  eventName?: string
  status: number
  currentChapter: number
  historyDeviation: number
  wordCount?: number
  createdAt: string
  finishedAt?: string
  dynasty?: string
  keywords?: Keyword[]
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

// === 筛选状态 ===
const filterStatus = ref<FilterStatus>('all')
const filterKeyword = ref<string | null>(null)

// === 排序状态 ===
const sortMode = ref<SortMode>('date')

// === 详情面板 ===
const detailPanelStory = ref<StoredStory | null>(null)

// === 删除确认 ===
const deleteConfirmStory = ref<StoredStory | null>(null)

// === 卡菜单（卡片级） ===
const cardMenuStory = ref<StoredStory | null>(null)

// === 下拉展开状态 ===
const sortDropdownOpen = ref(false)
const keywordDropdownOpen = ref(false)

// === 所有关键词列表（从 stories 提取去重） ===
const allKeywords = computed(() => {
  const kwMap = new Map<string, Keyword>()
  for (const s of stories.value) {
    if (s.keywords) {
      for (const kw of s.keywords) {
        if (!kwMap.has(kw.name)) {
          kwMap.set(kw.name, kw)
        }
      }
    }
  }
  return Array.from(kwMap.values())
})

// === 筛选后的列表 ===
const filteredStories = computed(() => {
  let list = [...stories.value]

  // 状态筛选
  if (filterStatus.value === 'completed') {
    list = list.filter(s => s.status === 2)
  } else if (filterStatus.value === 'in_progress') {
    list = list.filter(s => s.status === 1)
  }

  // 关键词筛选
  if (filterKeyword.value) {
    list = list.filter(s =>
      s.keywords?.some(kw => kw.name === filterKeyword.value)
    )
  }

  return list
})

// === 排序后的列表 ===
const sortedStories = computed(() => {
  const list = [...filteredStories.value]
  if (sortMode.value === 'date') {
    return list.sort(
      (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
    )
  } else if (sortMode.value === 'deviation') {
    return list.sort((a, b) => Math.abs(b.historyDeviation) - Math.abs(a.historyDeviation))
  } else if (sortMode.value === 'words') {
    return list.sort((a, b) => (b.wordCount ?? 0) - (a.wordCount ?? 0))
  }
  return list
})

// 按朝代分组（山河图视图）
const groupedByDynasty = computed(() => {
  const groups: Record<string, StoredStory[]> = {}
  for (const story of filteredStories.value) {
    const dynasty = story.dynasty ?? '其他'
    if (!groups[dynasty]) groups[dynasty] = []
    groups[dynasty].push(story)
  }
  return groups
})

// 朝代顺序（固定排序）
const dynastyOrder = ['先秦', '秦汉', '魏晋', '南北朝', '隋唐', '五代', '宋', '元', '明', '清', '近代', '其他']

// === 是否有激活的筛选 ===
const hasActiveFilter = computed(
  () => filterStatus.value !== 'all' || filterKeyword.value !== null
)

// === 是否为空书架 ===
const isEmpty = computed(() => stories.value.length === 0)

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

function formatDeviation(dev: number) {
  if (dev === 0) return '±0'
  return `${dev > 0 ? '+' : ''}${dev}`
}

// 状态筛选
function setFilterStatus(status: FilterStatus) {
  filterStatus.value = status
}

// 关键词筛选
function setFilterKeyword(kwName: string | null) {
  filterKeyword.value = kwName
}

// 清除筛选
function clearFilters() {
  filterStatus.value = 'all'
  filterKeyword.value = null
}

// 排序
function setSortMode(mode: SortMode) {
  sortMode.value = mode
}

// 打开详情面板
function openDetail(story: StoredStory) {
  detailPanelStory.value = story
}

// 关闭详情面板
function closeDetail() {
  detailPanelStory.value = null
}

// 继续阅读
function continueReading(story: StoredStory) {
  router.push(`/story/${story.id}`)
}

// 分享故事
function shareStory(story: StoredStory) {
  router.push(`/share/${story.storyNo ?? story.id}`)
}

// 删除故事 - 打开卡菜单
function openCardMenu(story: StoredStory) {
  cardMenuStory.value = story
}

// 关闭卡菜单
function closeCardMenu() {
  cardMenuStory.value = null
}

// 打开删除确认
function openDeleteConfirm(story: StoredStory) {
  cardMenuStory.value = null
  deleteConfirmStory.value = story
}

function confirmDelete() {
  if (!deleteConfirmStory.value) return
  const id = deleteConfirmStory.value.id
  stories.value = stories.value.filter(s => s.id !== id)
  saveToStorage()
  deleteConfirmStory.value = null
  detailPanelStory.value = null
}

function cancelDelete() {
  deleteConfirmStory.value = null
}

function saveToStorage() {
  localStorage.setItem('bookshelf_stories', JSON.stringify(stories.value))
}

function startNewStory() {
  router.push('/entry-questions')
}

onMounted(() => {
  const raw = localStorage.getItem('bookshelf_stories')
  if (raw) {
    try {
      const parsed: StoredStory[] = JSON.parse(raw)
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
      <div class="header-actions">
        <!-- 排序选择器 -->
        <div class="sort-select-wrapper" data-testid="sort-select">
          <button
            class="sort-trigger"
            @click="sortDropdownOpen = !sortDropdownOpen"
          >{{ { date: '按时间', deviation: '按偏离度', words: '按字数' }[sortMode] }}</button>
          <div class="dropdown-menu" v-if="sortDropdownOpen">
            <button
              class="dropdown-item"
              :class="{ active: sortMode === 'date' }"
              data-testid="sort-option-date"
              @click="setSortMode('date'); sortDropdownOpen = false"
            >按时间</button>
            <button
              class="dropdown-item"
              :class="{ active: sortMode === 'deviation' }"
              data-testid="sort-option-deviation"
              @click="setSortMode('deviation'); sortDropdownOpen = false"
            >按偏离度</button>
            <button
              class="dropdown-item"
              :class="{ active: sortMode === 'words' }"
              data-testid="sort-option-words"
              @click="setSortMode('words'); sortDropdownOpen = false"
            >按字数</button>
          </div>
        </div>

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
      </div>
    </header>

    <!-- 筛选栏 -->
    <div class="filter-bar" v-if="!isEmpty">
      <!-- 状态筛选 -->
      <div class="filter-group">
        <button
          :class="['filter-btn', { active: filterStatus === 'all' }]"
          @click="setFilterStatus('all')"
        >全部</button>
        <button
          class="filter-btn"
          :class="{ active: filterStatus === 'completed' }"
          data-testid="filter-status-completed"
          @click="setFilterStatus('completed')"
        >已完成</button>
        <button
          class="filter-btn"
          :class="{ active: filterStatus === 'in_progress' }"
          data-testid="filter-status-in_progress"
          @click="setFilterStatus('in_progress')"
        >进行中</button>
      </div>

      <!-- 关键词筛选下拉 -->
      <div class="filter-dropdown" v-if="allKeywords.length > 0" data-testid="filter-keyword">
        <button
          class="keyword-trigger"
          @click="keywordDropdownOpen = !keywordDropdownOpen"
        >{{ filterKeyword || '全部关键词' }}</button>
        <div class="dropdown-menu" v-if="keywordDropdownOpen">
          <button
            class="dropdown-item"
            :class="{ active: filterKeyword === null }"
            @click="setFilterKeyword(null); keywordDropdownOpen = false"
          >全部关键词</button>
          <button
            v-for="kw in allKeywords"
            :key="kw.id"
            class="dropdown-item"
            :class="{ active: filterKeyword === kw.name }"
            :data-testid="`keyword-option-${kw.name}`"
            @click="setFilterKeyword(kw.name); keywordDropdownOpen = false"
          >{{ kw.name }}</button>
        </div>
      </div>

      <!-- 清除筛选 -->
      <button
        v-if="hasActiveFilter"
        class="filter-clear-btn"
        data-testid="filter-clear"
        @click="clearFilters"
      >清除筛选</button>
    </div>

    <!-- 格子视图 - 老式书架 -->
    <div v-if="viewMode === 'grid'" class="bookshelf-container">
      <template v-if="bookshelfRows.length > 0">
        <div
          v-for="(row, rowIdx) in bookshelfRows"
          :key="rowIdx"
          class="bookshelf-row"
          :style="{ height: shelfHeight(row.map(s => s.wordCount ?? 0)) + 'px' }"
        >
          <!-- 书架底板木纹 -->
          <div class="shelf-plank"></div>
          <!-- 书籍容器 -->
          <div class="books-row" :style="{ height: shelfHeight(row.map(s => s.wordCount ?? 0)) - 18 + 'px' }">
            <div
              v-for="story in row"
              :key="story.id"
              class="book-spine"
              :class="{ completed: story.status === 2 }"
              :style="{ height: spineHeight(story.wordCount) + 'px' }"
              data-testid="story-card"
              :data-status="story.status === 2 ? 'completed' : 'in_progress'"
              @click="openDetail(story)"
            >
              <!-- 书脊顶部色带 -->
              <div class="spine-top-band"></div>
              <!-- 题签（竖排书名） -->
              <div class="spine-title-slip">
                <span class="spine-title-text" data-testid="story-title">{{ story.title }}</span>
              </div>
              <!-- 题签装饰线 -->
              <div class="spine-slip-line"></div>
              <!-- 书脊底部区 -->
              <div class="spine-bottom">
                <div class="spine-event" data-testid="story-event">{{ story.eventName }}</div>
                <!-- 朱红印鉴：仅已完成书籍显示 -->
                <div v-if="story.status === 2" class="seal-icon" title="已完成">
                  <svg viewBox="0 0 28 28" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <rect x="1" y="1" width="26" height="26" rx="2" stroke="#B22222" stroke-width="1.5"/>
                    <rect x="3.5" y="3.5" width="21" height="21" rx="1" stroke="#B22222" stroke-width="0.75" stroke-dasharray="2 2"/>
                    <text x="14" y="17" text-anchor="middle" font-size="8" fill="#B22222" font-family="serif">完</text>
                  </svg>
                </div>
              </div>
            </div>
          </div>
        </div>
      </template>
      <template v-else-if="isEmpty">
        <p class="empty-hint" data-testid="empty-bookshelf-message">暂无故事记录</p>
        <button
          class="start-btn"
          data-testid="start-new-story-button"
          @click="startNewStory"
        >开始新故事</button>
      </template>
      <template v-else>
        <p class="empty-hint">没有符合筛选条件的记录</p>
      </template>
    </div>

    <!-- 时光轴视图 -->
    <div v-else-if="viewMode === 'timeline'" class="timeline-view" data-testid="timeline-view">
      <template v-if="sortedStories.length > 0">
        <div class="timeline">
          <div
            v-for="(story, idx) in sortedStories"
            :key="story.id"
            class="timeline-item"
            data-testid="timeline-node"
            @click="openDetail(story)"
          >
            <div class="timeline-marker">
              <div class="timeline-dot" :class="statusClass[story.status]"></div>
              <div v-if="idx < sortedStories.length - 1" class="timeline-line"></div>
            </div>
            <div class="timeline-content">
              <div class="timeline-year">{{ formatTimelineYear(story.createdAt) }}</div>
              <h3 class="timeline-title">{{ story.title }}</h3>
              <div class="timeline-meta">
                <span class="story-status" :class="statusClass[story.status]">
                  {{ statusLabel[story.status] }}
                </span>
                <span class="deviation" v-if="story.historyDeviation !== 0" data-testid="deviation-badge">
                  偏离度 {{ story.historyDeviation > 0 ? '+' : '' }}{{ story.historyDeviation }}
                </span>
                <span class="word-count" v-if="story.wordCount" data-testid="word-count-badge">
                  {{ story.wordCount }}字
                </span>
              </div>
            </div>
          </div>
        </div>
      </template>
      <template v-else-if="isEmpty">
        <p class="empty-hint" data-testid="empty-bookshelf-message">暂无故事记录</p>
        <button class="start-btn" data-testid="start-new-story-button" @click="startNewStory">开始新故事</button>
      </template>
      <template v-else>
        <p class="empty-hint">没有符合筛选条件的记录</p>
      </template>
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
              @click="openDetail(story)"
            >
              <div class="story-card-inner">
                <h3 class="story-title">{{ story.title }}</h3>
                <div class="story-meta">
                  <span class="story-status" :class="statusClass[story.status]">
                    {{ statusLabel[story.status] }}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </template>
      <template v-else-if="isEmpty">
        <p class="empty-hint" data-testid="empty-bookshelf-message">暂无故事记录</p>
        <button class="start-btn" data-testid="start-new-story-button" @click="startNewStory">开始新故事</button>
      </template>
      <template v-else>
        <p class="empty-hint">没有符合筛选条件的记录</p>
      </template>
    </div>

    <!-- 故事详情面板 -->
    <Teleport to="body">
      <div
        v-if="detailPanelStory"
        class="detail-overlay"
        @click.self="closeDetail"
      >
        <div class="detail-panel" data-testid="story-detail-panel">
          <button class="detail-close" @click="closeDetail">✕</button>
          <h2 class="detail-title">{{ detailPanelStory.title }}</h2>
          <div class="detail-event">{{ detailPanelStory.eventName }}</div>
          <div class="detail-meta">
            <span
              class="story-status"
              :class="statusClass[detailPanelStory.status]"
            >{{ statusLabel[detailPanelStory.status] }}</span>
            <span>第 {{ detailPanelStory.currentChapter }} 章</span>
            <span v-if="detailPanelStory.historyDeviation !== 0" class="deviation" data-testid="deviation-badge">
              偏离度 {{ detailPanelStory.historyDeviation > 0 ? '+' : '' }}{{ detailPanelStory.historyDeviation }}
            </span>
            <span v-if="detailPanelStory.wordCount" data-testid="word-count-badge">
              {{ detailPanelStory.wordCount }}字
            </span>
          </div>

          <!-- 关键词展示 -->
          <div class="detail-keywords" v-if="detailPanelStory.keywords?.length">
            <span v-for="kw in detailPanelStory.keywords" :key="kw.id" class="keyword-tag">
              {{ kw.name }}
            </span>
          </div>

          <div class="detail-actions">
            <!-- 已完成：分享按钮 -->
            <button
              v-if="detailPanelStory.status === 2"
              class="action-btn share-btn"
              data-testid="share-button"
              @click="shareStory(detailPanelStory!)"
            >分享</button>

            <!-- 进行中：继续阅读按钮 -->
            <button
              v-if="detailPanelStory.status === 1"
              class="action-btn continue-btn"
              data-testid="continue-reading-button"
              @click="continueReading(detailPanelStory!)"
            >继续阅读</button>

            <!-- 卡片菜单按钮 -->
            <button
              class="action-btn menu-btn"
              data-testid="card-menu-button"
              @click="openDeleteConfirm(detailPanelStory!)"
            >删除</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 删除确认弹窗 -->
    <Teleport to="body">
      <div v-if="deleteConfirmStory" class="confirm-overlay" @click.self="cancelDelete">
        <div class="confirm-dialog">
          <p>确定要删除「{{ deleteConfirmStory.title }}」吗？</p>
          <p class="confirm-sub">删除后无法恢复</p>
          <div class="confirm-actions">
            <button class="action-btn cancel-btn" @click="cancelDelete">取消</button>
            <button
              class="action-btn delete-btn"
              data-testid="confirm-delete-button"
              @click="confirmDelete"
            >确认删除</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 卡片菜单弹窗 -->
    <Teleport to="body">
      <div v-if="cardMenuStory" class="card-menu-overlay" @click.self="closeCardMenu">
        <div class="card-menu-popup">
          <button
            class="card-menu-item"
            data-testid="delete-option"
            @click="openDeleteConfirm(cardMenuStory!)"
          >删除</button>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
/* Google Fonts - 马善政楷体（题签手写感） */
@import url('https://fonts.googleapis.com/css2?family=Ma+Shan+Zheng&display=swap');

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

.header-actions {
  display: flex;
  align-items: center;
  gap: 0.75rem;
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

/* 排序选择器 */
.sort-select-wrapper {
  position: relative;
}

.sort-trigger {
  padding: 0.25rem 0.5rem;
  border: 1px solid #8b7355;
  border-radius: 2px;
  background: rgba(255, 255, 255, 0.5);
  font-family: inherit;
  font-size: 0.8rem;
  color: #2c1f14;
  cursor: pointer;
}

/* 下拉菜单 */
.dropdown-menu {
  position: absolute;
  top: 100%;
  left: 0;
  margin-top: 4px;
  background: #f5efe0;
  border: 1px solid rgba(44, 31, 20, 0.15);
  border-radius: 4px;
  box-shadow: 0 4px 12px rgba(44, 31, 20, 0.15);
  z-index: 100;
  min-width: 120px;
  overflow: hidden;
}

.dropdown-item {
  display: block;
  width: 100%;
  padding: 0.4rem 0.75rem;
  text-align: left;
  background: none;
  border: none;
  font-family: inherit;
  font-size: 0.8rem;
  color: #2c1f14;
  cursor: pointer;
  transition: background 0.15s;
}

.dropdown-item:hover {
  background: rgba(44, 31, 20, 0.08);
}

.dropdown-item.active {
  background: rgba(44, 31, 20, 0.12);
  color: #8b7355;
}

.sort-select {
  padding: 0.25rem 0.5rem;
  border: 1px solid #8b7355;
  border-radius: 2px;
  background: rgba(255, 255, 255, 0.5);
  font-family: inherit;
  font-size: 0.8rem;
  color: #2c1f14;
  cursor: pointer;
}

/* 筛选栏 */
.filter-bar {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem 1.5rem;
  border-bottom: 1px solid rgba(44, 31, 20, 0.08);
  flex-wrap: wrap;
}

.filter-group {
  display: flex;
  gap: 0.5rem;
}

.filter-btn {
  padding: 0.2rem 0.6rem;
  border: 1px solid rgba(139, 115, 85, 0.4);
  border-radius: 2px;
  background: transparent;
  font-family: inherit;
  font-size: 0.75rem;
  color: #8b7355;
  cursor: pointer;
  transition: all 0.2s;
}

.filter-btn.active {
  background: #2c1f14;
  color: #f5efe0;
  border-color: #2c1f14;
}

.keyword-trigger {
  padding: 0.2rem 0.6rem;
  border: 1px solid rgba(139, 115, 85, 0.4);
  border-radius: 2px;
  background: rgba(255, 255, 255, 0.5);
  font-family: inherit;
  font-size: 0.75rem;
  color: #2c1f14;
  cursor: pointer;
}

.filter-dropdown {
  position: relative;
}

.filter-clear-btn {
  padding: 0.2rem 0.6rem;
  border: 1px solid #c9a84c;
  border-radius: 2px;
  background: transparent;
  font-family: inherit;
  font-size: 0.75rem;
  color: #c9a84c;
  cursor: pointer;
}

/* =============================================
   老式书架视图（grid 模式）
   ============================================= */

/* 木纹背景（书架整体背景） */
.bookshelf-container {
  padding: 1.5rem 1rem 2.5rem;
  /* 多层 linear-gradient 叠加模拟木纹 */
  background:
    repeating-linear-gradient(
      90deg,
      transparent 0px,
      transparent 3px,
      rgba(139, 90, 43, 0.04) 3px,
      rgba(139, 90, 43, 0.04) 4px
    ),
    repeating-linear-gradient(
      180deg,
      transparent 0px,
      transparent 18px,
      rgba(101, 67, 33, 0.06) 18px,
      rgba(101, 67, 33, 0.06) 20px
    ),
    repeating-linear-gradient(
      90deg,
      transparent 0px,
      transparent 60px,
      rgba(165, 110, 60, 0.03) 60px,
      rgba(165, 110, 60, 0.03) 62px
    ),
    linear-gradient(
      180deg,
      #e8d5b0 0%,
      #d4b98a 15%,
      #c8a876 30%,
      #d4b98a 50%,
      #cfaa78 65%,
      #d4b98a 80%,
      #e0cba0 100%
    );
  min-height: calc(100vh - 120px);
}

/* 每行书架 */
.bookshelf-row {
  position: relative;
  margin-bottom: 0;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
}

/* 书架底板（木板视觉） */
.shelf-plank {
  height: 16px;
  background:
    linear-gradient(
      180deg,
      #8b5a2b 0%,
      #6b4226 20%,
      #7a4f2e 40%,
      #6b4226 60%,
      #5c3a20 80%,
      #7a4f2e 100%
    );
  border-top: 2px solid #a07040;
  border-bottom: 2px solid #4a2e14;
  box-shadow:
    0 4px 8px rgba(74, 46, 20, 0.4),
    inset 0 1px 0 rgba(255, 220, 160, 0.2);
  border-radius: 0 0 2px 2px;
}

/* 书籍行容器 */
.books-row {
  display: flex;
  align-items: flex-end;
  gap: 6px;
  padding: 0 4px;
  overflow: visible;
}

/* 书脊 */
.book-spine {
  position: relative;
  width: 42px;
  min-width: 42px;
  display: flex;
  flex-direction: column;
  align-items: center;
  border-radius: 2px 2px 0 0;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
  flex-shrink: 0;
  /* 木纹+暖棕色调 */
  background:
    repeating-linear-gradient(
      180deg,
      transparent 0px,
      transparent 3px,
      rgba(0, 0, 0, 0.03) 3px,
      rgba(0, 0, 0, 0.03) 4px
    ),
    linear-gradient(
      180deg,
      #9b7355 0%,
      #8b6348 12%,
      #9b7a5a 28%,
      #8b6348 45%,
      #7a5a40 62%,
      #8b6348 78%,
      #9b7a5a 100%
    );
  box-shadow:
    inset -2px 0 4px rgba(0, 0, 0, 0.15),
    inset 2px 0 3px rgba(255, 220, 160, 0.08),
    1px 0 2px rgba(74, 46, 20, 0.3);
}

.book-spine:hover {
  transform: translateY(-6px);
  box-shadow:
    inset -2px 0 4px rgba(0, 0, 0, 0.15),
    inset 2px 0 3px rgba(255, 220, 160, 0.08),
    0 8px 16px rgba(74, 46, 20, 0.4);
}

/* 书脊顶部装饰色带 */
.spine-top-band {
  width: 100%;
  height: 5px;
  background: linear-gradient(180deg, #5c3a20, #8b5a2b);
  border-radius: 2px 2px 0 0;
  flex-shrink: 0;
}

/* 题签区 */
.spine-title-slip {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  padding: 4px 3px;
  overflow: hidden;
}

.spine-title-text {
  /* 竖排文字：每个字分一行 */
  writing-mode: vertical-rl;
  text-orientation: mixed;
  font-size: 11px;
  color: #2c1f14;
  /* 手写楷体感 */
  font-family: 'Ma Shan Zheng', 'STKaiti', 'KaiTi', '楷体', '楷体_GB2312', serif;
  letter-spacing: 0.1em;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-height: 100%;
  display: -webkit-box;
  -webkit-box-orient: horizontal;
  -webkit-line-clamp: 1;
  /* 略微倾斜增加古感 */
  transform: rotate(0deg);
}

/* 题签装饰线 */
.spine-slip-line {
  width: 70%;
  height: 1px;
  background: linear-gradient(
    90deg,
    transparent,
    rgba(139, 90, 43, 0.4),
    transparent
  );
  margin: 2px 0;
  flex-shrink: 0;
}

/* 书脊底部区域 */
.spine-bottom {
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  padding: 0 3px 5px;
  flex-shrink: 0;
}

.spine-event {
  font-size: 8px;
  color: rgba(44, 31, 20, 0.55);
  writing-mode: vertical-rl;
  text-orientation: mixed;
  letter-spacing: 0.05em;
  line-height: 1.2;
  font-family: 'STKaiti', 'KaiTi', '楷体', '楷体_GB2312', serif;
  max-height: 36px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: -webkit-box;
  -webkit-box-orient: horizontal;
  -webkit-line-clamp: 1;
}

/* 朱红印鉴 */
.seal-icon {
  width: 22px;
  height: 22px;
  opacity: 0.9;
  flex-shrink: 0;
}

.seal-icon svg {
  width: 100%;
  height: 100%;
}

/* =============================================
   以下保留旧样式（timeline / map 视图 / 详情面板）
   ============================================= */

/* Grid view - 旧样式（山河图等继续使用） */
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

.card-header-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.4rem;
  margin-bottom: 0.5rem;
}

.card-header-row .story-title {
  margin-bottom: 0;
  flex: 1;
}

.card-menu-btn {
  background: none;
  border: none;
  font-size: 1.1rem;
  color: #a08060;
  cursor: pointer;
  padding: 0 0.2rem;
  line-height: 1;
  flex-shrink: 0;
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

.story-badges {
  display: flex;
  gap: 0.4rem;
  margin-bottom: 0.4rem;
}

.badge {
  font-size: 0.65rem;
  padding: 0.1rem 0.35rem;
  border-radius: 2px;
}

.deviation-badge {
  background: rgba(201, 168, 76, 0.12);
  color: #c9a84c;
  border: 1px solid rgba(201, 168, 76, 0.3);
}

.word-count-badge {
  background: rgba(107, 142, 107, 0.12);
  color: #6b8e6b;
  border: 1px solid rgba(107, 142, 107, 0.3);
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
  cursor: pointer;
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

.word-count {
  font-size: 0.7rem;
  color: #6b8e6b;
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

.start-btn {
  grid-column: 1 / -1;
  display: block;
  margin: 0 auto;
  padding: 0.6rem 1.5rem;
  background: #2c1f14;
  color: #f5efe0;
  border: none;
  border-radius: 4px;
  font-family: inherit;
  font-size: 0.9rem;
  cursor: pointer;
}

/* 详情面板 */
.detail-overlay {
  position: fixed;
  inset: 0;
  background: rgba(44, 31, 20, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.detail-panel {
  background: #f5efe0;
  border-radius: 8px;
  padding: 1.5rem;
  max-width: 400px;
  width: 90%;
  position: relative;
  box-shadow: 0 8px 32px rgba(44, 31, 20, 0.3);
}

.detail-close {
  position: absolute;
  top: 0.75rem;
  right: 0.75rem;
  background: none;
  border: none;
  font-size: 1.2rem;
  color: #8b7355;
  cursor: pointer;
}

.detail-title {
  font-size: 1.3rem;
  color: #2c1f14;
  margin-bottom: 0.5rem;
}

.detail-event {
  font-size: 0.85rem;
  color: #8b7355;
  margin-bottom: 0.75rem;
}

.detail-meta {
  display: flex;
  gap: 0.75rem;
  align-items: center;
  flex-wrap: wrap;
  margin-bottom: 0.75rem;
  font-size: 0.8rem;
  color: #2c1f14;
}

.detail-keywords {
  display: flex;
  gap: 0.4rem;
  flex-wrap: wrap;
  margin-bottom: 1rem;
}

.keyword-tag {
  font-size: 0.7rem;
  padding: 0.15rem 0.5rem;
  background: rgba(139, 115, 85, 0.12);
  border: 1px solid rgba(139, 115, 85, 0.25);
  border-radius: 20px;
  color: #8b7355;
}

.detail-actions {
  display: flex;
  gap: 0.5rem;
  margin-top: 1rem;
}

.action-btn {
  padding: 0.4rem 0.9rem;
  border-radius: 4px;
  font-family: inherit;
  font-size: 0.8rem;
  cursor: pointer;
  border: 1px solid;
}

.share-btn {
  background: #2c1f14;
  color: #f5efe0;
  border-color: #2c1f14;
}

.continue-btn {
  background: #6b8e6b;
  color: #f5efe0;
  border-color: #6b8e6b;
}

.menu-btn {
  background: transparent;
  color: #c9a84c;
  border-color: #c9a84c;
  margin-left: auto;
}

/* 卡片菜单 */
.card-menu-overlay {
  position: fixed;
  inset: 0;
  background: rgba(44, 31, 20, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 900;
}

.card-menu-popup {
  background: #f5efe0;
  border: 1px solid rgba(44, 31, 20, 0.15);
  border-radius: 6px;
  box-shadow: 0 4px 16px rgba(44, 31, 20, 0.25);
  overflow: hidden;
  min-width: 140px;
}

.card-menu-item {
  display: block;
  width: 100%;
  padding: 0.6rem 1rem;
  text-align: left;
  background: none;
  border: none;
  font-family: inherit;
  font-size: 0.85rem;
  color: #c0392b;
  cursor: pointer;
}

.card-menu-item:hover {
  background: rgba(192, 57, 43, 0.08);
}

/* 删除确认弹窗 */
.confirm-overlay {
  position: fixed;
  inset: 0;
  background: rgba(44, 31, 20, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1100;
}

.confirm-dialog {
  background: #f5efe0;
  border-radius: 8px;
  padding: 1.5rem;
  max-width: 320px;
  width: 90%;
  text-align: center;
  box-shadow: 0 8px 32px rgba(44, 31, 20, 0.3);
}

.confirm-dialog p {
  color: #2c1f14;
  margin-bottom: 0.5rem;
}

.confirm-sub {
  font-size: 0.8rem;
  color: #a08060;
  margin-bottom: 1rem;
}

.confirm-actions {
  display: flex;
  gap: 0.75rem;
  justify-content: center;
}

.cancel-btn {
  background: transparent;
  color: #8b7355;
  border-color: #8b7355;
}

.delete-btn {
  background: #c0392b;
  color: #f5efe0;
  border-color: #c0392b;
}
</style>
