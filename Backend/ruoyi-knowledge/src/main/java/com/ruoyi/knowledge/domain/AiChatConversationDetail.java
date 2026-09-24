package com.ruoyi.knowledge.domain;

import java.util.Date;
import java.util.List;

/**
 * 公开对话详情。
 */
public record AiChatConversationDetail(
        String conversationId,
        String scene,
        String title,
        Date createTime,
        Date updateTime,
        List<AiChatMessageView> messages)
{
}
