package com.bn.aliagent.conversation.realtime;

public interface RealtimePublisher {
    void publish(String channel, RealtimeEnvelope envelope);
}
