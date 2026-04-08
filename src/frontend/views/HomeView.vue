<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useStoryStore } from '@/stores/storyStore'
import { useCardStore } from '@/stores/cardStore'

const router = useRouter()
const storyStore = useStoryStore()
const cardStore = useCardStore()

// ── 最近故事 ──
const recentStories = ref<Array<{ id: string; title: string; status: number; currentChapter: number }>>([])

// ── 卡牌数量 badge ──
const totalCardCount = computed(() => cardStore.totalCount)

// ── 背景墨迹动画状态 ──
const inkPulse = ref(false)

onMounted(() => {
  loadRecentStories()
  loadCardCount()
  // 启动墨迹脉冲
  setInterval(() => {
    inkPulse.value = !inkPulse.value
  }, 8000)
})

function loadRecentStories() {
  const saved = localStorage.getItem('recentStories')
  if (saved) {
    try {
      const stories = JSON.parse(saved)
      recentStories.value = stories.slice(0, 3)
    } catch {
      recentStories.value = []
    }
  }
}

function loadCardCount() {
  // 从 localStorage 读取已收集卡牌数量（由 cardStore 同步过来）
  const saved = localStorage.getItem('ownedKeywordCards')
  if (saved) {
    try {
      const cards = JSON.parse(saved)
      cardStore.keywordCards = cards
    } catch { /* ignore */ }
  }
}

function goToStory(id: string) {
  router.push(`/story/${id}`)
}

async function startNewStory() {
  try {
    const story = await storyStore.startStory()
    saveRecentStory({ id: story.id, title: story.title, status: story.status, currentChapter: 1 })
    router.push(`/story/${story.id}`)
  } catch {
    router.push('/pool')
  }
}

function saveRecentStory(story: { id: string; title: string; status: number; currentChapter: number }) {
  const saved = localStorage.getItem('recentStories')
  let stories: Array<{ id: string; title: string; status: number; currentChapter: number }> = saved ? JSON.parse(saved) : []
  stories = stories.filter(s => s.id !== story.id)
  stories.unshift(story)
  stories = stories.slice(0, 10)
  localStorage.setItem('recentStories', JSON.stringify(stories))
}
</script>

<template>
  <div class="home-view">
    <!-- 背景墨迹装饰层 -->
    <div class="bg-ink-layer" aria-hidden="true">
      <div class="bg-ink bg-ink--1" :class="{ pulse: inkPulse }" />
      <div class="bg-ink bg-ink--2" :class="{ pulse: !inkPulse }" />
      <!-- 角落卷轴装饰 -->
      <div class="scroll-deco scroll-deco--top" />
      <div class="scroll-deco scroll-deco--bottom" />
    </div>

    <!-- 顶部书签装饰 -->
    <div class="bookmark-deco" aria-hidden="true">
      <svg viewBox="0 0 60 20" class="bookmark-svg">
        <path d="M0,0 L60,0 L60,20 L30,14 L0,20 Z" fill="rgba(139,115,85,0.15)" />
      </svg>
    </div>

    <!-- 书房头部 -->
    <header class="study-header">
      <div class="header-seal" aria-hidden="true">📜</div>
      <h1 class="title">时光笺</h1>
      <p class="subtitle">穿越者书房</p>
      <div class="header-divider" />
    </header>

    <!-- 导航区 -->
    <nav class="study-nav">
      <!-- 墨池 -->
      <button class="nav-btn nav-btn--pool" @click="router.push('/pool')">
        <span class="btn-icon">墨池</span>
        <span class="btn-label">抽取关键词</span>
        <span class="btn-desc">与历史共鸣</span>
      </button>

      <!-- 卡匣（带 badge） -->
      <button class="nav-btn nav-btn--cards" @click="router.push('/cards')">
        <span class="btn-icon">卡匣</span>
        <span class="btn-label">我的卡牌</span>
        <span v-if="totalCardCount > 0" class="card-badge">{{ totalCardCount > 99 ? '99+' : totalCardCount }}</span>
        <span class="btn-desc">已收集关键词</span>
      </button>

      <!-- 书架 -->
      <button class="nav-btn nav-btn--bookshelf" @click="router.push('/bookshelf')">
        <span class="btn-icon">书架</span>
        <span class="btn-label">我的故事</span>
        <span class="btn-desc">延续时光脉络</span>
      </button>
    </nav>

    <!-- 最近故事快速入口 -->
    <section v-if="recentStories.length > 0" class="recent-section">
      <h3 class="section-label">📖 近闻</h3>
      <div class="recent-list">
        <button
          v-for="story in recentStories"
          :key="story.id"
          class="recent-item"
          @click="goToStory(story.id)"
        >
          <div class="recent-left">
            <span class="recent-title">{{ story.title || '未命名故事' }}</span>
            <span class="recent-ch">第{{ story.currentChapter || 1 }}章</span>
          </div>
          <div class="recent-right">
            <span
              class="recent-status"
              :class="story.status === 2 ? 'status--done' : 'status--ongoing'"
            >
              {{ story.status === 2 ? '已完结' : '进行中' }}
            </span>
            <span class="recent-arrow">›</span>
          </div>
        </button>
      </div>
    </section>

    <!-- 空白状态提示 -->
    <section v-else class="empty-state">
      <p class="empty-hint">尚无故事记录</p>
      <p class="empty-sub">前往墨池抽取关键词，开启你的第一个故事</p>
    </section>

    <!-- 开始新故事引导 -->
    <section class="entry-section">
      <div class="entry-scroll-deco" aria-hidden="true">
        <div class="scroll-end" />
        <div class="scroll-body" />
        <div class="scroll-end" />
      </div>
      <p class="entry-hint">关键词已备，时机已至</p>
      <button class="start-story-btn" @click="startNewStory">
        <span class="btn-inner">开卷启题</span>
        <span class="btn-inner-sub">穿越者踏入时光</span>
      </button>
    </section>

    <!-- 底部墨迹装饰 -->
    <div class="footer-ink" aria-hidden="true">
      <div class="footer-ink-blob" />
    </div>
  </div>
