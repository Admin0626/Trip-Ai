<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import draggable from 'vuedraggable'
import { createPlanApi, updatePlanApi, planDetailApi, planFromRouteApi } from '@/api/modules/plan'
import { destinationListApi, attractionsApi } from '@/api/modules/destination'
import { today, formatMoney } from '@/utils/format'
import EmptyState from '@/components/common/EmptyState.vue'

const route = useRoute()
const router = useRouter()

const editId = ref<number | null>(route.params.id ? Number(route.params.id) : null)

// ---------- 编辑状态数据 ----------
interface ItemVM {
  key: string
  id?: number
  timePoint?: string
  attractionId?: number
  title: string
  activity?: string
  transport?: string
  hotel?: string
  meal?: string
  durationMin?: number
  cost?: number
  tips?: string
}
interface DayVM {
  key: string
  title: string
  summary?: string
  items: ItemVM[]
}

let uid = 0
function nextUid(): string {
  uid += 1
  return `k${uid}`
}

const days = ref<DayVM[]>([])
const destOptions = ref<DestinationVO[]>([])
const form = reactive({
  title: '',
  destinationIds: [] as number[],
  startDate: today(),
  peopleNum: 2,
  budget: 3000,
})

const loading = ref(true)
const saving = ref(false)

// 景点抽屉
const pickerVisible = ref(false)
const pickerDest = ref<number | null>(null)
const attractionList = ref<AttractionVO[]>([])
const pickerTargetDay = ref(0)
const attractionsLoading = ref(false)

const activeDay = ref(0)

const totalCost = computed(() =>
  days.value.reduce((sum, d) => sum + d.items.reduce((s, it) => s + (it.cost || 0), 0), 0),
)
const budgetPercent = computed(() => {
  if (!form.budget) return 0
  return Math.min(100, Math.round((totalCost.value / form.budget) * 100))
})
const overBudget = computed(() => form.budget > 0 && totalCost.value > form.budget)

// ---------- 天数管理 ----------
function addDay(): void {
  if (days.value.length >= 30) {
    ElMessage.warning('最多安排 30 天')
    return
  }
  days.value.push({ key: nextUid(), title: `第 ${days.value.length + 1} 天`, items: [] })
}

function removeLastDay(): void {
  if (days.value.length <= 1) {
    ElMessage.warning('至少保留 1 天')
    return
  }
  days.value.splice(days.value.length - 1, 1)
  if (activeDay.value >= days.value.length) activeDay.value = days.value.length - 1
}

function ensureDays(n: number): void {
  while (days.value.length < n) addDay()
}

// ---------- 条目操作 ----------
function addManualItem(): void {
  const d = days.value[activeDay.value]
  if (!d) return
  d.items.push({ key: nextUid(), title: `行程 ${d.items.length + 1}` })
}

function removeItem(dayIdx: number, itemIdx: number): void {
  days.value[dayIdx].items.splice(itemIdx, 1)
}

// ---------- 景点抽屉 ----------
async function openPicker(dayIdx: number): Promise<void> {
  pickerTargetDay.value = dayIdx
  if (!destOptions.value.length) {
    destOptions.value = await destinationListApi()
  }
  pickerDest.value = destOptions.value[0]?.id ?? null
  pickerVisible.value = true
  if (pickerDest.value) void loadAttractions()
}

async function loadAttractions(): Promise<void> {
  if (!pickerDest.value) return
  attractionsLoading.value = true
  try {
    attractionList.value = await attractionsApi(pickerDest.value)
  } finally {
    attractionsLoading.value = false
  }
}

function addAttraction(a: AttractionVO): void {
  const d = days.value[pickerTargetDay.value]
  if (!d) return
  d.items.push({
    key: nextUid(),
    attractionId: a.id,
    title: a.name,
    timePoint: '09:00',
    durationMin: a.durationMin ?? 120,
    cost: a.ticketPrice ?? 0,
    tips: a.openTime ? `开放时间：${a.openTime}` : undefined,
  })
}

// ---------- 保存 ----------
function validate(status: number): boolean {
  if (!form.title.trim()) {
    ElMessage.warning('请填写规划标题')
    return false
  }
  if (!form.startDate || form.startDate < today() || !form.budget || form.budget <= 0) {
    ElMessage.warning('出发日期须为今天及以后，预算须大于 0')
    return false
  }
  if (!days.value.length || days.value.every((d) => !d.items.length)) {
    if (status === 1) {
      ElMessage.warning('正式保存要求：至少 1 天且每天至少 1 条行程')
      return false
    }
  }
  if (status === 1 && days.value.some((d) => !d.items.length)) {
    ElMessage.warning('正式保存要求：每一天都至少要有 1 条行程')
    return false
  }
  return true
}

