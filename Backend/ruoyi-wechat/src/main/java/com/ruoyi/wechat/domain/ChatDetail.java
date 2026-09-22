package com.ruoyi.wechat.domain;

import java.util.List;

/**
 * 打开某个会话后的完整视图。
 *
 * <p>这里可能携带已经落库的关系与消息洞察字段，但打开会话本身
 * <b>不会触发任何 LLM 调用</b>；完整分析历史仍由 {@code /analysis/list} 单独拉取。
 *
 * @param session  会话信息
 * @param messages 消息（按时间升序）
 * @param total    消息总数
 * @param offset   当前偏移
 * @param limit    每页条数
 */
public record ChatDetail(SessionView session, List<ChatMessageView> messages, int total, int offset, int limit)
{
}
