package com.ruoyi.knowledge.service;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.stereotype.Component;

import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.knowledge.domain.AiModelConfig;
import com.ruoyi.knowledge.model.ModelProvider;

/**
 * 根据数据库配置动态创建 Spring AI 模型客户端。
 */
@Component
public class AiModelClientFactory
{
    public EmbeddingModel createEmbeddingModel(AiModelConfig config)
    {
        ModelProvider provider = ModelProvider.fromCode(config.getProvider());
        return switch (provider)
        {
            case OPENAI -> OpenAiEmbeddingModel.builder()
                    .options(OpenAiEmbeddingOptions.builder()
                            .baseUrl(config.getBaseUrl())
                            .apiKey(config.getApiKey())
                            .model(config.getModelCode())
                            .build())
                    .build();
            case OLLAMA -> OllamaEmbeddingModel.builder()
                    .ollamaApi(ollamaApi(config.getBaseUrl()))
                    .options(OllamaEmbeddingOptions.builder()
                            .model(config.getModelCode())
                            .build())
                    .build();
            default -> throw new IllegalArgumentException(provider.getDisplayName() + " 不支持 embedding");
        };
    }

    public ChatModel createChatModel(AiModelConfig config)
    {
        ModelProvider provider = ModelProvider.fromCode(config.getProvider());
        return switch (provider)
        {
            case OPENAI -> OpenAiChatModel.builder()
                    .options(OpenAiChatOptions.builder()
                            .baseUrl(config.getBaseUrl())
                            .apiKey(config.getApiKey())
                            .model(config.getModelCode())
                            .build())
                    .build();
            case DEEPSEEK -> DeepSeekChatModel.builder()
                    .deepSeekApi(DeepSeekApi.builder()
                            .baseUrl(config.getBaseUrl())
                            .apiKey(config.getApiKey())
                            .build())
                    .options(DeepSeekChatOptions.builder()
                            .model(config.getModelCode())
                            .build())
                    .build();
            case OLLAMA -> OllamaChatModel.builder()
                    .ollamaApi(ollamaApi(config.getBaseUrl()))
                    .options(OllamaChatOptions.builder()
                            .model(config.getModelCode())
                            .build())
                    .build();
            default -> throw new IllegalArgumentException(provider.getDisplayName() + " 不支持 chat");
        };
    }

    private OllamaApi ollamaApi(String baseUrl)
    {
        return OllamaApi.builder()
                .baseUrl(StringUtils.isBlank(baseUrl) ? ModelProvider.OLLAMA.getDefaultBaseUrl() : baseUrl)
                .build();
    }
}
