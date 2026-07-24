import type { Message, Conversation, RAGDocument, RAGDocumentChunk, PageResult } from '@/types'

const BASE = '/api'

function getToken(): string | null {
  return localStorage.getItem('token') || sessionStorage.getItem('token')
}

function setToken(token: string, persist: boolean) {
  const store = persist ? localStorage : sessionStorage
  store.setItem('token', token)
}

function clearToken() {
  localStorage.removeItem('token')
  sessionStorage.removeItem('token')
}

function authHeaders(): Record<string, string> {
  const token = getToken()
  return token ? { Authorization: `Bearer ${token}` } : {}
}

interface RResponse<T> {
  code: number
  msg: string
  data: T
}

interface RemoteConversation {
  id: string
  title: string
  status: string
  pinned: boolean
  createdAt?: string
  updatedAt?: string
}

interface RemoteMessage {
  id: string
  conversationId?: string
  sequence: number
  senderType?: 'USER' | 'STAFF' | 'AI' | 'SYSTEM'
  content: string
  status: string
  createdAt?: string
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    ...options,
    headers: {
      ...authHeaders(),
      'Content-Type': 'application/json',
      ...options.headers,
    },
  })

  if (res.status === 401) {
    clearToken()
    throw new Error('UNAUTHORIZED')
  }

  if (!res.ok) {
    const body = await res.json().catch(() => ({ msg: res.statusText }))
    throw new Error(body.msg || body.message || `HTTP ${res.status}`)
  }

  const json: any = await res.json()

  // If backend returns R<T> wrapper ({ code, msg, data }), unwrap it
  if (json && typeof json.code === 'number' && json.data !== undefined) {
    if (json.code !== 200) {
      throw new Error(json.msg || '操作失败')
    }
    return json.data as T
  }

  // Otherwise return raw response (chat controllers return unwrapped data)
  return json as T
}

