package com.ruoyi.knowledge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chroma.vectorstore.ChromaApi;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.config.KnowledgeProperties;

@ExtendWith(MockitoExtension.class)
class ChromaCollectionServiceTest
{
    @Mock
    private ChromaApi chromaApi;

    private ChromaCollectionService service;

    @BeforeEach
    void setUp()
    {
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.getChroma().setTenant("tenant-1");
        properties.getChroma().setDatabase("database-1");
        service = new ChromaCollectionService(chromaApi, properties);
    }

    @Test
    void createsCollectionWithSpringAiApi()
    {
        when(chromaApi.listCollections("tenant-1", "database-1")).thenReturn(List.of());
        when(chromaApi.createCollection(any(), any(), any()))
                .thenReturn(new ChromaApi.Collection("new-id", "new-name", Map.of()));

        var collection = service.createCollection("new-name");

        assertEquals("new-id", collection.id());
        verify(chromaApi).createCollection("tenant-1", "database-1",
                new ChromaApi.CreateCollectionRequest("new-name"));
    }

    @Test
    void rejectsDuplicateCollectionName()
    {
        when(chromaApi.listCollections("tenant-1", "database-1"))
                .thenReturn(List.of(new ChromaApi.Collection("id-1", "existing", Map.of())));

        assertThrows(ServiceException.class, () -> service.createCollection("existing"));
    }

    @Test
    void deletesCollectionByChromaId()
    {
        service.deleteCollection("collection-uuid");

        verify(chromaApi).deleteCollection("tenant-1", "database-1", "collection-uuid");
    }
}
