package com.ruoyi.wechat.domain;

import java.util.List;

/**
 * 分析前的输入准备结果 —— 供用户核对"工具理解得对不对"。
 *
 * @param chatName    聊天名
 * @param group       是否群聊
 * @param messageCount 拉取到的消息条数
 * @param startTime   实际最早消息时间
 * @param endTime     实际最晚消息时间
 * @param identity    检测到的说话人身份信息
 * @param preview     前若干条清洗后对话预览，用于肉眼核对映射正确
 * @param warnings    需要用户注意的问题（如数据源未就绪、映射不确定）
 */
public record AnalysisPreview(String chatName, boolean group, int messageCount, String startTime,
        String endTime, IdentityCandidate identity, List<String> preview, List<String> warnings)
{
}
