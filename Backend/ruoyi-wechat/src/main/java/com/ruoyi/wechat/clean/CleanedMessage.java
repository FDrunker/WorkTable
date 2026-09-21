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
        /** 媒体占位（图片/文件/链接/位置/小程序），作为"发生过一次互动"的证据参与分析 */
        MEDIA,
        /**
         * 语音占位 —— 内容只有 {@code [语音]} 这一行，没有任何措辞。
         *
         * <p>它<b>要展示</b>在聊天记录里（用户需要看到当时发过语音），
         * 但<b>不参与分析</b>：把一个纯占位符喂给模型只会占 token、引不出有效信息。
         */
        VOICE,
        /** 动画表情占位 —— 与 {@link #VOICE} 同样只展示、不分析 */
        STICKER,
        /** 通话记录 */
        CALL,
        /** 未知类型 */
        OTHER;

        /**
         * 是否只展示、不参与分析。
         */
        public boolean displayOnly()
        {
            return this == VOICE || this == STICKER;
        }
    }
}
