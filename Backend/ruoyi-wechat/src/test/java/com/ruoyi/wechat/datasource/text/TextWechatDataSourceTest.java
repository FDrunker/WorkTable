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
import com.ruoyi.wechat.domain.TextImportPreview;

/**
 * {@link TextWechatDataSource} 的预览、昵称确认、会话归属与日期排序测试。
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
    @DisplayName("预览返回昵称候选，确认自己后按对方昵称归档")
    void shouldPreviewThenCommit()
    {
        TextImportPreview preview = source.preview(1L, CHAT);

        assertEquals(2, preview.senders().size());
        assertEquals("小美", preview.senders().get(0).label());
        assertEquals("我", preview.senders().get(1).label());
        assertEquals(2, preview.messageCount());

        TextWechatDataSource.CommitResult result = source.commit(1L, preview.importId(), "我");

        assertEquals("小美", result.session().displayName());
        assertEquals(List.of("我"), result.selfLabels());
        assertEquals(List.of("小美"), result.targetLabels());
        assertTrue(result.session().username().startsWith(TextWechatDataSource.USERNAME_PREFIX));

        List<RawMessage> fetched = source.fetchMessages(FetchQuery.of(result.session().username(), 500));
        assertEquals(2, fetched.size());
        assertEquals("在吗", fetched.get(0).content());
    }

    @Test
    @DisplayName("同一对方昵称归入同一个会话，不受粘贴批次和文本先后影响")
    void shouldKeepConversationStableByTargetName()
    {
        String later = """
                我
                2026-04-02 09:00
                早

                小美
                2026-04-02 09:01
                早呀
                """;

        TextImportPreview firstPreview = source.preview(1L, CHAT);
        TextWechatDataSource.CommitResult first = source.commit(1L, firstPreview.importId(), "我");
        TextImportPreview secondPreview = source.preview(1L, later);
        TextWechatDataSource.CommitResult second = source.commit(1L, secondPreview.importId(), "我");

        assertEquals(first.session().username(), second.session().username());
        assertEquals("小美", second.session().displayName());
    }

    @Test
    @DisplayName("确认导入：可以选择已有会话作为归档目标")
    void shouldCommitToSelectedSession()
    {
        TextImportPreview preview = source.preview(1L, CHAT);
        ChatSession selected = new ChatSession("text:existing", "小美（同事）", true, 0,
                null, null, null);

        TextWechatDataSource.CommitResult result = source.commit(1L, preview.importId(), "我", selected);

        assertEquals("text:existing", result.session().username());
        assertEquals("小美（同事）", result.session().displayName());
        assertTrue(result.session().group());
        assertEquals(2, source.fetchMessages(FetchQuery.of("text:existing", 10)).size());
    }

    @Test
    @DisplayName("消息按文本日期升序排列，而不是按粘贴顺序")
    void shouldSortMessagesByDate()
    {
        String outOfOrder = """
                小美
                2026-04-03 20:00
                第三条

                我
                2026-04-01 09:00
                第一条

                小美
                2026-04-02 12:00
                第二条
                """;

        TextImportPreview preview = source.preview(1L, outOfOrder);
        TextWechatDataSource.CommitResult result = source.commit(1L, preview.importId(), "我");
        List<RawMessage> fetched = source.fetchMessages(FetchQuery.of(result.session().username(), 10));

        assertEquals(List.of("2026-04-01 09:00", "2026-04-02 12:00", "2026-04-03 20:00"),
                fetched.stream().map(RawMessage::time).toList());
        assertEquals("第三条", result.session().lastMessage());
    }

    @Test
    @DisplayName("超过两个说话人并排除自己后判定为群聊")
    void shouldDetectGroupChat()
    {
        String text = """
                小美
                2026-04-01 19:02
                一

                老王
                2026-04-01 19:03
                二

                我
                2026-04-01 19:04
                三
                """;

        TextImportPreview preview = source.preview(1L, text);
        assertTrue(source.commit(1L, preview.importId(), "我").session().group());
    }

    @Test
    @DisplayName("文本为空、过长或解析不出消息时给出可照着做的错误")
    void shouldRejectBadInput()
    {
        WechatDataSourceException blank = assertThrows(WechatDataSourceException.class,
                () -> source.preview(1L, "   "));
        assertTrue(blank.getMessage().contains("没有可解析的文本"));

        WechatDataSourceException unparsable = assertThrows(WechatDataSourceException.class,
                () -> source.preview(1L, "这是一段没有任何时间行的文本"));
        assertTrue(unparsable.getMessage().contains("未能从文本中解析出任何消息"));
        assertTrue(unparsable.getSuggestion().contains("昵称"));

        String tooLong = "x".repeat(TextWechatDataSource.MAX_TEXT_LENGTH + 1);
        assertThrows(WechatDataSourceException.class, () -> source.preview(1L, tooLong));
    }

    @Test
    @DisplayName("预览只能由创建者提交，且确认后不能再次提交")
    void shouldProtectPreviewBuffer()
    {
        TextImportPreview preview = source.preview(1L, CHAT);

        assertThrows(IllegalArgumentException.class,
                () -> source.commit(2L, preview.importId(), "我"));

        source.commit(1L, preview.importId(), "我");
        assertThrows(WechatDataSourceException.class,
                () -> source.commit(1L, preview.importId(), "我"));
    }

    @Test
    @DisplayName("未确认的预览不能直接取消息，确认后可以读取")
    void shouldFailWhenTextIsNotCommitted()
    {
        TextImportPreview preview = source.preview(1L, CHAT);
        assertThrows(WechatDataSourceException.class,
                () -> source.fetchMessages(FetchQuery.of(preview.importId(), 10)));

        TextWechatDataSource.CommitResult result = source.commit(1L, preview.importId(), "我");
        assertFalse(source.fetchMessages(FetchQuery.of(result.session().username(), 10)).isEmpty());
        assertThrows(WechatDataSourceException.class, () -> source.fetchMessages(null));
    }

    @Test
    @DisplayName("会话列表按最后消息时间倒序，并受 limit 约束")
    void shouldListSessionsNewestFirst()
    {
        TextImportPreview early = source.preview(1L, """
                小美
                2026-04-01 09:00
                早

                我
                2026-04-01 09:01
                早呀
                """);
        source.commit(1L, early.importId(), "我");

        TextImportPreview late = source.preview(1L, """
                老王
                2026-04-02 21:00
                晚

                我
                2026-04-02 21:01
                收到
                """);
        source.commit(1L, late.importId(), "我");

        List<ChatSession> all = source.listSessions(10);
        assertEquals(2, all.size());
        assertEquals("老王", all.get(0).displayName());
        assertEquals(1, source.listSessions(1).size());
        assertTrue(source.listSessions(0).isEmpty());
        assertNotEquals(all.get(0).username(), all.get(1).username());
    }
}
