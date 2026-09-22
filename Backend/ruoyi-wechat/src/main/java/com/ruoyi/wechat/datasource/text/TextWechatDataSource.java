package com.ruoyi.wechat.datasource.text;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.util.DigestUtils;

import com.ruoyi.wechat.datasource.ChatSession;
import com.ruoyi.wechat.datasource.FetchQuery;
import com.ruoyi.wechat.datasource.RawMessage;
import com.ruoyi.wechat.datasource.SourceStatus;
import com.ruoyi.wechat.datasource.WechatDataSource;
import com.ruoyi.wechat.datasource.WechatDataSourceException;
import com.ruoyi.wechat.domain.TextImportPreview;
import com.ruoyi.wechat.domain.TextImportPreview.SenderOption;
import com.ruoyi.wechat.identity.ChatIdentity;

/**
 * 文本数据源 —— 用户粘贴聊天记录后，先预览并选择“我是谁”，再归档到对方昵称对应的会话。
 *
 * <p>预览阶段只把解析结果暂存在内存中，不写数据库。确认自己后，会话标识由
 * “除自己外的昵称”生成，因此同一对象的多次导入会落到同一个会话。
 * 消息在预览时按时间排序，粘贴顺序错误也不会影响归档顺序。
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
     * 常见的「这就是我」写法，仅用于预览时的建议名称，不参与角色写入。
     */
    private static final List<String> SELF_HINTS = List.of("me", "我", "自己");

    private final ChatTextParser parser = new ChatTextParser();

    /** 已预览但尚未确认“我是谁”的文本，按临时导入标识索引 */
    private final Map<String, PreviewBuffer> buffered = new ConcurrentHashMap<>();

    /** 已完成确认的文本，按稳定会话标识索引 */
    private final Map<String, List<RawMessage>> committedMessages = new ConcurrentHashMap<>();

    /** 提交过的会话摘要 */
    private final Map<String, ChatSession> submitted = new ConcurrentHashMap<>();

    /**
     * 解析粘贴文本并返回发送者候选，不写数据库。
     */
    public TextImportPreview preview(Long userId, String text)
    {
        if (userId == null)
        {
            throw new IllegalArgumentException("用户不能为空");
        }

        List<RawMessage> messages = parseAndSort(text);
        Map<String, Integer> counts = ChatIdentity.countSenders(messages);
        if (counts.isEmpty())
        {
            throw new WechatDataSourceException("文本中没有可识别的聊天昵称",
                    "每条消息需要按“昵称 / 时间 / 内容”三行格式粘贴");
        }

        String importId = USERNAME_PREFIX + "preview-" + UUID.randomUUID();
        buffered.put(importId, new PreviewBuffer(userId, List.copyOf(messages)));

        List<SenderOption> senders = counts.entrySet().stream()
                .map(entry -> new SenderOption(entry.getKey(), entry.getValue()))
                .toList();
        RawMessage first = messages.get(0);
        RawMessage last = messages.get(messages.size() - 1);

        return new TextImportPreview(importId, deriveDisplayName(messages), counts.size() > 2,
                messages.size(), first.time(), last.time(), senders);
    }

    /**
     * 确认“我”的昵称并生成稳定会话。
     *
     * <p>除自己以外的昵称共同组成会话名；私聊通常会得到唯一对方昵称。
     */
    public CommitResult commit(Long userId, String importId, String selfLabel)
    {
        if (importId == null || importId.isBlank())
        {
            throw new IllegalArgumentException("导入标识不能为空");
        }

        PreviewBuffer buffer = buffered.get(importId);
        if (buffer == null)
        {
            throw new WechatDataSourceException("本次导入预览已失效",
                    "请重新粘贴聊天记录后再选择昵称");
        }
        if (!Objects.equals(buffer.userId(), userId))
        {
            throw new IllegalArgumentException("无权提交该次文本导入");
        }

        String selectedSelf = selfLabel == null ? "" : selfLabel.trim();
        Map<String, Integer> counts = ChatIdentity.countSenders(buffer.messages());
        if (selectedSelf.isEmpty() || !counts.containsKey(selectedSelf))
        {
            throw new IllegalArgumentException("请选择聊天记录中实际出现的自己的昵称");
        }

        List<String> targetLabels = new ArrayList<>();
        for (String label : counts.keySet())
        {
            if (!selectedSelf.equals(label))
            {
                targetLabels.add(label);
            }
        }
        if (targetLabels.isEmpty())
        {
            throw new IllegalArgumentException("聊天记录里没有识别到对方昵称");
        }

        String displayName = truncate(String.join("、", targetLabels), NAME_MAX_LENGTH);
        String username = usernameForDisplayName(displayName);
        boolean group = counts.size() > 2;
        RawMessage last = buffer.messages().get(buffer.messages().size() - 1);
        ChatSession session = new ChatSession(username, displayName, group, 0,
                truncate(last.content(), 40), last.time(), null);

        buffered.remove(importId);
        committedMessages.put(username, buffer.messages());
        submitted.put(username, session);

        return new CommitResult(session, List.of(selectedSelf), List.copyOf(targetLabels));
    }

    @Override
    public String mode()
    {
        return MODE;
    }

    @Override
    public SourceStatus probe()
    {
        return SourceStatus.ok(MODE, "文本数据源：粘贴聊天记录后选择自己的昵称即可导入");
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
     * 取出已经确认过的会话文本，归档服务据此落库。
     */
    @Override
    public List<RawMessage> fetchMessages(FetchQuery query)
    {
        if (query == null || query.chatName() == null || query.chatName().isBlank())
        {
            throw new WechatDataSourceException("聊天名不能为空");
        }
        List<RawMessage> messages = committedMessages.get(query.chatName());
        if (messages == null)
        {
            throw new WechatDataSourceException("该会话没有可再次解析的文本",
                    "粘贴的原文只保留在本次运行的内存中，重新粘贴一次即可继续导入");
        }
        return messages;
    }

    /**
     * 由最终会话名生成稳定标识。
     */
    public static String usernameForDisplayName(String displayName)
    {
        return USERNAME_PREFIX
                + DigestUtils.md5DigestAsHex(displayName.getBytes(StandardCharsets.UTF_8));
    }

    private List<RawMessage> parseAndSort(String text)
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

        List<RawMessage> messages = new ArrayList<>(parser.parse(text));
        if (messages.isEmpty())
        {
            throw new WechatDataSourceException("未能从文本中解析出任何消息",
                    "每 3 行构成一条消息：第 1 行昵称、第 2 行时间（如 2026-04-01 19:02）、第 3 行消息内容");
        }
        messages.sort(Comparator.comparing(RawMessage::time).thenComparingInt(RawMessage::seq));
        return messages;
    }

    /**
     * 预览阶段只给建议名称，最终名称仍以用户选择后的对方昵称为准。
     */
    private String deriveDisplayName(List<RawMessage> messages)
    {
        Map<String, Integer> counts = ChatIdentity.countSenders(messages);
        if (counts.isEmpty())
        {
            return FALLBACK_NAME;
        }
        if (counts.size() == 1)
        {
            return truncate(counts.keySet().iterator().next(), NAME_MAX_LENGTH);
        }

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

    private String truncate(String s, int max)
    {
        if (s == null || s.isBlank())
        {
            return FALLBACK_NAME;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private record PreviewBuffer(Long userId, List<RawMessage> messages)
    {
    }

    /**
     * 文本确认结果。
     */
    public record CommitResult(ChatSession session, List<String> selfLabels, List<String> targetLabels)
    {
    }
}
