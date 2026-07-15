package com.macro.mall.portal.internal.read;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class InternalAccessDeniedException extends RuntimeException {
    public InternalAccessDeniedException(String message) {
        super(message);
    }
}
