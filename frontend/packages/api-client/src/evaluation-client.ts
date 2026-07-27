import { AgentClient } from './agent-client'

export interface EvaluationCandidateSummary { candidateId: string; status: 'PENDING_REVIEW' | 'QUARANTINED' | 'ACCEPTED' | 'REJECTED' | 'EXPIRED'; labels: string[]; riskLevel: 'LOW' | 'MEDIUM' | 'HIGH'; createdAt: string }
export interface EvaluationDatasetSummary { datasetId: string; name: string; version: string; state: 'DRAFT' | 'PUBLISHED'; sampleCount: number; digest: string }
export interface EvaluationTaskSummary { taskId: string; state: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED'; datasetVersion: string; targetVersion: string; completedAt?: string }
export interface VersionComparisonSummary { comparisonId: string; baselineVersion: string; targetVersion: string; delta: number; status: 'IMPROVED' | 'REGRESSED' | 'UNCHANGED' }
export interface FailureSampleSummary { sampleId: string; metric: string; riskLevel: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'; evidenceDigest: string; disposition: 'OPEN' | 'ACKNOWLEDGED' }
export interface GateDecisionSummary { decisionId: string; status: 'PASS' | 'FAIL'; policyVersion: string; targetVersion: string; expiresAt: string; reasons: string[] }

export class EvaluationClient extends AgentClient {
  candidates() { return this.request<EvaluationCandidateSummary[]>('/evaluation/candidates') }
  reviewCandidate(candidateId: string, action: 'ACCEPT' | 'REJECT', requestId?: string) { return this.request(`/evaluation/candidates/${candidateId}/review`, { method: 'POST', body: JSON.stringify({ action }) }, requestId) }
  datasets() { return this.request<EvaluationDatasetSummary[]>('/evaluation/datasets') }
  startTask(datasetVersion: string, targetVersion: string, requestId?: string) { return this.request<EvaluationTaskSummary>('/evaluation/tasks', { method: 'POST', body: JSON.stringify({ datasetVersion, targetVersion }) }, requestId) }
  tasks() { return this.request<EvaluationTaskSummary[]>('/evaluation/tasks') }
  comparisons() { return this.request<VersionComparisonSummary[]>('/evaluation/comparisons') }
  failureSamples() { return this.request<FailureSampleSummary[]>('/evaluation/failure-samples') }
  gateDecisions() { return this.request<GateDecisionSummary[]>('/evaluation/gate-decisions') }
}
