package com.ruoyi.wechat.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * 会话列表项。
 *
 * @param sessionId       会话ID
 * @param chatUsername    微信内部标识
 * @param displayName     展示名（即"昵称"，左侧列表按它分类展示）
 * @param group           是否群聊
 * @param identityConfirmed 说话人映射是否已确认
 * @param selfLabels      「我」的标签
 * @param targetLabels    对方标签
 * @param msgCount        已归档消息数
 * @param unanalyzedCount 未分析消息数
 * @param analysisCount   历史分析次数
 * @param lastTime        最后一条消息时间
 * @param lastMessage     最后一条消息预览
 * @param lastSyncTime    最后同步时间
 * @param affinityScore   当前好感度百分比，允许为空表示尚未计算
 * @param acquaintanceDate 用户填写的认识日期
 * @param acquaintanceDays 从认识日期到今天的天数，含认识当天
 * @param myPerformanceScore 我方回答综合得分
 * @param nextStepSuggestion 下一步建议，后续由大模型结果填充
 */
public record SessionView(Long sessionId, String chatUsername, String displayName, boolean group,
        boolean identityConfirmed, List<String> selfLabels, List<String> targetLabels,
        int msgCount, int unanalyzedCount, int analysisCount, String lastTime, String lastMessage,
        String lastSyncTime, BigDecimal affinityScore, String acquaintanceDate,
        Long acquaintanceDays, BigDecimal myPerformanceScore, String nextStepSuggestion)
{
}
