package com.bn.aliagent.evaluation.anonymization;

public record AnonymizationResult(AnonymizationStatus status, String canonicalJson, String ruleVersion, String digest) { }
