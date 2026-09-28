package com.ruoyi.knowledge.domain;

/**
 * 会话中一次问答的持久化结果。
 */
public record AiChatReplyView(
        String conversationId,
        String title,
        AiChatMessageView userMessage,
        AiChatMessageView assistantMessage)
{
}
