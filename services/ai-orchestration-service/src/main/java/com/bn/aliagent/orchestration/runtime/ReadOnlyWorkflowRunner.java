package com.bn.aliagent.orchestration.runtime;

import com.bn.aliagent.orchestration.contract.OrchestrationContract;
import com.bn.aliagent.orchestration.contract.OrchestrationPorts.ChatModelPort;
import com.bn.aliagent.orchestration.contract.OrchestrationPorts.ConversationStreamPort;
import com.bn.aliagent.orchestration.contract.OrchestrationPorts.KnowledgeRetrievalPort;
import com.bn.aliagent.orchestration.contract.OrchestrationPorts.MallReadToolPort;
import com.bn.aliagent.orchestration.core.ExecutionRecord;
import com.bn.aliagent.orchestration.core.WorkflowRunner;
import com.bn.aliagent.orchestration.routing.Intent;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ReadOnlyWorkflowRunner implements WorkflowRunner {
    private static final Pattern ORDER_ID = Pattern.compile("\\b(\\d+)\\b");
    private static final String HANDOFF_MESSAGE = "当前无法安全完成该请求，已为您转人工处理。";

    private final ChatModelPort model;
    private final KnowledgeRetrievalPort knowledge;
    private final MallReadToolPort mall;
    private final ConversationStreamPort stream;

    public ReadOnlyWorkflowRunner(ChatModelPort model, KnowledgeRetrievalPort knowledge, MallReadToolPort mall,
                                  ConversationStreamPort stream) {
        this.model = model;
        this.knowledge = knowledge;
        this.mall = mall;
        this.stream = stream;
    }

    @Override
    public void run(ExecutionRecord record, String input) {
        OrchestrationContract.ExecutionContext context = context(record);
        try {
            switch (record.intent()) {
                case GENERAL -> complete(context, model.generate(context, nonBlank(input)), List.of());
                case RAG -> runRag(context, input);
                case ORDER_QUERY -> runMall(context, input, true);
                case LOGISTICS_QUERY -> runMall(context, input, false);
                case HUMAN_HANDOFF -> handoff(context);
            }
        } catch (RuntimeException exception) {
            handoff(context);
        }
    }

    private void runRag(OrchestrationContract.ExecutionContext context, String input) {
        List<OrchestrationContract.Citation> citations = knowledge.retrieve(context, nonBlank(input), 5);
        if (citations.isEmpty()) {
            handoff(context);
            return;
        }
        complete(context, citations.get(0).content(), citations);
    }

    private void runMall(OrchestrationContract.ExecutionContext context, String input, boolean order) {
        long orderId = orderId(input);
        OrchestrationContract.ToolResult result = order ? mall.readOrder(context, orderId) : mall.readLogistics(context, orderId);
        complete(context, result.data().toString(), result.citations());
    }

    private void complete(OrchestrationContract.ExecutionContext context, String content,
                          List<OrchestrationContract.Citation> citations) {
        stream.append(context, new OrchestrationContract.StreamChunk(context.replyMessageId(), context.generationId(),
                0, content, true, "STOP", citations));
    }

    private void handoff(OrchestrationContract.ExecutionContext context) {
        complete(context, HANDOFF_MESSAGE, List.of());
    }

    private static long orderId(String input) {
        Matcher matcher = ORDER_ID.matcher(nonBlank(input));
        if (!matcher.find()) throw new IllegalArgumentException("订单号不能为空");
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("订单号无效", exception);
        }
    }

    private static String nonBlank(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("请求内容不能为空");
        return value;
    }

    // v2 事件尚未携带可信身份快照；占位值会使真实下游安全拒绝，避免伪造身份。
    private static OrchestrationContract.ExecutionContext context(ExecutionRecord record) {
        var request = record.request();
        return new OrchestrationContract.ExecutionContext(request.tenantId(), "unavailable", "UNAVAILABLE", List.of(),
                List.of(), request.traceId(), request.requestId(), request.conversationId(), request.messageId(),
                request.replyMessageId(), request.generationId(), request.eventId());
    }
}
