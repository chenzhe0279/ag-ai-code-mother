import { get, post } from '@/request'
import type { PageResult, UserVO } from './user'

export interface AppVO {
  id?: number
  appName?: string
  cover?: string
  initPrompt?: string
  codeGenType?: string
  currentVersion?: number
  deployKey?: string
  deployedTime?: string
  priority?: number
  userId?: number
  createTime?: string
  updateTime?: string
  user?: UserVO
}

export interface AppQueryRequest {
  pageNum?: number
  pageSize?: number
  id?: number
  appName?: string
  codeGenType?: string
  priority?: number
  userId?: number
}

export interface AppVersionVO {
  version?: number
  isCurrent?: boolean
  createTime?: string
}

export const addApp = (data: { initPrompt: string }) => post<number>('/app/add', data)

export const updateApp = (data: { id: number; appName: string }) =>
  post<boolean>('/app/update', data)

export const deleteApp = (id: number) => post<boolean>('/app/delete', { id })

export const deployApp = (appId: number) => post<string>('/app/deploy', { appId })

export const getAppVo = (id: number) => get<AppVO>('/app/get/vo', { id })

export const listMyAppByPage = (data: AppQueryRequest) =>
  post<PageResult<AppVO>>('/app/my/list/page/vo', data)

export const listGoodAppByPage = (data: AppQueryRequest) =>
  post<PageResult<AppVO>>('/app/good/list/page/vo', data)

export const listAppVersions = (appId: number) =>
  get<AppVersionVO[]>('/app/version/list', { appId })

export const rollbackApp = (appId: number, targetVersion: number) =>
  post<number>('/app/version/rollback', { appId, targetVersion })

// 管理员
export const listAppByPageAdmin = (data: AppQueryRequest) =>
  post<PageResult<AppVO>>('/app/admin/list/page/vo', data)

export const getAppVoByAdmin = (id: number) => get<AppVO>('/app/admin/get/vo', { id })

export const updateAppAdmin = (data: Record<string, unknown>) =>
  post<boolean>('/app/admin/update', data)

export const deleteAppAdmin = (id: number) => post<boolean>('/app/admin/delete', { id })
