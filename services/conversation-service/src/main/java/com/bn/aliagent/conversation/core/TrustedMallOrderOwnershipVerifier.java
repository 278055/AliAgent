package com.bn.aliagent.conversation.core;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class TrustedMallOrderOwnershipVerifier implements OrderOwnershipVerifier {
    private final String baseUrl;
    private final String serviceJwt;
    private final HttpClient client;
    private final int timeoutMs;

    public TrustedMallOrderOwnershipVerifier(String baseUrl, String serviceJwt, int timeoutMs) {
        this.baseUrl = baseUrl;
        this.serviceJwt = serviceJwt;
        this.timeoutMs = timeoutMs;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build();
    }

    @Override
    public void verifyMemberOwnsOrder(TrustedConversationRequestContext context, long orderId) {
        if (serviceJwt == null || serviceJwt.isBlank()) {
            throw new ConversationException("CONV-503-001", "Mall service JWT is not configured");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/internal/mall/orders/" + orderId))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("X-Service-Authorization", "Bearer " + serviceJwt)
                    .header("X-Tenant-Id", context.tenantId())
                    .header("X-Subject-Id", context.subjectId())
                    .header("X-Subject-Type", context.subjectType())
                    .header("X-Trace-Id", context.traceId())
                    .header("X-Request-Id", context.requestId().toString())
                    .GET().build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() / 100 != 2) {
                throw new ConversationException("CONV-403-002", "Order is not accessible to the caller");
            }
        } catch (ConversationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ConversationException("CONV-503-001", "Mall order verification is unavailable");
        }
    }
}
