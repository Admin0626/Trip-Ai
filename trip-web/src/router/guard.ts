// 路由守卫（03-前端设计.md §3.4）
import type { Router } from 'vue-router'
import { useUserStore } from '@/store/user'

export function setupGuard(router: Router): void {
  router.beforeEach((to) => {
    document.title = `${(to.meta.title as string) || ''} - 智游行程`
    const userStore = useUserStore()

    // 公开页直接放行
    if (to.meta.public) return true

    // 未登录 → 登录页带 redirect
    if (!userStore.isLoggedIn) {
      return { path: '/login', query: { redirect: to.fullPath } }
    }
    if (to.meta.admin && !userStore.isAdmin) return { path: '/' }

    return true
  })
}
