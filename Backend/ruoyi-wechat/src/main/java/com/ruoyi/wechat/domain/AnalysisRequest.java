package com.ruoyi.wechat.domain;

import java.util.List;

/**
 * 分析请求。
 */
public class AnalysisRequest
{
    /** 聊天名，需与数据源给出的名称一致 */
    private String chatName;

    /** 是否群聊 */
    private boolean group;

    /** 起始时间 yyyy-MM-dd，可为空 */
    private String startTime;

    /** 结束时间 yyyy-MM-dd，可为空 */
    private String endTime;

    /** 拉取条数上限 */
    private int limit = 500;

    /** 用户的诉求，例如"这句怎么回"或"判断她还有没有兴趣" */
    private String goal;

    /** 用户确认的"我"的原始标签；为空时尝试自动推断 */
    private List<String> selfLabels;

    /** 用户确认的对方原始标签 */
    private List<String> targetLabels;

    /** 数据源推断的"我"的标识 */
    private String selfUsername;

    public String getChatName()
    {
        return chatName;
    }

    public void setChatName(String chatName)
    {
        this.chatName = chatName;
    }

    public boolean isGroup()
    {
        return group;
    }

    public void setGroup(boolean group)
    {
        this.group = group;
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

    public int getLimit()
    {
        return limit;
    }

    public void setLimit(int limit)
    {
        this.limit = limit;
    }

    public String getGoal()
    {
        return goal;
    }

    public void setGoal(String goal)
    {
        this.goal = goal;
    }

    public List<String> getSelfLabels()
    {
        return selfLabels;
    }

    public void setSelfLabels(List<String> selfLabels)
    {
        this.selfLabels = selfLabels;
    }

    public List<String> getTargetLabels()
    {
        return targetLabels;
    }

    public void setTargetLabels(List<String> targetLabels)
    {
        this.targetLabels = targetLabels;
    }

    public String getSelfUsername()
    {
        return selfUsername;
    }

    public void setSelfUsername(String selfUsername)
    {
        this.selfUsername = selfUsername;
    }
}
