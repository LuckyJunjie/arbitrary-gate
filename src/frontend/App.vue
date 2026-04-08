<script setup lang="ts">
import { ref, onErrorCaptured } from 'vue'
import { useRouter } from 'vue-router'

// ── 路由守卫 ──
const router = useRouter()
const token = localStorage.getItem('token')
const isGuest = !token

// ── 全局错误边界 ──
const globalError = ref<string | null>(null)

onErrorCaptured((err, instance, info) => {
  console.error('[App] Global error captured:', err, info)
  globalError.value = '系统繁忙，请刷新页面重试'
  return false // 阻止错误继续传播
})

function dismissError() {
  globalError.value = null
}
</script>

<template>
  <div id="app-root" class="study-root">
    <!-- 书房背景层 -->
    <div class="study-bg" aria-hidden="true">
      <!-- 宣纸纹理叠加 -->
      <div class="paper-texture" />
      <!-- 角落墨迹装饰 -->
      <div class="ink-corner ink-corner--tl" />
      <div class="ink-corner ink-corner--tr" />
      <div class="ink-corner ink-corner--bl" />
      <div class="ink-corner ink-corner--br" />
      <!-- 微妙的墨水扩散动画 -->
      <div class="ink-diffuse ink-diffuse--1" />
      <div class="ink-diffuse ink-diffuse--2" />
    </div>

    <!-- 全局错误提示 -->
    <Transition name="error-slide">
      <div v-if="globalError" class="global-error" role="alert">
        <span class="error-icon">⚠️</span>
        <span class="error-msg">{{ globalError }}</span>
        <button class="error-dismiss" @click="dismissError">×</button>
      </div>
    </Transition>

    <!-- 游客模式提示（首次） -->
    <Transition name="guest-fade">
      <div v-if="isGuest" class="guest-hint" aria-live="polite">
        <span>游客模式 · </span>
        <button class="guest-login" @click="router.push('/pool')">获取关键词</button>
      </div>
    </Transition>

    <!-- 路由视图 + 墨迹过渡 -->
    <router-view v-slot="{ Component, route }">
      <Transition name="ink-transition" mode="out-in" @before-enter="onBeforeEnter" @after-enter="onAfterEnter">
        <component :is="Component" :key="route.fullPath" />
      </Transition>
    </router-view>
  </div>
</template>

<script lang="ts">
// 墨迹过渡钩子
function onBeforeEnter(el: Element) {
  const htmlEl = el as HTMLElement
  htmlEl.classList.add('ink-entering')
}

function onAfterEnter(el: Element) {
  const htmlEl = el as HTMLElement
  htmlEl.classList.remove('ink-entering')
}
</script>

<style>
/* ===== Global Reset & Base ===== */
*, *::before, *::after {
  box-sizing: border-box;
  margin: 0;
  padding: 0;
}

html, body {
  width: 100%;
  height: 100%;
  overflow-x: hidden;
}

body {
  font-family: 'Noto Serif SC', 'Source Han Serif SC', 'SimSun', serif;
  background-color: #1a1510;
  color: #e8dcc8;
  -webkit-font-smoothing: antialiased;
}

#app-root, .study-root {
  width: 100%;
  min-height: 100vh;
  position: relative;
}

/* 书房背景层 */
.study-bg {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 0;
  overflow: hidden;
}

/* 宣纸纹理 */
.paper-texture {
  position: absolute;
  inset: 0;
  background-color: #f5efe0;
  background-image:
    radial-gradient(ellipse at 20% 30%, rgba(200, 180, 140, 0.1) 0%, transparent 60%),
    radial-gradient(ellipse at 80% 70%, rgba(180, 160, 120, 0.08) 0%, transparent 50%),
    url("data:image/svg+xml,%3Csvg viewBox='0 0 200 200' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.65' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)' opacity='0.04'/%3E%3C/svg%3E");
  opacity: 0.95;
}

