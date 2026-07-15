package com.macro.mall.portal.internal.read;

public interface ServiceIdentityVerifier {
    void verify(String authorization, String audience, String requiredScope);
}
