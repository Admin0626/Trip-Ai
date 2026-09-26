// 用户状态（03-前端设计.md §5.1）
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { getToken, setToken, setUserInfo, getUserInfo, clearAuth } from '@/utils/storage'
import { loginApi, fetchProfileApi } from '@/api/modules/auth'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(getToken())
  const userInfo = ref<UserInfo | null>(getUserInfo() as UserInfo | null)

  const isLoggedIn = computed(() => !!token.value)
  const role = computed(() => userInfo.value?.role ?? 'GUEST')
  const isAdmin = computed(() => role.value === 'ADMIN')

  /** 登录：存 token + userInfo */
  async function login(payload: { username: string; password: string }): Promise<void> {
    const data = await loginApi(payload)
    token.value = data.accessToken
    setToken(data.accessToken)
    userInfo.value = data.userInfo
    setUserInfo(data.userInfo)
  }

  /** 拉取当前用户资料 */
  async function fetchProfile(): Promise<void> {
    const data = await fetchProfileApi()
    userInfo.value = data
    setUserInfo(data)
  }

  /** 登出：清本地 + 跳登录 */
  function logout(): void {
    token.value = ''
    userInfo.value = null
    clearAuth()
  }

  return { token, userInfo, isLoggedIn, role, isAdmin, login, fetchProfile, logout }
})