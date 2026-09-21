package com.ruoyi.wechat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ruoyi.wechat.domain.SessionView;
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
                    case "countMessages" -> 0;
                    case "selectMessages" -> List.of();
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

    private Object applyUpdate(WeChatSession update)
    {
        if (update.getDisplayName() != null)
        {
            stored.setDisplayName(update.getDisplayName());
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
