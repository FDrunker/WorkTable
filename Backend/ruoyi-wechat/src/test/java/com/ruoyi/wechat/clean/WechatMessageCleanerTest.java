package com.ruoyi.wechat.clean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ruoyi.wechat.datasource.RawMessage;
import com.ruoyi.wechat.identity.ChatIdentity;

/**
 * {@link WechatMessageCleaner} 单元测试。
 *
 * <p>清洗链路是"降低 token 成本"与"保留分析所需行为信号"之间的平衡点，
 * 两条边界都容易被改坏：要么把该留的信号删了，要么预算没生效把上下文撑爆。
 */
class WechatMessageCleanerTest
{
    private final WechatMessageCleaner cleaner = new WechatMessageCleaner(null);

    /** 默认参数：开降噪、开脱敏、折叠阈值 5、预算 40000 */
    private CleanResult clean(List<RawMessage> messages, ChatIdentity identity, boolean group)
    {
        return cleaner.clean(messages, identity, group, true, 5, true, 40000);
    }

    private ChatIdentity identity()
    {
        return ChatIdentity.confirmed("wxid_me", List.of("me"), List.of("小美"));
    }

    private List<RawMessage> msgs(RawMessage... items)
    {
        List<RawMessage> list = new ArrayList<>();
        for (int i = 0; i < items.length; i++)
        {
            list.add(items[i]);
        }
        return list;
    }

    @Test
    @DisplayName("降噪：表情、通话、撤回、系统提示被丢弃且计入 droppedCount")
    void shouldDropNoiseMessages()
    {
        List<RawMessage> raw = msgs(
                new RawMessage(0, "2026-04-01 10:00", "me", "在吗"),
                new RawMessage(1, "2026-04-01 10:01", "小美", "[表情]"),
                new RawMessage(2, "2026-04-01 10:02", "小美", "对方已取消通话"),
                new RawMessage(3, "2026-04-01 10:03", "小美", "你撤回了一条消息"),
                new RawMessage(4, "2026-04-01 10:04", null, "你已添加了小美，现在可以开始聊天了"),
                new RawMessage(5, "2026-04-01 10:05", "小美", "。。。"));

        CleanResult result = clean(raw, identity(), false);

        assertEquals(1, result.size(), "只应剩最初那条文本");
        assertEquals(5, result.droppedCount());
        assertEquals("在吗", result.messages().get(0).content());
    }

    @Test
    @DisplayName("媒体占位保留但标注为 MEDIA，因为它是'发生过一次互动'的证据")
    void shouldKeepMediaAsEvidence()
    {
        List<RawMessage> raw = msgs(new RawMessage(0, "2026-04-01 10:00", "小美", "[图片]"));

        CleanResult result = clean(raw, identity(), false);

        assertEquals(1, result.size());
        assertEquals(CleanedMessage.Kind.MEDIA, result.messages().get(0).kind());
        assertFalse(result.messages().get(0).kind().displayOnly());
    }

    @Test
    @DisplayName("语音与动画表情保留在归档里（界面上要看得见），但标记为只展示不分析")
    void shouldKeepVoiceAndStickerAsDisplayOnly()
    {
        List<RawMessage> raw = msgs(
                new RawMessage(0, "2026-04-01 10:00", "小美", "[语音]"),
                new RawMessage(1, "2026-04-01 10:01", "me", "[动画表情]"),
                new RawMessage(2, "2026-04-01 10:02", "小美", "在的"));

        CleanResult result = clean(raw, identity(), false);

        assertEquals(3, result.size(), "语音与动画表情不能被降噪丢掉");
        assertEquals(0, result.droppedCount());
        assertEquals(CleanedMessage.Kind.VOICE, result.messages().get(0).kind());
        assertEquals(CleanedMessage.Kind.STICKER, result.messages().get(1).kind());
        assertTrue(result.messages().get(0).kind().displayOnly());
        assertTrue(result.messages().get(1).kind().displayOnly());
        assertEquals(CleanedMessage.Kind.TEXT, result.messages().get(2).kind());
    }

    @Test
    @DisplayName("静态 [表情] 仍然降噪：只有语音与动画表情要求保留展示")
    void shouldStillDropPlainEmoji()
    {
        List<RawMessage> raw = msgs(new RawMessage(0, "2026-04-01 10:00", "小美", "[表情]"));

        CleanResult result = clean(raw, identity(), false);

        assertEquals(0, result.size());
        assertEquals(1, result.droppedCount());
    }

