<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchCommemorativeCard, type CommemorativeCard } from '../services/api'

const route = useRoute()
const router = useRouter()
const cardId = route.params.cardId as string

const card = ref<CommemorativeCard | null>(null)
const isLoading = ref(true)
const loadError = ref<string | null>(null)

// 半文半白时间格式化
function formatDate(dateStr: string): string {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const year = d.getFullYear()
  const month = d.getMonth() + 1
  const day = d.getDate()

  // 干支纪年（简化版）
  const heavenlyStems = ['甲', '乙', '丙', '丁', '戊', '己', '庚', '辛', '壬', '癸']
  const earthlyBranches = ['子', '丑', '寅', '卯', '辰', '巳', '午', '未', '申', '酉', '戌', '亥']
  const stemIndex = (year - 4) % 10
  const branchIndex = (year - 4) % 12

  // 季节雅称
  const seasonNames: Record<number, string> = {
    1: '正月', 2: '二月', 3: '三月', 4: '四月',
    5: '五月', 6: '六月', 7: '七月', 8: '八月',
    9: '九月', 10: '十月', 11: '冬月', 12: '腊月'
  }

  // 日期雅称
  const dayNames: Record<number, string> = {
    1: '初一', 2: '初二', 3: '初三', 4: '初四', 5: '初五',
    6: '初六', 7: '初七', 8: '初八', 9: '初九', 10: '初十',
    11: '十一', 12: '十二', 13: '十三', 14: '十四', 15: '十五',
    16: '十六', 17: '十七', 18: '十八', 19: '十九', 20: '二十',
    21: '廿一', 22: '廿二', 23: '廿三', 24: '廿四', 25: '廿五',
    26: '廿六', 27: '廿七', 28: '廿八', 29: '廿九', 30: '三十'
  }

  const suffix = day <= 10 ? '朔' : day <= 20 ? '望' : day <= 30 ? '晦' : ''
  return `${heavenlyStems[stemIndex]}${earthlyBranches[branchIndex]}年${seasonNames[month]}${dayNames[day]}${suffix}`
}

// 结局类型描述
function endingTypeLabel(type: string): string {
  const labels: Record<string, string> = {
    '功成名就': '功成名就',
    '归隐山林': '归隐山林',
    '悲剧收场': '悲剧收场',
    '爱情圆满': '爱情圆满',
    '友情长存': '友情长存',
    '宿命难逃': '宿命难逃',
    '意外转折': '意外转折',
    '平淡是真': '平淡是真',
    '未知': '命途未定'
  }
  return labels[type] || type
}

// 稀有度标签颜色
function rarityLabel(rarity: number): string {
  // rarity here is not card rarity but ending type
  return ''
}

async function loadCard() {
  if (!cardId) {
    loadError.value = '无效的纪念卡'
    isLoading.value = false
    return
  }
  try {
    card.value = await fetchCommemorativeCard(Number(cardId))
  } catch (err: any) {
    loadError.value = '加载失败，纪念卡可能不存在'
    console.error('加载纪念卡失败:', err)
  } finally {
    isLoading.value = false
  }
}

function goHome() {
  router.push('/')
}

onMounted(loadCard)
</script>

