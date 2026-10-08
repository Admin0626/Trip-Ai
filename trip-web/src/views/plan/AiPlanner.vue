<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { today } from '@/utils/format'
import { createPlanApi } from '@/api/modules/plan'
import { destinationListApi } from '@/api/modules/destination'
import { plannerOptions, plannerUsage, plannerCircuit, testModel, generatePlan, generatePlanStream, cancelPlannerRequest, type PlannerCircuit, type PlannerPreview, type PlannerUsage } from '@/api/modules/planner'

const router = useRouter(), user = useUserStore()
const props=defineProps<{embedded?:boolean;sharedQuery?:string}>()
const emit=defineEmits<{'update:sharedQuery':[string];busy:[boolean]}>()
const connection = reactive({ baseUrl: '', model: '', apiKey: '' })
const form = reactive({ query: props.sharedQuery||'', days: 3, budget: 3000, peopleNum: 2, startDate: today(), destinationIds: [] as number[] })
const destinations = ref<DestinationVO[]>([])
const options = ref<{ allowedHosts: string[]; allowLoopback: boolean }>({ allowedHosts: [], allowLoopback: false })
const busy = ref('')
const error = ref('')
const connected = ref(false)
const connectionReply=ref('')
const preview = ref<PlannerPreview | null>(null)
const title = ref('')
const acknowledged = ref(false)
const showProgress=ref(true),stage=ref(''),attempt=ref(1),elapsed=ref(0),cancelling=ref(false),cancelled=ref(false)
let activeRequest='',streamController:AbortController|null=null,startedAt=0
const stageText=computed(()=>({CONNECTING:'正在连接你的模型服务',GENERATING:'模型正在生成完整行程',VALIDATING:'正在检查行程结构',RETRYING:'行程结构需要重新生成，正在准备重试'}[stage.value]||'正在启动生成'))
const usage = ref<PlannerUsage | null>(null)
const usageError = ref(false)
const refreshingUsage = ref(false)
const circuit = ref<PlannerCircuit | null>(null)
const circuitError = ref(false), refreshingCircuit = ref(false), clockTick = ref(0)
let circuitRevision = 0, circuitDeadline = 0, mounted = true
const ticker = window.setInterval(() => { clockTick.value = performance.now(); if(busy.value==='generate')elapsed.value=Math.floor((performance.now()-startedAt)/1000) }, 1000)
const circuitWait = computed(() => Math.max(0, Math.ceil((circuitDeadline - clockTick.value) / 1000)))
const circuitBlocked = computed(() => circuit.value?.phase === 'HALF_OPEN' || (circuit.value?.phase === 'OPEN' && circuitWait.value > 0))
const exhausted = computed(() => usage.value?.quota.hourly.remaining === 0)
const storageKey = computed(() => `trip_ai_connection_${user.userInfo?.id ?? 'none'}`)
let revision = 0
function invalidate() { revision++; preview.value = null; acknowledged.value = false; error.value = '' }
watch(connection, () => {
  invalidate(); connected.value = false;connectionReply.value=''; circuitRevision++; circuit.value = null
  circuitError.value = false; refreshingCircuit.value = false; circuitDeadline = 0
}, { deep: true, flush: 'sync' })
watch(form, invalidate, { deep: true, flush: 'sync' })
watch(()=>form.query,value=>emit('update:sharedQuery',value))
watch(()=>props.sharedQuery,value=>{if(value!==undefined&&value!==form.query)form.query=value})
watch(busy,value=>emit('busy',!!value),{flush:'sync'})
watch(showProgress,invalidate)
watch(storageKey, () => { connection.apiKey = ''; usage.value = null; loadSettings(); void refreshUsage() })
onBeforeUnmount(() => { mounted = false; revision++; circuitRevision++; streamController?.abort(); window.clearInterval(ticker); connection.apiKey = '';emit('busy',false) })

