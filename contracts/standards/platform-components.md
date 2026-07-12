# P1 公共组件边界

P1-A 只定义公共组件的稳定边界，不向业务服务注入实现；具体服务在 P1-B 创建后按需依赖。

| 组件 | 推荐 Maven 坐标 | 职责 | 禁止事项 |
| --- | --- | --- | --- |
| 请求上下文 | `com.bn:platform-context` | 在 Gateway 建立可信的 `requestId`、`traceId`、租户和主体上下文，并只向下游透传规范头。 | 服务不得从请求体、参数或前端缓存推断租户。 |
| 结构化日志 | `com.bn:platform-observability` | 为入口、异步消费者和关键工具调用统一输出 `traceId`、`requestId`、`tenantId`、`service`、`eventId`。 | 不记录密码、原始 Token、完整身份证明、支付信息或未脱敏订单。 |
| 服务认证 | `com.bn:platform-service-security` | 校验 `X-Service-Authorization` 携带的短期服务 JWT：`caller`、`aud`、`scopes`，有效期不超过五分钟。 | 不把外部用户 JWT 当作服务 JWT，也不信任外部传入的同名内部头。 |

Gateway 是外部 JWT 验证和内部头注入的唯一边界；服务仅消费由 Gateway 注入的可信上下文。上述坐标是 P1-B 的包边界约定，P1-A 不创建服务目录或业务实现。
