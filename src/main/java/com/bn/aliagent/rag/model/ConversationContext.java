package com.bn.aliagent.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 对话上下文 —— 供 RetrievalStrategy 判断是否需要触发检索时参考
 *
 * <p>封装当前对话的元信息，策略实现可据此做更智能的判断
 * （如：同一对话中已触发过检索则后续问题也触发）。</p>
 *
 * @author AliAgent
 * @since 2026-06-18
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationContext {

    /** 会话 ID */
    private String conversationId;

    /** 会话标题 */
    private String title;

    /**
     * 创建仅包含会话 ID 的轻量上下文
     */
    public static ConversationContext of(String conversationId) {
        return ConversationContext.builder().conversationId(conversationId).build();
    }
}
