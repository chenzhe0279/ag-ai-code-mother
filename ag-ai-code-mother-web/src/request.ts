import axios from 'axios'
import type { AxiosRequestConfig } from 'axios'
import { message } from 'ant-design-vue'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
  withCredentials: true,
})

request.interceptors.response.use(
  (response) => {
    const res = response.data
    // 二进制文件流直接返回
    if (res instanceof Blob) {
      return res
    }
    // 统一响应体 { code, data, message }
    if (res && typeof res === 'object' && 'code' in res) {
      if (res.code === 0) {
        return res
      }
      message.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res
  },
  (error) => {
    const msg = error?.response?.data?.message || error.message || '网络错误，请稍后重试'
    message.error(msg)
    return Promise.reject(error)
  },
)

// 泛型请求辅助，返回 { code, data, message }
export async function get<T = unknown>(
  url: string,
  params?: object,
  config?: AxiosRequestConfig,
): Promise<T> {
  const res = (await request.get(url, { params, ...config })) as unknown as { data: T }
  return res.data
}

export async function post<T = unknown>(
  url: string,
  data?: object,
  config?: AxiosRequestConfig,
): Promise<T> {
  const res = (await request.post(url, data, config)) as unknown as { data: T }
  return res.data
}

export default request
