package com.ruoyi.wechat.domain;

/**
 * 触发分析的请求。
 */
public class AnalysisTriggerRequest
{
    /** 会话ID */
    private Long sessionId;

    /** 用户诉求，例如"这句怎么回" */
    private String goal;

    public Long getSessionId()
    {
        return sessionId;
    }

    public void setSessionId(Long sessionId)
    {
        this.sessionId = sessionId;
    }

    public String getGoal()
    {
        return goal;
    }

    public void setGoal(String goal)
    {
        this.goal = goal;
    }
}
