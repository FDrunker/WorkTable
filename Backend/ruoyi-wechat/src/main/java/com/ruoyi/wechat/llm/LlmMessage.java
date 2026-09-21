package com.ruoyi.wechat.llm;

/**
 * LLM 对话消息。
 */
public record LlmMessage(String role, String content)
{
    public static LlmMessage system(String content)
    {
        return new LlmMessage("system", content);
    }

    public static LlmMessage user(String content)
    {
        return new LlmMessage("user", content);
    }

    public static LlmMessage assistant(String content)
    {
        return new LlmMessage("assistant", content);
    }
}
