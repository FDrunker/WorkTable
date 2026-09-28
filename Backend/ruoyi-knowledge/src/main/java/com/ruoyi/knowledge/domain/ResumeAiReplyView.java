package com.ruoyi.knowledge.domain;

/**
 * 个人简历 AI 回复及持久化结果。
 */
public record ResumeAiReplyView(
        String conversationId,
        String title,
        AiChatMessageView userMessage,
        AiChatMessageView assistantMessage)
{
}
