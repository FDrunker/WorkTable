package com.ruoyi.wechat.clean;

/**
 * 清洗后的消息 — 已归一化发送者角色与内容。
 *
 * @param seq       清洗前的原始顺序号，用于回溯到原始记录
 * @param time      时间文本
 * @param rawSender 原始发送者标签（保留证据，便于用户核对映射）
 * @param role      归一化角色：SELF / TARGET / OTHER / UNKNOWN
 * @param content   清洗后的内容
 * @param kind      消息种类：TEXT / MEDIA / QUOTE / ...
 */
public record CleanedMessage(int seq, String time, String rawSender, Role role, String content, Kind kind)
{
    /**
     * 发送者角色。此处的判断<b>只依据用户已确认的映射</b>，
     * 不做任何基于语气或性别的猜测 —— 军师 skill 明确禁止按左右或语气猜说话人。
     */
    public enum Role
    {
        /** 用户本人 */
        SELF,
        /** 分析目标（对方） */
        TARGET,
        /** 群聊中的其他成员 */
        OTHER,
        /** 私聊中未标注发送者的消息，归属未知 */
        UNKNOWN
    }

    /**
     * 消息种类，决定渲染方式与是否参与"事实"判定。
     */
    public enum Kind
    {
        /** 文本原文 */
        TEXT,
        /** 引用/回复消息，含被引用内容，是重要的措辞证据 */
        QUOTE,
        /** 媒体占位（图片/文件/链接/位置/小程序） */
        MEDIA,
        /** 通话记录 */
        CALL,
        /** 未知类型 */
        OTHER
    }
}