function buildDTO(status: number): PlanSaveDTO {
  return {
    title: form.title.trim(),
    destinationIds: form.destinationIds,
    startDate: form.startDate,
    days: days.value.length,
    budget: form.budget,
    peopleNum: form.peopleNum,
    status,
    // 服务端按数组顺序重排 dayIndex / sortNo（BR-PLN-03），前端无需传
    dayList: days.value.map((d) => ({
      title: d.title,
      summary: d.summary,
      items: d.items.map((it, i) => ({
        sortNo: i + 1,
        timePoint: it.timePoint,
        attractionId: it.attractionId,
        title: it.title,
        activity: it.activity,
        transport: it.transport,
        hotel: it.hotel,
        meal: it.meal,
        durationMin: it.durationMin,
        cost: it.cost,
        tips: it.tips,
      })),
    })),
  }
}

async function save(status: number): Promise<void> {
  if (!validate(status)) return
  if (overBudget.value) {
    const ok = await ElMessageBox.confirm('当前预算已超支，仍要保存吗？', '预算提示', { type: 'warning' })
      .then(() => true)
      .catch(() => false)
    if (!ok) return
  }
  saving.value = true
  try {
    const dto = buildDTO(status)
    if (editId.value) {
      await updatePlanApi(editId.value, dto)
      ElMessage.success(status === 1 ? '规划已保存' : '草稿已保存')
    } else {
      const id = await createPlanApi(dto)
      editId.value = id
      ElMessage.success(status === 1 ? '规划已保存' : '草稿已保存')
    }
    void router.push('/plan')
  } finally {
    saving.value = false
  }
}

// ---------- 初始化 ----------
async function initFromRoute(): Promise<void> {
  const fromRoute = route.query.fromRoute ? Number(route.query.fromRoute) : null
  if (fromRoute) {
    const id = await planFromRouteApi(fromRoute)
    editId.value = id
    await router.replace(`/plan/${id}/edit`)
    await loadDetail(id)
    return
  }
  if (editId.value) {
    await loadDetail(editId.value)
    return
  }
  // 新建：默认 3 天框架
  ensureDays(3)
}

async function loadDetail(id: number): Promise<void> {
  const p = await planDetailApi(id)
  form.title = p.title
  form.destinationIds = p.destinationIds || []
  form.startDate = p.startDate
  form.peopleNum = p.peopleNum
  form.budget = p.budget
  days.value = (p.dayList || []).map((d) => ({
    key: nextUid(),
    title: d.title || `第 ${d.dayIndex} 天`,
    summary: d.summary,
    items: (d.items || []).map((it) => ({
      key: nextUid(),
      id: it.id,
      timePoint: it.timePoint,
      attractionId: it.attractionId,
      title: it.title,
      activity: it.activity,
      transport: it.transport,
      hotel: it.hotel,
      meal: it.meal,
      durationMin: it.durationMin,
      cost: it.cost,
      tips: it.tips,
    })),
  }))
  if (!days.value.length) ensureDays(3)
  activeDay.value = 0
}

