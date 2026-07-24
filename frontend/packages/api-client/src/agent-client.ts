export type Presence = 'OFFLINE' | 'ONLINE' | 'BUSY'
export interface AssignmentOffer { offerId: string; conversationId: string; skillGroupName: string; expiresAt: string }
export interface QueueItem { queueItemId: string; conversationId: string; title: string; priority: number }
export interface ConversationSummary { conversationId: string; title: string; customerName: string; orderSummary: string; logisticsSummary: string; afterSaleSummary: string }
export interface CopilotSuggestion { suggestionId: string; content: string; status: 'GENERATED' | 'FAILED_RETRYABLE' | 'ACCEPTED' | 'MODIFIED' | 'IGNORED'; citations: Array<{ title: string; source: string }> }
export class ApiError extends Error { constructor(public status: number, message: string) { super(message) } }
export class AgentClient {
  constructor(private readonly baseUrl = '/api/v1') {}
  async request<T>(path: string, init: RequestInit = {}): Promise<T> {
    const write = !['GET', 'HEAD'].includes(init.method ?? 'GET')
    const headers = new Headers(init.headers)
    headers.set('Content-Type', 'application/json')
    if (write) { const requestId = crypto.randomUUID(); headers.set('X-Request-Id', requestId); headers.set('Idempotency-Key', requestId) }
    const response = await fetch(`${this.baseUrl}${path}`, { ...init, headers })
    if (!response.ok) throw new ApiError(response.status, (await response.text()) || response.statusText)
    return response.status === 204 ? undefined as T : response.json() as Promise<T>
  }
  offers() { return this.request<AssignmentOffer[]>('/agent/offers') }
  queue() { return this.request<QueueItem[]>('/agent/queue') }
  conversations() { return this.request<ConversationSummary[]>('/agent/conversations') }
  acceptOffer(id: string) { return this.request(`/agent/offers/${id}/accept`, { method: 'POST' }) }
  rejectOffer(id: string) { return this.request(`/agent/offers/${id}/reject`, { method: 'POST' }) }
  claim(id: string) { return this.request(`/agent/queue/${id}/claim`, { method: 'POST' }) }
  suggestion(id: string) { return this.request<CopilotSuggestion>(`/copilot/conversations/${id}/suggestions`) }
  refresh(id: string) { return this.request(`/copilot/conversations/${id}/suggestions/refresh`, { method: 'POST' }) }
  action(id: string, action: 'accept' | 'ignore' | 'modify-and-send', content?: string) { return this.request(`/copilot/suggestions/${id}/${action}`, { method: 'POST', body: content ? JSON.stringify({ content }) : undefined }) }
}
