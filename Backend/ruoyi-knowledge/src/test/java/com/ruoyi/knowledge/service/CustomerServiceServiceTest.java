package com.ruoyi.knowledge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

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
import org.springframework.ai.embedding.EmbeddingModel;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.domain.AiModelConfig;
import com.ruoyi.knowledge.domain.ChromaSearchResult;
import com.ruoyi.knowledge.domain.CollectionView;
import com.ruoyi.knowledge.domain.CustomerServiceChatRequest;
import com.ruoyi.knowledge.domain.CustomerServiceConfig;
import com.ruoyi.knowledge.domain.CustomerServiceConfigRequest;
import com.ruoyi.knowledge.mapper.CustomerServiceConfigMapper;

import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class CustomerServiceServiceTest
{
    @Mock
    private CustomerServiceConfigMapper configMapper;

    @Mock
    private AiModelService modelService;

    @Mock
    private AiModelClientFactory modelClientFactory;

    @Mock
    private ChromaCollectionService chromaService;

    private CustomerServiceService service;

    @BeforeEach
    void setUp()
    {
        service = new CustomerServiceService(configMapper, modelService, modelClientFactory, chromaService,
                JsonMapper.builder().build());
    }

    @Test
    void retrievesTopKAndReturnsIntentAndAnswer()
    {
        CustomerServiceConfig config = runtimeConfig();
        AiModelConfig chatConfig = model("chat");
        AiModelConfig embeddingConfig = model("embedding");
        ChatModel chatModel = mock(ChatModel.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        float[] queryEmbedding = new float[] { 1F, 0F };
        when(configMapper.selectConfig()).thenReturn(config);
        when(modelService.getRuntimeModel(11L)).thenReturn(chatConfig);
        when(modelService.getRuntimeModel(12L)).thenReturn(embeddingConfig);
        when(modelClientFactory.createChatModel(chatConfig)).thenReturn(chatModel);
        when(modelClientFactory.createEmbeddingModel(embeddingConfig)).thenReturn(embeddingModel);
        when(embeddingModel.embed(anyString())).thenReturn(queryEmbedding);
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(response("{\"intent\":\"退款进度\",\"confidence\":0.93}"),
                        response("{\"answer\":\"退款会在 3 个工作日到账。\",\"needsClarification\":false,"
                                + "\"clarifyingQuestion\":\"\",\"usedSourceIds\":[\"chunk-1\",\"chunk-2\",\"chunk-3\"]}"));
        when(chromaService.query(eq("col-1"), any(float[].class), eq(3)))
                .thenReturn(List.of(hit("col-1", "chunk-1", 0.4D), hit("col-1", "chunk-2", 0.2D)));
        when(chromaService.query(eq("col-2"), any(float[].class), eq(3)))
                .thenReturn(List.of(hit("col-2", "chunk-3", 0.3D), hit("col-2", "chunk-4", 0.5D)));
        when(chromaService.listCollections(null)).thenReturn(List.of(
                new CollectionView("col-1", "售后知识", 10L, Map.of()),
                new CollectionView("col-2", "产品知识", 20L, Map.of())));

        var result = service.chat(new CustomerServiceChatRequest("退款多久到账？", List.of()));

        assertEquals("退款会在 3 个工作日到账。", result.answer());
        assertEquals("退款进度", result.intent());
        assertEquals(0.93D, result.intentConfidence());
        assertFalse(result.needsClarification());
        assertEquals(3, result.sources().size());
        assertEquals("chunk-2", result.sources().get(0).id());
        assertFalse(result.sources().stream().anyMatch(source -> "chunk-4".equals(source.id())));
        verify(chromaService).query("col-1", queryEmbedding, 3);
        verify(chromaService).query("col-2", queryEmbedding, 3);
    }

    @Test
    void asksClarifyingQuestionWhenNoKnowledgeMatches()
    {
        CustomerServiceConfig config = runtimeConfig();
        AiModelConfig chatConfig = model("chat");
        AiModelConfig embeddingConfig = model("embedding");
        ChatModel chatModel = mock(ChatModel.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(configMapper.selectConfig()).thenReturn(config);
        when(modelService.getRuntimeModel(11L)).thenReturn(chatConfig);
        when(modelService.getRuntimeModel(12L)).thenReturn(embeddingConfig);
        when(modelClientFactory.createChatModel(chatConfig)).thenReturn(chatModel);
        when(modelClientFactory.createEmbeddingModel(embeddingConfig)).thenReturn(embeddingModel);
        when(embeddingModel.embed(anyString())).thenReturn(new float[] { 1F, 0F });
        when(chatModel.call(any(Prompt.class))).thenReturn(response("{\"intent\":\"账号问题\",\"confidence\":0.8}"));
        when(chromaService.query(eq("col-1"), any(float[].class), eq(3))).thenReturn(List.of());
        when(chromaService.query(eq("col-2"), any(float[].class), eq(3))).thenReturn(List.of());
        when(chromaService.listCollections(null)).thenReturn(List.of());

        var result = service.chat(new CustomerServiceChatRequest("登不上怎么办？", List.of()));

        assertTrue(result.needsClarification());
        assertEquals("账号问题", result.intent());
        assertTrue(result.answer().contains("请补充"));
        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    void asksClarifyingQuestionWhenModelFindsTheQuestionAmbiguous()
    {
        CustomerServiceConfig config = runtimeConfig();
        AiModelConfig chatConfig = model("chat");
        AiModelConfig embeddingConfig = model("embedding");
        ChatModel chatModel = mock(ChatModel.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(configMapper.selectConfig()).thenReturn(config);
        when(modelService.getRuntimeModel(11L)).thenReturn(chatConfig);
        when(modelService.getRuntimeModel(12L)).thenReturn(embeddingConfig);
        when(modelClientFactory.createChatModel(chatConfig)).thenReturn(chatModel);
        when(modelClientFactory.createEmbeddingModel(embeddingConfig)).thenReturn(embeddingModel);
        when(embeddingModel.embed(anyString())).thenReturn(new float[] { 1F, 0F });
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(response("{\"intent\":\"退款\",\"confidence\":0.91}"),
                        response("{\"answer\":\"请告诉我订单号。\",\"needsClarification\":true,"
                                + "\"clarifyingQuestion\":\"请问你要查询哪个订单的退款进度？\","
                                + "\"usedSourceIds\":[\"chunk-1\"]}"));
        when(chromaService.query(eq("col-1"), any(float[].class), eq(3)))
                .thenReturn(List.of(hit("col-1", "chunk-1", 0.2D)));
        when(chromaService.query(eq("col-2"), any(float[].class), eq(3))).thenReturn(List.of());
        when(chromaService.listCollections(null)).thenReturn(List.of());

        var result = service.chat(new CustomerServiceChatRequest("退款呢？", List.of()));

        assertTrue(result.needsClarification());
        assertEquals("请问你要查询哪个订单的退款进度？", result.answer());
        assertEquals(1, result.sources().size());
    }

    @Test
    void savesValidatedConfig()
    {
        AiModelConfig chatConfig = model("chat");
        AiModelConfig embeddingConfig = model("embedding");
        CustomerServiceConfig saved = runtimeConfig();
        saved.setConfigId(1L);
        when(modelService.getRuntimeModel(11L)).thenReturn(chatConfig);
        when(modelService.getRuntimeModel(12L)).thenReturn(embeddingConfig);
        when(chromaService.listCollections(null)).thenReturn(List.of(
                new CollectionView("col-1", "售后知识", 10L, Map.of()),
                new CollectionView("col-2", "产品知识", 20L, Map.of())));
        when(configMapper.selectConfig()).thenReturn(null, saved);

        var result = service.saveConfig(
                new CustomerServiceConfigRequest(11L, 12L, List.of("col-1", "col-2"), 3, "1"), "tester");

        assertEquals(List.of("col-1", "col-2"), result.collectionIds());
        assertEquals(3, result.topK());
        verify(configMapper).insertConfig(any(CustomerServiceConfig.class));
    }

    @Test
    void rejectsChatWhenConfigurationIsMissing()
    {
        when(configMapper.selectConfig()).thenReturn(null);

        assertThrows(ServiceException.class,
                () -> service.chat(new CustomerServiceChatRequest("你好", List.of())));
        verify(modelClientFactory, never()).createChatModel(any());
    }

    private CustomerServiceConfig runtimeConfig()
    {
        CustomerServiceConfig config = new CustomerServiceConfig();
        config.setConfigId(1L);
        config.setChatModelId(11L);
        config.setEmbeddingModelId(12L);
        config.setCollectionIdsJson("[\"col-1\",\"col-2\"]");
        config.setTopK(3);
        config.setEnabled("1");
        return config;
    }

    private AiModelConfig model(String type)
    {
        AiModelConfig model = new AiModelConfig();
        model.setModelType(type);
        return model;
    }

    private ChromaSearchResult hit(String collectionId, String id, double distance)
    {
        return new ChromaSearchResult(collectionId, id, "知识片段 " + id,
                Map.of("documentId", "doc-1", "documentName", "售后说明.md", "chunkIndex", 1),
                distance);
    }

    private ChatResponse response(String text)
    {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
