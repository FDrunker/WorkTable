package com.ruoyi.wechat.datasource.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.wechat.datasource.ChatSession;
import com.ruoyi.wechat.datasource.RawMessage;
import com.ruoyi.wechat.datasource.WechatDataSourceException;

/**
 * wechat-cli 输出解析器。
 *
 * <p>解析目标来自仓库源码 {@code wechat_cli/commands/sessions.py} 与
 * {@code history.py}，其真实输出形如：
 *
 * <pre>
 * sessions → [{"chat":"张三","username":"wxid_x","is_group":false,"unread":0,
 *              "last_message":"...","msg_type":"文本","sender":"","timestamp":1712,"time":"04-01 10:23"}]
 * history  → {"chat":"张三","username":"wxid_x","is_group":false,"count":50,"offset":0,"limit":50,
 *             "messages":["[2026-04-01 10:23] me: 在吗","[2026-04-01 10:24] 张三: 在"],
 *             "failures":null}
 * </pre>
 *
 * <p>关键点：{@code messages} 里是<b>已经拼好的字符串</b>而非结构化对象，
 * 因此必须做正则拆解。行格式为 {@code [时间] 可选的发送者: 内容}。
 */
public final class CliOutputParser
{
    /**
     * 匹配 {@code [yyyy-MM-dd HH:mm] 发送者: 内容}。
     *
     * <p>发送者部分用非贪婪匹配到第一个冒号，且要求冒号后必须有一个空格
     * ——这是源码 {@code "[{time_str}] {sender_label}: {text}"} 的固定格式，
     * 可以避免把消息正文里的冒号误判为分隔符。
     */
    private static final Pattern LINE_PATTERN = Pattern.compile(
            "^\\[(?<time>[^\\]]+)\\]\\s+(?:(?<sender>[^:]+?):\\s+)?(?<content>.*)$",
            Pattern.DOTALL);

    private CliOutputParser()
    {
    }

    /**
     * 解析 {@code sessions} 命令的 JSON 数组输出。
     */
    public static List<ChatSession> parseSessions(String stdout)
    {
        String json = extractJson(stdout, '[', ']');
        try
        {
            JSONArray array = JSON.parseArray(json);
            List<ChatSession> result = new ArrayList<>(array.size());
            for (int i = 0; i < array.size(); i++)
            {
                JSONObject obj = array.getJSONObject(i);
                if (obj == null)
                {
                    continue;
                }
                result.add(new ChatSession(
                        obj.getString("username"),
                        obj.getString("chat"),
                        obj.getBooleanValue("is_group"),
                        obj.getIntValue("unread"),
                        obj.getString("last_message"),
                        obj.getString("time"),
                        // sessions 命令不返回"我"，留空由 identity 层推断
                        null));
            }
            return result;
        }
        catch (Exception e)
        {
            throw new WechatDataSourceException(
                    "无法解析 wechat-cli sessions 的 JSON 输出",
                    "请确认 wechat-cli 版本，或执行一次 `wechat-cli sessions --limit 3` 检查输出格式",
                    e);
        }
    }

    /**
     * 解析 {@code history} 命令的 JSON 对象输出。
     *
     * @return 消息列表，按时间升序
     */
    public static List<RawMessage> parseHistory(String stdout)
    {
        String json = extractJson(stdout, '{', '}');
        try
        {
            JSONObject obj = JSON.parseObject(json);
            JSONArray messages = obj.getJSONArray("messages");
            if (messages == null)
            {
                // 源码中"无消息记录"会走 text 分支或返回空数组，这里按空处理
                return new ArrayList<>();
            }
            List<RawMessage> result = new ArrayList<>(messages.size());
            for (int i = 0; i < messages.size(); i++)
            {
                String line = messages.getString(i);
                RawMessage msg = parseLine(line, i);
                if (msg != null)
                {
                    result.add(msg);
                }
            }
            // 按时间升序稳定排序，兼容数据源内部的倒序查询
            result.sort((a, b) -> {
                int c = nullSafeCompare(a.time(), b.time());
                return c != 0 ? c : Integer.compare(a.seq(), b.seq());
            });
            return result;
        }
        catch (WechatDataSourceException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new WechatDataSourceException(
                    "无法解析 wechat-cli history 的 JSON 输出",
                    "请执行一次 `wechat-cli history \"聊天名\" --limit 5` 检查输出格式",
                    e);
        }
    }

    /**
     * 拆解单行消息。
     *
     * @return 无法识别的行返回 null（例如数据源混入了非消息行）
     */
    public static RawMessage parseLine(String line, int seq)
    {
        if (line == null || line.isBlank())
        {
            return null;
        }
        Matcher m = LINE_PATTERN.matcher(line.trim());
        if (!m.matches())
        {
            return null;
        }
        String time = m.group("time");
        String sender = m.group("sender");
        String content = m.group("content");
        // 群聊中消息正文可能自带 "displayname:\n" 前缀，源码已剥离，
        // 但不同版本行为可能不一致，这里再兜底剥一次
        if (content != null)
        {
            int idx = content.indexOf(":\n");
            if (idx > 0 && sender == null)
            {
                sender = content.substring(0, idx).trim();
                content = content.substring(idx + 2);
            }
        }
        return new RawMessage(seq, time == null ? null : time.trim(),
                sender == null ? null : sender.trim(),
                content == null ? "" : content.trim());
    }

    /**
     * 从可能混有日志的输出中截取 JSON 主体。
     *
     * <p>wechat-cli 正常运行时 stdout 只输出 JSON，但 Node 包装脚本或
     * 上层终端可能注入警告行，因此做一个宽松的截取。
     */
    private static String extractJson(String stdout, char open, char close)
    {
        if (stdout == null)
        {
            throw new WechatDataSourceException("wechat-cli 没有产生任何输出");
        }
        String trimmed = stdout.trim();
        int start = trimmed.indexOf(open);
        int end = trimmed.lastIndexOf(close);
        if (start < 0 || end <= start)
        {
            String preview = trimmed.length() > 300 ? trimmed.substring(0, 300) + "..." : trimmed;
            throw new WechatDataSourceException(
                    "wechat-cli 输出中没有找到 JSON 结构",
                    "原始输出前 300 字符：" + preview);
        }
        return trimmed.substring(start, end + 1);
    }

    private static int nullSafeCompare(String a, String b)
    {
        if (a == null && b == null)
        {
            return 0;
        }
        if (a == null)
        {
            return -1;
        }
        if (b == null)
        {
            return 1;
        }
        return a.compareTo(b);
    }
}
