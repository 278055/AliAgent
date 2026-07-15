# P2 安全与环境收口设计

## 目标

使 mall 内部只读 API 能够验证 P1 定义的短期服务 JWT，并修复本地 MySQL 开发连接参数，以完成 P2 的安全链路与应用上下文验证。

## 服务 JWT 契约

mall-portal 仅接受 `X-Service-Authorization: Bearer <token>`。令牌使用 HS256，密钥来自必填环境变量 `SERVICE_JWT_SECRET`，长度至少为 32 字节。令牌必须具备以下声明：

- `sub` 与 `caller`：调用服务标识；
- `aud`：包含 `mall`；
- `scopes`：包含 `mall.internal.read`；
- `iat`、`exp`、`jti`：签发时间、过期时间和唯一标识；
- 有效期不超过 300 秒。

mall-portal 只负责验证服务身份、签名、有效期、受众和 scope。用户快照仍由现有 `X-Tenant-Id`、`X-Subject-Id`、`X-Subject-Type` 和可选 `X-User-Roles` 解析；两类验证均通过后才允许读取内部资源。

为避免与 P1 分叉，mall-portal 的适配器采用与 `platform-service-security` 的 `ServiceJwtSupport` 相同的 HS256 声明和校验规则。P2 不在 mall 内签发服务 JWT，签发方仍是 Gateway 或受信调用服务。

## 错误处理

缺少或格式错误的授权头、无效签名、过期令牌、错误 audience、scope 不足、无效 caller 或缺少 `jti` 均返回 HTTP 401 与 `AUTH-401-001`。用户快照不完整同样返回 401。订单归属或员工数据范围不足维持现有 HTTP 403 与 `MALL-403-001`。

## 配置与本地环境

新增 mall-portal 的服务 JWT 配置，使用 `${SERVICE_JWT_SECRET}`，不提供源码内默认密钥。测试直接构造本地固定测试密钥，不依赖环境变量。

本地 `application-dev.yml` 的 MySQL JDBC URL 增加 `allowPublicKeyRetrieval=true`，仅解决 MySQL 8 的认证握手错误；不提交账号、密码或数据库数据。使用已有 MySQL 服务与 `mall` schema 重跑 portal 应用上下文测试。

## 验证

服务 JWT 单元测试覆盖有效令牌、缺失令牌、错误签名、过期、错误 audience、scope 不足以及缺少关键声明。过滤器测试覆盖服务令牌与用户快照双重校验。完成后执行 mall-security、mall-admin、mall-portal 的定向集成测试和 portal `MallPortalApplicationTests`。

完整上游打包继续受固定 Docker 地址约束，P2 不修改上游 POM；PostgreSQL MCP 只读检查失败作为 P1 基础设施风险保留。
