import { computed, reactive } from 'vue'
import { api } from '@/utils/api'
import type { Conversation, Message, RAGDocument, RAGDocumentChunk, RAGSource, ToastItem } from '@/types'

const THEME_VERSION = 'white-ui-v1'

if (localStorage.getItem('theme-version') !== THEME_VERSION) {
  localStorage.setItem('theme', 'light')
  localStorage.setItem('theme-version', THEME_VERSION)
}

const state = reactive({
  authenticated: !!localStorage.getItem('token') || !!sessionStorage.getItem('token'),
  conversations: [] as Conversation[],
  currentConversationId: null as string | null,
  messages: [] as Message[],
  isLoading: false,
  streamingContent: '',
  sidebarOpen: false,
  sidebarCollapsed: localStorage.getItem('sidebar-collapsed') === 'true',
  theme: (localStorage.getItem('theme') as 'dark' | 'light') || 'light',
  serviceOnline: false,

  knowledgeBaseOpen: false,
  ragDocuments: [] as RAGDocument[],
  previewDocumentId: null as string | null,
  previewActiveChunkId: null as string | null,
  previewSelectedDocument: null as RAGDocument | null,
  previewChunks: [] as RAGDocumentChunk[],
  previewPage: 1,
  previewPageSize: 20,
  previewTotal: 0,
  previewKeyword: '',
  previewLoading: false,
})

const toasts = reactive<ToastItem[]>([])
let toastIdCounter = 0
let remoteWriteEnabled = false

async function refreshRemoteWriteMode() {
  remoteWriteEnabled = (await api.remoteWriteEnabled().catch(() => ({ enabled: false }))).enabled
  return remoteWriteEnabled
}

function addToast(message: string, type: 'success' | 'error' | 'info' = 'info', duration = 3000) {
  const id = ++toastIdCounter
  toasts.push({ id, message, type, duration })
  if (duration > 0) {
    setTimeout(() => removeToast(id), duration)
  }
}

function removeToast(id: number) {
  const idx = toasts.findIndex((toast) => toast.id === id)
  if (idx >= 0) toasts.splice(idx, 1)
}

function setTheme(theme: 'dark' | 'light') {
  state.theme = theme
  localStorage.setItem('theme', theme)
  localStorage.setItem('theme-version', THEME_VERSION)
  document.documentElement.setAttribute('data-theme', theme)
}

function setSidebarCollapsed(collapsed: boolean) {
  state.sidebarCollapsed = collapsed
  localStorage.setItem('sidebar-collapsed', String(collapsed))
}

const currentConversation = computed(() =>
  state.conversations.find((conversation) => conversation.id === state.currentConversationId),
)

async function loadConversations() {
  try {
    const remote = await refreshRemoteWriteMode()
    state.conversations = remote ? await api.getRemoteConversations() : await api.getConversations()
  } catch (e: any) {
    if (e.message === 'UNAUTHORIZED') {
      state.authenticated = false
      return
    }
    addToast('加载对话列表失败', 'error')
  }
}

async function loadMessages(conversationId: string) {
  try {
    state.messages = remoteWriteEnabled
      ? await api.getRemoteMessages(conversationId)
      : await api.getMessages(conversationId)
    state.messages.forEach((message) => {
      if (!message.metadata) return
      try {
        const meta = JSON.parse(message.metadata)
        if (meta.sources && Array.isArray(meta.sources)) {
          message.sources = meta.sources as RAGSource[]
        }
      } catch {
        // metadata 解析失败时忽略，不影响消息展示。
      }
    })
  } catch {
    addToast('加载消息失败', 'error')
  }
}

async function selectConversation(id: string) {
  state.currentConversationId = id
  state.streamingContent = ''
  await loadMessages(id)
}

async function deleteConversation(id: string) {
  try {
    await (remoteWriteEnabled ? api.deleteRemoteConversation(id) : api.deleteConversation(id))
    state.conversations = state.conversations.filter((conversation) => conversation.id !== id)
    if (state.currentConversationId === id) {
      state.currentConversationId = null
      state.messages = []
    }
    addToast('对话已删除', 'success')
  } catch {
    addToast('删除失败', 'error')
  }
}

async function renameConversation(id: string, title: string) {
  try {
    const result = remoteWriteEnabled
      ? await api.renameRemoteConversation(id, title)
      : await api.renameConversation(id, title)
    if (result.success) {
      const idx = state.conversations.findIndex((conversation) => conversation.id === id)
      if (idx >= 0) state.conversations[idx].title = title
      addToast('标题已更新', 'success')
    } else {
      addToast(result.error || '重命名失败', 'error')
    }
  } catch {
    addToast('重命名失败', 'error')
  }
}

async function pinConversation(id: string) {
  try {
    const idx = state.conversations.findIndex((conversation) => conversation.id === id)
    const result = remoteWriteEnabled
      ? await api.pinRemoteConversation(id, !(idx >= 0 && state.conversations[idx].pinned))
      : await api.pinConversation(id)
    if (idx >= 0) {
      state.conversations[idx].pinned = result.pinned ? 1 : 0
      state.conversations.sort((a, b) => (b.pinned ?? 0) - (a.pinned ?? 0))
    }
  } catch {
    addToast('操作失败', 'error')
  }
}

