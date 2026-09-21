package com.ruoyi.wechat.datasource.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ruoyi.wechat.datasource.ChatSession;
import com.ruoyi.wechat.datasource.FetchQuery;
import com.ruoyi.wechat.datasource.RawMessage;
import com.ruoyi.wechat.datasource.WechatDataSourceException;

/**
 * {@link TextWechatDataSource} 单元测试。
 *
 * <p>重点覆盖三件事：会话怎么命名、标识是否稳定（决定"再贴一次"是追加还是新建会话）、
 * 以及文本没了之后 {@code fetchMessages} 是否给出可读的失败原因。
 */
class TextWechatDataSourceTest
{
    private static final String CHAT = """
            小美
            2026-04-01 19:02
            在吗

            我
            2026-04-01 19:03
            在的
            """;

    private final TextWechatDataSource source = new TextWechatDataSource();

    @Test
    @DisplayName("数据源标识与状态：文本源无需外部依赖，始终就绪")
    void shouldReportReady()
    {
        assertEquals(TextWechatDataSource.MODE, source.mode());
        assertTrue(source.probe().ready());
        assertEquals(TextWechatDataSource.MODE, source.probe().mode());
    }

    @Test
    @DisplayName("提交文本：解析出消息、按会话名生成会话，归档服务再据此取数")
    void shouldSubmitAndServeMessages()
    {
        ChatSession session = source.submit("小美", CHAT);

        assertEquals("小美", session.displayName());
        assertFalse(session.group());
        assertEquals("2026-04-01 19:03", session.lastTime());
        assertEquals("在的", session.lastMessage());
        assertTrue(session.username().startsWith(TextWechatDataSource.USERNAME_PREFIX));

        List<RawMessage> fetched = source.fetchMessages(FetchQuery.of(session.username(), 500));
        assertEquals(2, fetched.size());
        assertEquals("在吗", fetched.get(0).content());
    }

    @Test
    @DisplayName("会话标识由会话名派生：再贴一次同一段对话会落到同一个会话")
    void shouldKeepUsernameStableForSameName()
    {
        ChatSession first = source.submit("小美", CHAT);
        ChatSession second = source.submit("小美", CHAT);

        assertEquals(first.username(), second.username(), "同名会话必须复用同一个标识，否则左侧会堆出一串同名会话");
        // 不同会话名必须是不同会话
        assertNotEquals(first.username(), source.submit("老王", CHAT).username());
    }

    @Test
    @DisplayName("会话名留空时自动识别：有 me 这类自我标签时取对方昵称")
    void shouldDeriveNameWithoutSelfHint()
    {
        String text = """
                me
                2026-04-01 19:02
                在吗

                小美
                2026-04-01 19:03
                在的
                """;

        assertEquals("小美", source.submit(null, text).displayName());
    }

    @Test
    @DisplayName("会话名留空时自动识别：没有自我标签时取发言最多的昵称")
    void shouldDeriveNameByMostFrequentSender()
    {
        String text = """
                阿强
                2026-04-01 19:02
                一

                阿强
                2026-04-01 19:03
                二

                小美
                2026-04-01 19:04
                三
                """;

        assertEquals("阿强", source.submit("  ", text).displayName());
    }

    @Test
    @DisplayName("超过两个说话人判定为群聊")
    void shouldDetectGroupChat()
    {
        String text = """
                小美
                2026-04-01 19:02
                一

                老王
                2026-04-01 19:03
                二

                阿强
                2026-04-01 19:04
                三
                """;

        assertTrue(source.submit("周末爬山群", text).group());
    }

    @Test
    @DisplayName("文本为空、过长或解析不出消息时给出可照着做的错误")
    void shouldRejectBadInput()
    {
        WechatDataSourceException blank = assertThrows(WechatDataSourceException.class,
                () -> source.submit("小美", "   "));
        assertTrue(blank.getMessage().contains("没有可解析的文本"));

        WechatDataSourceException unparsable = assertThrows(WechatDataSourceException.class,
                () -> source.submit("小美", "这是一段没有任何时间行的文本"));
        assertTrue(unparsable.getMessage().contains("未能从文本中解析出任何消息"));
        assertTrue(unparsable.getSuggestion().contains("昵称"));

        String tooLong = "x".repeat(TextWechatDataSource.MAX_TEXT_LENGTH + 1);
        assertThrows(WechatDataSourceException.class, () -> source.submit("小美", tooLong));
    }

    @Test
    @DisplayName("文本只在内存里：取不到原文时报错并提示重新粘贴，而不是抛空指针")
    void shouldFailWhenTextIsNotBuffered()
    {
        WechatDataSourceException e = assertThrows(WechatDataSourceException.class,
                () -> source.fetchMessages(FetchQuery.of("text:not-imported", 10)));
        assertTrue(e.getMessage().contains("没有可再次解析的文本"));
        assertTrue(e.getSuggestion().contains("重新粘贴"));

        assertThrows(WechatDataSourceException.class, () -> source.fetchMessages(null));
    }

    @Test
    @DisplayName("会话列表按最后消息时间倒序，并受 limit 约束")
    void shouldListSessionsNewestFirst()
    {
        source.submit("早会话", """
                小美
                2026-04-01 09:00
                早
                """);
        source.submit("晚会话", """
                小美
                2026-04-02 21:00
                晚
                """);

        List<ChatSession> all = source.listSessions(10);
        assertEquals(2, all.size());
        assertEquals("晚会话", all.get(0).displayName());
        assertEquals(1, source.listSessions(1).size());
        assertTrue(source.listSessions(0).isEmpty());
    }
}
