<template>
  <transition name="panel-slide">
    <div v-if="store.state.knowledgeBaseOpen" class="kb-overlay" @click.self="close">
      <div class="kb-panel">
        <aside class="kb-sidebar">
          <div class="kb-header">
            <div>
              <h3 class="kb-title">知识库</h3>
              <p class="kb-subtitle">查看文档分块，确认 AI 实际检索的内容。</p>
            </div>
            <button class="icon-btn" @click="close" title="关闭">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18" />
                <line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
          </div>

          <div class="kb-upload">
            <FileUploader @uploaded="onUploaded" />
          </div>

          <div class="kb-doc-search">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="11" cy="11" r="8" />
              <path d="M21 21l-4.35-4.35" />
            </svg>
            <input
              v-model="documentSearchKeyword"
              type="search"
              placeholder="搜索文档名称"
            />
            <button
              v-if="documentSearchKeyword"
              class="search-clear-btn"
              title="清空搜索"
              @click="documentSearchKeyword = ''"
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18" />
                <line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
          </div>

          <div class="kb-list-header">
            <span>文档</span>
            <small>{{ filteredDocuments.length }} / {{ store.state.ragDocuments.length }} 个</small>
          </div>

          <div class="kb-list">
            <div v-if="store.state.ragDocuments.length === 0" class="kb-empty">
              <p>暂无文档</p>
              <span>支持 PDF、DOCX、MD、TXT</span>
            </div>
            <div v-else-if="filteredDocuments.length === 0" class="kb-empty">
              <p>未找到匹配文档</p>
              <span>换个名称试试</span>
            </div>

            <div
              v-for="doc in filteredDocuments"
              :key="doc.id"
              :class="['kb-doc-item', { active: doc.id === store.state.previewDocumentId }]"
              @click="store.openDocumentPreview(doc.id)"
            >
              <div class="kb-doc-icon">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z" />
                  <polyline points="14 2 14 8 20 8" />
                  <line x1="16" y1="13" x2="8" y2="13" />
                  <line x1="16" y1="17" x2="8" y2="17" />
                </svg>
              </div>
              <div class="kb-doc-info">
                <span class="kb-doc-name">{{ doc.name }}</span>
                <span class="kb-doc-meta">
                  {{ formatSize(doc.size) }}
                  <template v-if="doc.chunkCount != null"> · {{ doc.chunkCount }} 分块</template>
                  <template v-if="doc.type"> · {{ doc.type.toUpperCase() }}</template>
                </span>
                <span class="kb-doc-time">{{ formatTime(doc.createdAt) }}</span>
              </div>
              <button class="doc-delete-btn" @click.stop="handleDelete(doc)" title="删除文档">
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="3 6 5 6 21 6" />
                  <path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2" />
                </svg>
              </button>
            </div>
          </div>
        </aside>

        <section class="preview-panel">
          <div v-if="store.state.previewLoading && !selectedDocument" class="preview-loading">
            <span class="loading-spinner"></span>
            <p>正在加载预览...</p>
          </div>

          <div v-else-if="!selectedDocument" class="preview-empty">
            <div class="preview-empty-icon">
              <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7">
                <path d="M4 19.5A2.5 2.5 0 016.5 17H20" />
                <path d="M6.5 2H20v20H6.5A2.5 2.5 0 014 19.5v-15A2.5 2.5 0 016.5 2z" />
              </svg>
            </div>
            <p>选择左侧文档查看预览</p>
          </div>

          <template v-else>
            <header class="preview-header">
              <div class="preview-title-wrap">
                <h3 class="preview-title">{{ selectedDocument.name }}</h3>
                <p class="preview-meta">
                  {{ selectedDocument.type?.toUpperCase() || '文档' }}
                  · {{ formatSize(selectedDocument.size) }}
                  · {{ selectedDocument.chunkCount || store.state.previewTotal }} 分块
                  · {{ formatTime(selectedDocument.createdAt) }}
                </p>
              </div>
              <button class="icon-btn mobile-back-btn" @click="store.closeDocumentPreview()" title="返回文档列表">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M19 12H5" />
                  <path d="M12 19l-7-7 7-7" />
                </svg>
              </button>
            </header>

            <div class="preview-toolbar">
              <div class="search-box">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="11" cy="11" r="8" />
                  <path d="M21 21l-4.35-4.35" />
                </svg>
                <input
                  v-model="keywordDraft"
                  type="search"
                  placeholder="搜索分块内容"
                  @keydown.enter="submitSearch"
                />
              </div>
              <button class="toolbar-btn" @click="submitSearch">搜索</button>
              <button v-if="store.state.previewKeyword" class="toolbar-btn ghost" @click="clearSearch">清空</button>
            </div>

            <div class="preview-status">
              <span>解析文本预览</span>
              <small>
                {{ store.state.previewTotal }} 个分块
                <template v-if="store.state.previewKeyword"> · 搜索 “{{ store.state.previewKeyword }}”</template>
              </small>
            </div>

            <div ref="chunkListRef" class="chunk-list">
              <div v-if="store.state.previewLoading" class="chunk-state loading">
                <span class="loading-spinner"></span>
                <p>正在加载预览...</p>
              </div>
              <div v-else-if="store.state.previewChunks.length === 0" class="chunk-state">没有找到可预览的分块</div>

              <template v-else>
                <article
                  v-for="chunk in store.state.previewChunks"
                  :key="chunk.id"
                  :data-chunk-id="chunk.id"
                  :class="['chunk-card', { active: chunk.id === store.state.previewActiveChunkId }]"
                >
                  <div class="chunk-card-header">
                    <span>分块 #{{ chunk.chunkIndex + 1 }}</span>
                    <small>
                      <template v-if="chunk.pageNumber != null">第 {{ chunk.pageNumber }} 页</template>
                      <template v-if="chunk.sectionTitle"> · {{ chunk.sectionTitle }}</template>
                    </small>
                  </div>
                  <p class="chunk-content" v-html="highlightText(chunk.content)"></p>
                </article>
              </template>
            </div>

            <footer v-if="totalPages > 1" class="preview-pagination">
              <button class="toolbar-btn ghost" :disabled="store.state.previewPage <= 1" @click="goPage(store.state.previewPage - 1)">
                上一页
              </button>
              <span>{{ store.state.previewPage }} / {{ totalPages }}</span>
              <button class="toolbar-btn ghost" :disabled="store.state.previewPage >= totalPages" @click="goPage(store.state.previewPage + 1)">
                下一页
              </button>
            </footer>
          </template>
        </section>
      </div>
    </div>
  </transition>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { store } from '@/stores'
