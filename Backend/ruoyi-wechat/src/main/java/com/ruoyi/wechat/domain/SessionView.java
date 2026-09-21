package com.ruoyi.wechat.domain;

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
 */
public record SessionView(Long sessionId, String chatUsername, String displayName, boolean group,
        boolean identityConfirmed, List<String> selfLabels, List<String> targetLabels,
        int msgCount, int unanalyzedCount, int analysisCount, String lastTime, String lastMessage,
        String lastSyncTime)
{
}
