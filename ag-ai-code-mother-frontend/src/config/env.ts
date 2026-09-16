/**
 * 环境变量配置
 */
import { CodeGenTypeEnum } from '@/utils/codeGenTypes.ts'

// 应用部署域名
export const DEPLOY_DOMAIN = import.meta.env.VITE_DEPLOY_DOMAIN || 'http://localhost'

// API 基础地址
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8123/api'

// 静态资源地址
export const STATIC_BASE_URL = `${API_BASE_URL}/static`

// 获取部署应用的完整URL
export const getDeployUrl = (deployKey: string) => {
  return `${DEPLOY_DOMAIN}/${deployKey}`
}

// 获取静态资源预览URL（版本化改造后代码保存在 v{currentVersion} 子目录下）
export const getStaticPreviewUrl = (
  codeGenType: string,
  appId: string,
  currentVersion?: number,
) => {
  const version = currentVersion && currentVersion > 0 ? currentVersion : 1
  const baseUrl = `${STATIC_BASE_URL}/${codeGenType}_${appId}/v${version}/`
  // 如果是 Vue 项目，浏览地址需要添加 dist 后缀
  if (codeGenType === CodeGenTypeEnum.VUE_PROJECT) {
    return `${baseUrl}dist/index.html`
  }
  return baseUrl
}

/**
 * 将后端返回的相对头像地址转换为浏览器可访问的地址。
 * 兼容历史数据 /file/avatar/xxx.jpg 和新的 /api/file/avatar/xxx.jpg。
 */
export const resolveAvatarUrl = (avatarUrl?: string) => {
  if (!avatarUrl) return ''
  if (/^(https?:)?\/\//i.test(avatarUrl) || /^(data|blob):/i.test(avatarUrl)) {
    return avatarUrl
  }

  const normalizedPath = avatarUrl.startsWith('/') ? avatarUrl : `/${avatarUrl}`
  if (normalizedPath.startsWith('/api/')) {
    return normalizedPath
  }

  return `${API_BASE_URL.replace(/\/$/, '')}${normalizedPath}`
}
