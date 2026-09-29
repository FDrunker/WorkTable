package com.ruoyi.knowledge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.config.KnowledgeProperties;
import com.ruoyi.knowledge.domain.ChromaRecord;
import com.ruoyi.knowledge.domain.DocumentImportRequest;
import com.ruoyi.knowledge.domain.DocumentTaskPayload;
import com.ruoyi.knowledge.domain.KnowledgeDocument;
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

    @TempDir
    private Path tempDir;

    private KnowledgeDocumentService service;

    @BeforeEach
    void setUp()
    {
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.setStoragePath(tempDir.toString());
        service = new KnowledgeDocumentService(documentMapper, chromaService, taskService, properties);
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

    @Test
    void importsPastedMarkdownThroughTemporaryFileWithoutPersistingTaskContent() throws Exception
    {
        DocumentImportRequest request = new DocumentImportRequest();
        request.setCollectionId("collection-1");
        request.setDocumentName("guide.md");
        request.setModelId(1L);
        request.setContent("# 标题\n\n正文");
        when(taskService.createAndSubmit(anyString(), anyString(), anyString(), any(), any(), eq(1L),
                any(DocumentTaskPayload.class), anyString())).thenReturn(null);

        service.importDocument(request, null, "admin");

        ArgumentCaptor<KnowledgeDocument> documentCaptor = ArgumentCaptor.forClass(KnowledgeDocument.class);
        verify(documentMapper).insertDocument(documentCaptor.capture());
        KnowledgeDocument savedDocument = documentCaptor.getValue();
        assertNull(savedDocument.getContent());
        assertTrue(Files.exists(Path.of(savedDocument.getTempFilePath())));
        assertEquals("# 标题\n\n正文", Files.readString(Path.of(savedDocument.getTempFilePath())));

        ArgumentCaptor<DocumentTaskPayload> payloadCaptor = ArgumentCaptor.forClass(DocumentTaskPayload.class);
        verify(taskService).createAndSubmit(anyString(), anyString(), anyString(), any(), any(), eq(1L),
                payloadCaptor.capture(), anyString());
        assertNull(payloadCaptor.getValue().getRequest().getContent());
    }

    @Test
    void recoversDocumentContentFromChromaForEditing()
    {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setDocumentId("doc-1");
        document.setCollectionId("collection-1");
        when(documentMapper.selectDocumentById("doc-1")).thenReturn(document);
        when(chromaService.listRecords("collection-1")).thenReturn(List.of(
                new ChromaRecord("chunk-2", "第二段", Map.of("documentId", "doc-1", "chunkIndex", 1)),
                new ChromaRecord("chunk-1", "第一段", Map.of("documentId", "doc-1", "chunkIndex", 0))));

        KnowledgeDocument result = service.getDocument("doc-1");

        assertEquals("第一段\n\n第二段", result.getContent());
    }
}
