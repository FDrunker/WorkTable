package com.ruoyi.knowledge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

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

    @Test
    void rejectsNonMarkdownUpload()
    {
        DocumentImportRequest request = new DocumentImportRequest();
        request.setCollectionId("collection-1");
        request.setDocumentName("guide.txt");
        request.setModelId(1L);
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("guide.txt");

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.importDocument(request, file, "admin"));

        assertEquals("仅支持 Markdown（.md）文件", exception.getMessage());
        verifyNoInteractions(documentMapper, chromaService, taskService);
    }
}
