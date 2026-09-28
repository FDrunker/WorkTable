package com.ruoyi.knowledge.domain;

/**
 * 客服工单人工处理请求。
 */
public record CustomerServiceTicketHandleRequest(
        String action,
        String priority,
        String replyContent,
        String note)
{
}
