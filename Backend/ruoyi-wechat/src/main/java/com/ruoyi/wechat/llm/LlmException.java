package com.ruoyi.wechat.llm;

/**
 * LLM 调用异常。
 */
public class LlmException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    private final Integer httpStatus;

    public LlmException(String message)
    {
        this(message, null, null);
    }

    public LlmException(String message, Integer httpStatus, Throwable cause)
    {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    public Integer getHttpStatus()
    {
        return httpStatus;
    }

    /**
     * 是否为可重试的错误（限流或服务端错误）。
     */
    public boolean retryable()
    {
        if (httpStatus == null)
        {
            // 网络层面失败通常是可重试的
            return true;
        }
        return httpStatus == 429 || httpStatus >= 500;
    }
}
