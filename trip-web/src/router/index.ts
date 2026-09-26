import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import UserLayout from '@/layouts/UserLayout.vue'

/**
 * 用户端路由（03-前端设计.md §3.1）
 * 第 2 批范围：Home / RouteList / RouteDetail / DestinationList / PlanList / PlanEditor / Favorites / Bookings / Login / Register
 */
export const userRoutes: RouteRecordRaw[] = [
  {
    path: '/',
    component: UserLayout,
    children: [
      { path: '', name: 'Home', component: () => import('@/views/home/Home.vue'), meta: { title: '首页', public: true } },
      { path: 'routes', name: 'RouteList', component: () => import('@/views/route/RouteList.vue'), meta: { title: '路线', public: true } },
      { path: 'route/:id', name: 'RouteDetail', component: () => import('@/views/route/RouteDetail.vue'), meta: { title: '路线详情', public: true } },
      { path: 'destinations', name: 'DestinationList', component: () => import('@/views/destination/DestinationList.vue'), meta: { title: '目的地', public: true } },
      { path: 'plan', name: 'PlanList', component: () => import('@/views/plan/PlanList.vue'), meta: { title: '我的规划' } },
      { path: 'plan/create', name: 'PlanCreate', component: () => import('@/views/plan/PlanEditor.vue'), meta: { title: '新建规划' } },
      { path: 'plan/:id/edit', name: 'PlanEdit', component: () => import('@/views/plan/PlanEditor.vue'), meta: { title: '编辑规划' } },
      { path: 'user/favorites', name: 'Favorites', component: () => import('@/views/user/Favorites.vue'), meta: { title: '我的收藏' } },
      { path: 'user/bookings', name: 'Bookings', component: () => import('@/views/user/Bookings.vue'), meta: { title: '我的预约' } },
    ],
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/Login.vue'),
    meta: { title: '登录', public: true },
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/login/Register.vue'),
    meta: { title: '注册', public: true },
  },
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

const router = createRouter({
  history: createWebHistory(),
  routes: userRoutes,
  scrollBehavior: () => ({ top: 0 }),
})

export default router