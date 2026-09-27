<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { adminSummaryApi, adminFeedbackApi, replyFeedbackApi, type AdminFeedback, type AdminSummary } from '@/api/modules/admin'
import Pagination from '@/components/common/Pagination.vue'
const summary=ref<AdminSummary|null>(null), rows=ref<AdminFeedback[]>([]), loading=ref(false), reply=ref<AdminFeedback|null>(null), dialogVisible=ref(false), replyText=ref(''), replyStatus=ref(1),saving=ref(false)
const labels=['待处理','处理中','已解决','已关闭']
const current = ref(1), total = ref(0), statusFilter = ref(-1)
const terminal = computed(() => (reply.value?.status ?? 0) >= 2)
const transitions = computed(() => reply.value?.status === 0 ? [1, 3] : reply.value?.status === 1 ? [2, 3] : [])
let loadVersion = 0
async function load() {
  const version = ++loadVersion
  loading.value = true
  try {
    const [s, p] = await Promise.all([adminSummaryApi(), adminFeedbackApi(current.value, statusFilter.value < 0 ? undefined : statusFilter.value)])
    if (version !== loadVersion) return
    summary.value = s
    total.value = p.total
    if (current.value > 1 && !p.records.length) {
      current.value = Math.max(1, p.pages)
      await load()
      return
    }
    rows.value = p.records
  } catch {
    // Shared request handling explains errors; leave refresh available for retry.
  } finally {
    if (version === loadVersion) loading.value = false
  }
}
function filterChanged() { current.value = 1; void load() }
function pageChanged(page: number) { current.value = page; void load() }
async function saveReply() {
  if (!reply.value || saving.value || terminal.value) return
  if (!replyText.value.trim()) { ElMessage.warning('请填写处理回复'); return }
  saving.value = true
  try {
    await replyFeedbackApi(reply.value.id, { expectedStatus: reply.value.status, status: replyStatus.value, replyContent: replyText.value.trim() })
    ElMessage.success('反馈已更新')
    dialogVisible.value = false
    reply.value = null
    await load()
  } catch {
    // Preserve the typed reply on conflict; the server asks the user to refresh.
  } finally {
    saving.value = false
  }
}
function openReply(row: AdminFeedback) {
  reply.value = row
  replyText.value = row.replyContent || ''
  replyStatus.value = row.status === 0 ? 1 : row.status === 1 ? 2 : row.status
  dialogVisible.value = true
}
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
      <div class="feedback-tools">
        <el-select v-model="statusFilter" aria-label="筛选反馈状态" @change="filterChanged">
          <el-option label="全部状态" :value="-1"/>
          <el-option v-for="(label, index) in labels" :key="index" :label="label" :value="index"/>
        </el-select>
        <el-button :loading="loading" @click="load">刷新反馈</el-button>
      </div>
      <el-table :data="rows" empty-text="暂无反馈">
        <el-table-column prop="title" label="标题" min-width="180"/>
        <el-table-column prop="type" label="类型" width="100"/>
        <el-table-column label="状态" width="110">
          <template #default="scope"><el-tag>{{ labels[scope.row.status] }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="createTime" label="时间" width="180"/>
        <el-table-column label="操作" width="100">
          <template #default="scope"><el-button link type="primary" @click="openReply(scope.row)">{{ scope.row.status >= 2 ? '查看' : '处理' }}</el-button></template>
        </el-table-column>
      </el-table>
      <div class="feedback-pagination"><Pagination :total="total" :current="current" :size="20" @change="pageChanged"/></div>
    </section>
    <el-dialog v-model="dialogVisible" :title="terminal ? '查看反馈' : '处理反馈'" width="min(520px, calc(100vw - 32px))"
      :close-on-click-modal="!saving" :close-on-press-escape="!saving" :show-close="!saving">
      <template v-if="reply">
        <p><strong>{{ reply.title }}</strong></p>
        <p class="text">{{ reply.content }}</p>
        <p class="text">联系方式：{{ reply.contact || '未提供' }}</p>
        <div class="feedback-images"><el-image v-for="url in reply.images" :key="url" :src="url" :preview-src-list="reply.images" preview-teleported fit="cover"/></div>
        <p v-if="terminal">{{ labels[reply.status] }}：此反馈已结束，仅可查看。</p>
        <el-select v-else v-model="replyStatus" aria-label="反馈状态" :disabled="saving">
          <el-option v-for="state in transitions" :key="state" :label="labels[state]" :value="state"/>
        </el-select>
        <el-input v-model="replyText" type="textarea" :rows="4" maxlength="2000" :disabled="terminal || saving" aria-label="处理回复"/>
        <p v-if="reply.replyTime" class="reply-time">上次回复：{{ reply.replyTime }}</p>
      </template>
      <template #footer>
        <el-button :disabled="saving" @click="dialogVisible=false">{{ terminal ? '关闭' : '取消' }}</el-button>
        <el-button v-if="!terminal" type="primary" :loading="saving" @click="saveReply">保存</el-button>
      </template>
    </el-dialog>
  </main>
</template>
<style scoped>.admin{max-width:1200px;margin:auto;padding:24px 16px}.stats{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:16px}.stat{padding:22px;display:flex;flex-direction:column;gap:8px}.stat strong{font-size:28px;color:#2f7bff}.stat span{color:#64748b}.panel{padding:20px;margin-top:24px}.heading{display:flex;justify-content:space-between;align-items:center}.text{white-space:pre-wrap;line-height:1.6}.el-input,.el-select{margin-top:14px;width:100%}@media(max-width:700px){.stats{grid-template-columns:repeat(2,minmax(0,1fr))}.panel{padding:10px}}
.feedback-tools{display:flex;align-items:center;gap:12px;margin-bottom:16px}.feedback-tools .el-select{width:180px;margin:0}.feedback-images{display:flex;flex-wrap:wrap;gap:8px}.feedback-images .el-image{width:100px;height:80px}.reply-time{color:#64748b;font-size:12px}.text{overflow-wrap:anywhere}.feedback-pagination{overflow-x:auto}
</style>
