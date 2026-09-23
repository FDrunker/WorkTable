package com.ruoyi.knowledge.domain;

import java.util.Date;

/**
 * 集合中文档视图。
 */
public record DocumentView(
        String documentId,
        String collectionId,
        String collectionName,
        String documentName,
        String sourceType,
        Integer chunkCount,
        Long embeddingModelId,
        String status,
        boolean managed,
        Date createTime)
{
}