export const api = {
  // 由服务端可信租户开关决定；前端不保存或设置 tenantId。
  remoteWriteEnabled() {
    return request<{ enabled: boolean }>('/chat/remote-write-enabled')
  },
  // Auth
  login(usernameOrEmail: string, password: string, persist: boolean) {
    return request<{ token: string; userId: string }>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username: usernameOrEmail, password }),
    }).then((data) => {
      setToken(data.token, persist)
      return data
    })
  },

  register(username: string, password: string, email: string) {
    return request<{ userId: string }>('/auth/register', {
      method: 'POST',
      body: JSON.stringify({ username, password, email }),
    })
  },

  changePassword(oldPassword: string, newPassword: string) {
    return request<{ message: string }>('/auth/password', {
      method: 'PUT',
      body: JSON.stringify({ oldPassword, newPassword }),
    })
  },

  logout() {
    return request<{ message: string }>('/auth/logout', { method: 'DELETE' }).finally(() => {
      clearToken()
    })
  },

  // Chat
  chatSync(message: string, conversationId?: string) {
    const params = new URLSearchParams({ message })
    if (conversationId) params.set('conversationId', conversationId)
    return request<Message>(`/chat?${params}`)
  },

  async chatStream(
    message: string,
    onChunk: (text: string) => void,
    onDone: () => void,
    conversationId?: string,
  ) {
    const params = new URLSearchParams({ message })
    if (conversationId) params.set('conversationId', conversationId)

    const res = await fetch(`${BASE}/chat/stream?${params}`, {
      headers: { ...authHeaders(), Accept: 'text/event-stream' },
    })

    if (res.status === 401) {
      clearToken()
      throw new Error('UNAUTHORIZED')
    }

    const reader = res.body!.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''
      for (const line of lines) {
        const trimmed = line.trim()
        if (!trimmed || !trimmed.startsWith('data:')) continue
        const data = trimmed.slice(5).trim()
        if (data === '[DONE]') {
          onDone()
          return
        }
        onChunk(data)
      }
    }
    onDone()
  },

  async remoteChatStream(
    message: string,
    onChunk: (text: string) => void,
    onDone: (conversationId: string) => void,
    conversationId?: string,
  ) {
    const activeId = conversationId || (await request<RemoteConversation>('/v1/conversations', {
      method: 'POST',
      body: JSON.stringify({ title: message.slice(0, 64) }),
    })).id
    const requestId = crypto.randomUUID()
    const accepted = await request<{ message: RemoteMessage; aiMessage: RemoteMessage }>(
      `/v1/conversations/${activeId}/messages`,
      {
        method: 'POST',
        headers: { 'Idempotency-Key': requestId },
        body: JSON.stringify({ content: message, requestId }),
      },
    )
    const res = await fetch(`${BASE}/v1/conversations/${activeId}/stream?afterSequence=${accepted.message.sequence}`, {
      headers: { ...authHeaders(), Accept: 'text/event-stream' },
    })
    if (!res.ok || !res.body) throw new Error(`HTTP ${res.status}`)
    const reader = res.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const blocks = buffer.split('\n\n')
      buffer = blocks.pop() || ''
      for (const block of blocks) {
        const dataLine = block.split('\n').find((line) => line.startsWith('data:'))
        if (!dataLine) continue
        const event = JSON.parse(dataLine.slice(5).trim())
        if (event.eventType === 'message.delta') onChunk(event.delta || '')
        if (event.eventType === 'message.completed' || event.eventType === 'message.interrupted') {
          onDone(activeId)
          return
        }
      }
    }
    onDone(activeId)
  },

  getRemoteConversations() {
    return request<{ items: RemoteConversation[] }>('/v1/conversations').then(({ items }) => items.map((value) => ({
      id: value.id,
      title: value.title,
      createdAt: value.createdAt || new Date().toISOString(),
      updatedAt: value.updatedAt || value.createdAt || new Date().toISOString(),
      deleted: 0,
      pinned: value.pinned ? 1 : 0,
    })))
  },

  getRemoteMessages(conversationId: string) {
    return request<{ items: RemoteMessage[] }>(`/v1/conversations/${conversationId}/messages`).then(({ items }) => items.map((value) => ({
      id: value.id,
      conversationId,
      role: value.senderType === 'USER' || value.senderType === 'STAFF' ? 'user' as const : 'assistant' as const,
      content: value.content,
      createdAt: value.createdAt || new Date().toISOString(),
    })))
  },

  deleteRemoteConversation(id: string) {
    return request<void>(`/v1/conversations/${id}`, { method: 'DELETE' })
  },

  renameRemoteConversation(id: string, title: string) {
    return request<RemoteConversation>(`/v1/conversations/${id}`, {
      method: 'PATCH',
      body: JSON.stringify({ title }),
    }).then((): { success: boolean; error?: string } => ({ success: true }))
  },

  pinRemoteConversation(id: string, pinned: boolean) {
    return request<RemoteConversation>(`/v1/conversations/${id}`, {
      method: 'PATCH',
      body: JSON.stringify({ pinned }),
    }).then((value) => ({ pinned: value.pinned }))
  },

  // Conversations
  getConversations() {
    return request<Conversation[]>('/chat/conversations')
  },

  deleteConversation(id: string) {
    return request<void>(`/chat/conversations/${id}`, { method: 'DELETE' })
  },

  renameConversation(id: string, title: string) {
    return request<{ success: boolean; error?: string }>(`/chat/conversations/${id}/title`, {
      method: 'PUT',
      body: JSON.stringify({ title }),
    })
  },

  pinConversation(id: string) {
    return request<{ pinned: boolean }>(`/chat/conversations/${id}/pin`, { method: 'PUT' })
  },

  getMessages(conversationId: string) {
    return request<Message[]>(`/chat/conversations/${conversationId}/messages`)
  },

  // Health
  healthCheck() {
    return fetch(`${BASE}/health`).then((r) => r.text())
  },

  // ── RAG: Knowledge Base ──
  async uploadDocument(file: File, onProgress?: (pct: number) => void) {
    const formData = new FormData()
    formData.append('file', file)

    return new Promise<{ id: string; name: string; type: string; size: number; chunkCount: number }>((resolve, reject) => {
      const xhr = new XMLHttpRequest()
      xhr.open('POST', `${BASE}/rag/documents/upload`)

      const token = getToken()
      if (token) xhr.setRequestHeader('Authorization', `Bearer ${token}`)

      xhr.upload.onprogress = (e) => {
        if (e.lengthComputable && onProgress) {
          onProgress(Math.round((e.loaded / e.total) * 100))
        }
      }

      xhr.onload = () => {
        if (xhr.status === 401) {
          clearToken()
          return reject(new Error('UNAUTHORIZED'))
        }
        if (xhr.status >= 200 && xhr.status < 300) {
          const json = JSON.parse(xhr.responseText)
          resolve(json.data || json)
        } else {
          reject(new Error(xhr.responseText || '上传失败'))
        }
      }

      xhr.onerror = () => reject(new Error('网络错误'))
      xhr.send(formData)
    })
  },

  getDocuments() {
    return request<RAGDocument[]>('/rag/documents')
  },

  getDocument(id: string) {
    return request<RAGDocument>(`/rag/documents/${id}`)
  },

  getDocumentChunks(id: string, page = 1, pageSize = 20, keyword = '', activeChunkId?: string | null) {
    const params = new URLSearchParams({
      page: String(page),
      pageSize: String(pageSize),
    })
    if (keyword.trim()) params.set('keyword', keyword.trim())
    if (activeChunkId) params.set('activeChunkId', activeChunkId)
    return request<PageResult<RAGDocumentChunk>>(`/rag/documents/${id}/chunks?${params}`)
  },

  deleteDocument(id: string) {
    return request<{ success: boolean }>(`/rag/documents/${id}`, { method: 'DELETE' })
  },

  searchDocuments(query: string, topK = 5) {
    const params = new URLSearchParams({ q: query, topK: String(topK) })
    return request<{ query: string; results: any[] }>(`/rag/search?${params}`)
  },
}

export { getToken, clearToken, setToken }
