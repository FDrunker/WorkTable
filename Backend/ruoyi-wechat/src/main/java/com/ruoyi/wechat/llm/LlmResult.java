package com.ruoyi.wechat.llm;

/**
 * 一次 LLM 调用的结果。
 *
 * @param content          模型输出正文
 * @param model            实际使用的模型
 * @param promptTokens     输入 token 数（接口未返回时为 0）
 * @param completionTokens 输出 token 数（接口未返回时为 0）
 * @param durationMs       耗时
 */
public record LlmResult(String content, String model, int promptTokens, int completionTokens, long durationMs)
{
    public int totalTokens()
    {
        return promptTokens + completionTokens;
    }
}
