<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
const router=useRouter(),user=useUserStore(),leaving=ref(false)
async function logout(){if(leaving.value)return;try{await ElMessageBox.confirm('确定退出管理后台吗？','退出登录');leaving.value=true;await user.logout();await router.replace('/login')}catch{}finally{leaving.value=false}}
const links=[['/admin','概览与反馈'],['/admin/destinations','目的地与景点'],['/admin/routes','路线与行程'],['/admin/banners','首页轮播'],['/admin/bookings','预约管理'],['/admin/comments','评论管理'],['/admin/users','用户管理'],['/admin/logs','AI调用日志'],['/admin/ai/knowledge','知识资料'],['/admin/workbench','项目学习工作台']]
</script>
<template>
  <div class="admin-layout">
    <header class="admin-header"><router-link to="/admin">智游 · 管理后台</router-link><div class="admin-account"><router-link to="/">返回前台</router-link><el-button :loading="leaving" @click="logout">退出登录</el-button></div></header>
    <div class="admin-shell"><nav aria-label="后台导航"><router-link v-for="link in links" :key="link[0]" :to="link[0]!" :class="{active:$route.path===link[0]}">{{ link[1] }}</router-link></nav><div class="admin-content"><router-view/></div></div>
  </div>
</template>
<style>
.admin-layout{min-height:100vh;background:var(--trip-canvas);color:var(--trip-ink)}.admin-header{display:flex;justify-content:space-between;padding:20px 28px;background:#fff;border-bottom:1px solid var(--trip-border)}.admin-header a{color:var(--trip-forest);text-decoration:none;font-weight:600}.admin-shell{display:flex;max-width:1600px;margin:auto}.admin-shell nav{width:190px;flex-shrink:0;padding:20px 12px;display:flex;flex-direction:column;gap:6px}.admin-shell nav a{padding:12px;border-radius:8px;text-decoration:none;color:var(--trip-muted)}.admin-shell nav a.active{background:var(--trip-tint);color:var(--trip-forest)}.admin-content{min-width:0;flex:1}.admin-page{padding:24px;max-width:1250px;margin:auto}.admin-page h1{margin:0 0 8px;font-size:24px}.admin-help{color:var(--trip-muted);line-height:1.7;margin-bottom:20px}.admin-panel{padding:20px;background:#fff;border:1px solid var(--trip-border);border-radius:12px}.admin-tools{display:flex;gap:12px;flex-wrap:wrap;align-items:center;margin-bottom:20px}.admin-tools>.el-input{width:230px}.admin-tools>.el-select{width:160px}.admin-form{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:0 20px}.admin-form .wide{grid-column:1/-1}.admin-form .el-input-number,.admin-form .el-select,.admin-form .el-date-editor{width:100%}.admin-pagination{margin-top:16px;overflow:auto}.admin-dialog .el-dialog__body{max-height:70vh;overflow:auto}.admin-note{color:var(--trip-muted);font-size:13px}.admin-row-actions{display:flex;flex-wrap:wrap;gap:6px}.admin-cover{width:70px;height:48px;object-fit:cover;border-radius:6px}.admin-text{white-space:pre-wrap;overflow-wrap:anywhere}.admin-dialog .el-form-item__label{line-height:24px}
@media(max-width:760px){.admin-header{padding:16px}.admin-shell{display:block}.admin-shell nav{width:auto;flex-direction:row;overflow:auto;padding:10px}.admin-shell nav a{white-space:nowrap;padding:10px}.admin-page{padding:16px 10px}.admin-panel{padding:12px}.admin-form{grid-template-columns:minmax(0,1fr)}.admin-tools>.el-input,.admin-tools>.el-select{width:100%}}
.admin-account{display:flex;gap:16px;align-items:center}
</style>
