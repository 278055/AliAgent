import type { EvaluationCandidateSummary, EvaluationDatasetSummary, EvaluationTaskSummary, FailureSampleSummary, GateDecisionSummary, VersionComparisonSummary } from '@aliagent/api-client'

export interface EvaluationConsoleState {
  candidates: EvaluationCandidateSummary[]
  datasets: EvaluationDatasetSummary[]
  tasks: EvaluationTaskSummary[]
  comparisons: VersionComparisonSummary[]
  failures: FailureSampleSummary[]
  decisions: GateDecisionSummary[]
  error: string
  pending: string
}
