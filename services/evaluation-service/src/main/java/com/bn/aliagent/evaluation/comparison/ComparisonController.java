package com.bn.aliagent.evaluation.comparison;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/evaluations/comparisons")
@ConditionalOnBean(EvaluationResultReader.class)
public final class ComparisonController {
    private final EvaluationResultReader results;
    private final VersionComparisonService comparisons = new VersionComparisonService();
    public ComparisonController(EvaluationResultReader results) { this.results = results; }
    @GetMapping("/{baseline}/against/{candidate}")
    public VersionComparison compare(@PathVariable String baseline, @PathVariable String candidate) {
        return comparisons.compare(results.find(baseline).orElseThrow(() -> new ComparisonNotFound()), results.find(candidate).orElseThrow(() -> new ComparisonNotFound()));
    }
    static final class ComparisonNotFound extends RuntimeException { }
}
