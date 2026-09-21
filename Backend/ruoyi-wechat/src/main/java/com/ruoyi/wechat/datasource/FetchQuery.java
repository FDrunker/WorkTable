package com.ruoyi.wechat.datasource;

/**
 * 消息拉取查询条件。
 *
 * @param chatName  聊天名（数据源自行解析为内部标识）
 * @param startTime 起始时间 {@code yyyy-MM-dd}，可为 null
 * @param endTime   结束时间 {@code yyyy-MM-dd}，可为 null
 * @param limit     期望条数；受数据源自身上限约束（CLI 为 500/次）
 * @param offset    偏移量。注意：CLI 数据源的 offset 语义是"从最新往回数"，
 *                  调用方不要假设它随翻页单调，增量同步应改用时间水位线。
 */
public record FetchQuery(String chatName, String startTime, String endTime, int limit, int offset)
{
    public static FetchQuery of(String chatName, int limit)
    {
        return new FetchQuery(chatName, null, null, limit, 0);
    }

    public static FetchQuery range(String chatName, String startTime, String endTime, int limit)
    {
        return new FetchQuery(chatName, startTime, endTime, limit, 0);
    }
}
