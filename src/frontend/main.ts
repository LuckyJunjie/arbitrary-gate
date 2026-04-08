import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'

// Views
import HomeView from './views/HomeView.vue'
import PoolView from './views/PoolView.vue'
import CardsView from './views/CardsView.vue'
import StoryView from './views/StoryView.vue'
import BookshelfView from './views/BookshelfView.vue'
import ShareView from './views/ShareView.vue'

const routes = [
  { path: '/', name: 'home', component: HomeView },
  { path: '/pool', name: 'pool', component: PoolView },
  { path: '/cards', name: 'cards', component: CardsView },
  { path: '/story/:id', name: 'story', component: StoryView },
  { path: '/bookshelf', name: 'bookshelf', component: BookshelfView },
  { path: '/share/:code', name: 'share', component: ShareView },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')
