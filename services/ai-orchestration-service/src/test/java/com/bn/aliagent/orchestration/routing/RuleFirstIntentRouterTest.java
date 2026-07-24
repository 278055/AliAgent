package com.bn.aliagent.orchestration.routing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuleFirstIntentRouterTest {
    @Test
    void 售后退款取消订单应始终转人工() {
        RuleFirstIntentRouter router = new RuleFirstIntentRouter(input -> Intent.HUMAN_HANDOFF);

        assertEquals(Intent.HUMAN_HANDOFF, router.route("我要退款取消订单"));
    }

    @Test
    void 订单和物流规则应先于模型兜底() {
        RuleFirstIntentRouter router = new RuleFirstIntentRouter(input -> Intent.GENERAL);

        assertEquals(Intent.ORDER_QUERY, router.route("查询订单 12345"));
        assertEquals(Intent.LOGISTICS_QUERY, router.route("订单 12345 的物流到哪里了"));
    }

    @Test
    void 非规则输入应使用模型兜底() {
        RuleFirstIntentRouter router = new RuleFirstIntentRouter(input -> Intent.RAG);

        assertEquals(Intent.RAG, router.route("请解释企业知识库"));
    }
}
