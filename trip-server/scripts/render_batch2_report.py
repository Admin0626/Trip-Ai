"""Render the real saved responses as Markdown; never fabricate test results."""
import json
from pathlib import Path

root=Path(__file__).resolve().parents[2]
dev=root/'docs/dev'
before=json.loads((dev/'evidence/baseline/responses.json').read_text(encoding='utf-8'))
after=json.loads((dev/'evidence/regression/responses.json').read_text(encoding='utf-8'))
browser=json.loads((dev/'evidence/browser-regression/results.json').read_text(encoding='utf-8'))
cases=after['cases']
lines=['# 第2批接口实测与缺陷修复记录', '', '> 测试日期：2026-09-26（Asia/Shanghai）。本文由真实 HTTP 响应、SQL 断言和真实 Edge 浏览器运行结果整理。不是设计示例，也不是仅阅读代码后的推断。', '',
'## 1. 结论与证据', '',
f'- 修复前：{len(before["cases"])} 项检查，{sum(x["passed"] for x in before["cases"])} 项通过，21 项未达预期。失败检查数不等于独立缺陷数，同一个根因可能触发多项失败。',
f'- 修复后：{len(cases)} 项接口/数据检查全部通过（包含注册、登录、夹具清理、并发结果和 SQL 断言；不是 {len(cases)} 个不同接口）。新增专项回归未在旧版本执行，不能据此声称它们旧版必然失败。',
f'- 浏览器：{len(browser["rows"])} 项检查全部通过。真实 Vite 代理 + 后端 + MySQL，没有拦截替换接口响应。',
'- `mvn test`：1 个应用上下文测试通过。它不是业务覆盖率证明；业务证据来自本报告 HTTP/SQL 和浏览器测试。',
'- `npm run build`：类型检查与生产构建通过；仍有主包约 1.12 MB 的体积警告。',
'- 原始数据：[修复前](evidence/baseline/responses.json)、[最终回归](evidence/regression/responses.json)、[浏览器修复前](evidence/browser-baseline/results.json)、[浏览器最终回归](evidence/browser-regression/results.json)。JWT 和密码已在落盘时脱敏。',
f'- 最终接口运行时间：`{after["time"]}`；浏览器运行时间（UTC）：`{browser["time"]}`。', '',
'## 2. 环境、请求约定与数据保护', '',
'- 本机 Windows；Spring Boot 4.0.8；Maven 3.9.14 实际使用 JDK 21.0.2（项目编译目标 Java 17）；MySQL 8；Vue 3/Vite 8。',
'- 后端 `http://localhost:8080/api`，前端 `http://127.0.0.1:5173`。第2批启动使用 `--spring.ai.model.chat=none`，不调用真实大模型。',
'- 业务异常沿用统一 R 响应：通常 HTTP 200 + `code=400/403/409/2002...`；Security 层明确返回 HTTP 401/403 + 同名业务 code。测试分别记录 HTTP 状态与业务 code，不把 HTTP 200 等同于业务成功。',
'- 接口脚本每轮创建 3 个独立测试用户和 3 条临时路线。预约日期按运行日动态生成，避免历史日期失效。数据库仅用于构造隔离夹具、核验持久化和清理；所有被测业务操作均走真实 HTTP。',
'- `finally` 清理只删除本轮创建的用户及关联数据，不重建数据库，不重置演示数据。浏览器使用独立账号，在路线201上执行成对点赞/取消、收藏/取消、预约/取消、评论/删除；清理后重算所触及路线计数。',
'- 自增 ID 会产生空洞，逻辑删除留下的测试数据最终被夹具清理。测试不是生产环境压测。', '',
'## 3. 缺陷台账（现象 → 原因 → 修复 → 证据）', '',
'| 编号 | 实际现象 | 根因 | 解决方案与验证 |',
'|---|---|---|---|',
'| B2-01 | 匿名请求规划返回 HTTP403；USER 管理接口响应不是统一 R | Security 未配置认证入口和拒绝处理器 | 匿名401、权限不足403，均输出 JSON code；两项回归通过 |',
'| B2-02 | 缺 routeId、非法路径参数返回 code500 | 参数绑定异常进入通用兜底 | 显式处理缺参数/类型不符/方法校验异常，返回400 |',
'| B2-03 | size=-1 被接受 | 仅限制分页上限，没有下限 | 互动、规划分页校验 current/size ≥1 |',
'| B2-04 | 同一用户并发取消点赞有一个 code500 | 两个事务同时查到同一记录，删除后各自减计数，unsigned 字段可能下溢 | 路线行锁后读取状态，串行切换与原子加减；收藏同路径修复，评论点赞使用当前读；并发回归通过 |',
'| B2-05 | 已确认3人、名额3人，另1人仍可提交 | selectCount 统计订单数，不是 SUM(people_num) | 当前读已确认订单并累加人数，提交返回2002 |',
'| B2-06 | 两个2人订单并发确认，在3人名额下都成功 | 审核入口没有名额校验，也没有共享路线锁 | 审核与提交共享路线锁，确认前重检；结果200/2002，确认人数2 |',
'| B2-07 | 管理员取消后 booking_count 与明细不一致 | 审核仅改状态，未同步计数/取消时间 | 管理员取消也减计数、记时间；用户取消在同锁内重读，防重复扣减 |',
'| B2-08 | 可跨路线回复、可回复隐藏评论 | 只检查父评论是否顶级，没检查所属路线和显示状态 | 父评论必须同路线、显示中且顶级；两项400 |',
'| B2-09 | 隐藏请求缺 status 返回500 | Controller 缺 @Valid，Integer 拆箱空指针 | 补 @Valid，Service 空值兜底；返回400 |',
'| B2-10 | 隐藏1星后平均分仍3.67，应5.00 | 隐藏/恢复时不重算评分 | 同事务同路线锁下重算显示中未删除评论均分；回归5.00 |',
'| B2-11 | 5个并发评论均写入，突破每日3条 | 先查再插未互斥 | 路线锁内执行限额检查、插入和计数；实测仅3条 |',
'| B2-12 | 有空天的草稿保存失败 | 每天至少一项的规则也应用于草稿 | 正式保存严格校验，草稿允许空天；草稿200，正式空项仍400 |',
'| B2-13 | days=2但提交5天、status=9也能保存 | 缺少结构一致性和枚举范围校验 | 天数与非空 dayList 长度一致，status只能0/1 |',
'| B2-14 | null天、null项、过长天标题返回500 | 集合元素未@NotNull，嵌套字段未限制长度 | 容器元素校验+天标题/摘要/时间点长度，全部400，数据库写入前拒绝 |',
'| B2-15 | 浏览器详情页失败，错误 tags.split is not a function | 实际 tags 是数组，前端类型声明和处理按字符串 | RoutePageVO.tags改为string[]，卡片/详情直接渲染数组；详情目的地读destination.name |',
'| B2-16 | 编辑后保存住宿/餐食/每日摘要变空 | loadDetail/buildDTO 漏映射字段；后端整体替换因此删除原值 | 补齐三个字段双向映射；浏览器保存再查库三项保持原值 |',
'| B2-17 | 编辑区挤在300px侧栏，标题输入框不可见 | 三张卡片自动填充两列Grid，基本信息没跨列；行程行过多固定宽控件 | 基本信息跨列，行程区占主列，条目使用可伸缩Grid；修复前后截图可对照 |',
'| B2-18 | 文案说支持跨天拖动，但页面无可用目标 | 只渲染当前一天的draggable，共用group不会凭空产生另一天列表 | 加持久可见的跨天投放区和拖拽手柄；真实鼠标将景点1移到第2天，保存重开一致 |',
'| B2-19 | 前端允许规划50人、预约20人，后端只允许10人 | 前后端约束不一致 | 两处max=10，预算下限0.01、天数最多30；浏览器核验aria-valuemax=10 |',
'| B2-20 | unknown被显示成中评；筛选值使用大写 | 后端枚举是小写positive/neutral/negative/unknown | 改用小写，unknown不显示情感结论；浏览器校验新评论不显示中评 |',
'| B2-21 | 用户页缺评论回复/删除/点赞入口，评论只能看首页 | 后端已具备但页面未接入 | 补回复对象提示、本人删除、点赞、分页、图片列表展示；发表/回复/删除浏览器实测通过 |',
'', '### 审查补强（与“旧版实测失败”分开）', '',
'- 预约号原为“当天最大流水+1”，不同路线锁不能互斥这段逻辑。改为32位随机 UUID 字符串，数据库唯一索引继续兜底。前端只展示单号，不解析格式；API 示例同步。新增并发跨路线预约验证两个号不同。此风险通过代码发现，未声称旧版已复现碰撞。',
'- 规划50条上限增加用户行锁与当前读，同时覆盖创建/复制/模板入口。旧版该轮恰好未超限，仍存在先查后插竞争；最终49条时并发创建只允许到50条，三个入口超限拒绝。',
'- 规划更新先锁主表，避免两个“先删后插”交错；复制200字标题时截断原始部分再加后缀；收藏列表过滤已删除路线，避免空引用。这些是代码审查补强，未做对应旧版失败复现。',
'- 从路线生成规划后立即替换为 `/plan/{id}/edit`，避免刷新创建第二份。此项代码修复，最终专项浏览器覆盖见开发文档；不要将API模板成功等同于刷新专项已测。', '',
'## 4. 全部最终检查清单', '',
'“SQL/assert”是与真实数据库/并发返回结果比较的断言；HTTP请求的完整请求体和响应体见原始JSON。', '',
'| # | 场景 | 方法与路径 | HTTP | 业务code/断言结果 | 结论 |',
'|---|---|---|---|---|---|']
for i,c in enumerate(cases,1):
    obj=c.get('response'); val=obj.get('code') if isinstance(obj,dict) else obj
    if val is None: val='见原始响应'
    lines.append(f'| {i} | {c["name"]} | `{c.get("method", "")} {c.get("path", "")}` | {c.get("http","—")} | `{json.dumps(val,ensure_ascii=False)}` | {"通过" if c["passed"] else "失败"} |')