import { formatRelativeTime } from '@/utils/time'
import FileUploader from '@/components/FileUploader.vue'
import type { RAGDocument } from '@/types'

const keywordDraft = ref('')
const documentSearchKeyword = ref('')
const chunkListRef = ref<HTMLElement | null>(null)

const selectedDocument = computed(() => store.state.previewSelectedDocument)
const filteredDocuments = computed(() => {
  const keyword = documentSearchKeyword.value.trim().toLowerCase()
  if (!keyword) return store.state.ragDocuments
  return store.state.ragDocuments.filter((doc) => doc.name.toLowerCase().includes(keyword))
})
const totalPages = computed(() => {
  return Math.max(1, Math.ceil(store.state.previewTotal / store.state.previewPageSize))
})

function close() {
  store.state.knowledgeBaseOpen = false
}

function onUploaded(_doc: any) {
  // 上传后的列表刷新由 store.uploadDocument 处理。
}

function formatTime(dateStr: string) {
  return formatRelativeTime(dateStr)
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

function escapeHtml(value: string) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;')
}

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function highlightText(content: string) {
  const escaped = escapeHtml(content || '')
  const keyword = store.state.previewKeyword.trim()
  if (!keyword) return escaped
  const pattern = new RegExp(`(${escapeRegExp(escapeHtml(keyword))})`, 'gi')
  return escaped.replace(pattern, '<mark>$1</mark>')
}

async function submitSearch() {
  await store.searchPreviewChunks(keywordDraft.value)
}

async function clearSearch() {
  keywordDraft.value = ''
  await store.searchPreviewChunks('')
}

async function goPage(page: number) {
  await store.loadPreviewChunks(page)
  await nextTick()
  chunkListRef.value?.scrollTo({ top: 0, behavior: 'smooth' })
}

async function handleDelete(doc: RAGDocument) {
  if (!confirm(`确定删除「${doc.name}」吗？删除后相关向量数据也将被移除。`)) return
  await store.deleteDocument(doc.id)
}

async function scrollToActiveChunk() {
  await nextTick()
  const activeChunkId = store.state.previewActiveChunkId
  if (!activeChunkId || !chunkListRef.value) return
  const target = chunkListRef.value.querySelector(`[data-chunk-id="${activeChunkId}"]`)
  target?.scrollIntoView({ block: 'center', behavior: 'smooth' })
}

