package com.ruoyi.wechat.identity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ruoyi.wechat.clean.CleanedMessage;
import com.ruoyi.wechat.datasource.RawMessage;

/**
 * 昵称映射 — 把数据源给出的<b>原始发送者标签</b>绑定到具体角色。
 *
 * <p>依据来自 wechat-cli 源码 {@code core/contacts.py}：
 * <ul>
 *   <li>{@code display_name_for_username} 对"我"返回字面量 {@code me}；</li>
 *   <li>对他人返回 备注 remark → 昵称 nick_name → wxid 的优先级；</li>
 *   <li>{@code get_self_username()} 靠 db_storage 父目录名反查 contact 表推断"我"。</li>
 * </ul>
 * 因此"我是谁"在有 CLI 数据源时是可知的；无法得知时由用户确认。
 *
 * @param selfUsername  数据源推断出的"我"的标识，可为 null
 * @param selfLabels    "我"在聊天记录里出现的原始标签候选
 * @param targetLabels  对方在聊天记录里出现的原始标签候选
 * @param autoDetected  是否为自动推断（false 表示用户手动确认过）
 */
public record ChatIdentity(String selfUsername, List<String> selfLabels, List<String> targetLabels,
        boolean autoDetected)
{
    /**
     * 判断某个原始标签是否代表"我"。
     *
     * <p>匹配是<b>精确</b>的，不做模糊匹配 —— 军师 skill 要求说话人映射不明时先问，
     * 宁可让用户确认，也不要猜错后把整段分析建立在错误前提上。
     */
    public boolean isSelf(String rawLabel)
    {
        return matches(selfLabels, rawLabel);
    }

    public boolean isTarget(String rawLabel)
    {
        return matches(targetLabels, rawLabel);
    }

    private static boolean matches(List<String> labels, String rawLabel)
    {
        if (labels == null || rawLabel == null)
        {
            return false;
        }
        for (String label : labels)
        {
            if (rawLabel.equals(label))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * 从原始消息中统计出现过的发送者标签及其发言次数，用于让用户确认映射。
     *
     * <p>使用 LinkedHashMap 保持首次出现顺序，便于前端稳定展示。
     */
    public static Map<String, Integer> countSenders(List<RawMessage> messages)
    {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (messages == null)
        {
            return counts;
        }
        for (RawMessage msg : messages)
        {
            if (msg.hasSender())
            {
                counts.merge(msg.sender(), 1, Integer::sum);
            }
        }
        return counts;
    }

    /**
     * 推断候选映射。
     *
     * <p>策略（按可靠性从高到低）：
     * <ol>
     *   <li>数据源给出 selfUsername 且消息里存在标签 {@code me} → 直接采用；</li>
     *   <li>私聊且恰有两个标签，其中一个是 {@code me} → 另一个即对方；</li>
     *   <li>其余情况不猜，返回空候选交由用户确认。</li>
     * </ol>
     *
     * @param selfUsername 数据源推断的"我"，可为 null
     * @param chatDisplayName 会话展示名，用于识别对方标签
     * @param group 是否群聊
     */
    public static ChatIdentity infer(String selfUsername, String chatDisplayName, boolean group,
            List<RawMessage> messages)
    {
        Map<String, Integer> counts = countSenders(messages);

        // 规则 1：源码对"我"输出字面量 me
        if (counts.containsKey("me"))
        {
            List<String> targetLabels = new java.util.ArrayList<>();
            if (chatDisplayName != null && counts.containsKey(chatDisplayName))
            {
                targetLabels.add(chatDisplayName);
            }
            // 私聊且只有两个标签时，另一个必然是对对方
            if (!group && counts.size() == 2)
            {
                for (String label : counts.keySet())
                {
                    if (!"me".equals(label) && !targetLabels.contains(label))
                    {
                        targetLabels.add(label);
                    }
                }
            }
            return new ChatIdentity(selfUsername, List.of("me"), targetLabels, true);
        }

        // 规则 3：无法可靠推断，交给用户
        return new ChatIdentity(selfUsername, List.of(), List.of(), false);
    }

    /**
     * 用户确认后的映射。
     */
    public static ChatIdentity confirmed(String selfUsername, List<String> selfLabels,
            List<String> targetLabels)
    {
        return new ChatIdentity(selfUsername, selfLabels, targetLabels, false);
    }

    /**
     * 把原始标签解析为角色。群聊中未匹配的标签归为 OTHER。
     *
     * @param rawSender 原始标签，可为 null（私聊中数据源未标注发送者）
     * @param group     是否群聊
     */
    public CleanedMessage.Role resolveRole(String rawSender, boolean group)
    {
        if (rawSender == null || rawSender.isBlank())
        {
            // 私聊里没有发送者标签的，通常就是"我"（源码 _resolve_sender_label 的行为），
            // 但只有在映射已确认的前提下才敢这么判
            return autoDetected ? CleanedMessage.Role.SELF : CleanedMessage.Role.UNKNOWN;
        }
        if (isSelf(rawSender))
        {
            return CleanedMessage.Role.SELF;
        }
        if (isTarget(rawSender))
        {
            return CleanedMessage.Role.TARGET;
        }
        return group ? CleanedMessage.Role.OTHER : CleanedMessage.Role.UNKNOWN;
    }
}
