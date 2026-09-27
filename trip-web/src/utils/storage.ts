// 本地存储封装（03-前端设计.md §二 utils/storage.ts）
const PREFIX = 'trip_'
const authListeners = new Set<() => void>()

export function onAuthChange(listener: () => void): void { authListeners.add(listener) }
function notifyAuth(): void { authListeners.forEach(listener => listener()) }
window.addEventListener('storage', event => {
  if (event.key === null || ['trip_token', 'trip_refreshToken', 'trip_userInfo'].includes(event.key)) notifyAuth()
})

export function getRefreshToken(): string { return localStorage.getItem(PREFIX + 'refreshToken') || '' }

export function setSession(data: LoginResult): void {
  localStorage.setItem(PREFIX + 'token', data.accessToken)
  localStorage.setItem(PREFIX + 'refreshToken', data.refreshToken)
  localStorage.setItem(PREFIX + 'userInfo', JSON.stringify(data.userInfo))
  notifyAuth()
}

export function getToken(): string {
  return localStorage.getItem(PREFIX + 'token') || ''
}

export function setToken(token: string): void {
  localStorage.setItem(PREFIX + 'token', token)
  notifyAuth()
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
  notifyAuth()
}

export function removeUserInfo(): void {
  localStorage.removeItem(PREFIX + 'userInfo')
}

export function clearAuth(): void {
  removeToken()
  removeUserInfo()
  localStorage.removeItem(PREFIX + 'refreshToken')
  notifyAuth()
}
