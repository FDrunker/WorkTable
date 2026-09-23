package com.ruoyi.knowledge.domain;

/**
 * Chroma 集合视图。
 */
public record CollectionView(
        String id,
        String name,
        Long documentCount,
        java.util.Map<String, Object> metadata)
{
}
