// 高德地图 JS API 动态加载器（单例，避免重复注入脚本）。
// 采用自写加载方式，不引入 @amap/amap-jsapi-loader 依赖。
import { AMAP_KEY, AMAP_SECURITY_CODE } from '@/config/amap'

let loading = null

export function loadAMap() {
  if (window.AMap) return Promise.resolve(window.AMap)
  if (loading) return loading

  loading = new Promise((resolve, reject) => {
    // 安全密钥需在脚本加载前设置（JS API 2.0 要求）
    window._AMapSecurityConfig = { securityJsCode: AMAP_SECURITY_CODE }
    const script = document.createElement('script')
    script.src = `https://webapi.amap.com/maps?v=2.0&key=${encodeURIComponent(AMAP_KEY)}`
    script.async = true
    script.onload = () => {
      if (window.AMap) resolve(window.AMap)
      else reject(new Error('AMap 脚本加载后未初始化（请检查 key）'))
    }
    script.onerror = () => {
      loading = null
      reject(new Error('高德地图脚本加载失败（请检查网络或 key）'))
    }
    document.head.appendChild(script)
  })
  return loading
}
