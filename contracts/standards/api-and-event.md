# API、事件与幂等规范

## HTTP 约定

- 路径使用复数资源名和 `/api/v1` 版本前缀；P0 旧接口保持兼容，迁移时经功能开关切换。
- 成功响应使用 `{ "code": 200, "message": "", "data": {} }`；错误响应使用 `{ "code": "字符串错误码", "message": "", "requestId": "" }`。
- 列表分页使用 `page`（从 1 开始）、`pageSize`（最大 100），返回 `items`、`page`、`pageSize`、`total`。
- 可重试的写请求必须传递 `Idempotency-Key`；相同租户、调用方、路由和键在有效期内只执行一次。

## 事件信封

```json
{
  "eventId": "evt-uuid",
  "eventType": "ProductUpdated",
  "eventVersion": 1,
  "occurredAt": "2026-07-12T10:00:00Z",
  "tenantId": "tenant-001",
  "traceId": "trace-uuid",
  "producer": "mall",
  "payload": {}
}
```

事件时间统一使用 UTC ISO-8601。生产者将事件与本地事实写入同一事务的 Outbox；消费者以 `eventId + consumer` 记录 Inbox 幂等状态。失败使用指数退避，超过阈值进入死信队列，重投必须保留原始 `eventId`。
