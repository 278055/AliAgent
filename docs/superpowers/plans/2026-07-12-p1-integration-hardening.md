# P1 集成加固 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 补齐 P1 服务 JWT 拒绝路径、标准启动方式、数据库 profile 验证和前端构建输出隔离。

**Architecture:** 新建仅含 JWT 解析与 Servlet Filter 的 `platform-service-security` Maven 模块；Gateway 使用同一密钥签发短期令牌，五个业务服务通过 Filter 默认拒绝未认证内部接口。服务 POM 提供 Spring Boot 可执行打包和运行入口；前端仅输出到 `frontend/dist`。

**Tech Stack:** Java 17、Spring Boot 3.4.5、JJWT、JUnit 5、Docker Compose、Vue 3/Vite。

---

### Task 1: 公共服务 JWT 组件

**Files:**
- Create: `platform/platform-service-security/pom.xml`
- Create: `platform/platform-service-security/src/main/java/com/bn/platform/security/ServiceJwtSupport.java`
- Create: `platform/platform-service-security/src/main/java/com/bn/platform/security/ServiceJwtAuthenticationFilter.java`
- Create: `platform/platform-service-security/src/test/java/com/bn/platform/security/ServiceJwtAuthenticationFilterTest.java`
- Modify: `pom.xml`

- [ ] 写缺失 JWT 返回 `AUTH-401-001` 的失败测试，并运行 `mvn -pl platform/platform-service-security test` 确认失败。
- [ ] 实现 HS256 签发、校验及 Filter；放行 `/api/v1/health`，其余路径验证签名、五分钟过期时间、audience 与 `METHOD:/path` scope。
- [ ] 运行模块测试确认通过，并提交 `feat(p1): add service JWT security component`。

### Task 2: Gateway 签发与服务强制认证

**Files:**
- Create: `services/gateway-service/src/main/java/com/bn/aliagent/gateway/ServiceJwtForwardingFilter.java`
- Modify: `services/gateway-service/src/main/java/com/bn/aliagent/gateway/WebConfiguration.java`
- Modify: all service POMs, Application classes and health/event tests

- [ ] 为评价、洞察事件端点写缺失、过期、错误 audience、错误签名、scope 不足和有效 JWT 的测试；先运行并确认未认证断言失败。
- [ ] 各服务接入公共 Filter，Gateway 只在转发至下游时写服务 JWT；运行相关 Maven 测试确认通过。
- [ ] 提交 `feat(p1): enforce service JWT on internal endpoints`。

### Task 3: 标准启动和数据库 profile

**Files:**
- Create: `deploy/verify-database-profile.ps1`
- Modify: root and service POMs, `deploy/.env.example`, `deploy/docker-compose.yml`

- [ ] 脚本以每个服务专属 JDBC 配置和 `database` profile 启动五个持久化服务，验证健康、Flyway 与 `service_health_probe` 表。
- [ ] 每个服务 POM 添加 Spring Boot Maven 插件；更新环境变量并运行脚本确认通过。
- [ ] 提交 `build(p1): support database profile service startup`。

### Task 4: 数据库隔离

**Files:**
- Create: `deploy/verify-database-isolation.ps1`
- Modify: `deploy/postgres/init-databases.sh`（仅权限断言失败时）

- [ ] 使用 Java JDBC 校验每个服务角色能连所属库、不能连其余四库，要求 5 个允许与 20 个拒绝均成立。
- [ ] 如需要，最小化修正数据库授权后重跑；提交 `test(p1): verify service database isolation`。

### Task 5: 前端输出隔离与验收

**Files:**
- Modify: `frontend/vite.config.ts`, `frontend/README.md`

- [ ] 将 Vite `build.outDir` 固定为 `dist`，运行 `npm.cmd ci`、`npm.cmd run build`，并断言 `src/main/resources/static` 无 Git 差异。
- [ ] 运行 pnpm workspace、Maven 全量测试、Compose 配置、服务 JWT 和数据库验证；关闭并清理 Compose 临时资源。
- [ ] 提交 `build(p1): isolate frontend build output`。
