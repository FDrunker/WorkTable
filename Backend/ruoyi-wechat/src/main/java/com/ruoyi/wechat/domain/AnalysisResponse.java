package com.ruoyi.wechat.domain;

import java.util.List;

import com.ruoyi.wechat.skill.AnalysisResult;

/**
 * 分析响应。
 *
 * @param chatName       聊天名
 * @param dataSourceMode 数据源标识
 * @param messageCount   实际参与分析的消息条数
 * @param identityDesc   使用的说话人映射说明（让用户能核对有没有认错人）
 * @param refsUsed       本次加载的知识文档路径
 * @param result         军师结构化结果；命中危机红线时为 null
 * @param crisisMessage  命中危机红线时的固定安全指引
 * @param model          实际使用的模型
 * @param promptTokens   输入 token
 * @param completionTokens 输出 token
 * @param durationMs     总耗时
 */
public record AnalysisResponse(String chatName, String dataSourceMode, int messageCount, String identityDesc,
        List<String> refsUsed, AnalysisResult result, String crisisMessage, String model,
        int promptTokens, int completionTokens, long durationMs)
{
    public boolean isCrisis()
    {
        return crisisMessage != null;
    }
}
