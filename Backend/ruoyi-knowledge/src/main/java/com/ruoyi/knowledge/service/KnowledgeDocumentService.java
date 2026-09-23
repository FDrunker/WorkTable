package com.ruoyi.knowledge.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.config.KnowledgeProperties;
import com.ruoyi.knowledge.domain.ChromaRecord;
import com.ruoyi.knowledge.domain.DocumentChunkView;
import com.ruoyi.knowledge.domain.DocumentImportRequest;
import com.ruoyi.knowledge.domain.DocumentTaskPayload;
import com.ruoyi.knowledge.domain.DocumentView;
import com.ruoyi.knowledge.domain.KnowledgeDocument;
import com.ruoyi.knowledge.domain.KnowledgeTask;
import com.ruoyi.knowledge.mapper.KnowledgeDocumentMapper;
import com.ruoyi.knowledge.model.KnowledgeDocumentStatus;
import com.ruoyi.knowledge.model.KnowledgeTaskType;

/**
 * 知识库逻辑文档管理。
 */
@Service
public class KnowledgeDocumentService
{
    private final KnowledgeDocumentMapper documentMapper;

    private final ChromaCollectionService chromaService;

    private final KnowledgeTaskService taskService;

    private final KnowledgeProperties properties;

    public KnowledgeDocumentService(KnowledgeDocumentMapper documentMapper, ChromaCollectionService chromaService,
            KnowledgeTaskService taskService, KnowledgeProperties properties)
    {
        this.documentMapper = documentMapper;
        this.chromaService = chromaService;
        this.taskService = taskService;
        this.properties = properties;
    }

    public TableDataInfo list(String collectionId, String collectionName, String documentName,
            int pageNum, int pageSize)
    {
        if (!StringUtils.hasText(collectionId))
        {
            throw new ServiceException("集合ID不能为空");
        }
        List<ChromaRecord> records = chromaService.listRecords(collectionId);
        Map<String, List<ChromaRecord>> grouped = groupRecords(records);

        KnowledgeDocument query = new KnowledgeDocument();
        query.setCollectionId(collectionId);
        List<KnowledgeDocument> localDocuments = documentMapper.selectDocumentList(query);
        Map<String, KnowledgeDocument> localMap = localDocuments.stream()
                .collect(Collectors.toMap(KnowledgeDocument::getDocumentId, item -> item, (left, right) -> left,
                        LinkedHashMap::new));

        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(grouped.keySet());
        keys.addAll(localMap.keySet());

        List<DocumentView> result = new ArrayList<>();
        for (String documentId : keys)
        {
            KnowledgeDocument local = localMap.get(documentId);
            List<ChromaRecord> chunks = grouped.getOrDefault(documentId, List.of());
            String name = local != null ? local.getDocumentName() : metadata(chunks, "documentName", documentId);
            if (StringUtils.hasText(documentName) && !name.toLowerCase().contains(documentName.toLowerCase()))
            {
                continue;
            }
            result.add(new DocumentView(documentId, collectionId,
                    local != null ? local.getCollectionName() : collectionName,
                    name,
                    local != null ? local.getSourceType() : "EXTERNAL",
                    chunks.size(),
                    local != null ? local.getEmbeddingModelId() : null,
                    local != null ? local.getStatus() : "EXTERNAL",
                    local != null,
                    local != null ? local.getCreateTime() : null));
        }
        result.sort(Comparator.comparing(DocumentView::createTime,
                Comparator.nullsLast(Comparator.reverseOrder())));

        int safePage = Math.max(pageNum, 1);
        int safeSize = Math.max(pageSize, 1);
        int from = Math.min((safePage - 1) * safeSize, result.size());
        int to = Math.min(from + safeSize, result.size());

        TableDataInfo table = new TableDataInfo();
        table.setCode(200);
        table.setMsg("查询成功");
        table.setRows(result.subList(from, to));
        table.setTotal(result.size());
        return table;
    }

    public KnowledgeDocument getDocument(String documentId)
    {
        KnowledgeDocument document = documentMapper.selectDocumentById(documentId);
        if (document == null)
        {
            throw new ServiceException("仅本系统管理的文档可以查看原文");
        }
        return document;
    }

    public List<DocumentChunkView> chunks(String collectionId, String documentId)
    {
        return chromaService.listRecords(collectionId).stream()
                .filter(record -> documentId.equals(metadata(record, "documentId", record.id())))
                .map(record -> new DocumentChunkView(record.id(), record.content(), record.metadata()))
                .toList();
    }

