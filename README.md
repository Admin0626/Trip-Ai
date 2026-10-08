# Trip-AI

2026-10-08：前端轻量动效[PR #12](https://github.com/Admin0626/Trip-Ai/pull/12)已合并main，保留已集成统计与环形图；本次构建及集成浏览器9/9通过。交接见[SESSION](SESSION.md)。

基于 LLM 的旅行行程推荐系统。统一仓库包含 Spring Boot 后端、Vue 前端和设计/验收文档。

| 目录 | 内容 |
|---|---|
| `trip-server/` | Java 后端、SQL 初始化脚本、真实接口验收脚本 |
| `trip-web/` | Vue 3 + TypeScript 前端、浏览器验收脚本 |
| `docs/` | 设计、接口契约、实施记录、缺陷复盘和测试证据 |

## 当前进度

2026-09-29已记录[基础功能基线 v0.1.0-baseline](docs/releases/v0.1.0-baseline.md)：核心基础业务闭环已交付，未完成项明确列出。[GitHub Release](https://github.com/Admin0626/Trip-Ai/releases/tag/v0.1.0-baseline)保存固定版本；后续新功能在独立`codex/*`分支开发并推送，验收后通过PR融合main，见[分支流程](docs/dev/Git分支与合并流程.md)。

第2批互动/自主规划/用户端页面及基础后台操作闭环已完成；2026-09-28补完用户个人统计、保存偏好参与基础推荐、跨标签页刷新协调及用户模型故障熔断恢复。管理员可维护目录/行程/轮播、处理互动与反馈、启停普通用户及查看脱敏AI日志。项目整体仍有后续任务，保存的测试结果不代表每次打开项目都会重新执行。

- 2026-10-04前端轻量动效已完成：页面/模式淡入上移、列表错峰、卡片与按钮反馈、导航/手机菜单、生成状态点及系统减少动态支持。Edge动效49/49、基础工具页面回归28/28与前端构建通过；AI状态仅页面夹具，无真实模型调用。[实现/验收/答辩](docs/dev/前端轻量动效实现与验收.md)、[进度](docs/dev/前端轻量动效进度.md)。独立codex/frontend-motion，发布状态见SESSION。

- 2026-10-08后台环形图（[PR #14](https://github.com/Admin0626/Trip-Ai/pull/14)已合并main）：预约状态/模型结果显示数量与占比、中心总数、图例和键盘操作；只读HTTP/MySQL31项、真实浏览器32项及前端构建通过。[实测与答辩](docs/dev/后台环形图实测与开发复盘.md)、[进度](docs/dev/后台环形图进度.md)。

- 2026-10-04后台数据与可视化：`/admin/analytics`提供7/30/90日、目的地筛选、真实趋势/柱状/散点图和明细；HTTP/SQL55项、Edge44项、Maven53项及前端构建通过。[实现/实测/答辩](docs/dev/后台数据可视化实现与验收.md)、[进度](docs/dev/后台数据可视化进度.md)。2026-10-08 [PR #13](https://github.com/Admin0626/Trip-Ai/pull/13)已按用户授权合并main，集成状态见SESSION；无统计种子、真实模型调用或新SQL迁移。

- 2026-10-03基础数据工具完成，2026-10-04 [PR #11](https://github.com/Admin0626/Trip-Ai/pull/11)已按用户授权合并main：/admin/users筛选CSV导出、/admin/import目的地/景点/含每日行程路线JSON预览与原子导入、/user/profile本人注销及所有会话失效。[114项实际接口与28项页面实测](docs/dev/基础数据工具接口与页面实测.md)、[开发与答辩](docs/dev/基础数据工具开发与答辩复盘.md)、[进度](docs/dev/基础数据工具与注销进度.md)。集成状态见SESSION；已有库先运行upgrade_basic_data_tools.sql，只增幂等结果表。

- [统一旅行助手实测](docs/dev/统一旅行助手接口与页面实测.md)：统一查路线与AI入口，标准API配置，修复DeepSeek空最终回复；实际返回中文建议＋可保存每日行程。真实模型16项、受控HTTP18项、Edge28项及回归通过。[使用与答辩](docs/dev/统一旅行助手开发与答辩复盘.md)、[进度](docs/dev/统一旅行助手进度.md)。独立codex/travel-assistant已推送，[PR #7](https://github.com/Admin0626/Trip-Ai/pull/7)集成记录，发布见SESSION；建议全文目前仅当次预览，不自动持久化。
- [本次 Session](SESSION.md)：本次提交范围、验证结果、已知问题与下一步。
- [知识来源维护实测](docs/dev/知识来源维护接口与页面实测.md)：来源摘要/分页与单独关联，停用资料可选隐藏目录，正文分片与历史引用保留；124项接口、31项Edge及回归通过。[开发答辩](docs/dev/知识来源维护开发与答辩复盘.md)、[进度](docs/dev/知识来源维护进度.md)，独立codex/knowledge-sources已推送，[PR #6](https://github.com/Admin0626/Trip-Ai/pull/6)已合并main=aabcffe，发布见SESSION。
- [本地知识维护实测](docs/dev/本地知识维护接口与页面实测.md)：健康统计、异常/来源筛选、最多10篇逐项修复、正文命中及来源多样性；86项接口/SQL/Redis、24项Edge及回归通过。[开发答辩](docs/dev/本地知识维护开发与答辩复盘.md)、[进度](docs/dev/本地知识维护进度.md)。codex/knowledge-quality独立分支已推送，[PR #5](https://github.com/Admin0626/Trip-Ai/pull/5)已合并main596065e，发布记录见SESSION。
- 邮箱验证/绑定与找回密码已随[PR #4](https://github.com/Admin0626/Trip-Ai/pull/4)合并；[使用与部署](docs/dev/邮箱验证与找回密码使用与开发文档.md)、[原实测](docs/dev/邮箱验证与找回密码实测.md)。SMTP默认关闭，真实供应商尚未配置。
- [资料检索会话实测](docs/dev/资料检索会话接口与页面实测.md)：私人会话/历史、游标分页、版本冲突、UUID重试与历史引用核验；146项真实接口、55项Edge和回归通过。[开发与答辩复盘](docs/dev/资料检索会话开发与答辩复盘.md)、[进度](docs/dev/资料检索会话进度.md)。已推送独立`codex/knowledge-sessions`，[PR #3](https://github.com/Admin0626/Trip-Ai/pull/3)已合并main=29f0bb8；入口`/ai/chat`，不接真实模型。
- [本地知识检索实测](docs/dev/本地知识检索接口与页面实测.md)：管理员文档维护/UTF-8 TXT或MD导入、持久分片、本地检索/完整来源及有效目录链接；129项接口、44项Edge、33项后端测试通过。[开发与答辩复盘](docs/dev/本地知识检索开发与答辩复盘.md)、[进度](docs/dev/本地知识检索进度.md)。[PR #2](https://github.com/Admin0626/Trip-Ai/pull/2)已审核合并main=d63cb66；访问`/knowledge`或管理员`/admin/ai/knowledge`，不调用模型或扣AI额度。
- [AI规划SSE实测](docs/dev/AI规划SSE接口与页面实测.md)：实际阶段/心跳、本人取消、断连及会话撤销，116项接口、32项生命周期、40项页面与解析检查通过；[开发与答辩复盘](docs/dev/AI规划SSE开发与答辩复盘.md)、[进度](docs/dev/AI规划SSE进度.md)。[PR #1](https://github.com/Admin0626/Trip-Ai/pull/1)已审核合并至main=9d58768；上游仍返回完整JSON，未交付模型逐token输出。
- [模型熔断实测](docs/dev/模型熔断接口与页面实测.md)：Redis共享状态、用户/服务/模型/Key隔离、故障暂停/单次探测恢复，150项集成、26项默认配置和34项页面通过；[开发与答辩复盘](docs/dev/模型熔断开发与答辩复盘.md)、[进度/目标/问题](docs/dev/模型熔断进度.md)。AI规划页可刷新模型状态，故障时转基础推荐。无SQL迁移。
- [用户侧收尾实测](docs/dev/用户侧收尾接口与页面实测.md)：接口63项、会话30项、用户页面41项及回归通过；[开发与答辩复盘](docs/dev/用户侧收尾开发与答辩复盘.md)、[进度/目标/问题](docs/dev/用户侧收尾进度.md)。入口 `/user/profile`、`/user/preference`、`/recommend`。
- [基础后台实测](docs/dev/基础后台接口与页面实测.md)：真实接口/SQL/Redis139项、后台浏览器流程及既有功能回归；[开发与答辩复盘](docs/dev/基础后台开发与答辩复盘.md)。管理员访问 `/admin`，或从前台用户菜单进入“管理后台”。
- [管理员反馈实测](docs/dev/管理员反馈接口与页面实测.md)：94项接口/SQL等检查、30项Edge流程通过；支持分页筛选、合法状态转换、旧页面冲突和终态只读。[开发复盘](docs/dev/管理员反馈开发复盘.md)。
- [改密与会话失效实测](docs/dev/改密与会话失效接口实测.md)：75项接口/SQL等检查、24项Edge流程通过，已修复页面未处理异常；[开发复盘](docs/dev/改密与会话失效开发复盘.md)。
- [头像与资料保存实测](docs/dev/头像上传与资料保存接口实测.md)：路径校验与旧值响应已修复，59项接口/SQL等检查、16项Edge流程通过；[开发复盘](docs/dev/头像上传与资料保存开发复盘.md)。
- [当前进度、目标与问题](docs/dev/当前进度与交接.md)：历史阶段记录。
- [所有已完成项与剩余任务](docs/dev/项目完成项总览.md)：汇总交付能力、验收证据和边界。
- [接口实测与缺陷修复记录](docs/dev/第2批接口实测与缺陷修复记录.md)：真实响应及缺陷修复前后对照。
- [开发复盘与答辩提纲](docs/dev/第2批收尾开发复盘.md)：事务、并发名额、排序与权限的设计原因。
- [完整文档索引](docs/README.md)：系统设计和后续 AI 开发路线。
- 第3批已完成规则意图解析子阶段（无真实模型）：[24项接口/SQL实测与缺陷](docs/dev/第3批意图降级接口实测.md)、[开发复盘](docs/dev/第3批意图降级开发复盘.md)。模型链路及其余AI功能仍待开发。
- 第3批基础路线匹配与用户确认已完成：访问 `/travel-assistant?mode=routes`（旧`/recommend`兼容）；[37项HTTP/SQL及23项浏览器验收](docs/dev/第3批基础推荐接口与页面实测.md)、[开发复盘](docs/dev/第3批基础推荐开发复盘.md)。无需模型密钥。
- 用户自定义AI规划已接入：访问 `/travel-assistant?mode=ai`（旧`/ai-planner`兼容），每个用户填写API地址、模型和Key，连接测试→生成预览→确认保存草稿。Key不落盘；[使用与部署说明](docs/dev/用户自定义AI规划使用与开发文档.md)、[50项接口及22项浏览器测试](docs/dev/用户自定义AI规划实测.md)。原批测试使用可控兼容服务；2026-10-03新增真实DeepSeek一日游完整链路，范围见统一旅行助手实测，其他质量仍待验证。
- AI请求配额与统计已完成：Redis原子限制用户小时/日、全站日请求；AI规划页展示用量与重置时间，提供管理员脱敏日志查询。[70项接口/Redis及11项浏览器实测](docs/dev/AI配额与统计接口实测.md)、[计数规则与答辩复盘](docs/dev/AI配额与统计开发复盘.md)。
- 基础功能补齐：个人资料/偏好/改密/退出、目的地详情与景点、PNG/JPEG上传、反馈、轮播和目录管理已接入。邮箱验证码/找回密码已合并；本批补齐注销、用户导出与内容批量导入，情感分析及其他高级运营功能仍未完成。

## 本地运行

先按 [部署手册](docs/10-部署手册.md) 准备 MySQL 和演示库。已有数据时不要重新运行建表脚本。

```powershell
# 后端（默认禁用聊天模型，支持第3批规则意图解析）
cd trip-server
mvn spring-boot:run '-Dspring-boot.run.arguments=--spring.ai.model.chat=none'

# 另开终端，从仓库根目录进入前端
cd trip-web
npm ci
npm run dev -- --host 127.0.0.1
```

后端地址 `http://localhost:8080/api`，前端地址 `http://127.0.0.1:5173`。数据库凭据可通过 `MYSQL_PASSWORD` 覆盖本机开发默认值。Redis需在localhost:6379运行；登录/刷新/会话认证及AI模型配额依赖Redis，不可用时拒绝相关请求并返回503。

AI规划首次部署还需在MySQL客户端执行 `source D:/demo_series/Trip-AI/trip-server/src/main/resources/sql/upgrade_user_planner.sql;`（按实际项目路径调整，本机已执行）。这是增加USER_PLANNER提示词的幂等升级，不会重建数据库。用户密钥在页面填写，无需配置应用全局模型Key。公网模型主机范围、本机端口和生产环境要求见上面的使用说明。

本地知识首次部署在MySQL客户端选择trip_llm后执行 `source D:/demo_series/Trip-AI/trip-server/src/main/resources/sql/upgrade_local_knowledge.sql;`（调整为实际路径）。此脚本只增4字段/2表，可重复执行，已有数据不要运行schema.sql/data.sql。管理员逐篇“重建分片”后启用；本机演示10篇已索引。开发可用`python scripts/knowledge_seed_index.py`仅重建与仓库种子完全一致的演示资料，修改过的跳过，不猜关联ID。

真实验收会创建和清理专用测试数据；命令、环境要求和证据路径见接口实测报告。报告生成器只读取证据，不会重新测试接口。

私人资料会话首次部署在MySQL选择trip_llm后执行 `source D:/demo_series/Trip-AI/trip-server/src/main/resources/sql/upgrade_knowledge_sessions.sql;`，前提是知识升级已经完成。本机已重复执行成功；只增5字段/2索引，旧聊天标记LEGACY，29表不变。访问`/knowledge`进入资料会话，或直接`/ai/chat`。历史引用在读取时重新检查当前公开状态，已显示内容需刷新。

## 尚未交付

第3批平台真实模型意图解析、多路召回与模型重排、推荐/问答的模型token流、语义向量与生成式RAG聊天、规划AI优化和评论情感分析，以及运营大屏、提示词版本管理、角色调整、生产云存储等仍待开发。注销、用户CSV导出与目录JSON批量导入在本批功能分支完成；邮箱验证/找回密码已实现，真实SMTP未配置。阶段SSE、本地知识和历史已集成，但生成式知识问答/语义上下文未提供。用户模型一日游真实链路曾验收，不代表所有供应商/行程质量验证完成。
