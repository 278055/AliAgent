# 多会话 Git 分支与 Worktree 协作指南

> 适用范围：AliAgent 的所有并行实施阶段。  
> 核心目标：**不同会话绝不在同一个工作目录中修改代码**；任务完成后统一进入阶段集成分支测试，测试通过后再以 PR 合入 `main`。

## 1. 强制规则

1. 根目录 `D:\Java\code\AliAgent` 仅供协调者查看状态、创建分支和准备 Worktree，**禁止实施会话直接修改**。
2. 每个实施会话必须同时拥有独立分支和独立 Worktree；不得只切换分支而继续共用根目录。
3. 一个文件、目录或公共配置在一个阶段内只能有一个任务负责人。公共文件包括根 `pom.xml`、`contracts/`、CI、共享部署配置和阶段文档。
4. 实施会话只能修改其任务卡授权的目录；发现需要改动其他任务负责的文件时，停止修改并在交付说明中提出集成请求。
5. 每个任务先在自己的 Worktree 完成测试、提交；只有集成会话可以合并任务分支。
6. 阶段集成分支通过完整测试后才允许创建合入 `main` 的 PR；禁止将单个任务分支直接合入 `main`。
7. 禁止在共享目录运行会改写文件的命令，例如格式化、依赖升级、代码生成、`git clean`、`git reset --hard` 或自动迁移。

## 2. 角色和分支命名

| 角色 | 工作目录 | 分支模式 | 权限 |
|---|---|---|---|
| 协调者 | `D:\Java\code\AliAgent` | `main` | 创建阶段分支/Worktree、分派任务、查看状态；不实施业务代码 |
| 实施会话 | `D:\Java\code\AliAgent-worktrees\p{N}-{任务}` | `codex/p{N}-{任务}` | 仅修改任务授权目录、测试、提交 |
| 集成会话 | `D:\Java\code\AliAgent-worktrees\p{N}-integration` | `codex/integration-p{N}` | 合并已验收任务、解决冲突、运行阶段测试、创建 PR |

## 3. 阶段启动：协调者操作

开始前必须确认根工作区没有未提交变更。若上一阶段仍有变更，先提交或由负责人明确处理，**不要把未提交文件带入新的并行阶段**。

```powershell
Set-Location D:\Java\code\AliAgent
git status --short
git switch main
git pull --ff-only origin main

# 创建阶段集成分支，但不在根工作区实施。
git branch codex/integration-p1 main

# 为每个任务创建独立目录和独立分支。
New-Item -ItemType Directory -Force D:\Java\code\AliAgent-worktrees | Out-Null
git worktree add D:\Java\code\AliAgent-worktrees\p1-platform-contracts -b codex/p1-platform-contracts codex/integration-p1
git worktree add D:\Java\code\AliAgent-worktrees\p1-service-skeletons -b codex/p1-service-skeletons codex/integration-p1
git worktree add D:\Java\code\AliAgent-worktrees\p1-infra-frontend -b codex/p1-infra-frontend codex/integration-p1
```

创建后用以下命令确认隔离正确：

```powershell
git worktree list
```

预期每个 Worktree 显示不同路径和不同 `codex/p1-*` 分支。若两个会话显示同一路径或同一分支，禁止启动实施，先由协调者修正。

## 4. 任务卡必须包含的内容

协调者为每个会话提供任务卡时必须明确以下信息：

```text
工作目录：D:\Java\code\AliAgent-worktrees\p1-<任务>
工作分支：codex/p1-<任务>
允许修改：<目录/文件列表>
禁止修改：<公共文件和其他任务目录>
依赖分支：codex/integration-p1
验收命令：<编译、测试、配置校验命令>
交付要求：提交 SHA、变更文件清单、验证结果、集成风险
```

会话启动后的第一条命令必须是：

```powershell
Set-Location D:\Java\code\AliAgent-worktrees\p1-<任务>
git branch --show-current
git status --short
```

分支或路径不匹配时，立即停止，不得在错误目录继续工作。

## 5. 实施会话标准流程

