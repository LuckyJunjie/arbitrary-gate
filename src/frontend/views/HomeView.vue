<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div class="home-view">
    <header class="study-header">
      <h1 class="title">时光笺</h1>
      <p class="subtitle">穿越者书房</p>
    </header>

    <nav class="study-nav">
      <!-- TODO: 书房书架背景图 -->
      <button class="nav-btn ink-pool" @click="router.push('/pool')">
        <span class="btn-label">墨池</span>
        <span class="btn-desc">抽取关键词</span>
      </button>

      <button class="nav-btn card-case" @click="router.push('/cards')">
        <span class="btn-label">卡匣</span>
        <span class="btn-desc">已收集卡牌</span>
      </button>

      <button class="nav-btn bookshelf" @click="router.push('/bookshelf')">
        <span class="btn-label">书架</span>
        <span class="btn-desc">我的故事</span>
      </button>
    </nav>

    <!-- TODO: 入局三问引导 -->
    <section class="entry-section">
      <p class="entry-hint">已有足够的关键词与事件？</p>
      <button class="start-story-btn" @click="router.push('/pool')">
        开始新故事
      </button>
    </section>
  </div>
</template>

<style scoped>
.home-view {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-height: 100vh;
  padding: 2rem 1rem;
  background: linear-gradient(180deg, #f5efe0 0%, #ede0c8 100%);
}

.study-header {
  text-align: center;
  margin-bottom: 3rem;
}

.title {
  font-size: 3rem;
  font-weight: 700;
  color: #2c1f14;
  letter-spacing: 0.1em;
  margin-bottom: 0.5rem;
}

.subtitle {
  font-size: 1rem;
  color: #8b7355;
  letter-spacing: 0.2em;
}

.study-nav {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
  width: 100%;
  max-width: 320px;
}

.nav-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 1.5rem 2rem;
  border: 2px solid #8b7355;
  border-radius: 4px;
  background: rgba(245, 239, 224, 0.9);
  cursor: pointer;
  transition: all 0.3s ease;
  font-family: inherit;
}

.nav-btn:hover {
  background: rgba(139, 115, 85, 0.1);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(44, 31, 20, 0.15);
}

.btn-label {
  font-size: 1.25rem;
  font-weight: 600;
  color: #2c1f14;
}

.btn-desc {
  font-size: 0.8rem;
  color: #8b7355;
  margin-top: 0.25rem;
}

.entry-section {
  margin-top: 3rem;
  text-align: center;
}

.entry-hint {
  color: #8b7355;
  font-size: 0.9rem;
  margin-bottom: 1rem;
}

.start-story-btn {
  padding: 0.75rem 2.5rem;
  background: #2c1f14;
  color: #f5efe0;
  border: none;
  border-radius: 2px;
  font-family: inherit;
  font-size: 1rem;
  cursor: pointer;
  letter-spacing: 0.1em;
  transition: background 0.3s ease;
}

.start-story-btn:hover {
  background: #4a3520;
}
</style>
