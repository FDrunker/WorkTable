package com.ruoyi.knowledge.domain;

import java.util.Map;

/**
 * Chroma 文档切片视图。
 */
public record DocumentChunkView(
        String id,
        String content,
        Map<String, Object> metadata)
{
}
