package com.ruoyi.wechat.domain;

/**
 * 设置会话认识日期。
 *
 * <p>日期使用 {@code yyyy-MM-dd}；传空字符串或 null 表示清除。
 */
public class AcquaintanceDateRequest
{
    private Long sessionId;

    private String acquaintanceDate;

    public Long getSessionId()
    {
        return sessionId;
    }

    public void setSessionId(Long sessionId)
    {
        this.sessionId = sessionId;
    }

    public String getAcquaintanceDate()
    {
        return acquaintanceDate;
    }

    public void setAcquaintanceDate(String acquaintanceDate)
    {
        this.acquaintanceDate = acquaintanceDate;
    }
}
