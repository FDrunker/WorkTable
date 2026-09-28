package com.ruoyi.knowledge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ruoyi.knowledge.domain.AiChatAssistantData;
import com.ruoyi.knowledge.domain.AiChatConversation;
import com.ruoyi.knowledge.domain.AiChatMessageView;
import com.ruoyi.knowledge.domain.AiChatReplyView;
import com.ruoyi.knowledge.domain.ResumeAiSendRequest;

@ExtendWith(MockitoExtension.class)
class ResumeAiChatServiceTest
{
    @Mock
    private PublicConversationStore conversationStore;

    @Mock
    private KnowledgeQaService knowledgeQaService;

    private ResumeAiChatService service;

    @BeforeEach
    void setUp()
    {
        service = new ResumeAiChatService(conversationStore, knowledgeQaService);
    }

    @Test
    void retrievesResumeKnowledgeAndReturnsAnswerDirectly()
    {
        AiChatConversation conversation = new AiChatConversation();
        conversation.setConversationId("conversation-1");
        conversation.setVisitorId("resume_12345678");
        conversation.setScene("personal-info");
        conversation.setTitle("新对话");
        when(conversationStore.normalizeVisitorId("resume_12345678", true)).thenReturn("resume_12345678");
        when(knowledgeQaService.normalizeQuestion("有哪些项目经验？")).thenReturn("有哪些项目经验？");
        when(conversationStore.requireConversation("conversation-1", "resume_12345678", "personal-info"))
                .thenReturn(conversation);
        when(conversationStore.recentHistory("conversation-1")).thenReturn(List.of());
        when(knowledgeQaService.answer("personal-info", "有哪些项目经验？", List.of(), ""))
                .thenReturn(new KnowledgeQaService.KnowledgeQaAnswer("有三段项目经历。", false, List.of()));
        when(conversationStore.saveExchange(eq(conversation), eq("有哪些项目经验？"),
                any(AiChatAssistantData.class))).thenReturn(reply());

        var result = service.sendMessage("conversation-1",
                new ResumeAiSendRequest("resume_12345678", "有哪些项目经验？"));

        assertEquals("有三段项目经历。", result.assistantMessage().content());
        verify(knowledgeQaService).answer("personal-info", "有哪些项目经验？", List.of(), "");
    }

    private AiChatReplyView reply()
    {
        AiChatMessageView user = new AiChatMessageView(
                1L, "user", "有哪些项目经验？", null, null, false, List.of(),
                null, null, null, new Date());
        AiChatMessageView assistant = new AiChatMessageView(
                2L, "assistant", "有三段项目经历。", null, null, false, List.of(),
                null, null, null, new Date());
        return new AiChatReplyView("conversation-1", "有哪些项目经验？", user, assistant);
    }
}
