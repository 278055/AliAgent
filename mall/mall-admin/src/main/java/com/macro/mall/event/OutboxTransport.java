package com.macro.mall.event;

public interface OutboxTransport {
    void send(EventEnvelope event);
}
