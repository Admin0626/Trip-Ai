// 认证接口（api/01 · /auth/**）
import request from '@/api/request'

export function loginApi(payload: { username: string; password: string }): Promise<LoginResult> {
  return request.post<ApiResponse<LoginResult>>('/auth/login', payload).then((r) => r.data.data)
}

export function registerApi(payload: {
  username: string
  password: string
  nickname?: string
  email?: string
}): Promise<UserInfo> {
  return request.post<ApiResponse<UserInfo>>('/auth/register', payload).then((r) => r.data.data)
}

export function fetchProfileApi(): Promise<UserInfo> {
  return request.get<ApiResponse<UserInfo>>('/auth/me').then((r) => r.data.data)
}