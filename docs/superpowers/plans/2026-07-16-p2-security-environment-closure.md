# P2 安全与环境收口实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 mall 内部只读 API 落地可验证的短期服务 JWT，并恢复本地 MySQL 上下文测试。

**Architecture:** mall-portal 新增一个 HS256 服务 JWT 验证器，使用 `SERVICE_JWT_SECRET` 并强制检查 P1 约定的声明。内部读取过滤器继续在服务 JWT 验证后解析用户快照，实现双重身份校验。开发 JDBC URL 只增加 MySQL 8 的公钥检索参数。

**Tech Stack:** Java 8 target、Spring Boot 2.7、JJWT 0.9.1、JUnit 5、MySQL 8。

---

### Task 1: 服务 JWT 验签器

**Files:**
- Create: `mall/mall-portal/src/main/java/com/macro/mall/portal/internal/read/Hs256ServiceIdentityVerifier.java`
- Modify: `mall/mall-portal/src/main/java/com/macro/mall/portal/internal/read/InternalReadFilterConfiguration.java`
- Test: `mall/mall-portal/src/test/java/com/macro/mall/portal/internal/read/Hs256ServiceIdentityVerifierTest.java`

- [x] **Step 1: 编写失败测试**

覆盖有效令牌、缺少 Bearer 前缀、错误签名、过期、错误 audience、scope 不足、caller 缺失、jti 缺失和超五分钟有效期。测试令牌使用 32 字节以上固定密钥，声明采用 `caller`、`aud`、`scopes`、`iat`、`exp`、`jti`。

- [x] **Step 2: 运行失败测试**

Run: `mvn -s D:\Java_Tools\Maven\apache-maven-3.9.6\conf\settings.xml -f mall\pom.xml -pl mall-portal -am -Dtest=Hs256ServiceIdentityVerifierTest -DfailIfNoTests=false -DskipTests=false test`

Expected: FAIL，因为验签器尚不存在。

- [x] **Step 3: 实现最小验签器与配置**

使用 `Jwts.parser().setSigningKey(secret).parseClaimsJws(token)` 验证 HS256 签名和标准过期时间；检查 `sub`、`caller`、`aud=mall`、`scopes` 含 `mall.internal.read`、`iat`、`exp`、`jti` 且 `exp - iat <= 300`。任何验证失败转换为 `InternalAuthenticationException`。配置使用 `${SERVICE_JWT_SECRET}`，无默认值。

- [x] **Step 4: 运行验签器测试**

Run: `mvn -s D:\Java_Tools\Maven\apache-maven-3.9.6\conf\settings.xml -f mall\pom.xml -pl mall-portal -am -Dtest=Hs256ServiceIdentityVerifierTest -DfailIfNoTests=false -DskipTests=false test`

Expected: PASS，所有验签情形通过。

### Task 2: 双重身份过滤器测试

**Files:**
- Modify: `mall/mall-portal/src/test/java/com/macro/mall/portal/internal/read/InternalReadAuthenticationFilterTest.java`
- Test: `mall/mall-portal/src/test/java/com/macro/mall/portal/internal/read/InternalReadAuthenticationFilterTest.java`

- [x] **Step 1: 编写失败测试**

通过真实 `Hs256ServiceIdentityVerifier` 签发有效服务令牌。验证有效服务 JWT 加完整 MEMBER 快照返回 200；有效 JWT 加缺失快照返回 401；scope 不足令牌加完整快照返回 401。

- [x] **Step 2: 运行过滤器测试**

Run: `mvn -s D:\Java_Tools\Maven\apache-maven-3.9.6\conf\settings.xml -f mall\pom.xml -pl mall-portal -am -Dtest=InternalReadAuthenticationFilterTest -DfailIfNoTests=false -DskipTests=false test`

Expected: 在真实验签器尚未接入或不完整时失败。

- [x] **Step 3: 调整最小生产代码**

仅在必要时让过滤器配置注入 HS256 验签器；不改变订单授权、员工数据范围或读取 DTO。

