package com.ruoyi.wechat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * {@link WeChatArchiveService} 的会话改名用例。
 *
 * <p>Mapper 用动态代理做替身：改名这条链路只碰"读会话 / 写会话 / 读计数"几个方法，
 * 为了这几个方法去连数据库或引入 mock 框架都不划算，
 * 而校验（空名、超长、越权）恰恰是最容易写错又最该被钉死的部分。
 */
class WeChatArchiveServiceTest
{
    private static final Long USER_ID = 1L;

    private static final Long SESSION_ID = 9L;

    /** 替身"数据库"里的会话行 */
    private WeChatSession stored;

    private final List<WeChatMessage> storedMessages = new ArrayList<>();

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
        stored.setMsgCount(0);
        stored.setUnanalyzedCount(0);

        WeChatSessionMapper sessionMapper = fake(WeChatSessionMapper.class,
                (proxy, method, args) -> switch (method.getName())
                {
                    case "selectById" -> stored;
                    // 模拟 update 语句：把非空字段写回"数据库"
                    case "updateSession" -> applyUpdate((WeChatSession) args[0]);
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        WeChatMessageMapper messageMapper = fake(WeChatMessageMapper.class,
                (proxy, method, args) -> switch (method.getName())
                {
                    case "countMessages" -> storedMessages.size();
                    case "selectMessages" -> storedMessages;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        WeChatAnalysisMapper analysisMapper = fake(WeChatAnalysisMapper.class,
                (proxy, method, args) -> switch (method.getName())
                {
                    case "selectBySession" -> List.of();
                    default -> throw new UnsupportedOperationException(method.getName());
                });

        service = new WeChatArchiveService(null, null, null, sessionMapper, messageMapper, analysisMapper);
    }

    @Test
    @DisplayName("改名：首尾空白被裁掉，返回的会话信息里是新名字")
    void shouldRenameAndTrimName()
    {
        SessionView view = service.renameSession(USER_ID, SESSION_ID, "  小美（暧昧对象）  ", "admin");

        assertEquals("小美（暧昧对象）", view.displayName());
        assertEquals("小美（暧昧对象）", stored.getDisplayName());
        // 只改名字，会话标识与归属不动，否则历史消息会变成另一个会话的
        assertEquals("text:abc123", view.chatUsername());
        assertEquals(SESSION_ID, view.sessionId());
    }

    @Test
    @DisplayName("改名：空名字、纯空白与 null 都被拒绝，且不写库")
    void shouldRejectBlankName()
    {
        assertThrows(IllegalArgumentException.class,
                () -> service.renameSession(USER_ID, SESSION_ID, "", "admin"));
        assertThrows(IllegalArgumentException.class,
                () -> service.renameSession(USER_ID, SESSION_ID, "   ", "admin"));
        assertThrows(IllegalArgumentException.class,
                () -> service.renameSession(USER_ID, SESSION_ID, null, "admin"));

        assertEquals("小美", stored.getDisplayName(), "校验失败不应改动会话名");
    }

    @Test
    @DisplayName("改名：超长名字被拒绝，避免超出表字段长度")
    void shouldRejectTooLongName()
    {
        String tooLong = "名".repeat(61);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.renameSession(USER_ID, SESSION_ID, tooLong, "admin"));

        assertTrue(e.getMessage().contains("上限"));
        assertEquals("小美", stored.getDisplayName());
    }

    @Test
    @DisplayName("改名：不能改别人的会话")
    void shouldRejectSessionOfOtherUser()
    {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.renameSession(2L, SESSION_ID, "改名试试", "admin"));

        assertTrue(e.getMessage().contains("无权访问"));
        assertEquals("小美", stored.getDisplayName());
    }

    @Test
    @DisplayName("改名：会话不存在时报错而不是静默成功")
    void shouldRejectMissingSession()
    {
        WeChatArchiveService emptyService = new WeChatArchiveService(null, null, null,
                fake(WeChatSessionMapper.class, (proxy, method, args) -> switch (method.getName())
                {
                    case "selectById" -> null;
                    default -> throw new UnsupportedOperationException(method.getName());
                }), null, null);

        assertThrows(IllegalArgumentException.class,
                () -> emptyService.renameSession(USER_ID, SESSION_ID, "新名字", "admin"));
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
        return 1;
    }

    /**
     * 生成一个接口的动态代理替身；toString / hashCode / equals 由外层统一兜底，
     * 免得断言失败打印对象时又抛出 UnsupportedOperationException 掩盖真正的原因。
     */
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
