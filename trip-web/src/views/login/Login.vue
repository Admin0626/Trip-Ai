<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useUserStore } from '@/store/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function onSubmit(): Promise<void> {
  if (!formRef.value || loading.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid || loading.value) return
  loading.value = true
  try {
    await userStore.login({ ...form })
    ElMessage.success('登录成功')
    const redirect = (route.query.redirect as string) || '/'
    void router.push(redirect)
  } catch {
    // The request interceptor has shown the failure; allow another login attempt.
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-card card">
      <h2 class="auth-card__title">登录</h2>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" size="large" @keyup.enter="onSubmit">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="user1001" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="123456" autocomplete="current-password" />
        </el-form-item>
        <el-button type="primary" class="auth-card__submit" :loading="loading" @click="onSubmit">登 录</el-button>
      </el-form>
      <div class="auth-card__foot">
        <router-link to="/forgot-password">忘记密码？</router-link>
        <br />
        <span class="text-secondary">还没有账号？</span>
        <router-link to="/register">立即注册</router-link>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--trip-tint);
}

.auth-card {
  width: 400px;
  padding: 32px;

  &__title {
    margin: 0 0 20px;
    text-align: center;
    font-size: 22px;
  }

  &__submit {
    width: 100%;
  }

  &__foot {
    margin-top: 16px;
    text-align: center;
    font-size: 13px;
  }
}
</style>
