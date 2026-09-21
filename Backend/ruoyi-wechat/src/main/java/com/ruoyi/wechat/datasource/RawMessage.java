package com.ruoyi.wechat.datasource;

/**
 * 原始消息 — 数据源产出的最小单元，尚未做任何清洗。
 *
 * <p>{@code sender} 为数据源给出的原始发送者标签，含义由数据源决定：
 * <ul>
 *   <li>CLI 数据源：可能是备注名、昵称、wxid，或是字面量 {@code me}</li>
 *   <li>上传数据源：文件里写的发送者文本，未识别时为 {@code null}</li>
 * </ul>
 * 该标签属于"证据"，不允许在这一层做任何语义推断，映射工作交给 identity 层。
 *
 * @param seq     同一批次内的原始顺序号，用于稳定排序与回溯
 * @param time    消息时间文本，格式 {@code yyyy-MM-dd HH:mm}
 * @param sender  原始发送者标签，可为 null
 * @param content 原始内容（已含数据源的类型占位符，如 {@code [图片]}）
 */
public record RawMessage(int seq, String time, String sender, String content)
{
    public boolean hasSender()
    {
        return sender != null && !sender.isBlank();
    }
}
