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
     * 写入分析成功的结果。
     */
    int updateResult(WeChatAnalysis analysis);

    /**
     * 更新失败原因（分析过程出错时补写）。
     */
    int updateFailure(WeChatAnalysis analysis);
}
