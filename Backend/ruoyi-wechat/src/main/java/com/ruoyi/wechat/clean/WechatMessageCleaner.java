package com.ruoyi.wechat.clean;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.ruoyi.wechat.config.WechatProperties;
import com.ruoyi.wechat.datasource.RawMessage;
import com.ruoyi.wechat.identity.ChatIdentity;

/**
 * 消息清洗器 —— 把数据源产出的原始行归一化成可送入 LLM 的上下文。
 *
 * <p>处理顺序（顺序不可调换）：
 * <ol>
 *   <li><b>类型识别</b>：区分文本 / 引用 / 媒体 / 语音 / 动画表情 / 通话 / 未知</li>
 *   <li><b>降噪</b>：丢弃静态表情、通话、撤回、系统提示、纯符号</li>
 *   <li><b>脱敏</b>：手机号、身份证、银行卡打码（数据要出境到云端 API）</li>
 *   <li><b>折叠</b>：连续同一人刷屏合并，但<b>保留"刷屏"这一行为特征</b></li>
 *   <li><b>裁剪</b>：按字符预算截断，并明确标注上下文不完整</li>
 * </ol>
 *
 * <p>其中 {@code [语音]} 与 {@code [动画表情]} 是<b>只展示、不分析</b>的：
 * 它们要出现在聊天记录界面里，但不能进入分析上下文
 * （见 {@link CleanedMessage.Kind#displayOnly()}）。
 * 因此降噪并不等于"从归档里删掉" —— 归档内容与送分析的上下文是两回事。
 *
 * <p>第 4 步是刻意的设计：军师 skill 明确要求"优先看持续主动、兑现、投入"，
 * 连续刷屏本身就是投入度的信号，因此只能压缩表示，不能消除。
 */
@Component
public class WechatMessageCleaner
{
    /** 媒体占位标记 */
    private static final Pattern MEDIA_PATTERN = Pattern.compile("^\\[(图片|文件|链接|小程序|位置|视频)\\]");

    /**
     * 语音占位。
     *
     * <p>只展示、不分析：聊天记录里必须看得到"发过语音"，但一条纯占位符
     * 对分析没有任何信息量。
     */
    private static final Pattern VOICE_PATTERN = Pattern.compile("^\\[语音\\]");

    /** 动画表情占位 —— 与语音同样只展示、不分析 */
    private static final Pattern STICKER_PATTERN = Pattern.compile("^\\[动画表情\\]");

    /** 通话记录 */
    private static final Pattern CALL_PATTERN = Pattern.compile("^\\[通话\\]|已取消通话|对方已取消|通话时长");

    /** 引用回复，源码中 app_type == 57，格式为 "回复 A: 内容" 或含 "↳" */
    private static final Pattern QUOTE_PATTERN = Pattern.compile("^(回复\\s*.+?:|.*↳)");

    /**
     * 纯符号、无文本信息量。
     *
     * <p>注意用 {@code \p{P}}（Unicode 标点）而不是 {@code \p{Punct}}：后者只覆盖 ASCII，
     * 而中文全角标点在 Unicode 里属于 Po/Ps/Pe 类。此处也不逐个列举全角符号，
     * 避免把 {@code “”‘’} 这类字符写进 Java 字符串字面量里 —— 它们是 U+201C/U+201D/U+2018/U+2019，
     * 会直接终止字符串导致编译失败。
     */
    private static final Pattern NOISE_ONLY = Pattern.compile("^[\\s\\p{P}\\p{S}]+$");

    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(1[3-9]\\d{9})(?!\\d)");

    private static final Pattern ID_CARD = Pattern.compile("(?<!\\d)(\\d{6})(\\d{8})(\\d{3})([\\dXx])(?!\\d)");

    private static final Pattern BANK_CARD = Pattern.compile("(?<!\\d)(\\d{4})\\d{8,11}(\\d{4})(?!\\d)");

    private final WechatProperties properties;

    public WechatMessageCleaner(WechatProperties properties)
    {
        this.properties = properties;
    }

    /**
     * 执行清洗（从配置读取参数）。
     *
     * @param messages 原始消息，按时间升序
     * @param identity 已确认或已推断的昵称映射
     * @param group    是否群聊
     */
    public CleanResult clean(List<RawMessage> messages, ChatIdentity identity, boolean group)
    {
        WechatProperties.Clean cfg = properties.getClean();
        return clean(messages, identity, group, cfg.isDropNoise(), cfg.getCollapseRepeatThreshold(),
                cfg.isMaskSensitive(), cfg.getContextCharBudget());
    }