- [x] **Step 4: 运行过滤器测试**

Run: `mvn -s D:\Java_Tools\Maven\apache-maven-3.9.6\conf\settings.xml -f mall\pom.xml -pl mall-portal -am -Dtest=InternalReadAuthenticationFilterTest -DfailIfNoTests=false -DskipTests=false test`

Expected: PASS。

### Task 3: MySQL 上下文测试

**Files:**
- Modify: `mall/mall-portal/src/main/resources/application-dev.yml`
- Test: `mall/mall-portal/src/test/java/com/macro/mall/portal/MallPortalApplicationTests.java`

- [x] **Step 1: 补充 MySQL 8 JDBC 参数**

在开发 JDBC URL 的查询参数中加入 `allowPublicKeyRetrieval=true`，保持 host、schema、账号和密码来源不变。

- [x] **Step 2: 运行 portal 上下文测试**

Run: `mvn -s D:\Java_Tools\Maven\apache-maven-3.9.6\conf\settings.xml -f mall\pom.xml -pl mall-portal -am -Dtest=MallPortalApplicationTests -DfailIfNoTests=false -DskipTests=false test`

Expected: 在本机 MySQL `mall` schema 和依赖服务可用时 PASS；若环境仍缺失，记录具体外部依赖错误且不伪造通过。

结果：JDBC 公钥检索错误已消失；MySQL 服务与 3306 端口可用，但 `root/root` 和无密码 `root` 均被拒绝，需提供可访问 `mall` schema 的测试账号。

### Task 4: 集成验证与提交

**Files:**
- Modify: `mall/mall-portal/src/main/java/com/macro/mall/portal/internal/read/Hs256ServiceIdentityVerifier.java`
- Modify: `mall/mall-portal/src/main/java/com/macro/mall/portal/internal/read/InternalReadFilterConfiguration.java`
- Modify: `mall/mall-portal/src/main/resources/application-dev.yml`
- Test: `mall/mall-security/src/test/java/com/macro/mall/security/util/JwtTokenUtilTest.java`
- Test: `mall/mall-admin/src/test/java/com/macro/mall/event/OutboxEventServiceTest.java`
- Test: `mall/mall-portal/src/test/java/com/macro/mall/portal/internal/read/InternalReadServiceTest.java`

- [x] **Step 1: 执行定向 P2 测试**

Run: `mvn -s D:\Java_Tools\Maven\apache-maven-3.9.6\conf\settings.xml -f mall\pom.xml -pl mall-security,mall-admin,mall-portal -am '-Dtest=JwtTokenUtilTest,JwtAuthenticationTokenFilterTest,UmsAdminIdentityLoginTest,EventEnvelopeTest,OutboxEventServiceTest,MockLogisticsAdapterTest,Hs256ServiceIdentityVerifierTest,InternalReadAuthenticationFilterTest,InternalReadServiceTest,MallPortalApplicationTests' -DfailIfNoTests=false -DskipTests=false test`

Expected: 所有指定测试通过，或明确输出外部 MySQL/Redis/Mongo/RabbitMQ 依赖的阻塞。

- [x] **Step 2: 校验变更范围并提交**

Run: `git diff --check; git status --short`

Expected: 仅安全验证、测试和 JDBC 开发配置变更；提交信息为 `fix(p2): 验证内部服务 JWT`。

结果：已提交 `5117a26 fix(p2): 验证内部服务 JWT`。完整上游构建在本地 Docker endpoint 覆盖后到达镜像制作阶段，但固定基础镜像 `openjdk:8` 已在 Docker Hub 返回 404；P2 不修改上游 POM 或镜像基线。

## 基础设施验收记录

- PostgreSQL MCP 内置工具持续返回 `-32603`；MCP CLI 备用只读查询可连接 `postgres` 数据库。
- 只读结果显示 `conversation_db`、`orchestration_db`、`knowledge_db`、`evaluation_db`、`insight_db` 均不存在；仅有可登录角色 `postgres`。因此独立账号与 `knowledge_db` 的 vector 扩展核验尚无法执行。
