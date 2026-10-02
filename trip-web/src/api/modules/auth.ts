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
  emailCode?: string
}): Promise<UserInfo> {
  return request.post<ApiResponse<UserInfo>>('/auth/register', payload).then((r) => r.data.data)
}

export function fetchProfileApi(): Promise<UserInfo> {
  return request.get<ApiResponse<UserInfo>>('/auth/me').then((r) => r.data.data)
}

export function logoutApi(): Promise<void> {
  return request.post<ApiResponse<void>>('/auth/logout').then(r => r.data.data)
}

export interface EmailReceipt { message: string; expiresIn: number; retryAfter: number }
export const registrationCodeApi=(email:string)=>request.post<ApiResponse<EmailReceipt>>('/auth/email/code',{email}).then(r=>r.data.data)
export const passwordCodeApi=(email:string)=>request.post<ApiResponse<EmailReceipt>>('/auth/password/code',{email}).then(r=>r.data.data)
export const resetPasswordApi=(body:{email:string;code:string;newPassword:string})=>request.post<ApiResponse<void>>('/auth/password/reset',body).then(r=>r.data.data)
export const bindingCodeApi=(body:{email:string;password:string})=>request.post<ApiResponse<EmailReceipt>>('/user/email/code',body).then(r=>r.data.data)
export const verifyEmailApi=(body:{email:string;code:string})=>request.post<ApiResponse<UserInfo>>('/user/email/verify',body).then(r=>r.data.data)
