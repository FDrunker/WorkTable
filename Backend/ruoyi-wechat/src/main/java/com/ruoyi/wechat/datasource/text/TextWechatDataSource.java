package com.ruoyi.wechat.datasource.text;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.util.DigestUtils;

import com.ruoyi.wechat.datasource.ChatSession;
import com.ruoyi.wechat.datasource.FetchQuery;
import com.ruoyi.wechat.datasource.RawMessage;
import com.ruoyi.wechat.datasource.SourceStatus;
import com.ruoyi.wechat.datasource.WechatDataSource;
import com.ruoyi.wechat.datasource.WechatDataSourceException;
import com.ruoyi.wechat.identity.ChatIdentity;

/**
 * 文本数据源 —— 聊天分析功能的数据来源：用户粘贴的聊天记录文本。
 *
 * <p>工作方式：{@link #submit} 把一段粘贴文本解析成消息并暂存在内存里，
 * 随后归档服务按普通数据源的路径 {@link #fetchMessages} 取数落库。
 * 也就是说，解析之外的所有环节（清洗、映射确认、去重归档、再展示）都与数据来源无关。
 *
 * <p>几个刻意的取舍：
 * <ul>
 *   <li><b>会话标识由会话名派生</b>（{@value #USERNAME_PREFIX} + 会话名的 MD5）。
 *       名字相同就落到同一个会话，于是"再贴一段同一段对话"是幂等追加，
 *       而不会在左侧堆出一串同名会话。</li>
 *   <li><b>文本只留在内存</b>，不落盘、不进日志：聊天记录属于敏感内容，
 *       落库展示已经足够，没必要再多存一份原文。代价是应用重启后
 *       没法对老会话再"同步记录"，此时归档内容照常可读。</li>
 *   <li><b>不做说话人推断</b>。粘贴文本里"我"的昵称无从得知，
 *       猜错会把双方认反，因此一律交给用户在界面上确认映射。</li>
 * </ul>
 */
public class TextWechatDataSource implements WechatDataSource
{
    /** 数据源标识 */
    public static final String MODE = "text";

    /** 会话标识前缀：与真实微信 wxid 区分开，避免和历史归档数据串号 */
    public static final String USERNAME_PREFIX = "text:";

    /** 单次粘贴的字符上限，防止误贴超大文件把内存和事务撑爆 */
    public static final int MAX_TEXT_LENGTH = 200_000;

    /** 自动识别不出会话名时的兜底名 */
    private static final String FALLBACK_NAME = "文本导入";

    /** 会话名长度上限（归档表该列 128，留足余量） */
    private static final int NAME_MAX_LENGTH = 60;

    /**
     * 常见的「这就是我」写法。
     *
     * <p>只用于给会话起名时避开自己的昵称，<b>不</b>用于角色映射 ——
     * 映射必须由用户确认。
     */
    private static final List<String> SELF_HINTS = List.of("me", "我", "自己");

    private final ChatTextParser parser = new ChatTextParser();

    /** 最近一次提交的解析结果，按会话标识索引 */
    private final Map<String, List<RawMessage>> buffered = new ConcurrentHashMap<>();

    /** 提交过的会话摘要 */
    private final Map<String, ChatSession> submitted = new ConcurrentHashMap<>();

    /**
     * 接收一段粘贴文本。
     *
     * @param chatName 用户填写的会话名，留空则自动识别
     * @param text     粘贴的原文
     * @return 可交给归档服务继续处理的会话摘要
     * @throws WechatDataSourceException 文本为空、过长或解析不出消息
     */
    public ChatSession submit(String chatName, String text)
    {
        if (text == null || text.isBlank())
        {
            throw new WechatDataSourceException("没有可解析的文本，请先粘贴聊天记录");
        }
        if (text.length() > MAX_TEXT_LENGTH)
        {
            throw new WechatDataSourceException("粘贴的文本过长："
                    + text.length() + " 字符，上限 " + MAX_TEXT_LENGTH + " 字符",
                    "请分批粘贴，每次只贴一段聊天记录");
        }

        List<RawMessage> messages = parser.parse(text);
        if (messages.isEmpty())
        {
            throw new WechatDataSourceException("未能从文本中解析出任何消息",
                    "每 3 行构成一条消息：第 1 行昵称、第 2 行时间（如 2026-04-01 19:02）、第 3 行消息内容");
        }

        String displayName = deriveDisplayName(chatName, messages);
        String username = USERNAME_PREFIX
                + DigestUtils.md5DigestAsHex(displayName.getBytes(StandardCharsets.UTF_8));
        boolean group = distinctSenders(messages) > 2;
        RawMessage last = messages.get(messages.size() - 1);

        // 文本源没有"未读"概念，unread 固定为 0
        ChatSession session = new ChatSession(username, displayName, group, 0,
                truncate(last.content(), 40), last.time(), null);

        buffered.put(username, List.copyOf(messages));
        submitted.put(username, session);
        return session;
    }

