package com.ruoyi.wechat.skill;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

/**
 * 解析并校验模型返回的分析结果。
 *
 * <p>校验比"能解析"更重要：把 SKILL.md 的流程要求变成可断言的检查，
 * 而不是依赖模型每次都守规矩。校验失败时保留原始文本，便于排查与回归。
 */
@Component
public class AnalysisResultParser
{
    private static final Logger log = LoggerFactory.getLogger(AnalysisResultParser.class);

    /**
     * 解析结果。
     *
     * @return 解析出的结果；结构完全无法识别时返回 null，由调用方决定降级策略
     */
    public AnalysisResult parse(String content)
    {
        if (content == null || content.isBlank())
        {
            return null;
        }
        String json = extractJson(content);
        try
        {
            JSONObject obj = JSON.parseObject(json);
            if (obj == null)
            {
                return null;
            }

            boolean crisis = obj.getBooleanValue("crisisReferral");

            JSONObject factsObj = obj.getJSONObject("facts");
            AnalysisResult.FactBreakdown facts = factsObj == null ? null
                    : new AnalysisResult.FactBreakdown(
                            stringList(factsObj.getJSONArray("knownFacts")),
                            stringList(factsObj.getJSONArray("inferences")),
                            stringList(factsObj.getJSONArray("keyUnknowns")));

            JSONObject interestObj = obj.getJSONObject("interest");
            AnalysisResult.InterestJudgement interest = interestObj == null ? null
                    : new AnalysisResult.InterestJudgement(interestObj.getString("evaluation"));

            JSONObject adviceObj = obj.getJSONObject("advice");
            AnalysisResult.Advice advice = adviceObj == null ? null
                    : new AnalysisResult.Advice(
                            adviceObj.getString("primary"),
                            stringList(adviceObj.getJSONArray("reasons")),
                            adviceObj.getString("steadyVariant"),
                            adviceObj.getString("flirtyVariant"),
                            adviceObj.getString("assertiveVariant"));

            JSONObject actionsObj = obj.getJSONObject("actions");
            AnalysisResult.ActionPlan actions = actionsObj == null ? null
                    : new AnalysisResult.ActionPlan(
                            actionsObj.getString("immediateAction"),
                            actionsObj.getString("observationWindow"),
                            actionsObj.getString("stopCondition"),
                            stringList(actionsObj.getJSONArray("followUpQuestions")));

            AnalysisResult result = new AnalysisResult(
                    obj.getString("emotionLanding"), facts, interest, advice, actions, crisis);

            validate(result);
            return result;
        }
        catch (Exception e)
        {
            log.warn("解析军师输出失败：{}。原始输出前 300 字符：{}",
                    e.getMessage(), content.length() > 300 ? content.substring(0, 300) : content);
            return null;
        }
    }

    /**
     * 结构校验 —— 把 SKILL.md 的硬性要求落成检查项。
     *
     * <p>只记录警告而不抛异常：宁可给用户一份缺了某节的答案，
     * 也好过整个分析失败；但警告会进日志，便于持续改进 prompt。
     */
    private void validate(AnalysisResult r)
    {
        if (r.crisisReferral())
        {
            return;
        }
        if (isBlank(r.emotionLanding()))
        {
            log.warn("军师输出缺少情绪落地（emotionLanding）");
        }
        if (r.facts() == null || r.facts().knownFacts() == null || r.facts().knownFacts().isEmpty())
        {
            log.warn("军师输出缺少已知事实（facts.knownFacts），这违反 SKILL 的事实拆分要求");
        }
        else if (r.facts().knownFacts().stream().noneMatch(this::hasTimestampish))
        {
            log.warn("军师输出的已知事实没有任何时间引用，可追溯性不足");
        }
        if (r.advice() == null || isBlank(r.advice().primary()))
        {
            log.warn("军师输出缺少首选建议（advice.primary）");
        }
        if (r.actions() == null || isBlank(r.actions().immediateAction()))
        {
            log.warn("军师输出缺少可执行动作（actions.immediateAction）");
        }
    }

    /**
     * 粗略判断文本里是否含时间引用，用于检查"事实可追溯"。
     */
    private boolean hasTimestampish(String s)
    {
        if (s == null)
        {
            return false;
        }
        return s.matches(".*\\d{1,2}[-/月]\\d{1,2}.*") || s.matches(".*\\d{2}:\\d{2}.*")
                || s.contains("今天") || s.contains("昨天") || s.contains("前天")
                || s.contains("上午") || s.contains("下午") || s.contains("晚上");
    }

    private List<String> stringList(JSONArray array)
    {
        if (array == null)
        {
            return List.of();
        }
        List<String> list = new java.util.ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++)
        {
            String s = array.getString(i);
            if (s != null && !s.isBlank() && !"null".equalsIgnoreCase(s))
            {
                list.add(s);
            }
        }
        return list;
    }

    private String extractJson(String content)
    {
        String trimmed = content.trim();
        if (trimmed.startsWith("```"))
        {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline)
            {
                trimmed = trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        return (start >= 0 && end > start) ? trimmed.substring(start, end + 1) : trimmed;
    }

    private boolean isBlank(String s)
    {
        return s == null || s.isBlank() || "null".equalsIgnoreCase(s);
    }
}
