package com.bn.aliagent.orchestration.runtime;

import com.bn.aliagent.orchestration.contract.OrchestrationContract;
import com.bn.aliagent.orchestration.contract.OrchestrationPorts;
import com.bn.aliagent.orchestration.core.ExecutionRecord;
import com.bn.aliagent.orchestration.core.ReplyRequestedV2;
import com.bn.aliagent.orchestration.routing.Intent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReadOnlyWorkflowRunnerTest {
    @Test
    void 普通问答只调用模型并使用回复流标识() {
        Fixtures fixtures = new Fixtures();
        fixtures.runner().run(record(Intent.GENERAL), "你好");

        assertEquals(1, fixtures.modelCalls);
        assertEquals(0, fixtures.knowledgeCalls);
        assertEquals(0, fixtures.orderCalls);
        assertEquals(0, fixtures.logisticsCalls);
        assertEquals("模型回复", fixtures.chunks.get(0).content());
        assertIdentifiers(fixtures.chunks.get(0));
    }

    @Test
    void 知识问答只检索并携带引用() {
        Fixtures fixtures = new Fixtures();
        fixtures.runner().run(record(Intent.RAG), "会员政策");

        assertEquals(0, fixtures.modelCalls);
        assertEquals(1, fixtures.knowledgeCalls);
        assertEquals(1, fixtures.chunks.get(0).citations().size());
        assertIdentifiers(fixtures.chunks.get(0));
    }

    @Test
    void 订单与物流分别只调用对应mall只读工具() {
        Fixtures fixtures = new Fixtures();
        fixtures.runner().run(record(Intent.ORDER_QUERY), "查询订单 123");
        fixtures.runner().run(record(Intent.LOGISTICS_QUERY), "查询物流 456");

        assertEquals(0, fixtures.modelCalls);
        assertEquals(1, fixtures.orderCalls);
        assertEquals(1, fixtures.logisticsCalls);
    }

    @Test
    void 转人工不调用模型或工具() {
        Fixtures fixtures = new Fixtures();
        fixtures.runner().run(record(Intent.HUMAN_HANDOFF), "申请退款");

        assertEquals(0, fixtures.modelCalls);
        assertEquals(0, fixtures.knowledgeCalls + fixtures.orderCalls + fixtures.logisticsCalls);
        assertTrue(fixtures.chunks.get(0).content().contains("人工"));
    }

    @Test
    void 依赖故障不编造事实并转人工() {
        Fixtures fixtures = new Fixtures();
        fixtures.failKnowledge = true;
        fixtures.runner().run(record(Intent.RAG), "会员政策");

        assertTrue(fixtures.chunks.get(0).content().contains("人工"));
        assertTrue(fixtures.chunks.get(0).citations().isEmpty());
    }

    private static void assertIdentifiers(OrchestrationContract.StreamChunk chunk) {
        assertEquals(Fixtures.REPLY_MESSAGE_ID, chunk.messageId());
        assertEquals(Fixtures.GENERATION_ID, chunk.generationId());
        assertEquals(0, chunk.chunkIndex());
        assertTrue(chunk.completed());
    }

    private static ExecutionRecord record(Intent intent) {
        return ExecutionRecord.created(new ReplyRequestedV2(UUID.randomUUID(), "test-p5-runtime", UUID.randomUUID(),
                UUID.randomUUID(), Fixtures.REPLY_MESSAGE_ID, Fixtures.GENERATION_ID, UUID.randomUUID(), "trace", Instant.now()), intent);
    }

    private static final class Fixtures {
        private static final UUID REPLY_MESSAGE_ID = UUID.randomUUID();
        private static final UUID GENERATION_ID = UUID.randomUUID();
        private int modelCalls;
        private int knowledgeCalls;
        private int orderCalls;
        private int logisticsCalls;
        private boolean failKnowledge;
        private final List<OrchestrationContract.StreamChunk> chunks = new ArrayList<>();

        private ReadOnlyWorkflowRunner runner() {
            OrchestrationPorts.ChatModelPort model = (context, prompt) -> { modelCalls++; return "模型回复"; };
            OrchestrationPorts.KnowledgeRetrievalPort knowledge = (context, query, topK) -> {
                knowledgeCalls++;
                if (failKnowledge) throw new IllegalStateException("知识服务不可用");
                return List.of(new OrchestrationContract.Citation(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "知识内容", 0.9));
            };
            OrchestrationPorts.MallReadToolPort mall = new OrchestrationPorts.MallReadToolPort() {
                @Override public OrchestrationContract.ToolResult readOrder(OrchestrationContract.ExecutionContext context, long orderId) {
                    orderCalls++; return new OrchestrationContract.ToolResult("mall.order.read", Map.of("id", orderId), List.of());
                }
                @Override public OrchestrationContract.ToolResult readLogistics(OrchestrationContract.ExecutionContext context, long orderId) {
                    logisticsCalls++; return new OrchestrationContract.ToolResult("mall.logistics.read", Map.of("id", orderId), List.of());
                }
            };
            return new ReadOnlyWorkflowRunner(model, knowledge, mall, (context, chunk) -> chunks.add(chunk));
        }
    }
}
