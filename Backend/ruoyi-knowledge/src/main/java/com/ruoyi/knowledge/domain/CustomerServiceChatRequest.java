package com.ruoyi.knowledge.domain;

import java.util.List;

/**
 * AI对话提问。
 */
public record CustomerServiceChatRequest(
        String question,
        List<CustomerServiceChatTurn> history)
{
}
