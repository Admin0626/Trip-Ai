# Trip-AI 项目学习导览

依据当前仓库代码整理，教学内容快照：2026-10-03。

## 一句话架构
Vue 页面 → Axios / HTTP → Spring Security → Controller → Service → Mapper / JDBC → MySQL；Redis 管理会话、配额与熔断。

## Spring 装配
### 启动容器
TripServerApplication 位于 com.trip。默认组件扫描覆盖这个包及其子包；自动配置根据依赖、配置项和条件补齐基础设施。
代码出处：trip-server/src/main/java/com/trip/TripServerApplication.java

### 注册 Bean
扫描到的 RouteServiceImpl 和 RouteController 被注册为 Bean 定义。RouteService 只是 Java 接口，本身不会被实例化。
代码出处：trip-server/src/main/java/com/trip/module/route/service/impl/RouteServiceImpl.java

### 准备基础设施
@Bean 将方法返回的分页拦截器交给容器。@MapperScan 由 MyBatis 为 Mapper 接口注册代理对象。这与普通的组件扫描有不同的职责。
代码出处：trip-server/src/main/java/com/trip/config/MybatisPlusConfig.java

### 注入依赖
Lombok 的 @RequiredArgsConstructor 为 final 字段生成构造器。只有一个构造器时，Spring 可以直接使用它，找到 RouteService 类型的实现 Bean 并传入。
代码出处：trip-server/src/main/java/com/trip/module/route/controller/RouteController.java

### 处理业务调用
请求到来时，Controller 调用已注入的 Service。常见 Bean 默认在一个容器内共享；业务方法仍会在每次请求时执行。事务方法通过 Spring 代理调用时获得事务边界。
代码出处：trip-server/src/main/java/com/trip/module/route/controller/RouteController.java

## 业务请求
### 浏览路线
GET /api/route/page?current=1&size=10
公开读取，按当前代码查询已上架路线与有效目的地。
1. Vue 页面：RouteList.vue 收集筛选条件，调用 routePageApi。（trip-web/src/views/route/RouteList.vue）
2. Axios / 开发代理：封装 /api 基础地址；Vite 在开发时将请求代理到后端 8080。（trip-web/src/api/modules/route.ts）
3. Security：GET /route/** 属于公开接口；有 Token 时仍会进入身份处理。（trip-server/src/main/java/com/trip/config/SecurityConfig.java）
4. Controller：绑定分页和筛选参数，将 size 上限限制为 100，查询上架状态 1。（trip-server/src/main/java/com/trip/module/route/controller/RouteController.java）
5. Service → Mapper → MySQL：组合过滤条件，执行分页查询，再将 Entity 转换为 RoutePageVO。（trip-server/src/main/java/com/trip/module/route/service/impl/RouteServiceImpl.java）
6. 响应 → 页面：R.ok 包装结果；前端读取 r.data.data，更新列表、分页与加载状态。（trip-web/src/api/modules/route.ts）

### 保存规划
POST /api/plan
登录用户提交规划；服务端检查身份、参数和业务规则后保存。
1. 规划编辑器：将天数、景点顺序、预算等组装为 PlanSaveDTO 对应的 JSON。（trip-web/src/views/plan/PlanEditor.vue）
2. 身份认证：请求携带 Bearer Token；后端结合 Redis 会话获取当前用户。（trip-server/src/main/java/com/trip/security/JwtAuthenticationFilter.java）
3. DTO 校验：@Valid 校验标题、1–30 天、正预算、1–10 人等输入条件。（trip-server/src/main/java/com/trip/module/plan/dto/PlanSaveDTO.java）
4. Service 规则与事务：验证日期、目的地等规则，用当前用户归属创建规划。public 入口上的 @Transactional 控制事务。（trip-server/src/main/java/com/trip/module/plan/service/impl/PlanServiceImpl.java）
5. 持久化：主规划、每日行程与明细在同一事务中写入 MySQL。（trip-server/src/main/java/com/trip/module/plan/mapper/UserPlanMapper.java）
6. 返回 ID：R<Long> 返回新规划 ID，页面可继续编辑；示例工作台不实际写库。（trip-server/src/main/java/com/trip/module/plan/controller/PlanController.java）

### AI 定制行程
POST /api/ai/planner/generate-stream
SSE 展示生成阶段。模型的完整 JSON 通过校验后成为预览，用户确认后才保存。
1. 页面发起：提供需求、天数、总预算、同行人数和用户模型连接参数。（trip-web/src/views/plan/AiPlanner.vue）
2. 认证与参数校验：验证 Token、会话和输入；每个流请求有独立 requestId。（trip-server/src/main/java/com/trip/module/ai/PlannerController.java）
3. 资源与调用控制：地址策略、并发名额、熔断和 Redis 配额控制是否能请求模型。（trip-server/src/main/java/com/trip/module/ai/PlannerService.java）
4. 模型调用：CompatiblePlannerClient 请求 OpenAI 兼容服务；SSE 发布 CONNECTING / GENERATING 等阶段。（trip-server/src/main/java/com/trip/module/ai/CompatiblePlannerClient.java）
5. 校验与预览：验证完整 JSON 结构；结构无效最多尝试两次，返回行程预览与建议。（trip-server/src/main/java/com/trip/module/ai/PlannerOutputValidator.java）
6. 用户确认 → 保存：用户确认后，另行调用 POST /api/plan；生成本身不保存个人规划。（trip-web/src/views/plan/AiPlanner.vue）

### 本地资料检索
POST /api/ai/knowledge/search
本地检索查询 MySQL 中的资料与索引，返回片段和引用；当前不请求模型。
1. 输入检索词：页面提交关键词和检索参数。（trip-web/src/views/knowledge/Search.vue）
2. 认证与输入校验：认证后调用 KnowledgeController.search。（trip-server/src/main/java/com/trip/module/ai/KnowledgeController.java）
3. 本地索引检索：KnowledgeService 查询已启用资料、分片与关键词索引并排序。（trip-server/src/main/java/com/trip/module/ai/KnowledgeService.java）
4. 过滤来源：检查来源公开状态以及关联目录是否有效。（trip-server/src/main/java/com/trip/module/ai/KnowledgeService.java）
5. 返回片段和引用：结果附来源信息；私人检索会话可保留历史引用。（trip-server/src/main/java/com/trip/module/ai/KnowledgeSessionService.java）
6. 展示与跳转：Vue 显示片段，用户可打开资料来源与关联目的地/路线。（trip-web/src/views/knowledge/Search.vue）

## 模块与边界
- 用户与认证（已实现）：注册、登录、资料、偏好、邮箱验证和密码找回
- 目的地与路线（已实现）：目录浏览、详情、后台维护、每日行程
- 互动与规划（已实现）：点赞、收藏、预约、评论、自主规划和保存
- 旅行助手（已实现）：规则路线匹配、用户模型规划、阶段 SSE、配额与熔断
- 本地知识（已实现）：资料维护、分片检索、引用和私人检索会话
- 运营与扩展 AI（设计待实现）：运营大屏、模型重排、向量检索、生成式 RAG、情感分析

## 阅读依据
README.md、docs/01-总体技术方案.md、docs/02-接口文档.md、docs/03-前端设计.md、docs/05-数据库设计.md、docs/08-业务流程与业务规则.md

Spring 参考：https://docs.spring.io/spring-boot/4.0/reference/using/spring-beans-and-dependency-injection.html

工作台中的接口、装配和事务实验是本地教学模拟，不能作为运行中的服务健康或验收结果。