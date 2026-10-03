<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Connection, Collection, SetUp, Switch, Monitor, DataLine, ArrowRight, ArrowLeft, Download, Printer, Check } from '@element-plus/icons-vue'
import ConnectionDiagram from './ConnectionDiagram.vue'
import { lessons, overviewNodes, overviewEdges, springNodes, springEdges, springSteps, frontendNodes, frontendEdges, dataNodes, dataEdges, scenarios, modules, quizzes, type ScenarioKey } from './content'

const route = useRoute(), router = useRouter()
const icons = [Connection, SetUp, Switch, Monitor, Collection, DataLine]
const current = computed(() => lessons.find(l => l.id === route.query.view) || lessons[0]!)
const selected = ref('service')
const graphNodes = computed(() => current.value.id === 'spring' ? springNodes : current.value.id === 'frontend' ? frontendNodes : current.value.id === 'data' ? dataNodes : overviewNodes)
const graphEdges = computed(() => current.value.id === 'spring' ? springEdges : current.value.id === 'frontend' ? frontendEdges : current.value.id === 'data' ? dataEdges : overviewEdges)
const node = computed(() => graphNodes.value.find(n => n.id === selected.value) || graphNodes.value[0]!)
function navigate(id: string) { void router.replace({ path: '/workbench', query: { view: id } }) }
watch(() => current.value.id, () => { selected.value = graphNodes.value[0]!.id; questionIndex.value = 0; choice.value = null; tourOpen.value = false })
const springStep = ref(0)
const springLesson = computed(() => springSteps[springStep.value]!)
const assemblyMode = ref('normal')
const assemblyResult = computed(() => assemblyMode.value === 'missing'
  ? { ok: false, title: '依赖缺失：找不到 RouteService Bean', text: '移除唯一实现类的 @Service 后，如果没有其他注册方式，Controller 的构造器依赖无法满足。检查扫描范围、注解和配置。' }
  : assemblyMode.value === 'ambiguous'
    ? { ok: false, title: '候选冲突：发现两个 RouteService Bean', text: '当两个实现均已注册且没有名称等消歧线索时，按类型无法确定候选。可用 @Primary 指定默认实现，或用 @Qualifier 明确选择。当前项目路线模块没有这个冲突。' }
    : { ok: true, title: '装配成功：Controller 获得 Service 依赖', text: '容器已有 RouteServiceImpl 和各 Mapper 代理，构造器依赖能够被解析。这是概念模拟；未启动或修改后端容器。' })
const scenarioKey = ref<ScenarioKey>('route'), stepIndex = ref(0)
const scenario = computed(() => scenarios[scenarioKey.value])
const activeStep = computed(() => scenario.value.steps[stepIndex.value]!)
watch(scenarioKey, () => { stepIndex.value = 0 })
const localState = ref('success')
const frontendStates: Record<string, { title: string; body: string }> = {
  loading: { title: '正在查询路线…', body: 'loading = true；请求尚未返回，页面显示加载状态。' },
  success: { title: '杭州轻松一日游', body: '教学样例 · 1 天 · 美食 / 城市漫步。records 更新后，模板显示结果。' },
  empty: { title: '没有符合条件的路线', body: 'records = []；提示用户调整筛选条件。' },
  error: { title: '暂时无法获取路线', body: '请求失败；展示可理解的错误信息，保留重新查询入口。' },
}
const endpoint = ref('route'), keyword = ref('杭州'), pageSize = ref('10'), identity = ref('guest'), invalidInput = ref(false)
const labResult = ref<{ code: number; message: string; data: unknown; timestamp: number } | null>(null)
const labExplanation = ref('')
const endpoints: Record<string, { method: string; path: string; permission: string; file: string }> = {
  route: { method: 'GET', path: '/api/route/page', permission: '公开读取', file: 'module/route/controller/RouteController.java' },
  plan: { method: 'POST', path: '/api/plan', permission: '登录用户', file: 'module/plan/controller/PlanController.java' },
  admin: { method: 'GET', path: '/api/admin/user/page', permission: '管理员', file: 'module/user/controller/AdminUserController.java' },
}
const api = computed(() => endpoints[endpoint.value]!)
const requestPreview = computed(() => endpoint.value === 'route'
  ? `${api.value.method} ${api.value.path}?current=1&size=${encodeURIComponent(pageSize.value)}&keyword=${encodeURIComponent(keyword.value)}\nAccept: application/json${identity.value !== 'guest' ? '\nAuthorization: Bearer <教学占位 Token>' : ''}`
  : `${api.value.method} ${api.value.path}\n${identity.value !== 'guest' ? 'Authorization: Bearer <教学占位 Token>\n' : ''}${endpoint.value === 'plan' ? 'Content-Type: application/json\n\n' + JSON.stringify({ title: invalidInput.value ? '' : '杭州一日游', destinationIds: [1], startDate: '2026-10-10', days: 1, budget: 800, peopleNum: 1, status: 0, dayList: [] }, null, 2) : 'Accept: application/json'}`)
