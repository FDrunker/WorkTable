package com.ruoyi.knowledge.domain;

/**
 * 个人简历 AI 提问。
 */
public record ResumeAiSendRequest(
        String visitorId,
        String question)
{
}
