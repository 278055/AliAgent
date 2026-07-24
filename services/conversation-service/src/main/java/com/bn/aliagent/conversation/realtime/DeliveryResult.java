package com.bn.aliagent.conversation.realtime;

public record DeliveryResult(boolean delivered, String code) {
    static DeliveryResult successful() { return new DeliveryResult(true, null); }
    static DeliveryResult rejected(String code) { return new DeliveryResult(false, code); }
}
