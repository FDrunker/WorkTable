package com.ruoyi.knowledge.service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.knowledge.domain.AiModelConfig;
import com.ruoyi.knowledge.domain.DocumentImportRequest;
import com.ruoyi.knowledge.domain.DocumentTaskPayload;
import com.ruoyi.knowledge.domain.KnowledgeDocument;
import com.ruoyi.knowledge.domain.KnowledgeTask;
import com.ruoyi.knowledge.mapper.KnowledgeDocumentMapper;
import com.ruoyi.knowledge.mapper.KnowledgeTaskMapper;
import com.ruoyi.knowledge.model.KnowledgeDocumentStatus;
import com.ruoyi.knowledge.model.KnowledgeTaskStatus;

import tools.jackson.databind.json.JsonMapper;

/**
 * 文档导入、更新和重建任务处理器。
 */
@Service
public class DocumentTaskProcessor
{
    private static final int WRITE_BATCH_SIZE = 64;

    private final KnowledgeTaskMapper taskMapper;

    private final KnowledgeDocumentMapper documentMapper;

    private final KnowledgeTaskService taskService;

    private final KnowledgeDocumentService documentService;

    private final ChromaCollectionService chromaService;

    private final MarkdownChunker chunker;

    private final AiModelService modelService;

    private final AiModelClientFactory modelClientFactory;

    private final JsonMapper objectMapper;

    public DocumentTaskProcessor(KnowledgeTaskMapper taskMapper, KnowledgeDocumentMapper documentMapper,
            KnowledgeTaskService taskService, KnowledgeDocumentService documentService,
            ChromaCollectionService chromaService, MarkdownChunker chunker, AiModelService modelService,
            AiModelClientFactory modelClientFactory, JsonMapper objectMapper)
    {
        this.taskMapper = taskMapper;
        this.documentMapper = documentMapper;
        this.taskService = taskService;
        this.documentService = documentService;
        this.chromaService = chromaService;
        this.chunker = chunker;
        this.modelService = modelService;
        this.modelClientFactory = modelClientFactory;
        this.objectMapper = objectMapper;
    }

    public void process(Long taskId)
    {
        KnowledgeTask task = taskMapper.selectTaskById(taskId);
        if (task == null)
        {
            return;
        }
        try
        {
            if ("1".equals(task.getCancelRequested()))
            {
                finishDocument(task.getDocumentId(), KnowledgeDocumentStatus.CANCELLED);
                taskService.finish(taskId, KnowledgeTaskStatus.CANCELLED, "CANCELLED", "任务已取消", "");
                return;
            }
            taskService.updateProgress(taskId, KnowledgeTaskStatus.RUNNING, 5, "PREPARING", "读取文档配置");
            finishDocument(task.getDocumentId(), KnowledgeDocumentStatus.RUNNING);
            DocumentTaskPayload payload = objectMapper.readValue(task.getRequestPayload(), DocumentTaskPayload.class);
            processDocument(task, payload);
        }
        catch (TaskCancelledException e)
        {
            finishDocument(task.getDocumentId(), KnowledgeDocumentStatus.CANCELLED);
            taskService.finish(taskId, KnowledgeTaskStatus.CANCELLED, "CANCELLED", "任务已取消", "");
        }
        catch (Exception e)
        {
            String message = StringUtils.isBlank(e.getMessage()) ? e.getClass().getSimpleName() : e.getMessage();
            finishDocument(task.getDocumentId(), KnowledgeDocumentStatus.FAILED);
            taskService.finish(taskId, KnowledgeTaskStatus.FAILED, "FAILED", "任务失败", trim(message));
        }
    }

    private void processDocument(KnowledgeTask task, DocumentTaskPayload payload)
    {
        DocumentImportRequest request = payload.getRequest();
        if (request == null)
        {
            throw new ServiceException("任务请求为空");
        }
        String content = request.getContent();
        KnowledgeDocument document = documentMapper.selectDocumentById(task.getDocumentId());
        if ((StringUtils.isBlank(content)) && document != null && StringUtils.isNotBlank(document.getTempFilePath()))
        {
            content = readTempFile(document.getTempFilePath());
        }
        if (StringUtils.isBlank(content))
        {
            throw new ServiceException("Markdown 内容不能为空");
        }
        request.setContent(content);

        List<String> chunks = chunker.split(content, request.getChunkSize(), request.getChunkOverlap());
        if (chunks.isEmpty())
        {
            throw new ServiceException("切片结果为空");
        }
        taskService.updateProgress(task.getTaskId(), KnowledgeTaskStatus.RUNNING, 20, "CHUNKING",
                "已切分为 " + chunks.size() + " 个片段");
        ensureNotCancelled(task.getTaskId());

        if (request.getModelId() == null)
        {
            throw new ServiceException("请选择 Embedding 模型");
        }
        AiModelConfig model = modelService.getRuntimeModel(request.getModelId());
        if (!"embedding".equals(model.getModelType()))
        {
            throw new ServiceException("所选模型不是 embedding 类型");
        }
        EmbeddingModel embeddingModel = modelClientFactory.createEmbeddingModel(model);
        List<float[]> embeddings = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++)
        {
            ensureNotCancelled(task.getTaskId());
            embeddings.add(embeddingModel.embed(chunks.get(i)));
            int progress = 25 + (int) Math.floor(50.0 * (i + 1) / chunks.size());
            taskService.updateProgress(task.getTaskId(), KnowledgeTaskStatus.RUNNING, progress, "EMBEDDING",
                    "生成向量 " + (i + 1) + "/" + chunks.size());
        }

