<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import EmailCodeInput from '@/components/EmailCodeInput.vue'
import { bindingCodeApi, verifyEmailApi } from '@/api/modules/auth'
const props=defineProps<{ email: string; verified: boolean }>()
const emit=defineEmits<{ verified: [UserInfo] }>()
const form=reactive({email:'',password:'',code:''}),busy=ref(false)
const send=()=>bindingCodeApi({email:form.email,password:form.password})
async function verify(){
  if(busy.value)return
  if(!/^\d{6}$/.test(form.code)){ElMessage.warning('请输入6位邮箱验证码');return}
  busy.value=true
  try{
    const user=await verifyEmailApi({email:form.email,code:form.code})
    emit('verified',user);Object.assign(form,{email:'',password:'',code:''});ElMessage.success('邮箱已验证，可用于找回密码')
  }catch{}finally{busy.value=false}
}
</script>
<template><section class="card email-panel"><h2>邮箱验证</h2><p class="email-status">当前邮箱：{{props.email||'未绑定'}} <el-tag :type="verified?'success':'info'">{{verified?'已验证':'未验证'}}</el-tag></p><p class="hint">验证后可用于找回密码。更换邮箱时，请输入新邮箱和当前登录密码。</p><el-form label-position="top"><el-form-item label="待验证邮箱"><el-input v-model="form.email" aria-label="待验证邮箱" type="email" maxlength="100" autocomplete="email" :disabled="busy"/></el-form-item><el-form-item label="当前密码"><el-input v-model="form.password" aria-label="当前密码" type="password" show-password autocomplete="current-password" :disabled="busy"/></el-form-item><EmailCodeInput v-model="form.code" :email="form.email" :disabled="busy||!form.password" :send="send"/><el-button type="primary" :loading="busy" @click="verify">验证并绑定邮箱</el-button></el-form></section></template>
<style scoped>.email-panel{padding:24px;margin:20px 0}.hint{font-size:13px;line-height:1.7;color:var(--trip-muted)}.email-status{overflow-wrap:anywhere}@media(max-width:640px){.email-panel{padding:16px}}</style>
