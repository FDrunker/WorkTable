package com.ruoyi.knowledge.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 文档导入或更新请求。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DocumentImportRequest
{
    private String documentId;

    private String collectionId;

    private String collectionName;

    private String documentName;

    private Long modelId;

    private Integer chunkSize = 1024;

    private Integer chunkOverlap = 100;

    private String content;

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

    public Long getModelId()
    {
        return modelId;
    }

    public void setModelId(Long modelId)
    {
        this.modelId = modelId;
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

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }
}
