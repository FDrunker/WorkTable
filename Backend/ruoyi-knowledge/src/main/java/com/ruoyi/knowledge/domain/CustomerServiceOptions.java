package com.ruoyi.knowledge.domain;

import java.util.List;

/**
 * AI对话设置选项。
 */
public record CustomerServiceOptions(
        List<AiModelConfig> chatModels,
        List<AiModelConfig> embeddingModels,
        List<CollectionView> collections)
{
}
