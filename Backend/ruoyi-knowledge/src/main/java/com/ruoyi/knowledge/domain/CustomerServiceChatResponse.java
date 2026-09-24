package com.ruoyi.knowledge.domain;

import java.util.List;

/**
 * AI对话回答。
 */
public record CustomerServiceChatResponse(
        String answer,
        String intent,
        Double intentConfidence,
        boolean needsClarification,
        List<CustomerServiceSource> sources)
{
}
