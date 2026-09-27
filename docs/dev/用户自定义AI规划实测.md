# 用户自定义AI规划实测

日期：2026-09-27。**这是实际运行的Trip-AI接口、浏览器和MySQL测试，模型供应商由本机可控HTTP服务代替。没有使用真实API Key，不是云端大模型或本机大模型的质量验收。**

## 本轮结果

| 项目 | 结果 | 证据 |
|---|---|---|
| 首轮HTTP/SQL | 44/44通过，尚未覆盖隐式类型转换 | [初轮](evidence/user-planner/20260927-170049-277000-http.json) |
| 补充类型边界后 | 44/48通过，发现4个隐式转换案例 | [失败基线](evidence/user-planner/20260927-170143-656960-http.json) |
| 类型修复后 | 48/48通过 | [回归](evidence/user-planner/20260927-170415-184591-http.json) |
| 最终HTTP/SQL（增加日期校验） | 50/50通过 | [最终](evidence/user-planner/20260927-170804-349593-http.json) |
| 最终浏览器 | 22/22通过，0个未处理页面异常 | [结果](evidence/user-planner/2026-09-27T09-08-10-457Z-browser/results.json) |
| Maven | 16/16通过（新增7项+原有9项） | [命令日志摘录](evidence/user-planner/build-results.txt) |
| 前端类型检查与生产构建 | 通过，仍有约1.12MB主包警告 | 同上 |

## 实际请求与响应

接口脚本在随机本机高位端口启动独立兼容服务，使用真实JWT访问Trip-AI后端。请求API Key只使用明确的测试占位值，证据递归脱敏password/apiKey/accessToken/refreshToken等字段。

`POST /ai/planner/test`实际返回HTTP200/code200、connected=true，测试服务确认收到正确路径`/v1/chat/completions`与Bearer头。`POST /ai/planner/generate`请求2天、预算3000、2人，实际返回2天、每天2项活动的预览；source=USER_MODEL，model=fixture-valid，attempts=1。此处USER_MODEL表示经过用户配置的协议适配器，不表示fixture本身是真实模型。

让服务第一次返回非法JSON后，接口第二次返回有效预览，attempts=2且真实上游请求数=2；连续非法JSON最多2次后返回code3004。401认证失败只请求1次，不能无意义消耗第二次额度。302重定向被拒绝，没有跟到目标地址；超过1MiB的响应被中断。

供应商401正文故意包含测试Key，前端API响应没有泄露这段正文。URL中的userinfo/query、非可信主机、元数据地址、低位本机端口和已经包含/chat/completions的地址均被拒绝。API Key中的CRLF注入被参数校验拒绝。

JSON结构校验测试覆盖：未知费用null、模型注入attractionId被重置为0、天数/条目要求和多种费用非法值。预览后数据库规划数量仍为0；单独发送确认保存请求后恰好生成一个草稿，再查询原有规划详情验证两天保留。测试账号、草稿、条目与日志均按本次ID清理。

## 浏览器流程

真实Edge经Vite代理调用应用后端，再转发到本机11435端口的测试服务。没有使用Playwright伪造接口响应。覆盖：填写用户地址/模型/Key、密码遮罩、只记住地址和模型、Key不出现在localStorage/sessionStorage、URL查询参数中的凭据不能保存、真实连接测试、生成两天预览、未确认禁用保存、确认后进入现有编辑器并回显生成标题。

再次打开及刷新页面：地址保留，Key为空。切换第二个账号：地址与模型均为空，未继承前一个账号。认证失败返回提示且不展示上游含密钥正文。390px手机无横向溢出。

- [桌面截图](evidence/user-planner/2026-09-27T09-08-10-457Z-browser/desktop.png)
- [手机截图](evidence/user-planner/2026-09-27T09-08-10-457Z-browser/mobile.png)

截图中的粉色区域是验收工具对API Key输入框的额外遮罩，实际页面是密码输入框。内容中的“可控服务”来自专用测试夹具，不是产品内置的假行程。

## 问题、原因与修复

| 编号 | 现象 | 原因 | 修复与结果 |
|---|---|---|---|
| UP-01 | days=1.5、days="2"、query=12345、budget="3000"本应拒绝却code200；44/48 | Jackson默认将小数转整数、标量和数字字符串自动转型，Bean Validation发生在转换之后 | 增加仅作用于Planner DTO的StrictPlannerJson反序列化；修复后48/48，最后含日期的50/50通过 |
| TEST-01 | 浏览器10/11，确认勾选框不可见，30秒超时 | Element Plus隐藏原生checkbox，由可见标签承接交互，脚本定位原生输入 | 改为真实点击可见标签并断言保存按钮启用 |
| TEST-02 | 浏览器20/21，编辑器标题为空但后端保存成功 | 脚本在规划详情异步加载完成前读取输入框 | 等待真实回填后读取生成标题；21/21通过，补URL凭据检查后22/22 |
| TEST-03 | 编写阶段发现原拟定标题断言count>=0恒真、无验证价值（未作为最终结果） | 定位错误并使用了无效断言 | 删除该断言，改为等待并严格比较编辑器标题与模型响应title |
| ENV-01 | 清理浏览器测试夹具时Get-CimInstance拒绝访问 | 沙箱禁止查询进程元数据 | 授权后仅匹配本轮脚本的Python进程并停止11435夹具；应用前后端未停止，最终后端健康UP |

API Key不落盘与响应大小/主机限制是本轮主动设计的保护，不将未发生的泄漏或攻击编造成已发现漏洞。

## 复现

前置：运行8080后端、5173前端、MySQL/Redis；已执行`upgrade_user_planner.sql`。Maven使用JDK21，项目编译目标17；前端Node24。

```powershell
cd D:\demo_series\Trip-AI\trip-server
mvn test
python scripts/user_planner_acceptance.py

# 浏览器测试前在单独终端启动协议夹具（不是模型）
python scripts/user_planner_acceptance.py --serve

# 另一终端
cd D:\demo_series\Trip-AI\trip-web
node scripts/user_planner_browser.cjs
npm run build
```

浏览器脚本沿用本机Playwright路径，可通过PLAYWRIGHT_MODULE覆盖。证据文件按时间戳新增，不覆盖失败记录。接口脚本自启服务自动关闭；浏览器夹具需测试完成后停止。不要把11435测试服务当作可供用户使用的真实模型。

## 未验收范围

真实供应商的Key权限、模型兼容程度、输出质量与费用；生产多节点限流、熔断和计费；真实模型30天行程、SSE流式生成、RAG、现有规划的就地AI优化。当前每次生成上限14天。没有把规则匹配、测试夹具或旧SSE设计宣称为这些功能已完成。
