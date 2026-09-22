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
import com.ruoyi.wechat.domain.WeChatSession;

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
        assertTrue(archiveService.confirmRename);
        assertEquals(9L, archiveService.confirmedSessionId);
        assertEquals(List.of("我"), archiveService.confirmedSelfLabels);
        assertEquals(List.of("小美"), archiveService.confirmedTargetLabels);
        assertEquals(9L, detail.session().sessionId());
    }

    @Test
    @DisplayName("确认：选择已有会话时归档到该会话且不覆盖原名称")
    void shouldCommitToSelectedSession()
    {
        TextWechatDataSource source = new TextWechatDataSource();
        WechatTextImportService service = new WechatTextImportService(provider(source),
                archiveService, new WechatStatusService(new MockWechatDataSource()));
        TextImportPreview preview = service.previewText(1L, TEXT);

        service.commitText(1L, preview.importId(), "我", 42L, "admin");

        assertEquals(42L, archiveService.selectedSessionId);
        assertEquals(42L, archiveService.firstSessionId);
        assertEquals("小美（同事）", archiveService.firstDisplayName);
        assertFalse(archiveService.confirmRename);
        assertEquals(42L, archiveService.confirmedSessionId);
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

        private String firstChatUsername;

        private Long firstSessionId;

        private boolean firstSync;

        private Long selectedSessionId;

        private Long confirmedSessionId;

        private boolean confirmRename;

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
                firstSessionId = sessionId;
                firstDisplayName = displayName;
                firstChatUsername = chatUsername;
                firstSync = sync;
            }
            Long resultSessionId = sessionId == null ? 9L : sessionId;
            return new ChatDetail(sessionView(resultSessionId), List.of(), 0, offset, limit);
        }

        @Override
        public WeChatSession getSession(Long userId, Long sessionId)
        {
            selectedSessionId = sessionId;
            WeChatSession session = new WeChatSession();
            session.setSessionId(sessionId);
            session.setUserId(userId);
            session.setChatUsername("text:existing");
            session.setDisplayName("小美（同事）");
            session.setIsGroup("0");
            return session;
        }

        @Override
        public SessionView confirmIdentity(Long userId, Long sessionId, String selfUsername,
                List<String> selfLabels, List<String> targetLabels, boolean renameSession, String operator)
        {
            called = true;
            confirmedSessionId = sessionId;
            confirmedSelfLabels = selfLabels;
            confirmedTargetLabels = targetLabels;
            confirmRename = renameSession;
            return sessionView(sessionId);
        }

        private static SessionView sessionView(Long sessionId)
        {
            String username = sessionId == 42L ? "text:existing" : "text:target";
            String displayName = sessionId == 42L ? "小美（同事）" : "小美";
            return new SessionView(sessionId, username, displayName, false, true,
                    List.of("我"), List.of("小美"), 2, 0, 0,
                    "2026-04-01 19:03", "在的", null,
                    new BigDecimal("50.00"), null, null, null, null);
        }
    }
}
