package com.ruoyi.knowledge.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.knowledge.domain.AiModelConfig;
import com.ruoyi.knowledge.mapper.AiModelConfigMapper;
import com.ruoyi.knowledge.model.ModelProvider;
import com.ruoyi.knowledge.model.ModelType;

/**
 * AI 模型配置管理。
 */
@Service
public class AiModelService
{
    private final AiModelConfigMapper modelMapper;

    private final ApiKeyCryptoService cryptoService;

    private final AiModelClientFactory clientFactory;

    public AiModelService(AiModelConfigMapper modelMapper, ApiKeyCryptoService cryptoService,
            AiModelClientFactory clientFactory)
    {
        this.modelMapper = modelMapper;
        this.cryptoService = cryptoService;
        this.clientFactory = clientFactory;
    }

    public List<AiModelConfig> list(AiModelConfig query)
    {
        List<AiModelConfig> models = modelMapper.selectModelList(query);
        models.forEach(this::mask);
        return models;
    }

    public List<AiModelConfig> options(String modelType)
    {
        if (StringUtils.isNotBlank(modelType))
        {
            ModelType.fromCode(modelType);
        }
        List<AiModelConfig> models = modelMapper.selectEnabledModels(modelType);
        models.forEach(this::mask);
        return models;
    }

    public AiModelConfig get(Long modelId)
    {
        AiModelConfig model = requireModel(modelId);
        mask(model);
        return model;
    }

    public AiModelConfig getRuntimeModel(Long modelId)
    {
        if (modelId == null)
        {
            return null;
        }
        AiModelConfig model = requireModel(modelId);
        if (!"1".equals(model.getEnabled()))
        {
            throw new ServiceException("所选模型已停用");
        }
        model.setApiKey(cryptoService.decrypt(model.getApiKeyCipher()));
        return model;
    }

    @Transactional
    public AiModelConfig create(AiModelConfig model, String username)
    {
        validate(model, true);
        prepareModel(model, username, true);
        if ("1".equals(model.getIsDefault()))
        {
            modelMapper.clearDefault(model.getModelType(), null);
        }
        modelMapper.insertModel(model);
        return get(model.getModelId());
    }

    @Transactional
    public AiModelConfig update(AiModelConfig model, String username)
    {
        AiModelConfig existing = requireModel(model.getModelId());
        validate(model, false);
        if (StringUtils.isBlank(model.getApiKey()))
        {
            model.setApiKeyCipher(existing.getApiKeyCipher());
        }
        else
        {
            model.setApiKeyCipher(cryptoService.encrypt(model.getApiKey()));
        }
        if (StringUtils.isBlank(model.getBaseUrl()))
        {
            model.setBaseUrl(ModelProvider.fromCode(model.getProvider()).getDefaultBaseUrl());
        }
        model.setUpdateBy(username);
        if ("1".equals(model.getIsDefault()))
        {
            modelMapper.clearDefault(model.getModelType(), model.getModelId());
        }
        modelMapper.updateModel(model);
        return get(model.getModelId());
    }

    @Transactional
    public int delete(Long[] modelIds)
    {
        return modelMapper.deleteModels(modelIds);
    }

    public AiModelConfig test(Long modelId)
    {
        AiModelConfig model = getRuntimeModel(modelId);
        try
        {
            if (ModelType.EMBEDDING.getCode().equals(model.getModelType()))
            {
                EmbeddingModel embeddingModel = clientFactory.createEmbeddingModel(model);
                float[] vector = embeddingModel.embed("连通性测试");
                if (vector == null || vector.length == 0)
                {
                    throw new IllegalStateException("embedding 服务未返回向量");
                }
                model.setLastTestMessage("连通成功，向量维度 " + vector.length);
            }
            else if (ModelType.CHAT.getCode().equals(model.getModelType()))
            {
                ChatModel chatModel = clientFactory.createChatModel(model);
                String content = chatModel.call(new Prompt("请只回复 OK")).getResult().getOutput().getText();
                if (StringUtils.isBlank(content))
                {
                    throw new IllegalStateException("chat 服务未返回内容");
                }
                model.setLastTestMessage("连通成功");
            }
            else
            {
                testTypeSafe(model);
            }
            model.setLastTestStatus("SUCCESS");
        }
        catch (Exception e)
        {
            model.setLastTestStatus("FAILED");
            model.setLastTestMessage(trimMessage(e.getMessage()));
        }
        modelMapper.updateTestResult(model);
        return get(modelId);
    }

