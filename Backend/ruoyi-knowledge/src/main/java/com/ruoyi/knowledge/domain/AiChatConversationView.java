package com.ruoyi.knowledge.domain;

import java.util.Date;

/**
 * 公开对话历史列表项。
 */
public record AiChatConversationView(
        String conversationId,
        String title,
        Date createTime,
        Date updateTime,
        Integer messageCount,
        String lastMessage)
{
}
