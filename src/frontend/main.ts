import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import { initCardStore } from './stores/cardStore'
import { initInkValueStore } from './stores/inkValueStore'

// Views
import HomeView from './views/HomeView.vue'
import PoolView from './views/PoolView.vue'
import CardsView from './views/CardsView.vue'
import StoryView from './views/StoryView.vue'
import BookshelfView from './views/BookshelfView.vue'
import ShareView from './views/ShareView.vue'
import EntryQuestionsView from './views/EntryQuestionsView.vue'
import ManuscriptView from './views/ManuscriptView.vue'
import ShopView from './views/ShopView.vue'

const routes = [
  { path: '/', name: 'home', component: HomeView },
  { path: '/pool', name: 'pool', component: PoolView },
  { path: '/cards', name: 'cards', component: CardsView },
  { path: '/entry-questions', name: 'entry-questions', component: EntryQuestionsView },
  { path: '/story/:id', name: 'story', component: StoryView },
  { path: '/bookshelf', name: 'bookshelf', component: BookshelfView },
  { path: '/share/:code', name: 'share', component: ShareView },
  { path: '/manuscript/:id', name: 'manuscript', component: ManuscriptView },
  { path: '/shop', name: 'shop', component: ShopView },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)
app.use(router)

// app 挂载后静默初始化 card store（从 localStorage 恢复 + 异步拉取后端）
app.mount('#app')
initCardStore()
initInkValueStore()
