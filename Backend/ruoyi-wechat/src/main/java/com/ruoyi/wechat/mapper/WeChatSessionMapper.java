package com.ruoyi.wechat.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.ruoyi.wechat.domain.WeChatSession;

/**
 * 微信会话 Mapper。
 */
public interface WeChatSessionMapper
{
    /**
     * 按用户与微信标识查询会话，包含已逻辑删除的占位记录。
     */
    WeChatSession selectAnyByUserAndChat(@Param("userId") Long userId,
            @Param("chatUsername") String chatUsername);

    /**
     * 加锁查询有效会话，用于并发创建后的唯一键兜底。
     */
    WeChatSession selectActiveByUserAndChatForUpdate(@Param("userId") Long userId,
            @Param("chatUsername") String chatUsername);

    /**
     * 查询用户的会话列表。
     */
    List<WeChatSession> selectSessionsByUser(@Param("userId") Long userId, @Param("displayName") String displayName);

    WeChatSession selectById(@Param("sessionId") Long sessionId);

    /**
     * 仅当用户与微信标识尚不存在时插入会话。
     *
     * @return 实际新增返回 1，命中唯一键返回 0
     */
    int insertSessionIfAbsent(WeChatSession session);

    /**
     * 物理清理已逻辑删除的会话占位，释放用户与微信标识的唯一键。
     */
    int purgeDeletedById(@Param("sessionId") Long sessionId, @Param("userId") Long userId);

    int updateSession(WeChatSession session);

    /**
     * 重新统计会话的消息数与未分析数。
     *
     * <p>做成独立语句而不是累加，是因为去重插入会让"插入了多少行"与
     * "实际新增了多少条"不一致，累加会算错。
     */
    int refreshCounters(@Param("sessionId") Long sessionId);

    /**
     * 更新说话人映射（用户确认）。
     */
    int updateIdentity(WeChatSession session);

    int deleteByIds(@Param("sessionIds") Long[] sessionIds, @Param("userId") Long userId);
}
