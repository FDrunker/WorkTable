package com.ruoyi.knowledge.domain;

import java.util.Date;
import java.util.List;

/**
 * 公开对话消息视图。
 */
public record AiChatMessageView(
        Long messageId,
        String role,
        String content,
        String intent,
        Double intentConfidence,
        boolean needsClarification,
        List<CustomerServiceSource> sources,
        String category,
        String priority,
        String ticketNo,
        Date createTime)
{
}
