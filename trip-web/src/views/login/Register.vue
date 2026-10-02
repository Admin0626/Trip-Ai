<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { registerApi, registrationCodeApi } from '@/api/modules/auth'
import EmailCodeInput from '@/components/EmailCodeInput.vue'

const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({ username: '', password: '', confirm: '', nickname: '', email: '', emailCode: '' })

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 4, max: 20, message: '用户名 4-20 位', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, max: 20, message: '密码 8-20 位', trigger: 'blur' },
    { pattern: /^(?=.*[a-zA-Z])(?=.*\d).+$/, message: '密码须同时包含字母与数字', trigger: 'blur' },
  ],
  confirm: [
    {
      validator: (_r, value, cb) => (value === form.password ? cb() : cb(new Error('两次密码不一致'))),
      trigger: 'blur',
    },
  ],
}

async function onSubmit(): Promise<void> {
  if (!formRef.value || loading.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  if(form.email.trim()&&!/^\d{6}$/.test(form.emailCode)){ElMessage.warning('填写邮箱时须输入6位验证码');return}
  loading.value = true
  try {
    await registerApi({ username: form.username, password: form.password, nickname: form.nickname,
      ...(form.email.trim()?{email:form.email.trim(),emailCode:form.emailCode}:{}) })
    ElMessage.success('注册成功，请登录')
    void router.push('/login')
  } catch {
    // Keep the form on validation or delivery failures.
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-card card">
      <h2 class="auth-card__title">注册</h2>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" size="large">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="4-20 位用户名" />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" placeholder="展示昵称（可选）" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="8-20 位，含字母和数字" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirm">
          <el-input v-model="form.confirm" type="password" show-password placeholder="再次输入密码" />
        </el-form-item>
        <el-form-item label="邮箱（可选，验证后可找回密码）"><el-input v-model="form.email" type="email" maxlength="100" aria-label="注册邮箱" :disabled="loading"/></el-form-item>
        <EmailCodeInput v-if="form.email.trim()" v-model="form.emailCode" :email="form.email" :disabled="loading" :send="()=>registrationCodeApi(form.email)"/>
        <el-button type="primary" class="auth-card__submit" :loading="loading" @click="onSubmit">注 册</el-button>
      </el-form>
      <div class="auth-card__foot">
        <span class="text-secondary">已有账号？</span>
        <router-link to="/login">去登录</router-link>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.auth-page {
  padding: 24px 16px;
  box-sizing: border-box;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #e8f1ff 0%, #f5f7fa 100%);
}

.auth-card {
  max-width: 100%;
  box-sizing: border-box;
  width: 420px;
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
