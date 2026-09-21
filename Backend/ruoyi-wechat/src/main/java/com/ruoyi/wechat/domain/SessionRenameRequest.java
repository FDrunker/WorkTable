package com.ruoyi.wechat.domain;

/**
 * 会话改名请求 —— 对应聊天界面标题上的「修改会话名称」。
 *
 * <p>只改展示名 {@code displayName}，不动会话标识：会话标识是归档数据的归属依据，
 * 换了名字如果连标识一起换，历史消息就会变成另一个会话的记录。
 */
public class SessionRenameRequest
{
    /** 会话ID */
    private Long sessionId;

    /** 新的会话名称 */
    private String displayName;

    public Long getSessionId()
    {
        return sessionId;
    }

    public void setSessionId(Long sessionId)
    {
        this.sessionId = sessionId;
    }

    public String getDisplayName()
    {
        return displayName;
    }

    public void setDisplayName(String displayName)
    {
        this.displayName = displayName;
    }
}
