# AI规划SSE接口与页面实测

日期：2026-09-29；起点`ceec9a2`，分支`codex/ai-planner-sse`。实际请求本机Spring Boot、MySQL和Redis，并连接受控HTTP模型服务。没有接入收费真实模型；阶段通信验收不能证明真实模型的内容质量、费用或供应商取消能力。

## 最终结果与证据

| 检查 | 结果 | 实际证据 |
|---|---|---|
| 增量SSE、权限、取消、重试、配额/熔断/SQL | 116/116 | [HTTP](evidence/planner-sse/20260929-201927-667717-expanded-final.json) |
| 令牌轮换、账号禁用、整体超时 | 32/32 | [生命周期](evidence/planner-sse/20260929-202507-512960-lifecycle.json) |
| Edge真实流/页面与独立解析器检查 | 40/40，pageerror=0 | [浏览器](evidence/planner-sse/2026-09-29T12-37-08-974Z-browser/results.json) |
| 旧普通规划接口 | 52/52 | [HTTP回归](evidence/user-planner/20260929-202816-422636-http.json) |
| 旧普通规划预览/确认保存/编辑 | 26/26 | [页面回归](evidence/user-planner/2026-09-29T12-28-21-920Z-browser/results.json) |
| 密码/会话 | 75/75 | [会话回归](evidence/password/20260929-202906-313536-final.json) |
| 基础业务 | 21/21 | [基础回归](evidence/basic-user/20260929-202928-609764-http.json) |
| Maven | 29/29，失败/错误0 | 新增4项取消/提交/共享名额测试；[构建摘要](evidence/planner-sse/build-evidence.json) |
| 前端 | vue-tsc与生产构建通过 | 保留既有约1.13MB主包警告；未声称本批完成拆包 |

浏览器SSE走实际Vite代理，不使用`route.fetch`缓冲整条生成响应。仅“取消接口故障”一项主动替换取消接口返回404；该项生成流仍为真实网络。解析器15项在Edge中导入实际TS模块，构造分块输入，报告名称以`local actual parser`区分，不能当成15个真实HTTP接口。

## 请求与实际响应

`POST /api/ai/planner/generate-stream`，Bearer鉴权，Accept为`text/event-stream`，请求为`{requestId,input}`。requestId为小写UUID；input沿用普通generate的connection/query/days/budget/peopleNum/startDate。Key、JWT与密码在证据中脱敏。

成功响应HTTP200，`text/event-stream;charset=UTF-8`、`Cache-Control: no-cache, no-store`、`X-Accel-Buffering: no`。以下取自最终受控响应，requestId缩写仅用于阅读：

```text
event:start
data:{"requestId":"…","data":{"state":"RUNNING"}}

event:progress
data:{"requestId":"…","data":{"stage":"CONNECTING","attempt":1}}

event:progress
data:{"requestId":"…","data":{"stage":"GENERATING","attempt":1}}

event:heartbeat
data:{"requestId":"…","data":{"elapsedSeconds":5}}

event:progress
data:{"requestId":"…","data":{"stage":"VALIDATING","attempt":1}}

event:done
data:{"requestId":"…","data":{"draft":{"title":"SSE协议夹具","dayList":[…]},"source":"USER_MODEL","model":"sse-heartbeat","attempts":1}}
```

首帧在慢模型返回前实际读到，6秒等待期间实际收到心跳；完整结果为2天，每天具有已校验条目。数据库确认没有自动保存规划。结构不合格时出现RETRYING，随后attempt=2；外呼2次、额度2次、操作日志1次，最终done的attempts=2。

取消路径为`POST /api/ai/planner/requests/{id}/cancel`。进行中返回HTTP200/code200，data.accepted=true，state通常CANCELLING；流终态为cancelled/code499，状态查询最终CANCELLED。再次取消已取消任务仍code200；成功/提交中的任务code409。他人查询/取消均code404，匿名HTTP401。重复UUID启动code409，未增加外呼。

| 场景 | 实际结果 |
|---|---|
| 非法UUID、字符串天数、过期日期 | 开流前JSON code400，不扣模型额度 |
| 本人同时旧generate/新SSE | code429，同一用户名额共用 |
| 供应商401/429/500、无有效文本、超过1MiB | SSE error/code3004，安全中文提示，不返回供应商错误正文；无网络自动重试 |
| 两次草稿结构不合格 | SSE error/code3004，无done |
| 用户额度耗尽 | SSE error/code429，外呼0、日志0 |
| 实际外呼后取消 | 终态及时到达，等待远短于8秒供应商延迟；额度1、操作1、审计CANCELLED，故障样本0 |
| 关闭真实HTTP连接 | 写入/心跳检测后CANCELLED；后续请求可进入；不记供应商故障 |
| 退出、刷新令牌轮换、管理员禁用夹具账号 | error/code401、FAILED；审计STOPPED_401，不计模型故障 |
| 已到冷却期的半开探测取消 | 不增加样本、释放探测；新的真实调用立即可探测并恢复CLOSED |
| 独立8081配置总超时5秒、心跳1秒 | 在供应商8秒响应前收到error/code408、FAILED、STOPPED_408，故障样本0 |

