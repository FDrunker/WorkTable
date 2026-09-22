package com.ruoyi.wechat.domain;

import java.math.BigDecimal;
import java.util.Date;

import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 微信消息 sys_wechat_message。
 *
 * <p>落库的是<b>清洗后</b>的内容：降噪、脱敏、角色归一化都已完成。
 * 原始标签 {@code senderRaw} 同时保留，作为"说话人映射是否正确"的可核对证据。
 *
 * <p>去重依赖唯一索引 {@code (session_id, msg_time, sender_raw, content_md5)}，
 * 因此重复同步同一时间段是幂等的 —— 这一点对"每次打开会话都同步一次"的交互至关重要。
 */
public class WeChatMessage extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 消息ID */
    private Long messageId;

    /** 会话ID */
    private Long sessionId;

    /** 归属用户ID */
    private Long userId;

    /** 消息时间文本 */
    private String msgTime;

    /** 消息日期 */
    private String msgDate;

    /** 原始发送者标签 */
    private String senderRaw;

    /** 归一化角色 SELF/TARGET/OTHER/UNKNOWN */
    private String senderRole;

    /** 清洗后内容 */
    private String content;

    /** 内容摘要 */
    private String contentMd5;

    /** 消息种类 TEXT/QUOTE/MEDIA/VOICE/STICKER/CALL/OTHER */
    private String msgKind;

    /** 是否已参与过分析（0未分析 1已分析） */
    private String analyzed;

    /** 首次参与分析的时间 */
    private Date analyzedTime;

    /** 首次参与的分析ID */
    private Long analysisId;

    /** 对方消息情绪标签 */
    private String emotionTag;

    /** 情绪标签百分比 */
    private BigDecimal emotionPercent;

    /** 对方消息意图标签 */
    private String intentTag;

    /** 意图标签百分比 */
    private BigDecimal intentPercent;

    /** 我方回答评级 */
    private String replyRating;

    /** 我方回答得分 */
    private BigDecimal replyScore;

    /** 删除标志 */
    private String delFlag;

    public Long getMessageId()
    {
        return messageId;
    }

    public void setMessageId(Long messageId)
    {
        this.messageId = messageId;
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

    public String getMsgTime()
    {
        return msgTime;
    }

    public void setMsgTime(String msgTime)
    {
        this.msgTime = msgTime;
    }

    public String getMsgDate()
    {
        return msgDate;
    }

    public void setMsgDate(String msgDate)
    {
        this.msgDate = msgDate;
    }

    public String getSenderRaw()
    {
        return senderRaw;
    }

    public void setSenderRaw(String senderRaw)
    {
        this.senderRaw = senderRaw;
    }

    public String getSenderRole()
    {
        return senderRole;
    }

    public void setSenderRole(String senderRole)
    {
        this.senderRole = senderRole;
    }

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

    public String getContentMd5()
    {
        return contentMd5;
    }

    public void setContentMd5(String contentMd5)
    {
        this.contentMd5 = contentMd5;
    }

    public String getMsgKind()
    {
        return msgKind;
    }

    public void setMsgKind(String msgKind)
    {
        this.msgKind = msgKind;
    }

    public String getAnalyzed()
    {
        return analyzed;
    }

    public void setAnalyzed(String analyzed)
    {
        this.analyzed = analyzed;
    }

    public Date getAnalyzedTime()
    {
        return analyzedTime;
    }

    public void setAnalyzedTime(Date analyzedTime)
    {
        this.analyzedTime = analyzedTime;
    }

    public Long getAnalysisId()
    {
        return analysisId;
    }

    public void setAnalysisId(Long analysisId)
    {
        this.analysisId = analysisId;
    }

    public String getEmotionTag()
    {
        return emotionTag;
    }

    public void setEmotionTag(String emotionTag)
    {
        this.emotionTag = emotionTag;
    }

    public BigDecimal getEmotionPercent()
    {
        return emotionPercent;
    }

    public void setEmotionPercent(BigDecimal emotionPercent)
    {
        this.emotionPercent = emotionPercent;
    }

    public String getIntentTag()
    {
        return intentTag;
    }

    public void setIntentTag(String intentTag)
    {
        this.intentTag = intentTag;
    }

    public BigDecimal getIntentPercent()
    {
        return intentPercent;
    }

    public void setIntentPercent(BigDecimal intentPercent)
    {
        this.intentPercent = intentPercent;
    }

    public String getReplyRating()
    {
        return replyRating;
    }

    public void setReplyRating(String replyRating)
    {
        this.replyRating = replyRating;
    }

    public BigDecimal getReplyScore()
    {
        return replyScore;
    }

    public void setReplyScore(BigDecimal replyScore)
    {
        this.replyScore = replyScore;
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
