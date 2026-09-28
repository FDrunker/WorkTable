package com.ruoyi.knowledge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.ruoyi.knowledge.domain.DocumentImportRequest;
import com.ruoyi.knowledge.domain.DocumentTaskPayload;

import tools.jackson.databind.json.JsonMapper;

class KnowledgeTaskPayloadJsonTest
{
    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Test
    void roundTripsTaskPayloadWithJackson3() throws Exception
    {
        DocumentImportRequest request = new DocumentImportRequest();
        request.setCollectionId("collection-1");
        request.setDocumentName("guide.md");
        request.setContent("# 标题");

        DocumentTaskPayload payload = new DocumentTaskPayload();
        payload.setRequest(request);
        payload.setManaged(true);
        payload.setOriginalRecordIds(java.util.List.of("old-1"));

        String json = objectMapper.writeValueAsString(payload);
        DocumentTaskPayload restored = objectMapper.readValue(json, DocumentTaskPayload.class);

        assertEquals("guide.md", restored.getRequest().getDocumentName());
        assertEquals("# 标题", restored.getRequest().getContent());
        assertTrue(restored.isManaged());
        assertEquals("old-1", restored.getOriginalRecordIds().get(0));
    }

    @Test
    void ignoresLegacySeparatorInPersistedTaskPayload() throws Exception
    {
        String json = "{\"request\":{\"collectionId\":\"collection-1\",\"documentName\":\"guide.md\","
                + "\"content\":\"# 标题\",\"separator\":\"\\n\"},\"managed\":true}";

        DocumentTaskPayload restored = objectMapper.readValue(json, DocumentTaskPayload.class);

        assertEquals("guide.md", restored.getRequest().getDocumentName());
        assertEquals("# 标题", restored.getRequest().getContent());
        assertTrue(restored.isManaged());
    }
}
