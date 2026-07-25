package com.bn.aliagent.conversation.core;

@FunctionalInterface
public interface OrderOwnershipVerifier {
    void verifyMemberOwnsOrder(TrustedConversationRequestContext context, long orderId);
}
