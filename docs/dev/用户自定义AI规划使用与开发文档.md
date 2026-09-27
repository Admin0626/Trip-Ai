# 用户自定义AI规划：使用与开发复盘

更新：2026-09-27。用户已确认“每个用户自行填写OpenAI兼容API地址、模型名称和API Key”，这替代了此前暂不接模型的决定。本阶段交付真实兼容协议客户端，不再只返回规则结果；但没有用户的真实服务密钥，所以验收使用可控协议服务，不能声称真实模型生成质量已通过。

## 用户怎样使用

1. 启动项目并登录，点击导航“AI规划”，或直接访问 `http://127.0.0.1:5173/ai-planner`。首页和规划编辑器也有入口。
2. 填写API基础地址、模型ID和API Key。基础地址例如 `https://api.deepseek.com/v1`；本机模型例如 `http://localhost:11434/v1`，端口可自行修改，不要再附加`/chat/completions`。模型ID以自己的服务为准，本机无鉴权服务可不填Key。
3. 点击“测试连接”。这会真实向该服务发送短请求，可能产生少量费用，并非只检查URL格式。
4. 填写旅行需求、1—14天、总预算、同行人数和出发日期。可选择保存时关联的数据库目的地；AI的目的地依据需求文本生成，关联选择不会自动替代需求文本。
5. 点击“生成AI行程”，检查逐日活动、交通、餐饮、住宿及费用信息。服务返回格式不合格时最多重新请求一次。
6. 确认勾选后点击“保存到我的规划并编辑”，保存成草稿并进入原有编辑器，可以增删、排序、修改费用，再正式保存。生成预览本身不写规划表，也不覆盖既有规划。

API Key只保留当前页面内存，离开、刷新或切换用户后重新输入，不进入localStorage、sessionStorage或数据库。点击“记住地址与模型”只保存这两个字段，按当前用户ID区分，作用范围是当前浏览器，不做跨设备同步。包含账号、密码、查询参数或片段的URL不能被记住或用于调用。保存的地址不是密钥保险箱，不应将敏感信息嵌入路径。

## 连接范围与部署

当前只支持非流式、`messages`形式的Chat Completions，不支持原生Anthropic协议或Responses API。客户端追加`/chat/completions`，发Bearer认证、model/messages/stream=false/max_tokens。没有强制response_format参数，减少不同兼容服务对可选参数的差异；JSON要求写入数据库提示词，并由服务端自行校验。协议依据：[DeepSeek Chat Completions官方文档](https://api-docs.deepseek.com/api/create-chat-completion/)。

公网默认允许HTTPS主机`api.deepseek.com`和`api.openai.com`。其他兼容供应商由部署者在启动后端前加入可信列表，例如：

```powershell
$env:TRIP_AI_ALLOWED_HOSTS = 'api.deepseek.com,api.openai.com,你的可信模型主机'
mvn spring-boot:run
```

这是精确主机名单，不是任意URL代理。仅添加自己信任的模型服务，不添加任意用户控制的域名。URL禁止userinfo、查询参数、片段；公网要求HTTPS且解析地址不能为常见内网/回环等地址。客户端不跟随重定向，避免凭据被带到新目标。

开发配置`TRIP_AI_ALLOW_LOOPBACK`默认true，允许localhost或127.0.0.1的1024—65535端口，localhost规范为127.0.0.1。它指**运行Java后端的电脑**，不是远程访问者的电脑。部署到公共服务器时应设置false，不能把开发模式的本机访问能力暴露给不可信用户。内网IP模型暂不支持直接接入，需要可信HTTPS网关或后续受控网络方案。

每次调用默认45秒，可用`TRIP_AI_TIMEOUT_SECONDS`配置1—60秒。生成最多两次调用；前端等待上限125秒。响应上限1MiB（按接收流限制），单用户最多一个并行AI请求，单进程最多8个。暂未实现跨节点配额、按天计费统计、SSE或服务端断线取消；关闭页面时可能仍有已经发出的供应商请求消耗额度。

## 数据库初始化

本机已执行增量脚本；已有库无需重建。其他环境需执行一次，重复执行不会覆盖同版本模板：

```powershell
mysql -u root -p
```

进入MySQL客户端后：

```sql
source D:/demo_series/Trip-AI/trip-server/src/main/resources/sql/upgrade_user_planner.sql;
```

脚本新增启用的`USER_PLANNER`提示词，保留已有`ITINERARY_GEN` Markdown/SSE模板。没有新增表、删除业务数据或保存模型密钥。部署后更改模板可以使用新version，服务读取启用的最高版本。

## 开发结构

| 文件（trip-server/src/main/java/com/trip/module/ai内） | 职责 |
|---|---|
| PlannerController / PlannerConnection | 登录接口、请求参数校验，连接对象toString脱敏 |
| StrictPlannerJson | 字符串、整数和金额类型的严格反序列化，禁止字符串数字、小数天数被隐式接受 |
| PlannerEndpointPolicy | 可信主机、协议、本机端口和URL结构检查，提供前端可显示的连接范围 |
| CompatiblePlannerClient | JDK HttpClient调用兼容服务、Bearer认证、响应大小与超时、禁止重定向、错误正文不外传 |
| PlannerOutputValidator | JSON完整结构、天数、条目数、文本长度、费用类型和范围校验；不采信模型提供的数据库ID |
| PlannerService | 加载数据库提示词、一次结构重试、并发限制和脱敏调用审计 |

前端`AiPlanner.vue`管理用户配置、连接提示、表单、预览和确认保存，`api/modules/planner.ts`封装调用。输出始终通过Vue文本插值展示，不当HTML执行。修改输入/连接会使旧预览失效，页面卸载增加revision，旧响应不会回填离开的页面。

生成接口返回`source=USER_MODEL`，不伪造基础降级成功。供应商认证失败/429/超时/不合格输出返回code3004并提示用户修正，页面保持可重试。生成阶段不伪造数据库目的地或景点ID；每个活动的attractionId统一为0，用户在编辑器中可再关联景点。

## 输出与费用边界

返回恰好请求的天数，每天1—8项；总标题≤200字、天标题和活动标题≤100字，摘要/活动/提示≤255字，交通≤50字，餐饮/住宿≤100字。费用只接收非负、≤1000000、最多2位小数的JSON数字；未知可null，不能以“约100元”字符串代替。

未知费用预览显示为未提供；保存到现有规划表时复用旧逻辑将空费用记0用于编辑器计算，这不表示免费或真实预算已满足。AI只生成建议，不验证实时营业、交通可达性、价格、签证政策或天气，需要用户核实。

审计写入llm_call_log的scene为USER_MODEL_TEST或USER_PLANNER，包含userId/model/耗时/success及固定错误代码，不保存API地址、Key、完整需求或上游正文。本阶段token数量未解析，表中默认0不能解释为免费调用；attempts表示结构生成尝试次数，不等于调用日志条数（每次用户操作一条日志）。日志写失败仍会通过全局异常返回错误，尚无独立审计容错。

## 答辩复盘

可以这样解释：“配置属于每个用户，密钥只随当次请求使用。后端从数据库读取提示词，调用用户选择的兼容模型，校验返回的完整JSON，失败最多重试一次。模型只提供建议，不获得数据库写权限；用户确认后才通过已有规划接口保存草稿。真实接口测试还发现框架默认会把1.5天转换成1天，因此我针对新接口添加严格JSON反序列化。”

具体失败证据与结果见[用户自定义AI规划实测](用户自定义AI规划实测.md)。真实服务的连通性、兼容性、耗时和生成质量仍需用户填入自己的服务后验证。