    /**
     * 执行清洗（参数显式传入）。
     *
     * <p>把参数抽出来是为了让清洗链路能被纯粹的单元测试覆盖 ——
     * 这段逻辑最容易出错（正则、折叠、预算截断），而它又完全不依赖 Spring，
     * 没有理由非要启动容器才能验证。
     */
    public CleanResult clean(List<RawMessage> messages, ChatIdentity identity, boolean group,
            boolean dropNoise, int collapseThreshold, boolean maskSensitive, int contextCharBudget)
    {
        List<CleanedMessage> kept = new ArrayList<>();
        Map<CleanedMessage.Role, Integer> roleCounts = new EnumMap<>(CleanedMessage.Role.class);
        int dropped = 0;
        int masked = 0;

        for (RawMessage raw : messages)
        {
            if (raw.content() == null || raw.content().isBlank())
            {
                dropped++;
                continue;
            }

            String content = raw.content().trim();
            Kind0 kind0 = classify(content);

            if (dropNoise && kind0.noise)
            {
                dropped++;
                continue;
            }

            String cleaned = content;
            if (maskSensitive)
            {
                MaskOutcome mo = mask(content);
                cleaned = mo.text;
                if (mo.changed)
                {
                    masked++;
                }
            }

            CleanedMessage.Role role = identity.resolveRole(raw.sender(), group);
            roleCounts.merge(role, 1, Integer::sum);

            kept.add(new CleanedMessage(raw.seq(), raw.time(), raw.sender(), role, cleaned, kind0.kind));
        }

        CollapseOutcome collapsed = collapse(kept, collapseThreshold);

        BudgetOutcome budget = applyBudget(collapsed.messages, contextCharBudget);

        return new CleanResult(budget.messages, dropped, collapsed.collapsedCount, masked,
                roleCounts, budget.truncated, budget.note);
    }

    /**
     * 消息分类。
     *
     * <p>注意 {@code [语音]} 与 {@code [动画表情]} 走的是"保留但标记为只展示"的分支，
     * 而不是降噪丢弃：它们在聊天记录界面里必须看得到，只是不参与分析。
     * 也因此它们必须在普通媒体占位之前判定，否则会被当成"发生过一次互动"的媒体证据。
     */
    private Kind0 classify(String content)
    {
        if (CALL_PATTERN.matcher(content).find())
        {
            // 通话记录本身无措辞证据，且军师不需要通话元数据
            return new Kind0(CleanedMessage.Kind.CALL, true);
        }
        if (VOICE_PATTERN.matcher(content).find())
        {
            return new Kind0(CleanedMessage.Kind.VOICE, false);
        }
        if (STICKER_PATTERN.matcher(content).find())
        {
            return new Kind0(CleanedMessage.Kind.STICKER, false);
        }
        if ("[表情]".equals(content))
        {
            // 静态表情只是符号，展示价值也低，维持降噪
            return new Kind0(CleanedMessage.Kind.OTHER, true);
        }
        if (content.contains("撤回"))
        {
            return new Kind0(CleanedMessage.Kind.OTHER, true);
        }
        if (content.startsWith("你已添加了") || content.startsWith("以上是打招呼")
                || content.startsWith("我通过了你的朋友验证"))
        {
            return new Kind0(CleanedMessage.Kind.OTHER, true);
        }
        if (MEDIA_PATTERN.matcher(content).find())
        {
            // 媒体是"发生过一次互动"的证据，保留占位但不算原文
            return new Kind0(CleanedMessage.Kind.MEDIA, false);
        }
        if (QUOTE_PATTERN.matcher(content).find())
        {
            return new Kind0(CleanedMessage.Kind.QUOTE, false);
        }
        if (NOISE_ONLY.matcher(content).matches())
        {
            return new Kind0(CleanedMessage.Kind.OTHER, true);
        }
        return new Kind0(CleanedMessage.Kind.TEXT, false);
    }

