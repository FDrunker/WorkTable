package com.ruoyi.wechat.domain;

/**
 * 文本解析预览请求 —— 对应界面左下角「导入文本」弹出的粘贴框。
 *
 * <p>格式为每 3 行一条消息：昵称 / 时间 / 消息内容。
 */
public class TextImportRequest
{
    /** 粘贴的聊天记录原文 */
    private String text;

    public String getText()
    {
        return text;
    }

    public void setText(String text)
    {
        this.text = text;
    }
}
