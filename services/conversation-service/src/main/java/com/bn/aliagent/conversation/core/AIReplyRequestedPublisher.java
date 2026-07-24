package com.bn.aliagent.conversation.core;

import com.bn.aliagent.conversation.core.ConversationModels.ReplyRequest;

public interface AIReplyRequestedPublisher {
    void publish(ReplyRequest request);
}
