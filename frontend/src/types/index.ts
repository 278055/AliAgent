export interface User {
  id: string
  username: string
  email: string
  createdAt: string
  updatedAt: string
  deleted: number
}

export interface Conversation {
  id: string
  title: string
  createdAt: string
  updatedAt: string
  deleted: number
  pinned: number
}

export interface Message {
  id: string
  conversationId: string
  role: 'user' | 'assistant'
  content: string
  createdAt: string
  metadata?: string
  sources?: RAGSource[]
}

export interface RAGDocument {
  id: string
  name: string
  type: string
  size: number
  chunkCount?: number
  createdAt: string
}

export interface RAGDocumentChunk {
  id: string
  documentId: string
  content: string
  sectionTitle?: string
  pageNumber?: number
  chunkIndex: number
  metadata?: string
  createdAt: string
}

export interface PageResult<T> {
  page: number
  pageSize: number
  total: number
  items: T[]
}

export interface RAGSource {
  chunkId: string
  documentId: string
  documentName: string
  content: string
  score: number
  sectionTitle?: string
  pageNumber?: number
}

export interface AuthPayload {
  username?: string
  email?: string
  password: string
}

export interface RegisterPayload {
  username: string
  password: string
  email: string
}

export interface ChatStreamParams {
  message: string
  conversationId?: string
}

export type Theme = 'dark' | 'light'

export interface ToastItem {
  id: number
  message: string
  type: 'success' | 'error' | 'info'
  duration?: number
}
