package com.ruoyi.wechat.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.ruoyi.wechat.datasource.ChatSession;
import com.ruoyi.wechat.datasource.WechatDataSourceException;
import com.ruoyi.wechat.datasource.text.TextWechatDataSource;
import com.ruoyi.wechat.domain.ChatDetail;

/**
 * 文本导入服务 —— 把用户粘贴的聊天记录解析并归档。
 *
 * <p>数据来源已改为"读取文本"，因此这是聊天分析功能的主入口：
 * <ol>
 *   <li>{@link TextWechatDataSource#submit} 解析文本并暂存，得到会话标识；</li>
 *   <li>走归档服务原有的 {@code openChat} 路径落库 —— 清洗、说话人映射、
 *       去重、计数全部复用同一套逻辑，与数据来源无关。</li>
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
    public ChatDetail importText(Long userId, String chatName, String text, String operator)
    {
        TextWechatDataSource source = textDataSource.getIfAvailable();
        if (source == null)
        {
            // 只有 wechat.datasource=text 时才装配文本数据源；其它取值下明确拒绝而不是静默失败
            String mode = statusService.dataSourceMode();
            throw new WechatDataSourceException("当前数据源是「" + mode + "」，不支持文本导入",
                    "请把配置项 wechat.datasource 设为 text 后重启服务");
        }

        ChatSession submitted = source.submit(chatName, text);
        return archiveService.openChat(userId, null, submitted.username(), submitted.displayName(),
                submitted.group(), 0, IMPORT_PAGE_SIZE, true, operator);
    }
}
