package com.bn.aliagent.orchestration.tool;

import com.bn.aliagent.orchestration.adapter.AdapterException;
import com.bn.aliagent.orchestration.contract.OrchestrationContract;
import com.bn.aliagent.orchestration.contract.OrchestrationPorts.MallReadToolPort;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MallReadToolAdapter implements MallReadToolPort {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final String baseUrl;
    private final TrustedToolHttpClient client;
    public MallReadToolAdapter(String baseUrl, String jwt, int timeoutMs, int attempts) { this.baseUrl = baseUrl; this.client = new TrustedToolHttpClient(jwt, timeoutMs, attempts); }
    @Override public OrchestrationContract.ToolResult readOrder(OrchestrationContract.ExecutionContext context, long orderId) { return read(context, orderId, "order", ""); }
    @Override public OrchestrationContract.ToolResult readLogistics(OrchestrationContract.ExecutionContext context, long orderId) { return read(context, orderId, "logistics", "/logistics"); }
    private OrchestrationContract.ToolResult read(OrchestrationContract.ExecutionContext context, long orderId, String name, String suffix) {
        if (orderId <= 0) throw new AdapterException(AdapterException.Category.VALIDATION, "orderId must be positive");
        try {
            Map<String, Object> data = JSON.convertValue(JSON.readTree(client.get(baseUrl + "/api/v1/internal/mall/orders/" + orderId + suffix, context)).path("data"), new TypeReference<LinkedHashMap<String, Object>>() { });
            return new OrchestrationContract.ToolResult("mall." + name + ".read", redact(data), List.of());
        } catch (AdapterException exception) { throw exception;
        } catch (Exception exception) { throw new AdapterException(AdapterException.Category.REMOTE, "invalid mall response", exception); }
    }
    private Map<String, Object> redact(Map<String, Object> values) { values.replaceAll((key, value) -> key.toLowerCase().matches(".*(phone|address|password|token).*" ) ? "***" : value); return values; }
}
