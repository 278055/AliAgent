package com.bn.aliagent.knowledge.storage;

import java.io.InputStream;

public interface KnowledgeObjectStorage {
    void put(String objectKey, InputStream input, long contentLength, String mediaType) throws Exception;
    InputStream get(String objectKey) throws Exception;
}
