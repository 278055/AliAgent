package com.macro.mall.security.identity;

public class IdentityTokenException extends RuntimeException {
    public static final String ERROR_CODE = IdentityErrorCode.INVALID_TOKEN;

    public IdentityTokenException(String message, Throwable cause) {
        super(message, cause);
    }

    public IdentityTokenException(String message) {
        super(message);
    }
}
