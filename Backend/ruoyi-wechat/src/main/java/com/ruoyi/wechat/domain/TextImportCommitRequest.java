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

    /** 可选的已有会话ID；为空时按对方昵称新建会话 */
    private Long sessionId;

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

    public Long getSessionId()
    {
        return sessionId;
    }

    public void setSessionId(Long sessionId)
    {
        this.sessionId = sessionId;
    }
}
