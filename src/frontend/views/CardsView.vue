<script setup lang="ts">
import { ref, computed } from 'vue'
import { useCardStore } from '@/stores/cardStore'

const cardStore = useCardStore()
const activeTab = ref<'keyword' | 'event'>('keyword')
const activeRarity = ref<number | null>(null)

const filteredCards = computed(() => {
  const cards = activeTab.value === 'keyword'
    ? cardStore.keywordCards
    : cardStore.eventCards
  if (activeRarity.value === null) return cards
  return cards.filter(c => c.rarity === activeRarity.value)
})
</script>

<template>
  <div class="cards-view">
    <header class="cards-header">
      <h2>卡匣</h2>
      <p class="card-count">共 {{ cardStore.totalCount }} 张</p>
    </header>

    <nav class="filter-tabs">
      <button
        :class="['tab', { active: activeTab === 'keyword' }]"
        @click="activeTab = 'keyword'"
      >关键词</button>
      <button
        :class="['tab', { active: activeTab === 'event' }]"
        @click="activeTab = 'event'"
      >事件</button>
    </nav>

    <div class="rarity-filter">
      <button
        v-for="rarity in [null, 1, 2, 3, 4]"
        :key="rarity ?? 'all'"
        :class="['rarity-btn', { active: activeRarity === rarity }]"
        @click="activeRarity = rarity"
      >
        {{ rarity === null ? '全部' : ['凡', '珍', '奇', '绝'][rarity - 1] }}
      </button>
    </div>

    <!-- 多宝格卡片墙 -->
    <div class="card-grid">
      <div
        v-for="card in filteredCards"
        :key="card.id"
        class="card-slot"
      >
        <!-- TODO: 卡片组件渲染 -->
        <div class="card-placeholder">
          {{ card.name }}
        </div>
      </div>
    </div>

    <!-- TODO: 空状态 -->
  </div>
</template>

<style scoped>
.cards-view {
  min-height: 100vh;
  background: #f5efe0;
  padding-bottom: 2rem;
}

.cards-header {
  padding: 1.5rem;
  text-align: center;
  border-bottom: 1px solid rgba(44, 31, 20, 0.1);
}

.cards-header h2 {
  font-size: 1.5rem;
  color: #2c1f14;
}

.card-count {
  font-size: 0.8rem;
  color: #8b7355;
  margin-top: 0.25rem;
}

.filter-tabs {
  display: flex;
  justify-content: center;
  gap: 2rem;
  padding: 1rem;
  border-bottom: 1px solid rgba(44, 31, 20, 0.1);
}

.tab {
  background: none;
  border: none;
  font-family: inherit;
  font-size: 1rem;
  color: #8b7355;
  cursor: pointer;
  padding: 0.25rem 0;
  border-bottom: 2px solid transparent;
  transition: all 0.2s;
}

.tab.active {
  color: #2c1f14;
  border-bottom-color: #8b7355;
}

.rarity-filter {
  display: flex;
  justify-content: center;
  gap: 0.5rem;
  padding: 0.75rem;
  flex-wrap: wrap;
}

.rarity-btn {
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

.rarity-btn.active {
  background: #2c1f14;
  color: #f5efe0;
  border-color: #2c1f14;
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(100px, 1fr));
  gap: 0.75rem;
  padding: 1rem;
}

.card-slot {
  aspect-ratio: 3 / 4;
  border: 1px solid rgba(44, 31, 20, 0.15);
  border-radius: 2px;
  overflow: hidden;
}

.card-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.75rem;
  color: #8b7355;
  background: rgba(200, 180, 140, 0.1);
}
</style>
