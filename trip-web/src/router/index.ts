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
      { path: 'destination/:id', name: 'DestinationDetail', component: () => import('@/views/destination/DestinationDetail.vue'), meta: { title: '目的地详情', public: true } },
      { path: 'user/profile', name: 'Profile', component: () => import('@/views/user/Profile.vue'), meta: { title: '个人资料' } },
      { path: 'user/preference', name: 'Preference', component: () => import('@/views/user/Preference.vue'), meta: { title: '旅行偏好' } },
      { path: 'user/feedback', name: 'Feedback', component: () => import('@/views/user/Feedback.vue'), meta: { title: '意见反馈' } },
      { path: 'recommend', name: 'Recommend', component: () => import('@/views/recommend/Recommend.vue'), meta: { title: '旅行推荐' } },
      { path: 'ai-planner', name: 'AiPlanner', component: () => import('@/views/plan/AiPlanner.vue'), meta: { title: 'AI规划' } },
      { path: 'knowledge', name: 'KnowledgeSearch', component: () => import('@/views/knowledge/Search.vue'), meta: { title: '旅行资料' } },
      { path: 'knowledge/:id', name: 'KnowledgeDocument', component: () => import('@/views/knowledge/Document.vue'), meta: { title: '资料来源' } },
      { path: 'plan', name: 'PlanList', component: () => import('@/views/plan/PlanList.vue'), meta: { title: '我的规划' } },
      { path: 'plan/create', name: 'PlanCreate', component: () => import('@/views/plan/PlanEditor.vue'), meta: { title: '新建规划' } },
      { path: 'plan/:id/edit', name: 'PlanEdit', component: () => import('@/views/plan/PlanEditor.vue'), meta: { title: '编辑规划' } },
      { path: 'user/favorites', name: 'Favorites', component: () => import('@/views/user/Favorites.vue'), meta: { title: '我的收藏' } },
      { path: 'user/bookings', name: 'Bookings', component: () => import('@/views/user/Bookings.vue'), meta: { title: '我的预约' } },
    ],
  },
  {
    path: '/admin', component: () => import('@/layouts/AdminLayout.vue'), meta: { admin: true },
    children: [
      { path: '', name: 'Admin', component: () => import('@/views/Admin.vue'), meta: { title: '概览与反馈' } },
      { path: 'destinations', component: () => import('@/views/admin/Destinations.vue'), meta: { title: '目的地与景点' } },
      { path: 'routes', component: () => import('@/views/admin/Routes.vue'), meta: { title: '路线与行程' } },
      { path: 'banners', component: () => import('@/views/admin/Banners.vue'), meta: { title: '首页轮播' } },
      { path: 'ai/knowledge', component: () => import('@/views/admin/Knowledge.vue'), meta: { title: '知识资料' } },
      ...(['bookings','comments','users','logs'] as const).map(kind=>({path:kind,component:()=>import('@/views/admin/Operations.vue'),props:{kind},meta:{title:{bookings:'预约管理',comments:'评论管理',users:'用户管理',logs:'AI调用日志'}[kind]}})),
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
