package com.ruoyi.wechat.domain;

import com.ruoyi.common.core.domain.BaseEntity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 微信会话 sys_wechat_session。
 *
 * <p>「我 / 对方」的标签以逗号分隔的字符串落库，
 * 因为这是一份由用户确认、之后只读回放的映射快照，不值得为它单独建关联表。
 */
public class WeChatSession extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 会话ID */
    private Long sessionId;

    /** 归属用户ID */
    private Long userId;

    /** 微信内部标识 */
    private String chatUsername;

    /** 展示名 */
    private String displayName;

    /** 是否群聊（0否 1是） */
    private String isGroup;

    /** 「我」的微信标识 */
    private String selfUsername;

    /** 「我」的原始标签，逗号分隔 */
    private String selfLabels;

    /** 对方原始标签，逗号分隔 */
    private String targetLabels;

    /** 说话人映射是否已确认（0未确认 1已确认） */
    private String identityConfirmed;

    /** 已归档消息总数 */
    private Integer msgCount;

    /** 未分析消息数 */
    private Integer unanalyzedCount;

    /** 已同步到的时间 */
    private String syncedUntil;

    /** 最后同步时间 */
    private java.util.Date lastSyncTime;

    /** 删除标志（0存在 2删除） */
    private String delFlag;

    public boolean groupChat()
    {
        return "1".equals(isGroup);
    }

    /**
     * 说话人映射是否已确认。
     *
     * <p>刻意不叫 {@code isIdentityConfirmed()}：那会与
     * {@link #getIdentityConfirmed()} 构成本文属性 {@code identityConfirmed} 的
     * 重载 getter，违反 JavaBeans 规范，MyBatis 反射取值时会直接抛
     * {@code ReflectionException: Illegal overloaded getter method}。
     */
    public boolean identityConfirmed()
    {
        return "1".equals(identityConfirmed);
    }

    /**
     * 把逗号分隔的标签串还原成列表。
     */
    public static List<String> splitLabels(String labels)
    {
        if (labels == null || labels.isBlank())
        {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        for (String part : labels.split(","))
        {
            String trimmed = part.trim();
            if (!trimmed.isEmpty() && !result.contains(trimmed))
            {
                result.add(trimmed);
            }
        }
        return result;
    }

    /**
     * 把标签列表序列化为逗号分隔串。
     */
    public static String joinLabels(List<String> labels)
    {
        return labels == null || labels.isEmpty() ? "" : String.join(",", labels);
    }

    /**
     * 兼容从数组直接构造的场景。
     */
    public void setSelfLabelsArray(String[] labels)
    {
        this.selfLabels = labels == null ? "" : String.join(",", Arrays.asList(labels));
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

    public String getChatUsername()
    {
        return chatUsername;
    }

    public void setChatUsername(String chatUsername)
    {
        this.chatUsername = chatUsername;
    }

    public String getDisplayName()
    {
        return displayName;
    }

    public void setDisplayName(String displayName)
    {
        this.displayName = displayName;
    }

    public String getIsGroup()
    {
        return isGroup;
    }

    public void setIsGroup(String isGroup)
    {
        this.isGroup = isGroup;
    }

    public String getSelfUsername()
    {
        return selfUsername;
    }

    public void setSelfUsername(String selfUsername)
    {
        this.selfUsername = selfUsername;
    }

    public String getSelfLabels()
    {
        return selfLabels;
    }

    public void setSelfLabels(String selfLabels)
    {
        this.selfLabels = selfLabels;
    }

    public String getTargetLabels()
    {
        return targetLabels;
    }

    public void setTargetLabels(String targetLabels)
    {
        this.targetLabels = targetLabels;
    }

    public String getIdentityConfirmed()
    {
        return identityConfirmed;
    }

    public void setIdentityConfirmed(String identityConfirmed)
    {
        this.identityConfirmed = identityConfirmed;
    }

    public Integer getMsgCount()
    {
        return msgCount;
    }

    public void setMsgCount(Integer msgCount)
    {
        this.msgCount = msgCount;
    }

    public Integer getUnanalyzedCount()
    {
        return unanalyzedCount;
    }

    public void setUnanalyzedCount(Integer unanalyzedCount)
    {
        this.unanalyzedCount = unanalyzedCount;
    }

    public String getSyncedUntil()
    {
        return syncedUntil;
    }

    public void setSyncedUntil(String syncedUntil)
    {
        this.syncedUntil = syncedUntil;
    }

    public java.util.Date getLastSyncTime()
    {
        return lastSyncTime;
    }

    public void setLastSyncTime(java.util.Date lastSyncTime)
    {
        this.lastSyncTime = lastSyncTime;
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
