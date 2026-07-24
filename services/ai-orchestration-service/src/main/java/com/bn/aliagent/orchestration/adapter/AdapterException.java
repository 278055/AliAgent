package com.bn.aliagent.orchestration.adapter;

public final class AdapterException extends RuntimeException {
    public enum Category { VALIDATION, UNAVAILABLE, REMOTE, CONFIGURATION }
    private final Category category;

    public AdapterException(Category category, String message) { super(message); this.category = category; }
    public AdapterException(Category category, String message, Throwable cause) { super(message, cause); this.category = category; }
    public Category category() { return category; }
}
