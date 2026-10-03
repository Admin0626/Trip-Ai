export interface DiagramNode {
  id: string; title: string; subtitle: string; tag: string; col: number; row: number
  description: string; facts: string[]; source: string; code?: string
}
export interface DiagramEdge { from: string; to: string; label: string; dashed?: boolean }
export interface Lesson { id: string; title: string; subtitle: string; question: string }
export const lessons: Lesson[] = [
  { id: 'overview', title: '系统总览', subtitle: '先建立全局认知', question: '这个项目由哪些部分组成？' },
  { id: 'spring', title: 'Spring 装配', subtitle: '理解对象如何连接', question: 'Controller 的 Service 从哪里来？' },
  { id: 'request', title: '请求链路', subtitle: '跟随一次真实调用', question: '点击按钮之后，系统做了什么？' },
  { id: 'frontend', title: '前端协作', subtitle: '页面、状态与接口', question: '后端返回的数据如何变成页面？' },
  { id: 'api', title: '接口实验', subtitle: '读懂请求与响应', question: '前后端如何约定数据和错误？' },
  { id: 'data', title: '数据关系', subtitle: '理解持久化与事务', question: '行程如何被完整地保存？' },
]
const server = 'trip-server/src/main/java/com/trip/'
export const overviewNodes: DiagramNode[] = [
  { id: 'vue', title: 'Vue 页面', subtitle: '用户端 / 管理后台', tag: '展示层', col: 0, row: 0,
    description: '用户看到的页面由 Vue 组件组成。用户操作触发接口调用，返回的数据驱动页面更新。', facts: ['Vue 3 + TypeScript + Element Plus', 'Vue Router 选择页面，Pinia 管理共享用户状态', '游客可浏览目录；个人功能需要登录'], source: 'trip-web/src/router/index.ts', code: "{ path: 'routes', component: () => import('@/views/route/RouteList.vue'),\n  meta: { title: '路线', public: true } }" },
  { id: 'http', title: 'HTTP / SSE', subtitle: 'Axios 统一请求', tag: '通信', col: 1, row: 0,
    description: '前端通过 HTTP 与后端通信，统一请求封装负责基础地址、超时、Token 和错误处理。AI 规划还使用 SSE 传递阶段事件。', facts: ['开发代理：5173 的 /api → 8080', 'JSON 接口通常返回 R<T>；SSE 返回事件流', 'AI SSE 使用 Axios fetch adapter 读取流'], source: 'trip-web/src/api/request.ts', code: "const baseURL = import.meta.env.VITE_API_BASE_URL || '/api'\nconst request = axios.create({ baseURL, timeout: 30000 })\n// 请求拦截器按当前会话设置 Authorization" },
  { id: 'security', title: 'Spring Security', subtitle: 'JWT + Redis 会话', tag: '接入层', col: 2, row: 0,
    description: '请求先经过过滤器链。系统检查访问规则；携带 Token 时检查身份和会话，再把认证信息交给业务代码。', facts: ['GET /route/**、/destination/** 可公开浏览', '其他业务默认需登录，/admin/** 需 ADMIN', 'JWT 配合 Redis 会话支持撤销与刷新'], source: server + 'config/SecurityConfig.java', code: '.requestMatchers(HttpMethod.GET, "/destination/**", "/route/**", "/banner/list").permitAll()\n.requestMatchers("/admin/**").hasRole("ADMIN")\n.anyRequest().authenticated()' },
  { id: 'controller', title: 'Controller', subtitle: '接收参数 · 返回结果', tag: '接口层', col: 3, row: 0,
    description: 'Controller 将 URL 和 HTTP 方法映射到 Java 方法，接收参数、触发校验并调用 Service。', facts: ['@RestController 注册组件并将返回值写入响应体', '@RequestMapping + @GetMapping 定位接口', 'DTO 接收输入，VO 表达输出'], source: server + 'module/route/controller/RouteController.java', code: '@RestController\n@RequestMapping("/route")\n@RequiredArgsConstructor\npublic class RouteController {\n    private final RouteService routeService;\n}' },
  { id: 'mysql', title: 'MySQL', subtitle: '业务数据持久化', tag: '数据层', col: 1, row: 1,
    description: '用户、目的地、路线、规划和知识资料等长期数据存储在关系数据库中。', facts: ['Entity 通过 @TableName 对应数据表', 'Mapper 查询结果在业务层组装成 VO', '规划及其每日条目通过事务一起保存'], source: 'trip-server/src/main/resources/sql/schema.sql', code: 'route → route_day → route_item\nuser_plan → user_plan_day → user_plan_item\nknowledge_doc → knowledge_chunk → knowledge_chunk_token' },
  { id: 'mapper', title: 'Mapper / JDBC', subtitle: '执行数据库访问', tag: '持久层', col: 2, row: 1,
    description: '目录业务使用 MyBatis-Plus Mapper。AI 和知识模块也直接使用 JdbcTemplate。两种方式最终都通过数据源访问 MySQL。', facts: ['@MapperScan 为 Mapper 接口注册代理 Bean', 'BaseMapper 提供常用查询和写入方法', 'Mapper 是 Java 接口；其代理实现由框架创建'], source: server + 'module/route/mapper/RouteMapper.java', code: 'public interface RouteMapper extends BaseMapper<Route> {\n}\n// MybatisPlusConfig 中 @MapperScan 包含 route.mapper' },
  { id: 'redis', title: 'Redis', subtitle: '会话 · 配额 · 熔断', tag: '共享状态', col: 2, row: 2,
    description: 'Redis 保存可过期或需要原子协调的共享状态。它服务于认证与 AI 控制，而不是替代 MySQL 的业务数据。', facts: ['AuthSessionService 管理登录会话', 'AiQuotaService 原子控制模型请求额度', 'PlannerCircuitService 管理故障熔断与探测恢复'], source: server + 'module/ai/AiQuotaService.java' },
  { id: 'service', title: 'Service', subtitle: '规则 · 编排 · 事务', tag: '业务层', col: 3, row: 1,
    description: 'Service 负责具体业务：筛选已上架路线、检查数据归属、保存规划、组织 AI 请求等。它把数据库结果转换为页面需要的内容。', facts: ['@Service 让实现类进入 Spring 容器', 'Controller 依赖接口，Spring 注入实现对象', '各模块按业务组织；它们运行在同一个后端应用内'], source: server + 'module/route/service/impl/RouteServiceImpl.java', code: '@Service\n@RequiredArgsConstructor\npublic class RouteServiceImpl implements RouteService {\n    private final RouteMapper routeMapper;\n    // 另有目的地、行程和互动 Mapper 依赖\n}' },
  { id: 'knowledge', title: '本地资料检索', subtitle: '分片 · 关键词 · 引用', tag: '已实现', col: 0, row: 1,
    description: '本地知识层将资料正文分片和索引，通过本地检索返回片段与来源，并保存私人检索会话。', facts: ['无需调用模型，也不消耗 AI 额度', '资料停用后，历史引用读取时重新核验', '生成式 RAG 和语义向量检索仍待开发'], source: server + 'module/ai/KnowledgeService.java' },
  { id: 'model', title: '用户模型服务', subtitle: 'OpenAI 兼容协议', tag: '外部依赖', col: 0, row: 2,
    description: '用户配置服务地址、模型名和 Key。后端通过 CompatiblePlannerClient 请求兼容服务，验证结构后返回预览。', facts: ['模型仅在 AI 定制行程等相应操作中被调用', '用户 Key 不落盘', '目前上游返回完整 JSON；阶段 SSE 不等于逐 token 输出'], source: server + 'module/ai/CompatiblePlannerClient.java' },
  { id: 'planner', title: 'AI 规划编排', subtitle: '限额 → 生成 → 校验', tag: '已实现', col: 1, row: 2,
    description: 'PlannerService 管理模型调用。AI 输出先成为预览，用户确认后通过规划接口保存可编辑草稿。', facts: ['地址策略、并发限制、额度和熔断共同控制调用', '无效结构最多尝试两次；业务校验在服务端完成', '查找已有路线走规则匹配；本地资料检索走本地索引'], source: server + 'module/ai/PlannerService.java' },
]
export const overviewEdges: DiagramEdge[] = [
  { from: 'vue', to: 'http', label: '调用' }, { from: 'http', to: 'security', label: '/api' },
  { from: 'security', to: 'controller', label: '放行' }, { from: 'controller', to: 'service', label: '委托' },
  { from: 'service', to: 'mapper', label: '读写' }, { from: 'mapper', to: 'mysql', label: 'SQL' },
  { from: 'security', to: 'redis', label: '会话核验' }, { from: 'service', to: 'planner', label: 'AI 分支' },
  { from: 'planner', to: 'model', label: '请求' }, { from: 'planner', to: 'redis', label: '控制', dashed: true },
  { from: 'knowledge', to: 'mysql', label: '索引', dashed: true },
]
export const springSteps = [
  { title: '启动容器', brief: '让 Spring 管理对象', annotation: '@SpringBootApplication',
    text: 'TripServerApplication 位于 com.trip。默认组件扫描覆盖这个包及其子包；自动配置根据依赖、配置项和条件补齐基础设施。',
    file: server + 'TripServerApplication.java', code: '@SpringBootApplication\npublic class TripServerApplication {\n    public static void main(String[] args) {\n        SpringApplication.run(TripServerApplication.class, args);\n    }\n}' },
  { title: '注册 Bean', brief: '发现需要管理的组件', annotation: '@Service / @RestController',
    text: '扫描到的 RouteServiceImpl 和 RouteController 被注册为 Bean 定义。RouteService 只是 Java 接口，本身不会被实例化。',
    file: server + 'module/route/service/impl/RouteServiceImpl.java', code: '@Service\n@RequiredArgsConstructor\npublic class RouteServiceImpl implements RouteService {\n    private final RouteMapper routeMapper;\n    // 其余依赖省略\n}' },
  { title: '准备基础设施', brief: '创建配置对象与代理', annotation: '@Bean / @MapperScan',
    text: '@Bean 将方法返回的分页拦截器交给容器。@MapperScan 由 MyBatis 为 Mapper 接口注册代理对象。这与普通的组件扫描有不同的职责。',
    file: server + 'config/MybatisPlusConfig.java', code: '@Configuration\n@MapperScan(basePackages = { /* 本项目各 mapper 包 */ })\npublic class MybatisPlusConfig {\n    @Bean\n    public MybatisPlusInterceptor mybatisPlusInterceptor() {\n        var interceptor = new MybatisPlusInterceptor();\n        interceptor.addInnerInterceptor(\n            new PaginationInnerInterceptor(DbType.MYSQL));\n        return interceptor;\n    }\n}' },
  { title: '注入依赖', brief: '按类型连接对象', annotation: '构造器注入',
    text: 'Lombok 的 @RequiredArgsConstructor 为 final 字段生成构造器。只有一个构造器时，Spring 可以直接使用它，找到 RouteService 类型的实现 Bean 并传入。',
    file: server + 'module/route/controller/RouteController.java', code: '// 等价教学展开：此构造器由 Lombok 生成\npublic RouteController(RouteService routeService) {\n    this.routeService = routeService;\n}\n// 注入的是 RouteServiceImpl 对象，或其 Spring 代理' },
  { title: '处理业务调用', brief: '装配好的对象开始协作', annotation: '调用 ≠ 装配',
    text: '请求到来时，Controller 调用已注入的 Service。常见 Bean 默认在一个容器内共享；业务方法仍会在每次请求时执行。事务方法通过 Spring 代理调用时获得事务边界。',
    file: server + 'module/route/controller/RouteController.java', code: '@GetMapping("/{id}")\npublic R<RouteDetailVO> detail(@PathVariable Long id) {\n    return R.ok(routeService.detail(id));\n}' },
]
export const springNodes: DiagramNode[] = [
  { id: 'container', title: 'ApplicationContext', subtitle: '注册与管理 Bean', tag: 'IoC 容器', col: 0, row: 0, description: 'IoC：创建对象及其依赖的控制权交给容器。DI：容器把依赖对象注入构造器。', facts: ['装配发生在对象创建期间', '业务请求使用已经装配的对象'], source: server + 'TripServerApplication.java' },
  { id: 'mapperBean', title: 'RouteMapper 代理', subtitle: '@MapperScan 注册', tag: 'Bean', col: 1, row: 0, description: 'MyBatis 扫描 Mapper 接口，为它创建可执行数据库访问的代理对象。', facts: ['接口定义可用方法', '代理承担 SQL 执行'], source: server + 'config/MybatisPlusConfig.java' },
  { id: 'serviceBean', title: 'RouteServiceImpl', subtitle: 'implements RouteService', tag: '@Service', col: 2, row: 0, description: 'Service 实现作为 Bean 注册。Spring 可以用它满足 RouteService 接口类型的依赖。', facts: ['构造器需要 RouteMapper 等依赖', '当前路线模块有一个 RouteService 实现'], source: server + 'module/route/service/impl/RouteServiceImpl.java' },
  { id: 'controllerBean', title: 'RouteController', subtitle: 'final RouteService', tag: '@RestController', col: 3, row: 0, description: 'Controller 声明需要 RouteService；Spring 解析类型并通过构造器注入实现对象。', facts: ['Lombok 生成构造器', '无须手动 new RouteServiceImpl()'], source: server + 'module/route/controller/RouteController.java' },
]
export const springEdges: DiagramEdge[] = [ { from: 'container', to: 'mapperBean', label: '准备代理' }, { from: 'mapperBean', to: 'serviceBean', label: '注入' }, { from: 'serviceBean', to: 'controllerBean', label: '注入' } ]
export const scenarios = {
  route: { label: '浏览路线', method: 'GET', url: '/api/route/page?current=1&size=10', note: '公开读取，按当前代码查询已上架路线与有效目的地。', steps: [
    ['Vue 页面', 'RouteList.vue 收集筛选条件，调用 routePageApi。', 'trip-web/src/views/route/RouteList.vue'],
    ['Axios / 开发代理', '封装 /api 基础地址；Vite 在开发时将请求代理到后端 8080。', 'trip-web/src/api/modules/route.ts'],
    ['Security', 'GET /route/** 属于公开接口；有 Token 时仍会进入身份处理。', server + 'config/SecurityConfig.java'],
    ['Controller', '绑定分页和筛选参数，将 size 上限限制为 100，查询上架状态 1。', server + 'module/route/controller/RouteController.java'],
    ['Service → Mapper → MySQL', '组合过滤条件，执行分页查询，再将 Entity 转换为 RoutePageVO。', server + 'module/route/service/impl/RouteServiceImpl.java'],
    ['响应 → 页面', 'R.ok 包装结果；前端读取 r.data.data，更新列表、分页与加载状态。', 'trip-web/src/api/modules/route.ts'],
  ] },
  plan: { label: '保存规划', method: 'POST', url: '/api/plan', note: '登录用户提交规划；服务端检查身份、参数和业务规则后保存。', steps: [
    ['规划编辑器', '将天数、景点顺序、预算等组装为 PlanSaveDTO 对应的 JSON。', 'trip-web/src/views/plan/PlanEditor.vue'],
    ['身份认证', '请求携带 Bearer Token；后端结合 Redis 会话获取当前用户。', server + 'security/JwtAuthenticationFilter.java'],
    ['DTO 校验', '@Valid 校验标题、1–30 天、正预算、1–10 人等输入条件。', server + 'module/plan/dto/PlanSaveDTO.java'],
    ['Service 规则与事务', '验证日期、目的地等规则，用当前用户归属创建规划。public 入口上的 @Transactional 控制事务。', server + 'module/plan/service/impl/PlanServiceImpl.java'],
    ['持久化', '主规划、每日行程与明细在同一事务中写入 MySQL。', server + 'module/plan/mapper/UserPlanMapper.java'],
    ['返回 ID', 'R<Long> 返回新规划 ID，页面可继续编辑；示例工作台不实际写库。', server + 'module/plan/controller/PlanController.java'],
  ] },
  ai: { label: 'AI 定制行程', method: 'POST', url: '/api/ai/planner/generate-stream', note: 'SSE 展示生成阶段。模型的完整 JSON 通过校验后成为预览，用户确认后才保存。', steps: [
    ['页面发起', '提供需求、天数、总预算、同行人数和用户模型连接参数。', 'trip-web/src/views/plan/AiPlanner.vue'],
    ['认证与参数校验', '验证 Token、会话和输入；每个流请求有独立 requestId。', server + 'module/ai/PlannerController.java'],
    ['资源与调用控制', '地址策略、并发名额、熔断和 Redis 配额控制是否能请求模型。', server + 'module/ai/PlannerService.java'],
    ['模型调用', 'CompatiblePlannerClient 请求 OpenAI 兼容服务；SSE 发布 CONNECTING / GENERATING 等阶段。', server + 'module/ai/CompatiblePlannerClient.java'],
    ['校验与预览', '验证完整 JSON 结构；结构无效最多尝试两次，返回行程预览与建议。', server + 'module/ai/PlannerOutputValidator.java'],
    ['用户确认 → 保存', '用户确认后，另行调用 POST /api/plan；生成本身不保存个人规划。', 'trip-web/src/views/plan/AiPlanner.vue'],
  ] },
  knowledge: { label: '本地资料检索', method: 'POST', url: '/api/ai/knowledge/search', note: '本地检索查询 MySQL 中的资料与索引，返回片段和引用；当前不请求模型。', steps: [
    ['输入检索词', '页面提交关键词和检索参数。', 'trip-web/src/views/knowledge/Search.vue'],
    ['认证与输入校验', '认证后调用 KnowledgeController.search。', server + 'module/ai/KnowledgeController.java'],
    ['本地索引检索', 'KnowledgeService 查询已启用资料、分片与关键词索引并排序。', server + 'module/ai/KnowledgeService.java'],
    ['过滤来源', '检查来源公开状态以及关联目录是否有效。', server + 'module/ai/KnowledgeService.java'],
    ['返回片段和引用', '结果附来源信息；私人检索会话可保留历史引用。', server + 'module/ai/KnowledgeSessionService.java'],
    ['展示与跳转', 'Vue 显示片段，用户可打开资料来源与关联目的地/路线。', 'trip-web/src/views/knowledge/Search.vue'],
  ] },
}
export type ScenarioKey = keyof typeof scenarios
export const frontendNodes: DiagramNode[] = [
  { id: 'router', title: 'Vue Router', subtitle: 'URL → 页面组件', tag: '路由', col: 0, row: 0, description: '路由决定当前页面，guard 决定是否先跳登录。前端守卫改善体验；后端负责实际权限检查。', facts: ['routes 为公开页面', 'meta.public 允许游客访问', '路由组件按需加载'], source: 'trip-web/src/router/guard.ts' },
  { id: 'component', title: 'Vue 组件', subtitle: '模板 + 行为 + 样式', tag: '页面', col: 1, row: 0, description: '单文件组件用 template 描述结构，script 管理事件与数据，style 定义样式。', facts: ['用户事件触发业务操作', 'ref 数据变化驱动 DOM 更新', 'loading / empty / error 都是页面状态'], source: 'trip-web/src/views/route/RouteList.vue' },
  { id: 'module', title: 'API 模块', subtitle: 'routePageApi(params)', tag: '接口封装', col: 2, row: 0, description: 'API 模块给业务请求命名，把参数和返回类型集中管理，页面不必到处拼 URL。', facts: ['TS 类型帮助开发时检查字段', 'TS 类型不能替代服务端运行时校验'], source: 'trip-web/src/api/modules/route.ts', code: "export function routePageApi(params: RouteQuery) {\n  return request\n    .get('/route/page', { params })\n    .then(r => r.data.data)\n}" },
  { id: 'client', title: 'Axios 请求层', subtitle: 'Token / 错误 / 刷新', tag: '通信', col: 3, row: 0, description: '所有普通 API 共用请求封装，避免每个页面重复处理基础地址、Token 与错误。', facts: ['业务成功读取 response.data.data', '401 可触发受协调的会话刷新', '403 提示权限不足'], source: 'trip-web/src/api/request.ts' },
  { id: 'store', title: 'Pinia 用户状态', subtitle: '登录信息与用户身份', tag: '共享状态', col: 0, row: 1, description: '多个组件需要共享当前用户信息时，使用 Pinia 用户 store；会话持久化与跨标签协调由 storage 工具配合。', facts: ['页面局部数据不必全部放入 store', '用户状态与页面列表是不同生命周期'], source: 'trip-web/src/store/user.ts' },
  { id: 'render', title: '响应式渲染', subtitle: '数据变化 → UI 更新', tag: '视图更新', col: 1, row: 1, description: '异步响应到达后更新组件数据，Vue 根据响应式依赖刷新模板。', facts: ['无需手动操作列表 DOM', '旧用户会话的响应会被请求层拦截'], source: 'trip-web/src/App.vue' },
]
export const frontendEdges: DiagramEdge[] = [ { from: 'router', to: 'component', label: '加载' }, { from: 'component', to: 'module', label: '调用' }, { from: 'module', to: 'client', label: '请求' }, { from: 'store', to: 'component', label: '用户状态', dashed: true }, { from: 'component', to: 'render', label: '更新 ref' } ]
export const dataNodes: DiagramNode[] = [
  { id: 'user', title: 'sys_user', subtitle: 'id · username · role', tag: '用户', col: 0, row: 0, description: '用户是个人规划和互动行为的归属主体。', facts: ['user_plan.user_id 指向用户', '业务层检查当前用户对数据的访问权限'], source: 'trip-server/src/main/resources/sql/schema.sql' },
  { id: 'plan', title: 'user_plan', subtitle: 'user_id · days · budget', tag: '规划', col: 1, row: 0, description: '主规划保存标题、目的地、日期、预算与用户归属。', facts: ['一个用户可以有多个规划', '一个规划有多天行程'], source: server + 'module/plan/entity/UserPlan.java' },
  { id: 'day', title: 'user_plan_day', subtitle: 'user_plan_id · day_index', tag: '每日行程', col: 2, row: 0, description: '一条记录表示某规划的某一天。', facts: ['user_plan_id 关联主规划', '服务端按 dayList 顺序重排'], source: server + 'module/plan/entity/UserPlanDay.java' },
  { id: 'item', title: 'user_plan_item', subtitle: 'plan_day_id · sort_no', tag: '明细', col: 3, row: 0, description: '行程明细记录景点、活动等内容；每条明细属于一天。', facts: ['plan_day_id 关联每日行程', '保存时由业务层维护条目顺序'], source: server + 'module/plan/entity/UserPlanItem.java' },
  { id: 'destination', title: 'destination', subtitle: 'id · name · status', tag: '目录', col: 0, row: 1, description: '目的地关联景点和路线，目录的启停会影响公开查询结果。', facts: ['attraction.destination_id 关联目的地', 'route.destination_id 关联目的地'], source: 'trip-server/src/main/resources/sql/schema.sql' },
  { id: 'route', title: 'route', subtitle: 'destination_id · status', tag: '目录路线', col: 1, row: 1, description: '目录中的可浏览路线，与用户自己的规划分开存储。', facts: ['路线行程：route_day → route_item', '从路线创建规划时复制内容，不直接修改目录'], source: server + 'module/route/entity/Route.java' },
  { id: 'doc', title: 'knowledge_doc', subtitle: '资料正文与来源', tag: '知识', col: 2, row: 1, description: '知识资料保存正文和来源元信息，目录关联只是部分资料的可选关系。', facts: ['资料可分成多个片段', '资料停用时保留正文与历史引用'], source: 'trip-server/src/main/resources/sql/schema.sql' },
  { id: 'chunk', title: 'knowledge_chunk', subtitle: 'doc_id · chunk_index', tag: '分片', col: 3, row: 1, description: '分片让正文可以按片段检索；关键词索引在 knowledge_chunk_token 中维护。', facts: ['当前是本地文本检索', '向量 embedding 尚未交付'], source: 'trip-server/src/main/resources/sql/schema.sql' },
]
export const dataEdges: DiagramEdge[] = [ { from: 'user', to: 'plan', label: '1 : N' }, { from: 'plan', to: 'day', label: '1 : N' }, { from: 'day', to: 'item', label: '1 : N' }, { from: 'destination', to: 'route', label: '1 : N' }, { from: 'doc', to: 'chunk', label: '1 : N' } ]
export const modules = [
  ['用户与认证', '注册、登录、资料、偏好、邮箱验证和密码找回', '已实现'],
  ['目的地与路线', '目录浏览、详情、后台维护、每日行程', '已实现'],
  ['互动与规划', '点赞、收藏、预约、评论、自主规划和保存', '已实现'],
  ['旅行助手', '规则路线匹配、用户模型规划、阶段 SSE、配额与熔断', '已实现'],
  ['本地知识', '资料维护、分片检索、引用和私人检索会话', '已实现'],
  ['运营与扩展 AI', '运营大屏、模型重排、向量检索、生成式 RAG、情感分析', '设计待实现'],
]
export const quizzes = [
  { question: 'RouteController 中的 RouteService 是怎么来的？', choices: ['每次请求时手动 new', 'Spring 根据构造器参数注入实现 Bean', '浏览器通过 JSON 传入'], answer: 1, reason: 'Controller 是 Bean；Lombok 生成构造器，Spring 按 RouteService 类型解析实现并注入。' },
  { question: 'TypeScript 中写了接口类型，就可以取消后端校验吗？', choices: ['可以，前端已检查所有请求', '不可以，服务端必须在运行时校验', '只有管理员请求可以'], answer: 1, reason: 'TS 类型主要用于开发时检查；调用者可以绕过页面直接发送 HTTP 请求。' },
  { question: '本项目生成 AI 规划后，何时保存进 user_plan？', choices: ['模型一开始生成时', '收到每个 SSE 阶段时', '用户确认后调用规划保存接口'], answer: 2, reason: '生成接口返回预览；保存是独立的规划业务请求。' },
  { question: '@MapperScan 在当前项目中承担什么职责？', choices: ['为 Mapper 接口注册代理 Bean', '扫描所有 Vue 页面', '为每个接口创建数据库表'], answer: 0, reason: 'RouteMapper 是接口，MyBatis 通过扫描注册其代理，供 Service 注入和调用。' },
]
