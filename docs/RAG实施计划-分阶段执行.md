# AliAgent RAG 实施计划 — 分阶段执行

> 每个 Phase 独立可验证，层层递进，不回头改代码。
>
> 蓝图: `d:\download\enterprise_rag_design.md`
> 架构: `C:\Users\14259\.claude\plans\1-rag-md-2-rosy-wren.md`
> 最后更新: 2026-06-18

---

## 📍 当前进度

```
✅ Phase 1 ─→ ✅ Phase 2 ─→ ✅ Phase 3 ─→ ✅ Phase 4 ─→ ✅ Phase 5 ─→ ✅ Phase 6 ─→ ✅ Phase 7
   基础          接口骨架      最简实现      文档摄入      集成对话      前端适配      端到端验证
```

| Phase | 状态 | 完成时间 |
|-------|------|----------|
| 1 | ✅ 已完成 | 2026-06-18 |
| 2 | ✅ 已完成 | 2026-06-18 |
| 3 | ✅ 已完成 | 2026-06-18 |
| 4 | ✅ 已完成 | 2026-06-18 |
| 5 | ✅ 已完成 | 2026-06-18 |
| 6 | ✅ 已完成 | 2026-06-18 |
| 7 | ✅ 已完成 | 2026-06-18 |

---

## 总览

```
Phase 1 ─→ Phase 2 ─→ Phase 3 ─→ Phase 4 ─→ Phase 5 ─→ Phase 6 ─→ Phase 7
 基础      接口骨架    最简实现   文档摄入   集成对话   前端适配   端到端验证
```

| Phase | 内容 | 产出 | 可验证 |
|-------|------|------|--------|
| 1 | pom + yaml + schema | 依赖就绪、表建好 | `mvn compile` 通过 |
| 2 | 模型类 + 6 个接口 | 编译通过的空骨架 | `mvn compile` 通过 |
| 3 | 6 个最简实现 + Pipeline | 代码完整可运行 | 单元测试/注入验证 |
| 4 | Entity + Mapper + 摄入 + Controller | 文件上传 → 向量入库 | `POST /api/rag/documents/upload` |
| 5 | 改 Agent.java 接 Pipeline | 对话中自动检索增强 | 普通对话不受影响 |
| 6 | 前端 5 改 5 增 | 知识库面板 + 引用卡片 | UI 全流程走通 |
| 7 | 上传文档 → 提问 → 验证回答 | 全链路跑通 | AI 基于文档回答 |

---

## Phase 1: 基础设施就绪

**目标**: 依赖、配置、数据库全部到位，`mvn compile` 通过。

### 任务

- [x] 1.1 `pom.xml` 新增 2 个依赖 + Spring AI BOM
  ```xml
  spring-ai-starter-vector-store-pgvector
  spring-ai-tika-document-reader
  ```
- [x] 1.2 `application.yaml` 新增 embedding + vectorstore 配置
  - text-embedding-v3 / 1024维 / HNSW / COSINE_DISTANCE
- [x] 1.3 `schema.sql` 升级
  - 旧 `document` 表已删除（旧结构: id, content, embedding(1536), metadata, created_at）
  - 新 `document` 表（纯元信息: id, name, type, size, metadata, created_at）
  - 新 `document_chunk` 表（向量存储: id, document_id, content, embedding(1024), section_title, page_number, chunk_index, metadata, created_at）
  - HNSW 索引 `idx_chunk_embedding` (vector_cosine_ops)
- [x] 1.4 数据库执行完毕（Java JDBC 方式，MCP 已验证表结构）

### 验证

```bash
mvn compile          # 依赖下载成功，编译通过
# 检查 schema.sql 语法无误
```

---

## Phase 2: RAG 2.0 接口骨架

**目标**: `rag/` 包下所有接口和模型类定义完毕，编译通过，但实现类为空或直接抛异常。

### 任务

- [ ] 2.1 新建包 `com.bn.aliagent.rag.model`
  - `RagChunk.java`
  - `RagContext.java`
  - `RetrievalContext.java`

