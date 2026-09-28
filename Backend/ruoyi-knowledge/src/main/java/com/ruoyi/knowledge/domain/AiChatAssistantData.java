package com.ruoyi.knowledge.domain;

import java.util.List;

/**
 * 写入会话记录的一条助手回复。
 */
public record AiChatAssistantData(
        String content,
        String intent,
        Double intentConfidence,
        boolean needsClarification,
        List<CustomerServiceSource> sources,
        String category,
        String priority,
        String ticketNo)
{
    public static AiChatAssistantData standard(String content, boolean needsClarification,
            List<CustomerServiceSource> sources)
    {
        return new AiChatAssistantData(content, null, null, needsClarification, sources,
                null, null, null);
    }
}
