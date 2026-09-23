package com.ruoyi.knowledge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.config.KnowledgeProperties;
import com.ruoyi.knowledge.domain.DocumentImportRequest;
import com.ruoyi.knowledge.mapper.KnowledgeDocumentMapper;

@ExtendWith(MockitoExtension.class)
class KnowledgeDocumentServiceTest
{
    @Mock
    private KnowledgeDocumentMapper documentMapper;

    @Mock
    private ChromaCollectionService chromaService;

    @Mock
    private KnowledgeTaskService taskService;

    private KnowledgeDocumentService service;

    @BeforeEach
    void setUp()
    {
        service = new KnowledgeDocumentService(documentMapper, chromaService, taskService, new KnowledgeProperties());
    }

    @Test
    void rejectsImportWithoutEmbeddingModel()
    {
        DocumentImportRequest request = new DocumentImportRequest();
        request.setCollectionId("collection-1");
        request.setDocumentName("guide.md");
        request.setContent("# 标题");

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.importDocument(request, null, "admin"));

        assertEquals("请选择 Embedding 模型", exception.getMessage());
        verifyNoInteractions(documentMapper, chromaService, taskService);
    }
}