- [ ] 2.2 新建包 `com.bn.aliagent.rag.strategy`
  - `RetrievalStrategy.java` (接口)

- [ ] 2.3 新建包 `com.bn.aliagent.rag.rewrite`
  - `QueryRewriter.java` (接口)

- [ ] 2.4 新建包 `com.bn.aliagent.rag.retriever`
  - `Retriever.java` (接口)

- [ ] 2.5 新建包 `com.bn.aliagent.rag.rerank`
  - `Reranker.java` (接口)

- [ ] 2.6 新建包 `com.bn.aliagent.rag.context`
  - `ContextBuilder.java` (接口)

- [ ] 2.7 新建包 `com.bn.aliagent.rag.prompt`
  - `PromptBuilder.java` (接口)

### 验证

```bash
mvn compile          # 所有接口和模型编译通过
```

---

## Phase 3: 一期最简实现

**目标**: 六个接口全部有了一期实现，Pipeline 串联跑通，`RAGConfig` 装配好 Bean。

### 任务

- [ ] 3.1 `RuleStrategy.java` — 规则判断
  - 检测用户消息是否包含知识类关键词
  - 简单规则：含"?"、"什么"、"如何"、"怎么"、"政策"、"规定"等 → 触发检索
  - 寒暄类（"你好"、"谢谢"、"再见"等）→ 跳过

- [ ] 3.2 `NoOpQueryRewriter.java` — 原样透传
  - `rewrite(query)` 直接返回 `List.of(query)`

- [ ] 3.3 `VectorRetriever.java` — pgvector 向量检索
  - 注入 `EmbeddingModel`，将 query 向量化
  - 通过 `JdbcTemplate` 或 `PgVectorStore` 做 cosine 相似度检索
  - 返回 Top-K 个 `RagChunk`

- [ ] 3.4 `NoOpReranker.java` — 直通
  - `rerank()` 直接返回原列表

- [ ] 3.5 `SimpleContextBuilder.java` — 拼接 + 去重
  - 简单拼接 chunks 的 content
  - 基于 content hash 去重
  - 硬截断 2000 字符

- [ ] 3.6 `BasicPromptBuilder.java` — 固定模板
  - System: "你是一个知识库助手。请基于以下上下文回答问题..."
  - User: 上下文 + 用户问题

- [ ] 3.7 `RAGPipeline.java` — 串联六步
  - Step1~6 按序调用
  - 返回完整的 `RagContext`

- [ ] 3.8 `RAGConfig.java` — Spring 装配
  - 所有接口 → 实现类的 Bean 映射
  - 注入 `EmbeddingModel`、`JdbcTemplate` 等依赖

### 验证

```bash
mvn compile          # 完整编译通过
# 检查 RAGConfig Bean 装配无误（启动应用无报错）
```

---

## Phase 4: 文档摄入 + API

**目标**: 文件上传 → Tika 解析 → 分块 → 向量化 → 入库全链路走通。

### 任务

- [x] 4.1 新建 `entity/Document.java`
  - `id, name, type, size, metadata, createdAt`

- [x] 4.2 新建 `entity/DocumentChunk.java`
  - `id, documentId, content, sectionTitle, pageNumber, chunkIndex, metadata, createdAt`
  - embedding 字段通过 SQL 写入（实体不映射）

- [x] 4.3 新建 `mapper/DocumentMapper.java`
  - `BaseMapper<Document>`

- [x] 4.4 新建 `mapper/DocumentChunkMapper.java`
  - `BaseMapper<DocumentChunk>`
  - 自定义方法：`findByDocumentId` / `countByDocumentId` / `deleteByDocumentId`

- [x] 4.5 新建 `service/DocumentService.java`
  - 文档 CRUD（增删查）
  - 分块查询

