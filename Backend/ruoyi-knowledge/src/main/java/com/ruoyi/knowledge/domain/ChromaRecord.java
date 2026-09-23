package com.ruoyi.knowledge.domain;

import java.util.Map;

/**
 * Chroma 原始向量记录。
 */
public record ChromaRecord(
        String id,
        String content,
        Map<String, Object> metadata)
{
}
