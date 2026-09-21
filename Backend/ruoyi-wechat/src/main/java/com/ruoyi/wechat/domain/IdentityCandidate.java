package com.ruoyi.wechat.domain;

import java.util.List;

/**
 * 昵称映射候选 — 返回给前端供用户确认。
 *
 * @param chatName     聊天名
 * @param group        是否群聊
 * @param selfUsername 数据源推断的"我"的标识，可为 null
 * @param autoDetected 是否已能自动确定映射
 * @param senders      出现过的原始发送者标签及发言次数（按首次出现顺序）
 * @param guessSelf    推测的"我"的标签候选
 * @param guessTarget  推测的对方标签候选
 * @param hint         给用户的说明，尤其是"无法自动判断"时的原因
 */
public record IdentityCandidate(String chatName, boolean group, String selfUsername, boolean autoDetected,
        List<SenderCount> senders, List<String> guessSelf, List<String> guessTarget, String hint)
{
    /**
     * 原始发送者标签及发言次数。
     */
    public record SenderCount(String label, int count)
    {
    }
}
