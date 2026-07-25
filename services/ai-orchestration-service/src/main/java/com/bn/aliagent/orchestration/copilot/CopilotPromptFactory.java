package com.bn.aliagent.orchestration.copilot;

public final class CopilotPromptFactory {
    public String create(CopilotModels.PromptContext context) {
        return "你是仅供客服查看的 AI 副驾。仅给出可发送建议；不得虚构订单、物流、退款或审批事实；不要输出思维过程。"
                + "\n会话：" + String.join("\n", context.conversation().messages())
                + "\n已验证订单/物流事实：" + String.join("；", context.commerceFacts())
                + "\n已验证售后事实：" + String.join("；", context.afterSaleFacts())
                + "\n知识引用：" + context.citations().stream().map(CopilotModels.Citation::title).reduce("", (a, b) -> a + "、" + b);
    }
}
