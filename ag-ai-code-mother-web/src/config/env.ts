/**
 * 前端环境配置
 */

// API 基础地址（开发环境走 Vite 代理）
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'

// 静态资源（AI 生成代码）地址
export const STATIC_BASE_URL = `${API_BASE_URL}/static`

// 获取静态资源预览地址
// 部署目录命名：{codeGenType}_{appId}/
export const getStaticPreviewUrl = (codeGenType: string, appId: string | number) => {
  return `${STATIC_BASE_URL}/${codeGenType}_${appId}/`
}