</template>

<style scoped>
.home-view {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-height: 100vh;
  padding: 3rem 1.5rem 4rem;
  background: linear-gradient(180deg, #f5efe0 0%, #ede0c8 60%, #e5d8b8 100%);
  position: relative;
  overflow: hidden;
}

/* ── 背景墨迹层 ── */
.bg-ink-layer {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 0;
  overflow: hidden;
}

.bg-ink {
  position: absolute;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(44, 31, 20, 0.07) 0%, transparent 70%);
  transition: transform 4s ease;
}

.bg-ink--1 {
  width: 350px;
  height: 350px;
  top: -80px;
  right: -60px;
  transform-origin: center;
}

.bg-ink--2 {
  width: 250px;
  height: 250px;
  bottom: -40px;
  left: -40px;
  transform-origin: center;
}

.bg-ink.pulse {
  transform: scale(1.12);
}

@keyframes ink-drift {
  0%, 100% { transform: translate(0, 0) scale(1); }
  50% { transform: translate(8px, 6px) scale(1.05); }
}

/* 卷轴装饰 */
.scroll-deco {
  position: absolute;
  left: 0;
  right: 0;
  height: 8px;
  background: linear-gradient(90deg,
    transparent 0%,
    rgba(139, 115, 85, 0.2) 15%,
    rgba(139, 115, 85, 0.25) 50%,
    rgba(139, 115, 85, 0.2) 85%,
    transparent 100%
  );
}

.scroll-deco--top { top: 0; }
.scroll-deco--bottom { bottom: 0; }

/* ── 顶部书签 ── */
.bookmark-deco {
  position: absolute;
  top: 0;
  left: 50%;
  transform: translateX(-50%);
  z-index: 2;
}

.bookmark-svg {
  width: 60px;
  height: 20px;
}

/* ── 头部 ── */
.study-header {
  text-align: center;
  margin-bottom: 2.5rem;
  position: relative;
  z-index: 1;
}

.header-seal {
  font-size: 2rem;
  margin-bottom: 0.5rem;
  opacity: 0.6;
}

.title {
  font-size: 3rem;
  font-weight: 700;
  color: #2c1f14;
  letter-spacing: 0.12em;
  margin-bottom: 0.4rem;
  text-shadow: 0 2px 4px rgba(44, 31, 20, 0.15);
}

.subtitle {
  font-size: 0.9rem;
  color: #8b7355;
  letter-spacing: 0.3em;
  font-weight: 300;
}

.header-divider {
  margin-top: 1.2rem;
  width: 60px;
  height: 2px;
  background: linear-gradient(90deg, transparent, #8b7355, transparent);
  margin-left: auto;
  margin-right: auto;
}

/* ── 导航 ── */
.study-nav {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  width: 100%;
  max-width: 340px;
  position: relative;
  z-index: 1;
}

.nav-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 1.3rem 2rem;
  border: 1.5px solid rgba(139, 115, 85, 0.5);
  border-radius: 6px;
  background: rgba(245, 239, 224, 0.85);
  backdrop-filter: blur(4px);
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  font-family: inherit;
  position: relative;
  overflow: hidden;
  text-align: center;
}

.nav-btn::before {
  content: '';
  position: absolute;
  inset: 0;
  background: radial-gradient(circle at 30% 50%, rgba(139, 115, 85, 0.08) 0%, transparent 60%);
  opacity: 0;
  transition: opacity 0.3s;
}

.nav-btn:hover::before {
  opacity: 1;
}

.nav-btn:hover {
  transform: translateY(-3px);
  box-shadow: 0 6px 20px rgba(44, 31, 20, 0.18);
  border-color: rgba(139, 115, 85, 0.8);
  background: rgba(245, 239, 224, 0.95);
}

