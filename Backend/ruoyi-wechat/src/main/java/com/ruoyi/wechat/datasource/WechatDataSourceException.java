package com.ruoyi.wechat.datasource;

/**
 * 数据源访问异常。
 *
 * <p>把各数据源形态各异的失败（进程退出码、文件格式错误、网络问题）
 * 统一成一种异常，附带可展示给用户的修复建议。
 */
public class WechatDataSourceException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    /** 建议的修复动作，可为 null */
    private final String suggestion;

    public WechatDataSourceException(String message)
    {
        this(message, null, null);
    }

    public WechatDataSourceException(String message, String suggestion)
    {
        this(message, suggestion, null);
    }

    public WechatDataSourceException(String message, String suggestion, Throwable cause)
    {
        super(message, cause);
        this.suggestion = suggestion;
    }

    public String getSuggestion()
    {
        return suggestion;
    }
}
