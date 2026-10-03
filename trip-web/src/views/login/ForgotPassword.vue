<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import EmailCodeInput from '@/components/EmailCodeInput.vue'
import { passwordCodeApi, resetPasswordApi } from '@/api/modules/auth'
import { useUserStore } from '@/store/user'
const router=useRouter(),store=useUserStore(),busy=ref(false)
const form=reactive({email:'',code:'',newPassword:'',confirm:''})
async function reset(){
  if(busy.value)return
  if(!/^\d{6}$/.test(form.code)){ElMessage.warning('请输入6位邮箱验证码');return}
  if(form.newPassword.length<8||form.newPassword.length>20||!/[a-zA-Z]/.test(form.newPassword)||!/[0-9]/.test(form.newPassword)){ElMessage.warning('新密码须为8-20位并同时包含字母与数字');return}
  if(form.newPassword!==form.confirm){ElMessage.warning('两次新密码不一致');return}
  busy.value=true
  try{
    await resetPasswordApi({email:form.email,code:form.code,newPassword:form.newPassword})
    if(store.userInfo?.email?.toLowerCase()===form.email.trim().toLowerCase())store.resetSession()
    Object.assign(form,{email:'',code:'',newPassword:'',confirm:''})
    ElMessage.success('密码已重置，请使用新密码登录')
    await router.replace('/login')
  }catch{}finally{busy.value=false}
}
</script>
<template><main class="recovery-page"><section class="card recovery-card"><h1>找回密码</h1><p class="hint">使用已验证邮箱重置密码。旧账号请先登录，在个人中心完成邮箱验证。重置成功后，所有设备需要重新登录。</p><el-form label-position="top" size="large"><el-form-item label="已验证邮箱"><el-input v-model="form.email" aria-label="已验证邮箱" type="email" maxlength="100" autocomplete="email" :disabled="busy"/></el-form-item><EmailCodeInput v-model="form.code" :email="form.email" :disabled="busy" :send="()=>passwordCodeApi(form.email)"/><el-form-item label="新密码"><el-input v-model="form.newPassword" aria-label="新密码" type="password" show-password maxlength="20" autocomplete="new-password" :disabled="busy"/></el-form-item><el-form-item label="确认新密码"><el-input v-model="form.confirm" aria-label="确认新密码" type="password" show-password maxlength="20" autocomplete="new-password" :disabled="busy"/></el-form-item><el-button type="primary" class="submit" :loading="busy" @click="reset">重置密码</el-button></el-form><router-link to="/login" class="back">返回登录</router-link></section></main></template>
<style scoped>.recovery-page{min-height:100vh;display:flex;align-items:center;justify-content:center;background:var(--trip-tint);padding:24px 16px;box-sizing:border-box}.recovery-card{width:420px;max-width:100%;padding:28px;box-sizing:border-box}h1{text-align:center;font-size:22px}.hint{font-size:13px;line-height:1.7;color:var(--trip-muted)}.submit{width:100%}.back{display:block;text-align:center;margin-top:20px;font-size:13px}</style>
