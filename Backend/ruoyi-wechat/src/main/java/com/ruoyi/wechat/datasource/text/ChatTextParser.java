package com.ruoyi.wechat.datasource.text;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ruoyi.wechat.datasource.RawMessage;

/**
 * 粘贴文本解析器 —— 把用户粘贴的聊天记录切成一条条 {@link RawMessage}。
 *
 * <p>约定格式：每条消息占 3 行
 *
 * <pre>
 * 昵称
 * 2026-04-01 19:02
 * 消息内容
 * </pre>
 *
 * <p>规则：
 * <ul>
 *   <li>「昵称 + 时间」是消息的起点；消息内容可以跨多行，直到下一组「昵称 + 时间」为止。</li>
 *   <li>组与组之间的空行会被忽略，消息内容自身的换行会被保留（首尾空白裁掉）。</li>
 *   <li>时间接受 {@code yyyy-MM-dd HH:mm[:ss]}、{@code yyyy/M/d HH:mm}、{@code yyyy年M月d日 HH:mm}
 *       以及省略年份的 {@code MM-dd HH:mm} / {@code M月d日 HH:mm}（省略年份时按当前年份补齐），
 *       并兼容 {@code 下午7:02} 这类时段前缀。最终统一归一化为 {@code yyyy-MM-dd HH:mm[:ss]}，
 *       因为归档表按前 10 个字符取日期。</li>
 *   <li>不成组的行会被直接跳过：粘错几行不会导致整段解析失败。</li>
 * </ul>
 *
 * <p>这里刻意<b>不</b>做任何内容语义判断（例如丢掉表情、语音）：
 * 「哪些消息要展示、哪些消息要参与分析」是清洗链路的职责，
 * 解析层只负责把文本还原成消息，否则语音/动画表情会因为被提前丢弃而无法出现在聊天记录里。
 */
public class ChatTextParser
{
    /** 时刻：可选时段前缀 + HH:mm[:ss] */
    private static final Pattern CLOCK = Pattern.compile(
            "^(上午|下午|中午|凌晨|早上|晚上)?\\s*(\\d{1,2}):(\\d{2})(?::(\\d{2}))?$");

    /** 带年份的日期 */
    private static final Pattern DATE_FULL = Pattern.compile(
            "^(\\d{4})\\s*[-/.年]\\s*(\\d{1,2})\\s*[-/.月]\\s*(\\d{1,2})\\s*日?$");

    /** 不带年份的日期 */
    private static final Pattern DATE_SHORT = Pattern.compile(
            "^(\\d{1,2})\\s*[-/.月]\\s*(\\d{1,2})\\s*日?$");

    /** 时间行的长度上限：超过这个长度的行不可能是时间，避免把正文误判成时间 */
    private static final int TIME_LINE_MAX_LENGTH = 40;

    /**
     * 解析文本。
     *
     * @param text 用户粘贴的原文，可为 null
     * @return 按文本顺序排列的消息；解析不出任何消息时返回空列表
     */
    public List<RawMessage> parse(String text)
    {
        List<RawMessage> messages = new ArrayList<>();
        if (text == null || text.isBlank())
        {
            return messages;
        }

        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        int seq = 0;
        int i = 0;
        while (i < lines.length)
        {
            if (lines[i].isBlank())
            {
                i++;
                continue;
            }
            String time = (i + 1 < lines.length) ? normalizeTime(lines[i + 1]) : null;
            if (time == null)
            {
                // 不成组的行：粘进来的标题、日期分隔符之类，跳过即可
                i++;
                continue;
            }

            String sender = lines[i].trim();
            i += 2;

            StringBuilder content = new StringBuilder();
            while (i < lines.length && !startsBlock(lines, i))
            {
                content.append(lines[i]).append('\n');
                i++;
            }

            messages.add(new RawMessage(seq++, time, sender, content.toString().trim()));
        }
        return messages;
    }

    /**
     * 第 {@code i} 行是否是下一条消息的「昵称」行。
     *
     * <p>判断依据是它<b>紧跟</b>一条时间行 —— 三行格式里这是唯一的可靠信号。
     */
    private boolean startsBlock(String[] lines, int i)
    {
        if (lines[i].isBlank() || i + 1 >= lines.length)
        {
            return false;
        }
        return normalizeTime(lines[i + 1]) != null;
    }

    /**
     * 把时间行归一化成 {@code yyyy-MM-dd HH:mm[:ss]}。
     *
     * @return 归一化结果；不是时间行时返回 null
     */
    String normalizeTime(String raw)
    {
        if (raw == null)
        {
            return null;
        }
        String line = raw.trim();
        if (line.isEmpty() || line.length() > TIME_LINE_MAX_LENGTH)
        {
            return null;
        }

        // 日期与时刻之间用空白分隔；从右往左找最后一个空白，兼容全角空格
        int split = -1;
        for (int k = line.length() - 1; k >= 0; k--)
        {
            if (Character.isWhitespace(line.charAt(k)))
            {
                split = k;
                break;
            }
        }
        if (split <= 0 || split == line.length() - 1)
        {
            return null;
        }

        Matcher clock = CLOCK.matcher(line.substring(split + 1).trim());
        if (!clock.matches())
        {
            return null;
        }
        int hour = toHour(Integer.parseInt(clock.group(2)), clock.group(1));
        int minute = Integer.parseInt(clock.group(3));
        String second = clock.group(4);
        if (hour < 0 || hour > 23 || minute > 59)
        {
            return null;
        }

        String datePart = line.substring(0, split).trim();
        int year;
        int month;
        int day;
        Matcher full = DATE_FULL.matcher(datePart);
        Matcher shortDate = DATE_SHORT.matcher(datePart);
        if (full.matches())
        {
            year = Integer.parseInt(full.group(1));
            month = Integer.parseInt(full.group(2));
            day = Integer.parseInt(full.group(3));
        }
        else if (shortDate.matches())
        {
            // 省略年份的写法：按当前年份补齐
            year = LocalDate.now().getYear();
            month = Integer.parseInt(shortDate.group(1));
            day = Integer.parseInt(shortDate.group(2));
        }
        else
        {
            return null;
        }

        try
        {
            LocalDate.of(year, month, day);
        }
        catch (DateTimeException e)
        {
            return null;
        }

        return String.format("%04d-%02d-%02d %02d:%02d%s", year, month, day, hour, minute,
                second == null ? "" : ":" + second);
    }

    /**
     * 处理中文时段前缀。
     */
    private int toHour(int hour, String period)
    {
        if (period == null)
        {
            return hour;
        }
        return switch (period)
        {
            case "下午", "晚上" -> hour < 12 ? hour + 12 : hour;
            case "中午" -> hour < 12 ? hour + 12 : hour;
            case "凌晨", "早上", "上午" -> hour == 12 ? 0 : hour;
            default -> hour;
        };
    }
}
