# P7 人工客服协作契约

## 1. 事实归属

- `conversation-service` 是技能组、队列、分配邀请、接管、转派、人工消息和客服容量的事实来源。
- `ai-orchestration-service` 是 AI 副驾建议、模型版本、Prompt 版本、工作流版本和建议处理审计的事实来源。
- Redis 只保存在线心跳和实时路由加速数据，不能作为接管、容量或消息事实来源。
- AI 在 `WAITING_HUMAN`、`ASSIGNING`、`HUMAN_ACTIVE` 状态下不得产生消费者可见的公开回复。

## 2. 状态定义

### 2.1 会话协作状态

| 状态 | 公开发言方 | 允许迁移 |
|---|---|---|
| `AI_ACTIVE` | AI、用户 | `WAITING_HUMAN`、`CLOSED` |
| `WAITING_HUMAN` | 用户 | `ASSIGNING`、`HUMAN_ACTIVE`、`AI_ACTIVE`、`CLOSED` |
| `ASSIGNING` | 用户 | `HUMAN_ACTIVE`、`WAITING_HUMAN`、`AI_ACTIVE`、`CLOSED` |
| `HUMAN_ACTIVE` | 用户、当前客服 | `WAITING_HUMAN`、`AI_ACTIVE`、`CLOSED` |
| `CLOSED` | 无 | 无 |

### 2.2 客服在线状态

- `OFFLINE`：不能自动分配、领取或接受转派。
- `ONLINE`：可以参与分配和领取。
- `BUSY`：在线但不能接收新会话。

### 2.3 队列、邀请和接管状态

- 队列：`WAITING`、`OFFERED`、`CLAIMABLE`、`ASSIGNED`、`CANCELLED`、`CLOSED`。
- 邀请：`PENDING`、`ACCEPTED`、`REJECTED`、`EXPIRED`、`CANCELLED`。
- 接管：`ACTIVE`、`RELEASED`、`TRANSFERRED`、`CLOSED`、`FORCE_RELEASED`。
- 转派：`PENDING`、`ACCEPTED`、`REJECTED`、`EXPIRED`、`CANCELLED`。

## 3. 确定性规则

### 3.1 技能组路由

AI 可以输出候选意图、类目和风险标签，最终技能组由版本化规则匹配。规则输入仅允许可信事实和已验证标签，规则结果至少包含：

```json
{
  "skillGroupId": "uuid",
  "matchedTags": ["AFTERSALE", "REFUND"],
  "ruleVersion": "p7-routing-v1"
}
```

### 3.2 优先级

优先级使用可解释的整数分数：

```text
riskScore + complaintScore + membershipScore + waitingScore
```

- 高风险售后：`+1000`
- 投诉或敏感承诺风险：`+800`
- 会员等级：普通 `0`、VIP `+100`、高价值会员 `+200`
- 等待分：每完整等待一分钟 `+1`，最大 `+300`
- 同分按 `enqueuedAt` 升序排列

队列项创建时固定 `routingRuleVersion` 和 `priorityRuleVersion`。等待分可以随时间计算，但其他权重不得在重试时切换版本。

### 3.3 自动分配

候选客服必须同时满足：同租户、技能组成员启用、状态 `ONLINE`、当前有效接管量小于最大接待量。候选人按当前接待量升序、最近一次分配时间升序、`staffId` 升序选择。

自动分配创建限时邀请，客服接受后才建立有效接管。拒绝或超时重新分配；达到技能组 `maxAssignmentAttempts` 后进入 `CLAIMABLE`。

## 4. 幂等与并发

- 所有命令携带 `requestId` 和 `Idempotency-Key`。
- 接受邀请、主动领取、转派、结束、关闭和副驾操作必须可重复提交并返回同一事实。
- 每个会话最多一个未结束队列项、一个有效邀请和一个有效接管。
- 每个客服的有效接管数不得超过成员配置的最大接待量。
- 数据库唯一约束是最终并发保护；进程锁和 Redis 锁不能替代数据库约束。

建议唯一约束：

```text
human_queue_item(tenant_id, conversation_id) WHERE status IN ('WAITING','OFFERED','CLAIMABLE')
human_assignment_offer(tenant_id, conversation_id) WHERE status = 'PENDING'
human_takeover(tenant_id, conversation_id) WHERE status = 'ACTIVE'
copilot_suggestion(tenant_id, conversation_id, trigger_message_id, workflow_version, refresh_no)
```

## 5. REST API

### 5.1 技能组和成员

- `POST /api/v1/agent/skill-groups`
- `PATCH /api/v1/agent/skill-groups/{skillGroupId}`
- `PUT /api/v1/agent/skill-groups/{skillGroupId}/tags`
- `PUT /api/v1/agent/skill-groups/{skillGroupId}/members/{staffId}`
- `GET /api/v1/agent/skill-groups`

