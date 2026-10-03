# Trip-AI 自然纯净主题：Figma 与前端实现

2026-10-03。先在用户选择的个人团队创建可编辑 Figma 样式图，再取得设计上下文，按现有 Vue 3 / Element Plus / SCSS 实现。

[Figma 设计文件](https://www.figma.com/design/KpiEIIPPgKqGBQ6fbx2ZjA) · [设计规范](../../DESIGN.md)

## 页面对应

| 页面 | 桌面节点 | 手机节点 | 实现 |
|---|---|---|---|
| 首页 | 5:35 | 5:36 | Home.vue |
| 旅行助手 | 5:37 | 5:38 | TravelAssistant.vue / AiPlanner.vue / Recommend.vue |
| 路线详情 | 5:39 | 5:40 | RouteDetail.vue |
| 我的规划 | 5:41 | 5:42 | PlanList.vue |

图层使用原生文字、自动布局、变量绑定和本地组件实例；包含森林绿按钮、表单、路线卡片和自然矢量插画。没有将整页截图作为设计图。现有 SDS 的 Inter 字体与项目品牌不一致，因此使用本地 Noto Sans SC 组件和令牌。

## 实现行为

- 米白背景、森林绿主色、中文字体、统一圆角和焦点样式；共享导航、页脚、表单和已有页面配色一起统一。
- 首页保留真实热门目的地、精选路线、热门路线和活动轮播数据；主操作进入旅行助手。各数据加载独立处理失败，可重新加载；轮播默认手动，可开启自动播放。
- 旅行助手在桌面采用双栏配置和需求表单，手机单栏；原模式切换、查询保留、切换清 Key、额度、SSE 和确认保存逻辑保留。
- 路线详情文字与封面分栏，价格和操作独立成区；原预约、点赞、收藏、每日行程和评论入口保留。
- 我的规划保留编辑、复制、导出和删除操作，空状态接入自然插画。
- `public/figma-landscape.svg` 是设计上下文返回的矢量导出，运行时直接复用；`nature-landscape.svg` 为创建设计所用的源图。业务封面仍来自接口，加载时有提示，加载失败时卡片回到文字布局；详情页保留插画与不可用说明，不伪装为目的地照片。

## 实测与边界

- 最终 `npm run build`：vue-tsc 和 Vite 通过。原主包 1129.89 kB，大于 500 kB 的提示保留；没有声称 CI 通过。
- 四个关键页面在 1440、1024、390、320px 宽度检查，无横向溢出。实际页面几何和交互记录见 [browser-checks.json](qa/browser-checks.json)。
- 实际验证：首页 CTA；AI 缺 Key 的内联提示；切换模式保留需求并清 Key；手机菜单展开和跳转收起；路线预约弹窗打开/取消；新建规划进入原编辑器。
- 浏览器使用当前登录会话读取真实数据；没有保存草稿、提交预约、删除记录或调用真实模型。当前账号有两条规划，所以实测的是有数据列表；空状态实现已构建，但未清空真实记录测试。
- 本次是前端布局与主题改造，无后端/API/SQL 变更；未重跑后端完整回归。外部业务图片在本机存在加载失败，证据反映实际状态。
- Figma Starter 工具额度在最后细调时达到限制，八张图已建立且四个桌面页上下文已取得。规划空状态插画实例在 Figma 中仍有裁切，前端使用完整 SVG。路线插画实例也存在缩放裁切差异；不能称为全部画板最终像素验收完成。
- `figma-home-desktop.png`、`figma-assistant-mobile.png`、`figma-composition.json` 是制作过程中的截图/结构快照，助手手机截图采集早于输入框换行修复。当前结果以 Figma 文件及 `qa/` 实际前端截图为准。
- 本地 Vite 曾保留旧组件转换缓存，触发文件监听后已刷新，并以浏览器实际组件重新验证；手机导航验证曾因按钮展开后名称改为“收起导航”而定位超时，读取状态后按真实名称完成验证。

## 证据

设计变量及组件 ID：`figma-foundations.json`；制作状态：`figma-state.json`；四页设计上下文：`figma-*-context.txt`。

前端截图：`qa/home-desktop.jpg`、`qa/assistant-desktop.jpg`、`qa/route-desktop.jpg`、`qa/plans-desktop.jpg`，以及对应 `*-mobile.jpg`。

分支 `codex/nature-design` 从首页留白分支继续。发布核对确认 PR #8 已合并到 main=e8c60cd，因此同步该 main 合并提交，无文件冲突或新增代码差异。[设计 PR #9](https://github.com/Admin0626/Trip-Ai/pull/9) 目标 main，保持 open、未合并；比较差异仅包含本批设计实现和文档。用户原有 docs/README.md、实训报告和答辩材料不纳入设计提交。
