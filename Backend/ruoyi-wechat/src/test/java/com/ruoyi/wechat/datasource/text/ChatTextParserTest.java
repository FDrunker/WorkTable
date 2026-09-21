package com.ruoyi.wechat.datasource.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ruoyi.wechat.datasource.RawMessage;

/**
 * {@link ChatTextParser} 单元测试。
 *
 * <p>解析层是"粘贴文本"这个数据来源的入口，格式容错与时间归一化都在这里，
 * 一旦出错表现为"消息少了几条"或"日期全乱"，属于最难从界面看出来的故障。
 */
class ChatTextParserTest
{
    private final ChatTextParser parser = new ChatTextParser();

    @Test
    @DisplayName("基本格式：每 3 行一条消息，昵称/时间/内容各归其位")
    void shouldParseThreeLineBlocks()
    {
        String text = """
                小美
                2026-04-01 19:02
                在吗？

                我
                2026-04-01 19:03
                在的
                """;

        List<RawMessage> messages = parser.parse(text);

        assertEquals(2, messages.size());
        assertEquals("小美", messages.get(0).sender());
        assertEquals("2026-04-01 19:02", messages.get(0).time());
        assertEquals("在吗？", messages.get(0).content());
        assertEquals("我", messages.get(1).sender());
        assertEquals("在的", messages.get(1).content());
    }

    @Test
    @DisplayName("多行内容整段归入同一条消息，不会在换行处断开")
    void shouldKeepMultilineContent()
    {
        String text = """
                小美
                2026-04-01 19:02
                第一行
                第二行

                我
                2026-04-01 19:03
                收到
                """;

        List<RawMessage> messages = parser.parse(text);

        assertEquals(2, messages.size());
        assertEquals("第一行\n第二行", messages.get(0).content());
        assertEquals("收到", messages.get(1).content());
    }

    @Test
    @DisplayName("[语音] 与 [动画表情] 在解析层被完整保留：它们要显示在聊天记录里")
    void shouldKeepVoiceAndStickerAsMessages()
    {
        String text = """
                小美
                2026-04-01 19:02
                [语音]

                我
                2026-04-01 19:03
                [动画表情]

                小美
                2026-04-01 19:04
                在的
                """;

        List<RawMessage> messages = parser.parse(text);

        assertEquals(3, messages.size(), "语音与动画表情必须各占一条消息，不能被解析层丢掉");
        assertEquals("[语音]", messages.get(0).content());
        assertEquals("[动画表情]", messages.get(1).content());
        assertEquals("在的", messages.get(2).content());
    }

    @Test
    @DisplayName("时间归一化：兼容斜杠、中文日期与省略年份的写法")
    void shouldNormalizeTimeFormats()
    {
        assertEquals("2026-04-01 19:02", parser.normalizeTime("2026/4/1 19:02"));
        assertEquals("2026-04-01 19:02", parser.normalizeTime("2026年4月1日 19:02"));
        assertEquals("2026-04-01 19:02:30", parser.normalizeTime("2026-4-1 19:02:30"));
        assertEquals("2026-04-01 09:05", parser.normalizeTime("2026-04-01 09:05"));

        String thisYear = String.valueOf(LocalDate.now().getYear());
        assertEquals(thisYear + "-04-01 10:23", parser.normalizeTime("04-01 10:23"));
        assertEquals(thisYear + "-04-01 10:23", parser.normalizeTime("4月1日 10:23"));
    }

    @Test
    @DisplayName("时间归一化：支持「下午7:02」这类时段前缀")
    void shouldHandleChinesePeriodPrefix()
    {
        assertEquals("2026-04-01 19:02", parser.normalizeTime("2026-04-01 下午7:02"));
        assertEquals("2026-04-01 08:30", parser.normalizeTime("2026-04-01 上午8:30"));
        assertEquals("2026-04-01 00:10", parser.normalizeTime("2026-04-01 凌晨12:10"));
    }

    @Test
    @DisplayName("时间归一化：非法时间返回 null，交由解析循环跳过该行")
    void shouldRejectInvalidTime()
    {
        assertNull(parser.normalizeTime("19:02"));
        assertNull(parser.normalizeTime("2026-13-40 19:02"));
        assertNull(parser.normalizeTime("2026-04-01 25:00"));
        assertNull(parser.normalizeTime("今天下午"));
        // 超长行不可能是时间行，长度上限把正文挡在外面
        assertNull(parser.normalizeTime("这是一条很长的正常消息内容，长度超过时间行上限时"
                + "不应该被当成时间，否则整段解析都会错位"));
    }

    @Test
    @DisplayName("容错：粘进来的标题行被跳过，不影响后续消息解析")
    void shouldSkipLinesThatDoNotStartABlock()
    {
        String text = """
                以下是小美和我的聊天记录
                小美
                2026-04-01 19:02
                在吗
                """;

        List<RawMessage> messages = parser.parse(text);

        assertEquals(1, messages.size());
        assertEquals("小美", messages.get(0).sender());
        assertEquals("在吗", messages.get(0).content());
    }

    @Test
    @DisplayName("空文本与纯空白文本解析出空列表")
    void shouldReturnEmptyForBlankText()
    {
        assertTrue(parser.parse(null).isEmpty());
        assertTrue(parser.parse("").isEmpty());
        assertTrue(parser.parse("   \n\n  \n").isEmpty());
    }
}
