<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { myPlansApi, deletePlanApi, copyPlanApi, exportPlanApi } from '@/api/modules/plan'
import { formatMoney, planStatusText } from '@/utils/format'
import EmptyState from '@/components/common/EmptyState.vue'
import Pagination from '@/components/common/Pagination.vue'

const router = useRouter()
const list = ref<PlanVO[]>([])
const total = ref(0)
const current = ref(1)
const size = 8
const loading = ref(false)

const exportVisible = ref(false)
const exportText = ref('')
const exportingId = ref<number | null>(null)

async function load(): Promise<void> {
  loading.value = true
  try {
    const page = await myPlansApi({ current: current.value, size })
    list.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

function onPageChange(page: number): void {
  current.value = page
  void load()
}

function edit(p: PlanVO): void {
  void router.push(`/plan/${p.id}/edit`)
}

function copy(p: PlanVO): void {
  ElMessageBox.confirm(`复制「${p.title}」为草稿副本？`, '复制规划', { type: 'info' })
    .then(async () => {
      const newId = await copyPlanApi(p.id)
      ElMessage.success('已复制为草稿副本')
      void router.push(`/plan/${newId}/edit`)
    })
    .catch(() => {})
}

function del(p: PlanVO): void {
  ElMessageBox.confirm(`确定删除规划「${p.title}」？删除后不可恢复。`, '删除规划', { type: 'warning' })
    .then(async () => {
      await deletePlanApi(p.id)
      ElMessage.success('已删除')
      if (list.value.length === 1 && current.value > 1) current.value -= 1
      void load()
    })
    .catch(() => {})
}

async function doExport(p: PlanVO): Promise<void> {
  exportingId.value = p.id
  try {
    exportText.value = await exportPlanApi(p.id)
    exportVisible.value = true
  } finally {
    exportingId.value = null
  }
}

onMounted(load)
</script>

<template>
  <main class="plan-list container page-wrapper">
    <div class="page-head">
      <h1 class="page-head__title">我的规划</h1>
      <el-button type="primary" @click="router.push('/plan/create')">新建规划</el-button>
    </div>
    <p class="plan-intro">把期待变成行程，保存后随时回来继续编辑。</p>

    <div v-loading="loading">
      <template v-if="list.length">
        <div v-for="(p,index) in list" v-reveal="index" :key="p.id" class="plan-card card trip-interactive-card">
          <div class="plan-card__main" role="link" tabindex="0" @click="edit(p)" @keydown.enter="edit(p)">
            <h3 class="plan-card__title">{{ p.title }}</h3>
            <div class="plan-card__meta">
              <el-tag :type="p.status === 1 ? 'success' : 'info'" size="small">{{ planStatusText(p.status) }}</el-tag>
              <span>{{ p.startDate }} 起 · {{ p.days }} 天</span>
              <span>{{ (p.destinationIds || []).length }} 个目的地</span>
              <span>{{ p.peopleNum }} 人</span>
              <span class="money">预算 {{ formatMoney(p.budget) }}</span>
              <span class="text-secondary">{{ p.updateTime }}</span>
            </div>
          </div>
          <div class="plan-card__actions">
            <el-button size="small" @click="edit(p)">编辑</el-button>
            <el-button size="small" @click="copy(p)">复制</el-button>
            <el-button size="small" :loading="exportingId === p.id" @click="doExport(p)">导出</el-button>
            <el-button size="small" type="danger" plain @click="del(p)">删除</el-button>
          </div>
        </div>
      </template>
      <EmptyState v-else-if="!loading" class="plan-empty card" text="还没有保存的规划">
        <p class="text-secondary">从一份新的规划开始，慢慢安排你的旅程。</p>
        <el-button type="primary" @click="router.push('/plan/create')">新建规划</el-button>
      </EmptyState>
    </div>

    <Pagination v-if="total > size" :total="total" :current="current" :size="size" @change="onPageChange" />

    <el-dialog v-model="exportVisible" title="行程单" width="560px">
      <pre class="export-text">{{ exportText }}</pre>
    </el-dialog>
  </main>
</template>

<style scoped lang="scss">
@use '@/assets/styles/variables.scss' as *;

.page-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  flex-wrap: wrap;
  gap: 24px;

  &__title {
    margin: 0;
    font-size: 32px;
  }
}

.plan-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24px;
  margin-bottom: 24px;
  gap: 24px;
  cursor: pointer;

  &__main {
    flex: 1;
    min-width: 0;
  }

  &__title {
    margin: 0 0 8px;
    font-size: 20px;
  }

  &__meta {
    display: flex;
    gap: 16px;
    font-size: 14px;
    color: $color-text-secondary;
    align-items: center;
    flex-wrap: wrap;
  }

  &__actions {
    display: flex;
    gap: 4px;
    flex-shrink: 0;
    flex-wrap: wrap;
  }
}
.plan-intro { color: $color-text-secondary; margin: 0 0 48px; }
.plan-empty { box-shadow: none; }
.plan-empty p { margin: 0; text-align: center; }
@media (max-width: 767px) { .plan-card { flex-direction: column; align-items: stretch; }.plan-card__actions { gap: 8px; }.plan-card__actions :deep(.el-button) { margin-left: 0; } }

.export-text {
  white-space: pre-wrap;
  font-family: inherit;
  font-size: 13px;
  line-height: 1.7;
  background: #fafbfc;
  border-radius: $radius-md;
  padding: 16px;
  max-height: 460px;
  overflow: auto;
}
</style>
