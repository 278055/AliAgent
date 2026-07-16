package com.bn.aliagent.knowledge.ingestion;

import java.util.List;

@FunctionalInterface
public interface TextChunker {
    List<String> split(String text);
}
