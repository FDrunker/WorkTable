package com.ruoyi.knowledge.domain;

import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 知识库逻辑文档。
 */
public class KnowledgeDocument extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private String documentId;

    private String collectionId;

    private String collectionName;

    private String documentName;

    private String sourceType;

    /**
     * 仅用于查看或编辑时临时组装，不持久化到数据库。
     */
    private String content;

    private String tempFilePath;

    private Integer chunkSize;

    private Integer chunkOverlap;

    private Long embeddingModelId;

    private String status;

    private Integer chunkCount;

    public String getDocumentId()
    {
        return documentId;
    }

    public void setDocumentId(String documentId)
    {
        this.documentId = documentId;
    }

    public String getCollectionId()
    {
        return collectionId;
    }

    public void setCollectionId(String collectionId)
    {
        this.collectionId = collectionId;
    }

    public String getCollectionName()
    {
        return collectionName;
    }

    public void setCollectionName(String collectionName)
    {
        this.collectionName = collectionName;
    }

    public String getDocumentName()
    {
        return documentName;
    }

    public void setDocumentName(String documentName)
    {
        this.documentName = documentName;
    }

    public String getSourceType()
    {
        return sourceType;
    }

    public void setSourceType(String sourceType)
    {
        this.sourceType = sourceType;
    }

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

    public String getTempFilePath()
    {
        return tempFilePath;
    }

    public void setTempFilePath(String tempFilePath)
    {
        this.tempFilePath = tempFilePath;
    }

    public Integer getChunkSize()
    {
        return chunkSize;
    }

    public void setChunkSize(Integer chunkSize)
    {
        this.chunkSize = chunkSize;
    }

    public Integer getChunkOverlap()
    {
        return chunkOverlap;
    }

    public void setChunkOverlap(Integer chunkOverlap)
    {
        this.chunkOverlap = chunkOverlap;
    }

    public Long getEmbeddingModelId()
    {
        return embeddingModelId;
    }

    public void setEmbeddingModelId(Long embeddingModelId)
    {
        this.embeddingModelId = embeddingModelId;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public Integer getChunkCount()
    {
        return chunkCount;
    }

    public void setChunkCount(Integer chunkCount)
    {
        this.chunkCount = chunkCount;
    }
}
