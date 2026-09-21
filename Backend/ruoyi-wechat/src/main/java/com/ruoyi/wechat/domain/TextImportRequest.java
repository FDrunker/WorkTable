package com.ruoyi.wechat.domain;

/**
 * 文本导入请求 —— 对应界面左下角「导入文本」弹出的粘贴框。
 *
 * <p>格式为每 3 行一条消息：昵称 / 时间 / 消息内容。
 */
public class TextImportRequest
{
    /** 会话名，可留空：留空时由后端按文本内容自动识别 */
    private String chatName;

    /** 粘贴的聊天记录原文 */
    private String text;

    public String getChatName()
    {
        return chatName;
    }

    public void setChatName(String chatName)
    {
        this.chatName = chatName;
    }

    public String getText()
    {
        return text;
    }

    public void setText(String text)
    {
        this.text = text;
    }
}
