package com.ruoyi.wechat.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.ruoyi.wechat.domain.WeChatAnalysis;

/**
 * 分析记录 Mapper。
 */
public interface WeChatAnalysisMapper
{
    int insertAnalysis(WeChatAnalysis analysis);

    WeChatAnalysis selectById(@Param("analysisId") Long analysisId);

    /**
     * 查询某会话的历史分析记录，按时间倒序。
     */
    List<WeChatAnalysis> selectBySession(@Param("sessionId") Long sessionId, @Param("limit") int limit);

    int deleteByIds(@Param("analysisIds") Long[] analysisIds, @Param("userId") Long userId);

    /**
     * 更新失败原因。
     *
     * <p>当前模块内已无调用方（写分析结果的链路随 agent 调用一起移除），
     * 保留是为了将来由其他方式写入分析记录时可以直接复用。
     */
    int updateFailure(WeChatAnalysis analysis);
}
