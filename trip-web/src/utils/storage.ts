// 本地存储封装（03-前端设计.md §二 utils/storage.ts）
const PREFIX = 'trip_'

export function getToken(): string {
  return localStorage.getItem(PREFIX + 'token') || ''
}

export function setToken(token: string): void {
  localStorage.setItem(PREFIX + 'token', token)
}

export function removeToken(): void {
  localStorage.removeItem(PREFIX + 'token')
}

export function getUserInfo(): Record<string, unknown> | null {
  const raw = localStorage.getItem(PREFIX + 'userInfo')
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

export function setUserInfo(info: unknown): void {
  localStorage.setItem(PREFIX + 'userInfo', JSON.stringify(info))
}

export function removeUserInfo(): void {
  localStorage.removeItem(PREFIX + 'userInfo')
}

export function clearAuth(): void {
  removeToken()
  removeUserInfo()
}