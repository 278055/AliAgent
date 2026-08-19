export type RadarStatus = 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED' | 'IGNORED'
export type InsightView = 'radar' | 'metrics' | 'topics' | 'query'

export interface QueryPlanView { metric: string; confirmed: boolean }
export interface ProblemRadarView { id: string; title: string; status: RadarStatus; severity: string; window: string }
export interface MetricCardView { name: string; value: string; trend: string; definition: string }
export interface TopicView { name: string; summary: string; risk: string; pendingReview: boolean; anonymized: boolean }
export interface InsightConsoleState {
  activeView: InsightView
  radars: ProblemRadarView[]
  metrics: MetricCardView[]
  topics: TopicView[]
  queryPlan?: QueryPlanView
  pending: boolean
  error: string
}

export function createInsightConsoleState(): InsightConsoleState {
  return { activeView: 'radar', radars: [], metrics: [], topics: [], pending: false, error: '' }
}

export function canExecuteQuery(state: InsightConsoleState): boolean {
  return !!state.queryPlan?.confirmed && !state.pending
}