onMounted(async () => {
  try {
    await initFromRoute()
    destOptions.value = await destinationListApi()
  } catch {
    /* 拦截器已提示 */
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div v-loading="loading" class="plan-editor">
    <div class="editor-headbar">
      <el-button text @click="router.push('/plan')">← 返回我的规划</el-button>
      <h1 class="editor-headbar__title">{{ editId ? '编辑规划' : '新建规划' }}</h1>
    </div>

    <div class="container">
      <!-- 基本信息 -->
      <div class="card section-card">
        <el-form :model="form" label-width="90px">
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="规划标题">
                <el-input v-model="form.title" placeholder="例如：我的云南 5 日自由行" maxlength="50" />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="出发日期">
                <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="出行人数">
                <el-input-number v-model="form.peopleNum" :min="1" :max="10" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="目的地">
                <el-select v-model="form.destinationIds" multiple filterable placeholder="选择目的地（可多选）" style="width: 100%">
                  <el-option v-for="d in destOptions" :key="d.id" :label="d.name" :value="d.id" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="预算（元）">
                <el-input-number v-model="form.budget" :min="0.01" :step="500" :max="1000000" style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </div>

      <!-- 天数 Tab + 编辑区 -->
      <div class="card section-card">
        <div class="days-toolbar">
          <el-tabs v-model="activeDay" type="card" class="days-tabs">
            <el-tab-pane v-for="(d, i) in days" :key="d.key" :name="i">
              <template #label>
                <span class="day-tab-label">
                  <el-input v-model="d.title" size="small" class="day-title-input" @click.stop />
                </span>
              </template>
            </el-tab-pane>
          </el-tabs>
          <div class="days-toolbar__btns">
            <el-button size="small" @click="addDay">+ 添加一天</el-button>
            <el-button size="small" plain @click="removeLastDay">删除最后一天</el-button>
          </div>
        </div>

        <div class="cross-day-targets" aria-label="跨天拖拽目标">
          <div v-for="(d, i) in days" :key="d.key" class="cross-day-target" :data-day="i">
            <span>拖到第 {{ i + 1 }} 天</span>
            <draggable :model-value="[]" group="plan-items" item-key="key" class="day-drop-zone"
              @update:model-value="(items: ItemVM[]) => d.items.push(...items)">
              <template #item><span /></template>
            </draggable>
          </div>
        </div>
        <div v-if="days[activeDay]" class="day-body">
          <div class="day-body__head">
            <span class="day-body__label">{{ form.startDate }} · {{ days[activeDay].items.length }} 条行程</span>
            <div class="day-body__ops">
              <el-button size="small" type="primary" plain @click="openPicker(activeDay)">+ 从景点库添加</el-button>
              <el-button size="small" @click="addManualItem">+ 手动添加一条</el-button>
            </div>
          </div>

          <draggable
            v-model="days[activeDay].items"
            group="plan-items"
            item-key="key"
            class="item-list"
            handle=".item-row__drag"
            :animation="150"
          >
            <template #item="{ element, index }">
              <div class="item-row">
                <span class="item-row__drag">⠿</span>
                <div class="item-row__fields f-main">
                  <el-input v-model="element.timePoint" placeholder="时间" class="f-time" />
                  <el-input v-model="element.title" placeholder="行程标题（必填）" class="f-title" />
                </div>
                <div class="item-row__fields f-second">
                  <el-input v-model="element.activity" placeholder="活动/项目" class="f-activity" />
                  <el-input v-model="element.transport" placeholder="交通方式" class="f-transport" />
                </div>
                <div class="item-row__fields f-extra">
                  <el-input-number v-model="element.durationMin" :min="0" placeholder="时长(分)" class="f-duration" controls-position="right" />
                  <el-input-number v-model="element.cost" :min="0" :step="50" placeholder="花费(元)" class="f-cost" controls-position="right" />
                </div>
                <el-input v-model="element.tips" placeholder="小贴士" class="f-tips" />
                <el-button size="small" type="danger" text @click="removeItem(activeDay, index)">删除</el-button>
              </div>
            </template>
          </draggable>

          <EmptyState v-if="!days[activeDay].items.length" icon="🗺️" text="当天暂无行程，点击上方按钮添加" />
        </div>
      </div>

      <!-- 右侧栏：预算 + 操作 -->
      <div class="card section-card side-panel">
        <h3 class="side-panel__title">预算统计</h3>
        <div class="budget-line">
          <span class="money">{{ formatMoney(totalCost) }}</span>
          <span class="text-secondary"> / {{ formatMoney(form.budget) }}</span>
        </div>
        <el-progress
          :percentage="budgetPercent"
          :status="overBudget ? 'exception' : budgetPercent >= 80 ? 'warning' : 'success'"
          :stroke-width="14"
        />
        <div v-if="overBudget" class="budget-warn">⚠️ 已超支 {{ formatMoney(totalCost - form.budget) }}</div>

        <el-divider />

        <div class="side-panel__tips">
          <p>· 拖动条目可调整同一天顺序，也可拖到其他天</p>
          <p>· 正式保存要求：每天至少 1 条行程</p>
        </div>

        <div class="side-panel__actions">
          <el-button :loading="saving" style="width: 100%" @click="save(0)">保存草稿</el-button>
          <el-button type="primary" :loading="saving" style="width: 100%" @click="save(1)">保存规划</el-button>
          <el-button style="width: 100%" @click="router.push('/ai-planner')">✨ 用自己的AI生成新规划</el-button>
        </div>
      </div>
    </div>

    <!-- 景点抽屉 -->
    <el-drawer v-model="pickerVisible" title="从景点库添加" size="420px">
      <el-select v-model="pickerDest" style="width: 100%" @change="loadAttractions">
        <el-option v-for="d in destOptions" :key="d.id" :label="d.name" :value="d.id" />
      </el-select>
      <div v-loading="attractionsLoading" class="attraction-list">
        <div v-for="a in attractionList" :key="a.id" class="attraction-item card">
          <img v-if="a.coverImg" :src="a.coverImg" :alt="a.name" loading="lazy" />
          <div class="attraction-item__body">
            <div class="attraction-item__title">{{ a.name }}</div>
            <div class="attraction-item__meta text-secondary">
              {{ a.openTime || '' }}{{ a.ticketPrice ? ` · ${formatMoney(a.ticketPrice)}` : '' }}
            </div>
            <div v-if="a.intro" class="attraction-item__intro">{{ a.intro }}</div>
            <el-button size="small" type="primary" plain style="margin-top: 8px" @click="addAttraction(a)">
              添加到第 {{ pickerTargetDay + 1 }} 天
            </el-button>
          </div>
        </div>
        <EmptyState v-if="!attractionsLoading && !attractionList.length" icon="🏛️" text="该目的地暂无景点数据" />
      </div>
    </el-drawer>
  </div>
</template>

<style scoped lang="scss">
@use '@/assets/styles/variables.scss' as *;

.plan-editor {
  .editor-headbar {
    display: flex;
    align-items: center;
    gap: 12px;
    max-width: 1200px;
    margin: 0 auto;
    padding: 16px 16px 0;

    &__title {
      margin: 0;
      font-size: 20px;
    }
  }

  .container {
    display: grid;
    grid-template-columns: 1fr 300px;
    gap: 16px;
    max-width: 1200px;
    margin: 0 auto;
    padding: 16px;
    align-items: start;
  }

  .section-card {
    padding: 20px;
    min-width: 0;
  }

  .section-card:first-child { grid-column: 1 / -1; }
  .cross-day-targets { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 16px; }
  .cross-day-target { position: relative; padding: 12px; border: 1px dashed #9baaca; border-radius: 8px; background: #f3f7ff; }
  .day-drop-zone { position: absolute; inset: 0; min-height: 40px; }

  .days-toolbar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;

    .days-tabs {
      flex: 1;
      min-width: 0;

      :deep(.el-tabs__item) {
        height: auto;
        padding: 6px 12px;
      }
    }

    &__btns {
      display: flex;
      gap: 4px;
      margin-left: 12px;
      flex-shrink: 0;
    }
  }

  .day-tab-label {
    display: inline-block;
    padding: 2px 0;
  }

  .day-title-input {
    width: 96px;
  }

  .day-body {
    &__head {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 12px;
    }

    &__label {
      font-size: 13px;
      color: $color-text-secondary;
    }

    &__ops {
      display: flex;
      gap: 4px;
    }
  }

  .item-list {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .item-row {
    display: grid;
    grid-template-columns: 20px minmax(0, 1fr) auto;
    align-items: center;
    gap: 8px;
    border: 1px solid $color-border;
    border-radius: $radius-md;
    padding: 10px;
    background: #fafbfc;

    &__drag {
      cursor: grab;
      color: #b0b7c3;
      font-size: 16px;
      user-select: none;
      flex-shrink: 0;
    }

    &__fields {
      display: flex;
      gap: 8px;
    }

    .f-main {
      grid-column: 2 / 4;
      min-width: 0;
      flex: 1.2;
      .f-time {
        width: 100px;
        flex-shrink: 0;
      }
      .f-title {
        flex: 1;
      }
    }

    .f-second {
      grid-column: 2 / 4;
      flex: 1.4;
      .f-activity {
        flex: 1;
        width: 0;
      }
      .f-transport {
        flex: 1;
        width: 0;
      }
    }

    .f-extra {
      grid-column: 2 / 4;
      flex-shrink: 0;
      .f-duration {
        width: 110px;
      }
      .f-cost {
        width: 110px;
      }
    }

    .f-tips {
      grid-column: 2;
      width: 100%;
      flex-shrink: 0;
    }
  }

  .side-panel {
    position: sticky;
    top: 76px;

    &__title {
      margin: 0 0 12px;
      font-size: 15px;
    }

    &__tips {
      font-size: 12px;
      color: $color-text-secondary;

      p {
        margin: 4px 0;
      }
    }

    &__actions {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
  }

  @media (max-width: 900px) {
    .container { grid-template-columns: minmax(0, 1fr); }
    .side-panel { position: static; }
    .days-toolbar { flex-wrap: wrap; }
  }

  .budget-line {
    display: flex;
    align-items: baseline;
    gap: 4px;
    margin-bottom: 8px;
    font-size: 20px;
  }

  .budget-warn {
    margin-top: 8px;
    color: $color-danger;
    font-size: 13px;
  }

  .attraction-list {
    margin-top: 12px;
    display: flex;
    flex-direction: column;
    gap: 12px;

    .attraction-item {
      overflow: hidden;

      img {
        width: 100%;
        height: 120px;
        object-fit: cover;
        display: block;
      }

      &__body {
        padding: 12px;
      }

      &__title {
        font-weight: 600;
      }

      &__meta {
        font-size: 12px;
        margin-top: 4px;
      }

      &__intro {
        font-size: 12px;
        color: $color-text-secondary;
        margin-top: 4px;
        line-height: 1.5;
        display: -webkit-box;
        -webkit-line-clamp: 2;
        -webkit-box-orient: vertical;
        overflow: hidden;
      }
    }
  }
}
</style>
