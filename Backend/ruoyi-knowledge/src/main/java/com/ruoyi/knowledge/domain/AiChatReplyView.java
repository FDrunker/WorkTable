package com.ruoyi.knowledge.domain;

/**
 * 公开 AI 对话回复及持久化结果。
 */
public record AiChatReplyView(
        String conversationId,
        String title,
        AiChatMessageView userMessage,
        AiChatMessageView assistantMessage)
{
}
