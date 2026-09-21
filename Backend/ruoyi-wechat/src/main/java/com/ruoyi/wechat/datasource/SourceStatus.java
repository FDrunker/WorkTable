package com.ruoyi.wechat.datasource;

/**
 * 数据源运行状态。
 *
 * <p>用于在用户点"同步"之前就把环境问题暴露出来，而不是等到分析时才失败。
 *
 * @param ready   是否可用
 * @param mode    数据源标识
 * @param message 人类可读的说明（不可用时给出修复建议）
 */
public record SourceStatus(boolean ready, String mode, String message)
{
    public static SourceStatus ok(String mode, String message)
    {
        return new SourceStatus(true, mode, message);
    }

    public static SourceStatus notReady(String mode, String message)
    {
        return new SourceStatus(false, mode, message);
    }
}