async function sendMessage(message: string) {
  if (state.isLoading || !message.trim()) return

  state.isLoading = true
  state.streamingContent = ''

  const remoteWrite = await refreshRemoteWriteMode()

  const userMsg: Message = {
    id: `local-user-${Date.now()}`,
    conversationId: state.currentConversationId || '',
    role: 'user',
    content: message,
    createdAt: new Date().toISOString(),
  }
  state.messages.push(userMsg)

  const assistantMsg: Message = {
    id: `local-assistant-${Date.now()}`,
    conversationId: state.currentConversationId || '',
    role: 'assistant',
    content: '',
    createdAt: new Date().toISOString(),
  }
  state.messages.push(assistantMsg)

  try {
    const stream = remoteWrite ? api.remoteChatStream : api.chatStream
    await stream(
      message,
      (chunk) => {
        state.streamingContent += chunk
      },
      (remoteConversationId?: string) => {
        if (remoteConversationId) {
          state.currentConversationId = remoteConversationId
          userMsg.conversationId = remoteConversationId
          assistantMsg.conversationId = remoteConversationId
        }
        assistantMsg.content = state.streamingContent
        state.isLoading = false
        state.streamingContent = ''

        loadConversations().then(() => {
          if (state.currentConversationId || state.conversations.length === 0) return
          const newest = state.conversations.reduce(
            (a, b) => (new Date(b.createdAt).getTime() > new Date(a.createdAt).getTime() ? b : a),
            state.conversations[0],
          )
          if (newest) state.currentConversationId = newest.id
        })
        setTimeout(() => loadConversations(), 1200)

        if (state.currentConversationId) {
          loadMessages(state.currentConversationId)
        }
      },
      state.currentConversationId || undefined,
    )
  } catch (e: any) {
    if (e.message === 'UNAUTHORIZED') {
      state.authenticated = false
    } else {
      addToast('发送消息失败', 'error')
    }
    state.isLoading = false
    state.messages.pop()
  }
}

function newConversation() {
  state.currentConversationId = null
  state.messages = []
  state.streamingContent = ''
}

async function checkHealth() {
  try {
    const res = await api.healthCheck()
    state.serviceOnline = res === 'ok'
  } catch {
    state.serviceOnline = false
  }
}

async function loadDocuments() {
  try {
    state.ragDocuments = await api.getDocuments()
    if (state.previewDocumentId) {
      state.previewSelectedDocument =
        state.ragDocuments.find((document) => document.id === state.previewDocumentId) || state.previewSelectedDocument
    }
  } catch (e: any) {
    if (e.message !== 'UNAUTHORIZED') {
      addToast('加载知识库列表失败', 'error')
    }
  }
}

async function openDocumentPreview(documentId: string, chunkId?: string) {
  state.knowledgeBaseOpen = true
  state.previewDocumentId = documentId
  state.previewActiveChunkId = chunkId || null
  state.previewKeyword = ''
  state.previewPage = 1
  state.previewLoading = true

  const cached = state.ragDocuments.find((document) => document.id === documentId)
  state.previewSelectedDocument = cached || null

  if (!cached) {
    try {
      state.previewSelectedDocument = await api.getDocument(documentId)
    } catch (e: any) {
      if (e.message === 'UNAUTHORIZED') {
        state.authenticated = false
      } else {
        addToast('加载文档详情失败', 'error')
      }
      state.previewLoading = false
      return
    }
  }

  await loadPreviewChunks(1, Boolean(chunkId))
}

function closeDocumentPreview() {
  state.previewDocumentId = null
  state.previewActiveChunkId = null
  state.previewSelectedDocument = null
  state.previewChunks = []
  state.previewPage = 1
  state.previewTotal = 0
  state.previewKeyword = ''
  state.previewLoading = false
}

async function loadPreviewChunks(page = state.previewPage, includeActiveChunk = false) {
  if (!state.previewDocumentId) return

  state.previewLoading = true
  try {
    const result = await api.getDocumentChunks(
      state.previewDocumentId,
      page,
      state.previewPageSize,
      state.previewKeyword,
      includeActiveChunk ? state.previewActiveChunkId : null,
    )
    state.previewPage = result.page
    state.previewPageSize = result.pageSize
    state.previewTotal = result.total
    state.previewChunks = result.items
  } catch (e: any) {
    if (e.message === 'UNAUTHORIZED') {
      state.authenticated = false
    } else {
      addToast('加载文档预览失败', 'error')
    }
  } finally {
    state.previewLoading = false
  }
}

async function searchPreviewChunks(keyword: string) {
  state.previewKeyword = keyword
  state.previewActiveChunkId = null
  await loadPreviewChunks(1)
}

async function uploadDocument(file: File) {
  try {
    const doc = await api.uploadDocument(file, () => {
      // 上传进度由上传组件自身展示，当前 store 只负责刷新知识库列表。
    })
    addToast(`上传成功: ${doc.name}（${doc.chunkCount || 0} 个分块）`, 'success')
    await loadDocuments()
    return doc
  } catch (e: any) {
    if (e.message === 'UNAUTHORIZED') {
      state.authenticated = false
      addToast('请重新登录', 'error')
    } else {
      addToast('上传失败: ' + (e.message || '未知错误'), 'error')
    }
    throw e
  }
}

async function deleteDocument(id: string) {
  try {
    await api.deleteDocument(id)
    addToast('文档已删除', 'success')
    if (state.previewDocumentId === id) {
      closeDocumentPreview()
    }
    await loadDocuments()
  } catch {
    addToast('删除失败', 'error')
  }
}

export const store = {
  state,
  toasts,
  currentConversation,
  addToast,
  removeToast,
  setTheme,
  setSidebarCollapsed,
  loadConversations,
  loadMessages,
  selectConversation,
  deleteConversation,
  renameConversation,
  pinConversation,
  sendMessage,
  newConversation,
  checkHealth,
  loadDocuments,
  openDocumentPreview,
  closeDocumentPreview,
  loadPreviewChunks,
  searchPreviewChunks,
  uploadDocument,
  deleteDocument,
}
