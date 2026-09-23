package com.ruoyi.knowledge.domain;

import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 智能客服运行配置。
 */
public class CustomerServiceConfig extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long configId;

    private Long chatModelId;

    private Long embeddingModelId;

    private String collectionIdsJson;

    private Integer topK;

    private String enabled;

    public Long getConfigId()
    {
        return configId;
    }

    public void setConfigId(Long configId)
    {
        this.configId = configId;
    }

    public Long getChatModelId()
    {
        return chatModelId;
    }

    public void setChatModelId(Long chatModelId)
    {
        this.chatModelId = chatModelId;
    }

    public Long getEmbeddingModelId()
    {
        return embeddingModelId;
    }

    public void setEmbeddingModelId(Long embeddingModelId)
    {
        this.embeddingModelId = embeddingModelId;
    }

    public String getCollectionIdsJson()
    {
        return collectionIdsJson;
    }

    public void setCollectionIdsJson(String collectionIdsJson)
    {
        this.collectionIdsJson = collectionIdsJson;
    }

    public Integer getTopK()
    {
        return topK;
    }

    public void setTopK(Integer topK)
    {
        this.topK = topK;
    }

    public String getEnabled()
    {
        return enabled;
    }

    public void setEnabled(String enabled)
    {
        this.enabled = enabled;
    }
}
