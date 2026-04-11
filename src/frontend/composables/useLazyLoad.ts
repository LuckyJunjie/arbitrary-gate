/**
 * I-10 图片懒加载 Composable
 * 使用 IntersectionObserver 在图片进入视口时才加载
 * 避免首屏一次性请求大量图片，提升性能
 *
 * 支持两种模式:
 * 1. isInView 响应式（已有）：适合配合 v-if 条件渲染
 * 2. data-src 模式（新增）：自动替换 data-src → src，并添加 loaded 类
 */

import { ref, onMounted, onUnmounted, type Directive, type DirectiveBinding } from 'vue'

export interface LazyImageOptions {
  /** 预加载阈值（进入视口多少比例时开始加载，默认 50px） */
  rootMargin?: string
  /** 是否默认显示占位符（默认 true） */
  showPlaceholder?: boolean
}

// ── 全局 observer 池，避免重复创建 ──
const observerMap = new Map<HTMLElement, IntersectionObserver>()
const ROOT_MARGIN = '50px'

function getObserver(): IntersectionObserver {
  if (typeof window === 'undefined' || !('IntersectionObserver' in window)) {
    // SSR 或不支持的环境：直接加载
    return null as any
  }
  return new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          const el = entry.target as HTMLElement
          const src = el.getAttribute('data-src')
          if (src) {
            el.setAttribute('src', src)
            el.removeAttribute('data-src')
            // 等待图片实际加载完成再加 loaded 类
            const img = el as HTMLImageElement
            if (img.complete) {
              el.classList.add('loaded')
            } else {
              img.addEventListener('load', () => el.classList.add('loaded'), { once: true })
            }
          }
          // 取消观察（只加载一次）
          observerMap.get(el)?.unobserve(el)
          observerMap.delete(el)
        }
      })
    },
    { rootMargin: ROOT_MARGIN }
  )
}

// ── 核心 lazyLoad 函数 ──
/**
 * 对单个元素启用懒加载
 * @param el img 元素
 */
export function lazyLoad(el: HTMLElement): void {
  if (!el || el.hasAttribute('data-src') === false) return
  const observer = getObserver()
  observer.observe(el)
  observerMap.set(el, observer)
}

/**
 * 取消对元素的观察
 * @param el img 元素
 */
export function unobserve(el: HTMLElement): void {
  observerMap.get(el)?.unobserve(el)
  observerMap.delete(el)
}

/**
 * 断开并清理所有 observer
 */
export function cleanupObservers(): void {
  observerMap.forEach((obs) => obs.disconnect())
  observerMap.clear()
}

// ── Vue 指令 v-lazy ──
/**
 * 用法: <img v-lazy data-src="..." />
 * 进入视口后自动将 data-src 替换为 src，并添加 loaded 类
 */
export const vLazy: Directive = {
  mounted(el: HTMLElement, binding: DirectiveBinding) {
    if (!el.hasAttribute('data-src')) return
    lazyLoad(el)
  },
  unmounted(el: HTMLElement) {
    unobserve(el)
  },
}

// ── 原有 useLazyLoad 保持兼容 ──
export function useLazyLoad(options: LazyImageOptions = {}) {
  const { rootMargin = '100px', showPlaceholder = true } = options

  const isLoaded = ref(false)
  const isInView = ref(false)
  let observer: IntersectionObserver | null = null
  let element: HTMLElement | null = null

  function observe(el: HTMLElement | null) {
    if (!el) return
    element = el

    observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            isInView.value = true
            observer?.unobserve(entry.target)
          }
        })
      },
      { rootMargin }
    )

    observer.observe(element)
  }

  function unobserveFn() {
    if (observer && element) {
      observer.unobserve(element)
    }
  }

  function cleanup() {
    if (observer) {
      observer.disconnect()
      observer = null
    }
  }

  onUnmounted(cleanup)

  return {
    isLoaded,
    isInView,
    observe,
    unobserve: unobserveFn,
    cleanup,
    placeholderClass: showPlaceholder ? 'img-placeholder' : '',
  }
}

/**
 * 简易版：给 img 标签自动添加懒加载
 */
export function useAutoLazyImg(imgRef: { value: HTMLImageElement | null }, src: string) {
  const actualSrc = ref('')
  let observer: IntersectionObserver | null = null

  onMounted(() => {
    const el = imgRef.value
    if (!el) return

    observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            actualSrc.value = src
            observer?.unobserve(entry.target)
          }
        })
      },
      { rootMargin: '100px' }
    )

    observer.observe(el)
  })

  onUnmounted(() => {
    observer?.disconnect()
  })

  return { actualSrc }
}
