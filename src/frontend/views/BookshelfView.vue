<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'

type ViewMode = 'grid' | 'timeline' | 'map' | 'bookshelf'
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
  /** 古风地图标记：事件发生地（如"赤壁""玄武门"） */
  location?: string
}

// === 古风地图坐标映射 ===
// viewBox="0 0 800 600"，地图范围约东经73-135°，北纬18-54°
const LOCATION_COORDS: Record<string, { x: number; y: number; label: string }> = {
  '洛阳': { x: 580, y: 265, label: '洛' },
  '长安': { x: 505, y: 280, label: '长' },
  '赤壁': { x: 668, y: 365, label: '赤' },
  '汴京': { x: 610, y: 270, label: '汴' },
  '临安': { x: 680, y: 330, label: '杭' },
  '金陵': { x: 665, y: 315, label: '金' },
  '扬州': { x: 655, y: 300, label: '扬' },
  '成都': { x: 450, y: 350, label: '蜀' },
  '苏州': { x: 678, y: 310, label: '苏' },
  '武昌': { x: 640, y: 345, label: '鄂' },
  '南昌': { x: 660, y: 370, label: '赣' },
  '福州': { x: 695, y: 385, label: '闽' },
  '广州': { x: 640, y: 435, label: '粤' },
  '桂林': { x: 595, y: 430, label: '桂' },
  '大理': { x: 510, y: 420, label: '理' },
  '昆明': { x: 520, y: 430, label: '滇' },
  '拉萨': { x: 370, y: 400, label: '藏' },
  '敦煌': { x: 340, y: 250, label: '敦' },
  '碎叶': { x: 200, y: 240, label: '叶' },
  '幽州': { x: 540, y: 205, label: '幽' },
  '蓟城': { x: 545, y: 210, label: '蓟' },
  '辽东': { x: 570, y: 180, label: '辽' },
  '高句丽': { x: 610, y: 165, label: '丽' },
  '太原': { x: 545, y: 255, label: '晋' },
  '雁门关': { x: 530, y: 235, label: '雁' },
  '马嵬驿': { x: 490, y: 295, label: '骊' },
  '玄武门': { x: 505, y: 280, label: '玄' },
  '陈桥驿': { x: 608, y: 268, label: '陈' },
  '崖山': { x: 640, y: 460, label: '崖' },
  '钱塘': { x: 678, y: 325, label: '钱' },
  '泰山': { x: 618, y: 270, label: '岱' },
  '洞庭': { x: 635, y: 360, label: '洞' },
  '鄱阳': { x: 658, y: 355, label: '鄱' },
  '沧州': { x: 570, y: 245, label: '沧' },
  '青州': { x: 610, y: 255, label: '青' },
  '登州': { x: 625, y: 248, label: '登' },
  '宁波': { x: 690, y: 340, label: '甬' },
  '泉州': { x: 690, y: 405, label: '泉' },
  '荆州': { x: 625, y: 340, label: '荆' },
  '襄阳': { x: 618, y: 325, label: '襄' },
  '汉中': { x: 520, y: 315, label: '汉' },
  '长安·大明宫': { x: 505, y: 280, label: '明' },
  '巨鹿': { x: 565, y: 250, label: '巨' },
  '乌江': { x: 655, y: 295, label: '乌' },
  '垓下': { x: 630, y: 285, label: '垓' },
  '山海关': { x: 545, y: 190, label: '关' },
  '沈阳': { x: 565, y: 170, label: '沈' },
  '长春': { x: 575, y: 155, label: '春' },
  '新疆': { x: 240, y: 255, label: '疆' },
  '西藏': { x: 370, y: 395, label: '藏' },
  '漠北': { x: 420, y: 180, label: '漠' },
  '塞外': { x: 450, y: 200, label: '塞' },
}

// === 地图标记点（从 stories 聚合） ===
const mapMarkers = computed(() => {
  const locMap = new Map<string, StoredStory[]>()
  for (const s of filteredStories.value) {
    if (s.location) {
      // 精确匹配
      if (!locMap.has(s.location)) locMap.set(s.location, [])
      locMap.get(s.location)!.push(s)
    }
  }
  return Array.from(locMap.entries()).map(([loc, storyList]) => {
    const coord = LOCATION_COORDS[loc]
    return {
      location: loc,
      stories: storyList,
      x: coord?.x ?? 600,
      y: coord?.y ?? 300,
      label: coord?.label ?? loc.slice(0, 1),
    }
  })
})

