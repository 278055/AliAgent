package com.macro.mall.event;

public interface EventPublisher {
    void publish(EventEnvelope event);
}
