package com.ruoyi.wechat.clean;

import java.util.List;
import java.util.Map;

/**
 * 清洗结果。
 *
 * @param messages        清洗后的消息（已按时间升序）
 * @param droppedCount    被降噪丢弃的条数
 * @param collapsedCount  被折叠合并的条数
 * @param maskedCount     被脱敏的条数
 * @param roleCounts      各角色的发言条数，用于给军师提供"互惠"的量化依据
 * @param truncated       是否因为上下文预算而做了截断
 * @param truncatedNote   截断说明，注入 prompt 让模型知道上下文不完整
 */
public record CleanResult(List<CleanedMessage> messages, int droppedCount, int collapsedCount,
        int maskedCount, Map<CleanedMessage.Role, Integer> roleCounts, boolean truncated, String truncatedNote)
{
    public int size()
    {
        return messages.size();
    }
}