    public KnowledgeTask importDocument(DocumentImportRequest request, MultipartFile file, String username)
    {
        String content = readContent(request, file);
        String documentName = resolveDocumentName(request, file);
        String documentId = "doc_" + UUID.randomUUID().toString().replace("-", "");
        String tempFilePath = saveTempFile(file);

        KnowledgeDocument document = new KnowledgeDocument();
        document.setDocumentId(documentId);
        document.setCollectionId(request.getCollectionId());
        document.setCollectionName(request.getCollectionName());
        document.setDocumentName(documentName);
        document.setSourceType(file != null && !file.isEmpty() ? "UPLOAD" : "PASTE");
        document.setContent(content);
        document.setTempFilePath(tempFilePath);
        applyChunkConfig(document, request);
        document.setEmbeddingModelId(request.getModelId());
        document.setStatus(KnowledgeDocumentStatus.PENDING);
        document.setChunkCount(0);
        document.setCreateBy(username);
        documentMapper.insertDocument(document);

        request.setDocumentId(documentId);
        request.setDocumentName(documentName);
        request.setContent(content);
        return submitTask(KnowledgeTaskType.IMPORT, document, true, List.of(), request, username);
    }

    public KnowledgeTask updateDocument(String documentId, DocumentImportRequest request, MultipartFile file,
            String username)
    {
        if (!StringUtils.hasText(documentId))
        {
            throw new ServiceException("文档ID不能为空");
        }
        KnowledgeDocument existing = documentMapper.selectDocumentById(documentId);
        boolean managed = existing != null;
        String content = readContent(request, file);
        String documentName = resolveDocumentName(request, file);
        if (!StringUtils.hasText(documentName))
        {
            documentName = managed ? existing.getDocumentName() : documentId;
        }

        List<String> originalRecordIds = new ArrayList<>();
        if (!managed)
        {
            originalRecordIds.add(documentId);
        }

        KnowledgeDocument document = managed ? existing : new KnowledgeDocument();
        document.setDocumentId(documentId);
        document.setCollectionId(request.getCollectionId());
        document.setCollectionName(request.getCollectionName());
        document.setDocumentName(documentName);
        document.setSourceType(file != null && !file.isEmpty() ? "UPLOAD" : "PASTE");
        document.setContent(content);
        document.setTempFilePath(saveTempFile(file));
        applyChunkConfig(document, request);
        document.setEmbeddingModelId(request.getModelId());
        document.setStatus(KnowledgeDocumentStatus.PENDING);
        document.setCreateBy(managed ? existing.getCreateBy() : username);
        document.setUpdateBy(username);
        if (managed)
        {
            documentMapper.updateDocument(document);
        }
        else
        {
            documentMapper.insertDocument(document);
        }

        request.setDocumentId(documentId);
        request.setDocumentName(documentName);
        request.setContent(content);
        return submitTask(KnowledgeTaskType.UPDATE, document, managed, originalRecordIds, request, username);
    }

    public KnowledgeTask reindex(String collectionId, String documentId, Long modelId, String username)
    {
        KnowledgeDocument document = documentMapper.selectDocumentById(documentId);
        boolean managed = document != null;
        List<String> originalRecordIds = new ArrayList<>();
        if (!managed)
        {
            originalRecordIds.add(documentId);
            ChromaRecord record = chromaService.getRecord(collectionId, documentId);
            if (record == null)
            {
                throw new ServiceException("文档不存在");
            }
            document = new KnowledgeDocument();
            document.setDocumentId(documentId);
            document.setCollectionId(collectionId);
            document.setDocumentName(metadata(record, "documentName", documentId));
            document.setSourceType("EXTERNAL");
            document.setContent(record.content());
            document.setChunkSeparator("\n");
            document.setChunkSize(1024);
            document.setChunkOverlap(100);
            document.setStatus(KnowledgeDocumentStatus.PENDING);
            document.setChunkCount(1);
            document.setCreateBy(username);
            documentMapper.insertDocument(document);
        }
        else if (modelId != null)
        {
            document.setEmbeddingModelId(modelId);
            documentMapper.updateDocument(document);
        }
        DocumentImportRequest request = toImportRequest(document);
        request.setModelId(modelId != null ? modelId : document.getEmbeddingModelId());
        DocumentTaskPayload payload = new DocumentTaskPayload();
        payload.setRequest(request);
        payload.setManaged(managed);
        payload.setOriginalRecordIds(originalRecordIds);
        return taskService.createAndSubmit(KnowledgeTaskType.REINDEX, documentId, document.getDocumentName(),
                collectionId, document.getCollectionName(), request.getModelId(), payload, username);
    }

