package com.ruoyi.knowledge.domain;

import java.util.List;

/**
 * 智能客服提问。
 */
public record CustomerServiceChatRequest(
        String question,
        List<CustomerServiceChatTurn> history)
{
}
