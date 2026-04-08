<script setup lang="ts">
import { ref } from 'vue'

interface CardData {
  id: number
  name: string
  rarity?: number
  category?: number
  imageUrl?: string
  [key: string]: unknown
}

const props = defineProps<{
  card: CardData | null
}>()

const emit = defineEmits<{
  flip: []
}>()

const isFlipped = ref(false)

function handleClick() {
  if (!isFlipped.value) {
    isFlipped.value = true
  } else {
    emit('flip')
  }
}

const rarityLabel = ['', '凡', '珍', '奇', '绝']
const categoryLabel = ['', '器物', '职人', '风物', '情绪', '称谓']
</script>

<template>
  <div
    class="card"
    :class="[`rarity-${card?.rarity ?? 1}`, { flipped: isFlipped }]"
    @click="handleClick"
    role="button"
    tabindex="0"
    @keydown.enter="handleClick"
  >
    <!-- 卡片背面（翻转前显示） -->
    <div class="card-back">
      <div class="back-pattern">
        <span>时光笺</span>
      </div>
    </div>

    <!-- 卡片正面 -->
    <div class="card-front">
      <!-- 卡面图案区 -->
      <div class="card-art">
        <!-- TODO: 卡图（imageUrl） -->
        <div class="art-placeholder">
          {{ card?.name?.charAt(0) ?? '?' }}
        </div>
      </div>

      <!-- 卡名 -->
      <div class="card-name">
        {{ card?.name ?? '未知' }}
      </div>

      <!-- 稀有度标记 -->
      <div v-if="card?.rarity" class="card-rarity">
        {{ rarityLabel[card.rarity] }}
      </div>

      <!-- 分类 -->
      <div v-if="card?.category" class="card-category">
        {{ categoryLabel[card.category] }}
      </div>
    </div>
  </div>
</template>

<style scoped>
.card {
  width: 160px;
  aspect-ratio: 3 / 4;
  perspective: 800px;
  cursor: pointer;
}

.card-back,
.card-front {
  position: absolute;
  inset: 0;
  border-radius: 4px;
  backface-visibility: hidden;
  transition: transform 0.6s ease;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.card-back {
  background: linear-gradient(135deg, #2c1f14 0%, #4a3520 100%);
  border: 2px solid #8b7355;
}

.back-pattern {
  font-size: 1.5rem;
  color: #8b7355;
  letter-spacing: 0.2em;
  opacity: 0.6;
}

.card-front {
  transform: rotateY(180deg);
  background: #f5efe0;
  border: 2px solid #8b7355;
  padding: 0.5rem;
}

/* 翻转 */
.card.flipped .card-back {
  transform: rotateY(180deg);
}
.card.flipped .card-front {
  transform: rotateY(0deg);
}

/* 稀有度边框颜色 */
.card.rarity-1 .card-front { border-color: #8b7355; }
.card.rarity-2 .card-front { border-color: #6b8e6b; }
.card.rarity-3 .card-front { border-color: #7b6ba0; }
.card.rarity-4 .card-front { border-color: #c9a84c; }

.card-art {
  flex: 1;
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(200, 180, 140, 0.1);
  border-radius: 2px;
  margin-bottom: 0.5rem;
}

.art-placeholder {
  font-size: 2.5rem;
  color: #8b7355;
  font-weight: 700;
}

.card-name {
  font-size: 1rem;
  font-weight: 600;
  color: #2c1f14;
  text-align: center;
}

.card-rarity {
  font-size: 0.7rem;
  color: #8b7355;
  margin-top: 0.25rem;
  letter-spacing: 0.1em;
}

.card-category {
  font-size: 0.65rem;
  color: #a08060;
  margin-top: 0.1rem;
}
</style>
