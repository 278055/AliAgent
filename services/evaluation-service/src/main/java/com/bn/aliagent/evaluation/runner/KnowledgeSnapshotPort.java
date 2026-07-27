package com.bn.aliagent.evaluation.runner;

import java.util.List;
import java.util.UUID;

/** 只读知识快照端口。 */
public interface KnowledgeSnapshotPort {
    List<String> retrieve(UUID knowledgeVersionId, String input);
}
