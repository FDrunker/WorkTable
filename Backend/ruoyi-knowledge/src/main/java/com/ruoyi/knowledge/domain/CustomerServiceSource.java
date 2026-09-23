package com.ruoyi.knowledge.domain;

/**
 * 智能客服回答引用的知识片段。
 */
public record CustomerServiceSource(
        String id,
        String collectionId,
        String collectionName,
        String documentId,
        String documentName,
        Integer chunkIndex,
        String content,
        Double distance)
{
}