- [x] 4.6 新建 `rag/ingest/DocumentIngestService.java`
  - `ingest(MultipartFile file)`:
    1. TikaDocumentReader 解析文本
    2. TokenTextSplitter 分块（800 tokens, overlap 100）
    3. DashScopeEmbeddingModel 向量化
    4. 写入 `document` 表 + 批量写入 `document_chunk` 表

- [x] 4.7 新建 `controller/RAGController.java`
  - `POST /api/rag/documents/upload`
  - `GET /api/rag/documents`
  - `DELETE /api/rag/documents/{id}`
  - `GET /api/rag/search?q=xxx&topK=5`

- [x] 4.8 修改 `config/WebMvcConfig.java`
  - 确认 `/api/rag/**` 需要 Token 鉴权（已满足，无需修改）

### 验证

```bash
curl -X POST http://localhost:8080/api/rag/documents/upload \
  -H "Authorization: Bearer <token>" \
  -F "file=@test.pdf"
# 返回 200 + 文档信息

curl "http://localhost:8080/api/rag/search?q=退款政策&topK=3"
# 返回检索到的相关片段
```

---

## Phase 5: 集成对话链路

**目标**: Agent.java 注入 RAGPipeline，知识类问题自动检索增强，普通对话不受影响。

### 任务

- [x] 5.1 修改 `service/MessageService.java`
  - 新增 `saveMessage(cid, role, content, metadata)` 重载方法（metadata 为 JSON 字符串）
  - 原 `saveMessage(cid, role, content)` 委托到新方法（metadata=null → DB 默认 `'{}'`）

- [x] 5.2 修改 `agent/Agent.java`
  - 注入 `RAGPipeline`
  - 在 `chat()` 方法中调用 `ragPipeline.execute(message, conversationId)`
  - 如果 `ragContext.getPrompt() != null` → 用 RAG Prompt 替换普通 user message
  - 流结束时用 `buildSourcesJson()` 序列化来源片段，存入 message.metadata

- [x] 5.3 数据层适配（原"前端适配"改为数据层）
  - `schema.sql` message 表加 `metadata JSONB DEFAULT '{}'`
  - `entity/Message.java` 加 `metadata` 字段
  - 数据库通过 JDBC 执行 `ALTER TABLE` 加列
  - 前端通过现有 `GET /messages` 接口即可拿到 metadata（无需改 Controller）

### 验证

```
① 寒暄测试: "你好" → 正常回复，无延迟
② 知识测试: "退款政策是什么" → AI 基于知识库文档回答
③ 无知识库: 不上传文档时 → 普通对话不受影响
```

---

## Phase 6: 前端适配

**目标**: 知识库管理面板、来源卡片、快捷上传全部可用。

### 任务

- [x] 6.1 修改 `types/index.ts`
  - 新增 `RAGDocument` 接口（id, name, type, size, chunkCount, createdAt）
  - 新增 `RAGSource` 接口（chunkId, documentId, documentName, content, score, sectionTitle, pageNumber）
  - `Message` 增加 `metadata?: string`（JSON 原文）+ `sources?: RAGSource[]`（解析后）

- [x] 6.2 修改 `utils/api.ts`
  - `uploadDocument(file, onProgress?)` — XMLHttpRequest 带进度回调
  - `getDocuments()` — 获取文档列表
  - `deleteDocument(id)` — 删除文档
  - `searchDocuments(query, topK?)` — 向量检索

- [x] 6.3 修改 `stores/index.ts`
  - 新增 `ragDocuments: RAGDocument[]`
  - 新增 `knowledgeBaseOpen: boolean`
  - 新增 `loadDocuments()`、`uploadDocument()`、`deleteDocument()` 方法
  - `loadMessages()` 自动解析 metadata JSON → sources
  - `sendMessage()` 流结束后调用 `loadMessages()` 获取 sources

- [x] 6.4 新增 `components/FileUploader.vue`
  - 拖拽区域 + 点击选择
  - 文件类型校验（pdf/docx/md/txt，上限 50MB）
  - 上传进度条（渐变色填充）
  - 使用现有 CSS 设计变量

