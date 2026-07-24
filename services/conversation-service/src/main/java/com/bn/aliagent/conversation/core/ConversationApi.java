package com.bn.aliagent.conversation.core;

import java.util.UUID;

public final class ConversationApi {
    private ConversationApi() { }
    public record ConversationView(UUID id, String title, String status, boolean pinned) { }
    public record MessageView(UUID id, long sequence, String content, String status, UUID requestId) { }
    public record CreateConversation(String title) { }
    public record PatchConversation(String title, Boolean pinned, Boolean closed) { }
    public record SubmitMessage(String content, UUID requestId) { }
    public record SubmitStaffMessage(String content, UUID clientMessageId) { }
}
