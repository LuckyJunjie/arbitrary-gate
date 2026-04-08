<script setup lang="ts">
// TODO: 全局布局（书房主题背景）
// TODO: 路由守卫（未登录跳转）
</script>

<template>
  <div id="app-root" class="paper-texture">
    <router-view v-slot="{ Component }">
      <transition name="fade" mode="out-in">
        <component :is="Component" />
      </transition>
    </router-view>
  </div>
</template>

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

#app-root {
  width: 100%;
  min-height: 100vh;
}

/* 宣纸纹理背景 */
.paper-texture {
  background-color: #f5efe0;
  background-image:
    radial-gradient(ellipse at 20% 30%, rgba(200, 180, 140, 0.08) 0%, transparent 60%),
    radial-gradient(ellipse at 80% 70%, rgba(180, 160, 120, 0.06) 0%, transparent 50%);
}

/* 路由过渡 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