- [x] 6.5 新增 `components/KnowledgeBase.vue`
  - 右侧滑出面板（glass-strong + 毛玻璃 overlay）
  - FileUploader 嵌入顶部
  - 文档列表（图标 + 名称 + 大小/分块数/类型 + 时间）
  - 删除按钮（confirm 确认弹窗）
  - CSS transition 滑入/滑出动画

- [x] 6.6 新增 `components/RAGSourceCard.vue`
  - 文档名 + 片段摘要（可展开）
  - 环形相似度百分比（conic-gradient 圆环）
  - 颜色按分数分级（>80% cyan / >60% aurora / 其他 rose）
  - aurora-border hover 效果

- [x] 6.7 修改 `components/Sidebar.vue`
  - "新对话"按钮下方新增"知识库"按钮（📚 图标）
  - 点击切换 `store.state.knowledgeBaseOpen`
  - cyan 配色 hover 效果

- [x] 6.8 修改 `components/InputArea.vue`
  - 输入框左侧新增附件按钮（📎 回形针图标）
  - 点击触发 `<input type="file">` 选择文件
  - 上传完成后自动刷新知识库列表（toast 通知）

- [x] 6.9 修改 `components/MessageBubble.vue`
  - `message.sources` 有数据时，在气泡底部渲染 `RAGSourceCard`
  - 仅 assistant 消息显示来源
  - 引用数量标签 + 样式统一

- [x] 6.10 样式全部通过组件 scoped CSS 覆盖，未修改 main.css

### 验证

```
① 点击侧边栏"知识库" → 面板滑出
② 拖拽 PDF → 上传成功 → 列表中出现
③ 输入框附件按钮上传 → toast 通知
④ 知识类对话 → 消息气泡底部显示来源卡片
⑤ 深色/浅色主题切换 → 新组件均适配
```

---

## Phase 7: 端到端验证 + 回归

**目标**: 全链路跑通，确认无回归问题。

### 任务

- [x] 7.1 准备测试文档（TXT 或 PDF，包含明确的事实信息）
- [x] 7.2 上传测试文档 → 确认数据库中 `document` + `document_chunk` 有数据
- [x] 7.3 发起知识类对话 → 验证 AI 回答引用了文档内容
- [x] 7.4 发起寒暄对话 → 验证跳过检索、无额外延迟
- [x] 7.5 回归测试：
  - 登录/注册正常 ✅
  - 普通对话（无知识库）正常 ✅
  - 会话管理（创建/删除/置顶/重命名）正常 ✅
  - 主题切换正常（前端独立验证）
  - 修改密码正常 ✅
- [x] 7.6 修复 JSONB 类型匹配问题（DocumentIngestService + MessageService）
- [x] 7.7 修复 chunk metadata 字段名映射（snake_case → 统一读取）
- [x] 7.8 修复 doOnComplete → doFinally（客户端断开仍保存消息）

### 验证

```
✅ 上传文档 → 向量入库 (test-doc.txt, 1445 bytes, 1 chunk, 1024-dim embedding)
✅ 知识提问 → AI 基于文档回答（退款政策完整正确）
✅ sources JSON 存入 message.metadata（score: 0.744, 3 sources）
✅ 寒暄 → 无检索延迟（metadata = {}）
✅ 所有原有功能不受影响（登录/注册/密码修改/会话CRUD/软删除）
```

### 已知遗留问题

| 问题 | 影响 | 计划 |
|------|------|------|
| chunk metadata 字段名映射修复需重启后端 | documentName 暂时为空 | 已修复 Agent.java + DocumentIngestService.java，下次重启生效 |
| 前端上传端点路径为 `/api/rag/documents/upload` | 前端可能需要调整 API 地址 | 确认前端 api.ts 中路径一致 |

---

## 完成标志

七个 Phase 全部通过后，一期 RAG 功能即为 **已交付**。

二期升级（Hybrid Search / Rerank / Query Rewrite）只需替换 `RAGConfig.java` 中的 Bean 注入，不动任何其他代码。
