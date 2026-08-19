package com.bn.aliagent.insight.query;

public record QueryActor(String tenantId, QueryRole role) { }
enum QueryRole { OPERATOR, SUPERVISOR }
