package com.ruoyi.knowledge.domain;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * AI 模型配置。
 */
public class AiModelConfig extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long modelId;

    private String modelName;

    private String provider;

    private String modelType;

    private String modelCode;

    private String baseUrl;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String apiKey;

    private String apiKeyCipher;

    private String apiKeyMasked;

    private String enabled;

    private String isDefault;

    private String extraConfig;

    private String lastTestStatus;

    private String lastTestMessage;

    private Date lastTestTime;

    public Long getModelId()
    {
        return modelId;
    }

    public void setModelId(Long modelId)
    {
        this.modelId = modelId;
    }

    public String getModelName()
    {
        return modelName;
    }

    public void setModelName(String modelName)
    {
        this.modelName = modelName;
    }

    public String getProvider()
    {
        return provider;
    }

    public void setProvider(String provider)
    {
        this.provider = provider;
    }

    public String getModelType()
    {
        return modelType;
    }

    public void setModelType(String modelType)
    {
        this.modelType = modelType;
    }

    public String getModelCode()
    {
        return modelCode;
    }

    public void setModelCode(String modelCode)
    {
        this.modelCode = modelCode;
    }

    public String getBaseUrl()
    {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl)
    {
        this.baseUrl = baseUrl;
    }

    public String getApiKey()
    {
        return apiKey;
    }

    public void setApiKey(String apiKey)
    {
        this.apiKey = apiKey;
    }

    public String getApiKeyCipher()
    {
        return apiKeyCipher;
    }

    public void setApiKeyCipher(String apiKeyCipher)
    {
        this.apiKeyCipher = apiKeyCipher;
    }

    public String getApiKeyMasked()
    {
        return apiKeyMasked;
    }

    public void setApiKeyMasked(String apiKeyMasked)
    {
        this.apiKeyMasked = apiKeyMasked;
    }

    public String getEnabled()
    {
        return enabled;
    }

    public void setEnabled(String enabled)
    {
        this.enabled = enabled;
    }

    public String getIsDefault()
    {
        return isDefault;
    }

    public void setIsDefault(String isDefault)
    {
        this.isDefault = isDefault;
    }

    public String getExtraConfig()
    {
        return extraConfig;
    }

    public void setExtraConfig(String extraConfig)
    {
        this.extraConfig = extraConfig;
    }

    public String getLastTestStatus()
    {
        return lastTestStatus;
    }

    public void setLastTestStatus(String lastTestStatus)
    {
        this.lastTestStatus = lastTestStatus;
    }

    public String getLastTestMessage()
    {
        return lastTestMessage;
    }

    public void setLastTestMessage(String lastTestMessage)
    {
        this.lastTestMessage = lastTestMessage;
    }

    public Date getLastTestTime()
    {
        return lastTestTime;
    }

    public void setLastTestTime(Date lastTestTime)
    {
        this.lastTestTime = lastTestTime;
    }
}
