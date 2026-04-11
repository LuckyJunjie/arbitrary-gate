/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<object, object, unknown>
  export default component
}

// SH-05: 微信 JSSDK 全局变量（由 index.html CDN 注入）
interface WxJsApiConfig {
  debug: boolean
  appId: string
  timestamp: number
  nonceStr: string
  signature: string
  jsApiList: string[]
}

interface WxShareData {
  title?: string
  desc?: string
  link?: string
  imgUrl?: string
  success?: () => void
  fail?: (err: any) => void
  cancel?: () => void
}

interface Wx {
  config: (config: WxJsApiConfig) => void
  ready: (callback: () => void) => void
  error: (callback: (res: any) => void) => void
  updateAppMessageShareData: (data: WxShareData) => void
  updateTimelineShareData: (data: WxShareData) => void
  chooseWXPay: (params: any) => void
  [key: string]: any
}

declare interface Window {
  wx: Wx
}
