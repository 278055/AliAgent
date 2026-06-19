-- 启用 pgvector 扩展 (需要数据库用户有 superuser 权限)
CREATE EXTENSION IF NOT EXISTS vector;

-- =============================================
-- 会话表
-- =============================================
CREATE TABLE IF NOT EXISTS conversation (
    id VARCHAR(36) PRIMARY KEY,
    title VARCHAR(255) DEFAULT '新对话',
    deleted  SMALLINT DEFAULT 0,
    pinned   SMALLINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE conversation IS '会话表';
COMMENT ON COLUMN conversation.id IS '会话ID';
COMMENT ON COLUMN conversation.title IS '会话标题';
COMMENT ON COLUMN conversation.deleted IS '软删除标志：0=未删除，1=已删除';
COMMENT ON COLUMN conversation.pinned IS '置顶标志：0=未置顶，1=已置顶';
COMMENT ON COLUMN conversation.created_at IS '创建时间';
COMMENT ON COLUMN conversation.updated_at IS '更新时间';

-- =============================================
-- 消息表
-- =============================================
CREATE TABLE IF NOT EXISTS message (
    id VARCHAR(36) PRIMARY KEY,
    conversation_id VARCHAR(36) NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_message_conversation FOREIGN KEY (conversation_id) REFERENCES conversation(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_message_conversation_id ON message(conversation_id);
CREATE INDEX IF NOT EXISTS idx_message_created_at ON message(created_at);

COMMENT ON TABLE message IS '消息表';
COMMENT ON COLUMN message.id IS '消息ID';
COMMENT ON COLUMN message.conversation_id IS '所属会话ID';
COMMENT ON COLUMN message.role IS '角色: user/assistant';
COMMENT ON COLUMN message.content IS '消息内容';
COMMENT ON COLUMN message.metadata IS '扩展元数据 (JSON): sources 引用等';
COMMENT ON COLUMN message.created_at IS '创建时间';

-- =============================================
-- 知识库文档表（元信息）
-- =============================================
CREATE TABLE IF NOT EXISTS document (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(500) NOT NULL,
    type VARCHAR(50),
    size BIGINT,
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE document IS '知识库文档表（元信息）';
COMMENT ON COLUMN document.name IS '文档名称';
COMMENT ON COLUMN document.type IS '文件类型 (pdf/docx/md/txt)';
COMMENT ON COLUMN document.size IS '文件大小 (bytes)';
COMMENT ON COLUMN document.metadata IS '扩展元数据 (JSON)';

-- =============================================
-- 文档分块表（向量存储）
-- =============================================
CREATE TABLE IF NOT EXISTS document_chunk (
    id VARCHAR(36) PRIMARY KEY,
    document_id VARCHAR(36) NOT NULL REFERENCES document(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    embedding vector(1024),
    section_title VARCHAR(500),
    page_number INTEGER,
    chunk_index INTEGER DEFAULT 0,
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_chunk_document_id ON document_chunk(document_id);
CREATE INDEX IF NOT EXISTS idx_chunk_embedding ON document_chunk USING hnsw (embedding vector_cosine_ops);
CREATE INDEX IF NOT EXISTS idx_chunk_created_at ON document_chunk(created_at);

COMMENT ON TABLE document_chunk IS '文档分块表（向量存储）';
COMMENT ON COLUMN document_chunk.document_id IS '所属文档ID';
COMMENT ON COLUMN document_chunk.content IS '分块文本内容';
COMMENT ON COLUMN document_chunk.embedding IS '文本向量 (1024维, text-embedding-v3)';
COMMENT ON COLUMN document_chunk.section_title IS '所属章节标题';
COMMENT ON COLUMN document_chunk.page_number IS '所在页码';
COMMENT ON COLUMN document_chunk.chunk_index IS '分块序号';

-- =============================================
-- 用户表
-- =============================================
CREATE TABLE IF NOT EXISTS "users" (
    id VARCHAR(36) PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(100) UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_users_username ON "users"(username);
CREATE INDEX IF NOT EXISTS idx_users_email ON "users"(email);

-- =============================================
-- 更新时间戳触发器
-- =============================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

DROP TRIGGER IF EXISTS update_conversation_updated_at ON conversation;
CREATE TRIGGER update_conversation_updated_at
    BEFORE UPDATE ON conversation
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_users_updated_at ON "users";
CREATE TRIGGER update_users_updated_at
    BEFORE UPDATE ON "users"
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
