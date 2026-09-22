package com.ruoyi.wechat.domain;

/**
 * 提交已预览的文本，并指定聊天记录中代表当前用户的昵称。
 */
public class TextImportCommitRequest
{
    /** 预览接口返回的临时导入标识 */
    private String importId;

    /** 用户在本次文本中选择的自己的昵称 */
    private String selfLabel;

    public String getImportId()
    {
        return importId;
    }

    public void setImportId(String importId)
    {
        this.importId = importId;
    }

    public String getSelfLabel()
    {
        return selfLabel;
    }

    public void setSelfLabel(String selfLabel)
    {
        this.selfLabel = selfLabel;
    }
}
