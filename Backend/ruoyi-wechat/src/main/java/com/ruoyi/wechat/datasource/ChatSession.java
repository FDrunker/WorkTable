package com.ruoyi.wechat.datasource;

/**
 * 会话摘要 — 来自会话列表，用于让用户挑选要分析的聊天。
 *
 * @param username    微信内部标识（wxid_xxx / xxx@chatroom），作为稳定主键
 * @param displayName 展示名（备注优先，其次昵称）
 * @param group       是否群聊
 * @param unread      未读条数
 * @param lastMessage 最后一条消息预览
 * @param lastTime    最后消息时间文本
 * @param selfUsername 数据源推断出的"我"的 username，推断不出时为 null
 */
public record ChatSession(String username, String displayName, boolean group, int unread,
        String lastMessage, String lastTime, String selfUsername)
{
}