浏览器验证默认进度选项、实际阶段/等待秒数、表单禁用时取消按钮仍可点、取消无预览/无保存/无自动补发、成功后2天预览、错误清旧预览、手动普通生成、离页关闭流/清Key、账号隔离及手机无横向溢出。截图：[桌面](evidence/planner-sse/2026-09-29T12-37-08-974Z-browser/desktop-progress.png)、[手机](evidence/planner-sse/2026-09-29T12-37-08-974Z-browser/mobile-progress.png)，Key字段已遮罩。

## 所有失败与修复

| 证据/问题 | 类型与原因 | 解决及复测 |
|---|---|---|
| [最早0/1](evidence/planner-sse/20260929-122434-680862-baseline.json)、[续测0/1](evidence/planner-sse/20260929-200807-777996-initial.json) | 环境：开发服务未启动，Win10061 | 恢复Redis/后端/Vite；没有重建数据库 |
| [原功能基线3/5](evidence/planner-sse/20260929-122656-200301-baseline.json) | 功能未实现：新路径返回JSON404，没有done | 完成SSE链路；[首条流5/5](evidence/planner-sse/20260929-201048-237274-first-stream.json) |
| [环境0/2](evidence/planner-sse/20260929-200917-906220-implementation.json) | 环境：历史数据库密码已不适用，注册code500/health DOWN | 核对当前本机开发配置后重启；未改变密码，报告不保存凭据 |
| [扩展18/20](evidence/planner-sse/20260929-201605-013025-expanded-initial.json) | 产品：Accept仅SSE时，启动前异常按全局R输出无法协商JSON，出现空响应；另有测试要求显式charset，原响应仅SSE类型 | 规划专属异常处理强制JSON；SSE显式UTF-8。UUID、非法字段/日期、重复ID/并发错误全部实测通过 |
| [中间111/113](evidence/planner-sse/20260929-201752-064476-expanded-after-json-fix.json) | 测试：配额夹具key误写日期字符串，应用使用小时epoch秒，故未真正耗尽；终态断言后取code产生KeyError | 按实际Windows定义生成key，最终116/116；仅改所属夹具 |
| [浏览器17/18](evidence/planner-sse/2026-09-29T12-22-41-947Z-browser/results.json) | 测试：Element Plus原生checkbox隐藏，uncheck等待不可见元素超时 | 点击用户实际可见的label；最终通过 |
| [浏览器39/40](evidence/planner-sse/2026-09-29T12-24-57-435Z-browser/results.json) | 测试：跨轮复用模型名，使受控服务累计计数为3而非本轮2 | 每轮模型名追加唯一时间后缀，最终40/40 |
| 同轮超长分块解析耗时明显 | 产品性能：每个小分块反复正则扫描完整未结束行，扫描量随长度平方增长 | 只扫描当前新分块，行片段在换行时合并；UTF-8逐字节、LF/CRLF/CR、跨块换行、多data行、2MiB上限、缺失终态等15项通过 |

实现审查另补：终态幂等并清认证回调；断连后由容器完成，不重复写入/complete；查询也检查10分钟TTL；取消HTTP失败时中断本地流，409保留已提交结果；普通业务异常保留code；UUID生成放在try内，失败能恢复按钮。实际取消接口404注入验证本地中止与后端断连清理。TTL到期、提交瞬间的409竞争和容器异常组合属于代码审查防护，没有伪称全部通过真实并发故障注入。

截图检查发现旧全页截图把当前滚动位置的sticky导航放在画面中部，属于采集问题；先回到页首、等待登录提示消失后重拍，最终截图导航与取消控件人工核对。旧40/40轮截图仍保留，最终重拍40/40也保留。

工具中间错误也记录：脚本从错误工作目录执行导致文件不存在；大补丁格式/同一路径删除新增被apply_patch拒绝，均无应用修改；密码脚本不支持自定义label，改用final；独立实例首次命中PATH Java8而编译目标17，改用JAVA_HOME的JDK21启动。后续运行均通过。工具错误未计为产品缺陷。

## 复现与清理

正常开发后端8080、前端5173、MySQL3306和Redis6379。先看根README启动，不执行schema.sql。后端目录：

```powershell
python scripts/planner_sse_acceptance.py --label final
# 单独终端为浏览器启动受控服务：
python scripts/planner_sse_acceptance.py --serve 11437
```

生命周期脚本需另开8081，配置`trip.ai.stream.timeout-seconds=5`、`heartbeat-seconds=1`及独立`trip:test:circuit:sse-timeout`、`trip:test:quota:sse-timeout`前缀；详见开发文档。随后运行`python scripts/planner_sse_lifecycle.py`。前端目录运行`node scripts/planner_sse_browser.cjs`；依赖可由PLAYWRIGHT_MODULE指定，使用已安装Edge。普通生成回归服务为`python scripts/user_planner_acceptance.py --serve`（11435）。

所有本批账号、会话、个人AI状态与业务日志按明确fixture身份清理；个人规划没有SSE自动写入，普通浏览器保存夹具由原脚本清理。没有清空共享Redis、修改现有用户、重建库。默认全站日额度保留实测已准入次数，不伪造退款；独立短超时前缀在停止对应测试实例后清理。[最终清理核对](evidence/planner-sse/cleanup-evidence.json)确认16个证据可定位的账号名均不存在，独立前缀清空，正常开发后端仍health UP。原始服务日志与构建产物不提交，JSON响应已脱敏，失败证据保留。
