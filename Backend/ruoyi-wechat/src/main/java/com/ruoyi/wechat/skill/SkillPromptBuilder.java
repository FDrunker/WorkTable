package com.ruoyi.wechat.skill;

import java.util.List;

/**
 * 军师 prompt 构建器。
 *
 * <p>分两个 prompt：
 * <ul>
 *   <li>{@link #buildRoutingPrompt} —— 路由阶段，小模型从文档目录里挑 1-3 篇；</li>
 *   <li>{@link #buildAnalysisSystemPrompt} / {@link #buildAnalysisUserPrompt} —— 主分析</li>
 * </ul>
 *
 * <p>安全边界（家暴、跟踪、威胁、自伤等）在 prompt 里写明，同时在 Java 侧另有
 * {@code SafetyGuard} 做关键词兜底 —— 只靠 prompt 约束模型是不够的。
 */
public final class SkillPromptBuilder
{
    private SkillPromptBuilder()
    {
    }

    /**
     * 路由阶段 system prompt。
     *
     * <p>之所以让模型读路由表而不是硬编码关键词：路由表本身就是 goutoujunshi 的
     * 设计资产，硬编码会在上游更新时失效；而且让模型做判断可以理解用户的自然语言诉求。
     */
    public static String buildRoutingSystemPrompt()
    {
        return """
                你是资料路由员。你的唯一任务：根据用户诉求与聊天概况，从资料目录中选择最相关的 1-3 篇。

                规则：
                1. 只输出 JSON，不要任何解释、不要 markdown 代码块围栏。
                2. 输出格式：{"refs":["路径1","路径2"]}
                3. 路径必须逐字来自给定目录，不得编造、不得改写。
                4. 宁少勿多：只选真正会改变建议走向的资料，最多 3 篇。若都不相关，返回 {"refs":[]}。
                5. 优先满足"用户当前最想解决的问题"，而不是覆盖所有可能的主题。
                """;
    }

    /**
     * 路由阶段 user prompt。
     *
     * @param userGoal    用户的诉求描述
     * @param statsBlock  会话概况（条数、角色分布等）
     * @param routingTable 路由表原文
     * @param catalog     可选资料目录（路径 → 标题）
     */
    public static String buildRoutingUserPrompt(String userGoal, String statsBlock,
            String routingTable, String catalog)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("## 用户诉求\n").append(blankToDash(userGoal)).append("\n\n");
        sb.append("## 聊天概况\n").append(blankToDash(statsBlock)).append("\n\n");
        if (routingTable != null && !routingTable.isBlank())
        {
            sb.append("## 路由表（决策依据）\n").append(routingTable).append("\n\n");
        }
        sb.append("## 可选资料目录\n").append(catalog).append('\n');
        return sb.toString();
    }

    /**
     * 主分析 system prompt：军师行为内核 + 本次加载的知识。
     */
    public static String buildAnalysisSystemPrompt(SkillBundle bundle)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                你是「狗头军师」，一个面向真实关系问题的分析助手。你的行为准则完全由下方 SKILL 内容定义。

                ## 输出格式（硬性要求，必须遵守）
                你必须只输出一个 JSON 对象，不要输出 markdown 代码块围栏，不要输出任何 JSON 之外的解释文字。
                结构如下：

                {
                  "emotionLanding": "2-4 句，指出用户的感受、触发点和冲突。认可感受，但不为未经证实的解释背书。",
                  "facts": {
                    "knownFacts": ["只能来自我提供的聊天内容，每条尽量带上时间，例如 [2026-04-02 10:30] 我主动发起邀约"],
                    "inferences": ["合理推测，必须能被区分于事实"],
                    "keyUnknowns": ["缺失但会改变建议的关键信息"]
                  },
                  "interest": { "evaluation": "评估互惠、可靠、吸引、价值观、现实可行性、可逆性、安全与机会成本。" },
                  "advice": {
                    "primary": "一句话的首选建议",
                    "reasons": ["2-4 条理由"],
                    "steadyVariant": "稳健版，或 null",
                    "flirtyVariant": "会撩/策略版，或 null",
                    "assertiveVariant": "强势版（指边界与快速筛选，不是羞辱或控制），或 null"
                  },
                  "actions": {
                    "immediateAction": "现在就能做的一个小动作",
                    "observationWindow": "观察窗口",
                    "stopCondition": "停止条件",
                    "followUpQuestions": ["1-3 个真正会改变决策的待确认问题"]
                  },
                  "crisisReferral": false
                }

                ## 铁律
                1. 不得编造聊天内容。knownFacts 里的每一条都必须能在下方"聊天记录"中找到依据。
                2. 我提供的上下文可能因为长度限制而被截断；若概况中提示了截断，你要在 keyUnknowns 或 facts 中明确说明"较早记录未提供"，不要把它当作"没有发生"。
                3. 不得替对方读心。不凭单次回复、表情、MBTI 或性别给对方定性。
                4. 明确拒绝、僵住、躲避或撤回时停止推进建议；不得给出贬低、服从性测试、虚假时间限制、嫉妒操控、煤气灯、孤立、跟踪或性施压的方案。
                5. 不诊断心理疾病，不用标签替代行为证据。
                6. 若内容出现家暴、跟踪、胁迫、人身威胁、自伤或诈骗迹象，把 crisisReferral 设为 true，
                   并把 emotionLanding 与 advice.primary 写为"先确认当下安全、联系可信支持或当地紧急服务"，
                   不要提供普通恋爱话术。
                7. 不保证话术能让特定的人产生感情。

                """);

        if (bundle != null)
        {
            sb.append("\n===== SKILL 定义与本次按需加载的资料 开始 =====\n");
            sb.append(bundle.render());
            sb.append("\n===== SKILL 定义与资料 结束 =====\n");
        }
        else
        {
            sb.append("\n【注意】本次未能加载 SKILL 资料，请仅在通用常识范围内给出克制、保守的建议，"
                    + "并在 keyUnknowns 中说明知识库不可用。\n");
        }
        return sb.toString();
    }

    /**
     * 主分析 user prompt：把清洗后的上下文交给模型。
     *
     * @param sessionDesc 会话描述（名称、类型、时间范围）
     * @param identityDesc 说话人映射说明
     * @param statsBlock   发言统计
     * @param goal         用户诉求
     * @param chatContext  聊天记录正文
     */
    public static String buildAnalysisUserPrompt(String sessionDesc, String identityDesc,
            String statsBlock, String goal, String chatContext)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("## 会话信息\n").append(blankToDash(sessionDesc)).append("\n\n");
        sb.append("## 说话人映射（已确认，请严格按此理解）\n").append(blankToDash(identityDesc)).append("\n\n");
        sb.append("## 发言统计\n").append(blankToDash(statsBlock)).append("\n\n");
        sb.append("## 用户诉求\n").append(blankToDash(goal)).append("\n\n");
        sb.append("## 聊天记录\n```\n").append(blankToDash(chatContext)).append("\n```\n");
        return sb.toString();
    }

    private static String blankToDash(String s)
    {
        return (s == null || s.isBlank()) ? "（未提供）" : s;
    }
}
