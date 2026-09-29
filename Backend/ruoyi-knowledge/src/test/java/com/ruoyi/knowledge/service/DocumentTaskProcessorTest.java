package com.ruoyi.knowledge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
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
import org.springframework.ai.embedding.EmbeddingModel;

import com.ruoyi.knowledge.domain.AiModelConfig;
import com.ruoyi.knowledge.domain.ChromaRecord;
import com.ruoyi.knowledge.domain.DocumentImportRequest;
import com.ruoyi.knowledge.domain.DocumentTaskPayload;
import com.ruoyi.knowledge.domain.KnowledgeDocument;
import com.ruoyi.knowledge.domain.KnowledgeTask;
import com.ruoyi.knowledge.mapper.KnowledgeDocumentMapper;
import com.ruoyi.knowledge.mapper.KnowledgeTaskMapper;
import com.ruoyi.knowledge.model.KnowledgeDocumentStatus;
import com.ruoyi.knowledge.model.KnowledgeTaskType;

import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class DocumentTaskProcessorTest
{
    @Mock
    private KnowledgeTaskMapper taskMapper;

    @Mock
    private KnowledgeDocumentMapper documentMapper;

    @Mock
    private KnowledgeTaskService taskService;

    @Mock
    private KnowledgeDocumentService documentService;

    @Mock
    private ChromaCollectionService chromaService;

    @Mock
    private MarkdownChunker chunker;

    @Mock
    private AiModelService modelService;

    @Mock
    private AiModelClientFactory modelClientFactory;

    @Mock
    private EmbeddingModel embeddingModel;

    @TempDir
    private Path tempDir;

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    private DocumentTaskProcessor processor;

    @BeforeEach
    void setUp()
    {
        processor = new DocumentTaskProcessor(taskMapper, documentMapper, taskService, documentService,
                chromaService, chunker, modelService, modelClientFactory, objectMapper);
    }

    @Test
    void deletesTemporaryMarkdownAfterSuccessfulImport() throws Exception
    {
        Path tempFile = tempDir.resolve("guide.md");
        Files.writeString(tempFile, "# 标题\n\n正文");
        KnowledgeTask task = task(KnowledgeTaskType.IMPORT);
        KnowledgeDocument document = document(tempFile.toString());
        stubTask(task, document, true);
        when(chunker.split(anyString(), eq(1024), eq(100))).thenReturn(List.of("# 标题\n\n正文"));

        processor.process(task.getTaskId());

        ArgumentCaptor<KnowledgeDocument> documentCaptor = ArgumentCaptor.forClass(KnowledgeDocument.class);
        verify(documentMapper, atLeastOnce()).updateDocument(documentCaptor.capture());
        KnowledgeDocument updated = documentCaptor.getAllValues().get(documentCaptor.getAllValues().size() - 1);
        assertEquals(KnowledgeDocumentStatus.SUCCESS, updated.getStatus());
        assertEquals("", updated.getTempFilePath());
        assertFalse(Files.exists(tempFile));
    }

    @Test
    void reindexesExistingChunksWithoutReadingDatabaseContent() throws Exception
    {
        KnowledgeTask task = task(KnowledgeTaskType.REINDEX);
        KnowledgeDocument document = document(null);
        ChromaRecord existingChunk = new ChromaRecord("old-1", "现有切片",
                Map.of("documentId", "doc-1", "chunkIndex", 0));
        stubTask(task, document, true);
        when(chromaService.listRecords("collection-1")).thenReturn(List.of(existingChunk));

        processor.process(task.getTaskId());

        verifyNoInteractions(chunker);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> documentsCaptor = ArgumentCaptor.forClass(List.class);
        verify(chromaService).upsert(anyList(), anyList(), anyList(), documentsCaptor.capture(), eq("collection-1"));
        assertEquals(List.of("现有切片"), documentsCaptor.getValue());
        ArgumentCaptor<KnowledgeDocument> documentCaptor = ArgumentCaptor.forClass(KnowledgeDocument.class);
        verify(documentMapper, atLeastOnce()).updateDocument(documentCaptor.capture());
        KnowledgeDocument updated = documentCaptor.getAllValues().get(documentCaptor.getAllValues().size() - 1);
        assertEquals(KnowledgeDocumentStatus.SUCCESS, updated.getStatus());
        assertEquals(1, updated.getChunkCount());
    }

    private void stubTask(KnowledgeTask task, KnowledgeDocument document, boolean managed) throws Exception
    {
        DocumentImportRequest request = new DocumentImportRequest();
        request.setCollectionId("collection-1");
        request.setDocumentName("guide.md");
        request.setModelId(1L);
        DocumentTaskPayload payload = new DocumentTaskPayload();
        payload.setRequest(request);
        payload.setManaged(managed);
        task.setRequestPayload(objectMapper.writeValueAsString(payload));

        AiModelConfig model = new AiModelConfig();
        model.setModelType("embedding");
        when(taskMapper.selectTaskById(task.getTaskId())).thenReturn(task);
        when(documentMapper.selectDocumentById(task.getDocumentId())).thenReturn(document);
        when(taskService.cancelRequested(task.getTaskId())).thenReturn(false);
        when(modelService.getRuntimeModel(1L)).thenReturn(model);
        when(modelClientFactory.createEmbeddingModel(model)).thenReturn(embeddingModel);
        when(embeddingModel.embed(anyString())).thenReturn(new float[] { 1.0F });
    }

    private KnowledgeTask task(String taskType)
    {
        KnowledgeTask task = new KnowledgeTask();
        task.setTaskId(1L);
        task.setTaskType(taskType);
        task.setDocumentId("doc-1");
        task.setCollectionId("collection-1");
        return task;
    }

    private KnowledgeDocument document(String tempFilePath)
    {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setDocumentId("doc-1");
        document.setCollectionId("collection-1");
        document.setDocumentName("guide.md");
        document.setChunkSize(1024);
        document.setChunkOverlap(100);
        document.setTempFilePath(tempFilePath);
        return document;
    }
}
