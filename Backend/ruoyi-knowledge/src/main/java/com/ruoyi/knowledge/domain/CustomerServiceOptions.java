package com.ruoyi.knowledge.domain;

import java.util.List;

/**
 * 智能客服设置选项。
 */
public record CustomerServiceOptions(
        List<AiModelConfig> chatModels,
        List<AiModelConfig> embeddingModels,
        List<CollectionView> collections)
{
}