    public List<Map<String, Object>> providerCatalog()
    {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ModelProvider provider : ModelProvider.values())
        {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", provider.getCode());
            item.put("name", provider.getDisplayName());
            item.put("defaultBaseUrl", provider.getDefaultBaseUrl());
            item.put("modelTypes", provider.getModelTypes());
            result.add(item);
        }
        return result;
    }

    private void validate(AiModelConfig model, boolean create)
    {
        if (model == null)
        {
            throw new ServiceException("模型配置不能为空");
        }
        if (StringUtils.isBlank(model.getModelName()) || StringUtils.isBlank(model.getProvider())
                || StringUtils.isBlank(model.getModelType()) || StringUtils.isBlank(model.getModelCode()))
        {
            throw new ServiceException("模型名称、提供商、类型和模型代码不能为空");
        }
        ModelProvider provider = ModelProvider.fromCode(model.getProvider());
        ModelType.fromCode(model.getModelType());
        if (!provider.supports(model.getModelType()))
        {
            throw new ServiceException(provider.getDisplayName() + " 不支持该模型类型");
        }
        if (create && StringUtils.isBlank(model.getApiKey()))
        {
            throw new ServiceException("API Key 不能为空");
        }
    }

    private void prepareModel(AiModelConfig model, String username, boolean create)
    {
        model.setProvider(model.getProvider().toLowerCase());
        model.setModelType(model.getModelType().toLowerCase());
        model.setEnabled(defaultValue(model.getEnabled(), "1"));
        model.setIsDefault(defaultValue(model.getIsDefault(), "0"));
        model.setBaseUrl(StringUtils.isBlank(model.getBaseUrl())
                ? ModelProvider.fromCode(model.getProvider()).getDefaultBaseUrl()
                : model.getBaseUrl().trim());
        model.setApiKeyCipher(cryptoService.encrypt(model.getApiKey()));
        model.setLastTestStatus("UNTESTED");
        model.setLastTestMessage("");
        if (create)
        {
            model.setCreateBy(username);
        }
        model.setUpdateBy(username);
    }

    private void testTypeSafe(AiModelConfig model)
    {
        String baseUrl = model.getBaseUrl().replaceAll("/+$", "");
        String uri = baseUrl.endsWith("/v1") ? baseUrl + "/models" : baseUrl + "/v1/models";
        String content = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + model.getApiKey())
                .build()
                .get()
                .uri(uri)
                .retrieve()
                .body(String.class);
        if (StringUtils.isBlank(content))
        {
            throw new IllegalStateException("TypeSafe 服务未返回模型列表");
        }
        model.setLastTestMessage("连通成功");
    }

    private AiModelConfig requireModel(Long modelId)
    {
        if (modelId == null)
        {
            throw new ServiceException("模型ID不能为空");
        }
        AiModelConfig model = modelMapper.selectModelById(modelId);
        if (model == null)
        {
            throw new ServiceException("模型配置不存在");
        }
        return model;
    }

    private void mask(AiModelConfig model)
    {
        String plainText = null;
        if (StringUtils.isNotBlank(model.getApiKeyCipher()))
        {
            try
            {
                plainText = cryptoService.decrypt(model.getApiKeyCipher());
            }
            catch (Exception ignored)
            {
                plainText = null;
            }
        }
        model.setApiKeyMasked(cryptoService.mask(plainText));
        model.setApiKey(null);
        model.setApiKeyCipher(null);
    }

    private String defaultValue(String value, String fallback)
    {
        return StringUtils.isBlank(value) ? fallback : value;
    }

    private String trimMessage(String message)
    {
        if (StringUtils.isBlank(message))
        {
            return "连通失败";
        }
        return message.length() > 900 ? message.substring(0, 900) : message;
    }
}
