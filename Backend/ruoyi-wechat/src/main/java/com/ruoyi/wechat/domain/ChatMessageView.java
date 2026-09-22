package com.ruoyi.wechat.domain;

import java.math.BigDecimal;

/**
 * 聊天界面上的一条消息。
 *
 * @param messageId  消息ID
 * @param time       消息时间
 * @param date       消息日期，前端据此插入日期分隔
 * @param senderRole 归一化角色 SELF/TARGET/OTHER/UNKNOWN
 * @param senderRaw  原始发送者标签，未确认映射时前端展示它当作昵称
 * @param content    内容
 * @param kind       消息种类 TEXT/QUOTE/MEDIA/VOICE/STICKER/CALL/OTHER
 *                   （VOICE 与 STICKER 只展示、不参与分析）
 * @param analyzed   是否已参与过分析，用于在气泡上标出"将送给军师"的范围
 * @param emotionTag 对方消息的情绪标签
 * @param emotionPercent 情绪标签百分比
 * @param intentTag  对方消息的意图标签
 * @param intentPercent 意图标签百分比
 * @param replyRating 我方回答评级
 * @param replyScore 我方回答得分，用于汇总"我的发挥"
 */
public record ChatMessageView(Long messageId, String time, String date, String senderRole,
        String senderRaw, String content, String kind, boolean analyzed, String emotionTag,
        BigDecimal emotionPercent, String intentTag, BigDecimal intentPercent,
        String replyRating, BigDecimal replyScore)
{
}
