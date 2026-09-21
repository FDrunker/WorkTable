package com.ruoyi.wechat.domain;

/**
 * 聊天界面上的一条消息。
 *
 * @param messageId  消息ID
 * @param time       消息时间
 * @param date       消息日期，前端据此插入日期分隔
 * @param senderRole 归一化角色 SELF/TARGET/OTHER/UNKNOWN
 * @param senderRaw  原始发送者标签，未确认映射时前端展示它当作昵称
 * @param content    内容
 * @param kind       消息种类 TEXT/QUOTE/MEDIA/CALL/OTHER
 * @param analyzed   是否已参与过分析，用于在气泡上标出"将送给军师"的范围
 */
public record ChatMessageView(Long messageId, String time, String date, String senderRole,
        String senderRaw, String content, String kind, boolean analyzed)
{
}
