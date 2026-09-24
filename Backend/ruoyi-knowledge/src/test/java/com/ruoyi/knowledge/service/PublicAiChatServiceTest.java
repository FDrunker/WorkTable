package com.ruoyi.knowledge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.domain.AiChatConversation;
import com.ruoyi.knowledge.domain.AiChatMessage;
import com.ruoyi.knowledge.domain.AiChatSendRequest;
import com.ruoyi.knowledge.domain.CustomerServiceChatResponse;
import com.ruoyi.knowledge.domain.CustomerServiceConfigView;
import com.ruoyi.knowledge.mapper.AiChatConversationMapper;

import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class PublicAiChatServiceTest
{
    private static final String VISITOR_ID = "visitor_12345678";

    @Mock
    private CustomerServiceService customerService;

    @Mock
    private AiChatConversationMapper conversationMapper;

    private PublicAiChatService service;

    @BeforeEach
    void setUp()
    {
        service = new PublicAiChatService(customerService, conversationMapper, JsonMapper.builder().build());
    }

    @Test
    void bootstrapReturnsConfiguredSceneAndConversationHistory()
    {
        when(customerService.getConfig("personal-info")).thenReturn(
                new CustomerServiceConfigView(2L, "personal-info", "个人信息", "你好",
                        11L, 12L, List.of("col-1"), 5, "1", new Date()));
        AiChatConversation conversation = conversation();
        conversation.setMessageCount(2);
        conversation.setLastMessage("上一条消息");
        when(conversationMapper.selectConversationList(VISITOR_ID, "personal-info"))
                .thenReturn(List.of(conversation));

        var result = service.bootstrap("personal-info", VISITOR_ID);

        assertTrue(result.configured());
        assertTrue(result.enabled());
        assertEquals("个人信息", result.scene().title());
        assertEquals(1, result.conversations().size());
        assertEquals("上一条消息", result.conversations().get(0).lastMessage());
    }

    @Test
    void sendsMessageAndPersistsBothTurns()
    {
        AiChatConversation conversation = conversation();
        conversation.setScene("customer-service");
        when(conversationMapper.selectConversationById("conversation-1")).thenReturn(conversation);
        when(conversationMapper.countMessages("conversation-1")).thenReturn(0);
        when(conversationMapper.selectRecentMessages("conversation-1", 10)).thenReturn(List.of());
        when(customerService.chat(eq("customer-service"), any())).thenReturn(
                new CustomerServiceChatResponse("请先登录账号。", "账号问题", 0.96D, false, List.of()));

        var result = service.sendMessage("customer-service", "conversation-1",
                new AiChatSendRequest(VISITOR_ID, "我登录不上账号"));

        assertEquals("我登录不上账号", result.title());
        assertEquals("user", result.userMessage().role());
        assertEquals("assistant", result.assistantMessage().role());
        assertEquals("账号问题", result.assistantMessage().intent());
        verify(conversationMapper, times(2)).insertMessage(any(AiChatMessage.class));
        verify(conversationMapper).updateConversationTitle("conversation-1", "我登录不上账号");
    }

    @Test
    void rejectsConversationOwnedByAnotherVisitor()
    {
        AiChatConversation conversation = conversation();
        conversation.setVisitorId("another_visitor_123");
        when(conversationMapper.selectConversationById("conversation-1")).thenReturn(conversation);

        assertThrows(ServiceException.class,
                () -> service.getConversation("personal-info", "conversation-1", VISITOR_ID));
    }

    @Test
    void doesNotExposeAdminSceneToPublicApi()
    {
        assertThrows(ServiceException.class,
                () -> service.listConversations("admin", VISITOR_ID));
    }

    private AiChatConversation conversation()
    {
        AiChatConversation conversation = new AiChatConversation();
        conversation.setConversationId("conversation-1");
        conversation.setVisitorId(VISITOR_ID);
        conversation.setScene("personal-info");
        conversation.setTitle("新对话");
        conversation.setCreateTime(new Date());
        conversation.setUpdateTime(new Date());
        return conversation;
    }
}
