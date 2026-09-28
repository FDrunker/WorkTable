package com.ruoyi.knowledge.domain;

import java.util.Date;

import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 公开 AI 对话消息。
 */
public class AiChatMessage extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long messageId;

    private String conversationId;

    private String role;

    private String content;

    private String intent;

    private Double intentConfidence;

    private String needsClarification;

    private String sourcesJson;

    private String category;

    private String priority;

    private String ticketNo;

    private Date createTime;

    public Long getMessageId()
    {
        return messageId;
    }

    public void setMessageId(Long messageId)
    {
        this.messageId = messageId;
    }

    public String getConversationId()
    {
        return conversationId;
    }

    public void setConversationId(String conversationId)
    {
        this.conversationId = conversationId;
    }

    public String getRole()
    {
        return role;
    }

    public void setRole(String role)
    {
        this.role = role;
    }

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

    public String getIntent()
    {
        return intent;
    }

    public void setIntent(String intent)
    {
        this.intent = intent;
    }

    public Double getIntentConfidence()
    {
        return intentConfidence;
    }

    public void setIntentConfidence(Double intentConfidence)
    {
        this.intentConfidence = intentConfidence;
    }

    public String getNeedsClarification()
    {
        return needsClarification;
    }

    public void setNeedsClarification(String needsClarification)
    {
        this.needsClarification = needsClarification;
    }

    public String getSourcesJson()
    {
        return sourcesJson;
    }

    public void setSourcesJson(String sourcesJson)
    {
        this.sourcesJson = sourcesJson;
    }

    public String getCategory()
    {
        return category;
    }

    public void setCategory(String category)
    {
        this.category = category;
    }

    public String getPriority()
    {
        return priority;
    }

    public void setPriority(String priority)
    {
        this.priority = priority;
    }

    public String getTicketNo()
    {
        return ticketNo;
    }

    public void setTicketNo(String ticketNo)
    {
        this.ticketNo = ticketNo;
    }

    @Override
    public Date getCreateTime()
    {
        return createTime;
    }

    @Override
    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }
}
