package com.bn.aliagent.knowledge.ingestion;

@FunctionalInterface
public interface SourceTextExtractor {
    String extract(IngestionSource source) throws Exception;
}
