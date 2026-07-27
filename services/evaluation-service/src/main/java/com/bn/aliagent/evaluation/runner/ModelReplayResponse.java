package com.bn.aliagent.evaluation.runner;

import java.math.BigDecimal;
import java.util.List;

public record ModelReplayResponse(String intent, String answer, List<String> tools, List<String> citations, boolean humanHandoff,
                                  boolean toolFailure, long firstTokenMillis, long totalLatencyMillis, long inputTokens,
                                  long outputTokens, BigDecimal cost) { }
