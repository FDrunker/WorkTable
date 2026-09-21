package com.ruoyi.wechat.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link AnalysisResultParser} 单元测试。
 *
 * <p>这是"模型输出格式契约"的守门人。SKILL.md 要求的五步流程必须在解析后仍然完整，
 * 否则界面上会出现空白的段落，而用户只会觉得"分析没生成"。
 */
class AnalysisResultParserTest
{
    private final AnalysisResultParser parser = new AnalysisResultParser();

    private static final String VALID_JSON = """
            {
              "emotionLanding": "你现在的焦虑主要来自对方回复变慢。",
              "facts": {
                "knownFacts": ["[2026-04-02 10:30] 你主动发起邀约", "[2026-04-02 16:30] 对方 6 小时后回复"],
                "inferences": ["对方可能工作较忙"],
                "keyUnknowns": ["对方近期的日程安排"]
              },
              "interest": { "evaluation": "互惠程度中等，你主动次数明显更多。" },
              "advice": {
                "primary": "下次由对方先开口，你等待观察。",
                "reasons": ["主动次数已明显失衡", "连续追问会降低吸引力"],
                "steadyVariant": "三天后发一条轻松的话题。",
                "flirtyVariant": null,
                "assertiveVariant": null
              },
              "actions": {
                "immediateAction": "今天不再发消息。",
                "observationWindow": "观察三天内对方是否主动。",
                "stopCondition": "一周内对方从未主动则降级投入。",
                "followUpQuestions": ["你们认识多久了？"]
              },
              "crisisReferral": false
            }
            """;

    @Test
    @DisplayName("完整 JSON 被解析成五步结构")
    void shouldParseFullStructuredResult()
    {
        AnalysisResult result = parser.parse(VALID_JSON);

        assertNotNull(result);
        assertFalse(result.crisisReferral());
        assertTrue(result.emotionLanding().contains("焦虑"));

        assertEquals(2, result.facts().knownFacts().size());
        assertEquals(1, result.facts().inferences().size());
        assertEquals(1, result.facts().keyUnknowns().size());

        assertNotNull(result.interest());
        assertEquals("下次由对方先开口，你等待观察。", result.advice().primary());
        assertEquals(2, result.advice().reasons().size());
        assertNull(result.advice().flirtyVariant());

        assertEquals("今天不再发消息。", result.actions().immediateAction());
        assertEquals(1, result.actions().followUpQuestions().size());
    }

    @Test
    @DisplayName("模型套了 markdown 代码块围栏也能解析")
    void shouldTolerateMarkdownFence()
    {
        String wrapped = "```json\n" + VALID_JSON + "\n```";

        AnalysisResult result = parser.parse(wrapped);

        assertNotNull(result, "prompt 已禁止围栏，但模型偶尔会加，解析必须容错");
        assertEquals("下次由对方先开口，你等待观察。", result.advice().primary());
    }

    @Test
    @DisplayName("JSON 前后有多余解释文字时截取主体")
    void shouldExtractJsonFromSurroundingText()
    {
        String noisy = "好的，这是分析结果：\n" + VALID_JSON + "\n希望对你有帮助。";

        AnalysisResult result = parser.parse(noisy);

        assertNotNull(result);
        assertNotNull(result.actions());
    }

    @Test
    @DisplayName("命中安全红线时只认 crisisReferral")
    void shouldParseCrisisReferral()
    {
        String crisis = """
                {
                  "emotionLanding": "先确认你当下的安全。",
                  "facts": null, "interest": null,
                  "advice": { "primary": "请联系当地紧急服务。" },
                  "actions": null,
                  "crisisReferral": true
                }
                """;

        AnalysisResult result = parser.parse(crisis);

        assertNotNull(result);
        assertTrue(result.crisisReferral());
    }

    @Test
    @DisplayName("完全无法解析时返回 null，交由调用方决定降级策略")
    void shouldReturnNullForUnparsableContent()
    {
        assertNull(parser.parse(null));
        assertNull(parser.parse(""));
        assertNull(parser.parse("模型今天不太想干活"));
    }

    @Test
    @DisplayName("字段缺失不抛异常，缺失部分为 null 或空列表")
    void shouldTolerateMissingSections()
    {
        AnalysisResult result = parser.parse("{\"emotionLanding\":\"只有情绪落地\"}");

        assertNotNull(result);
        assertEquals("只有情绪落地", result.emotionLanding());
        assertNull(result.advice());
        assertNull(result.actions());
        assertFalse(result.crisisReferral());
    }

    @Test
    @DisplayName("字符串形式的 null 不应被当成有效内容")
    void shouldNormalizeStringNullInLists()
    {
        String json = """
                {"facts":{"knownFacts":["真实事实","null","  "],"inferences":[],"keyUnknowns":[]}}
                """;

        AnalysisResult result = parser.parse(json);

        assertNotNull(result);
        assertEquals(1, result.facts().knownFacts().size(),
                "字面量 null 与空白项都应被剔除");
        assertEquals("真实事实", result.facts().knownFacts().get(0));
    }
}
