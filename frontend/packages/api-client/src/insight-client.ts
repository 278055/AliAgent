import { AgentClient } from './agent-client'

export interface InsightRadar { id: string; title: string; status: string; severity: string }
export interface InsightMetric { name: string; value: string; trend: string }

/** 网关负责注入可信身份；客户端绝不发送内部租户或服务认证头。 */
export class InsightClient extends AgentClient {
  metrics() { return this.request<InsightMetric[]>('/insights/metrics') }
  radars() { return this.request<InsightRadar[]>('/insights/radars') }
  disposeRadar(radarId: string, action: 'ACKNOWLEDGED' | 'RESOLVED' | 'IGNORED', reason: string) {
    return this.request(`/insights/radars/${radarId}/actions`, { method: 'POST', body: JSON.stringify({ action, reason }) })
  }
  verifyOrder(radarId: string, reason: string, evidenceRef: string) {
    return this.request('/insights/orders/verify', { method: 'POST', body: JSON.stringify({ radarId, reason, evidenceRef }) })
  }
}
