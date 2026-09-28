package com.ruoyi.knowledge.domain;

import java.util.Date;

import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 智能客服复杂问题人工处理工单。
 */
public class CustomerServiceTicket extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long ticketId;

    private String ticketNo;

    private String conversationId;

    private String visitorId;

    private String issueType;

    private String question;

    private String category;

    private String classificationReason;

    private String priority;

    private String status;

    private String preanalysisSummary;

    private String preanalysisSolution;

    private String suggestedReply;

    private String handlerReply;

    private String handleNote;

    private String handledBy;

    private Date handleTime;

    public Long getTicketId()
    {
        return ticketId;
    }

    public void setTicketId(Long ticketId)
    {
        this.ticketId = ticketId;
    }

    public String getTicketNo()
    {
        return ticketNo;
    }

    public void setTicketNo(String ticketNo)
    {
        this.ticketNo = ticketNo;
    }

    public String getConversationId()
    {
        return conversationId;
    }

    public void setConversationId(String conversationId)
    {
        this.conversationId = conversationId;
    }

    public String getVisitorId()
    {
        return visitorId;
    }

    public void setVisitorId(String visitorId)
    {
        this.visitorId = visitorId;
    }

    public String getIssueType()
    {
        return issueType;
    }

    public void setIssueType(String issueType)
    {
        this.issueType = issueType;
    }

    public String getQuestion()
    {
        return question;
    }

    public void setQuestion(String question)
    {
        this.question = question;
    }

    public String getCategory()
    {
        return category;
    }

    public void setCategory(String category)
    {
        this.category = category;
    }

    public String getClassificationReason()
    {
        return classificationReason;
    }

    public void setClassificationReason(String classificationReason)
    {
        this.classificationReason = classificationReason;
    }

    public String getPriority()
    {
        return priority;
    }

    public void setPriority(String priority)
    {
        this.priority = priority;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getPreanalysisSummary()
    {
        return preanalysisSummary;
    }

    public void setPreanalysisSummary(String preanalysisSummary)
    {
        this.preanalysisSummary = preanalysisSummary;
    }

    public String getPreanalysisSolution()
    {
        return preanalysisSolution;
    }

    public void setPreanalysisSolution(String preanalysisSolution)
    {
        this.preanalysisSolution = preanalysisSolution;
    }

    public String getSuggestedReply()
    {
        return suggestedReply;
    }

    public void setSuggestedReply(String suggestedReply)
    {
        this.suggestedReply = suggestedReply;
    }

    public String getHandlerReply()
    {
        return handlerReply;
    }

    public void setHandlerReply(String handlerReply)
    {
        this.handlerReply = handlerReply;
    }

    public String getHandleNote()
    {
        return handleNote;
    }

    public void setHandleNote(String handleNote)
    {
        this.handleNote = handleNote;
    }

    public String getHandledBy()
    {
        return handledBy;
    }

    public void setHandledBy(String handledBy)
    {
        this.handledBy = handledBy;
    }

    public Date getHandleTime()
    {
        return handleTime;
    }

    public void setHandleTime(Date handleTime)
    {
        this.handleTime = handleTime;
    }
}
