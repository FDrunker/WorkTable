package com.ruoyi.wechat.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.ruoyi.wechat.domain.WeChatMessage;

/**
 * 微信消息 Mapper。
 */
public interface WeChatMessageMapper
{
    /**
     * 批量插入，依赖唯一索引做去重。
     *
     * <p>用 {@code INSERT IGNORE} 而非先查后插：并发同步时"查了不存在、插的时候已存在"
     * 会撞唯一键报错，而忽略重复是这里想要的确切语义。
     *
     * @return 实际插入的行数（重复的会被忽略）
     */
    int batchInsertIgnore(@Param("list") List<WeChatMessage> messages);

    /**
     * 分页查询某会话的消息，按时间升序。
     */
    List<WeChatMessage> selectMessages(@Param("sessionId") Long sessionId, @Param("analyzed") String analyzed,
            @Param("offset") int offset, @Param("limit") int limit);

    /**
     * 统计某会话的消息数。
     */
    int countMessages(@Param("sessionId") Long sessionId, @Param("analyzed") String analyzed);

    /**
     * 查询未分析的消息，按时间升序。
     *
     * <p>保留此方法是为了将来接入其他分析方式时能直接取到"待处理"的记录集合，
     * 当前模块内不再有调用方（调用 agent 的链路已移除）。
     */
    List<WeChatMessage> selectUnanalyzed(@Param("sessionId") Long sessionId, @Param("limit") int limit);

    /**
     * 按原始标签批量回写归一化角色。
     *
     * <p>用 SQL 一次更新而不是把几千条消息查出来再逐条改：
     * 用户确认映射后需要立刻让历史消息的角色生效，逐条更新的往返开销不可接受。
     *
     * @param rawLabel 原始发送者标签
     * @param role     目标角色
     */
    int updateSenderRoleByLabel(@Param("sessionId") Long sessionId, @Param("rawLabel") String rawLabel,
            @Param("role") String role);
}
