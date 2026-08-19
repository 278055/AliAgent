package com.bn.aliagent.insight.topic;

import java.util.List;

public interface TopicNamingPort {
    TopicNaming name(String tenantId, List<String> anonymizedSamples, String modelVersion, String promptVersion);
}
