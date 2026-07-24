package com.bn.aliagent.gateway;

import java.util.List;

record TrustedIdentity(String tenantId, String subjectId, String subjectType, List<String> roles,
        List<String> permissions) { }