watch([endpoint, keyword, pageSize, identity, invalidInput], () => { labResult.value = null; labExplanation.value = '' })
function runLab() {
  let code = 200, message = 'success', data: unknown = null
  if (endpoint.value !== 'route' && identity.value === 'guest') {
    code = 401; message = '请先登录'; labExplanation.value = '认证先于业务处理。受保护接口没有有效身份时，不进入 Controller 的业务逻辑。实际系统也可能在响应中触发 Token 刷新流程。'
  } else if (endpoint.value === 'admin' && identity.value !== 'admin') {
    code = 403; message = '无权限操作'; labExplanation.value = '已经登录，但没有 ADMIN 角色。前端是否显示菜单不会改变后端权限检查。'
  } else if (endpoint.value === 'plan' && invalidInput.value) {
    code = 400; message = '规划标题不能为空'; labExplanation.value = '@Valid 与 DTO 的 @NotBlank 检查请求体，缺少标题时不会写入规划。'
  } else if (endpoint.value === 'route') {
    const size = Number(pageSize.value)
    if (!Number.isInteger(size) || size < 1) {
      code = 400; message = '教学参数要求 size 为正整数'; labExplanation.value = '这是实验器自身的输入约束；当前 RouteController 仅显式限制 size 上限为 100，不代表真实接口覆盖了这里的所有检查。'
    } else {
      const records = keyword.value.trim() && !'杭州轻松一日游'.includes(keyword.value.trim()) ? [] : [{ id: 1, title: '杭州轻松一日游', destinationName: '杭州', days: 1, price: 800, tags: ['美食', '城市漫步'] }]
      data = { records, total: records.length, current: 1, size: Math.min(size, 100), pages: records.length ? 1 : 0 }
      labExplanation.value = '样例数据经 R<PageResult<RoutePageVO>> 包装返回。size 上限取 100；前端读取 response.data.data。这里不访问实际目录或数据库。'
    }
  } else {
    data = endpoint.value === 'plan' ? 1001 : { records: [], total: 0, current: 1, size: 20, pages: 0 }
    labExplanation.value = endpoint.value === 'plan' ? '通过认证和校验后，Service 保存规划并返回 ID。1001 是教学 ID，未写入真实数据库。' : '管理员通过角色检查，可读取用户管理接口。这里返回空的教学分页数据。'
  }
  labResult.value = { code, message, data, timestamp: Date.now() }
}
const rollback = ref(false)
const questionIndex = ref(0), choice = ref<number | null>(null)
const question = computed(() => quizzes[questionIndex.value]!)
const tourOpen = ref(false)
function exportGuide() {
  const text = ['# Trip-AI 项目学习导览', '', '依据当前仓库代码整理，教学内容快照：2026-10-03。', '', '## 一句话架构', 'Vue 页面 → Axios / HTTP → Spring Security → Controller → Service → Mapper / JDBC → MySQL；Redis 管理会话、配额与熔断。', '', '## Spring 装配', ...springSteps.flatMap(s => [`### ${s.title}`, s.text, `代码出处：${s.file}`, '']), '## 业务请求', ...Object.values(scenarios).flatMap(s => [`### ${s.label}`, `${s.method} ${s.url}`, s.note, ...s.steps.map((step, i) => `${i + 1}. ${step[0]}：${step[1]}（${step[2]}）`), '']), '## 模块与边界', ...modules.map(m => `- ${m[0]}（${m[2]}）：${m[1]}`), '', '## 阅读依据', 'README.md、docs/01-总体技术方案.md、docs/02-接口文档.md、docs/03-前端设计.md、docs/05-数据库设计.md、docs/08-业务流程与业务规则.md', '', 'Spring 参考：https://docs.spring.io/spring-boot/4.0/reference/using/spring-beans-and-dependency-injection.html', '', '工作台中的接口、装配和事务实验是本地教学模拟，不能作为运行中的服务健康或验收结果。'].join('\n')
  const url = URL.createObjectURL(new Blob([text], { type: 'text/markdown;charset=utf-8' }))
  const anchor = document.createElement('a'); anchor.href = url; anchor.download = 'Trip-AI-项目学习导览.md'; anchor.click(); URL.revokeObjectURL(url)
}
function printGuide() { window.print() }
</script>