```powershell
# 1. 进入被分配的 Worktree。
Set-Location D:\Java\code\AliAgent-worktrees\p1-<任务>

# 2. 开始前确认分支、工作区和文件边界。
git branch --show-current
git status --short

# 3. 仅编辑授权目录，完成后检查变更范围。
git diff --check
git status --short

# 4. 执行任务卡规定的测试。
# 示例：mvn -s D:\Java_Tools\Maven\apache-maven-3.9.6\conf\settings.xml test

# 5. 提交本任务的原子变更。
git add <仅任务授权的文件>
git commit -m "feat(p1): <任务摘要>"
git push -u origin codex/p1-<任务>
```

实施会话交付时必须报告：分支名、提交 SHA、修改文件、测试命令及结果、未解决依赖或需要集成会话处理的事项。未提交的变更不允许交付。

## 6. 阶段集成与测试

所有任务分支完成并通过自身测试后，由集成会话创建专用 Worktree：

```powershell
Set-Location D:\Java\code\AliAgent
git fetch origin
git worktree add D:\Java\code\AliAgent-worktrees\p1-integration codex/integration-p1
Set-Location D:\Java\code\AliAgent-worktrees\p1-integration

# 按依赖顺序合并。
git merge --no-ff codex/p1-<任务A> -m "merge(p1): <任务A>"
git merge --no-ff codex/p1-<任务B> -m "merge(p1): <任务B>"
```

发生冲突时，只有集成会话可以处理。若冲突涉及任务语义、接口设计或目录所有权，暂停合并并要求对应任务会话提供修正提交；不要由集成会话猜测业务意图。

阶段验收命令和业务检查由对应阶段任务卡定义。端到端测试产生的资源必须使用 `test-` 或 `rag-test-` 前缀并清理。

## 7. 创建 PR 与阶段完成

集成测试通过后，集成会话推送阶段集成分支并创建唯一 PR：

```powershell
Set-Location D:\Java\code\AliAgent-worktrees\p1-integration
git push -u origin codex/integration-p1
gh pr create --base main --head codex/integration-p1 --title "feat(p1): <阶段摘要>" --body "阶段集成测试已通过，详见 PR 检查项。"
```

PR 必须包含：任务分支/提交列表、集成测试结果、未覆盖风险、数据库测试数据清理结果。PR 合入 `main` 后，下一阶段只能从更新后的 `main` 创建新的阶段集成分支。

## 8. 清理 Worktree

确认 PR 已合入且不再需要回溯时，由协调者执行：

```powershell
Set-Location D:\Java\code\AliAgent
git worktree remove D:\Java\code\AliAgent-worktrees\p1-<任务A>
git worktree remove D:\Java\code\AliAgent-worktrees\p1-<任务B>
git worktree remove D:\Java\code\AliAgent-worktrees\p1-integration
git branch -d codex/p1-<任务A>
git branch -d codex/p1-<任务B>
git branch -d codex/integration-p1
git worktree prune
```

若需要保留分支用于排障，不删除对应分支，但仍应移除闲置 Worktree。禁止使用 `git worktree remove --force` 清理含未提交变更的目录；应先由任务负责人提交、导出补丁或明确放弃。

## 9. 禁止行为与异常处理

| 情况 | 必须动作 |
|---|---|
| 发现自己位于根工作区或其他任务 Worktree | 立即停止，切换到分配目录后再继续。 |
| 需要修改未授权的公共文件 | 不修改；记录集成请求，由文件负责人或集成会话处理。 |
| 任务开始前 `git status` 非空 | 不覆盖、不清理；确认变更来源后由协调者处理。 |
| 需要更新阶段基线 | 新建修正任务或由集成会话单独提交，不能夹带在功能任务中。 |
| 任务测试失败 | 保留失败信息和提交前状态；不得把失败或临时调试文件合入集成分支。 |
| 数据库/Compose 产生共享状态冲突 | 为测试资源使用唯一 `test-<任务>-<时间戳>` 前缀，并由创建者清理。 |
