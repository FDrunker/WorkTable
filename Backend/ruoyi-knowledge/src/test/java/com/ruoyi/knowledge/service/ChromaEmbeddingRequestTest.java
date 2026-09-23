package com.ruoyi.knowledge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chroma.vectorstore.ChromaApi.AddEmbeddingsRequest;

class ChromaEmbeddingRequestTest
{
    @Test
    void supportsDocumentOnlyUpsertWithoutEmbeddings()
    {
        AddEmbeddingsRequest request = new AddEmbeddingsRequest(
                List.of("doc-1"),
                null,
                List.of(Map.of("documentId", "doc-1")),
                List.of("# 标题"));

        assertNull(request.embeddings());
        assertEquals(List.of("# 标题"), request.documents());
        assertEquals("doc-1", request.ids().get(0));
    }
}
