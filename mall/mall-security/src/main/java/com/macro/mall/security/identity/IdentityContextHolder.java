package com.macro.mall.security.identity;

public final class IdentityContextHolder {
    private static final ThreadLocal<IdentityContext> CONTEXT = new ThreadLocal<>();

    private IdentityContextHolder() {
    }

    public static IdentityContext getContext() {
        return CONTEXT.get();
    }

    public static void setContext(IdentityContext context) {
        CONTEXT.set(context);
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
