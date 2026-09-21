package com.ruoyi.wechat.domain;

/**
 * 触发分析的结果。
 *
 * @param analysis       本次分析记录（含结构化结果）
 * @param analyzedCount  本次消费掉的未分析消息条数
 * @param remaining      剩余未分析消息条数
 * @param hasMore        是否还有未分析消息（大于 0 表示需要再次点击分析继续）
 */
public record AnalysisTriggerResult(WeChatAnalysis analysis, int analyzedCount, int remaining, boolean hasMore)
{
}
