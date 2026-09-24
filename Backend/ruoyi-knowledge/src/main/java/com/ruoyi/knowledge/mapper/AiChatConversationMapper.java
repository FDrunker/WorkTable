package com.ruoyi.knowledge.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.ruoyi.knowledge.domain.AiChatConversation;
import com.ruoyi.knowledge.domain.AiChatMessage;

public interface AiChatConversationMapper
{
    List<AiChatConversation> selectConversationList(@Param("visitorId") String visitorId,
            @Param("scene") String scene);

    AiChatConversation selectConversationById(@Param("conversationId") String conversationId);

    int insertConversation(AiChatConversation conversation);

    int updateConversationTitle(@Param("conversationId") String conversationId,
            @Param("title") String title);

    int touchConversation(@Param("conversationId") String conversationId);

    int deleteConversation(@Param("conversationId") String conversationId);

    int deleteMessages(@Param("conversationId") String conversationId);

    List<AiChatMessage> selectMessages(@Param("conversationId") String conversationId);

    List<AiChatMessage> selectRecentMessages(@Param("conversationId") String conversationId,
            @Param("limit") int limit);

    int countMessages(@Param("conversationId") String conversationId);

    int insertMessage(AiChatMessage message);
}
