package com.ruoyi.knowledge.domain;

/**
 * 智能客服工单提问。
 */
public record CustomerServiceSendRequest(
        String visitorId,
        String issueType,
        String question)
{
}
