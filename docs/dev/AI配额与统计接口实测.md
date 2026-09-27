# AI配额与统计接口实测

日期：2026-09-27。后端8080、Vue5173、MySQL8、Redis本机实例、Edge无头浏览器。模型为本机可控HTTP协议服务，未接真实LLM。本报告记录实际响应；全局用量是测试当时快照，不是固定初始值。

## 结果与原始证据

| 验证 | 最终结果 | 证据 |
|---|---|---|
| HTTP/MySQL/Redis，包括Lua并发 | 70/70 | [最终响应](evidence/ai-quota/20260927-183532-834567-http-redis.json) |
| 扩展非标准数字校验（失败） | 68/70 | [边界失败](evidence/ai-quota/20260927-183445-946304-http-redis.json) |
| 中间HTTP/Redis回归 | 66/66 | [历史通过](evidence/ai-quota/20260927-182529-456179-http-redis.json) |
| 首轮HTTP/Redis | 65/66 | [保留失败](evidence/ai-quota/20260927-182446-361929-http-redis.json) |
| Edge配额页面 | 11/11 | [最终结果](evidence/ai-quota/2026-09-27T10-27-50-401Z-browser/results.json)、同目录桌面/手机截图 |
| 首轮Edge | 10/11 | [保留失败](evidence/ai-quota/2026-09-27T10-26-54-705Z-browser/results.json) |
| 原用户模型HTTP/SQL回归 | 50/50 | [本轮回归](evidence/user-planner/20260927-182708-643729-http.json) |
| 原用户模型Edge回归 | 22/22 | [本轮回归](evidence/user-planner/2026-09-27T10-27-14-880Z-browser/results.json) |
| Maven | 22/22（原16+新增6） | [构建摘录](evidence/ai-quota/build-results.txt) |
| TypeScript/Vite构建 | 通过；仍有大包警告 | 同上 |

70项是脚本断言数，包含接口调用、数据库/Redis断言和清理断言，不代表66个不同接口。Redis故障为单元测试注入异常，未停止用户正在使用的Redis。小时/午夜边界用固定时间单测；页面“重置”通过删除专用临时账号的小时键模拟，没有等待真实整点。

## 实际接口响应

以下响应选自18:25的中间回归证据；后续复测的时间和全局用量不同。`GET /api/ai/planner/usage`，当前用户完成连接测试、一次需要重试的生成和一次上游认证失败后，实际HTTP200、业务code200，data为：

```json
{
  "quota": {
    "hourly": {"limit":20,"used":4,"remaining":16,"resetAt":"2026-09-27T19:00+08:00"},
    "daily": {"limit":200,"used":4,"remaining":196,"resetAt":"2026-09-28T00:00+08:00"},
    "globalDaily": {"limit":2000,"used":24,"remaining":1976,"resetAt":"2026-09-28T00:00+08:00"},
    "timeZone":"Asia/Shanghai"
  },
  "today":{"operations":3,"succeeded":2,"failed":1,"averageCostMs":4.0}
}
```

这里4次是上游请求准入次数，3次是用户操作次数，差值来自结构重试。全局24包含其他测试账号的已准入请求。规则解析和基础匹配后用户used仍为4。

填充到只剩1次后，让模型返回错误JSON：第一次上游请求消耗最后1次，第二次尝试在后端被拒绝。实际HTTP200、业务code429：

```json
{"code":429,"message":"本小时AI调用额度已用尽，请在额度重置后重试","data":null}
```

上游接收计数证明本次只发了1个请求。再调用测试/生成均返回429且上游总数仍20；统计为19次操作、成功17、失败2，没有为纯配额拒绝制造模型调用日志。

另一个临时账号小时已用0、日计数夹具设200，其测试连接返回业务429、提示“今日AI调用额度已用尽”；小时键仍不存在，上游计数不变。传`?userId=其他账号`查询usage不能读取别人用量，后端只采用JWT用户ID。