watch(
  () => store.state.previewKeyword,
  (keyword) => {
    keywordDraft.value = keyword
  },
)

watch(
  () => [store.state.previewChunks, store.state.previewActiveChunkId],
  () => {
    scrollToActiveChunk()
  },
  { deep: true },
)

onMounted(() => {
  store.loadDocuments()
})
</script>

<style scoped>
.kb-overlay {
  position: fixed;
  inset: 0;
  z-index: 300;
  display: flex;
  justify-content: flex-end;
  background: rgba(0, 0, 0, 0.22);
}

.kb-panel {
  width: min(980px, 94vw);
  height: 100%;
  display: grid;
  grid-template-columns: 360px minmax(0, 1fr);
  background: var(--bg-surface);
  border-left: 1px solid var(--border-subtle);
  box-shadow: var(--shadow-md);
}

.kb-sidebar {
  min-width: 0;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--border-subtle);
}

.kb-header {
  flex-shrink: 0;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  padding: 22px 22px 16px;
  border-bottom: 1px solid var(--border-subtle);
}

.kb-title,
.preview-title {
  color: var(--text-primary);
  font-family: var(--font-display);
  font-size: 20px;
  font-weight: 650;
  letter-spacing: 0;
}

.kb-subtitle {
  margin-top: 5px;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.5;
}

.icon-btn,
.doc-delete-btn {
  display: grid;
  place-items: center;
  border: none;
  border-radius: 9px;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  transition: background var(--transition-fast), color var(--transition-fast);
}

.icon-btn {
  width: 34px;
  height: 34px;
  flex-shrink: 0;
}

.icon-btn:hover,
.doc-delete-btn:hover {
  background: var(--bg-surface-muted);
  color: var(--danger);
}

.kb-upload {
  flex-shrink: 0;
  padding: 18px 22px;
}

.kb-doc-search {
  flex-shrink: 0;
  height: 38px;
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 22px 14px;
  padding: 0 10px;
  border: 1px solid var(--border-subtle);
  border-radius: 10px;
  background: var(--bg-surface);
  color: var(--text-tertiary);
  transition: border-color var(--transition-fast), box-shadow var(--transition-fast);
}

.kb-doc-search:focus-within {
  border-color: var(--accent);
  box-shadow: var(--input-focus-shadow);
}

.kb-doc-search input {
  min-width: 0;
  flex: 1;
  height: 34px;
  border: none;
  outline: none;
  background: transparent;
  color: var(--text-primary);
  font-size: 13px;
}

.kb-doc-search input::placeholder {
  color: var(--text-tertiary);
}

.search-clear-btn {
  width: 24px;
  height: 24px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  border: none;
  border-radius: 7px;
  background: transparent;
  color: var(--text-tertiary);
  cursor: pointer;
  transition: background var(--transition-fast), color var(--transition-fast);
}

.search-clear-btn:hover {
  background: var(--bg-surface-muted);
  color: var(--text-primary);
}

.kb-list-header {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 22px 10px;
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 650;
}

.kb-list-header small,
.preview-meta,
.preview-status small {
  color: var(--text-tertiary);
  font-size: 12px;
  font-weight: 500;
}

.kb-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 0 14px 18px;
}

.kb-empty,
.preview-empty,
.chunk-state {
  padding: 34px 8px;
  text-align: center;
  color: var(--text-secondary);
  font-size: 14px;
}

.kb-empty span {
  display: block;
  margin-top: 5px;
  color: var(--text-tertiary);
  font-size: 12px;
}

.kb-doc-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 8px;
  border-radius: 10px;
  cursor: pointer;
  transition: background var(--transition-fast);
}

.kb-doc-item:hover,
.kb-doc-item.active {
  background: var(--bg-surface-muted);
}

.kb-doc-item.active .kb-doc-icon {
  background: var(--accent);
  color: #fff;
}

.kb-doc-icon {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  border-radius: 10px;
  background: var(--accent-soft);
  color: var(--accent);
}

