package com.ruoyi.knowledge.domain;

import com.ruoyi.common.core.domain.BaseEntity;

/**
 * AI对话运行配置。
 */
public class CustomerServiceConfig extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long configId;

    private String scene;

    private String title;

    private String welcomeMessage;

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

    public String getScene()
    {
        return scene;
    }

    public void setScene(String scene)
    {
        this.scene = scene;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getWelcomeMessage()
    {
        return welcomeMessage;
    }

    public void setWelcomeMessage(String welcomeMessage)
    {
        this.welcomeMessage = welcomeMessage;
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
