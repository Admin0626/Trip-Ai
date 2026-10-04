<script setup lang="ts">
import {onBeforeUnmount,ref} from 'vue'
import {useRouter} from 'vue-router'
import {ElMessage,ElMessageBox} from 'element-plus'
import request from '@/api/request'
import {useUserStore} from '@/store/user'
const store=useUserStore(),router=useRouter(),password=ref(''),confirmation=ref(''),acknowledged=ref(false),busy=ref(false)
onBeforeUnmount(()=>{password.value='';confirmation.value=''})
async function close(){
  if(busy.value)return
  if(!password.value||confirmation.value!=='注销账号'||!acknowledged.value){ElMessage.warning('请填写当前密码、输入注销账号并确认影响');return}
  try{await ElMessageBox.confirm('注销后无法登录或恢复此账号。确定继续吗？','最后确认',{confirmButtonText:'确认注销',cancelButtonText:'保留账号',type:'warning'})}catch{return}
  busy.value=true
  try{await request.post('/user/account/close',{password:password.value,confirmation:confirmation.value});password.value='';store.resetSession();ElMessage.success('账号已注销');await router.replace('/login')}catch{}finally{busy.value=false}
}
</script>
<template><section v-if="!store.isAdmin" class="card close-account"><h2>注销账号</h2><p>注销会清除昵称、头像、联系方式、城市和旅行偏好，撤销所有登录会话。用户名保留且不能重新注册；历史评论、预约和规划保留关联记录，无法再通过此账号访问。此操作不可恢复。</p><p>存在待确认或已确认预约时，请先取消预约或等待完成。</p><el-form label-position="top" :disabled="busy"><el-form-item label="当前密码"><el-input v-model="password" type="password" autocomplete="off" aria-label="注销当前密码" maxlength="100"/></el-form-item><el-form-item label="输入‘注销账号’确认"><el-input v-model="confirmation" aria-label="注销确认文字" maxlength="4"/></el-form-item><el-checkbox v-model="acknowledged" data-testid="close-ack">我已了解注销不可恢复及历史记录保留规则</el-checkbox><div><el-button type="danger" :loading="busy" :disabled="!acknowledged" @click="close">申请注销账号</el-button></div></el-form></section></template>
<style scoped>.close-account{padding:24px;margin:20px 0;border:1px solid #fecaca}.close-account p{color:var(--trip-muted);line-height:1.8}.el-button{margin-top:16px}.el-checkbox{height:auto;white-space:normal}:deep(.el-checkbox__label){white-space:normal}@media(max-width:640px){.close-account{padding:16px}}</style>
