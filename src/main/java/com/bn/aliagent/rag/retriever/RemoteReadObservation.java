package com.bn.aliagent.rag.retriever;

import com.bn.aliagent.rag.model.RagChunk;
import java.util.List;

/** 双跑及回退的观测边界，严禁传递查询词和切片正文。 */
public interface RemoteReadObservation {
    void compared(List<RagChunk> local, List<RagChunk> remote, long latencyMillis);

    void fallback(RemoteFailure reason, long latencyMillis);
}
