package com.ruoyi.wechat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;

import com.ruoyi.wechat.datasource.MockWechatDataSource;
import com.ruoyi.wechat.datasource.WechatDataSourceException;
import com.ruoyi.wechat.datasource.text.TextWechatDataSource;
import com.ruoyi.wechat.domain.ChatDetail;

/**
 * {@link WechatTextImportService} 单元测试。
 *
 * <p>它只有两个职责：把文本交给文本数据源解析、再把解析结果交给归档服务。
 * 这里用一个记录参数的同门类替身来验证第二条 —— 归档本身依赖数据库，
 * 属于集成测试的范围，不在单测里假装覆盖。
 */
class WechatTextImportServiceTest
{
    private static final String TEXT = """
            小美
            2026-04-01 19:02
            在吗

            我
            2026-04-01 19:03
            [语音]
            """;

    private final RecordingArchiveService archiveService = new RecordingArchiveService();

    @Test
    @DisplayName("导入：解析文本后按派生出的会话标识归档，并把归档结果原样返回给前端")
    void shouldParseThenArchive()
    {
        TextWechatDataSource source = new TextWechatDataSource();
        WechatTextImportService service = new WechatTextImportService(provider(source),
                archiveService, new WechatStatusService(new MockWechatDataSource()));

        ChatDetail detail = service.importText(1L, "小美", TEXT, "admin");

        assertNotNull(detail);
        assertTrue(archiveService.called, "必须把解析结果交给归档服务");
        assertTrue(archiveService.chatUsername.startsWith(TextWechatDataSource.USERNAME_PREFIX));
        assertEquals("小美", archiveService.displayName);
        assertEquals(1L, archiveService.userId);
        assertEquals("admin", archiveService.operator);
        assertTrue(archiveService.sync, "导入必须触发一次落库同步");
    }

    @Test
    @DisplayName("当前数据源不是文本时明确报错，并提示改哪个配置")
    void shouldRejectWhenDataSourceIsNotText()
    {
        WechatTextImportService service = new WechatTextImportService(provider(null),
                archiveService, new WechatStatusService(new MockWechatDataSource()));

        WechatDataSourceException e = assertThrows(WechatDataSourceException.class,
                () -> service.importText(1L, "小美", TEXT, "admin"));

        assertTrue(e.getMessage().contains("mock"), "错误里要说明当前数据源是什么：" + e.getMessage());
        assertTrue(e.getSuggestion().contains("wechat.datasource"));
        assertFalse(archiveService.called, "数据源不对时不应写库");    }

    /** 构造一个只实现 getObject 的 ObjectProvider；getIfAvailable 的默认实现会据此返回 null */
    private static <T> ObjectProvider<T> provider(T value)
    {
        return new ObjectProvider<>()
        {
            @Override
            public T getObject()
            {
                if (value == null)
                {
                    throw new NoSuchBeanDefinitionException("测试：容器里没有该 Bean");
                }
                return value;
            }
        };
    }

    /** 记录 openChat 入参的替身，避免单测依赖数据库 */
    private static class RecordingArchiveService extends WeChatArchiveService
    {
        private boolean called;

        private Long userId;

        private String chatUsername;

        private String displayName;

        private boolean sync;

        private String operator;

        RecordingArchiveService()
        {
            // 只覆写 openChat，父类依赖全为 null 也不会被碰到
            super(null, null, null, null, null, null);
        }

        @Override
        public ChatDetail openChat(Long userId, Long sessionId, String chatUsername, String displayName,
                boolean group, int offset, int limit, boolean sync, String operator)
        {
            this.called = true;
            this.userId = userId;
            this.chatUsername = chatUsername;
            this.displayName = displayName;
            this.sync = sync;
            this.operator = operator;
            return new ChatDetail(null, List.of(), 0, offset, limit);
        }
    }
}
