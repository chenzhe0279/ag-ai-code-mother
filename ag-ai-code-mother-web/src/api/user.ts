import { get, post } from '@/request'

export interface LoginUserVO {
  id?: number
  userAccount?: string
  userName?: string
  userAvatar?: string
  userProfile?: string
  isVip?: number
  userRole?: string
  createTime?: string
}

export interface UserVO extends LoginUserVO {}

export interface UserQueryRequest {
  pageNum?: number
  pageSize?: number
  id?: number
  userName?: string
  userAccount?: string
  userProfile?: string
  userRole?: string
}

export interface PageResult<T> {
  records: T[]
  pageNumber: number
  pageSize: number
  totalPage: number
  totalRow: number
}

// 登录 / 注册
export const userRegister = (data: {
  userAccount: string
  userPassword: string
  checkPassword: string
}) => post<number>('/user/register', data)

export const userLogin = (data: { userAccount: string; userPassword: string }) =>
  post<LoginUserVO>('/user/login', data)

export const getLoginUser = () => get<LoginUserVO>('/user/get/login')

export const userLogout = () => post<boolean>('/user/logout')

// 管理员
export const listUserByPage = (data: UserQueryRequest) =>
  post<PageResult<UserVO>>('/user/list/page/vo', data)

export const getUserById = (id: number) => get<Record<string, unknown>>('/user/get', { id })

export const addUser = (data: Record<string, unknown>) => post<number>('/user/add', data)

export const updateUser = (data: Record<string, unknown>) => post<boolean>('/user/update', data)

export const deleteUser = (id: number) => post<boolean>('/user/delete', { id })
