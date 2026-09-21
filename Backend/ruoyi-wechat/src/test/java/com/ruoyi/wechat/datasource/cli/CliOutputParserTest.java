package com.ruoyi.wechat.datasource.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ruoyi.wechat.datasource.ChatSession;
import com.ruoyi.wechat.datasource.RawMessage;
import com.ruoyi.wechat.datasource.WechatDataSourceException;

/**
 * {@link CliOutputParser} 单元测试。
 *
 * <p>测试数据直接照抄 wechat-cli 源码里
 * {@code "[{time_str}] {sender_label}: {text}"} 的输出格式。
 * 这段解析一旦出错，所有下游分析都会建立在错位的数据上，所以覆盖得细一些。
 */
class CliOutputParserTest
{
    @Test
    @DisplayName("解析 history 输出：带发送者与不带发送者的行都能正确拆开")
    void parseHistoryShouldSplitSenderAndContent()
    {
        String stdout = """
                {
                  "chat": "小美", "username": "wxid_x", "is_group": false,
                  "count": 3, "offset": 0, "limit": 50,
                  "messages": [
                    "[2026-04-01 10:23] me: 在吗",
                    "[2026-04-01 10:24] 小美: 在的",
                    "[2026-04-01 10:25] 系统提示"
                  ],
                  "failures": null
                }
                """;

        List<RawMessage> messages = CliOutputParser.parseHistory(stdout);

        assertEquals(3, messages.size());

        assertEquals("me", messages.get(0).sender());
        assertEquals("在吗", messages.get(0).content());
        assertEquals("2026-04-01 10:23", messages.get(0).time());

        assertEquals("小美", messages.get(1).sender());
        assertEquals("在的", messages.get(1).content());

        // 没有发送者前缀的行：sender 为 null，内容完整保留
        assertNull(messages.get(2).sender());
        assertEquals("系统提示", messages.get(2).content());
    }

    @Test
    @DisplayName("正文里的冒号不会被误判成发送者分隔符")
    void shouldNotSplitOnColonInsideContent()
    {
        // 关键点：真实格式是 "sender: content"，冒号后必须跟一个空格。
        // 时间戳里的冒号、以及追问里的冒号都不能被切。
        RawMessage msg = CliOutputParser.parseLine("[2026-04-01 10:23] me: 时间点：比预期晚两周", 0);

        assertNotNull(msg);
        assertEquals("me", msg.sender());
        assertEquals("时间点：比预期晚两周", msg.content());
    }

    @Test
    @DisplayName("解析为空的消息列表")
    void parseHistoryShouldHandleEmptyMessages()
    {
        String stdout = "{\"chat\":\"小美\",\"messages\":[]}";
        assertTrue(CliOutputParser.parseHistory(stdout).isEmpty());
    }

    @Test
    @DisplayName("混有日志行时仍能截取出 JSON")
    void parseHistoryShouldTolerateSurroundingNoise()
    {
        String stdout = "npm warn something\n{\"chat\":\"小美\",\"messages\":[\"[2026-04-01 10:23] me: hi\"]}\n";

        List<RawMessage> messages = CliOutputParser.parseHistory(stdout);

        assertEquals(1, messages.size());
        assertEquals("hi", messages.get(0).content());
    }

    @Test
    @DisplayName("输出中没有 JSON 时抛出带修复建议的异常")
    void parseHistoryShouldFailLoudlyOnGarbage()
    {
        WechatDataSourceException e = assertThrows(WechatDataSourceException.class,
                () -> CliOutputParser.parseHistory("wechat-cli: binary not found for win32-x64"));

        assertTrue(e.getMessage().contains("没有找到 JSON"), "异常信息应说明找不到 JSON");
        assertNotNull(e.getSuggestion(), "应附带排查建议");
    }

    @Test
    @DisplayName("输出按时间升序归一化，不依赖数据源内部顺序")
    void parseHistoryShouldSortAscending()
    {
        String stdout = """
                {"messages":[
                  "[2026-04-03 09:00] 小美: 后一条",
                  "[2026-04-01 09:00] 小美: 前一条"
                ]}
                """;

        List<RawMessage> messages = CliOutputParser.parseHistory(stdout);

        assertEquals("前一条", messages.get(0).content());
        assertEquals("后一条", messages.get(1).content());
    }

    @Test
    @DisplayName("解析 sessions 输出")
    void parseSessionsShouldMapAllFields()
    {
        String stdout = """
                [{
                  "chat": "小美", "username": "wxid_x", "is_group": false,
                  "unread": 2, "last_message": "在吗", "msg_type": "文本",
                  "sender": "", "timestamp": 1774000000, "time": "04-01 10:23"
                }]
                """;

        List<ChatSession> sessions = CliOutputParser.parseSessions(stdout);

        assertEquals(1, sessions.size());
        ChatSession s = sessions.get(0);
        assertEquals("小美", s.displayName());
        assertEquals("wxid_x", s.username());
        assertEquals(2, s.unread());
        assertEquals("04-01 10:23", s.lastTime());
        // sessions 命令不返回"我"，应由 identity 层推断
        assertNull(s.selfUsername());
    }

    @Test
    @DisplayName("空白行与畸形行被安全忽略，不会产生半个消息")
    void parseLineShouldReturnNullForUnparsableInput()
    {
        assertNull(CliOutputParser.parseLine(null, 0));
        assertNull(CliOutputParser.parseLine("   ", 0));
        // 缺少方括号时间戳的行不符合格式
        assertNull(CliOutputParser.parseLine("这只是一句普通文本", 0));
    }
}
