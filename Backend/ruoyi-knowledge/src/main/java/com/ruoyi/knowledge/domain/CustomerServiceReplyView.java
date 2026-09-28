package com.ruoyi.knowledge.domain;

/**
 * 智能客服回复、分类和工单结果。
 */
public record CustomerServiceReplyView(
        String conversationId,
        String title,
        AiChatMessageView userMessage,
        AiChatMessageView assistantMessage,
        String category,
        String priority,
        String ticketNo,
        String ticketStatus)
{
}