<template>
  <div class="commemorative-view">
    <!-- 背景装饰层 -->
    <div class="bg-pattern"></div>

    <!-- 加载状态 -->
    <div v-if="isLoading" class="loading-state">
      <div class="loading-card">
        <div class="loading-text">墨痕浮现中...</div>
      </div>
    </div>

    <!-- 错误状态 -->
    <div v-else-if="loadError" class="error-state">
      <div class="error-card">
        <p class="error-title">此笺已逝</p>
        <p class="error-msg">{{ loadError }}</p>
        <button class="error-btn" @click="goHome">返回主页</button>
      </div>
    </div>

    <!-- 纪念卡主体 -->
    <template v-else-if="card">
      <div class="card-container">
        <!-- 装饰角标 -->
        <div class="corner corner-tl"></div>
        <div class="corner corner-tr"></div>
        <div class="corner corner-bl"></div>
        <div class="corner corner-br"></div>

        <!-- 顶部金印标题 -->
        <div class="card-header">
          <div class="seal-wrapper">
            <div class="seal-text">合券纪念</div>
          </div>
          <p class="card-no">{{ card.cardNo }}</p>
        </div>

        <!-- 分隔线 -->
        <div class="divider">
          <div class="divider-line"></div>
          <div class="divider-ornament">◆</div>
          <div class="divider-line"></div>
        </div>

        <!-- 故事标题 -->
        <div class="story-title-block">
          <p class="story-title-label">题</p>
          <p class="story-title">{{ card.storyTitle }}</p>
        </div>

        <!-- 合券时间 -->
        <div class="joint-time-block">
          <p class="joint-time">{{ formatDate(card.jointTime) }}</p>
        </div>

        <!-- 分隔装饰 -->
        <div class="section-divider">
          <span class="section-ornament">◇</span>
        </div>

        <!-- 两用户昵称（对联式排列） -->
        <div class="users-block">
          <div class="user-panel left">
            <p class="user-role">赠</p>
            <p class="user-nickname">{{ card.user1Nickname }}</p>
          </div>
          <div class="users-separator">
            <div class="separator-line"></div>
            <p class="separator-text">合券</p>
            <div class="separator-line"></div>
          </div>
          <div class="user-panel right">
            <p class="user-role">受</p>
            <p class="user-nickname">{{ card.user2Nickname }}</p>
          </div>
        </div>

        <!-- 分隔装饰 -->
        <div class="section-divider">
          <span class="section-ornament">◇</span>
        </div>

        <!-- 专属印记 -->
        <div class="mark-block">
          <p class="mark-label">专属印记</p>
          <div class="mark-box">
            <p class="mark-text">「{{ card.exclusiveMark }}」</p>
          </div>
        </div>

        <!-- 底部结局类型 -->
        <div class="ending-block">
          <p class="ending-type">{{ endingTypeLabel(card.endingType) }}</p>
        </div>

        <!-- 底部签名 -->
        <div class="card-footer">
          <p class="card-footer-text">时光笺 · 任意门</p>
        </div>
      </div>

      <!-- 操作按钮 -->
      <div class="actions">
        <button class="action-btn primary" @click="router.push('/')">返回主页</button>
        <button class="action-btn" @click="router.push(`/story/${card.storyId}`)">阅读故事</button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.commemorative-view {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: #1a1209;
  padding: 2rem 1rem;
  position: relative;
  overflow: hidden;
}

