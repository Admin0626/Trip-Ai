// 用户状态（03-前端设计.md §5.1）
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { getToken, setSession, setUserInfo, getUserInfo, clearAuth, onAuthChange } from '@/utils/storage'
import { loginApi, fetchProfileApi, logoutApi } from '@/api/modules/auth'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(getToken())
  const userInfo = ref<UserInfo | null>(getUserInfo() as UserInfo | null)
  onAuthChange(() => {
    token.value = getToken()
    userInfo.value = getUserInfo() as UserInfo | null
  })

  const isLoggedIn = computed(() => !!token.value)
  const role = computed(() => userInfo.value?.role ?? 'GUEST')
  const isAdmin = computed(() => role.value === 'ADMIN')

  /** 登录：存 token + userInfo */
  async function login(payload: { username: string; password: string }): Promise<void> {
    const data = await loginApi(payload)
    setSession(data)
  }

  /** 拉取当前用户资料 */
  async function fetchProfile(): Promise<void> {
    const data = await fetchProfileApi()
    userInfo.value = data
    setUserInfo(data)
  }

  /** 服务端撤销当前会话后再清除本地登录态。 */
  async function logout(): Promise<void> {
    await logoutApi()
    clearAuth()
  }

  function updateProfile(data: UserInfo): void { setUserInfo(data) }
  function resetSession(): void { clearAuth() }

  return { token, userInfo, isLoggedIn, role, isAdmin, login, fetchProfile, logout, updateProfile, resetSession }
})
