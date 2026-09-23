package com.ruoyi.knowledge.domain;

import java.util.Date;

import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 知识库异步任务。
 */
public class KnowledgeTask extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long taskId;

    private String taskType;

    private String documentId;

    private String documentName;

    private String collectionId;

    private String collectionName;

    private Long modelId;

    private String status;

    private Integer progress;

    private String stage;

    private String message;

    private String errorMessage;

    private String requestPayload;

    private String cancelRequested;

    private Integer retryCount;

    private Date startTime;

    private Date endTime;

    public Long getTaskId()
    {
        return taskId;
    }

    public void setTaskId(Long taskId)
    {
        this.taskId = taskId;
    }

    public String getTaskType()
    {
        return taskType;
    }

    public void setTaskType(String taskType)
    {
        this.taskType = taskType;
    }

    public String getDocumentId()
    {
        return documentId;
    }

    public void setDocumentId(String documentId)
    {
        this.documentId = documentId;
    }

    public String getDocumentName()
    {
        return documentName;
    }

    public void setDocumentName(String documentName)
    {
        this.documentName = documentName;
    }

    public String getCollectionId()
    {
        return collectionId;
    }

    public void setCollectionId(String collectionId)
    {
        this.collectionId = collectionId;
    }

    public String getCollectionName()
    {
        return collectionName;
    }

    public void setCollectionName(String collectionName)
    {
        this.collectionName = collectionName;
    }

    public Long getModelId()
    {
        return modelId;
    }

    public void setModelId(Long modelId)
    {
        this.modelId = modelId;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public Integer getProgress()
    {
        return progress;
    }

    public void setProgress(Integer progress)
    {
        this.progress = progress;
    }

    public String getStage()
    {
        return stage;
    }

    public void setStage(String stage)
    {
        this.stage = stage;
    }

    public String getMessage()
    {
        return message;
    }

    public void setMessage(String message)
    {
        this.message = message;
    }

    public String getErrorMessage()
    {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage)
    {
        this.errorMessage = errorMessage;
    }

    public String getRequestPayload()
    {
        return requestPayload;
    }

    public void setRequestPayload(String requestPayload)
    {
        this.requestPayload = requestPayload;
    }

    public String getCancelRequested()
    {
        return cancelRequested;
    }

    public void setCancelRequested(String cancelRequested)
    {
        this.cancelRequested = cancelRequested;
    }

    public Integer getRetryCount()
    {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount)
    {
        this.retryCount = retryCount;
    }

    public Date getStartTime()
    {
        return startTime;
    }

    public void setStartTime(Date startTime)
    {
        this.startTime = startTime;
    }

    public Date getEndTime()
    {
        return endTime;
    }

    public void setEndTime(Date endTime)
    {
        this.endTime = endTime;
    }
}
