# P2-0：mall Subtree 与最小启动基线

## Subtree 来源

- 上游仓库：`https://github.com/macrozheng/mall.git`
- 引入标签：`v1.0.3`
- 固定上游 commit：`dd617ac3fe89c8083af56bee3364b1e812cda3ed`
- 引入日期：2026-07-15
- 引入方式：Git Subtree，目录为 `mall/`，使用 squash commit
- 本项目 Subtree squash commit：`fe00e0d5e2cd7ab1be5bac952c0401f93a5a9eab`

后续同步上游时，在本项目根目录执行：

```powershell
git subtree pull --prefix=mall https://github.com/macrozheng/mall.git v1.0.3 --squash
```

如需同步新的上游 tag，先记录新的 tag、commit 和日期，再将命令中的版本替换为目标 tag；不得直接覆盖或重构 `mall/` 内的上游模块。

## 技术边界

`mall/` 保持 macrozheng/mall 的模块化单体结构和 MySQL 技术基线。P2-0 不修改其业务模型，不实现 AI 功能，不接入 AliAgent 的 JWT、Outbox 或 PostgreSQL/pgvector 数据链路。后续跨系统能力通过明确的适配契约和独立任务接入。

当前上游模块包括：`mall-common`、`mall-mbg`、`mall-security`、`mall-admin`、`mall-search`、`mall-portal` 和 `mall-demo`。

## 最小本地启动

### 依赖

- JDK 17 可用于本地编译和运行该上游 Java 8 目标项目。
- Maven 3.9+。
- MySQL 5.7 或兼容版本，数据库名 `mall`。
- Redis 7 或兼容版本，默认监听 `localhost:6379`。
- 首次启动前按上游文档导入 `mall` 数据库脚本；P2-0 不提交数据库导出文件。

### 配置模板

不要把真实账号、密钥或数据库口令提交到 Git。复制以下模板到本地配置文件后填写：

```yaml
spring:
  datasource:
    url: jdbc:mysql://${MALL_MYSQL_HOST:localhost}:${MALL_MYSQL_PORT:3306}/${MALL_MYSQL_DATABASE:mall}?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false
    username: ${MALL_MYSQL_USERNAME:CHANGE_ME}
    password: ${MALL_MYSQL_PASSWORD:CHANGE_ME}
  redis:
    host: ${MALL_REDIS_HOST:localhost}
    port: ${MALL_REDIS_PORT:6379}
    password: ${MALL_REDIS_PASSWORD:}
```

推荐仅启动后台模块：在 `mall/mall-admin/src/main/resources/application-dev.yml` 的本地副本中填写数据库和 Redis 配置，然后运行：

```powershell
Set-Location D:\Java\code\AliAgent-worktrees\p2-mall-bootstrap
mvn -s D:\Java_Tools\Maven\apache-maven-3.9.6\conf\settings.xml -f mall\mall-admin\pom.xml spring-boot:run
```

该启动路径仍需要可用的 MySQL `mall` schema 和 Redis；MinIO、OSS 等可选能力按实际接口调用再配置。启动类为 `com.macro.mall.MallAdminApplication`。

## 验证记录

### 最小 Maven 构建

以下命令不触发上游 Docker 镜像插件，已验证基础模块构建成功：

```powershell
mvn -s D:\Java_Tools\Maven\apache-maven-3.9.6\conf\settings.xml -f mall\pom.xml -pl mall-common,mall-mbg,mall-security,mall-demo -am clean package -DskipTests
```

结果：`BUILD SUCCESS`，`mall-common`、`mall-mbg`、`mall-security`、`mall-demo` 及其依赖模块完成构建。

### 完整 reactor 构建阻塞

复现命令：

```powershell
mvn -s D:\Java_Tools\Maven\apache-maven-3.9.6\conf\settings.xml -f mall\pom.xml clean package -DskipTests
```

结果：在 `mall-admin` 的 `io.fabric8:docker-maven-plugin:0.40.2:build` 阶段失败。上游 POM 固定 Docker 地址 `192.168.3.101:2375`，当前环境无法连接；`mall-common`、`mall-mbg`、`mall-security` 和 `mall-demo` 已成功。P2-0 不修改上游 POM，因此完整镜像构建需后续提供兼容 Docker daemon 或由集成任务处理。

## 后续风险与阻塞

- P2-A：需设计 AliAgent 与 mall 的身份/权限边界；当前 mall 自带 JWT/Spring Security，P2-0 不做 JWT 接入。
- P2-B：需通过只读业务工具或 API 访问 MySQL 业务事实，不能让 AI 直接访问 mall 数据库；跨库、租户和审计边界尚未落地。
- P2-C：订单、售后和退款写操作仍缺少确认、幂等、Outbox/Saga 与回退机制；在这些约束完成前不得开放 AI 写操作。
- mall 上游为 Spring Boot 2.7.5/Java 8 目标和 MySQL，不能直接并入根项目 Java 17/PostgreSQL 的 Maven reactor。
