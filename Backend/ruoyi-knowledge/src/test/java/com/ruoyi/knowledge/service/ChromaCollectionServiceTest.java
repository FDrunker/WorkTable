package com.ruoyi.knowledge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.ai.chroma.vectorstore.ChromaApi.QueryRequest;
import org.springframework.ai.chroma.vectorstore.ChromaApi.QueryResponse;

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

    @Test
    void queriesTopKWithDocumentsMetadataAndDistances()
    {
        when(chromaApi.queryCollection(any(), any(), any(), any())).thenReturn(new QueryResponse(
                List.of(List.of("record-1")),
                null,
                List.of(List.of("退货后 3 个工作日退款")),
                List.of(List.of(Map.of("documentId", "doc-1"))),
                List.of(List.of(0.12D))));

        var results = service.query("collection-1", new float[] { 1F, 0F }, 3);

        assertEquals(1, results.size());
        assertEquals("record-1", results.get(0).id());
        assertEquals("退货后 3 个工作日退款", results.get(0).content());
        assertEquals(0.12D, results.get(0).distance());
        ArgumentCaptor<QueryRequest> requestCaptor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(chromaApi).queryCollection(eq("tenant-1"), eq("database-1"), eq("collection-1"),
                requestCaptor.capture());
        assertEquals(3, requestCaptor.getValue().nResults());
        assertEquals(List.of(1F, 0F).size(), requestCaptor.getValue().queryEmbeddings().get(0).length);
        assertEquals(List.of(QueryRequest.Include.METADATAS, QueryRequest.Include.DOCUMENTS,
                QueryRequest.Include.DISTANCES), requestCaptor.getValue().include());
    }
}
