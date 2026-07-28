package com.bn.aliagent.evaluation.intake;

public record AnonymizedPayload(IntakeAnonymizationStatus status, String canonicalJson, String ruleVersion, String digest) { }