    public boolean delete(String collectionId, String documentId)
    {
        KnowledgeDocument document = documentMapper.selectDocumentById(documentId);
        if (document != null)
        {
            chromaService.deleteByDocumentId(collectionId, documentId);
            documentMapper.deleteDocumentById(documentId);
            deleteTempFile(document.getTempFilePath());
            return true;
        }
        chromaService.deleteByIds(collectionId, List.of(documentId));
        return true;
    }

    private KnowledgeTask submitTask(String taskType, KnowledgeDocument document, boolean managed,
            List<String> originalRecordIds, DocumentImportRequest request, String username)
    {
        DocumentTaskPayload payload = new DocumentTaskPayload();
        payload.setRequest(request);
        payload.setManaged(managed);
        payload.setOriginalRecordIds(originalRecordIds);
        return taskService.createAndSubmit(taskType, document.getDocumentId(), document.getDocumentName(),
                document.getCollectionId(), document.getCollectionName(), request.getModelId(), payload, username);
    }

    private String readContent(DocumentImportRequest request, MultipartFile file)
    {
        if (file != null && !file.isEmpty())
        {
            String name = file.getOriginalFilename();
            if (name == null || !name.toLowerCase().endsWith(".md"))
            {
                throw new ServiceException("仅支持 Markdown（.md）文件");
            }
            try
            {
                return new String(file.getBytes(), StandardCharsets.UTF_8);
            }
            catch (IOException e)
            {
                throw new ServiceException("读取 Markdown 文件失败");
            }
        }
        if (!StringUtils.hasText(request.getContent()))
        {
            throw new ServiceException("Markdown 内容不能为空");
        }
        return request.getContent();
    }

    private String resolveDocumentName(DocumentImportRequest request, MultipartFile file)
    {
        if (StringUtils.hasText(request.getDocumentName()))
        {
            return request.getDocumentName().trim();
        }
        if (file != null && StringUtils.hasText(file.getOriginalFilename()))
        {
            return file.getOriginalFilename();
        }
        return "未命名文档";
    }

    private void applyChunkConfig(KnowledgeDocument document, DocumentImportRequest request)
    {
        document.setChunkSeparator(StringUtils.hasText(request.getSeparator()) ? request.getSeparator() : "\n");
        document.setChunkSize(request.getChunkSize() == null ? 1024 : request.getChunkSize());
        document.setChunkOverlap(request.getChunkOverlap() == null ? 100 : request.getChunkOverlap());
    }

    private DocumentImportRequest toImportRequest(KnowledgeDocument document)
    {
        DocumentImportRequest request = new DocumentImportRequest();
        request.setDocumentId(document.getDocumentId());
        request.setCollectionId(document.getCollectionId());
        request.setCollectionName(document.getCollectionName());
        request.setDocumentName(document.getDocumentName());
        request.setModelId(document.getEmbeddingModelId());
        request.setSeparator(document.getChunkSeparator());
        request.setChunkSize(document.getChunkSize());
        request.setChunkOverlap(document.getChunkOverlap());
        request.setContent(document.getContent());
        return request;
    }

    private String saveTempFile(MultipartFile file)
    {
        if (file == null || file.isEmpty())
        {
            return null;
        }
        try
        {
            Path directory = Path.of(properties.getStoragePath()).toAbsolutePath().normalize();
            Files.createDirectories(directory);
            String name = UUID.randomUUID().toString().replace("-", "") + ".md";
            Path target = directory.resolve(name);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return target.toString();
        }
        catch (IOException e)
        {
            throw new ServiceException("保存 Markdown 暂存文件失败");
        }
    }

    private void deleteTempFile(String path)
    {
        if (!StringUtils.hasText(path))
        {
            return;
        }
        try
        {
            Files.deleteIfExists(Path.of(path));
        }
        catch (IOException ignored)
        {
        }
    }

    private Map<String, List<ChromaRecord>> groupRecords(List<ChromaRecord> records)
    {
        Map<String, List<ChromaRecord>> result = new LinkedHashMap<>();
        for (ChromaRecord record : records)
        {
            String documentId = metadata(record, "documentId", record.id());
            result.computeIfAbsent(documentId, key -> new ArrayList<>()).add(record);
        }
        return result;
    }

    private String metadata(List<ChromaRecord> records, String key, String fallback)
    {
        if (records == null || records.isEmpty())
        {
            return fallback;
        }
        return metadata(records.get(0), key, fallback);
    }

    private String metadata(ChromaRecord record, String key, String fallback)
    {
        if (record == null || record.metadata() == null || record.metadata().get(key) == null)
        {
            return fallback;
        }
        return String.valueOf(record.metadata().get(key));
    }
}
