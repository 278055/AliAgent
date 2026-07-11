# MCP 配置与使用指南

> AliAgent 项目已配置的 MCP Server 列表及其使用方法

---

## 1. PostgreSQL MCP Server

- **包名**: `@modelcontextprotocol/server-postgres`
- **运行方式**: `npx`（无需全局安装）
- **连接串**: `postgresql://postgres:123456@localhost:5432/postgres`

### 注册到 Claude Code

```bash
claude mcp add postgres -- \
  npx -y @modelcontextprotocol/server-postgres \
  "postgresql://postgres:123456@localhost:5432/postgres"
```

### 提供的工具

| 工具 | 说明 |
|------|------|
| `query` | 执行只读 SQL 查询（SELECT） |

### 使用方式

**方式一：在对话中让 AI 直接调用 MCP 工具**

> MCP 注册后，AI 在对话中可以直接使用 `query` 工具查询数据库。

**方式二：命令行直接调用（不依赖 AI）**

```bash
# 查询模板
echo '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"query","arguments":{"sql":"<SQL语句>"}}}' \
  | timeout 5 npx -y @modelcontextprotocol/server-postgres \
  "postgresql://postgres:123456@localhost:5432/postgres"
```

### 常用查询示例

```bash
# 查看所有表
echo '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"query","arguments":{"sql":"SELECT table_name FROM information_schema.tables WHERE table_schema = '\''public'\'' ORDER BY table_name"}}}' | timeout 5 npx -y @modelcontextprotocol/server-postgres "postgresql://postgres:123456@localhost:5432/postgres"

# 查看表结构
echo '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"query","arguments":{"sql":"SELECT column_name, data_type FROM information_schema.columns WHERE table_name = '\''document_chunk'\'' ORDER BY ordinal_position"}}}' | timeout 5 npx -y @modelcontextprotocol/server-postgres "postgresql://postgres:123456@localhost:5432/postgres"

# 查看 document 表内容
echo '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"query","arguments":{"sql":"SELECT id, name, type, size FROM document ORDER BY created_at DESC"}}}' | timeout 5 npx -y @modelcontextprotocol/server-postgres "postgresql://postgres:123456@localhost:5432/postgres"

# 查看 document_chunk 数量
echo '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"query","arguments":{"sql":"SELECT d.name, COUNT(dc.id) as chunks FROM document d LEFT JOIN document_chunk dc ON d.id = dc.document_id GROUP BY d.id, d.name"}}}' | timeout 5 npx -y @modelcontextprotocol/server-postgres "postgresql://postgres:123456@localhost:5432/postgres"
```

### 注意事项

- `query` 工具**只支持 SELECT**，无法执行 DDL/INSERT/UPDATE/DELETE
- 需要执行 DDL 时，用 Java JDBC 方式（见下方）

---

## 2. 直接执行 DDL（Java JDBC）

当需要执行 CREATE/DROP/ALTER 等写操作时，MCP 不支持，改用这个方式：

```bash
# 1. 写 Java 文件到 /tmp
# 2. 编译
PG_JAR=$(ls ~/.m2/repository/org/postgresql/postgresql/*/postgresql-*.jar | head -1)
javac -cp "$PG_JAR" /tmp/YourScript.java -d /tmp/ -encoding UTF-8

# 3. 运行
java -cp "/tmp:$PG_JAR" YourScript
```

### JDBC 连接信息

| 参数 | 值 |
|------|-----|
| URL | `jdbc:postgresql://localhost:5432/postgres` |
| User | `postgres` |
| Password | `123456` |

---

## 3. 数据库连接信息

| 服务 | 地址 | 端口 | 用户 | 密码 |
|------|------|------|------|------|
| PostgreSQL | localhost | 5432 | postgres | 123456 |
| Redis | localhost | 6600 | - | - |

### 关键扩展与版本

- pgvector: 0.8.2
- PostgreSQL 支持的向量操作: `vector_cosine_ops`（余弦距离）
- 索引类型: HNSW
- Embedding 维度: 1024 (text-embedding-v3)
