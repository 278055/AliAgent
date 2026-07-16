package com.bn.aliagent.conversation.core;

public final class ConversationException extends RuntimeException {
    private final String code;
    public ConversationException(String code, String message) { super(message); this.code = code; }
    public String code() { return code; }
}
