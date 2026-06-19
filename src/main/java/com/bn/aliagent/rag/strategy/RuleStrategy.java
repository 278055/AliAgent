package com.bn.aliagent.rag.strategy;

import com.bn.aliagent.rag.model.ConversationContext;

import java.util.Set;

/**
 * 规则策略 —— 基于关键词匹配判断是否需要触发 RAG 检索
 *
 * <p>一期最简实现：</p>
 * <ul>
 *   <li>白名单命中 → 触发检索</li>
 *   <li>黑名单命中（寒暄）→ 跳过检索</li>
 *   <li>其他 → 默认跳过</li>
 * </ul>
 *
 * <p>二期升级：LLM 语义级别分类判断</p>
 *
 * @author AliAgent
 * @since 2026-06-18
 */
public class RuleStrategy implements RetrievalStrategy {

    /** 知识类关键词 —— 命中则触发 RAG 检索 */
    private static final Set<String> KNOWLEDGE_KEYWORDS = Set.of(
            "?", "？",
            "什么", "如何", "怎么", "怎样", "为什么",
            "政策", "规定", "规则", "条款", "协议",
            "退款", "退货", "换货", "售后",
            "价格", "费用", "收费",
            "流程", "步骤", "方法", "操作",
            "限制", "条件", "要求", "资格",
            "时间", "期限", "截止",
            "地址", "联系方式", "电话",
            "介绍", "说明", "解释", "定义",
            "区别", "不同", "对比"
    );

    /** 寒暄类关键词 —— 命中则跳过检索 */
    private static final Set<String> GREETING_KEYWORDS = Set.of(
            "你好", "您好", "hi", "hello", "嗨",
            "谢谢", "感谢", "多谢",
            "再见", "拜拜", "bye",
            "好的", "ok", "嗯", "哦", "行"
    );

    @Override
    public boolean shouldRetrieve(String userMessage, ConversationContext ctx) {
        if (userMessage == null || userMessage.isBlank()) {
            return false;
        }

        String msg = userMessage.trim();

        // 优先检查寒暄类关键词，命中则直接跳过
        for (String keyword : GREETING_KEYWORDS) {
            if (msg.contains(keyword)) {
                return false;
            }
        }

        // 检查知识类关键词，命中则触发检索
        for (String keyword : KNOWLEDGE_KEYWORDS) {
            if (msg.contains(keyword)) {
                return true;
            }
        }

        // 默认不触发（保守策略：宁可漏检，不浪费检索资源）
        return false;
    }
}
