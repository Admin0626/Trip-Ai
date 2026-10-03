<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { profileApi, updateProfileApi, changePasswordApi, uploadImageApi, statsApi, type UserStats, type UserProfile } from '@/api/modules/user'
import { useUserStore } from '@/store/user'
import EmailVerification from '@/components/EmailVerification.vue'
const emailVerified=ref(false)
function verifiedEmail(user:UserInfo){profile.email=user.email||'';emailVerified.value=!!user.emailVerified;store.updateProfile(user)}
const store=useUserStore(), loading=ref(false),saving=ref(false),passwordSaving=ref(false),uploading=ref(false)
const router = useRouter()
const stats=ref<UserStats|null>(null),statsLoading=ref(false),statsFailed=ref(false)
const statsCards=[{key:'favoriteCount',label:'收藏记录',link:'/user/favorites'},{key:'bookingCount',label:'预约记录',link:'/user/bookings'},{key:'planCount',label:'个人规划',link:'/plan'},{key:'commentCount',label:'评论记录',link:''}] as const
async function loadStats(){statsLoading.value=true;statsFailed.value=false;try{stats.value=await statsApi()}catch{statsFailed.value=true}finally{statsLoading.value=false}}
const profile=reactive({nickname:'',avatar:'',phone:'',email:'',city:''}), password=reactive({oldPassword:'',newPassword:'',confirm:''})
async function load() {
  loading.value = true
  try {
    const data: UserProfile = await profileApi()
    Object.assign(profile, { nickname: data.nickname || '', avatar: data.avatar || '', phone: data.phone || '', email: data.email || '', city: data.city || '' })
    store.updateProfile(data)
    emailVerified.value=!!data.emailVerified
  } catch {
    // The request interceptor already reports errors and handles expired sessions.
  } finally {
    loading.value = false
  }
}
async function save(){if(saving.value)return;if(!profile.nickname.trim()){ElMessage.warning('昵称不能为空');return} saving.value=true;try{const data=await updateProfileApi({...profile,nickname:profile.nickname.trim()});store.updateProfile(data);ElMessage.success('资料已保存')}catch{}finally{saving.value=false}}
async function changePassword() {
  if (passwordSaving.value) return
  if (password.newPassword !== password.confirm) {
    ElMessage.warning('两次新密码不一致')
    return
  }
  passwordSaving.value = true
  try {
    await changePasswordApi({ oldPassword: password.oldPassword, newPassword: password.newPassword })
    Object.assign(password, { oldPassword: '', newPassword: '', confirm: '' })
    store.resetSession()
    ElMessage.success('密码已修改，请重新登录')
    await router.replace('/login')
  } catch {
    // Keep the form and session on validation failure; the interceptor shows why.
  } finally {
    passwordSaving.value = false
  }
}
async function upload(event:Event){const file=(event.target as HTMLInputElement).files?.[0];if(!file)return;if(!['image/png','image/jpeg'].includes(file.type)||file.size>5*1024*1024){ElMessage.warning('请选择5MB以内PNG/JPEG图片');return}uploading.value=true;try{profile.avatar=(await uploadImageApi(file)).url;ElMessage.success('头像上传成功')}catch{}finally{uploading.value=false;(event.target as HTMLInputElement).value=''}}
onMounted(()=>{void load();void loadStats()})
</script>
<template><main class="profile" v-loading="loading"><h1>个人资料</h1><section class="card panel" aria-label="个人统计" v-loading="statsLoading"><div class="stats-heading"><h2>我的旅行记录</h2><el-button :loading="statsLoading" @click="loadStats">刷新统计</el-button></div><el-alert v-if="statsFailed" title="统计加载失败，请刷新重试" type="error" :closable="false"/><div v-else class="stats-grid"><div v-for="s in statsCards" :key="s.key" class="stats-card" :data-stat="s.key"><strong>{{stats?.[s.key]??'—'}}</strong><span>{{s.label}}</span><router-link v-if="s.link" :to="s.link">查看记录</router-link></div></div><p class="stats-note">统计当前账号的记录：预约包含已取消/已完成，评论与规划不计已删除记录。</p></section><section class="card panel"><h2>基本资料</h2><el-form label-position="top"><el-form-item label="登录名"><el-input :model-value="store.userInfo?.username" disabled/></el-form-item><el-form-item label="昵称"><el-input v-model="profile.nickname" maxlength="50" aria-label="昵称"/></el-form-item><el-form-item label="头像"><div class="avatar-edit"><el-avatar :size="72" :src="profile.avatar"/><input type="file" accept="image/png,image/jpeg" :disabled="uploading" aria-label="选择头像" @change="upload"/></div></el-form-item><el-form-item label="手机号"><el-input v-model="profile.phone" maxlength="20" aria-label="手机号"/></el-form-item><el-form-item label="邮箱"><el-input v-model="profile.email" maxlength="100" aria-label="邮箱" readonly/><span>请在下方验证或更换邮箱</span></el-form-item><el-form-item label="所在城市"><el-input v-model="profile.city" maxlength="50" aria-label="所在城市"/></el-form-item><el-button type="primary" :loading="saving" @click="save">保存资料</el-button></el-form></section><EmailVerification :email="profile.email" :verified="emailVerified" @verified="verifiedEmail"/><section class="card panel"><h2>修改密码</h2><el-form label-position="top"><el-form-item label="原密码"><el-input v-model="password.oldPassword" type="password" show-password aria-label="原密码"/></el-form-item><el-form-item label="新密码"><el-input v-model="password.newPassword" type="password" show-password aria-label="新密码" placeholder="8—20位，含字母和数字"/></el-form-item><el-form-item label="确认新密码"><el-input v-model="password.confirm" type="password" show-password aria-label="确认新密码"/></el-form-item><el-button type="warning" :loading="passwordSaving" @click="changePassword">修改密码</el-button></el-form></section></main></template>
<style scoped>.profile{max-width:760px;margin:auto;padding:24px 16px}.panel{padding:24px;margin:20px 0}.avatar-edit{display:flex;align-items:center;gap:16px}.avatar-edit input{max-width:260px}.stats-heading{display:flex;align-items:center;justify-content:space-between}.stats-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px}.stats-card{display:flex;flex-direction:column;gap:8px;padding:16px 12px;background:var(--trip-tint);border-radius:8px}.stats-card strong{font-size:28px;color:var(--trip-forest)}.stats-card span,.stats-note{color:var(--trip-muted);font-size:13px}.stats-card a{font-size:12px;color:var(--trip-forest)}.stats-note{line-height:1.6}@media(max-width:640px){.panel{padding:16px}.stats-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.avatar-edit{flex-wrap:wrap}.avatar-edit input{max-width:100%}}</style>
