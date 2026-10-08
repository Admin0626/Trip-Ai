<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { ArrowDown } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'

const router = useRouter()
const route = useRoute()
const menuOpen = ref(false)
watch(() => route.fullPath, () => { menuOpen.value = false })
const assistantActive = computed(() => ['/travel-assistant', '/recommend', '/ai-planner'].includes(route.path))
const userStore = useUserStore()

const nickname = computed(() => userStore.userInfo?.nickname ?? '未登录')

function handleCommand(cmd: string): void {
  if (cmd === 'logout') {
    ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' })
      .then(async () => {
        await userStore.logout()
        void router.push('/login')
      })
      .catch(() => {})
  } else if (cmd === 'favorites') {
    void router.push('/user/favorites')
  } else if (cmd === 'bookings') {
    void router.push('/user/bookings')
  } else if (cmd === 'plans') {
    void router.push('/plan')
  } else if (cmd === 'profile') {
    void router.push('/user/profile')
  } else if (cmd === 'preference') {
    void router.push('/user/preference')
  } else if (cmd === 'feedback') {
    void router.push('/user/feedback')
  } else if (cmd === 'admin' && userStore.isAdmin) {
    void router.push('/admin')
  }
}
</script>

<template>
  <header class="app-header">
    <div class="app-header__inner container">
      <router-link to="/" class="app-header__logo" aria-label="Trip-AI 智游行程首页">
        <span class="logo-text">Trip-AI <span class="logo-divider">/</span> 智游行程</span>
      </router-link>

      <button class="menu-toggle" type="button" :aria-expanded="menuOpen" aria-controls="main-navigation" @click="menuOpen = !menuOpen">{{ menuOpen ? '收起导航' : '导航菜单' }}</button>
      <nav id="main-navigation" class="app-header__nav" :class="{ 'is-open': menuOpen }" aria-label="主导航">
        <router-link to="/" class="nav-link" active-class="" exact-active-class="router-link-active">首页</router-link>
        <router-link to="/routes" class="nav-link">路线</router-link>
        <router-link to="/destinations" class="nav-link">目的地</router-link>
        <router-link to="/travel-assistant" class="nav-link" :class="{ 'router-link-active': assistantActive }">旅行助手</router-link>
        <router-link to="/knowledge" class="nav-link">旅行资料</router-link>
        <router-link to="/plan" class="nav-link">我的规划</router-link>
      </nav>

      <div class="app-header__user">
        <template v-if="userStore.isLoggedIn">
          <el-dropdown @command="handleCommand">
            <span class="user-chip">
              <el-avatar :size="28" :src="userStore.userInfo?.avatar" />
              <span class="user-name">{{ nickname }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人资料</el-dropdown-item>
                <el-dropdown-item command="preference">旅行偏好</el-dropdown-item>
                <el-dropdown-item command="plans">我的规划</el-dropdown-item>
                <el-dropdown-item command="favorites">我的收藏</el-dropdown-item>
                <el-dropdown-item command="bookings">我的预约</el-dropdown-item>
                <el-dropdown-item command="feedback">系统反馈</el-dropdown-item>
                <el-dropdown-item v-if="userStore.isAdmin" command="admin">管理后台</el-dropdown-item>
                <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
        <template v-else>
          <el-button text type="primary" @click="router.push('/login')">登录</el-button>
          <el-button type="primary" plain @click="router.push('/register')">注册</el-button>
        </template>
      </div>
    </div>
  </header>
</template>

<style scoped lang="scss">
@use '@/assets/styles/variables.scss' as *;

.app-header {
  position: sticky;
  top: 0;
  z-index: 100;
  background: #fff;
  border-bottom: 1px solid $color-border;

  &__inner {
    display: flex;
    align-items: center;
    gap: 32px;
    min-height: 72px;
  }

  &__logo {
    display: flex;
    align-items: center;
    gap: 8px;
    cursor: pointer;

    .logo-icon {
      font-size: 22px;
    }
    .logo-text {
      font-size: 20px;
      font-weight: 600;
      color: $color-primary;
    }
  }

  &__nav {
    display: flex;
    gap: 4px;
    flex: 1;

    .nav-link {
      padding: 12px 10px;
      border-radius: 8px;
      color: $color-text;
      font-size: 14px;

      &.router-link-active {
        color: $color-primary;
        background: #EDF4EF;
        font-weight: 600;
      }

      &:hover {
        color: $color-primary;
      }
    }
  }

  &__user {
    .user-chip {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      cursor: pointer;
      min-height: 44px;
    }
    .user-name {
      font-size: 14px;
      color: $color-text;
    }
  }
}

.container {
  max-width: 1320px;
  margin: 0 auto;
  padding: 0 20px;
  width: 100%;
}

@media (max-width: 1100px) {
  .app-header {
    &__inner { height: auto; min-height: 60px; flex-wrap: wrap; gap: 8px; padding-top: 10px; padding-bottom: 10px; }
    &__logo { flex-shrink: 0; white-space: nowrap; }
    &__user { margin-left: auto; }
    &__nav { order: 3; flex: 0 0 100%; justify-content: space-between;
      .nav-link { padding: 6px 6px; white-space: nowrap; }
    }
  }
}
.logo-divider { color: $color-text-secondary; margin: 0 4px; font-weight: 400; }
.menu-toggle { display: none; min-height: 44px; padding: 8px 12px; border: 1px solid $color-border; border-radius: 12px; background: white; color: $color-primary; font: inherit; font-size: 14px; cursor: pointer; }
@media (max-width: 767px) {
  .menu-toggle { display: block; margin-left: auto; }
  .app-header__logo .logo-text { font-size: 18px; }
  .app-header__user { margin-left: 0; }
  .app-header__nav { display: none; }
  .app-header__nav.is-open { display: grid; grid-template-columns: 1fr; text-align: left; }
  .user-name { max-width: 100px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .app-header__user :deep(.el-button) { padding: 8px 12px; margin-left: 0; }
}
</style>
