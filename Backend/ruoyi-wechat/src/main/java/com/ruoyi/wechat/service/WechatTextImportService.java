package com.ruoyi.wechat.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.ruoyi.wechat.datasource.ChatSession;
import com.ruoyi.wechat.datasource.WechatDataSourceException;
import com.ruoyi.wechat.datasource.text.TextWechatDataSource;
import com.ruoyi.wechat.domain.ChatDetail;
import com.ruoyi.wechat.domain.TextImportPreview;
import com.ruoyi.wechat.domain.WeChatSession;

/**
 * 文本导入服务 —— 先解析预览并让用户确认“我是谁”，再归档到已有会话或按对方昵称新建。
 *
 * <p>数据来源已改为"读取文本"，因此这是聊天分析功能的主入口：
 * <ol>
 *   <li>预览阶段解析文本并把发送者候选返回前端；</li>
 *   <li>确认阶段可指定已有会话；未指定时按“除自己外的昵称”生成稳定会话标识，
 *       再走归档服务原有的 {@code openChat} 路径落库；</li>
 *   <li>确认映射后回写消息角色，并把对方昵称保存为会话名称。</li>
 * </ol>
 *
 * <p>重复粘贴同一段文本是幂等的：归档表按「会话 + 时间 + 发送者 + 内容摘要」去重。
 */
@Service
public class WechatTextImportService
{
    /** 导入后一次返回的消息条数，与聊天记录界面单页展示量保持一致 */
    private static final int IMPORT_PAGE_SIZE = 200;

    private final ObjectProvider<TextWechatDataSource> textDataSource;

    private final WeChatArchiveService archiveService;

    private final WechatStatusService statusService;

    public WechatTextImportService(ObjectProvider<TextWechatDataSource> textDataSource,
            WeChatArchiveService archiveService, WechatStatusService statusService)
    {
        this.textDataSource = textDataSource;
        this.archiveService = archiveService;
        this.statusService = statusService;
    }

    /**
     * 解析并归档一段粘贴文本。
     *
     * @param userId   当前用户
     * @param chatName 用户填写的会话名，可留空
     * @param text     粘贴的聊天记录原文
     * @param operator 操作人账号，用于审计字段
     * @return 导入后的聊天记录视图，前端可直接渲染
     */
    public TextImportPreview previewText(Long userId, String text)
    {
        return requireTextSource().preview(userId, text);
    }

    /**
     * 用户选择自己的昵称后，把预览文本归档到对应会话。
     */
    public ChatDetail commitText(Long userId, String importId, String selfLabel, String operator)
    {
        return commitText(userId, importId, selfLabel, null, operator);
    }

    /**
     * 用户选择自己的昵称后，把预览文本归档到指定会话或按对方昵称新建会话。
     */
    public ChatDetail commitText(Long userId, String importId, String selfLabel, Long targetSessionId,
            String operator)
    {
        TextWechatDataSource source = requireTextSource();

        WeChatSession targetSession = targetSessionId == null ? null
                : archiveService.getSession(userId, targetSessionId);
        ChatSession target = targetSession == null ? null
                : new ChatSession(targetSession.getChatUsername(), targetSession.getDisplayName(),
                        targetSession.groupChat(), 0, null, null, null);
        TextWechatDataSource.CommitResult committed = source.commit(userId, importId, selfLabel, target);
        ChatSession session = committed.session();
        Long destinationSessionId = targetSession == null ? null : targetSession.getSessionId();
        ChatDetail detail = archiveService.openChat(userId, destinationSessionId,
                destinationSessionId == null ? session.username() : null, session.displayName(),
                session.group(), 0, IMPORT_PAGE_SIZE, true, operator);
        archiveService.confirmIdentity(userId, detail.session().sessionId(), session.username(),
                committed.selfLabels(), committed.targetLabels(), destinationSessionId == null, operator);
        return archiveService.openChat(userId, detail.session().sessionId(), null, null,
                session.group(), 0, IMPORT_PAGE_SIZE, false, operator);
    }

    private TextWechatDataSource requireTextSource()
    {
        TextWechatDataSource source = textDataSource.getIfAvailable();
        if (source != null)
        {
            return source;
        }
        String mode = statusService.dataSourceMode();
        throw new WechatDataSourceException("当前数据源是「" + mode + "」，不支持文本导入",
                "请把配置项 wechat.datasource 设为 text 后重启服务");
    }
}