// === 当前选中的地图标记（展示故事列表浮层） ===
const selectedMapMarker = ref<(typeof mapMarkers.value)[0] | null>(null)

function openMapMarker(marker: (typeof mapMarkers.value)[0]) {
  selectedMapMarker.value = marker
}

function closeMapMarker() {
  selectedMapMarker.value = null
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
const _groupedByDynasty = computed(() => {
  const groups: Record<string, StoredStory[]> = {}
  for (const story of filteredStories.value) {
    const dynasty = story.dynasty ?? '其他'
    if (!groups[dynasty]) groups[dynasty] = []
    groups[dynasty].push(story)
  }
  return groups
})

// 朝代顺序（固定排序）
const _dynastyOrder = ['先秦', '秦汉', '魏晋', '南北朝', '隋唐', '五代', '宋', '元', '明', '清', '近代', '其他']

// === 是否有激活的筛选 ===
const hasActiveFilter = computed(
  () => filterStatus.value !== 'all' || filterKeyword.value !== null
)

// === 是否为空书架 ===
const isEmpty = computed(() => stories.value.length === 0)

function _formatDate(iso: string) {
  const d = new Date(iso)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function _formatYear(iso: string) {
  return new Date(iso).getFullYear()
}

function formatTimelineYear(iso: string) {
  const d = new Date(iso)
  return `${d.getFullYear()}.${d.getMonth() + 1}.${d.getDate()}`
}

function _formatDeviation(dev: number) {
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
function _openCardMenu(story: StoredStory) {
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
            {{ { grid: '格子', timeline: '时光轴', map: '山河图', bookshelf: '📚 书架' }[mode] }}
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
    <div v-if="viewMode === 'grid'" class="bookshelf-container" data-testid="bookshelf-wooden-frame">
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
      <template v-if="sortedStories.length > 0">
        <div class="shanhe-map-wrapper">
          <!-- 古风中国地图 SVG -->
          <svg
            class="shanhe-map"
            viewBox="0 0 800 600"
            xmlns="http://www.w3.org/2000/svg"
            xmlns:xlink="http://www.w3.org/1999/xlink"
          >
            <!-- 底色 -->
            <rect width="800" height="600" fill="#f0e8d5" />

            <!-- 沙漠/戈壁纹理 -->
            <ellipse cx="360" cy="220" rx="200" ry="80" fill="rgba(180,140,80,0.08)" />
            <ellipse cx="260" cy="250" rx="120" ry="50" fill="rgba(180,140,80,0.06)" />

            <!-- 黄河（粗细不均） -->
            <path
              d="M 510 190 C 520 210 530 220 535 240 C 540 255 545 260 545 265
                 C 545 270 550 278 555 290 C 560 300 570 310 580 318"
              stroke="#c4a35a"
              stroke-width="4"
              fill="none"
              stroke-linecap="round"
              opacity="0.6"
            />

            <!-- 长江（粗细不均） -->
            <path
              d="M 510 280 C 540 290 580 300 620 310 C 645 316 660 325 675 340
                 C 685 350 688 360 685 370 C 682 380 675 390 665 400
                 C 658 408 650 415 640 420"
              stroke="#7a9e7a"
              stroke-width="4"
              fill="none"
              stroke-linecap="round"
              opacity="0.5"
            />

            <!-- 海岸线（中国东部轮廓简化） -->
            <path
              d="M 540 190
                 C 545 195 555 200 560 210
                 C 565 220 568 230 572 240
                 C 576 250 578 260 580 270
                 C 585 280 590 288 595 295
                 C 605 305 615 312 625 318
                 C 635 325 645 330 655 335
                 C 665 340 672 345 678 350
                 C 682 358 685 368 683 378
                 C 681 388 676 398 670 408
                 C 664 418 656 428 648 438
                 C 644 445 638 452 632 458
                 C 626 464 620 468 614 472"
              stroke="#8b7355"
              stroke-width="1.5"
              fill="none"
              opacity="0.5"
            />

            <!-- 北部边界（长城示意） -->
            <path
              d="M 280 180 C 320 175 380 172 440 175 C 490 178 530 183 560 190
                 C 580 195 595 202 600 210"
              stroke="#9b7a5a"
              stroke-width="1.5"
              fill="none"
              stroke-dasharray="6,4"
              opacity="0.4"
            />

            <!-- 西部高原山脉示意 -->
            <path
              d="M 250 260 C 280 245 310 240 340 238 C 370 236 400 240 430 248
                 C 460 256 480 265 500 275"
              stroke="#a08060"
              stroke-width="1"
              fill="none"
              opacity="0.3"
            />
            <path
              d="M 300 300 C 330 295 360 295 390 300 C 420 305 450 315 470 325"
              stroke="#a08060"
              stroke-width="1"
              fill="none"
              opacity="0.25"
            />

            <!-- 南部海岸 -->
            <path
              d="M 614 472 C 610 480 605 488 598 495 C 590 503 580 510 570 515
                 C 558 520 545 522 535 520 C 525 518 515 514 508 508"
              stroke="#8b7355"
              stroke-width="1.5"
              fill="none"
              opacity="0.4"
            />

            <!-- 台湾岛 -->
            <path
              d="M 690 410 C 695 405 700 400 700 395 C 700 390 698 385 694 385
                 C 690 385 686 388 684 393 C 682 398 684 405 690 410"
              stroke="#8b7355"
              stroke-width="1"
              fill="rgba(139,115,85,0.1)"
              opacity="0.4"
            />

            <!-- 海南岛 -->
            <path
              d="M 635 455 C 640 450 645 447 647 443 C 649 439 648 435 644 435
                 C 640 435 636 438 634 442 C 632 446 632 452 635 455"
              stroke="#8b7355"
              stroke-width="1"
              fill="rgba(139,115,85,0.1)"
              opacity="0.4"
            />

            <!-- 东北轮廓 -->
            <path
              d="M 540 190 C 545 175 555 165 565 160 C 575 155 585 158 595 165
                 C 605 172 610 180 608 190"
              stroke="#8b7355"
              stroke-width="1"
              fill="none"
              opacity="0.3"
            />

            <!-- 四大方位标注 -->
            <text x="760" y="30" font-size="11" fill="#8b7355" opacity="0.5" font-family="serif">东</text>
            <text x="10" y="30" font-size="11" fill="#8b7355" opacity="0.5" font-family="serif">西</text>
            <text x="390" y="20" font-size="11" fill="#8b7355" opacity="0.5" font-family="serif">北</text>
            <text x="390" y="590" font-size="11" fill="#8b7355" opacity="0.5" font-family="serif">南</text>

            <!-- 地图标记点 -->
            <g v-for="marker in mapMarkers" :key="marker.location">
              <!-- 外圈晕 -->
              <circle
                :cx="marker.x"
                :cy="marker.y"
                r="14"
                :fill="marker.stories.some(s => s.status === 2) ? 'rgba(139,115,85,0.12)' : 'rgba(107,142,107,0.12)'"
              />
              <!-- 中圈 -->
              <circle
                :cx="marker.x"
                :cy="marker.y"
                r="9"
                :fill="marker.stories.some(s => s.status === 2) ? '#8b7355' : '#6b8e6b'"
                class="map-marker-dot"
                :class="{ 'has-completed': marker.stories.some(s => s.status === 2) }"
                data-testid="map-marker"
                @click.stop="openMapMarker(marker)"
              />
              <!-- 位置简称 -->
              <text
                :x="marker.x"
                :y="marker.y + 4"
                text-anchor="middle"
                font-size="8"
                fill="#f5efe0"
                font-family="'Ma Shan Zheng', 'STKaiti', serif"
                style="pointer-events: none; user-select: none;"
              >{{ marker.label }}</text>
            </g>

            <!-- 空白状态提示 -->
            <g v-if="mapMarkers.length === 0">
              <text
                x="400" y="300" text-anchor="middle"
                font-size="14" fill="#8b7355" opacity="0.5"
                font-family="'Ma Shan Zheng', serif"
              >山河无言，待风云际会</text>
            </g>
          </svg>

          <!-- 故事列表浮层 -->
          <Transition name="marker-popup">
            <div
              v-if="selectedMapMarker"
              class="map-popup"
              @click.stop
            >
              <div class="map-popup-header">
                <span class="map-popup-location">{{ selectedMapMarker.location }}</span>
                <button class="map-popup-close" @click="closeMapMarker">✕</button>
              </div>
              <div class="map-popup-stories">
                <div
                  v-for="story in selectedMapMarker.stories"
                  :key="story.id"
                  class="map-popup-story"
                  data-testid="map-story-item"
                  @click="openDetail(story); closeMapMarker()"
                >
                  <div class="map-story-title">{{ story.title }}</div>
                  <div class="map-story-meta">
                    <span class="story-status" :class="statusClass[story.status]">
                      {{ statusLabel[story.status] }}
                    </span>
                  </div>
                </div>
              </div>
            </div>
          </Transition>
        </div>
      </template>

      <template v-else-if="isEmpty">
        <div class="shanhe-map-wrapper">
          <svg class="shanhe-map" viewBox="0 0 800 600" xmlns="http://www.w3.org/2000/svg">
            <rect width="800" height="600" fill="#f0e8d5" />
            <text x="400" y="300" text-anchor="middle" font-size="16" fill="#8b7355" opacity="0.4"
              font-family="'Ma Shan Zheng', serif">山河无言，待风云际会</text>
          </svg>
        </div>
        <p class="empty-hint" data-testid="empty-bookshelf-message">暂无故事记录</p>
        <button class="start-btn" data-testid="start-new-story-button" @click="startNewStory">开始新故事</button>
      </template>

      <template v-else>
        <div class="shanhe-map-wrapper">
          <svg class="shanhe-map" viewBox="0 0 800 600" xmlns="http://www.w3.org/2000/svg">
            <rect width="800" height="600" fill="#f0e8d5" />
            <text x="400" y="300" text-anchor="middle" font-size="14" fill="#8b7355" opacity="0.4"
              font-family="'Ma Shan Zheng', serif">无故事于此</text>
          </svg>
        </div>
      </template>
    </div>

    <!-- 老式书架视图 -->
    <div v-if="viewMode === 'bookshelf'" class="bookshelf-old-view" data-testid="bookshelf-old-view">
      <!-- 书架整体背景（深色木质） -->
      <div class="old-bookshelf-bg">
        <!-- 书架装饰层（顶部花瓶/摆件） -->
        <div class="shelf-top-decor">
          <div class="decor-vase">
            <svg viewBox="0 0 40 60" fill="none" xmlns="http://www.w3.org/2000/svg">
              <ellipse cx="20" cy="55" rx="12" ry="4" fill="#6b4226"/>
              <path d="M10 55 Q8 35 12 20 Q16 10 20 8 Q24 10 28 20 Q32 35 30 55Z" fill="#c4785a" opacity="0.85"/>
              <path d="M15 8 Q20 3 25 8" stroke="#5a8a5a" stroke-width="1.5" fill="none"/>
              <ellipse cx="20" cy="6" rx="4" ry="2" fill="#5a8a5a" opacity="0.7"/>
            </svg>
          </div>
          <div class="decor-scroll">
            <svg viewBox="0 0 50 30" fill="none" xmlns="http://www.w3.org/2000/svg">
              <rect x="2" y="5" width="46" height="20" rx="2" fill="#e8d5c4" opacity="0.8"/>
              <rect x="2" y="5" width="6" height="20" rx="1" fill="#d4b98a"/>
              <rect x="42" y="5" width="6" height="20" rx="1" fill="#d4b98a"/>
              <line x1="12" y1="10" x2="38" y2="10" stroke="#8b7355" stroke-width="0.75" opacity="0.4"/>
              <line x1="12" y1="14" x2="35" y2="14" stroke="#8b7355" stroke-width="0.75" opacity="0.4"/>
              <line x1="12" y1="18" x2="30" y2="18" stroke="#8b7355" stroke-width="0.75" opacity="0.4"/>
            </svg>
          </div>
        </div>

        <!-- 书架行 -->
        <template v-if="bookshelfRows.length > 0">
          <div
            v-for="(row, rowIdx) in bookshelfRows"
            :key="rowIdx"
            class="old-bookshelf-row"
          >
            <!-- 书架背板（木质深色） -->
            <div class="shelf-back-panel"></div>

            <!-- 书籍层（带前后层次阴影） -->
            <div class="old-books-layer">
              <!-- 前排书籍（书脊朝外） -->
              <div class="old-books-row">
                <div
                  v-for="story in row"
                  :key="story.id"
                  class="old-book-spine"
                  :class="{ completed: story.status === 2, 'in-progress': story.status === 1 }"
                  :style="{
                    height: spineHeight(story.wordCount) + 'px',
                    // 根据偏离度调整书脊颜色深浅
                    filter: story.historyDeviation !== 0
                      ? `brightness(${1 - Math.min(Math.abs(story.historyDeviation) * 0.01, 0.3)})`
                      : 'none'
                  }"
                  data-testid="old-story-card"
                  :data-status="story.status === 2 ? 'completed' : 'in_progress'"
                  @click="openDetail(story)"
                >
                  <!-- 书脊顶部色带 -->
                  <div class="old-spine-top-band"></div>

                  <!-- 题签（手写风格竖排） -->
                  <div class="old-spine-title-slip">
                    <div class="old-slip-bg"></div>
                    <span class="old-slip-title" data-testid="old-story-title">{{ story.title }}</span>
                  </div>

                  <!-- 题签装饰线 -->
                  <div class="old-slip-divider"></div>

                  <!-- 关键词/标签 -->
                  <div class="old-spine-keyword" data-testid="old-story-keyword">
                    {{ story.keywords?.[0]?.name ?? story.eventName ?? '' }}
                  </div>

                  <!-- 书脊底部：朱红印鉴（仅已完成） -->
                  <div v-if="story.status === 2" class="old-seal-mark" title="已完成">
                    <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                      <rect x="1" y="1" width="22" height="22" rx="2" stroke="#B22222" stroke-width="1.5"/>
                      <rect x="3" y="3" width="18" height="18" rx="1" stroke="#B22222" stroke-width="0.5" stroke-dasharray="2 2"/>
                      <text x="12" y="15" text-anchor="middle" font-size="7" fill="#B22222" font-family="serif">完</text>
                    </svg>
                  </div>
                </div>
              </div>
            </div>

            <!-- 书架横梁（层与层之间的木板） -->
            <div class="shelf-beam"></div>
          </div>
        </template>

        <template v-else-if="isEmpty">
          <div class="old-empty-bookshelf">
            <p class="empty-hint" data-testid="empty-bookshelf-message">书架空空，暂无故事记录</p>
            <button class="start-btn" data-testid="start-new-story-button" @click="startNewStory">开始新故事</button>
          </div>
        </template>

        <template v-else>
          <p class="empty-hint">没有符合筛选条件的记录</p>
        </template>

        <!-- 底层抽屉装饰 -->
        <div class="shelf-bottom-drawers">
          <div class="drawer-face">
            <div class="drawer-handle"></div>
          </div>
          <div class="drawer-face">
            <div class="drawer-handle"></div>
          </div>
        </div>
      </div>
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
  padding: 0;
}

.shanhe-map-wrapper {
  position: relative;
  padding: 1rem;
}

.shanhe-map {
  width: 100%;
  max-width: 800px;
  height: auto;
  display: block;
  margin: 0 auto;
  border: 1px solid rgba(139, 115, 85, 0.2);
  border-radius: 4px;
  background: #f0e8d5;
  box-shadow: inset 0 0 30px rgba(139, 90, 43, 0.08);
}

/* 地图标记点交互 */
.map-marker-dot {
  cursor: pointer;
  transition: r 0.15s ease, opacity 0.15s ease;
}

.map-marker-dot:hover {
  r: 11;
  filter: drop-shadow(0 0 4px rgba(139, 115, 85, 0.5));
}

.map-marker-dot.has-completed:hover {
  filter: drop-shadow(0 0 4px rgba(139, 115, 85, 0.6));
}

/* 故事列表浮层 */
.map-popup {
  position: absolute;
  top: 1.5rem;
  right: 1.5rem;
  width: 220px;
  background: #f5efe0;
  border: 1px solid rgba(139, 115, 85, 0.3);
  border-radius: 6px;
  box-shadow: 0 4px 20px rgba(44, 31, 20, 0.25);
  z-index: 100;
  overflow: hidden;
}

.map-popup-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.6rem 0.75rem;
  border-bottom: 1px solid rgba(139, 115, 85, 0.15);
  background: rgba(139, 115, 85, 0.06);
}

.map-popup-location {
  font-size: 0.85rem;
  font-family: 'Ma Shan Zheng', 'STKaiti', serif;
  color: #2c1f14;
  font-weight: bold;
}

.map-popup-close {
  background: none;
  border: none;
  font-size: 0.9rem;
  color: #8b7355;
  cursor: pointer;
  padding: 0;
  line-height: 1;
}

.map-popup-stories {
  padding: 0.4rem 0;
  max-height: 280px;
  overflow-y: auto;
}

.map-popup-story {
  padding: 0.5rem 0.75rem;
  cursor: pointer;
  transition: background 0.15s;
  border-bottom: 1px solid rgba(139, 115, 85, 0.06);
}

.map-popup-story:last-child {
  border-bottom: none;
}

.map-popup-story:hover {
  background: rgba(139, 115, 85, 0.08);
}

.map-story-title {
  font-size: 0.8rem;
  color: #2c1f14;
  margin-bottom: 0.2rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.map-story-meta {
  display: flex;
  gap: 0.4rem;
}

/* 浮层动画 */
.marker-popup-enter-active,
.marker-popup-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.marker-popup-enter-from,
.marker-popup-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

/* 空状态提示 */
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
  text-align: center;
  color: #8b7355;
  padding: 2rem;
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

/* =============================================
   老式书架视图（bookshelf 模式）
   ============================================= */

.old-bookshelf-bg {
  min-height: calc(100vh - 120px);
  padding-bottom: 2rem;
  /* 深色木质纹理背景 */
  background:
    /* 横向木纹 */
    repeating-linear-gradient(
      90deg,
      transparent 0px,
      transparent 4px,
      rgba(0, 0, 0, 0.04) 4px,
      rgba(0, 0, 0, 0.04) 5px
    ),
    /* 纵向纹理叠加 */
    repeating-linear-gradient(
      180deg,
      transparent 0px,
      transparent 20px,
      rgba(0, 0, 0, 0.03) 20px,
      rgba(0, 0, 0, 0.03) 22px
    ),
    /* 深色渐变底色 */
    linear-gradient(
      180deg,
      #2c1810 0%,
      #3d2415 20%,
      #2c1810 40%,
      #3d2415 60%,
      #2c1810 80%,
      #1a0f0a 100%
    );
  position: relative;
}

/* 顶层装饰 */
.shelf-top-decor {
  display: flex;
  align-items: flex-end;
  gap: 1rem;
  padding: 1.5rem 2rem 0;
  height: 80px;
}

.decor-vase {
  width: 40px;
  height: 60px;
  opacity: 0.85;
}

.decor-scroll {
  width: 50px;
  height: 30px;
  opacity: 0.8;
}

/* 书架行 */
.old-bookshelf-row {
  position: relative;
  margin: 0 1.5rem;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
}

/* 书架背板（深色木质） */
.shelf-back-panel {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 16px;
  background:
    repeating-linear-gradient(
      90deg,
      transparent 0px,
      transparent 8px,
      rgba(0, 0, 0, 0.06) 8px,
      rgba(0, 0, 0, 0.06) 10px
    ),
    linear-gradient(
      180deg,
      #3a2010 0%,
      #2a1508 30%,
      #3a2010 60%,
      #2a1508 100%
    );
  border-radius: 2px;
  z-index: 0;
}

/* 书籍层（带层次感阴影） */
.old-books-layer {
  position: relative;
  z-index: 1;
  padding: 0 6px;
  /* 底部阴影营造前排感 */
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.4);
}

.old-books-row {
  display: flex;
  align-items: flex-end;
  gap: 5px;
  padding-bottom: 2px;
}

/* 书脊 */
.old-book-spine {
  position: relative;
  width: 40px;
  min-width: 40px;
  display: flex;
  flex-direction: column;
  align-items: center;
  border-radius: 2px 2px 0 0;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
  flex-shrink: 0;
  /* 宣纸质感暖色书脊 */
  background:
    repeating-linear-gradient(
      180deg,
      transparent 0px,
      transparent 3px,
      rgba(0, 0, 0, 0.02) 3px,
      rgba(0, 0, 0, 0.02) 4px
    ),
    linear-gradient(
      90deg,
      #f5e6d3 0%,
      #e8d5c4 30%,
      #dcc8b5 60%,
      #e8d5c4 100%
    );
  box-shadow:
    inset 2px 0 4px rgba(0, 0, 0, 0.15),
    inset -1px 0 3px rgba(255, 220, 160, 0.1),
    3px 0 6px rgba(0, 0, 0, 0.3),
    -1px 0 2px rgba(0, 0, 0, 0.1);
}

.old-book-spine:hover {
  transform: translateY(-8px);
  box-shadow:
    inset 2px 0 4px rgba(0, 0, 0, 0.15),
    inset -1px 0 3px rgba(255, 220, 160, 0.1),
    0 12px 20px rgba(0, 0, 0, 0.4),
    -1px 0 2px rgba(0, 0, 0, 0.1);
  z-index: 10;
}

.old-book-spine.completed {
  background:
    repeating-linear-gradient(
      180deg,
      transparent 0px,
      transparent 3px,
      rgba(0, 0, 0, 0.03) 3px,
      rgba(0, 0, 0, 0.03) 4px
    ),
    linear-gradient(
      90deg,
      #f0e0c8 0%,
      #e0ccb0 30%,
      #d4bfa0 60%,
      #e0ccb0 100%
    );
}

.old-book-spine.in-progress {
  background:
    repeating-linear-gradient(
      180deg,
      transparent 0px,
      transparent 3px,
      rgba(0, 0, 0, 0.02) 3px,
      rgba(0, 0, 0, 0.02) 4px
    ),
    linear-gradient(
      90deg,
      #e8f0e0 0%,
      #d8e4cc 30%,
      #ccd8bc 60%,
      #d8e4cc 100%
    );
}

/* 书脊顶部色带 */
.old-spine-top-band {
  width: 100%;
  height: 6px;
  background: linear-gradient(180deg, #5c3a20, #8b5a2b);
  border-radius: 2px 2px 0 0;
  flex-shrink: 0;
}

/* 题签 */
.old-spine-title-slip {
  flex: 1;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  padding: 6px 4px;
  overflow: hidden;
}

.old-slip-bg {
  position: absolute;
  inset: 2px;
  background: rgba(255, 248, 235, 0.55);
  border-radius: 1px;
}

.old-slip-title {
  position: relative;
  z-index: 1;
  writing-mode: vertical-rl;
  text-orientation: mixed;
  font-size: 11px;
  font-family: 'Ma Shan Zheng', 'FZQingKeBenYueSong', 'STKaiti', 'KaiTi', '楷体', serif;
  color: #3d2914;
  letter-spacing: 0.12em;
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: -webkit-box;
  -webkit-box-orient: horizontal;
  -webkit-line-clamp: 1;
  max-height: 100%;
}

/* 题签装饰线 */
.old-slip-divider {
  width: 65%;
  height: 1px;
  background: linear-gradient(
    90deg,
    transparent,
    rgba(139, 90, 43, 0.45),
    transparent
  );
  margin: 1px 0;
  flex-shrink: 0;
}

/* 关键词 */
.old-spine-keyword {
  font-size: 9px;
  color: #8b7355;
  writing-mode: vertical-rl;
  text-orientation: mixed;
  padding: 2px 4px 4px;
  letter-spacing: 0.05em;
  font-family: 'STKaiti', 'KaiTi', '楷体', serif;
  max-height: 30px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: -webkit-box;
  -webkit-box-orient: horizontal;
  -webkit-line-clamp: 1;
  flex-shrink: 0;
}

/* 朱红印鉴 */
.old-seal-mark {
  width: 20px;
  height: 20px;
  padding: 2px;
  flex-shrink: 0;
}

.old-seal-mark svg {
  width: 100%;
  height: 100%;
}

/* 书架横梁 */
.shelf-beam {
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
    0 4px 8px rgba(0, 0, 0, 0.5),
    inset 0 1px 0 rgba(255, 220, 160, 0.2);
  border-radius: 0 0 2px 2px;
  position: relative;
  z-index: 2;
}

/* 底层抽屉装饰 */
.shelf-bottom-drawers {
  display: flex;
  gap: 8px;
  justify-content: center;
  padding: 1rem 2rem 0;
  margin-top: -4px;
}

.drawer-face {
  width: 120px;
  height: 40px;
  background:
    linear-gradient(
      180deg,
      #7a4f2e 0%,
      #5c3a20 40%,
      #6b4226 70%,
      #4a2e14 100%
    );
  border: 1px solid #a07040;
  border-radius: 2px;
  box-shadow:
    inset 0 1px 0 rgba(255, 220, 160, 0.15),
    0 2px 4px rgba(0, 0, 0, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
}

.drawer-handle {
  width: 24px;
  height: 6px;
  background: linear-gradient(180deg, #c9a84c, #a08040);
  border-radius: 3px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.3);
}

/* 空书架提示 */
.old-empty-bookshelf {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 300px;
  gap: 1rem;
}

.old-empty-bookshelf .empty-hint {
  color: #c9a84c;
  font-size: 1rem;
  font-family: 'Ma Shan Zheng', serif;
}
</style>
