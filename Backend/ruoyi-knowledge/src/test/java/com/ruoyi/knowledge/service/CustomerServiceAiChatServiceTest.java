package com.ruoyi.knowledge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import com.ruoyi.knowledge.domain.AiChatAssistantData;
import com.ruoyi.knowledge.domain.AiChatConversation;
import com.ruoyi.knowledge.domain.AiChatMessageView;
import com.ruoyi.knowledge.domain.AiChatReplyView;
import com.ruoyi.knowledge.domain.AiModelConfig;
import com.ruoyi.knowledge.domain.CustomerServiceConfigView;
import com.ruoyi.knowledge.domain.CustomerServiceSendRequest;
import com.ruoyi.knowledge.domain.CustomerServiceTicket;

import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class CustomerServiceAiChatServiceTest
{
    @Mock
    private PublicConversationStore conversationStore;

    @Mock
    private KnowledgeQaService knowledgeQaService;

    @Mock
    private AiModelService modelService;

    @Mock
    private AiModelClientFactory modelClientFactory;

    @Mock
    private CustomerServiceTicketService ticketService;

    private CustomerServiceAiChatService service;

    @BeforeEach
    void setUp()
    {
        service = new CustomerServiceAiChatService(conversationStore, knowledgeQaService,
                modelService, modelClientFactory, ticketService, JsonMapper.builder().build());
    }

    @Test
    void standardTicketRetrievesKnowledgeAndDoesNotCreateHumanTicket()
    {
        ChatModel chatModel = mock(ChatModel.class);
        prepareRuntime(chatModel);
        when(chatModel.call(any(Prompt.class))).thenReturn(response(
                "{\"category\":\"standard\",\"reason\":\"普通退款咨询\",\"priority\":\"medium\",\"confidence\":0.95}"));
        when(conversationStore.recentHistory("conversation-1")).thenReturn(List.of());
        when(knowledgeQaService.answer(eq("customer-service"), anyString(), any(), anyString()))
                .thenReturn(new KnowledgeQaService.KnowledgeQaAnswer("退款将在 3 个工作日内到账。", false, List.of()));
        when(conversationStore.saveExchange(any(), anyString(), any(AiChatAssistantData.class)))
                .thenReturn(reply("退款将在 3 个工作日内到账。", "standard", "medium", null));

        var result = service.sendMessage("conversation-1",
                new CustomerServiceSendRequest("visitor_12345678", "退款多久到账？"));

        assertEquals("standard", result.category());
        assertEquals("medium", result.priority());
        assertEquals("RESOLVED", result.ticketStatus());
        assertFalse(result.assistantMessage().content().contains("人工"));
        verify(ticketService, never()).createComplexTicket(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void complexTicketRunsPreanalysisCreatesTicketAndReturnsWaitingMessage()
    {
        ChatModel chatModel = mock(ChatModel.class);
        prepareRuntime(chatModel);
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(response(
                        "{\"category\":\"complex\",\"reason\":\"涉及投诉\",\"priority\":\"high\",\"confidence\":0.99}"),
                        response("""
                                {"summary":"用户投诉交付延迟","solution":"核实订单并联系专项团队","suggestedReply":"我们会尽快核实并联系您"}
                                """));
        CustomerServiceTicket ticket = new CustomerServiceTicket();
        ticket.setTicketNo("CS20260928120000123");
        ticket.setStatus("WAITING");
        when(ticketService.createComplexTicket(eq("conversation-1"), eq("visitor_12345678"),
                eq("我要投诉订单一直不发货"), eq("complex"), eq("涉及投诉"),
                eq("high"), eq("用户投诉交付延迟"), eq("核实订单并联系专项团队"),
                eq("我们会尽快核实并联系您")))
                .thenReturn(ticket);
        when(conversationStore.saveExchange(any(), anyString(), any(AiChatAssistantData.class)))
                .thenReturn(reply("已记录您的问题，等待人工给您处理。", "complex", "high", ticket.getTicketNo()));

        var result = service.sendMessage("conversation-1",
                new CustomerServiceSendRequest("visitor_12345678", "我要投诉订单一直不发货"));

        assertEquals("complex", result.category());
        assertEquals("high", result.priority());
        assertEquals("CS20260928120000123", result.ticketNo());
        assertEquals("WAITING", result.ticketStatus());
        assertEquals("已记录您的问题，等待人工给您处理。", result.assistantMessage().content());
        verify(ticketService).createComplexTicket(eq("conversation-1"), eq("visitor_12345678"),
                eq("我要投诉订单一直不发货"), eq("complex"), eq("涉及投诉"),
                eq("high"), eq("用户投诉交付延迟"), eq("核实订单并联系专项团队"),
                eq("我们会尽快核实并联系您"));
    }

    private void prepareRuntime(ChatModel chatModel)
    {
        CustomerServiceConfigView config = new CustomerServiceConfigView(
                3L, "customer-service", "智能客服", "你好",
                11L, 12L, List.of("col-1"), 5, "1", new Date());
        AiModelConfig model = new AiModelConfig();
        model.setModelType("chat");
        when(knowledgeQaService.getConfig("customer-service")).thenReturn(config);
        when(knowledgeQaService.normalizeQuestion(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(modelService.getRuntimeModel(11L)).thenReturn(model);
        when(modelClientFactory.createChatModel(model)).thenReturn(chatModel);
        when(conversationStore.normalizeVisitorId("visitor_12345678", true)).thenReturn("visitor_12345678");
        AiChatConversation conversation = new AiChatConversation();
        conversation.setConversationId("conversation-1");
        conversation.setVisitorId("visitor_12345678");
        conversation.setScene("customer-service");
        conversation.setTitle("新对话");
        when(conversationStore.requireConversation("conversation-1", "visitor_12345678", "customer-service"))
                .thenReturn(conversation);
    }

    private AiChatReplyView reply(String content, String category, String priority, String ticketNo)
    {
        AiChatMessageView user = new AiChatMessageView(
                1L, "user", "问题", null, null, false, List.of(), null, null, null, new Date());
        AiChatMessageView assistant = new AiChatMessageView(
                2L, "assistant", content, category, 0.9D, false, List.of(),
                category, priority, ticketNo, new Date());
        return new AiChatReplyView("conversation-1", "问题", user, assistant);
    }

    private ChatResponse response(String text)
    {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