.kb-doc-info {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.kb-doc-name,
.preview-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kb-doc-name {
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 550;
}

.kb-doc-meta,
.kb-doc-time {
  color: var(--text-tertiary);
  font-size: 12px;
}

.doc-delete-btn {
  width: 32px;
  height: 32px;
  flex-shrink: 0;
}

.preview-panel {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.preview-empty {
  height: 100%;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 10px;
}

.preview-loading {
  height: 100%;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 12px;
  color: var(--text-secondary);
  font-size: 14px;
}

.loading-spinner {
  width: 26px;
  height: 26px;
  display: inline-block;
  border: 2px solid var(--border-subtle);
  border-top-color: var(--accent);
  border-radius: 999px;
  animation: preview-spin 0.8s linear infinite;
}

.chunk-state.loading {
  min-height: 220px;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 10px;
}

.chunk-state.loading p,
.preview-loading p {
  margin: 0;
}

.preview-empty-icon {
  width: 58px;
  height: 58px;
  display: grid;
  place-items: center;
  border-radius: 16px;
  background: var(--accent-soft);
  color: var(--accent);
}

.preview-header {
  flex-shrink: 0;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  padding: 22px 24px 14px;
  border-bottom: 1px solid var(--border-subtle);
}

.preview-title-wrap {
  min-width: 0;
}

.preview-title {
  max-width: 100%;
}

.preview-meta {
  margin-top: 5px;
}

.mobile-back-btn {
  display: none;
}

.preview-toolbar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 24px;
  border-bottom: 1px solid var(--border-subtle);
}

.search-box {
  min-width: 0;
  flex: 1;
  height: 38px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 11px;
  border: 1px solid var(--border-subtle);
  border-radius: 10px;
  color: var(--text-tertiary);
  background: var(--bg-surface);
}

.search-box:focus-within {
  border-color: var(--accent);
  box-shadow: var(--input-focus-shadow);
}

.search-box input {
  min-width: 0;
  flex: 1;
  height: 34px;
  border: none;
  outline: none;
  background: transparent;
  color: var(--text-primary);
  font-size: 14px;
}

.toolbar-btn {
  height: 38px;
  padding: 0 13px;
  border: none;
  border-radius: 10px;
  background: var(--accent);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background var(--transition-fast), opacity var(--transition-fast);
}

.toolbar-btn:hover:not(:disabled) {
  background: var(--accent-hover);
}

.toolbar-btn.ghost {
  border: 1px solid var(--border-subtle);
  background: var(--bg-surface);
  color: var(--text-secondary);
}

.toolbar-btn.ghost:hover:not(:disabled) {
  background: var(--bg-surface-muted);
  color: var(--text-primary);
}

.toolbar-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.preview-status {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 24px;
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 650;
  border-bottom: 1px solid var(--border-subtle);
}

.chunk-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 18px 24px 22px;
}

.chunk-card {
  padding: 14px 15px;
  border: 1px solid var(--border-subtle);
  border-radius: 10px;
  background: var(--bg-surface);
  transition: border-color var(--transition-fast), background var(--transition-fast), box-shadow var(--transition-fast);
}

.chunk-card + .chunk-card {
  margin-top: 12px;
}

.chunk-card.active {
  border-color: var(--accent);
  background: color-mix(in srgb, var(--accent-soft) 32%, var(--bg-surface));
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--accent) 12%, transparent);
}

.chunk-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 9px;
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 650;
}

.chunk-card-header small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text-tertiary);
  font-size: 12px;
  font-weight: 500;
}

.chunk-content {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.68;
}

.chunk-content :deep(mark) {
  padding: 0 2px;
  border-radius: 4px;
  background: color-mix(in srgb, var(--accent) 22%, transparent);
  color: var(--text-primary);
}

.preview-pagination {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 12px 24px;
  border-top: 1px solid var(--border-subtle);
  color: var(--text-secondary);
  font-size: 13px;
}

.panel-slide-enter-active,
.panel-slide-leave-active {
  transition: opacity 0.22s ease;
}

.panel-slide-enter-active .kb-panel,
.panel-slide-leave-active .kb-panel {
  transition: transform 0.28s ease;
}

.panel-slide-enter-from,
.panel-slide-leave-to {
  opacity: 0;
}

.panel-slide-enter-from .kb-panel,
.panel-slide-leave-to .kb-panel {
  transform: translateX(100%);
}

@keyframes preview-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 760px) {
  .kb-panel {
    width: 100vw;
    grid-template-columns: 1fr;
  }

  .kb-sidebar {
    display: flex;
  }

  .preview-panel {
    display: none;
  }

  .kb-panel:has(.chunk-list) .kb-sidebar {
    display: none;
  }

  .kb-panel:has(.chunk-list) .preview-panel {
    display: flex;
  }

  .mobile-back-btn {
    display: grid;
  }

  .preview-toolbar,
  .preview-status,
  .chunk-list,
  .preview-header {
    padding-left: 18px;
    padding-right: 18px;
  }
}
</style>
