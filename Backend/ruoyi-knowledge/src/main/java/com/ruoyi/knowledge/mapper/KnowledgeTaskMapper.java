package com.ruoyi.knowledge.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.ruoyi.knowledge.domain.KnowledgeTask;

public interface KnowledgeTaskMapper
{
    List<KnowledgeTask> selectTaskList(KnowledgeTask query);

    KnowledgeTask selectTaskById(@Param("taskId") Long taskId);

    int insertTask(KnowledgeTask task);

    int updateTaskProgress(KnowledgeTask task);

    int requestCancel(@Param("taskId") Long taskId, @Param("updateBy") String updateBy);

    int finishTask(KnowledgeTask task);

    int recoverInterruptedTasks();
}
