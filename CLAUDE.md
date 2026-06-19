# AliAgent 项目开发指南

## 技术栈

| 层 | 技术 | 版本 |
|----|------|------|
| JDK | Java | 17 |
| 后端 | Spring Boot | 3.4.5 |
| AI | Spring AI Alibaba DashScope | 1.1.2.0 |
| ORM | MyBatis-Plus | 3.5.5 |
| 数据库 | PostgreSQL + pgvector (0.8.2) | 1024维 |
| 缓存 | Redis (Lettuce) | - |
| 前端 | Vue 3 + TypeScript + Vite | 6.3 |
| 构建 | Maven / npm | - |

## 环境路径

| 工具 | 路径 | 启动命令 |
|------|------|----------|
| Maven | `D:\Java_Tools\Maven\apache-maven-3.9.6` | - |
| JDK 17 | Java 17.0.12 LTS | - |
| Node | v24.15.0 | - |
| Redis | `D:\Java_Tools\Redis-x64-3.2.100` | `redis-server.exe redis.windows.conf` |
| PostgreSQL | localhost:5432 / postgres / 123456 | 系统服务（开机自启） |

## 测试规范

### 数据清理

每次端到端测试会产生对话和消息数据，**测试完成后必须清理**，避免数据库堆积冗余。

| 资源 | 清理方式 |
|------|----------|
| conversation + message | `DELETE FROM message WHERE conversation_id LIKE 'test-%'` 后删除对应 conversation |
| document + document_chunk | `DELETE FROM document_chunk WHERE document_id IN (SELECT id FROM document WHERE name LIKE 'test-%')` 后删除对应 document |
| 用户 | 测试用户从 `users` 表删除 |

**命名约定**：测试资源（会话、文档、用户）统一用 `test-` 或 `rag-test-` 前缀，便于批量定位和清理。

**检查命令**：
```sql
-- 查看测试会话数量
SELECT COUNT(*) FROM conversation WHERE id LIKE '%test%' OR title = '新对话';
-- 查看测试文档数量
SELECT COUNT(*) FROM document WHERE name LIKE 'test-%';
```

## 项目文档索引

| 文档 | 说明 |
|------|------|
| `docs/项目现状.md` | 项目完整介绍 + 代码结构 |
| `docs/开发命令速查.md` | 后端/前端/数据库常用命令 |
| `docs/MCP配置与使用指南.md` | PostgreSQL MCP 查询 + JDBC DDL 执行 |
| `docs/RAG实施计划-分阶段执行.md` | RAG 分 7 个 Phase 落地计划 |
| `d:\download\enterprise_rag_design.md` | 企业级 RAG 蓝图 |
