/**
 * SH-05 微信分享 Composable
 * <p>
 * 在微信浏览器中设置分享到朋友/朋友圈的内容。
 *
 * 使用方式：
 *   import { useWeChatShare } from '@/composables/useWeChatShare'
 *
 *   const { setupShare } = useWeChatShare()
 *   setupShare({ title, desc, link, imgUrl })
 */

import { getWx, isWeChatBrowser } from '@/services/wechat'

export interface ShareData {
  /** 分享标题 */
  title: string
  /** 分享描述 */
  desc: string
  /** 分享链接（必须是 JSSDK 安全域名下的页面） */
  link: string
  /** 分享图标 URL */
  imgUrl: string
}

/**
 * 设置微信分享内容（朋友/朋友圈）
 * <p>
 * 注意：此方法应在 wx.ready() 回调之后调用，或确保 JSSDK 已初始化。
 * 推荐配合 initWeChatJSSDK() 使用。
 */
export function setupWeChatShare(data: ShareData): void {
  if (!isWeChatBrowser()) return

  const wx = getWx()
  if (!wx) {
    console.warn('[WeChatShare] wx 对象不可用')
    return
  }

  try {
    // 分享到朋友（微信好友）
    wx.updateAppMessageShareData({
      title: data.title,
      desc: data.desc,
      link: data.link,
      imgUrl: data.imgUrl,
      success: () => {
        console.debug('[WeChatShare] updateAppMessageShareData 成功')
      },
    })

    // 分享到朋友圈
    wx.updateTimelineShareData({
      title: data.title,
      link: data.link,
      imgUrl: data.imgUrl,
      success: () => {
        console.debug('[WeChatShare] updateTimelineShareData 成功')
      },
    })
  } catch (err) {
    console.error('[WeChatShare] 设置分享内容失败', err)
  }
}

/**
 * useWeChatShare composable
 * <p>
 * 在组件中使用：
 *   const { setupShare } = useWeChatShare()
 *   setupShare({ title, desc, link, imgUrl })
 */
export function useWeChatShare() {
  const setupShare = (data: ShareData) => {
    setupWeChatShare(data)
  }

  return { setupShare }
}