function loadSettings() {
  connection.baseUrl = 'https://api.deepseek.com'; connection.model = 'deepseek-flash'; connection.apiKey = ''
  try {
    const saved = JSON.parse(localStorage.getItem(storageKey.value) || '{}')
    connection.baseUrl = typeof saved.baseUrl === 'string' ? saved.baseUrl : 'https://api.deepseek.com'
    connection.model = typeof saved.model === 'string' ? saved.model : 'deepseek-flash'
  } catch { /* Corrupt browser settings are ignored. */ }
}
function deepseekDefaults(){connection.baseUrl='https://api.deepseek.com';connection.model='deepseek-flash'}
function validConnection(requireKey=true) {
  if (!connection.baseUrl.trim() || !connection.model.trim()) { ElMessage.warning('请填写API基础地址和模型名称'); return false }
  try {
    const url = new URL(connection.baseUrl.trim())
    if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.search || url.hash
      || url.pathname.replace(/\/+$/, '').endsWith('/chat/completions')) throw new Error()
    if(requireKey&&!['localhost','127.0.0.1'].includes(url.hostname)&&!connection.apiKey.trim().replace(/^Bearer\s+/i,'').trim()){ElMessage.warning('请填写模型服务的API Key');return false}
  } catch { ElMessage.warning('请填写不含账号、查询参数或/chat/completions的API基础地址'); return false }
  return true
}
function saveSettings() {
  if (!validConnection(false)) return
  try {
    localStorage.setItem(storageKey.value, JSON.stringify({ baseUrl: connection.baseUrl.trim(), model: connection.model.trim() }))
    ElMessage.success('已记住当前账号的地址和模型，API Key未保存')
  } catch { ElMessage.error('浏览器无法保存设置，可继续临时使用') }
}
function clearSettings() {
  try { localStorage.removeItem(storageKey.value) } catch { ElMessage.warning('浏览器无法移除设置，请手动清理站点数据') }
  connection.baseUrl = ''; connection.model = ''; connection.apiKey = ''
}
function config() { return { baseUrl: connection.baseUrl.trim(), model: connection.model.trim(), apiKey: connection.apiKey.trim().replace(/^Bearer\s+/i,'').trim() } }
async function refreshCircuit() {
  if (!mounted || !connection.baseUrl.trim() || !connection.model.trim()) return
  const version = ++circuitRevision
  refreshingCircuit.value = true
  try {
    const result = await plannerCircuit(config())
    if (mounted && version === circuitRevision) {
      circuit.value = result; circuitError.value = false
      clockTick.value = performance.now(); circuitDeadline = clockTick.value + result.retryAfterSeconds * 1000
    }
  } catch {
    if (mounted && version === circuitRevision) { circuit.value = null; circuitError.value = true }
  } finally { if (mounted && version === circuitRevision) refreshingCircuit.value = false }
}
async function refreshUsage() {
  if(!mounted)return
  refreshingUsage.value = true
  const account = storageKey.value
  try { const result = await plannerUsage(); if (account === storageKey.value) { usage.value = result; usageError.value = false } }
  catch { if (account === storageKey.value) { usage.value = null; usageError.value = true } }
  finally { refreshingUsage.value = false }
}
function resetTime(value: string) { return new Date(value).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false }) }
async function test() {
  if (!validConnection()) return
  busy.value = 'test'; error.value = ''; connected.value = false;connectionReply.value=''
  const version = revision
  try { const result=await testModel(config()); if (version === revision) {connected.value = true;connectionReply.value=result.reply} }
  catch (e) { if (version === revision) error.value = e instanceof Error ? e.message : '连接测试失败' }
  finally { await Promise.allSettled([refreshUsage(), refreshCircuit()]); busy.value = '' }
}
async function generate() {
  if (!validConnection()) return
  if (form.query.trim().length < 5) { ElMessage.warning('请填写至少5字的旅行需求'); return }
  if (!form.days || !form.budget || !form.peopleNum) { ElMessage.warning('请填写天数、预算和人数'); return }
  if (!form.startDate || form.startDate < today()) { ElMessage.warning('出发日期须为今天及以后'); return }
  invalidate(); busy.value = 'generate'
  cancelled.value=false;stage.value='';attempt.value=1;elapsed.value=0;startedAt=performance.now()
  const controller=new AbortController();streamController=controller
  const version = revision
  try {
    const requestId=crypto.randomUUID();activeRequest=requestId
    const input={ connection: config(), query: form.query.trim(), days: form.days, budget: form.budget, peopleNum: form.peopleNum, startDate: form.startDate }
    const data = showProgress.value?await generatePlanStream(input,requestId,controller.signal,(name,event)=>{
      if(!mounted||version!==revision)return
      if(name==='progress'){stage.value=event.data.stage as string;attempt.value=event.data.attempt as number}
    }):await generatePlan(input)
    if (!mounted || version !== revision) return
    if(!data){cancelled.value=true;return}
    preview.value = data; title.value = data.draft.title
  } catch (e) { if (mounted && version === revision) { if(controller.signal.aborted)cancelled.value=true;else error.value = e instanceof Error ? e.message : '生成失败，请重试' } }
  finally { activeRequest='';streamController=null;cancelling.value=false;await Promise.allSettled([refreshUsage(), refreshCircuit()]); busy.value = '' }
}
async function cancelGeneration(){
  if(!activeRequest||cancelling.value)return
  cancelling.value=true
  try { await cancelPlannerRequest(activeRequest) }
  catch(e) {
    cancelling.value=false
    // A committed result is still deliverable. Other failures stop the local stream;
    // the server detects the closed connection on its next write/heartbeat.
    if((e as {code?:number}).code!==409)streamController?.abort()
  }
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
  <div class="ai-planner" :class="{embedded}">
    <h1 v-if="!embedded">让 AI 帮你规划旅程</h1>
    <p class="hint">连接自己的模型服务，生成行程预览，确认后保存并继续编辑。</p>
    <section class="panel quota-panel" data-testid="ai-usage" aria-live="polite">
      <div class="usage-heading"><h2>调用状态</h2><el-button :loading="refreshingUsage" :disabled="!!busy" @click="refreshUsage">刷新状态</el-button></div>
      <template v-if="usage">
        <div class="fields">
          <p data-testid="hourly-remaining">本小时可调用 <strong>{{ usage.quota.hourly.remaining }}</strong> / {{ usage.quota.hourly.limit }} 次</p>
        </div>
        <p class="hint">今日操作 {{ usage.today.operations }} 次：成功 {{ usage.today.succeeded }}，失败 {{ usage.today.failed }}；平均耗时 {{ Math.round(usage.today.averageCostMs) }} ms。</p>
        <p class="hint">每小时调用窗口重置于 {{ resetTime(usage.quota.hourly.resetAt) }}（北京时间）。</p>
        <el-alert v-if="exhausted" title="本小时调用次数已达上限。窗口重置后刷新状态即可继续，也可使用基础旅行推荐。" type="warning" :closable="false" />
      </template>
      <el-alert v-else-if="usageError" title="暂时无法读取调用状态，请刷新重试；模型调用仍由服务端进行限流校验。" type="warning" :closable="false" />
      <p class="hint">使用你提供的模型服务，Trip-AI不设置个人或全站每日调用上限；模型余额和计费由服务商管理。每小时限流用于保护转发服务，连接测试、每次生成尝试和结构重试各计1次，已发起的失败请求也计入次数。</p>
      <router-link to="/recommend">使用基础旅行推荐</router-link>
    </section>
    <el-form class="planner-columns" label-position="top" :disabled="!!busy">
      <section class="panel">
        <h2>1. 连接你的模型</h2>
        <p class="hint">填写API地址、模型名称与API Key即可使用自己的模型。DeepSeek已预填地址与模型，密钥由你自行填写。</p>
        <el-button :disabled="!!busy" @click="deepseekDefaults">填入DeepSeek地址与模型</el-button>
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
          <el-button :loading="busy === 'test'" :disabled="exhausted || circuitBlocked || refreshingCircuit" @click="test">测试连接</el-button>
          <el-button @click="saveSettings">记住地址与模型</el-button>
          <el-button @click="clearSettings">清除设置与密钥</el-button>
          <el-button :loading="refreshingCircuit" :disabled="!connection.baseUrl.trim() || !connection.model.trim()" @click="refreshCircuit">刷新模型状态</el-button>
        </div>
        <div data-testid="ai-circuit" aria-live="polite">
          <template v-if="circuit">
            <el-alert v-if="circuit.phase === 'OPEN'" :title="circuitWait > 0 ? `模型服务因故障暂停，约${circuitWait}秒后可尝试恢复。` : '等待已结束，可测试连接或生成行程进行一次恢复探测。'" type="warning" :closable="false" />
            <el-alert v-else-if="circuit.phase === 'HALF_OPEN'" title="模型服务正在恢复探测，请稍后刷新状态。" type="warning" :closable="false" />
            <p v-else class="hint">当前模型可尝试调用；连接测试成功不代表行程质量已验证。</p>
            <p class="hint">最近{{ circuit.windowSeconds }}秒实际调用{{ circuit.total }}次，失败{{ circuit.failed }}次。等待或恢复探测期间可继续使用基础旅行推荐。</p>
            <router-link v-if="circuit.phase !== 'CLOSED'" to="/recommend">改用基础旅行推荐</router-link>
          </template>
          <p v-else-if="circuitError" class="hint" role="status">暂时无法读取模型状态，请刷新重试；调用仍由服务端检查。</p>
          <p v-else class="hint">点击刷新可查看当前连接的故障状态，不调用模型、不消耗AI额度。</p>
        </div>
        <el-alert v-if="connected" title="连接成功，模型返回了有效响应" type="success" :closable="false" />
        <p v-if="connected" class="model-answer" data-testid="model-test-reply">模型回复：{{connectionReply}}</p>
      </section>
      <section class="panel">
        <h2>2. 你的旅行需求</h2>
        <el-checkbox v-model="showProgress" data-testid="planner-progress-mode">展示生成进度（支持取消）</el-checkbox>
        <p class="hint">关闭后使用普通生成。显示实际处理阶段，完整行程检查通过后再预览；切换方式后需要自行重新生成。</p>
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
        <el-button type="primary" :loading="busy === 'generate'" :disabled="exhausted || circuitBlocked || refreshingCircuit" @click="generate">生成AI行程</el-button>
        <p v-if="busy === 'generate' && !showProgress" class="hint" role="status">正在生成完整行程，结构不合格时会自动重试一次，请稍候。</p>
      </section>
    </el-form>
    <section v-if="busy === 'generate' && showProgress" class="panel" data-testid="planner-progress" aria-live="polite">
      <h2><span class="trip-progress-dot" aria-hidden="true" />{{ stageText }}</h2><p>第{{ attempt }}次尝试 · 已等待{{ elapsed }}秒</p>
      <p class="hint">取消会停止后台等待；已准入的调用仍计入本小时次数，模型服务可能已经计费。离开页面也会停止等待。</p>
      <el-button type="warning" :loading="cancelling" @click="cancelGeneration">取消生成</el-button>
    </section>
    <el-alert v-if="cancelled" title="已停止本次生成等待，未保存行程。已准入的调用仍计入本小时次数。" type="info" :closable="false" data-testid="planner-cancelled" />
    <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
    <section v-if="preview" v-reveal class="panel preview" data-testid="ai-preview">
      <h2>3. 模型回答与每日行程</h2>
      <p class="hint">由你配置的 {{ preview.model }} 生成。地点、交通和费用需自行核实；未提供费用的项目不是免费。保存后可逐项编辑。</p>
      <div v-if="preview.draft.answer" class="model-answer" data-testid="model-answer">{{preview.draft.answer}}</div>
      <p v-else class="hint">模型未单独提供文字建议，以下为它生成的每日行程。</p>
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
  </div>
</template>

<style scoped>
.ai-planner { max-width: 1280px; margin: auto; padding: 30px 20px 60px; }
.ai-planner.embedded{max-width:none;padding:0}.model-answer{white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.8;background:var(--trip-tint);border-radius:12px;padding:16px;margin:16px 0}
h1 { font-size: 30px; } h2 { margin: 0 0 20px; font-size: 20px; }
.panel { background: white; border: 1px solid var(--trip-border); border-radius: 20px; padding: 24px; margin: 24px 0; min-width: 0; overflow-wrap: anywhere; }
.hint { color: var(--trip-muted); font-size: 14px; line-height: 1.7; }
.planner-columns { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 24px; align-items: start; margin-top: 24px; }
.planner-columns > .panel { margin: 0; }
.planner-columns .fields { grid-template-columns: 1fr; }
.planner-columns :deep(.el-input-number), .planner-columns :deep(.el-date-editor) { width: 100%; }
.planner-columns :deep(.el-form-item__label) { height: auto; white-space: normal; }
.planner-columns :deep(.el-form-item) { margin-top: 16px; }
.fields { display: grid; grid-template-columns: 1fr 1fr; gap: 0 20px; }.wide { grid-column: 1 / -1; }
.actions { display: flex; flex-wrap: wrap; gap: 10px; margin-bottom: 12px; }.actions .el-button { margin: 0; }
.usage-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; }.usage-heading h2 { margin: 0; }
.day { border-bottom: 1px solid var(--trip-border); padding: 16px 0; line-height: 1.7; }.day li { margin: 16px 0; }.day p { margin: 4px 0; }
.preview > div:last-child { margin-top: 14px; }
@media(max-width:767px) { .fields,.planner-columns { grid-template-columns: 1fr; }.panel { padding: 24px; }.ai-planner { padding: 24px 20px; }.usage-heading { flex-wrap: wrap; } }
</style>