lines += ['', '## 5. 修复前后真实响应对照', '', '以下保留实际字段、时间戳和测试ID。SQL断言也保留原始值。测试ID已清理，复跑会生成新ID。']
for c in before['cases']:
    if c['passed']: continue
    matching=[x for x in cases if x['name']==c['name']]
    # Racing requests may complete in opposite order; both pass after fix.
    lines += ['', '### '+c['name'], '', f'请求：`{c.get("method", "SQL/assert")} {c.get("path", "")}`；角色：`{c.get("user","数据断言")}`。', '', '修复前：', '```json', json.dumps({'http':c.get('http'),'response':c['response']},ensure_ascii=False,indent=2), '```']
    if matching:
        a=matching[0]
        lines += ['', '修复后：', '```json',json.dumps({'http':a.get('http'),'response':a['response']},ensure_ascii=False,indent=2),'```']
lines += ['', '## 6. 浏览器实际结果', '', '| 场景 | 实测 | 结论 |','|---|---|---|']
for c in browser['rows']:
    lines.append(f'| {c["name"]} | `{json.dumps(c.get("actual"),ensure_ascii=False)}` | {"通过" if c["passed"] else "失败"} |')
lines += ['', '修复前编辑器：', '', '![修复前](evidence/browser-baseline/plan-editor.png)', '', '修复后编辑器：', '', '![修复后](evidence/browser-regression/plan-editor.png)', '',
'## 7. 复跑方法', '',
'先启动本地MySQL，确认已有演示库。不要重新执行schema.sql，否则会清空现有数据。', '',
'```powershell', '# 终端1：后端', 'cd D:\\demo_series\\Trip-AI\\trip-server', 'mvn spring-boot:run \'-Dspring-boot.run.arguments=--spring.ai.model.chat=none\'', '', '# 终端2：前端', 'cd D:\\demo_series\\Trip-AI\\trip-web', 'npm run dev -- --host 127.0.0.1', '', '# 终端3：真实接口验收', 'cd D:\\demo_series\\Trip-AI\\trip-server', "$env:PYTHONIOENCODING='utf-8'", 'python scripts/batch2_acceptance.py regression', '', '# 浏览器验收：需本机Edge和Playwright；可用PLAYWRIGHT_MODULE指定安装位置', 'cd D:\\demo_series\\Trip-AI\\trip-web', 'node scripts/batch2_browser.cjs regression', '', '# 更新本报告（只读取已存在证据，不执行测试）', 'cd D:\\demo_series\\Trip-AI\\trip-server', 'python scripts/render_batch2_report.py', '```', '',
'脚本默认使用本机开发数据库账号；可通过MYSQL_PASSWORD覆盖密码，通过TEST_BASE_URL覆盖接口地址。浏览器脚本默认使用本机已存在的Playwright，不会自动安装依赖。回归失败返回非零退出码。不要使用baseline参数覆盖本次保留的修复前证据。', '',
'## 8. 工具问题与未覆盖边界', '',
'- Maven首次启动因工作区外缓存写权限失败；授权后正常启动。离线mvn test因Surefire未缓存失败，联网获取依赖后1项测试通过。Vite沙箱子进程失败，授权后开发服务和构建正常。以上属于执行环境问题，不计为业务Bug。',
'- 最初接口脚本误取预约响应id而不是bookingId，已按真实VO更正；最初浏览器定位使用“用户名”而实际placeholder为user1001，已修正。最终证据排除这些测试脚本错误。一次失败创建的独立测试用户已按确切ID清理。',
'- 本次是本机功能/边界/小规模并发验收，不是高并发性能、跨浏览器兼容或生产安全审计。并发测试通过不意味着所有调度均被穷举。',
'- 评论接口三张图片URL存储/回显已实测；前端可展示已有图片，文件选择上传入口尚不在此次交付内。管理端接口已测，完整后台页面仍在后续批次。AI优化按约定返回3001占位，不算已实现AI。',
'- 前端主包体积警告、JDK动态Agent提示以及旧MyBatis-Plus弃用提示不影响本轮验收，后续按需处理。没有通过调高告警阈值隐藏体积问题。', '']
(dev/'第2批接口实测与缺陷修复记录.md').write_text('\n'.join(lines),encoding='utf-8')
print('Generated report from',len(cases),'real records')
