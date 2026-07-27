package com.bn.aliagent.evaluation.comparison;

import java.util.Optional;

/** C 通过此 B 自有端口读取结果，避免反向依赖具体聚合实现。 */
public interface EvaluationResultReader { Optional<EvaluationRunSummary> find(String runId); }
