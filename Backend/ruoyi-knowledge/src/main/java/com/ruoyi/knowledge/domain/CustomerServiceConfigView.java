package com.ruoyi.knowledge.domain;

import java.util.Date;
import java.util.List;

/**
 * 智能客服设置视图。
 */
public record CustomerServiceConfigView(
        Long configId,
        Long chatModelId,
        Long embeddingModelId,
        List<String> collectionIds,
        Integer topK,
        String enabled,
        Date updateTime)
{
}
