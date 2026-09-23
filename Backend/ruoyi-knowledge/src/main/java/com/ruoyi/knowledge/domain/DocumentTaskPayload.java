package com.ruoyi.knowledge.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档任务请求快照。
 */
public class DocumentTaskPayload
{
    private DocumentImportRequest request;

    private boolean managed;

    private List<String> originalRecordIds = new ArrayList<>();

    public DocumentImportRequest getRequest()
    {
        return request;
    }

    public void setRequest(DocumentImportRequest request)
    {
        this.request = request;
    }

    public boolean isManaged()
    {
        return managed;
    }

    public void setManaged(boolean managed)
    {
        this.managed = managed;
    }

    public List<String> getOriginalRecordIds()
    {
        return originalRecordIds;
    }

    public void setOriginalRecordIds(List<String> originalRecordIds)
    {
        this.originalRecordIds = originalRecordIds;
    }
}
