package com.ruoyi.knowledge.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.ai.chroma.vectorstore.ChromaApi.AddEmbeddingsRequest;
import org.springframework.ai.chroma.vectorstore.ChromaApi.DeleteEmbeddingsRequest;
import org.springframework.ai.chroma.vectorstore.ChromaApi.GetEmbeddingResponse;
import org.springframework.stereotype.Service;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.knowledge.config.KnowledgeProperties;
import com.ruoyi.knowledge.domain.ChromaRecord;
import com.ruoyi.knowledge.domain.CollectionView;

/**
 * 基于 Spring AI ChromaApi 的集合访问层。
 */
@Service
public class ChromaCollectionService
{
    private static final int PAGE_SIZE = 500;

    private static final int MAX_RECORDS = 10000;

    private final ChromaApi chromaApi;

    private final KnowledgeProperties properties;

    public ChromaCollectionService(ChromaApi chromaApi, KnowledgeProperties properties)
    {
        this.chromaApi = chromaApi;
        this.properties = properties;
    }

    public List<CollectionView> listCollections(String name)
    {
        List<ChromaApi.Collection> collections = chromaApi.listCollections(tenant(), database());
        if (collections == null)
        {
            return Collections.emptyList();
        }
        List<CollectionView> result = new ArrayList<>();
        for (ChromaApi.Collection collection : collections)
        {
            if (StringUtils.isNotBlank(name)
                    && (collection.name() == null || !collection.name().toLowerCase().contains(name.toLowerCase())))
            {
                continue;
            }
            Long count = chromaApi.countEmbeddings(tenant(), database(), collection.id());
            result.add(new CollectionView(collection.id(), collection.name(),
                    count == null ? 0L : count,
                    collection.metadata() == null ? Collections.emptyMap() : collection.metadata()));
        }
        return result;
    }

    public CollectionView createCollection(String name)
    {
        if (StringUtils.isBlank(name))
        {
            throw new ServiceException("集合名称不能为空");
        }
        String trimmedName = name.trim();
        boolean exists = listCollections(null).stream()
                .anyMatch(collection -> trimmedName.equals(collection.name()));
        if (exists)
        {
            throw new ServiceException("集合名称已存在");
        }
        ChromaApi.Collection collection = chromaApi.createCollection(tenant(), database(),
                new ChromaApi.CreateCollectionRequest(trimmedName));
        if (collection == null)
        {
            throw new ServiceException("创建集合失败");
        }
        return new CollectionView(collection.id(), collection.name(), 0L,
                collection.metadata() == null ? Collections.emptyMap() : collection.metadata());
    }

    public void deleteCollection(String collectionId)
    {
        if (StringUtils.isBlank(collectionId))
        {
            throw new ServiceException("集合ID不能为空");
        }
        chromaApi.deleteCollection(tenant(), database(), collectionId);
    }

    public List<ChromaRecord> listRecords(String collectionId)
    {
        List<ChromaRecord> records = new ArrayList<>();
        int offset = 0;
        while (offset < MAX_RECORDS)
        {
            GetEmbeddingResponse response = chromaApi.getEmbeddings(tenant(), database(), collectionId,
                    new ChromaApi.GetEmbeddingsRequest(null, null, PAGE_SIZE, offset));
            if (response == null || response.ids() == null || response.ids().isEmpty())
            {
                break;
            }
            for (int i = 0; i < response.ids().size(); i++)
            {
                String content = response.documents() != null && response.documents().size() > i
                        ? response.documents().get(i) : "";
                Map<String, Object> metadata = response.metadata() != null && response.metadata().size() > i
                        && response.metadata().get(i) != null
                                ? new LinkedHashMap<>(response.metadata().get(i))
                                : new LinkedHashMap<>();
                records.add(new ChromaRecord(response.ids().get(i), content, metadata));
            }
            offset += response.ids().size();
            if (response.ids().size() < PAGE_SIZE)
            {
                break;
            }
        }
        return records;
    }

    public ChromaRecord getRecord(String collectionId, String recordId)
    {
        GetEmbeddingResponse response = chromaApi.getEmbeddings(tenant(), database(), collectionId,
                new ChromaApi.GetEmbeddingsRequest(List.of(recordId), null, 1, 0));
        if (response == null || response.ids() == null || response.ids().isEmpty())
        {
            return null;
        }
        String content = response.documents() == null || response.documents().isEmpty()
                ? "" : response.documents().get(0);
        Map<String, Object> metadata = response.metadata() == null || response.metadata().isEmpty()
                || response.metadata().get(0) == null
                        ? new LinkedHashMap<>() : new LinkedHashMap<>(response.metadata().get(0));
        return new ChromaRecord(response.ids().get(0), content, metadata);
    }

    public void upsert(List<String> ids, List<float[]> embeddings, List<Map<String, Object>> metadata,
            List<String> documents, String collectionId)
    {
        if (ids.isEmpty())
        {
            return;
        }
        if (StringUtils.isBlank(collectionId))
        {
            throw new ServiceException("集合ID不能为空");
        }
        chromaApi.upsertEmbeddings(tenant(), database(), collectionId,
                new AddEmbeddingsRequest(ids, embeddings, metadata, documents));
    }

    public void deleteByDocumentId(String collectionId, String documentId)
    {
        chromaApi.deleteEmbeddings(tenant(), database(), collectionId,
                new DeleteEmbeddingsRequest(null, Map.of("documentId", documentId)));
    }

    public void deleteByIds(String collectionId, List<String> ids)
    {
        if (ids != null && !ids.isEmpty())
        {
            chromaApi.deleteEmbeddings(tenant(), database(), collectionId, new DeleteEmbeddingsRequest(ids));
        }
    }

    private String tenant()
    {
        return properties.getChroma().getTenant();
    }

    private String database()
    {
        return properties.getChroma().getDatabase();
    }
}
