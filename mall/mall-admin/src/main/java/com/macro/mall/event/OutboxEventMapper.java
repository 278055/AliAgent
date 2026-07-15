package com.macro.mall.event;

import org.apache.ibatis.annotations.Mapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Mapper
public interface OutboxEventMapper {
    int insert(OutboxEvent event);
    List<OutboxEvent> findDue(Instant now, int limit);
    int markPublished(UUID eventId, Instant publishedAt);
    int markFailed(UUID eventId, int attempts, Instant nextAttemptAt, String error, String status);
}
