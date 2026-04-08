<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const shareCode = route.params.code as string
const shareData = ref<Record<string, unknown> | null>(null)

// TODO: 调用 API 获取分享内容
// TODO: 渲染缺角故事卡 Canvas
// TODO: 合券验证入口

onMounted(() => {
  // TODO: fetch share data
})
</script>

<template>
  <div class="share-view">
    <header class="share-header">
      <h2>时光笺 · 故事卡</h2>
    </header>

    <div class="share-card">
      <!-- TODO: Canvas 渲染缺角故事卡 -->
      <div class="card-placeholder">
        <p>故事卡加载中...</p>
      </div>
    </div>

    <div class="share-actions">
      <!-- TODO: 缺角码显示 -->
      <!-- TODO: 合券入口 -->
    </div>

    <p class="copyright">由 时光笺 · 任意门 生成</p>
  </div>
</template>

<style scoped>
.share-view {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: radial-gradient(ellipse at center, #f5efe0 0%, #ede0c8 100%);
  padding: 2rem 1rem;
}

.share-header h2 {
  font-size: 1.25rem;
  color: #2c1f14;
  letter-spacing: 0.2em;
  margin-bottom: 2rem;
}

.share-card {
  width: 100%;
  max-width: 360px;
  aspect-ratio: 3 / 4;
  border: 2px solid #8b7355;
  border-radius: 4px;
  overflow: hidden;
  margin-bottom: 2rem;
}

.card-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(200, 180, 140, 0.1);
  color: #8b7355;
}

.share-actions {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  width: 100%;
  max-width: 280px;
}

.copyright {
  margin-top: 2rem;
  font-size: 0.75rem;
  color: #8b7355;
}
</style>
