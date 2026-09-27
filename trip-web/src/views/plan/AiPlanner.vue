<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { today } from '@/utils/format'
import { createPlanApi } from '@/api/modules/plan'
import { destinationListApi } from '@/api/modules/destination'
import { plannerOptions, plannerUsage, testModel, generatePlan, type PlannerPreview, type PlannerUsage } from '@/api/modules/planner'

const router = useRouter(), user = useUserStore()
const connection = reactive({ baseUrl: '', model: '', apiKey: '' })
const form = reactive({ query: '', days: 3, budget: 3000, peopleNum: 2, startDate: today(), destinationIds: [] as number[] })
const destinations = ref<DestinationVO[]>([])
const options = ref<{ allowedHosts: string[]; allowLoopback: boolean }>({ allowedHosts: [], allowLoopback: false })
const busy = ref('')
const error = ref('')
const connected = ref(false)
const preview = ref<PlannerPreview | null>(null)
const title = ref('')
const acknowledged = ref(false)
const usage = ref<PlannerUsage | null>(null)
const usageError = ref(false)
const refreshingUsage = ref(false)
const exhausted = computed(() => !!usage.value && [usage.value.quota.hourly, usage.value.quota.daily, usage.value.quota.globalDaily].some(q => q.remaining === 0))
const storageKey = computed(() => `trip_ai_connection_${user.userInfo?.id ?? 'none'}`)
let revision = 0
function invalidate() { revision++; preview.value = null; acknowledged.value = false; error.value = '' }
watch(connection, () => { invalidate(); connected.value = false }, { deep: true, flush: 'sync' })
watch(form, invalidate, { deep: true, flush: 'sync' })
watch(storageKey, () => { connection.apiKey = ''; usage.value = null; loadSettings(); void refreshUsage() })
onBeforeUnmount(() => { revision++; connection.apiKey = '' })

function loadSettings() {
  connection.baseUrl = ''; connection.model = ''; connection.apiKey = ''
  try {
    const saved = JSON.parse(localStorage.getItem(storageKey.value) || '{}')
    connection.baseUrl = typeof saved.baseUrl === 'string' ? saved.baseUrl : ''
    connection.model = typeof saved.model === 'string' ? saved.model : ''
  } catch { /* Corrupt browser settings are ignored. */ }
}
function validConnection() {
  if (!connection.baseUrl.trim() || !connection.model.trim()) { ElMessage.warning('请填写API基础地址和模型名称'); return false }
  try {
    const url = new URL(connection.baseUrl.trim())
    if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.search || url.hash
      || url.pathname.replace(/\/+$/, '').endsWith('/chat/completions')) throw new Error()
  } catch { ElMessage.warning('请填写不含账号、查询参数或/chat/completions的API基础地址'); return false }
  return true
}
function saveSettings() {
  if (!validConnection()) return
  try {
    localStorage.setItem(storageKey.value, JSON.stringify({ baseUrl: connection.baseUrl.trim(), model: connection.model.trim() }))
    ElMessage.success('已记住当前账号的地址和模型，API Key未保存')
  } catch { ElMessage.error('浏览器无法保存设置，可继续临时使用') }
}
function clearSettings() {
  try { localStorage.removeItem(storageKey.value) } catch { ElMessage.warning('浏览器无法移除设置，请手动清理站点数据') }
  connection.baseUrl = ''; connection.model = ''; connection.apiKey = ''
}
function config() { return { baseUrl: connection.baseUrl.trim(), model: connection.model.trim(), apiKey: connection.apiKey.trim() } }
async function refreshUsage() {
  refreshingUsage.value = true
  const account = storageKey.value
  try { const result = await plannerUsage(); if (account === storageKey.value) { usage.value = result; usageError.value = false } }
  catch { if (account === storageKey.value) { usage.value = null; usageError.value = true } }
  finally { refreshingUsage.value = false }
}
function resetTime(value: string) { return new Date(value).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false }) }
async function test() {
  if (!validConnection()) return
  busy.value = 'test'; error.value = ''; connected.value = false
  const version = revision
  try { await testModel(config()); if (version === revision) connected.value = true }
  catch (e) { if (version === revision) error.value = e instanceof Error ? e.message : '连接测试失败' }
  finally { busy.value = ''; await refreshUsage() }
}
async function generate() {
  if (!validConnection()) return
  if (form.query.trim().length < 5) { ElMessage.warning('请填写至少5字的旅行需求'); return }
  if (!form.days || !form.budget || !form.peopleNum) { ElMessage.warning('请填写天数、预算和人数'); return }
  if (!form.startDate || form.startDate < today()) { ElMessage.warning('出发日期须为今天及以后'); return }
  invalidate(); busy.value = 'generate'
  const version = revision
  try {
    const data = await generatePlan({ connection: config(), query: form.query.trim(), days: form.days, budget: form.budget, peopleNum: form.peopleNum, startDate: form.startDate })
    if (version !== revision) return
    preview.value = data; title.value = data.draft.title
  } catch (e) { if (version === revision) error.value = e instanceof Error ? e.message : '生成失败，请重试' }
  finally { busy.value = ''; await refreshUsage() }
}
async function save() {
  if (!preview.value || !acknowledged.value) return
  if (!title.value.trim()) { ElMessage.warning('请填写规划标题'); return }
  busy.value = 'save'
  try {
    const id = await createPlanApi({ title: title.value.trim(), destinationIds: [...form.destinationIds], startDate: form.startDate,
      days: form.days, budget: form.budget, peopleNum: form.peopleNum, status: 0,
      dayList: JSON.parse(JSON.stringify(preview.value.draft.dayList)) as PlanDayDTO[] })
    ElMessage.success('已保存为草稿，可继续编辑')
    await router.push(`/plan/${id}/edit`)
  } catch { /* Shared interceptor displays save errors; retain preview for retry. */ }
  finally { busy.value = '' }
}
onMounted(async () => {
  loadSettings()
  void refreshUsage()
  try { const [policy, list] = await Promise.all([plannerOptions(), destinationListApi()]); options.value = policy; destinations.value = list }
  catch { error.value = '无法加载服务配置，请刷新重试' }
})
</script>