<template>
  <div class="workbench">
    <header class="wb-header">
      <router-link class="brand" to="/"><span class="brand-mark"><el-icon><Connection /></el-icon></span><strong>Trip-AI</strong><span class="brand-divider">/</span><span>项目学习工作台</span></router-link>
      <div class="header-actions"><span class="snapshot">代码导览 · 2026.10.03</span><router-link to="/">返回项目 <el-icon><ArrowRight /></el-icon></router-link></div>
    </header>
    <div class="wb-layout">
      <aside class="sidebar">
        <div class="sidebar-label">理解一个完整的系统</div>
        <nav aria-label="学习视角">
          <button v-for="(lesson, i) in lessons" :key="lesson.id" type="button" :class="{ active: current.id === lesson.id }" :aria-current="current.id === lesson.id ? 'page' : undefined" @click="navigate(lesson.id)">
            <el-icon><component :is="icons[i]" /></el-icon><span><strong>{{ lesson.title }}</strong><small>{{ lesson.subtitle }}</small></span><span class="nav-number">0{{ i + 1 }}</span>
          </button>
        </nav>
        <div class="sidebar-note"><span class="note-line"></span><strong>从全局，到一行代码。</strong><p>用真实业务理解框架，用连接关系理解系统。</p><button type="button" class="text-button" @click="tourOpen = !tourOpen">{{ tourOpen ? '收起导览' : '开始学习导览' }} <el-icon><ArrowRight /></el-icon></button></div>
        <div class="sidebar-footer">Java 17 / Spring Boot 4.0.8<br>Vue 3 / TypeScript / MySQL / Redis</div>
      </aside>

      <main class="wb-main">
        <div class="breadcrumb">Trip-AI <span>/</span> 架构与基础知识 <span>/</span> {{ current.title }}</div>
        <section class="page-intro">
          <div><div class="eyebrow">PROJECT EXPLORER <span>0{{ lessons.indexOf(current) + 1 }} / 06</span></div><h1>{{ current.title }}<span>看清系统的连接方式</span></h1><p>{{ current.question }} 从实际项目出发，把概念、代码和业务连起来。</p></div>
          <div class="intro-actions"><button type="button" class="secondary-button" @click="exportGuide"><el-icon><Download /></el-icon>导出导览</button><button type="button" class="icon-button" aria-label="打印当前学习视角" @click="printGuide"><el-icon><Printer /></el-icon></button></div>
        </section>

        <section v-if="tourOpen" class="learning-tour" aria-label="学习导览">
          <strong>建议学习顺序</strong><p>先在总览点击 Service，再看 Spring 如何把它注入 Controller；跟随“浏览路线”请求，最后修改接口参数，观察响应与页面状态。</p><div><button v-for="lesson in lessons" :key="lesson.id" type="button" @click="navigate(lesson.id)">{{ lesson.title }} <el-icon><ArrowRight /></el-icon></button></div>
        </section>

        <template v-if="current.id === 'overview'">
          <div class="overview-summary"><div><span>系统目标</span><strong>把旅行需求变成可编辑的行程</strong></div><div><span>后端形态</span><strong>单个应用 · 按业务模块分层</strong></div><div><span>核心原则</span><strong>先校验，再执行；先预览，再保存</strong></div></div>
        </template>

        <template v-if="['overview', 'spring', 'frontend', 'data'].includes(current.id)">
          <section class="graph-section">
            <div class="section-heading"><div><h2>{{ current.id === 'overview' ? '一张图，读懂 Trip-AI' : current.id === 'spring' ? '容器把对象装配起来' : current.id === 'frontend' ? '从一个 URL，到一次页面更新' : '业务数据如何关联' }}</h2><p>{{ current.id === 'spring' ? '箭头表示依赖的准备与注入，这是概念顺序，并非所有 Bean 的固定创建顺序。' : current.id === 'data' ? '箭头表示主要逻辑关系；1 : N 表示一对多，不代表数据库已声明外键约束。' : '点击任一节点，查看它的职责、相关知识和代码出处。' }}</p></div><span class="graph-legend"><i></i>{{ current.id === 'spring' ? '依赖装配' : current.id === 'data' ? '数据关联' : '调用 / 数据流' }}</span></div>
            <div class="graph-body"><div class="graph-area"><ConnectionDiagram :nodes="graphNodes" :edges="graphEdges" :selected="node.id" :label="current.title + '连接图'" @select="selected = $event" /><div class="diagram-caption">{{ current.id === 'overview' ? '主线：页面 → 接口 → 业务 → 数据。AI 规划与本地检索是不同的业务分支。' : current.id === 'spring' ? 'IoC 管理对象；DI 连接依赖。接口声明能力，实现类提供能力。' : current.id === 'frontend' ? 'API 返回数据后，由组件响应式状态驱动视图更新。' : '目录路线与私人规划分开存储；这里只展示主要业务关系。' }}</div></div>
              <aside class="inspector" aria-live="polite"><span class="inspector-label">节点解读 <span>{{ node.tag }}</span></span><h3>{{ node.title }}</h3><p>{{ node.description }}</p><ul><li v-for="fact in node.facts" :key="fact">{{ fact }}</li></ul><div class="source-label">代码出处</div><code class="file-path">{{ node.source }}</code><details v-if="node.code"><summary>查看代码摘录 / 缩略示例</summary><pre><code>{{ node.code }}</code></pre></details></aside>
            </div>
          </section>
        </template>

        <template v-if="current.id === 'overview'">
          <section class="module-section"><div class="section-heading"><div><h2>业务版图与交付边界</h2><p>设计文档描述目标，当前代码决定可展示的能力。已实现不等于生产环境已配置。</p></div><span class="subtle-label">依据 README 与当前实现</span></div><div class="module-grid"><article v-for="(module, i) in modules" :key="module[0]"><span class="module-index">0{{ i + 1 }}</span><div><h3>{{ module[0] }}</h3><p>{{ module[1] }}</p></div><span class="status" :class="{ planned: module[2] !== '已实现' }">{{ module[2] }}</span></article></div><div class="boundary-note">阶段 SSE 已实现；模型逐 token 输出仍待开发。邮箱流程已实现；真实 SMTP 尚未配置。Jev 未接入当前代码。文件上传使用本地存储，生产云存储待实现。</div></section>
        </template>

        <template v-if="current.id === 'spring'">
          <section class="spring-walkthrough"><div class="section-heading"><div><h2>五步理解装配</h2><p>把注解与真实项目里的对象对应起来。</p></div><span class="subtle-label">{{ springStep + 1 }} / 5</span></div><div class="step-tabs"><button v-for="(s, i) in springSteps" :key="s.title" type="button" :class="{ active: springStep === i }" :aria-pressed="springStep === i" @click="springStep = i"><span>0{{ i + 1 }}</span>{{ s.title }}</button></div><div class="lesson-code"><div><span class="pill">{{ springLesson.annotation }}</span><h3>{{ springLesson.brief }}</h3><p>{{ springLesson.text }}</p><code class="file-path">{{ springLesson.file }}</code><a href="https://docs.spring.io/spring-boot/4.0/reference/using/spring-beans-and-dependency-injection.html" target="_blank" rel="noreferrer">阅读 Spring 官方说明 ↗</a></div><pre><code>{{ springLesson.code }}</code></pre></div></section>
          <section class="experiment"><div><span class="eyebrow">TRY IT / 概念模拟</span><h2>如果依赖装配失败呢？</h2><p>改变一个条件，观察 Controller 是否能获得 Service。</p><label for="assembly-mode">Bean 注册情况</label><select id="assembly-mode" v-model="assemblyMode"><option value="normal">一个 RouteService 实现 Bean</option><option value="missing">移除唯一实现类的 @Service</option><option value="ambiguous">注册两个实现且无消歧线索</option></select></div><div class="experiment-result" :class="{ failure: !assemblyResult.ok }" aria-live="polite"><strong>{{ assemblyResult.ok ? '✓' : '!' }} {{ assemblyResult.title }}</strong><p>{{ assemblyResult.text }}</p></div></section>
        </template>

        <template v-if="current.id === 'request'">
          <section class="request-section"><div class="section-heading"><div><h2>跟随一次请求</h2><p>逐步查看各层职责，以及数据在哪一步发生变化。</p></div><span class="pill">教学回放</span></div><div class="scenario-tabs"><button v-for="(s, key) in scenarios" :key="key" type="button" :class="{ active: scenarioKey === key }" :aria-pressed="scenarioKey === key" @click="scenarioKey = key">{{ s.label }}</button></div><div class="endpoint-bar"><b>{{ scenario.method }}</b><code>{{ scenario.url }}</code></div><p class="scenario-note">{{ scenario.note }}</p><div class="sequence"><div class="sequence-head"><span>前端</span><span>接入与接口</span><span>业务与数据</span><span>返回与展示</span></div><button v-for="(step, i) in scenario.steps" :key="step[0]" type="button" class="sequence-row" :class="{ active: stepIndex === i, done: stepIndex > i }" :aria-pressed="stepIndex === i" @click="stepIndex = i"><span class="step-number">{{ stepIndex > i ? '✓' : '0' + (i + 1) }}</span><span class="sequence-track" :style="{ '--start': i === 0 ? 0 : i === 1 || i === 2 ? 1 : i === 5 ? 2 : 2, '--span': i === 0 ? 1 : i === 5 ? 1 : 1 }"><strong>{{ step[0] }}</strong><el-icon><ArrowRight /></el-icon></span></button></div><div class="active-step" aria-live="polite"><div><span class="eyebrow">STEP 0{{ stepIndex + 1 }}</span><h3>{{ activeStep[0] }}</h3><p>{{ activeStep[1] }}</p><code class="file-path">{{ activeStep[2] }}</code></div><div class="step-controls"><button type="button" class="secondary-button" :disabled="stepIndex === 0" @click="stepIndex--"><el-icon><ArrowLeft /></el-icon>上一步</button><button type="button" class="primary-button" @click="stepIndex = stepIndex === 5 ? 0 : stepIndex + 1">{{ stepIndex === 5 ? '重新回放' : '下一步' }}<el-icon><ArrowRight /></el-icon></button></div></div></section>
          <div class="two-notes"><article><h3>身份检查在哪里？</h3><p>路由守卫负责页面体验；后端过滤器与业务校验负责真正的访问控制。只隐藏按钮无法阻止直接请求接口。</p></article><article><h3>装配与调用有什么区别？</h3><p>装配连接对象依赖；调用执行业务方法。HTTP 数据不会变成一个 Java Service 对象，JSON 只是请求的输入。</p></article></div>
        </template>

        <template v-if="current.id === 'frontend'">
          <section class="experiment"><div><span class="eyebrow">REACTIVE UI / 教学模拟</span><h2>同一个页面，四种状态</h2><p>组件根据请求结果更新状态，模板显示对应内容。</p><label for="view-state">切换页面状态</label><select id="view-state" v-model="localState"><option value="loading">加载中 loading</option><option value="success">成功 success</option><option value="empty">无结果 empty</option><option value="error">请求失败 error</option></select></div><div class="state-preview" aria-live="polite"><span>路线列表预览</span><div class="state-indicator" :class="localState"></div><h3>{{ frontendStates[localState]?.title }}</h3><p>{{ frontendStates[localState]?.body }}</p></div></section>
          <div class="two-notes"><article><h3>ref 与 Pinia</h3><p>筛选、列表、加载状态属于页面本地数据，通常用 ref。跨页面的当前用户信息由 Pinia store 共享。</p></article><article><h3>TypeScript 与运行时校验</h3><p>TS 帮助开发时发现字段和类型错误。真实请求进入后端后，仍需 DTO 校验、权限检查和业务规则校验。</p></article></div>
        </template>

        <template v-if="current.id === 'api'">
          <section class="api-section"><div class="section-heading"><div><h2>接口是一份数据约定</h2><p>选择接口、身份和参数，观察教学请求与响应。</p></div><span class="pill">本地模拟 · 不发送请求</span></div><div class="api-lab"><div class="lab-form"><label for="endpoint">接口场景</label><select id="endpoint" v-model="endpoint"><option value="route">查询路线 · GET</option><option value="plan">保存规划 · POST</option><option value="admin">管理员用户列表 · GET</option></select><label for="identity">请求身份</label><select id="identity" v-model="identity"><option value="guest">游客 / 无 Token</option><option value="user">已登录普通用户</option><option value="admin">已登录管理员</option></select><template v-if="endpoint === 'route'"><label for="keyword">keyword 关键词</label><input id="keyword" v-model="keyword" placeholder="杭州"><label for="page-size">size 每页条数</label><input id="page-size" v-model="pageSize" type="number" min="1" step="1"></template><label v-if="endpoint === 'plan'" class="checkbox-label"><input v-model="invalidInput" type="checkbox">模拟缺少标题</label><p class="permission">权限要求：{{ api.permission }}</p><button type="button" class="primary-button" @click="runLab">运行教学请求 <el-icon><ArrowRight /></el-icon></button><small>只演示主要规则，不复刻完整服务端校验和异常处理。</small></div><div class="lab-code"><div class="code-title">请求 <span>{{ endpoint === 'route' ? 'Query 参数' : endpoint === 'plan' ? 'JSON 请求体' : '角色鉴权' }}</span></div><pre><code>{{ requestPreview }}</code></pre><div class="code-title">响应 <span>{{ labResult ? '业务 code = ' + labResult.code : '等待运行' }}</span></div><pre class="response-code" aria-live="polite"><code>{{ labResult ? JSON.stringify(labResult, null, 2) : '// 点击“运行教学请求”查看响应\n// 此处的结果均为教学样例' }}</code></pre></div></div><p v-if="labExplanation" class="lab-explanation" aria-live="polite">{{ labExplanation }}</p><div class="boundary-note">真实接口出处：{{ 'trip-server/src/main/java/com/trip/' + api.file }}</div></section>
          <div class="contract-grid"><article><span>01 / 输入</span><h3>Path · Query · Body</h3><p>/route/{id} 中的 id 是路径参数；?size=10 是查询参数；保存规划发送 JSON 请求体。</p></article><article><span>02 / 数据对象</span><h3>DTO · Entity · VO</h3><p>DTO 接输入，Entity 映射数据库表，VO 定义对外结果。公开返回值应避免直接暴露内部实体。</p></article><article><span>03 / 输出</span><h3>HTTP 状态与业务 code</h3><p>普通接口用 code / message / data / timestamp。请求层同时检查 HTTP 状态与业务 code；SSE 有独立事件契约。</p></article></div>
        </template>

        <template v-if="current.id === 'data'">
          <section class="transaction-section"><div class="section-heading"><div><h2>一次保存，需要一个完整结果</h2><p>PlanServiceImpl 的 public 保存入口使用 @Transactional。</p></div><label class="checkbox-label"><input v-model="rollback" type="checkbox">模拟明细写入失败</label></div><div class="transaction-flow"><div><span>01</span><strong>写主规划</strong><code>user_plan</code></div><i>→</i><div><span>02</span><strong>写每日行程</strong><code>user_plan_day</code></div><i>→</i><div :class="{ failed: rollback }"><span>03</span><strong>{{ rollback ? '明细写入失败' : '写行程明细' }}</strong><code>user_plan_item</code></div><i>→</i><div class="transaction-outcome" :class="{ failed: rollback }"><span>{{ rollback ? 'ROLLBACK' : 'COMMIT' }}</span><strong>{{ rollback ? '全部回滚' : '整体提交' }}</strong><code>{{ rollback ? '不留下半个规划' : '完整保存规划' }}</code></div></div><div class="boundary-note" aria-live="polite">{{ rollback ? '模拟运行时异常向事务边界传播：主规划和每日行程也回滚。Spring 默认对 RuntimeException / Error 回滚；受检异常需配置相应规则。' : '模拟全部写入成功：事务提交。事务通常在经 Spring 代理调用的入口生效，类内直接自调用不会自动触发一个新的代理事务。' }}</div></section>
          <div class="two-notes"><article><h3>MySQL 与 Redis 的分工</h3><p>MySQL 保存长期业务数据；Redis 保存会话、配额和熔断状态。普通数据库事务不会自动把 Redis 与外部模型调用一起回滚。</p></article><article><h3>数据库表不等于已交付功能</h3><p>schema.sql 中可以预先存在运营统计等表。是否交付还要看对应接口、页面、业务实现与验收记录。</p></article></div>
        </template>

        <section class="quiz-section"><div><span class="eyebrow">CHECK YOUR UNDERSTANDING</span><h2>用一个问题，检验理解</h2><span class="subtle-label">{{ questionIndex + 1 }} / {{ quizzes.length }}</span></div><div><h3>{{ question.question }}</h3><div class="quiz-options"><button v-for="(answer, i) in question.choices" :key="answer" type="button" :class="{ chosen: choice === i, correct: choice !== null && i === question.answer }" :aria-pressed="choice === i" @click="choice = i"><span>{{ String.fromCharCode(65 + i) }}</span>{{ answer }}<el-icon v-if="choice !== null && i === question.answer"><Check /></el-icon></button></div><p v-if="choice !== null" class="quiz-feedback" aria-live="polite">{{ choice === question.answer ? '回答正确。' : '再看一下正确选项。' }}{{ question.reason }}</p><button type="button" class="text-button" @click="questionIndex = (questionIndex + 1) % quizzes.length; choice = null">下一题 <el-icon><ArrowRight /></el-icon></button></div></section>
        <footer class="wb-footer"><span>内容依据：当前代码、README 与系统设计文档 · 教学快照</span><span>理解职责，再理解连接。</span></footer>
      </main>
    </div>
  </div>
</template>

<style scoped src="./workbench.css"></style>