`GET /api/admin/ai/recommend/log?userId=10068&size=2`，专用ADMIN账号实际返回HTTP200、业务200，total20、records2、pages10。记录仅含id/userId/scene/model/costMs/success/isFallback/createTime；不返回Key、请求正文、API地址或上游错误。普通用户实际HTTP403/code403；匿名usage为HTTP401/code401；管理员size101为HTTP200/code400。

total20包括19条用户模型操作日志和1条免费规则解析日志。管理员日志覆盖已有场景；个人统计只统计USER_MODEL_TEST/USER_PLANNER，二者口径不同。

## 原子性与失败分支

- 对隔离键发40个并发Lua请求，小时限额7，实际只放行7；三维计数都为7。其余请求没有部分扣减。
- 小时阈值调高，在相同隔离数据上分别触发日限额、全局日限额；返回维度2/3，其他计数保持7。
- 计数过期时间有界；在阈值高于当前用量的场景注入1.5、1.0、01后均报INVALID_QUOTA_COUNTER，已检查的小时计数没有先被扣减。
- 单测验证北京时间23:59:59→00:00:00三维键变化；普通整点只有小时键变化，不同用户仅共用全局键。
- 单测验证非法配置、Redis空返回/连接异常返回503；配额失败不调用模型、不插入假日志，并且释放并发占位以允许后续请求。

没有做Redis Cluster真实部署、生产压力/故障切换、真实服务计费或模型质量测试。

## 本轮问题、原因与处理

| 问题 | 原因 | 处理与复验 |
|---|---|---|
| 首轮基础匹配检查返回400 | 新测试脚本写成`criteria`，现行接口字段是`intent` | 修正测试请求；未放宽业务契约；65/66→66/66 |
| 首轮页面找不到“使用基础旅行推荐”链接 | 新额度提示建议使用基础推荐，但未提供直接入口 | 补充RouterLink到/recommend；10/11→11/11，手机无横向溢出 |
| 切换账号可能短暂显示旧用量（代码检查发现） | 页面watch原本只清理模型Key和地址 | 同时清空usage并刷新；异步刷新按账号检查后再回填 |
| Lua计数字符串校验遗漏（扩展实测68/70） | tonumber接受1.0/01，但Redis INCR拒绝；原数值整数校验未阻止这些格式，存在部分扣次风险 | 改为先验证三个原始值均为规范非负十进制整数，再判断额度；低于阈值时注入1.5/1.0/01均报错且无部分扣次，最终70/70。原失败轮在小时已满时观察到的是没有按预期拒绝坏计数格式，未实际执行部分INCR |

本轮没有观察到真实模型缺陷，因为未连接真实模型。构建中的约1.12MB主包警告仍未解决；Mockito动态agent提示为工具链提示，测试通过。

## 复现与数据清理

先启动MySQL、Redis、8080后端和5173前端，数据库需有USER_PLANNER模板。不要重建数据库。

```powershell
# trip-server目录，脚本自行启动并关闭随机端口模型夹具
python scripts/ai_quota_acceptance.py
python scripts/user_planner_acceptance.py
mvn test

# 为浏览器验收另开终端启动固定端口夹具，验收后Ctrl+C关闭
python scripts/user_planner_acceptance.py --serve

# trip-web目录
node scripts/ai_quota_browser.cjs
node scripts/user_planner_browser.cjs
npm run build
```

Python脚本需要mysql和redis-cli在PATH；浏览器脚本需要Playwright和Edge，可通过PLAYWRIGHT_MODULE指定Playwright路径。数据库密码沿用MYSQL_PASSWORD本机配置，证据脱敏token/password/apiKey。新增配额脚本仅删除自建账号、其日志/偏好/Redis用户键，以及唯一命名的Lua测试键。原规划回归脚本产生的配额用户键按窗口自然到期。

**共享全局计数不回滚**：应用对可控服务发出的验收请求确实通过准入并消耗配额；清理时不重置全局键，避免覆盖并发的真实用户请求。本次四轮配额各20次、HTTP回归10次、原浏览器3次、两轮新浏览器各1次，合计95次；当天之后实测会继续增加。若当前全局额度不足，请在隔离部署执行验收，不能为让脚本通过清空真实计数。
