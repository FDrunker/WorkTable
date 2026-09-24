package com.ruoyi.knowledge.domain;

/**
 * 公开 AI 对话提问。
 */
public record AiChatSendRequest(
        String visitorId,
        String question)
{
}