/* 角落墨迹 */
.ink-corner {
  position: absolute;
  width: 120px;
  height: 120px;
  border-radius: 50%;
  background: radial-gradient(circle at center, rgba(44, 31, 20, 0.12) 0%, transparent 70%);
  filter: blur(20px);
}

.ink-corner--tl { top: -40px; left: -40px; }
.ink-corner--tr { top: -40px; right: -40px; }
.ink-corner--bl { bottom: -40px; left: -40px; }
.ink-corner--br { bottom: -40px; right: -40px; }

/* 墨水扩散动画 */
.ink-diffuse {
  position: absolute;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(44, 31, 20, 0.06) 0%, transparent 70%);
  animation: ink-spread 12s ease-in-out infinite;
  pointer-events: none;
}

.ink-diffuse--1 {
  width: 300px;
  height: 300px;
  top: 20%;
  left: 10%;
  animation-delay: 0s;
}

.ink-diffuse--2 {
  width: 200px;
  height: 200px;
  bottom: 15%;
  right: 8%;
  animation-delay: -6s;
}

@keyframes ink-spread {
  0%, 100% { transform: scale(1) translate(0, 0); opacity: 0.6; }
  33% { transform: scale(1.08) translate(6px, 4px); opacity: 0.8; }
  66% { transform: scale(0.95) translate(-4px, -3px); opacity: 0.5; }
}

/* 全局错误提示 */
.global-error {
  position: fixed;
  top: 1rem;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0.75rem 1.25rem;
  background: rgba(196, 90, 60, 0.15);
  border: 1px solid rgba(196, 90, 60, 0.4);
  border-radius: 6px;
  color: #e8a090;
  font-size: 0.88rem;
  z-index: 9999;
  backdrop-filter: blur(8px);
  max-width: 90vw;
}

.error-icon {
  font-size: 1rem;
  flex-shrink: 0;
}

.error-msg {
  flex: 1;
}

.error-dismiss {
  background: none;
  border: none;
  color: #e8a090;
  font-size: 1.1rem;
  cursor: pointer;
  padding: 0 0.25rem;
  opacity: 0.7;
  line-height: 1;
}

.error-dismiss:hover {
  opacity: 1;
}

.error-slide-enter-active,
.error-slide-leave-active {
  transition: all 0.3s ease;
}
.error-slide-enter-from,
.error-slide-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(-10px);
}

/* 游客模式提示 */
.guest-hint {
  position: fixed;
  bottom: 1.5rem;
  right: 1.5rem;
  font-size: 0.78rem;
  color: #8b7355;
  z-index: 100;
  display: flex;
  align-items: center;
  gap: 0.3rem;
  opacity: 0.7;
  transition: opacity 0.3s;
}

.guest-hint:hover {
  opacity: 1;
}

.guest-login {
  background: none;
  border: none;
  color: #c4a882;
  font-family: inherit;
  font-size: 0.78rem;
  cursor: pointer;
  text-decoration: underline;
  padding: 0;
}

.guest-fade-enter-active,
.guest-fade-leave-active {
  transition: opacity 0.5s ease;
}
.guest-fade-enter-from,
.guest-fade-leave-to {
  opacity: 0;
}

/* ── 路由过渡 - 墨迹淡入效果 ── */
.ink-transition-enter-active {
  transition: opacity 0.35s ease, transform 0.35s cubic-bezier(0.4, 0, 0.2, 1);
}

.ink-transition-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.ink-transition-enter-from {
  opacity: 0;
  transform: scale(0.98) translateY(6px);
}

.ink-transition-leave-to {
  opacity: 0;
  transform: scale(1.01);
}

/* 入场时墨迹晕染效果 */
.ink-entering {
  animation: ink-splash 0.4s cubic-bezier(0.4, 0, 0.2, 1) both;
}

@keyframes ink-splash {
  0% {
    opacity: 0;
    transform: scale(1.03) translateY(-4px);
    filter: blur(2px);
  }
  100% {
    opacity: 1;
    transform: scale(1) translateY(0);
    filter: blur(0);
  }
}
</style>
