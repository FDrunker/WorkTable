package com.ruoyi.wechat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;

import com.ruoyi.wechat.datasource.MockWechatDataSource;
import com.ruoyi.wechat.datasource.WechatDataSourceException;
import com.ruoyi.wechat.datasource.text.TextWechatDataSource;
import com.ruoyi.wechat.domain.ChatDetail;
import com.ruoyi.wechat.domain.SessionView;
import com.ruoyi.wechat.domain.TextImportPreview;

/**
 * {@link WechatTextImportService} 的预览与确认流程测试。
 */
class WechatTextImportServiceTest
{
    private static final String TEXT = """
            小美
            2026-04-01 19:02
            在吗

            我
            2026-04-01 19:03
            在的
            """;

    private final RecordingArchiveService archiveService = new RecordingArchiveService();

    @Test
    @DisplayName("预览：只解析昵称候选，不写数据库")
    void shouldPreviewWithoutArchiving()
    {
        TextWechatDataSource source = new TextWechatDataSource();
        WechatTextImportService service = new WechatTextImportService(provider(source),
                archiveService, new WechatStatusService(new MockWechatDataSource()));

        TextImportPreview preview = service.previewText(1L, TEXT);

        assertEquals(2, preview.senders().size());
        assertFalse(archiveService.called, "预览阶段不能写归档");
    }

    @Test
    @DisplayName("确认：按对方昵称归档，回写角色后再返回最终聊天记录")
    void shouldCommitAndConfirmIdentity()
    {
        TextWechatDataSource source = new TextWechatDataSource();
        WechatTextImportService service = new WechatTextImportService(provider(source),
                archiveService, new WechatStatusService(new MockWechatDataSource()));
        TextImportPreview preview = service.previewText(1L, TEXT);

        ChatDetail detail = service.commitText(1L, preview.importId(), "我", "admin");

        assertEquals(2, archiveService.openCount);
        assertEquals("小美", archiveService.firstDisplayName);
        assertTrue(archiveService.firstSync);
        assertEquals(9L, archiveService.confirmedSessionId);
        assertEquals(List.of("我"), archiveService.confirmedSelfLabels);
        assertEquals(List.of("小美"), archiveService.confirmedTargetLabels);
        assertEquals(9L, detail.session().sessionId());
    }

    @Test
    @DisplayName("当前数据源不是文本时明确报错，并提示改哪个配置")
    void shouldRejectWhenDataSourceIsNotText()
    {
        WechatTextImportService service = new WechatTextImportService(provider(null),
                archiveService, new WechatStatusService(new MockWechatDataSource()));

        WechatDataSourceException previewError = assertThrows(WechatDataSourceException.class,
                () -> service.previewText(1L, TEXT));
        WechatDataSourceException commitError = assertThrows(WechatDataSourceException.class,
                () -> service.commitText(1L, "text:preview-x", "我", "admin"));

        assertTrue(previewError.getMessage().contains("mock"));
        assertTrue(previewError.getSuggestion().contains("wechat.datasource"));
        assertTrue(commitError.getMessage().contains("mock"));
        assertFalse(archiveService.called, "数据源不对时不应写库");
    }

    /** 构造一个只实现 getObject 的 ObjectProvider；getIfAvailable 会据此返回 null */
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

    /**
     * 记录归档与映射入参的替身。
     */
    private static class RecordingArchiveService extends WeChatArchiveService
    {
        private boolean called;

        private int openCount;

        private String firstDisplayName;

        private boolean firstSync;

        private Long confirmedSessionId;

        private List<String> confirmedSelfLabels;

        private List<String> confirmedTargetLabels;

        RecordingArchiveService()
        {
            super(null, null, null, null, null, null);
        }

        @Override
        public ChatDetail openChat(Long userId, Long sessionId, String chatUsername, String displayName,
                boolean group, int offset, int limit, boolean sync, String operator)
        {
            called = true;
            openCount++;
            if (openCount == 1)
            {
                firstDisplayName = displayName;
                firstSync = sync;
            }
            return new ChatDetail(sessionView(), List.of(), 0, offset, limit);
        }

        @Override
        public SessionView confirmIdentity(Long userId, Long sessionId, String selfUsername,
                List<String> selfLabels, List<String> targetLabels, String operator)
        {
            called = true;
            confirmedSessionId = sessionId;
            confirmedSelfLabels = selfLabels;
            confirmedTargetLabels = targetLabels;
            return sessionView();
        }

        private static SessionView sessionView()
        {
            return new SessionView(9L, "text:target", "小美", false, true,
                    List.of("我"), List.of("小美"), 2, 0, 0,
                    "2026-04-01 19:03", "在的", null,
                    new BigDecimal("50.00"), null, null, null, null);
        }
    }
}
