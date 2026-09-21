package com.ruoyi.wechat.clean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ruoyi.wechat.config.WechatProperties;
import com.ruoyi.wechat.datasource.RawMessage;
import com.ruoyi.wechat.identity.ChatIdentity;

/**
 * {@link ChatContextRenderer} 单元测试。
 *
 * <p>核心约束只有一条：{@code [语音]} 与 {@code [动画表情]} 可以出现在聊天记录界面，
 * 但<b>绝不能</b>进入分析上下文。
 */
class ChatContextRendererTest
{
    private final WechatMessageCleaner cleaner = new WechatMessageCleaner(new WechatProperties());

    private final ChatContextRenderer renderer = new ChatContextRenderer();

    private ChatIdentity identity()
    {
        return ChatIdentity.confirmed("me", List.of("me"), List.of("小美"));
    }

    private CleanResult clean(RawMessage... items)
    {
        List<RawMessage> raw = new ArrayList<>(List.of(items));
        return cleaner.clean(raw, identity(), false, true, 5, true, 40000);
    }

    @Test
    @DisplayName("渲染上下文时跳过语音与动画表情，其余消息照常渲染")
    void shouldSkipDisplayOnlyMessages()
    {
        CleanResult result = clean(
                new RawMessage(0, "2026-04-01 19:00", "小美", "在吗"),
                new RawMessage(1, "2026-04-01 19:01", "小美", "[语音]"),
                new RawMessage(2, "2026-04-01 19:02", "me", "[动画表情]"),
                new RawMessage(3, "2026-04-01 19:03", "小美", "在的"));

        String context = renderer.render(result.messages());

        assertTrue(context.contains("在吗"));
        assertTrue(context.contains("在的"));
        assertFalse(context.contains("[语音]"), "语音不得进入分析上下文");
        assertFalse(context.contains("[动画表情]"), "动画表情不得进入分析上下文");
    }

    @Test
    @DisplayName("图片等媒体仍作为「发生过一次互动」的证据进入上下文")
    void shouldKeepMediaAsEvidence()
    {
        CleanResult result = clean(new RawMessage(0, "2026-04-01 19:00", "小美", "[图片]"));

        String context = renderer.render(result.messages());

        assertTrue(context.contains("[图片]"));
        assertTrue(context.contains("媒体内容"));
    }

    @Test
    @DisplayName("统计里说明有多少条只展示的消息，避免模型把总条数当成分析样本数")
    void shouldReportDisplayOnlyCountInStats()
    {
        CleanResult result = clean(
                new RawMessage(0, "2026-04-01 19:00", "小美", "[语音]"),
                new RawMessage(1, "2026-04-01 19:01", "me", "[动画表情]"));

        String stats = renderer.renderStats(result);

        assertTrue(stats.contains("总消息数：2"));
        assertTrue(stats.contains("2 条是语音/动画表情占位"), "统计里必须点明这两条只展示、未参与分析：" + stats);
    }

    @Test
    @DisplayName("没有只展示的消息时不出现多余说明")
    void shouldNotReportDisplayOnlyWhenAbsent()
    {
        CleanResult result = clean(new RawMessage(0, "2026-04-01 19:00", "小美", "在吗"));

        String stats = renderer.renderStats(result);

        assertEquals(-1, stats.indexOf("语音/动画表情占位"));
    }
}
