package com.ruoyi.knowledge.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.ruoyi.knowledge.domain.KnowledgeDocument;

public interface KnowledgeDocumentMapper
{
    List<KnowledgeDocument> selectDocumentList(KnowledgeDocument query);

    KnowledgeDocument selectDocumentById(@Param("documentId") String documentId);

    int insertDocument(KnowledgeDocument document);

    int updateDocument(KnowledgeDocument document);

    int deleteDocumentById(@Param("documentId") String documentId);

    int deleteDocumentsByCollection(@Param("collectionId") String collectionId);
}
