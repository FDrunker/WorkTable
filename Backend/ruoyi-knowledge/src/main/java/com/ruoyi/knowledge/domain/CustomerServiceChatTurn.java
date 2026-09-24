package com.ruoyi.knowledge.domain;

/**
 * AI对话历史项。
 */
public record CustomerServiceChatTurn(
        String role,
        String content)
{
}
