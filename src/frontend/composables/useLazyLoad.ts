/**
 * I-10 图片懒加载 Composable
 * 使用 IntersectionObserver 在图片进入视口时才加载
 * 避免首屏一次性请求大量图片，提升性能
 */

import { ref, onMounted, onUnmounted } from 'vue'

export interface LazyImageOptions {
  /** 预加载阈值（进入视口多少比例时开始加载，默认 0.1） */
  rootMargin?: string
  /** 是否默认显示占位符（默认 true） */
  showPlaceholder?: boolean
}

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
            // 一旦进入视口，取消观察（只加载一次）
            observer?.unobserve(entry.target)
          }
        })
      },
      { rootMargin }
    )

    observer.observe(element)
  }

  function unobserve() {
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
    unobserve,
    cleanup,
    /**
     * 占位符类名（用于 CSS 过渡）
     * 在模板中使用：class="{ 'img-placeholder': showPlaceholder && !isLoaded }"
     */
    placeholderClass: showPlaceholder ? 'img-placeholder' : '',
  }
}

/**
 * 简易版：给 img 标签自动添加懒加载
 * 在 mounted 后观察元素，进入视口时将 _lazySrc 设置为 src
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
