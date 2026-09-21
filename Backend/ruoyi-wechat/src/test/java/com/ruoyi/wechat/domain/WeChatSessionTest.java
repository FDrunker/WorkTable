package com.ruoyi.wechat.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link WeChatSession} 的 JavaBeans 契约测试。
 *
 * <p>回归背景：{@code identityConfirmed} 字段曾同时存在两个 getter
 * ——{@code getIdentityConfirmed()}（String）与 {@code isIdentityConfirmed()}（boolean）。
 * 二者构成"重载 getter"，违反 JavaBeans 规范，MyBatis 反射取值时抛
 * {@code ReflectionException: Illegal overloaded getter method with ambiguous type}，
 * 导致 {@code /wechat/chat/open} 新建会话时直接 500。
 *
 * <p>因此这里锁定两条不变量：
 * <ol>
 *   <li>持久化字段只有一个具名属性 getter（返回值类型唯一）；</li>
 *   <li>布尔便捷方法不得与「get + 首字母大写」拼出的同名属性冲突。</li>
 * </ol>
 */
class WeChatSessionTest
{
    /** MyBatis 的 Reflector 会把 isXxx() 视为属性 xxx 的候选 getter。 */
    private static final String[] PERSISTED_PROPERTIES = {
            "sessionId", "userId", "chatUsername", "displayName", "isGroup",
            "selfUsername", "selfLabels", "targetLabels", "identityConfirmed",
            "msgCount", "unanalyzedCount", "syncedUntil", "lastSyncTime", "delFlag"
    };

    @Test
    @DisplayName("identityConfirmed 只有唯一的 getter，不再与布尔便捷方法重载冲突")
    void shouldExposeSingleGetterForIdentityConfirmed()
    {
        Method[] candidates = Arrays.stream(WeChatSession.class.getMethods())
                .filter(m -> m.getParameterCount() == 0)
                .filter(m -> m.getName().equals("getIdentityConfirmed")
                        || m.getName().equals("isIdentityConfirmed"))
                .toArray(Method[]::new);

        assertEquals(1, candidates.length,
                "identityConfirmed 必须只有一个属性 getter，否则 MyBatis 反射取值会抛"
                        + " Illegal overloaded getter method");

        Method getter = candidates[0];
        assertEquals("getIdentityConfirmed", getter.getName(),
                "落库字段 identityConfirmed 是 char(1) 字符串，getter 必须返回 String");
        assertEquals(String.class, getter.getReturnType());
    }

    @Test
    @DisplayName("JavaBeans 内省认定 identityConfirmed 为 String 属性")
    void shouldIntrospectIdentityConfirmedAsString() throws Exception
    {
        BeanInfo info = Introspector.getBeanInfo(WeChatSession.class);
        PropertyDescriptor descriptor = Arrays.stream(info.getPropertyDescriptors())
                .filter(p -> p.getName().equals("identityConfirmed"))
                .findFirst()
                .orElse(null);

        assertNotNull(descriptor, "identityConfirmed 必须是一个可读写的 JavaBeans 属性");
        assertEquals(String.class, descriptor.getPropertyType());
        assertNotNull(descriptor.getReadMethod());
        assertNotNull(descriptor.getWriteMethod());
    }

    @Test
    @DisplayName("每个落库字段都能解析出唯一的取值方法（模拟 MyBatis Reflector）")
    void shouldResolveEveryPersistedPropertyUnambiguously()
    {
        for (String property : PERSISTED_PROPERTIES)
        {
            String suffix = Character.toUpperCase(property.charAt(0)) + property.substring(1);
            long getters = Arrays.stream(WeChatSession.class.getMethods())
                    .filter(m -> m.getParameterCount() == 0)
                    .filter(m -> !m.getReturnType().equals(Void.TYPE))
                    .filter(m -> m.getName().equals("get" + suffix) || m.getName().equals("is" + suffix))
                    .count();

            assertEquals(1, getters,
                    "属性 " + property + " 的 getter 数量应为 1，否则 MyBatis 反射取值会出现歧义");
        }
    }

    @Test
    @DisplayName("groupChat/identityConfirmed 便捷方法与 getter 语义一致")
    void shouldKeepConvenienceMethodsConsistentWithGetters()
    {
        WeChatSession session = new WeChatSession();
        session.setIsGroup("1");
        session.setIdentityConfirmed("1");
        assertTrue(session.groupChat());
        assertTrue(session.identityConfirmed());
        assertEquals("1", session.getIdentityConfirmed());

        session.setIsGroup("0");
        session.setIdentityConfirmed("0");
        assertFalse(session.groupChat());
        assertFalse(session.identityConfirmed());

        // null 视为未确认 / 非群聊，不能抛 NPE
        session.setIsGroup(null);
        session.setIdentityConfirmed(null);
        assertFalse(session.groupChat());
        assertFalse(session.identityConfirmed());
        assertNull(session.getIdentityConfirmed());
    }

    @Test
    @DisplayName("标签串与列表互转保持稳定")
    void shouldRoundTripLabels()
    {
        assertEquals("", WeChatSession.joinLabels(null));
        assertEquals("", WeChatSession.joinLabels(java.util.List.of()));
        assertEquals("我,小美", WeChatSession.joinLabels(java.util.List.of("我", "小美")));

        assertTrue(WeChatSession.splitLabels(null).isEmpty());
        assertTrue(WeChatSession.splitLabels("  ").isEmpty());
        assertEquals(java.util.List.of("我", "小美"), WeChatSession.splitLabels(" 我 , 小美 ,我 "));
    }
}
