package com.ruoyi.knowledge.domain;

import java.util.Map;

/**
 * Chroma 相似度检索结果。
 */
public record ChromaSearchResult(
        String collectionId,
        String id,
        String content,
        Map<String, Object> metadata,
        Double distance)
{
}