    @Override
    public String mode()
    {
        return MODE;
    }

    @Override
    public SourceStatus probe()
    {
        return SourceStatus.ok(MODE, "文本数据源：粘贴聊天记录即可导入，无需任何外部依赖");
    }

    @Override
    public List<ChatSession> listSessions(int limit)
    {
        if (limit <= 0)
        {
            return List.of();
        }
        List<ChatSession> all = new ArrayList<>(submitted.values());
        all.sort(Comparator.comparing(ChatSession::lastTime,
                Comparator.nullsLast(Comparator.<String>reverseOrder())));
        return all.size() > limit ? new ArrayList<>(all.subList(0, limit)) : all;
    }

    /**
     * 取出某个会话最近一次粘贴的解析结果。
     *
     * <p>归档服务据此落库：重复同步同一段文本是幂等的（归档表按
     * 「会话 + 时间 + 发送者 + 内容摘要」去重）。
     */
    @Override
    public List<RawMessage> fetchMessages(FetchQuery query)
    {
        if (query == null || query.chatName() == null || query.chatName().isBlank())
        {
            throw new WechatDataSourceException("聊天名不能为空");
        }
        List<RawMessage> messages = buffered.get(query.chatName());
        if (messages == null)
        {
            // 重启后内存里的原文没了，但已归档的内容照常可读
            throw new WechatDataSourceException("该会话没有可再次解析的文本",
                    "粘贴的原文只保留在本次运行的内存中，重新粘贴一次即可继续导入");
        }
        return messages;
    }

    /**
     * 得出会话名：优先用用户填写的，否则按文本内容自动识别。
     */
    private String deriveDisplayName(String chatName, List<RawMessage> messages)
    {
        if (chatName != null && !chatName.isBlank())
        {
            return truncate(chatName.trim(), NAME_MAX_LENGTH);
        }

        Map<String, Integer> counts = ChatIdentity.countSenders(messages);
        if (counts.isEmpty())
        {
            return FALLBACK_NAME;
        }
        if (counts.size() == 1)
        {
            return truncate(counts.keySet().iterator().next(), NAME_MAX_LENGTH);
        }

        // 私聊且其中一个标签明显是"我"时，另一个就是会话名
        String selfHint = findSelfHint(counts);
        if (selfHint != null && counts.size() == 2)
        {
            for (String label : counts.keySet())
            {
                if (!label.equals(selfHint))
                {
                    return truncate(label, NAME_MAX_LENGTH);
                }
            }
        }

        // 兜底：取发言最多的昵称；条数相同时取最先出现的
        return truncate(counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(FALLBACK_NAME), NAME_MAX_LENGTH);
    }

    private String findSelfHint(Map<String, Integer> counts)
    {
        for (String label : counts.keySet())
        {
            if (SELF_HINTS.contains(label.trim().toLowerCase(Locale.ROOT)))
            {
                return label;
            }
        }
        return null;
    }

    private int distinctSenders(List<RawMessage> messages)
    {
        Map<String, Boolean> senders = new LinkedHashMap<>();
        for (RawMessage m : messages)
        {
            if (m.hasSender())
            {
                senders.put(m.sender(), Boolean.TRUE);
            }
        }
        return senders.size();
    }

    private String truncate(String s, int max)
    {
        if (s == null || s.isBlank())
        {
            return FALLBACK_NAME;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
