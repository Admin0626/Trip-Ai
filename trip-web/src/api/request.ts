// axios 实例 + 拦截器（03-前端设计.md §6.1；清单 §五 卡点#6）
import axios, { type AxiosInstance, type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { getToken, clearAuth } from '@/utils/storage'
import router from '@/router'

const request: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 30000,
})

// 500ms 内相同请求自动取消前一个（防重复）
const pending = new Map<string, AbortController>()

function makeKey(config: AxiosRequestConfig): string {
  return `${config.method}:${config.url}:${JSON.stringify(config.params ?? {})}`
}

function cancelPending(config: InternalAxiosRequestConfig): void {
  const key = makeKey(config)
  const prev = pending.get(key)
  if (prev) {
    prev.abort()
    pending.delete(key)
  }
  const controller = new AbortController()
  config.signal = controller.signal
  pending.set(key, controller)
}

// 请求拦截：注入 Authorization
request.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  cancelPending(config)
  return config
})

// 响应拦截：code === 200 返回 res.data（页面拿"纯响应"）；否则报错 + 401 登出
request.interceptors.response.use(
  (response) => {
    const resp = response.data as ApiResponse
    pending.delete(makeKey(response.config))
    if (resp && typeof resp === 'object' && 'code' in resp) {
      if (resp.code === 200) {
        // 返回整个 R，调用方通过 resp.data 取数据（保留分页/详情结构选择权）
        return response
      }
      if (resp.code === 401) {
        handleUnauthorized()
      }
      ElMessage.error(resp.message || '请求失败')
      return Promise.reject(new Error(resp.message || '请求失败'))
    }
    return response
  },
  (error) => {
    if (axios.isCancel(error)) return Promise.reject(error)
    const status = error.response?.status
    const body = error.response?.data as ApiResponse | undefined
    if (status === 401 || body?.code === 401) {
      handleUnauthorized()
    } else if (status === 403) {
      ElMessage.error('无权限操作')
    } else {
      ElMessage.error(body?.message || '网络异常，请稍后重试')
    }
    return Promise.reject(error)
  },
)

function handleUnauthorized(): void {
  clearAuth()
  ElMessage.error('登录已过期，请重新登录')
  const current = router.currentRoute.value
  if (current.path !== '/login') {
    void router.push({ path: '/login', query: { redirect: current.fullPath } })
  }
}

/** 泛型请求助手：直接返回 data（默认）；second=false 可返回整个 wrapper */
export async function requestData<T>(
  config: AxiosRequestConfig,
  opts?: { wrapper?: boolean },
): Promise<T> {
  const resp = await request(config)
  if (opts?.wrapper) return resp.data as T
  return (resp.data as ApiResponse<T>).data as T
}

export default request