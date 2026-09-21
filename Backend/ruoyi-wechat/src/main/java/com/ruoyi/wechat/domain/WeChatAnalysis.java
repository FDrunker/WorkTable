package com.ruoyi.wechat.domain;

import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 分析记录 sys_wechat_analysis。
 *
 * <p>{@code promptSnapshot} 是刻意保留的：出了"它为什么会这么答"的问题时，
 * 必须能完整复现当时的输入，包括说话人映射与上下文截断情况。
 */
public class WeChatAnalysis extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 分析ID */
    private Long analysisId;

    /** 会话ID */
    private Long sessionId;

    /** 归属用户ID */
    private Long userId;

    /** 聊天名快照 */
    private String chatName;

    /** 用户诉求 */
    private String goal;

    /** 消息时间范围起 */
    private String startTime;

    /** 消息时间范围止 */
    private String endTime;

    /** 参与分析的最小消息ID */
    private Long messageIdFrom;

    /** 参与分析的最大消息ID */
    private Long messageIdTo;

    /** 参与分析的消息条数 */
    private Integer messageCount;

    /** 说话人映射说明 */
    private String identityDesc;

    /** 本次加载的知识文档 */
    private String refsUsed;

    /** 军师结构化结果 JSON */
    private String resultJson;

    /** prompt 快照 */
    private String promptSnapshot;

    /** 是否命中安全红线（0否 1是） */
    private String crisis;

    /** 安全指引内容 */
    private String crisisMessage;

    /** 实际使用的模型 */
    private String model;

    /** 输入 token 数 */
    private Integer promptTokens;

    /** 输出 token 数 */
    private Integer completionTokens;

    /** 耗时毫秒 */
    private Long durationMs;

    /** 状态（0成功 1失败） */
    private String status;

    /** 失败原因 */
    private String errorMsg;

    /** 删除标志 */
    private String delFlag;

    public Long getAnalysisId()
    {
        return analysisId;
    }

    public void setAnalysisId(Long analysisId)
    {
        this.analysisId = analysisId;
    }

    public Long getSessionId()
    {
        return sessionId;
    }

    public void setSessionId(Long sessionId)
    {
        this.sessionId = sessionId;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getChatName()
    {
        return chatName;
    }

    public void setChatName(String chatName)
    {
        this.chatName = chatName;
    }

    public String getGoal()
    {
        return goal;
    }

    public void setGoal(String goal)
    {
        this.goal = goal;
    }

    public String getStartTime()
    {
        return startTime;
    }

    public void setStartTime(String startTime)
    {
        this.startTime = startTime;
    }

    public String getEndTime()
    {
        return endTime;
    }

    public void setEndTime(String endTime)
    {
        this.endTime = endTime;
    }

    public Long getMessageIdFrom()
    {
        return messageIdFrom;
    }

    public void setMessageIdFrom(Long messageIdFrom)
    {
        this.messageIdFrom = messageIdFrom;
    }

    public Long getMessageIdTo()
    {
        return messageIdTo;
    }

    public void setMessageIdTo(Long messageIdTo)
    {
        this.messageIdTo = messageIdTo;
    }

    public Integer getMessageCount()
    {
        return messageCount;
    }

    public void setMessageCount(Integer messageCount)
    {
        this.messageCount = messageCount;
    }

    public String getIdentityDesc()
    {
        return identityDesc;
    }

    public void setIdentityDesc(String identityDesc)
    {
        this.identityDesc = identityDesc;
    }

    public String getRefsUsed()
    {
        return refsUsed;
    }

    public void setRefsUsed(String refsUsed)
    {
        this.refsUsed = refsUsed;
    }

    public String getResultJson()
    {
        return resultJson;
    }

    public void setResultJson(String resultJson)
    {
        this.resultJson = resultJson;
    }

    public String getPromptSnapshot()
    {
        return promptSnapshot;
    }

    public void setPromptSnapshot(String promptSnapshot)
    {
        this.promptSnapshot = promptSnapshot;
    }

    public String getCrisis()
    {
        return crisis;
    }

    public void setCrisis(String crisis)
    {
        this.crisis = crisis;
    }

    public String getCrisisMessage()
    {
        return crisisMessage;
    }

    public void setCrisisMessage(String crisisMessage)
    {
        this.crisisMessage = crisisMessage;
    }

    public String getModel()
    {
        return model;
    }

    public void setModel(String model)
    {
        this.model = model;
    }

    public Integer getPromptTokens()
    {
        return promptTokens;
    }

    public void setPromptTokens(Integer promptTokens)
    {
        this.promptTokens = promptTokens;
    }

    public Integer getCompletionTokens()
    {
        return completionTokens;
    }

    public void setCompletionTokens(Integer completionTokens)
    {
        this.completionTokens = completionTokens;
    }

    public Long getDurationMs()
    {
        return durationMs;
    }

    public void setDurationMs(Long durationMs)
    {
        this.durationMs = durationMs;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getErrorMsg()
    {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg)
    {
        this.errorMsg = errorMsg;
    }

    public String getDelFlag()
    {
        return delFlag;
    }

    public void setDelFlag(String delFlag)
    {
        this.delFlag = delFlag;
    }
}
