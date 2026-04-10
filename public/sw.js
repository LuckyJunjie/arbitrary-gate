/**
 * sw.js - Service Worker for 时光笺
 * I-09 离线缓存
 *
 * 缓存策略：
 * - 静态资源（CSS/JS/字体）：CacheFirst，寿命 7 天
 * - 图片：CacheFirst，寿命 30 天
 * - API 请求：NetworkFirst，失败返回缓存
 * - 入口页面：StaleWhileRevalidate
 *
 * 离线降级：网络不可用时显示已缓存内容
 */

// ─── 缓存名称 ────────────────────────────────────────────────────────────────
const CACHE_STATIC  = 'shiguangjian-static-v1'   // CSS/JS/字体
const CACHE_IMAGE   = 'shiguangjian-image-v1'      // 图片
const CACHE_API     = 'shiguangjian-api-v1'        // API 响应
const CACHE_PAGE    = 'shiguangjian-page-v1'       // HTML 页面

const MAX_AGE_STATIC = 7  * 24 * 60 * 60 * 1000  // 7 天
const MAX_AGE_IMAGE  = 30 * 24 * 60 * 60 * 1000  // 30 天
const MAX_AGE_API    = 1  * 24 * 60 * 60 * 1000  // 1 天

// ─── 静态资源 glob ───────────────────────────────────────────────────────────
const STATIC_ASSETS = [
  '/',
  '/index.html',
  '/favicon.svg',
]

// ─── 安装：预缓存关键资源 ────────────────────────────────────────────────────
self.addEventListener('install', event => {
  console.log('[SW] Installing...')
  event.waitUntil(
    caches.open(CACHE_STATIC).then(cache => {
      return cache.addAll(STATIC_ASSETS).catch(err => {
        console.warn('[SW] Precache failed (ok in dev):', err)
      })
    }).then(() => self.skipWaiting())
  )
})

// ─── 激活：清理旧缓存 ────────────────────────────────────────────────────────
self.addEventListener('activate', event => {
  console.log('[SW] Activating...')
  const validCaches = [CACHE_STATIC, CACHE_IMAGE, CACHE_API, CACHE_PAGE]
  event.waitUntil(
    caches.keys().then(names => {
      return Promise.all(
        names.filter(name => !validCaches.includes(name))
             .map(name => caches.delete(name))
      )
    }).then(() => self.clients.claim())
  )
})

// ─── 请求拦截 ────────────────────────────────────────────────────────────────
self.addEventListener('fetch', event => {
  const { request } = event
  const url = new URL(request.url)

  // 仅处理同源请求
  if (url.origin !== self.location.origin) return

  // skip non-GET
  if (request.method !== 'GET') return

  // skip chrome-extension / devtools
  if (url.protocol === 'chrome-extension:') return

  if (url.pathname.startsWith('/api/')) {
    // API 请求：NetworkFirst
    event.respondWith(networkFirst(request, CACHE_API, MAX_AGE_API))
  } else if (isImageRequest(request)) {
    // 图片：CacheFirst
    event.respondWith(cacheFirst(request, CACHE_IMAGE, MAX_AGE_IMAGE))
  } else if (isStaticAsset(request)) {
    // 静态资源：CacheFirst
    event.respondWith(cacheFirst(request, CACHE_STATIC, MAX_AGE_STATIC))
  } else if (isDocumentRequest(request)) {
    // HTML 页面：StaleWhileRevalidate
    event.respondWith(staleWhileRevalidate(request, CACHE_PAGE))
  }
  // 其他请求不拦截
})

// ─── 策略实现 ────────────────────────────────────────────────────────────────

/** CacheFirst：优先缓存，缓存过期后更新 */
async function cacheFirst(request, cacheName, maxAge) {
  const cache = await caches.open(cacheName)
  const cached = await cache.match(request)

  if (cached) {
    const age = Date.now() - new Date(cached.headers.get('sw-fetched') || 0).getTime()
    if (age < maxAge) {
      return cached
    }
    // 已过期，在后台更新
    backgroundUpdate(cache, request)
    return cached
  }

  // 无缓存，fetch 并缓存
  try {
    const response = await fetch(request)
    if (response.ok) {
      const clone = response.clone()
      const enriched = enrichResponse(clone, Date.now())
      cache.put(request, enriched)
    }
    return response
  } catch (err) {
    console.warn('[SW] fetch failed (offline):', request.url)
    // 降级：返回离线占位响应（仅图片/静态资源）
    return new Response('Offline', { status: 503 })
  }
}

/** NetworkFirst：优先网络，失败返回缓存 */
async function networkFirst(request, cacheName, maxAge) {
  const cache = await caches.open(cacheName)

  try {
    const response = await fetch(request)
    if (response.ok) {
      const clone = response.clone()
      const enriched = enrichResponse(clone, Date.now())
      cache.put(request, enriched)
    }
    // 清理过期条目
    trimCache(cacheName, 50)
    return response
  } catch (err) {
    // 网络失败，尝试返回缓存
    const cached = await cache.match(request)
    if (cached) {
      const age = Date.now() - new Date(cached.headers.get('sw-fetched') || 0).getTime()
      if (age < maxAge) {
        console.log('[SW] Network failed, serving cached:', request.url)
        return cached
      }
    }
    // 缓存也没有，返回离线提示
    return new Response(JSON.stringify({
      code: 503,
      message: '网络不可用，请稍后重试'
    }), {
      status: 503,
      headers: { 'Content-Type': 'application/json' }
    })
  }
}

/** StaleWhileRevalidate：先返回缓存，同时后台更新 */
async function staleWhileRevalidate(request, cacheName) {
  const cache = await caches.open(cacheName)
  const cached = await cache.match(request)

  const fetchPromise = fetch(request).then(response => {
    if (response.ok) {
      const clone = response.clone()
      const enriched = enrichResponse(clone, Date.now())
      cache.put(request, enriched)
    }
    return response
  }).catch(() => null)

  return cached || fetchPromise
}

// ─── 工具函数 ────────────────────────────────────────────────────────────────

/** 后台更新缓存（不阻塞响应） */
function backgroundUpdate(cache, request) {
  fetch(request).then(response => {
    if (response.ok) cache.put(request, enrichResponse(response.clone(), Date.now()))
  }).catch(() => {})
}

/** 给 Response 添加 sw-fetched 时间头（用于判断缓存年龄） */
function enrichResponse(response, timestamp) {
  const headers = new Headers(response.headers)
  headers.set('sw-fetched', new Date(timestamp).toISOString())
  return new Response(response.body, {
    status: response.status,
    statusText: response.statusText,
    headers,
  })
}

/** 限制缓存条目数量（防止无限膨胀） */
async function trimCache(cacheName, maxEntries) {
  const cache = await caches.open(cacheName)
  const keys = await cache.keys()
  if (keys.length > maxEntries) {
    const toDelete = keys.slice(0, keys.length - maxEntries)
    await Promise.all(toDelete.map(k => cache.delete(k)))
  }
}

function isImageRequest(request) {
  return /\.(webp|png|jpg|jpeg|gif|svg|ico)(\?|$)/.test(request.url)
}

function isStaticAsset(request) {
  return /\.(js|css|woff2?|ttf|otf|eot)(\?|$)/.test(request.url)
}

function isDocumentRequest(request) {
  return request.headers.get('accept')?.includes('text/html')
}

// ─── 推送通知（预留）──────────────────────────────────────────────────────────
// self.addEventListener('push', event => { ... })