.btn-icon {
  font-size: 1.1rem;
  font-weight: 600;
  color: #2c1f14;
  letter-spacing: 0.15em;
  margin-bottom: 0.2rem;
}

.btn-label {
  font-size: 0.95rem;
  color: #2c1f14;
  font-weight: 500;
}

.btn-desc {
  font-size: 0.72rem;
  color: #8b7355;
  margin-top: 0.2rem;
  letter-spacing: 0.05em;
}

/* 卡匣 badge */
.card-badge {
  position: absolute;
  top: 0.6rem;
  right: 0.8rem;
  min-width: 1.4rem;
  height: 1.4rem;
  padding: 0 0.3rem;
  background: linear-gradient(135deg, #c47c5a, #8b5a3c);
  color: #f5efe0;
  border-radius: 20px;
  font-size: 0.65rem;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 1px 4px rgba(0,0,0,0.2);
  animation: badge-pop 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}

@keyframes badge-pop {
  from { transform: scale(0); }
  to { transform: scale(1); }
}

/* ── 最近故事 ── */
.recent-section {
  width: 100%;
  max-width: 340px;
  margin-top: 2rem;
  position: relative;
  z-index: 1;
}

.section-label {
  font-size: 0.72rem;
  color: #8b7355;
  letter-spacing: 0.18em;
  margin-bottom: 0.7rem;
  display: flex;
  align-items: center;
  gap: 0.3rem;
}

.recent-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.recent-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.75rem 1rem;
  background: rgba(245, 239, 224, 0.7);
  border: 1px solid rgba(139, 115, 85, 0.3);
  border-radius: 5px;
  cursor: pointer;
  transition: all 0.2s ease;
  font-family: inherit;
  width: 100%;
  text-align: left;
}

.recent-item:hover {
  background: rgba(139, 115, 85, 0.12);
  transform: translateX(4px);
  border-color: rgba(139, 115, 85, 0.5);
}

.recent-left {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.recent-title {
  font-size: 0.9rem;
  color: #2c1f14;
  font-weight: 500;
}

.recent-ch {
  font-size: 0.7rem;
  color: #8b7355;
  letter-spacing: 0.05em;
}

.recent-right {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.recent-status {
  font-size: 0.7rem;
  letter-spacing: 0.05em;
}

.status--ongoing { color: #6b8e6b; }
.status--done { color: #8b7355; }

.recent-arrow {
  color: #8b7355;
  font-size: 1.1rem;
}

/* ── 空白状态 ── */
.empty-state {
  margin-top: 2rem;
  text-align: center;
  position: relative;
  z-index: 1;
}

.empty-hint {
  color: #8b7355;
  font-size: 0.9rem;
  margin-bottom: 0.4rem;
}

.empty-sub {
  color: #a09080;
  font-size: 0.78rem;
}

/* ── 开始新故事 ── */
.entry-section {
  margin-top: 2.5rem;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
  position: relative;
  z-index: 1;
}

/* 卷轴分隔装饰 */
.entry-scroll-deco {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 200px;
}

.scroll-end {
  width: 60px;
  height: 8px;
  background: linear-gradient(90deg, transparent, rgba(139, 115, 85, 0.4), transparent);
  border-radius: 4px;
}

.scroll-body {
  width: 180px;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(139, 115, 85, 0.3), transparent);
}

.entry-hint {
  color: #8b7355;
  font-size: 0.82rem;
  letter-spacing: 0.12em;
}

.start-story-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 1rem 3rem;
  background: linear-gradient(135deg, #2c1f14 0%, #3d2a1a 100%);
  border: none;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: 0 4px 16px rgba(44, 31, 20, 0.3);
  position: relative;
  overflow: hidden;
  font-family: inherit;
}

.start-story-btn::before {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, rgba(196, 168, 130, 0.1) 0%, transparent 60%);
  opacity: 0;
  transition: opacity 0.3s;
}

.start-story-btn:hover::before {
  opacity: 1;
}

.start-story-btn:hover {
  background: linear-gradient(135deg, #3d2a1a 0%, #4a3520 100%);
  box-shadow: 0 6px 24px rgba(44, 31, 20, 0.4);
  transform: translateY(-2px);
}

.btn-inner {
  color: #e8dcc8;
  font-size: 1.1rem;
  font-weight: 600;
  letter-spacing: 0.15em;
}

.btn-inner-sub {
  color: rgba(232, 220, 200, 0.5);
  font-size: 0.7rem;
  margin-top: 0.2rem;
  letter-spacing: 0.08em;
}

/* ── 底部墨迹 ── */
.footer-ink {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 120px;
  pointer-events: none;
  overflow: hidden;
}

.footer-ink-blob {
  position: absolute;
  bottom: -40px;
  left: 50%;
  transform: translateX(-50%);
  width: 400px;
  height: 100px;
  background: radial-gradient(ellipse at center, rgba(44, 31, 20, 0.08) 0%, transparent 70%);
  border-radius: 50%;
}
</style>