<template>
  <main class="ai-planner">
    <h1>让 AI 帮你规划旅程</h1>
    <p class="hint">连接自己的模型服务，生成行程预览，确认后保存并继续编辑。</p>
    <section class="panel quota-panel" data-testid="ai-usage" aria-live="polite">
      <div class="usage-heading"><h2>我的AI额度</h2><el-button :loading="refreshingUsage" :disabled="!!busy" @click="refreshUsage">刷新额度</el-button></div>
      <template v-if="usage">
        <div class="fields">
          <p data-testid="hourly-remaining">本小时剩余 <strong>{{ usage.quota.hourly.remaining }}</strong> / {{ usage.quota.hourly.limit }} 次</p>
          <p data-testid="daily-remaining">今日剩余 <strong>{{ usage.quota.daily.remaining }}</strong> / {{ usage.quota.daily.limit }} 次</p>
        </div>
        <p class="hint">今日操作 {{ usage.today.operations }} 次：成功 {{ usage.today.succeeded }}，失败 {{ usage.today.failed }}；平均耗时 {{ Math.round(usage.today.averageCostMs) }} ms。</p>
        <p class="hint">北京时间：小时额度重置于 {{ resetTime(usage.quota.hourly.resetAt) }}，日额度重置于 {{ resetTime(usage.quota.daily.resetAt) }}。全站今日剩余 {{ usage.quota.globalDaily.remaining }} 次。</p>
        <el-alert v-if="exhausted" title="AI额度已用尽。到重置时间后点击刷新额度，可继续使用基础旅行推荐。" type="warning" :closable="false" />
      </template>
      <el-alert v-else-if="usageError" title="暂时无法读取额度，请刷新重试；模型调用仍由服务端校验额度。" type="warning" :closable="false" />
      <p class="hint">连接测试和每次模型生成尝试各用1次额度，结构重试另用1次；已发起的失败请求不退还。操作统计按一次测试或生成计数，可能与额度用量不同。基础旅行推荐不消耗模型额度。</p>
      <router-link to="/recommend">使用基础旅行推荐</router-link>
    </section>
    <el-form label-position="top" :disabled="!!busy">
      <section class="panel">
        <h2>1. 你的模型服务</h2>
        <el-form-item label="API基础地址（可包含端口）">
          <el-input v-model="connection.baseUrl" maxlength="500" aria-label="API基础地址" placeholder="https://api.deepseek.com/v1 或 http://localhost:11434/v1" />
        </el-form-item>
        <div class="fields">
          <el-form-item label="模型名称"><el-input v-model="connection.model" maxlength="50" aria-label="模型名称" placeholder="填写服务提供商给出的模型ID" /></el-form-item>
          <el-form-item label="API Key（本机无鉴权服务可留空）"><el-input v-model="connection.apiKey" type="password" show-password autocomplete="off" maxlength="512" aria-label="API Key" placeholder="只在当前页面使用，不会保存" /></el-form-item>
        </div>
        <p class="hint">地址填写到 /v1 等基础路径，不要填写 /chat/completions。当前协议为非流式 Chat Completions。</p>
        <p class="hint">可信公网主机：{{ options.allowedHosts.join('、') || '加载中' }}。其他服务请让部署者加入可信主机列表。</p>
        <p v-if="options.allowLoopback" class="hint">支持 localhost / 127.0.0.1 的1024以上端口。这里指运行后端的电脑；远程部署后不代表你的个人电脑。</p>
        <p class="hint">API Key只存在当前页面内存，离开或刷新需重新填写。需求和密钥将经后端转发到你指定的服务，连接测试与生成可能消耗该服务额度。</p>
        <div class="actions">
          <el-button :loading="busy === 'test'" :disabled="exhausted" @click="test">测试连接</el-button>
          <el-button @click="saveSettings">记住地址与模型</el-button>
          <el-button @click="clearSettings">清除设置与密钥</el-button>
        </div>
        <el-alert v-if="connected" title="连接成功，模型返回了有效响应" type="success" :closable="false" />
      </section>
      <section class="panel">
        <h2>2. 你的旅行需求</h2>
        <el-form-item label="告诉AI你想去哪里、喜欢什么、有哪些限制">
          <el-input v-model="form.query" type="textarea" :rows="4" maxlength="1000" show-word-limit aria-label="AI旅行需求" placeholder="例如：想去大理玩3天，喜欢美食和自然风光，带父母同行，每天不要太赶。" />
        </el-form-item>
        <div class="fields">
          <el-form-item label="天数（1—14）"><el-input-number v-model="form.days" :min="1" :max="14" :precision="0" aria-label="AI天数" /></el-form-item>
          <el-form-item label="总预算（元）"><el-input-number v-model="form.budget" :min="0.01" :max="1000000" :precision="2" aria-label="AI预算" /></el-form-item>
          <el-form-item label="同行人数"><el-input-number v-model="form.peopleNum" :min="1" :max="10" :precision="0" aria-label="AI人数" /></el-form-item>
          <el-form-item label="出发日期"><el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" aria-label="AI出发日期" /></el-form-item>
          <el-form-item label="保存时关联的目的地（可选，AI以需求文本为准）" class="wide">
            <el-select v-model="form.destinationIds" multiple aria-label="关联目的地"><el-option v-for="d in destinations" :key="d.id" :value="d.id" :label="d.name" /></el-select>
          </el-form-item>
        </div>
        <el-button type="primary" :loading="busy === 'generate'" :disabled="exhausted" @click="generate">生成AI行程</el-button>
        <p v-if="busy === 'generate'" class="hint" role="status">正在生成完整行程，结构不合格时会自动重试一次，请稍候。</p>
      </section>
    </el-form>
    <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
    <section v-if="preview" class="panel preview" data-testid="ai-preview">
      <h2>3. 检查AI生成的行程</h2>
      <p class="hint">由你配置的 {{ preview.model }} 生成。地点、交通和费用需自行核实；未提供费用的项目不是免费。保存后可逐项编辑。</p>
      <el-input v-model="title" maxlength="200" aria-label="AI规划标题" :disabled="!!busy" />
      <article v-for="(day, index) in preview.draft.dayList" :key="index" class="day">
        <h3>第{{ index + 1 }}天 · {{ day.title }}</h3><p>{{ day.summary }}</p>
        <ol><li v-for="(item, i) in day.items" :key="i">
          <strong>{{ item.timePoint }} {{ item.title }}</strong>
          <p v-if="item.activity">{{ item.activity }}</p>
          <p v-if="item.transport">交通：{{ item.transport }}</p>
          <p v-if="item.hotel">住宿：{{ item.hotel }}</p><p v-if="item.meal">餐饮：{{ item.meal }}</p>
          <p v-if="item.cost != null">估算费用：¥{{ item.cost }}</p><p v-if="item.tips">提示：{{ item.tips }}</p>
        </li></ol>
      </article>
      <el-checkbox v-model="acknowledged" :disabled="!!busy">我已查看行程，保存为可编辑草稿</el-checkbox>
      <div><el-button type="primary" :disabled="!acknowledged || !!busy" :loading="busy === 'save'" @click="save">保存到我的规划并编辑</el-button></div>
    </section>
  </main>
</template>

<style scoped>
.ai-planner { max-width: 960px; margin: auto; padding: 30px 20px 60px; }
h1 { font-size: 30px; } h2 { margin: 0 0 20px; font-size: 20px; }
.panel { background: white; border: 1px solid #e2e8f0; border-radius: 14px; padding: 24px; margin: 24px 0; }
.hint { color: #64748b; font-size: 14px; line-height: 1.7; }
.fields { display: grid; grid-template-columns: 1fr 1fr; gap: 0 20px; }.wide { grid-column: 1 / -1; }
.actions { display: flex; flex-wrap: wrap; gap: 10px; margin-bottom: 12px; }.actions .el-button { margin: 0; }
.usage-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; }.usage-heading h2 { margin: 0; }
.day { border-bottom: 1px solid #e2e8f0; padding: 16px 0; line-height: 1.7; }.day li { margin: 16px 0; }.day p { margin: 4px 0; }
.preview > div:last-child { margin-top: 14px; }
@media(max-width:640px) { .fields { grid-template-columns: 1fr; }.panel { padding: 18px; }.ai-planner { padding: 24px 12px; } }
</style>
