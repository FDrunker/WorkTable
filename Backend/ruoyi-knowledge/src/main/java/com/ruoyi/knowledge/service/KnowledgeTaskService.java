package com.ruoyi.knowledge.service;

import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.domain.DocumentTaskPayload;
import com.ruoyi.knowledge.domain.KnowledgeTask;
import com.ruoyi.knowledge.mapper.KnowledgeTaskMapper;
import com.ruoyi.knowledge.model.KnowledgeTaskStatus;
import com.ruoyi.knowledge.model.KnowledgeTaskType;

/**
 * 异步任务持久化与调度。
 */
@Service
public class KnowledgeTaskService
{
    private final KnowledgeTaskMapper taskMapper;

    private final ObjectProvider<DocumentTaskProcessor> taskProcessor;

    private final ThreadPoolTaskExecutor executor;

    private final ObjectMapper objectMapper;

    public KnowledgeTaskService(KnowledgeTaskMapper taskMapper, ObjectProvider<DocumentTaskProcessor> taskProcessor,
            @Qualifier("knowledgeTaskExecutor") ThreadPoolTaskExecutor executor, ObjectMapper objectMapper)
    {
        this.taskMapper = taskMapper;
        this.taskProcessor = taskProcessor;
        this.executor = executor;
        this.objectMapper = objectMapper;
    }

    public List<KnowledgeTask> list(KnowledgeTask query)
    {
        List<KnowledgeTask> tasks = taskMapper.selectTaskList(query);
        tasks.forEach(task -> task.setRequestPayload(null));
        return tasks;
    }

    public KnowledgeTask get(Long taskId)
    {
        KnowledgeTask task = taskMapper.selectTaskById(taskId);
        if (task == null)
        {
            throw new ServiceException("任务不存在");
        }
        task.setRequestPayload(null);
        return task;
    }

    public KnowledgeTask createAndSubmit(String taskType, String documentId, String documentName,
            String collectionId, String collectionName, Long modelId, DocumentTaskPayload payload, String username)
    {
        KnowledgeTask task = new KnowledgeTask();
        task.setTaskType(taskType);
        task.setDocumentId(documentId);
        task.setDocumentName(documentName);
        task.setCollectionId(collectionId);
        task.setCollectionName(collectionName);
        task.setModelId(modelId);
        task.setStatus(KnowledgeTaskStatus.PENDING);
        task.setProgress(0);
        task.setStage("PENDING");
        task.setMessage("等待执行");
        task.setRetryCount(0);
        task.setCreateBy(username);
        task.setRequestPayload(writePayload(payload));
        taskMapper.insertTask(task);
        submit(task.getTaskId());
        return get(task.getTaskId());
    }

    public boolean requestCancel(Long taskId, String username)
    {
        return taskMapper.requestCancel(taskId, username) > 0;
    }

    public KnowledgeTask retry(Long taskId, String username)
    {
        KnowledgeTask source = taskMapper.selectTaskById(taskId);
        if (source == null)
        {
            throw new ServiceException("任务不存在");
        }
        if (!KnowledgeTaskStatus.FAILED.equals(source.getStatus())
                && !KnowledgeTaskStatus.CANCELLED.equals(source.getStatus()))
        {
            throw new ServiceException("只有失败或已取消的任务可以重试");
        }
        DocumentTaskPayload payload = readPayload(source.getRequestPayload());
        return createAndSubmit(source.getTaskType(), source.getDocumentId(), source.getDocumentName(),
                source.getCollectionId(), source.getCollectionName(), source.getModelId(), payload,
                username + "(retry:" + (source.getRetryCount() == null ? 1 : source.getRetryCount() + 1) + ")");
    }

    public void updateProgress(Long taskId, String status, int progress, String stage, String message)
    {
        KnowledgeTask task = new KnowledgeTask();
        task.setTaskId(taskId);
        task.setStatus(status);
        task.setProgress(progress);
        task.setStage(stage);
        task.setMessage(message);
        task.setStartTime(KnowledgeTaskStatus.RUNNING.equals(status) ? new Date() : null);
        taskMapper.updateTaskProgress(task);
    }

    public void finish(Long taskId, String status, String stage, String message, String errorMessage)
    {
        KnowledgeTask task = new KnowledgeTask();
        task.setTaskId(taskId);
        task.setStatus(status);
        task.setStage(stage);
        task.setMessage(message);
        task.setErrorMessage(errorMessage);
        task.setProgress(KnowledgeTaskStatus.SUCCESS.equals(status) ? 100 : null);
        taskMapper.finishTask(task);
    }

    public boolean cancelRequested(Long taskId)
    {
        KnowledgeTask task = taskMapper.selectTaskById(taskId);
        return task != null && "1".equals(task.getCancelRequested());
    }

    public void recoverInterruptedTasks()
    {
        taskMapper.recoverInterruptedTasks();
    }

    private void submit(Long taskId)
    {
        executor.execute(() -> taskProcessor.getObject().process(taskId));
    }

    private String writePayload(DocumentTaskPayload payload)
    {
        try
        {
            return objectMapper.writeValueAsString(payload);
        }
        catch (JsonProcessingException e)
        {
            throw new ServiceException("任务请求序列化失败");
        }
    }

    private DocumentTaskPayload readPayload(String payload)
    {
        try
        {
            return objectMapper.readValue(payload, DocumentTaskPayload.class);
        }
        catch (Exception e)
        {
            throw new ServiceException("任务请求解析失败，无法重试");
        }
    }
}
