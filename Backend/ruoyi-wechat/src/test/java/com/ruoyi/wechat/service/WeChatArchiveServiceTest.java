package com.ruoyi.wechat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ruoyi.wechat.domain.SessionView;
import com.ruoyi.wechat.domain.WeChatMessage;
import com.ruoyi.wechat.domain.WeChatSession;
import com.ruoyi.wechat.mapper.WeChatAnalysisMapper;
import com.ruoyi.wechat.mapper.WeChatMessageMapper;
import com.ruoyi.wechat.mapper.WeChatSessionMapper;

/**
 * {@link WeChatArchiveService} 的认识日期、映射、删除与视图透传测试。
 */
class WeChatArchiveServiceTest
{
    private static final Long USER_ID = 1L;

    private static final Long SESSION_ID = 9L;

    private static final Long NEW_SESSION_ID = 10L;

    private WeChatSession stored;

    private final List<WeChatMessage> storedMessages = new ArrayList<>();

    private boolean messagesDeleted;

    private boolean analysesDeleted;

    private boolean sessionDeleted;

    private boolean deletedSessionPurged;

    private boolean sessionInserted;

    private WeChatArchiveService service;

    @BeforeEach
    void setUp()
    {
        stored = new WeChatSession();
        stored.setSessionId(SESSION_ID);
        stored.setUserId(USER_ID);
        stored.setChatUsername("text:abc123");
        stored.setDisplayName("小美");
        stored.setIsGroup("0");
        stored.setSelfLabels("");
        stored.setTargetLabels("");
        stored.setIdentityConfirmed("0");
        stored.setMsgCount(0);
        stored.setUnanalyzedCount(0);
        stored.setDelFlag("0");

        WeChatSessionMapper sessionMapper = fake(WeChatSessionMapper.class,
                (proxy, method, args) -> switch (method.getName())
                {
                    case "selectById" -> stored;
                    case "selectAnyByUserAndChat" -> stored;
                    case "selectActiveByUserAndChatForUpdate" -> isActive(stored) ? stored : null;
                    case "insertSessionIfAbsent" ->
                    {
                        sessionInserted = true;
                        WeChatSession created = (WeChatSession) args[0];
                        created.setSessionId(NEW_SESSION_ID);
                        stored = created;
                        yield 1;
                    }
                    case "purgeDeletedById" ->
                    {
                        deletedSessionPurged = true;
                        stored = null;
                        yield 1;
                    }
                    case "updateSession" -> applyUpdate((WeChatSession) args[0]);
                    case "deleteByIds" ->
                    {
                        sessionDeleted = true;
                        yield 1;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        WeChatMessageMapper messageMapper = fake(WeChatMessageMapper.class,
                (proxy, method, args) -> switch (method.getName())
                {
                    case "countMessages" -> storedMessages.size();
                    case "selectMessages" -> storedMessages;
                    case "updateSenderRoleByLabel" -> 1;
                    case "deleteBySessionId" ->
                    {
                        messagesDeleted = true;
                        yield 1;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        WeChatAnalysisMapper analysisMapper = fake(WeChatAnalysisMapper.class,
                (proxy, method, args) -> switch (method.getName())
                {
                    case "selectBySession" -> List.of();
                    case "deleteBySessionId" ->
                    {
                        analysesDeleted = true;
                        yield 1;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });

        service = new WeChatArchiveService(null, null, null, sessionMapper, messageMapper, analysisMapper);
    }

    @Test
    @DisplayName("认识日期：保存后返回包含当天的认识天数")
    void shouldSaveAcquaintanceDate()
    {
        String date = LocalDate.now().minusDays(4).toString();

        SessionView view = service.updateAcquaintanceDate(USER_ID, SESSION_ID, date, "admin");

        assertEquals(date, view.acquaintanceDate());
        assertEquals(5L, view.acquaintanceDays(), "认识当天应记为第 1 天");
        assertEquals(date, stored.getAcquaintanceDate());
    }

    @Test
    @DisplayName("认识日期：清空值会移除日期与认识天数")
    void shouldClearAcquaintanceDate()
    {
        stored.setAcquaintanceDate(LocalDate.now().minusDays(2).toString());

        SessionView view = service.updateAcquaintanceDate(USER_ID, SESSION_ID, "  ", "admin");

        assertEquals("", view.acquaintanceDate());
        assertNull(view.acquaintanceDays());
        assertEquals("", stored.getAcquaintanceDate());
    }

    @Test
    @DisplayName("认识日期：拒绝错误格式和未来日期")
    void shouldRejectInvalidAcquaintanceDate()
    {
        assertThrows(IllegalArgumentException.class,
                () -> service.updateAcquaintanceDate(USER_ID, SESSION_ID, "2026/04/01", "admin"));
        assertThrows(IllegalArgumentException.class,
                () -> service.updateAcquaintanceDate(USER_ID, SESSION_ID,
                        LocalDate.now().plusDays(1).toString(), "admin"));
    }

    @Test
    @DisplayName("确认映射：会话名自动取对方昵称，并回写消息角色")
    void shouldConfirmIdentityAndNameConversation()
    {
        SessionView view = service.confirmIdentity(USER_ID, SESSION_ID, "text:target",
                List.of("我"), List.of("小美"), "admin");

        assertEquals("小美", view.displayName());
        assertEquals("小美", stored.getDisplayName());
        assertEquals("我", stored.getSelfLabels());
        assertEquals("小美", stored.getTargetLabels());
        assertEquals("1", stored.getIdentityConfirmed());
    }

    @Test
    @DisplayName("确认映射：导入已有会话时不覆盖原会话名")
    void shouldPreserveNameWhenConfirmingExistingSession()
    {
        stored.setDisplayName("小美（同事）");

        SessionView view = service.confirmIdentity(USER_ID, SESSION_ID, "text:target",
                List.of("我"), List.of("小美"), false, "admin");

        assertEquals("小美（同事）", view.displayName());
        assertEquals("小美（同事）", stored.getDisplayName());
        assertEquals("我", stored.getSelfLabels());
        assertEquals("小美", stored.getTargetLabels());
    }

    @Test
    @DisplayName("删除会话：会话、消息与历史分析记录一起逻辑删除")
    void shouldDeleteSessionData()
    {
        int affected = service.removeSession(USER_ID, SESSION_ID);

        assertEquals(1, affected);
        assertTrue(messagesDeleted);
        assertTrue(analysesDeleted);
        assertTrue(sessionDeleted);
    }

    @Test
    @DisplayName("删除会话来宾会话：不能删除别人的记录")
    void shouldRejectDeletingOtherUsersSession()
    {
        assertThrows(IllegalArgumentException.class,
                () -> service.removeSession(2L, SESSION_ID));
        assertFalse(messagesDeleted || analysesDeleted || sessionDeleted);
    }

    @Test
    @DisplayName("重新导入：已删除会话的占位记录会被清理并创建新会话")
    void shouldReplaceDeletedSessionWhenImportingAgain()
    {
        stored.setDelFlag("2");

        var detail = service.openChat(USER_ID, null, stored.getChatUsername(), "小美", false,
                0, 20, false, "admin");

        assertTrue(deletedSessionPurged);
        assertTrue(sessionInserted);
        assertEquals(NEW_SESSION_ID, detail.session().sessionId());
        assertEquals("0", stored.getDelFlag());
    }

    @Test
    @DisplayName("聊天详情：消息洞察与回答评分字段完整透传")
    void shouldExposeMessageInsights()
    {
        WeChatMessage message = new WeChatMessage();
        message.setMessageId(11L);
        message.setMsgTime("2026-09-23 12:00");
        message.setMsgDate("2026-09-23");
        message.setSenderRole("TARGET");
        message.setSenderRaw("小美");
        message.setContent("今天有点累");
        message.setMsgKind("TEXT");
        message.setAnalyzed("1");
        message.setEmotionTag("疲惫");
        message.setEmotionPercent(new BigDecimal("78.50"));
        message.setIntentTag("寻求安慰");
        message.setIntentPercent(new BigDecimal("66.00"));
        message.setReplyRating("A");
        message.setReplyScore(new BigDecimal("88.00"));
        storedMessages.add(message);

        var detail = service.openChat(USER_ID, SESSION_ID, null, null, false, 0, 20, false, "admin");

        var view = detail.messages().get(0);
        assertEquals("疲惫", view.emotionTag());
        assertEquals(new BigDecimal("78.50"), view.emotionPercent());
        assertEquals("寻求安慰", view.intentTag());
        assertEquals(new BigDecimal("66.00"), view.intentPercent());
        assertEquals("A", view.replyRating());
        assertEquals(new BigDecimal("88.00"), view.replyScore());
    }

    private Object applyUpdate(WeChatSession update)
    {
        if (update.getDisplayName() != null)
        {
            stored.setDisplayName(update.getDisplayName());
        }
        if (update.getAcquaintanceDate() != null)
        {
            stored.setAcquaintanceDate(update.getAcquaintanceDate());
        }
        if (update.getSelfLabels() != null)
        {
            stored.setSelfLabels(update.getSelfLabels());
        }
        if (update.getTargetLabels() != null)
        {
            stored.setTargetLabels(update.getTargetLabels());
        }
        if (update.getIdentityConfirmed() != null)
        {
            stored.setIdentityConfirmed(update.getIdentityConfirmed());
        }
        return 1;
    }

    private static boolean isActive(WeChatSession session)
    {
        return session != null && "0".equals(session.getDelFlag());
    }

    @SuppressWarnings("unchecked")
    private static <T> T fake(Class<T> type, InvocationHandler handler)
    {
        InvocationHandler guarded = (proxy, method, args) -> switch (method.getName())
        {
            case "toString" -> "fake:" + type.getSimpleName();
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> handler.invoke(proxy, method, args);
        };
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, guarded);
    }
}
