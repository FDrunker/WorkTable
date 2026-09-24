package com.ruoyi.knowledge.domain;

import java.util.List;

/**
 * AI对话设置保存请求。
 */
public record CustomerServiceConfigRequest(
        Long chatModelId,
        Long embeddingModelId,
        List<String> collectionIds,
        Integer topK,
        String enabled)
{
}
