package com.ruoyi.wechat.skill;

import java.util.List;

/**
 * 军师分析结果 — 严格对应 goutoujunshi SKILL.md 中「每次分析」的五个步骤。
 *
 * <p>为什么用固定结构而不是让模型自由发挥：
 * <ul>
 *   <li>SKILL.md 的五步流程是这套 skill 的核心价值，格式不稳定就等于流程失守；</li>
 *   <li>结构化之后"已知事实必须带时间戳"这类要求可以在 Java 侧<b>断言</b>，
 *       而不是祈祷模型遵守；</li>
 *   <li>便于做回归测试与版本对比。</li>
 * </ul>
 *
 * @param emotionLanding   第 1 步：情绪落地
 * @param facts            第 2 步：事实拆分
 * @param interest         第 3 步：利益判断
 * @param advice           第 4 步：明确建议
 * @param actions          第 5 步：行动收束
 * @param crisisReferral   命中安全红线时为 true，此时其余字段可能为空
 */
public record AnalysisResult(String emotionLanding, FactBreakdown facts, InterestJudgement interest,
        Advice advice, ActionPlan actions, boolean crisisReferral)
{
    /**
     * 事实拆分 —— SKILL.md 明确要求区分"事实／推测／未知"。
     *
     * @param knownFacts        已知事实，每条应可追溯到聊天原文
     * @param inferences        合理推测
     * @param keyUnknowns       关键未知
     */
    public record FactBreakdown(List<String> knownFacts, List<String> inferences, List<String> keyUnknowns)
    {
    }

    /**
     * 利益判断 —— 覆盖 SKILL.md 列举的评估维度。
     */
    public record InterestJudgement(String evaluation)
    {
    }

    /**
     * 明确建议 —— 先给一句首选与理由，必要时给不超过三个版本。
     *
     * @param primary           首选建议（一句话）
     * @param reasons           2-4 条理由
     * @param steadyVariant     稳健版，可为 null
     * @param flirtyVariant     会撩／策略版，可为 null
     * @param assertiveVariant  强势版，可为 null
     */
    public record Advice(String primary, List<String> reasons, String steadyVariant,
            String flirtyVariant, String assertiveVariant)
    {
    }

    /**
     * 行动收束 —— 本次分析必须落到可执行动作。
     *
     * @param immediateAction 现在能做的一个小动作
     * @param observationWindow 观察窗口
     * @param stopCondition   停止条件
     * @param followUpQuestions 值得回来反馈的具体信号／待确认问题
     */
    public record ActionPlan(String immediateAction, String observationWindow, String stopCondition,
            List<String> followUpQuestions)
    {
    }
}
