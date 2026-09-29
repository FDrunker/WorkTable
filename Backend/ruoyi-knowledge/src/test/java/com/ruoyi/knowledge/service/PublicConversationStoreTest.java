package com.ruoyi.knowledge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ruoyi.knowledge.domain.AiChatAssistantData;
import com.ruoyi.knowledge.domain.AiChatConversation;
import com.ruoyi.knowledge.domain.AiChatMessage;
import com.ruoyi.knowledge.domain.AiChatReplyView;
import com.ruoyi.knowledge.domain.ChromaRecord;
import com.ruoyi.knowledge.domain.CollectionView;
import com.ruoyi.knowledge.domain.CustomerServiceSource;
import com.ruoyi.knowledge.mapper.AiChatConversationMapper;

import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class PublicConversationStoreTest
{
    @Mock
    private AiChatConversationMapper conversationMapper;

    @Mock
    private ChromaCollectionService chromaService;

    private PublicConversationStore store;

    @BeforeEach
    void setUp()
    {
        store = new PublicConversationStore(conversationMapper, chromaService, JsonMapper.builder().build());
    }

    @Test
    void persistsOnlySourceReferencesWhileReturningFullSource()
    {
        AiChatConversation conversation = conversation("conversation-1");
        CustomerServiceSource source = new CustomerServiceSource(
                "record-1",
                "collection-1",
                "售后知识",
                "doc-1",
                "退款说明.md",
                1,
                "退款将在 3 个工作日内到账。",
                0.12D);
        when(conversationMapper.countMessages("conversation-1")).thenReturn(0);

        AiChatReplyView reply = store.saveExchange(
                conversation,
                "退款多久到账？",
                AiChatAssistantData.standard("退款将在 3 个工作日内到账。", false, List.of(source)));

        ArgumentCaptor<AiChatMessage> messageCaptor = ArgumentCaptor.forClass(AiChatMessage.class);
        verify(conversationMapper, times(2)).insertMessage(messageCaptor.capture());
        String sourcesJson = messageCaptor.getAllValues().get(1).getSourcesJson();
        assertEquals("[{\"id\":\"record-1\",\"collectionId\":\"collection-1\"}]", sourcesJson);
        assertFalse(sourcesJson.contains("退款将在 3 个工作日内到账"));
        assertFalse(sourcesJson.contains("退款说明.md"));
        assertEquals("退款将在 3 个工作日内到账。", reply.assistantMessage().sources().get(0).content());
        verifyNoInteractions(chromaService);
    }

    @Test
    void hydratesStoredSourceReferencesWhenReadingConversation()
    {
        AiChatConversation conversation = conversation("conversation-1");
        AiChatMessage assistant = new AiChatMessage();
        assistant.setMessageId(2L);
        assistant.setConversationId("conversation-1");
        assistant.setRole("assistant");
        assistant.setContent("退款将在 3 个工作日内到账。");
        assistant.setNeedsClarification("0");
        assistant.setSourcesJson("""
                [{"id":"record-1","collectionId":"collection-1"}]
                """);
        assistant.setCreateTime(new Date());
        when(conversationMapper.selectConversationById("conversation-1")).thenReturn(conversation);
        when(conversationMapper.selectMessages("conversation-1")).thenReturn(List.of(assistant));
        when(chromaService.listCollections(null)).thenReturn(List.of(
                new CollectionView("collection-1", "售后知识", 10L, Map.of())));
        when(chromaService.getRecords("collection-1", List.of("record-1"))).thenReturn(List.of(
                new ChromaRecord("record-1", "当前知识库正文", Map.of(
                        "documentId", "doc-1",
                        "documentName", "退款说明.md",
                        "chunkIndex", 1))));

        var detail = store.getConversation("customer-service", "conversation-1", "visitor_12345678");

        CustomerServiceSource source = detail.messages().get(0).sources().get(0);
        assertEquals("record-1", source.id());
        assertEquals("collection-1", source.collectionId());
        assertEquals("售后知识", source.collectionName());
        assertEquals("退款说明.md", source.documentName());
        assertEquals("当前知识库正文", source.content());
    }

    private AiChatConversation conversation(String conversationId)
    {
        AiChatConversation conversation = new AiChatConversation();
        conversation.setConversationId(conversationId);
        conversation.setVisitorId("visitor_12345678");
        conversation.setScene("customer-service");
        conversation.setTitle("新对话");
        return conversation;
    }
}
