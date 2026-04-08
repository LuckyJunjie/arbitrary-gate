<script setup lang="ts">
import { ref } from 'vue'
import InkPool from '@/components/InkPool.vue'
import Card from '@/components/Card.vue'
import RippleEffect from '@/components/RippleEffect.vue'

const hasDrawn = ref(false)
const drawnCard = ref<Record<string, unknown> | null>(null)

function onCardDrawn(card: Record<string, unknown>) {
  drawnCard.value = card
  hasDrawn.value = true
}

function reset() {
  hasDrawn.value = false
  drawnCard.value = null
}
</script>

<template>
  <div class="pool-view">
    <header class="pool-header">
      <h2>墨池</h2>
      <!-- TODO: 墨晶余额显示 -->
    </header>

    <div class="pool-stage">
      <RippleEffect :active="!hasDrawn" />
      <InkPool v-if="!hasDrawn" @draw="onCardDrawn" />
      <Card v-else :card="drawnCard" @flip="reset" />
    </div>

    <!-- TODO: 抽卡记录列表 -->
  </div>
</template>

<style scoped>
.pool-view {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: radial-gradient(ellipse at center, #1a1510 0%, #0d0a08 100%);
  color: #e8dcc8;
}

.pool-header {
  padding: 1.5rem;
  text-align: center;
  border-bottom: 1px solid rgba(232, 220, 200, 0.1);
}

.pool-header h2 {
  font-size: 1.5rem;
  letter-spacing: 0.2em;
}

.pool-stage {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
}
</style>