    /**
     * 折叠连续同一人的重复发言。
     *
     * <p>当某人连续发言条数达到阈值时，中间的消息合并为一条并标注原始条数，
     * 只保留最后一条的措辞 —— 既省 token，又让"连续发了很多条"这个行为特征可见。
     */
    private CollapseOutcome collapse(List<CleanedMessage> messages, int threshold)
    {
        if (threshold <= 1 || messages.size() < threshold)
        {
            return new CollapseOutcome(messages, 0);
        }

        List<CleanedMessage> result = new ArrayList<>(messages.size());
        int collapsedCount = 0;
        int i = 0;
        while (i < messages.size())
        {
            CleanedMessage current = messages.get(i);
            int j = i + 1;
            while (j < messages.size() && sameSpeaker(current, messages.get(j)))
            {
                j++;
            }
            int runLength = j - i;

            if (runLength >= threshold && current.role() != CleanedMessage.Role.UNKNOWN)
            {
                // 保留该人本条连续发言里的最后一条作为措辞代表
                CleanedMessage last = messages.get(j - 1);
                String marker = "（此人连续发送 " + runLength + " 条）";
                result.add(new CleanedMessage(last.seq(), last.time(), last.rawSender(), last.role(),
                        last.content() + "\n" + marker, last.kind()));
                collapsedCount += runLength - 1;
            }
            else
            {
                for (int k = i; k < j; k++)
                {
                    result.add(messages.get(k));
                }
            }
            i = j;
        }
        return new CollapseOutcome(result, collapsedCount);
    }

    private boolean sameSpeaker(CleanedMessage a, CleanedMessage b)
    {
        if (a.role() != b.role())
        {
            return false;
        }
        String sa = a.rawSender() == null ? "" : a.rawSender();
        String sb = b.rawSender() == null ? "" : b.rawSender();
        return sa.equals(sb);
    }

    /**
     * 按字符预算裁剪：从<b>最旧</b>的消息开始丢，保留最近对话。
     *
     * <p>军师 skill 会更看重最近的互动，因此保近丢远是正确方向；
     * 但必须把截断事实写进上下文，否则模型会把"不完整"当成"没有发生过"。
     */
    private BudgetOutcome applyBudget(List<CleanedMessage> messages, int budget)
    {
        if (budget <= 0)
        {
            return new BudgetOutcome(messages, false, null);
        }

        int total = 0;
        for (CleanedMessage m : messages)
        {
            total += renderLength(m);
        }
        if (total <= budget)
        {
            return new BudgetOutcome(messages, false, null);
        }

        // 从尾部往前保留，直到预算耗尽
        List<CleanedMessage> kept = new ArrayList<>();
        int used = 0;
        for (int i = messages.size() - 1; i >= 0; i--)
        {
            int len = renderLength(messages.get(i));
            if (used + len > budget && !kept.isEmpty())
            {
                break;
            }
            kept.add(messages.get(i));
            used += len;
        }
        java.util.Collections.reverse(kept);

        int droppedOldest = messages.size() - kept.size();
        String note = "较早的 " + droppedOldest + " 条消息因上下文长度限制未提供，"
                + "请在分析中明确这一点，不要假设未提供的部分是空白的。";
        return new BudgetOutcome(kept, true, note);
    }

    private int renderLength(CleanedMessage m)
    {
        int len = 0;
        if (m.time() != null)
        {
            len += m.time().length();
        }
        len += m.content() == null ? 0 : m.content().length();
        len += 24; // 角色标签与分隔符的粗略开销
        return len;
    }

    /**
     * 敏感信息脱敏。只处理有确定格式的强标识，避免误伤正常对话。
     */
    private MaskOutcome mask(String content)
    {
        String original = content;
        content = PHONE.matcher(content).replaceAll(m -> m.group(1).substring(0, 3) + "****" + m.group(1).substring(7));
        content = ID_CARD.matcher(content).replaceAll(m -> m.group(1) + "********" + m.group(4));
        content = BANK_CARD.matcher(content).replaceAll(m -> m.group(1) + "********" + m.group(2));
        return new MaskOutcome(content, !original.equals(content));
    }

    private record Kind0(CleanedMessage.Kind kind, boolean noise)
    {
    }

    private record MaskOutcome(String text, boolean changed)
    {
    }

    private record CollapseOutcome(List<CleanedMessage> messages, int collapsedCount)
    {
    }

    private record BudgetOutcome(List<CleanedMessage> messages, boolean truncated, String note)
    {
    }
}