/* 宣纸/绢帛质感背景 */
.bg-pattern {
  position: fixed;
  inset: 0;
  background:
    radial-gradient(ellipse at 20% 20%, rgba(201, 168, 76, 0.04) 0%, transparent 50%),
    radial-gradient(ellipse at 80% 80%, rgba(139, 115, 85, 0.04) 0%, transparent 50%),
    radial-gradient(ellipse at 50% 50%, rgba(44, 31, 20, 0.8) 0%, #0f0a04 100%);
  pointer-events: none;
}

/* 云纹边框 SVG */
.bg-pattern::before {
  content: '';
  position: absolute;
  inset: 0;
  background-image:
    url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='60' height='60' viewBox='0 0 60 60'%3E%3Cpath d='M5 30 Q15 20 25 30 Q35 40 45 30 Q55 20 55 30' stroke='rgba(201,168,76,0.03)' fill='none' stroke-width='1'/%3E%3C/svg%3E");
  background-size: 60px 60px;
  pointer-events: none;
}

/* 加载状态 */
.loading-state {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
}

.loading-card {
  text-align: center;
  padding: 3rem;
  border: 1px solid rgba(201, 168, 76, 0.2);
  border-radius: 4px;
  background: rgba(44, 31, 20, 0.5);
}

.loading-text {
  font-size: 0.9rem;
  color: #8b7355;
  letter-spacing: 0.15em;
}

/* 错误状态 */
.error-state {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
}

.error-card {
  text-align: center;
  padding: 2rem;
}

.error-title {
  font-size: 1.2rem;
  color: #c9a84c;
  letter-spacing: 0.2em;
  margin-bottom: 0.5rem;
}

.error-msg {
  font-size: 0.8rem;
  color: #8b7355;
  margin-bottom: 1.5rem;
}

.error-btn {
  padding: 0.5rem 1.5rem;
  border: 1px solid #8b7355;
  border-radius: 2px;
  background: transparent;
  font-family: inherit;
  font-size: 0.8rem;
  color: #8b7355;
  cursor: pointer;
}

/* 纪念卡主体 */
.card-container {
  position: relative;
  width: 100%;
  max-width: 360px;
  background:
    linear-gradient(135deg, #2a1e10 0%, #1e150a 50%, #2a1e10 100%);
  border: 1px solid rgba(201, 168, 76, 0.35);
  border-radius: 6px;
  padding: 2rem 1.8rem;
  box-shadow:
    0 0 0 1px rgba(201, 168, 76, 0.1),
    0 4px 30px rgba(0, 0, 0, 0.6),
    0 0 60px rgba(201, 168, 76, 0.06),
    inset 0 0 40px rgba(201, 168, 76, 0.03);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1.2rem;
  animation: card-reveal 0.8s var(--ease-out);
}

@keyframes card-reveal {
  from {
    opacity: 0;
    transform: translateY(20px) scale(0.98);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

/* 四角装饰 */
.corner {
  position: absolute;
  width: 24px;
  height: 24px;
  border-color: rgba(201, 168, 76, 0.5);
  border-style: solid;
}

.corner-tl {
  top: 8px;
  left: 8px;
  border-width: 1.5px 0 0 1.5px;
  border-radius: 2px 0 0 0;
}

.corner-tr {
  top: 8px;
  right: 8px;
  border-width: 1.5px 1.5px 0 0;
  border-radius: 0 2px 0 0;
}

.corner-bl {
  bottom: 8px;
  left: 8px;
  border-width: 0 0 1.5px 1.5px;
  border-radius: 0 0 0 2px;
}

.corner-br {
  bottom: 8px;
  right: 8px;
  border-width: 0 1.5px 1.5px 0;
  border-radius: 0 0 2px 0;
}

/* 顶部金印标题 */
.card-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.4rem;
}

.seal-wrapper {
  background: linear-gradient(135deg, #c9a84c, #8b6914, #c9a84c);
  padding: 0.4rem 1.2rem;
  border-radius: 2px;
  box-shadow:
    0 2px 8px rgba(201, 168, 76, 0.3),
    inset 0 1px 0 rgba(255, 255, 255, 0.15);
  transform: rotate(-1deg);
}

.seal-text {
  font-size: 1.1rem;
  color: #1e150a;
  font-family: 'SimSun', 'STSong', serif;
  font-weight: bold;
  letter-spacing: 0.25em;
  text-shadow: 0 1px 0 rgba(255, 215, 0, 0.3);
}

.card-no {
  font-size: 0.65rem;
  color: rgba(139, 115, 85, 0.6);
  letter-spacing: 0.15em;
  font-family: monospace;
  margin: 0;
}

/* 分隔线 */
.divider {
  display: flex;
  align-items: center;
  width: 100%;
  gap: 0.6rem;
}

.divider-line {
  flex: 1;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(201, 168, 76, 0.3), transparent);
}

.divider-ornament {
  font-size: 0.5rem;
  color: rgba(201, 168, 76, 0.5);
}

/* 故事标题 */
.story-title-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.3rem;
}

.story-title-label {
  font-size: 0.65rem;
  color: rgba(201, 168, 76, 0.5);
  letter-spacing: 0.3em;
  margin: 0;
}

.story-title {
  font-size: 1.15rem;
  color: #e8dcc8;
  font-family: 'SimSun', 'STSong', serif;
  text-align: center;
  letter-spacing: 0.1em;
  margin: 0;
  line-height: 1.5;
}

/* 合券时间 */
.joint-time-block {
  text-align: center;
}

.joint-time {
  font-size: 0.85rem;
  color: rgba(201, 168, 76, 0.7);
  font-family: 'SimSun', 'STSong', serif;
  letter-spacing: 0.2em;
  margin: 0;
  font-style: italic;
}

/* 小分隔 */
.section-divider {
  text-align: center;
}

.section-ornament {
  font-size: 0.6rem;
  color: rgba(201, 168, 76, 0.3);
}

/* 两用户昵称 */
.users-block {
  display: flex;
  align-items: center;
  width: 100%;
  gap: 0.8rem;
}

.user-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.2rem;
  padding: 0.6rem;
  border: 1px solid rgba(201, 168, 76, 0.15);
  border-radius: 2px;
  background: rgba(201, 168, 76, 0.03);
}

.user-role {
  font-size: 0.6rem;
  color: rgba(201, 168, 76, 0.4);
  letter-spacing: 0.2em;
  margin: 0;
}

.user-nickname {
  font-size: 0.9rem;
  color: #c9a84c;
  font-family: 'SimSun', 'STSong', serif;
  text-align: center;
  margin: 0;
}

.users-separator {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.3rem;
  flex-shrink: 0;
}

.separator-line {
  width: 1px;
  height: 12px;
  background: linear-gradient(180deg, transparent, rgba(201, 168, 76, 0.4), transparent);
}

.separator-text {
  font-size: 0.6rem;
  color: rgba(201, 168, 76, 0.5);
  letter-spacing: 0.1em;
  margin: 0;
  writing-mode: horizontal-tb;
}

/* 专属印记 */
.mark-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
}

