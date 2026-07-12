# 身份、租户与请求上下文规范

## 可信上下文

Gateway 验证外部 JWT 后，生成或透传 `traceId` 和 `requestId`，并只向下游注入以下内部头：

| 头 | 含义 |
|---|---|
| `X-Request-Id` | 单次外部请求 ID，UUID。 |
| `X-Trace-Id` | 全链路追踪 ID，UUID。 |
| `X-Tenant-Id` | 可信租户 ID。 |
| `X-Subject-Id` | 用户或员工主体 ID。 |
| `X-Subject-Type` | `MEMBER` 或 `STAFF`。 |
| `X-User-Roles` | 逗号分隔的角色快照。 |
| `X-Service-Authorization` | 面向目标服务的短期服务 JWT。 |

外部请求携带的同名头必须在 Gateway 删除。服务不允许从请求参数、请求体或前端缓存推断租户。

## JWT 最小声明

外部 JWT 至少包含 `sub`、`subjectType`、`tenantId`、`roles`、`permissions`、`iat`、`exp`、`jti`。内部服务 JWT 额外包含 `caller`、`aud` 和允许的 `scopes`，有效期不超过 5 分钟。

## 日志字段

所有入口、异步消费和关键工具调用日志必须包含 `traceId`、`requestId`、`tenantId`、`service`、`eventId`（如适用）。禁止记录原始密码、Token、完整身份证明、支付信息和未经脱敏的订单详情。
