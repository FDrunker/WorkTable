package com.ruoyi.wechat.domain;

import java.util.List;

/**
 * 昵称映射确认请求 —— 对应界面"哪个昵称是我"这一步。
 *
 * <p>持久化之后这里以 {@code sessionId} 为主键，不再依赖聊天名：
 * 聊天名可能重名或改名，而会话ID是稳定的。
 */
public class IdentityConfirmRequest
{
    /** 会话ID */
    private Long sessionId;

    /** 数据源推断出的「我」的微信标识 */
    private String selfUsername;

    /** 用户确认的「我」的原始标签 */
    private List<String> selfLabels;

    /** 用户确认的对方原始标签 */
    private List<String> targetLabels;

    public Long getSessionId()
    {
        return sessionId;
    }

    public void setSessionId(Long sessionId)
    {
        this.sessionId = sessionId;
    }

    public String getSelfUsername()
    {
        return selfUsername;
    }

    public void setSelfUsername(String selfUsername)
    {
        this.selfUsername = selfUsername;
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
}