.mark-label {
  font-size: 0.6rem;
  color: rgba(139, 115, 85, 0.6);
  letter-spacing: 0.3em;
  margin: 0;
}

.mark-box {
  border: 1px solid rgba(201, 168, 76, 0.4);
  border-radius: 2px;
  padding: 0.5rem 1.2rem;
  background: rgba(201, 168, 76, 0.05);
  box-shadow: 0 0 10px rgba(201, 168, 76, 0.08);
}

.mark-text {
  font-size: 1.1rem;
  color: #c9a84c;
  font-family: 'SimSun', 'STSong', serif;
  letter-spacing: 0.25em;
  margin: 0;
  text-shadow: 0 0 8px rgba(201, 168, 76, 0.4);
}

/* 结局类型 */
.ending-block {
  text-align: center;
}

.ending-type {
  font-size: 0.7rem;
  color: rgba(139, 115, 85, 0.5);
  letter-spacing: 0.2em;
  margin: 0;
}

/* 底部签名 */
.card-footer {
  margin-top: 0.5rem;
  text-align: center;
}

.card-footer-text {
  font-size: 0.65rem;
  color: rgba(139, 115, 85, 0.35);
  letter-spacing: 0.2em;
  margin: 0;
}

/* 操作按钮 */
.actions {
  display: flex;
  gap: 0.8rem;
  margin-top: 2rem;
  width: 100%;
  max-width: 360px;
}

.action-btn {
  flex: 1;
  padding: 0.65rem;
  border: 1px solid rgba(139, 115, 85, 0.4);
  border-radius: 2px;
  background: rgba(44, 31, 20, 0.5);
  font-family: inherit;
  font-size: 0.8rem;
  color: #8b7355;
  cursor: pointer;
  transition: all 0.2s;
  letter-spacing: 0.1em;
}

.action-btn:hover {
  background: rgba(44, 31, 20, 0.8);
  border-color: rgba(139, 115, 85, 0.6);
}

.action-btn.primary {
  background: rgba(201, 168, 76, 0.12);
  border-color: rgba(201, 168, 76, 0.4);
  color: #c9a84c;
}

.action-btn.primary:hover {
  background: rgba(201, 168, 76, 0.2);
}
</style>