        ensureNotCancelled(task.getTaskId());
        List<String> oldRecordIds = oldRecordIds(task, payload);
        List<String> ids = buildIds(task.getDocumentId(), chunks.size());
        List<Map<String, Object>> metadata = buildMetadata(request, task.getDocumentId(), chunks.size());
        int written = 0;
        for (int start = 0; start < chunks.size(); start += WRITE_BATCH_SIZE)
        {
            int end = Math.min(start + WRITE_BATCH_SIZE, chunks.size());
            chromaService.upsert(ids.subList(start, end), embeddings.subList(start, end), metadata.subList(start, end),
                    chunks.subList(start, end), request.getCollectionId());
            written = end;
            int progress = 80 + (int) Math.floor(15.0 * written / chunks.size());
            taskService.updateProgress(task.getTaskId(), KnowledgeTaskStatus.RUNNING, progress, "WRITING",
                    "写入 Chroma " + written + "/" + chunks.size());
        }
        chromaService.deleteByIds(request.getCollectionId(), oldRecordIds);

        document = documentMapper.selectDocumentById(task.getDocumentId());
        if (document != null)
        {
            String tempFilePath = document.getTempFilePath();
            document.setStatus(KnowledgeDocumentStatus.SUCCESS);
            document.setChunkCount(chunks.size());
            document.setContent(content);
            document.setTempFilePath("");
            documentMapper.updateDocument(document);
            deleteTempFile(tempFilePath);
        }
        taskService.finish(task.getTaskId(), KnowledgeTaskStatus.SUCCESS, "COMPLETED", "处理完成", "");
    }

    private List<String> oldRecordIds(KnowledgeTask task, DocumentTaskPayload payload)
    {
        if (payload.isManaged())
        {
            return chromaService.listRecords(task.getCollectionId()).stream()
                    .filter(record -> task.getDocumentId().equals(record.metadata().get("documentId")))
                    .map(record -> record.id())
                    .toList();
        }
        if (payload.getOriginalRecordIds() != null && !payload.getOriginalRecordIds().isEmpty())
        {
            return payload.getOriginalRecordIds();
        }
        return List.of();
    }

    private List<String> buildIds(String documentId, int count)
    {
        List<String> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
        {
            ids.add(documentId + "_" + i + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        }
        return ids;
    }

    private List<Map<String, Object>> buildMetadata(DocumentImportRequest request, String documentId, int count)
    {
        List<Map<String, Object>> metadata = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
        {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("documentId", documentId);
            item.put("documentName", request.getDocumentName());
            item.put("chunkIndex", i);
            item.put("sourceType", "KNOWLEDGE");
            item.put("createdAt", new Date().toString());
            metadata.add(item);
        }
        return metadata;
    }

    private void finishDocument(String documentId, String status)
    {
        KnowledgeDocument document = documentMapper.selectDocumentById(documentId);
        if (document != null)
        {
            document.setStatus(status);
            documentMapper.updateDocument(document);
        }
    }

    private void ensureNotCancelled(Long taskId)
    {
        if (taskService.cancelRequested(taskId))
        {
            throw new TaskCancelledException();
        }
    }

    private String readTempFile(String path)
    {
        try
        {
            return Files.readString(Path.of(path), StandardCharsets.UTF_8);
        }
        catch (Exception e)
        {
            throw new ServiceException("读取 Markdown 暂存文件失败");
        }
    }

    private void deleteTempFile(String path)
    {
        if (StringUtils.isNotBlank(path))
        {
            try
            {
                Files.deleteIfExists(Path.of(path));
            }
            catch (Exception ignored)
            {
            }
        }
    }

    private String trim(String message)
    {
        return message.length() > 1800 ? message.substring(0, 1800) : message;
    }

    private static class TaskCancelledException extends RuntimeException
    {
        private static final long serialVersionUID = 1L;
    }
}
