package com.ruoyi.wechat.domain;

import java.util.List;

/**
 * 粘贴文本解析预览。
 *
 * <p>这一步只在内存中解析，不写入会话和消息。用户确认“我是谁”后，
 * 后端归档到用户选择的已有会话，或按稳定的对方昵称新建会话。
 */
public record TextImportPreview(String importId, String suggestedDisplayName, boolean group,
        int messageCount, String firstTime, String lastTime, List<SenderOption> senders)
{
    public record SenderOption(String label, int count)
    {
    }
}
