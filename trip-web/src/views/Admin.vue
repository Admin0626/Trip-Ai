<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { adminSummaryApi, adminFeedbackApi, replyFeedbackApi, type AdminFeedback, type AdminSummary } from '@/api/modules/admin'
const summary=ref<AdminSummary|null>(null), rows=ref<AdminFeedback[]>([]), loading=ref(false), reply=ref<AdminFeedback|null>(null), dialogVisible=ref(false), replyText=ref(''), replyStatus=ref(1),saving=ref(false)
const labels=['待处理','处理中','已解决','已关闭']
async function load(){loading.value=true;try{const [s,p]=await Promise.all([adminSummaryApi(),adminFeedbackApi()]);summary.value=s;rows.value=p.records}finally{loading.value=false}}
async function saveReply(){if(!reply.value||!replyText.value.trim())return;saving.value=true;try{await replyFeedbackApi(reply.value.id,{status:replyStatus.value,replyContent:replyText.value.trim()});ElMessage.success('反馈已更新');reply.value=null;dialogVisible.value=false;await load()}finally{saving.value=false}}
function openReply(row:AdminFeedback){reply.value=row;replyText.value=row.replyContent||'';replyStatus.value=row.status;dialogVisible.value=true}
onMounted(load)
</script>
<template>
  <main class="admin" v-loading="loading">
    <h1>运营概览</h1>
    <div class="stats" v-if="summary">
      <div class="card stat"><strong>{{ summary.users }}</strong><span>用户</span></div>
      <div class="card stat"><strong>{{ summary.destinations }}</strong><span>目的地</span></div>
      <div class="card stat"><strong>{{ summary.routes }}</strong><span>路线</span></div>
      <div class="card stat"><strong>{{ summary.pendingBookings }}</strong><span>待确认预约</span></div>
      <div class="card stat"><strong>{{ summary.pendingFeedback }}</strong><span>待处理反馈</span></div>
    </div>
    <section class="card panel">
      <div class="heading"><h2>反馈处理</h2><router-link to="/">返回前台</router-link></div>
      <el-table :data="rows" empty-text="暂无反馈">
        <el-table-column prop="title" label="标题" min-width="180"/>
        <el-table-column prop="type" label="类型" width="100"/>
        <el-table-column label="状态" width="110">
          <template #default="scope"><el-tag>{{ labels[scope.row.status] }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="createTime" label="时间" width="180"/>
        <el-table-column label="操作" width="100">
          <template #default="scope"><el-button link type="primary" @click="openReply(scope.row)">处理</el-button></template>
        </el-table-column>
      </el-table>
    </section>
    <el-dialog v-model="dialogVisible" title="处理反馈" width="520px">
      <template v-if="reply">
        <p><strong>{{ reply.title }}</strong></p>
        <p class="text">{{ reply.content }}</p>
        <el-select v-model="replyStatus" aria-label="反馈状态">
          <el-option v-for="(label,index) in labels.slice(1)" :key="index" :label="label" :value="index+1"/>
        </el-select>
        <el-input v-model="replyText" type="textarea" :rows="4" maxlength="2000" aria-label="处理回复"/>
      </template>
      <template #footer>
        <el-button @click="dialogVisible=false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveReply">保存</el-button>
      </template>
    </el-dialog>
  </main>
</template>
<style scoped>.admin{max-width:1200px;margin:auto;padding:24px 16px}.stats{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:16px}.stat{padding:22px;display:flex;flex-direction:column;gap:8px}.stat strong{font-size:28px;color:#2f7bff}.stat span{color:#64748b}.panel{padding:20px;margin-top:24px}.heading{display:flex;justify-content:space-between;align-items:center}.text{white-space:pre-wrap;line-height:1.6}.el-input,.el-select{margin-top:14px;width:100%}@media(max-width:700px){.stats{grid-template-columns:repeat(2,minmax(0,1fr))}.panel{padding:10px}}
</style>
