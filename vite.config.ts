import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { VitePWA } from 'vite-plugin-pwa'
import { resolve } from 'path'

export default defineConfig({
  plugins: [
    vue(),
    VitePWA({
      registerType: 'prompt',
      includeAssets: ['favicon.svg', 'assets/**'],
      manifest: {
        name: 'Arbitrary Gate',
        short_name: 'ArbitraryGate',
        description: 'I-09 时光笺 PWA',
        theme_color: '#4a3f35',
        background_color: '#f5f0e8',
        display: 'standalone',
        icons: [
          {
            src: 'favicon.svg',
            sizes: 'any',
            type: 'image/svg+xml',
          },
        ],
      },
      workbox: {
        // 静态资源（JS/CSS/字体）：CacheFirst，缓存名 arbitrary-gate-static-v1
        runtimeCaching: [
          {
            urlPattern: /\.(js|css|woff2?|ttf|otf|eot)(\?|$)/,
            handler: 'CacheFirst',
            options: {
              cacheName: 'arbitrary-gate-static-v1',
              expiration: {
                maxEntries: 100,
                maxAgeSeconds: 7 * 24 * 60 * 60, // 7 天
              },
              cacheableResponse: {
                statuses: [0, 200],
              },
            },
          },
          // 图片：CacheFirst
          {
            urlPattern: /\.(webp|png|jpg|jpeg|gif|svg|ico)(\?|$)/,
            handler: 'CacheFirst',
            options: {
              cacheName: 'arbitrary-gate-image-v1',
              expiration: {
                maxEntries: 200,
                maxAgeSeconds: 30 * 24 * 60 * 60, // 30 天
              },
              cacheableResponse: {
                statuses: [0, 200],
              },
            },
          },
          // API 请求：NetworkFirst
          {
            urlPattern: /\/api\//,
            handler: 'NetworkFirst',
            options: {
              cacheName: 'arbitrary-gate-api-v1',
              expiration: {
                maxEntries: 100,
                maxAgeSeconds: 1 * 24 * 60 * 60, // 1 天
              },
              networkTimeoutSeconds: 10,
              cacheableResponse: {
                statuses: [0, 200],
              },
            },
          },
          // HTML 页面：NetworkFirst
          {
            urlPattern: /^https?:\/\/.*\/.*\.html/,
            handler: 'NetworkFirst',
            options: {
              cacheName: 'arbitrary-gate-page-v1',
              expiration: {
                maxEntries: 50,
                maxAgeSeconds: 7 * 24 * 60 * 60, // 7 天
              },
              cacheableResponse: {
                statuses: [0, 200],
              },
            },
          },
        ],
      },
    }),
  ],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src/frontend'),
    },
  },
  server: {
    port: 5173,
    host: true,
  },
  build: {
    outDir: 'dist',
    assetsDir: 'assets',
    sourcemap: false,
  },
})
