# Git分支与合并流程

生效：2026-09-29。用户明确要求：先记录当前基础项目，新的功能先上传新的分支，之后再融合。后续任务遵循本文件及SESSION。

## 版本和分支

| 名称 | 用途 |
|---|---|
| `main` | 已验收的集成版本；新功能经PR合并，不直接在main持续开发 |
| `v0.1.0-baseline` | 当前基础功能固定标签及GitHub Release，后续不移动、不覆盖 |
| `codex/ai-planner-sse` | AI规划SSE进度及取消已交付；PR #1已审核合并，main集成9d58768 |
| `codex/rag-knowledge` | 从9d58768建立，本地知识管理/分片/检索；[PR #2](https://github.com/Admin0626/Trip-Ai/pull/2)已审核合并，main集成d63cb66 |
| `codex/knowledge-sessions` | 从d63cb66建立，本人本地检索会话/历史；146项接口/55项Edge通过，fb797ed已推送，[PR #3](https://github.com/Admin0626/Trip-Ai/pull/3)开放待融合 |
| `codex/<feature>` | 其他新功能各建独立分支，例如`codex/rag-chat`；从当时最新main起步 |

这是协作工作约定。本次没有配置GitHub服务器分支保护或强制检查，不能宣称GitHub已经从技术上阻止直接push main。

## 每批操作

1. 先读SESSION，检查`git status`与当前分支，保存现有工作；不要用强制切换或重置丢弃未提交修改。
2. 新功能从已同步的main建立一个分支。若当前已经处于适合该功能的分支，继续使用，不重复创建。
3. 在功能分支实现和提交，及时写进度/目标/问题及SESSION，然后推送该分支。未完成也可保存进度提交，不能写成已验收或合并main。
4. 做与修改相关的真实接口/浏览器及构建回归；记录失败、原因、方案、证据及清理。补开发/答辩文档，创建目标为main的PR，按[PR模板](../../.github/pull_request_template.md)说明最终行为和验证边界。
5. 功能完成并满足验收要求后，通过PR融合main；有待解决的失败/冲突时保留在功能分支。不要强推覆盖他人的提交。
6. 合并后同步main，下一项另建新分支。需要新的阶段快照时使用新标签，保留旧基线可检出。

## 命令示例

```powershell
# 新功能开始，先确保已有修改已妥善保存
git switch main
git pull --ff-only origin main
git switch -c codex/feature-name

# 实现、实测、补文档后，提交相关文件
git add <相关文件>
git commit -m "feat: describe the completed change"
git push -u origin codex/feature-name
```

当前私人资料会话使用`codex/knowledge-sessions`，不混入已合并的SSE或知识分支。PR可使用GitHub网页的Compare & pull request；CLI可用时也可从文件提交PR正文。GitHub尚未配置CI检查，审核依据本地实测证据，不把没有检查称为CI通过。旧基线不移动。

查看固定基线可用GitHub标签页面；本地临时查看可`git switch --detach v0.1.0-baseline`，这不恢复本机数据库。继续开发时切回对应功能分支。
