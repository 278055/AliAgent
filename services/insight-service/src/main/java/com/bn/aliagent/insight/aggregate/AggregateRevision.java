package com.bn.aliagent.insight.aggregate; import java.time.Instant;
public record AggregateRevision(AggregateKey key,int revision,long numerator,long denominator,Instant createdAt){public static AggregateRevision next(AggregateKey key,int revision,long numerator,long denominator){return new AggregateRevision(key,revision,numerator,denominator,Instant.now());}}
