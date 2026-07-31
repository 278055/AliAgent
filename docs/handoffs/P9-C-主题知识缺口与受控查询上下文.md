# P9-C 主题、知识缺口与受控查询上下文

## 任务卡

- 工作目录：`D:\Java\code\AliAgent-worktrees\p9-insight-query-ui`
- 工作分支：`codex/p9-insight-query-ui`
- 允许修改：`services/insight-service/src/main/java/com/bn/aliagent/insight/{topic,gap,query}/`、对应测试，以及 `frontend/apps/aliagent-admin/src/insight/`。
- 禁止修改：公共契约、Flyway、POM、YAML、根包控制器/装配、C 以外后端包、其他服务、Gateway、`frontend/packages/`、`App.vue`、全局样式。

## 核心交付

实现确定性规则分类、固定版本向量聚类端口、AI 仅命名/摘要、高风险主题主管审核、知识缺口信号合并、受控指标语义层、查询计划校验与执行端口，以及不依赖公共入口改动的 P9 页面组件。

C 不直接接入 DashScope、pgvector、A/B 数据库或 Gateway；通过 C 包内端口和内存测试适配开发。集成会话负责模型/向量适配、持久化、API 控制器、API Client 公共出口、`App.vue` 和全局样式接入。

## 固定安全边界

- 规则先分类，剩余匿名文本才进入向量聚类。
- AI 不得改变成员、风险类别或生成业务事实。
- 隐私、欺诈、监管、资金主题必须主管批准后发布。
- 知识缺口不得自动创建或发布知识。
- 查询计划禁止 SQL、表名、列名、函数、连接和任意表达式。
- 禁止手机号、地址、消费者身份等敏感维度。
- 页面使用浅色卡片、深色正文和高对比状态色。

## 验证命令

```powershell
D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -pl services/insight-service -Dtest='*Topic*,*Cluster*,*Gap*,*Query*' test
D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd -pl services/insight-service test
pnpm --dir frontend --filter aliagent-admin build
git diff --check
```

## 必须使用的 Skill

`superpowers:executing-plans`、`superpowers:test-driven-development`；失败时使用 `superpowers:systematic-debugging`；提交前使用 `superpowers:verification-before-completion`。工作区已经隔离，不要创建嵌套 Worktree。修改前使用主仓库 CodeGraph 索引盘点后端与现有评测页面模式。

## 新会话提示词

```text
你负责 AliAgent P9-C：主题、知识缺口、受控查询与独立页面组件。只在 D:\Java\code\AliAgent-worktrees\p9-insight-query-ui 工作，分支必须为 codex/p9-insight-query-ui。开始时确认路径、分支、HEAD 和工作区干净；完整阅读 AGENTS.md、docs/tasks/p9/P9-阶段任务边界.md、docs/handoffs/P9-C-主题知识缺口与受控查询上下文.md、docs/superpowers/specs/2026-07-31-p9-operational-insight-design.md、docs/superpowers/plans/2026-07-31-p9-operational-insight-plan.md。

使用 superpowers:executing-plans 执行计划中 P9-C 的 Task 7-10，并严格使用 superpowers:test-driven-development。修改前优先用 CodeGraph 定位 insight-service、P8 EvaluationConsole.vue 和 API 模式；Worktree 若无索引，查询 D:\Java\code\AliAgent 主仓库索引。

只允许修改 insight-service 的 topic、gap、query 包与对应测试，以及 frontend/apps/aliagent-admin/src/insight/。禁止修改 contracts、Flyway、pom.xml、application*.yml、根包控制器和装配、A/B 包、其他服务、Gateway、frontend/packages、App.vue 和全局 CSS。跨任务依赖用 C 包内端口表达；页面在 insight 目录内自包含，不接管公共入口。

必须交付：规则分类；固定 Embedding/聚类版本和成员快照；AI 只命名与摘要；模型故障待命名降级；普通主题置信发布；隐私/欺诈/监管/资金主题主管审核；知识缺口合并且不自动发布知识；白名单 QueryPlan、角色/租户/时间/维度/复杂度校验；SQL 和敏感维度拒绝；结构化结果口径元数据；高对比问题雷达、指标、主题与自然语言查询页面组件。

按计划逐步写失败测试、确认失败、最小实现、确认通过并小步提交。完成后运行后端定向测试、insight-service 全模块测试、aliagent-admin 构建和 git diff --check，清理 test- 数据。提交最终代码，但不要推送、不要创建 PR、不要合并其他分支、不要清理 Worktree。报告 SHA、文件、测试数量、清理结果及集成方需要完成的模型/pgvector/JDBC/API/Gateway/前端入口缺口。
```
