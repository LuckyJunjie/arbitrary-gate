/**
 * SH-05 微信 JSSDK 服务
 * <p>
 * 负责在微信浏览器中初始化 JSSDK，支持分享到朋友/朋友圈。
 *
 * 使用方式：
 *   import { initWeChatJSSDK } from '@/services/wechat'
 *   await initWeChatJSSDK()
 */

import { api } from './api'

/** JSSDK config 接口返回值 */
export interface WeChatJsapiConfig {
  appId: string
  timestamp: string
  nonceStr: string
  signature: string
}

/**
 * 获取 JSSDK 签名配置
 *
 * @param url 当前页面 URL（需与调用 wx.config 的页面 URL 一致）
 */
export async function fetchJsapiConfig(url: string): Promise<WeChatJsapiConfig> {
  return api.get('/wechat/jsapi/config', { params: { url } })
}

/** 微信浏览器判断 */
export function isWeChatBrowser(): boolean {
  const ua = navigator.userAgent.toLowerCase()
  return ua.includes('micromessenger')
}

/** 获取全局 wx 对象（由 index.html CDN 注入） */
export function getWx(): any {
  if (typeof window !== 'undefined' && (window as any).wx) {
    return (window as any).wx
  }
  return null
}

/**
 * 初始化微信 JSSDK
 * <p>
 * 仅在微信浏览器中生效。
 * 调用此方法后，可使用 wx.updateAppMessageShareData / wx.updateTimelineShareData 设置分享内容。
 *
 * @param url 当前页面 URL（注意：应传入调用 wx.config 时的完整 URL，
 *            包含 hash 前的路径，但不含 hash fragment）
 */
export async function initWeChatJSSDK(url?: string): Promise<void> {
  if (!isWeChatBrowser()) {
    console.debug('[WeChat JSSDK] 非微信浏览器，跳过初始化')
    return
  }

  const wx = getWx()
  if (!wx) {
    console.error('[WeChat JSSDK] wx 对象未找到，JSSDK CDN 脚本可能未加载')
    return
  }

  // 获取当前页面 URL（不含 hash fragment）
  const currentUrl = url || window.location.href.split('#')[0]

  try {
    const config = await fetchJsapiConfig(currentUrl)

    wx.config({
      debug: import.meta.env.DEV, // 开发环境开启调试
      appId: config.appId,
      timestamp: Number(config.timestamp),
      nonceStr: config.nonceStr,
      signature: config.signature,
      jsApiList: [
        'updateAppMessageShareData', // 分享到朋友
        'updateTimelineShareData',   // 分享到朋友圈
      ],
    })

    wx.ready(() => {
      console.info('[WeChat JSSDK] 配置成功')
    })

    wx.error((res: any) => {
      console.error('[WeChat JSSDK] 配置失败', res)
    })
  } catch (err) {
    console.error('[WeChat JSSDK] 获取签名失败', err)
  }
}
