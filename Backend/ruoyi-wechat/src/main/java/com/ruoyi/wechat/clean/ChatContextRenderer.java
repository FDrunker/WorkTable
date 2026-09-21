package com.ruoyi.wechat.clean;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * 把清洗后的消息渲染成送入 LLM 的上下文文本。
 *
 * <p>渲染格式刻意保持<b>高信息密度且可追溯</b>：每行都带时间戳与角色标签，
 * 这样模型引用"已知事实"时能够带上时间，也便于人工核对它有没有编造。
 *
 * <p>标注为"只展示"的消息（{@code [语音]}、{@code [动画表情]}）在这里被整条跳过：
 * 它们会出现在聊天记录界面，但不进分析上下文。
 */
@Component
public class ChatContextRenderer
{
    /**
     * 渲染对话正文。
     *
     * @param messages 清洗后的消息
     */
    public String render(List<CleanedMessage> messages)
    {
        StringBuilder sb = new StringBuilder();
        String lastDate = null;
        for (CleanedMessage m : messages)
        {
            if (m.kind().displayOnly())
            {
                // 语音 / 动画表情：只展示，不参与分析
                continue;
            }
            String date = extractDate(m.time());
            if (date != null && !date.equals(lastDate))
            {
                sb.append("\n=== ").append(date).append(" ===\n");
                lastDate = date;
            }
            sb.append('[').append(nullSafe(m.time())).append("] ");
            sb.append(label(m.role())).append(": ");
            sb.append(nullSafe(m.content()));
            if (m.kind() == CleanedMessage.Kind.MEDIA)
            {
                sb.append("  (媒体内容，无文字)");
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /**
     * 渲染发言统计，作为"互惠程度"的量化输入。
     *
     * <p>军师 skill 要求判断互惠与投入，条数对比是最直接的客观依据之一，
     * 因此显式提供给模型，而不是让它自己数。
     */
    public String renderStats(CleanResult result)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("总消息数：").append(result.size()).append('\n');
        result.roleCounts().forEach((role, count) ->
                sb.append(label(role)).append("：").append(count).append(" 条\n"));
        long displayOnly = result.messages().stream().filter(m -> m.kind().displayOnly()).count();
        if (displayOnly > 0)
        {
            sb.append("（其中 ").append(displayOnly).append(" 条是语音/动画表情占位，只展示、未参与分析）\n");
        }
        if (result.collapsedCount() > 0)
        {
            sb.append("（已折叠合并 ").append(result.collapsedCount()).append(" 条连续重复发言）\n");
        }
        if (result.droppedCount() > 0)
        {
            sb.append("（已过滤 ").append(result.droppedCount()).append(" 条表情/通话/系统等无措辞内容）\n");
        }
        if (result.truncated())
        {
            sb.append("【重要】").append(result.truncatedNote()).append('\n');
        }
        return sb.toString();
    }

    private String label(CleanedMessage.Role role)
    {
        return switch (role)
        {
            case SELF -> "我";
            case TARGET -> "对方";
            case OTHER -> "群成员";
            case UNKNOWN -> "未知说话人";
        };
    }

    private String extractDate(String time)
    {
        if (time == null || time.length() < 10)
        {
            return null;
        }
        return time.substring(0, 10);
    }

    private String nullSafe(String s)
    {
        return s == null ? "" : s;
    }
}