### 5.2 队列与分配

- `POST /api/v1/conversations/{conversationId}/human-queue`
- `GET /api/v1/agent/queue`
- `GET /api/v1/supervisor/queue`
- `POST /api/v1/agent/offers/{offerId}/accept`
- `POST /api/v1/agent/offers/{offerId}/reject`
- `POST /api/v1/agent/queue/{queueItemId}/claim`
- `POST /internal/api/v1/agent/assignments/expire`

### 5.3 人工协作

- `POST /api/v1/conversations/{conversationId}/human-messages`
- `POST /api/v1/conversations/{conversationId}/transfer/skill-group`
- `POST /api/v1/conversations/{conversationId}/transfer/agent`
- `POST /api/v1/conversations/{conversationId}/human-release`
- `POST /api/v1/conversations/{conversationId}/human-close`
- `POST /api/v1/supervisor/conversations/{conversationId}/force-transfer`
- `POST /api/v1/supervisor/conversations/{conversationId}/force-release`

### 5.4 副驾

- `GET /api/v1/copilot/conversations/{conversationId}/suggestions`
- `POST /api/v1/copilot/conversations/{conversationId}/suggestions/refresh`
- `POST /api/v1/copilot/suggestions/{suggestionId}/accept`
- `POST /api/v1/copilot/suggestions/{suggestionId}/modify-and-send`
- `POST /api/v1/copilot/suggestions/{suggestionId}/ignore`

## 6. 事件

所有事件使用平台事件信封，至少包含 `eventId`、`eventType`、`eventVersion`、`occurredAt`、`tenantId`、`traceId`、`producer` 和 `payload`。

| Topic | 版本 | 生产者 | 消费者 |
|---|---:|---|---|
| `conversation.human.requested.v1` | 1 | conversation-service | conversation-service |
| `conversation.queue.entered.v1` | 1 | conversation-service | 实时通知/审计 |
| `conversation.assignment.offered.v1` | 1 | conversation-service | 客服工作台 |
| `conversation.assignment.accepted.v1` | 1 | conversation-service | 实时通知/AI 编排 |
| `conversation.assignment.rejected.v1` | 1 | conversation-service | 分配器 |
| `conversation.assignment.expired.v1` | 1 | conversation-service | 分配器 |
| `conversation.transferred.v1` | 1 | conversation-service | 实时通知/审计 |
| `conversation.human.released.v1` | 1 | conversation-service | AI 编排/实时通知 |
| `conversation.staff.message.created.v1` | 1 | conversation-service | AI 副驾 |
| `copilot.suggestion.requested.v1` | 1 | conversation-service | ai-orchestration-service |
| `copilot.suggestion.generated.v1` | 1 | ai-orchestration-service | conversation-service/工作台 |
| `copilot.suggestion.actioned.v1` | 1 | ai-orchestration-service | P8 候选样本消费者 |

`copilot.suggestion.requested.v1` 的 payload 必须包含：

```json
{
  "conversationId": "uuid",
  "triggerMessageId": "uuid",
  "assignedAgentId": "staff-id",
  "requestId": "uuid",
  "refreshNo": 0,
  "userIdentitySnapshot": {
    "subjectId": "member-id",
    "subjectType": "MEMBER",
    "roles": ["MEMBER"],
    "permissions": []
  }
}
```

## 7. 数据表

conversation_db：

- `agent_skill_group`
- `agent_skill_tag`
- `agent_skill_group_tag`
- `agent_skill_group_member`
- `human_queue_item`
- `human_assignment_offer`
- `human_takeover`
- `human_transfer`
- `human_collaboration_outbox`
- `human_collaboration_inbox`
- `human_collaboration_audit`

复用并演进：

- `conversation_agent_presence`
- `conversation_human_state`
- `message`

ai_orchestration_db：

- `copilot_suggestion`
- `copilot_suggestion_action`
- `copilot_inbox`
- `copilot_outbox`

## 8. 权限

- `MEMBER`：请求人工、查看自己的公开消息。
- `STAFF`：访问所属技能组队列、处理自己的邀请和接管会话、使用副驾。
- `SUPERVISOR`：管理本租户技能组与成员、查看本租户全部队列、强制转派和释放。
- 客户端传入的内部租户、主体或角色头必须被 Gateway 删除；下游只信任重新注入的可信上下文。

## 9. 降级规则

- AI、知识、mall 或售后查询不可用：人工聊天继续，副驾建议标记可重试，不虚构业务事实。
- Redis 不可用：在线路由降级，消息和接管事实从 PostgreSQL 恢复。
- MQ 不可用：业务事务写入 Outbox，恢复后补发。
- 客服离线或心跳过期：不接受新邀请；已有接管进入主管异常处理列表，不自动丢失责任。

