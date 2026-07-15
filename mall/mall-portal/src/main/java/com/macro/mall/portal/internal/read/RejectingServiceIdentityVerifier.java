package com.macro.mall.portal.internal.read;

public class RejectingServiceIdentityVerifier implements ServiceIdentityVerifier {
    @Override
    public void verify(String authorization, String audience, String requiredScope) {
        throw new InternalAuthenticationException("service identity verification is not configured");
    }
}