    @Test
    @DisplayName("脱敏：手机号被打码，且不误伤普通数字")
    void shouldMaskPhoneNumberButNotPlainNumbers()
    {
        List<RawMessage> raw = msgs(
                new RawMessage(0, "2026-04-01 10:00", "小美", "我手机 13812345678 你记一下"),
                new RawMessage(1, "2026-04-01 10:01", "me", "预算 1234 万"));

        CleanResult result = clean(raw, identity(), false);

        assertEquals(1, result.maskedCount(), "只有含手机号的那条应被脱敏");
        assertTrue(result.messages().get(0).content().contains("138****5678"),
                "手机号中间四位应打码，实际：" + result.messages().get(0).content());
        assertFalse(result.messages().get(0).content().contains("13812345678"));
        // 普通数字不能被误伤
        assertTrue(result.messages().get(1).content().contains("1234"));
    }

    @Test
    @DisplayName("折叠：连续刷屏合并为一条，但必须保留'连续发了 N 条'这一行为信号")
    void shouldCollapseRepeatedMessagesButKeepSignal()
    {
        List<RawMessage> raw = new ArrayList<>();
        for (int i = 0; i < 6; i++)
        {
            raw.add(new RawMessage(i, "2026-04-01 10:0" + i, "小美", "第" + i + "条"));
        }

        CleanResult result = clean(raw, identity(), false);

        assertEquals(1, result.size(), "6 条连续发言应折叠成 1 条");
        assertEquals(5, result.collapsedCount());
        String content = result.messages().get(0).content();
        assertTrue(content.contains("连续发送 6 条"),
                "必须保留连续条数这一互惠/投入信号，实际：" + content);
    }

    @Test
    @DisplayName("折叠：未达阈值时不合并")
    void shouldNotCollapseBelowThreshold()
    {
        List<RawMessage> raw = msgs(
                new RawMessage(0, "2026-04-01 10:00", "小美", "一"),
                new RawMessage(1, "2026-04-01 10:01", "小美", "二"));

        CleanResult result = clean(raw, identity(), false);

        assertEquals(2, result.size());
        assertEquals(0, result.collapsedCount());
    }

    @Test
    @DisplayName("角色映射：me 归为 SELF，对方昵称归为 TARGET，并统计条数")
    void shouldResolveRolesAndCount()
    {
        List<RawMessage> raw = msgs(
                new RawMessage(0, "2026-04-01 10:00", "me", "在吗"),
                new RawMessage(1, "2026-04-01 10:01", "小美", "在的"),
                new RawMessage(2, "2026-04-01 10:02", "小美", "什么事"));

        CleanResult result = clean(raw, identity(), false);

        assertEquals(1, result.roleCounts().get(CleanedMessage.Role.SELF));
        assertEquals(2, result.roleCounts().get(CleanedMessage.Role.TARGET));
    }

    @Test
    @DisplayName("预算裁剪：超预算时丢最旧的、保最近的，并明确标注上下文不完整")
    void shouldTruncateOldestAndFlagIncompleteness()
    {
        List<RawMessage> raw = new ArrayList<>();
        for (int i = 0; i < 40; i++)
        {
            raw.add(new RawMessage(i, "2026-04-01 10:" + String.format("%02d", i),
                    "小美", "这是第 " + i + " 条比较长的消息内容用于把预算撑爆"));
        }

        // 预算设得很小以触发截断
        CleanResult result = cleaner.clean(raw, identity(), false, true, 100, true, 300);

        assertTrue(result.truncated(), "应标记为已截断");
        assertNotNull(result.truncatedNote());
        assertTrue(result.truncatedNote().contains("未提供"), "截断说明必须提示上下文不完整");
        assertTrue(result.size() < 40, "应确实丢弃了部分消息");
        // 保留的必须是最近的：最后一条内容应在结果里
        String lastContent = result.messages().get(result.size() - 1).content();
        assertTrue(lastContent.contains("第 39 条"), "应保留最新消息，实际末尾：" + lastContent);
    }

    @Test
    @DisplayName("关闭降噪时表情等消息照常保留（配置可回退）")
    void shouldKeepNoiseWhenDropDisabled()
    {
        List<RawMessage> raw = msgs(new RawMessage(0, "2026-04-01 10:00", "小美", "[表情]"));

        CleanResult result = cleaner.clean(raw, identity(), false, false, 5, true, 40000);

        assertEquals(1, result.size());
        assertEquals(0, result.droppedCount());
    }
}
