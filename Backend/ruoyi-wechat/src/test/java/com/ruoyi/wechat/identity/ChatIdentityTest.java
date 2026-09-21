package com.ruoyi.wechat.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ruoyi.wechat.clean.CleanedMessage;
import com.ruoyi.wechat.datasource.RawMessage;

/**
 * {@link ChatIdentity} 单元测试。
 *
 * <p>这层最怕"猜错说话人"：军师 skill 明确要求说话人映射不明时先问，
 * 因为一旦把"我"和"对方"认反，整段关系分析的方向就是错的。
 * 因此测试重点是"不确定时不许猜"。
 */
class ChatIdentityTest
{
    @Test
    @DisplayName("私聊里出现字面量 me 时，能确定「我」并把另一个标签认作对方")
    void shouldInferSelfFromLiteralMe()
    {
        List<RawMessage> raw = List.of(
                new RawMessage(0, "2026-04-01 10:00", "me", "在吗"),
                new RawMessage(1, "2026-04-01 10:01", "小美", "在的"));

        ChatIdentity identity = ChatIdentity.infer("wxid_me", "小美", false, raw);

        assertTrue(identity.autoDetected());
        assertEquals(List.of("me"), identity.selfLabels());
        assertTrue(identity.targetLabels().contains("小美"));
        assertTrue(identity.isSelf("me"));
        assertTrue(identity.isTarget("小美"));
        assertFalse(identity.isSelf("小美"));
    }

    @Test
    @DisplayName("没有任何「我」的标记时不猜，交给用户确认")
    void shouldNotGuessWhenNoSelfMarker()
    {
        List<RawMessage> raw = List.of(
                new RawMessage(0, "2026-04-01 10:00", "阿强", "在吗"),
                new RawMessage(1, "2026-04-01 10:01", "小美", "在的"));

        ChatIdentity identity = ChatIdentity.infer(null, "小美", false, raw);

        assertFalse(identity.autoDetected(), "两个标签都不认识时必须放弃自动判断");
        assertTrue(identity.selfLabels().isEmpty());
        assertTrue(identity.targetLabels().isEmpty());
    }

    @Test
    @DisplayName("群聊中未匹配的标签归为 OTHER，而不是被误当成对方")
    void shouldTreatUnmatchedGroupMemberAsOther()
    {
        ChatIdentity identity = ChatIdentity.confirmed("wxid_me", List.of("me"), List.of("小美"));

        assertEquals(CleanedMessage.Role.SELF, identity.resolveRole("me", true));
        assertEquals(CleanedMessage.Role.TARGET, identity.resolveRole("小美", true));
        assertEquals(CleanedMessage.Role.OTHER, identity.resolveRole("路人甲", true));
    }

    @Test
    @DisplayName("私聊中未匹配的标签不做角色假设")
    void shouldNotAssignRoleForUnknownLabelInPrivateChat()
    {
        ChatIdentity identity = ChatIdentity.confirmed("wxid_me", List.of("me"), List.of("小美"));

        assertEquals(CleanedMessage.Role.UNKNOWN, identity.resolveRole("陌生标签", false));
    }

    @Test
    @DisplayName("映射匹配是精确的，不做模糊匹配以免认错人")
    void shouldMatchLabelsExactly()
    {
        ChatIdentity identity = ChatIdentity.confirmed("wxid_me", List.of("me"), List.of("小美"));

        assertTrue(identity.isTarget("小美"));
        // 相似但不同的标签不能被当成长度匹配
        assertFalse(identity.isTarget("小美美"));
        assertFalse(identity.isTarget("小"));
    }

    @Test
    @DisplayName("统计各标签的发言条数，供前端展示供用户确认")
    void shouldCountSendersInFirstAppearanceOrder()
    {
        List<RawMessage> raw = List.of(
                new RawMessage(0, "t", "me", "a"),
                new RawMessage(1, "t", "小美", "b"),
                new RawMessage(2, "t", "小美", "c"),
                new RawMessage(3, "t", null, "系统"));

        var counts = ChatIdentity.countSenders(raw);

        assertEquals(2, counts.size(), "无发送者标签的消息不参与统计");
        assertEquals(1, counts.get("me"));
        assertEquals(2, counts.get("小美"));
    }

    @Test
    @DisplayName("空输入不抛异常")
    void shouldHandleEmptyInput()
    {
        ChatIdentity identity = ChatIdentity.infer(null, "小美", false, List.of());

        assertFalse(identity.autoDetected());
        assertTrue(ChatIdentity.countSenders(null).isEmpty());
    }

    @Test
    @DisplayName("空映射下任何标签都不会被判成 SELF/TARGET")
    void emptyMappingShouldNotResolveAnyRole()
    {
        ChatIdentity identity = ChatIdentity.confirmed(null, List.of(), List.of());

        assertEquals(CleanedMessage.Role.OTHER, identity.resolveRole("某人", true));
        assertEquals(CleanedMessage.Role.UNKNOWN, identity.resolveRole("某人", false));
        assertFalse(identity.isSelf("某人"));
        assertFalse(identity.isTarget("某人"));
    }
}
