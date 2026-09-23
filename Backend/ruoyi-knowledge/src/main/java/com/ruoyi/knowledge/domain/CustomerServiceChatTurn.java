package com.ruoyi.knowledge.domain;

/**
 * 智能客服对话历史项。
 */
public record CustomerServiceChatTurn(
        String role,
        String content)
{
}
