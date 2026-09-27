<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { ArrowDown } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'

const router = useRouter()
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
      <div class="app-header__logo" @click="router.push('/')">
        <span class="logo-icon">✈️</span>
        <span class="logo-text">智游行程</span>
      </div>

      <nav class="app-header__nav">
        <router-link to="/" class="nav-link">首页</router-link>
        <router-link to="/routes" class="nav-link">路线</router-link>
        <router-link to="/destinations" class="nav-link">目的地</router-link>
        <router-link to="/recommend" class="nav-link">旅行推荐</router-link>
        <router-link to="/ai-planner" class="nav-link">AI规划</router-link>
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
  box-shadow: 0 1px 4px rgba(31, 41, 55, 0.08);

  &__inner {
    display: flex;
    align-items: center;
    gap: 24px;
    height: 60px;
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
      font-size: 18px;
      font-weight: 700;
      color: $color-primary;
    }
  }

  &__nav {
    display: flex;
    gap: 4px;
    flex: 1;

    .nav-link {
      padding: 6px 14px;
      border-radius: 6px;
      color: $color-text;
      font-size: 14px;
      transition: all 0.2s;

      &.router-link-active {
        color: $color-primary;
        background: rgba(47, 123, 255, 0.08);
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
      outline: none;
    }
    .user-name {
      font-size: 14px;
      color: $color-text;
    }
  }
}

.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 16px;
  width: 100%;
}

@media (max-width: 640px) {
  .app-header {
    &__inner { height: auto; min-height: 60px; flex-wrap: wrap; gap: 8px; padding-top: 10px; padding-bottom: 10px; }
    &__logo { flex-shrink: 0; white-space: nowrap; }
    &__user { margin-left: auto; }
    &__nav { order: 3; flex: 0 0 100%; justify-content: space-between;
      .nav-link { padding: 6px 6px; white-space: nowrap; }
    }
  }
}
</style>
