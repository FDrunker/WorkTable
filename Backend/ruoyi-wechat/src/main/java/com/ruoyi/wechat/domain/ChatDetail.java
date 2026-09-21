package com.ruoyi.wechat.domain;

import java.util.List;

/**
 * 打开某个会话后的完整视图。
 *
 * <p>注意这里<b>不包含任何分析结果</b>：按需求，点进会话只显示聊天记录，
 * 分析历史由 {@code /analysis/list} 单独拉取，避免"打开会话"这个动作触发任何 LLM 调用。
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
