package com.ruoyi.wechat.skill;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.wechat.llm.LlmClient;
import com.ruoyi.wechat.llm.LlmException;
import com.ruoyi.wechat.llm.LlmMessage;
import com.ruoyi.wechat.llm.LlmResult;

/**
 * 知识库路由 —— 用一次轻量 LLM 调用替代"会推理的 Agent 自行读文档"。
 *
 * <p>这是把 Codex Skill 搬到 Java 里最别扭的一环：SKILL.md 里的路由表写的是
 * "用户问什么 → 就读哪篇"，原本由 Agent 的推理能力承担，Java 端做不了语义判断。
 * 三种可选方案里我们选"小模型读路由表"而不是 embedding 检索：
 * <ul>
 *   <li>知识库只有 40 篇、约 250KB，建向量索引的工程量与维护成本不划算；</li>
 *   <li>可解释 —— 能看到它选了哪几篇、为什么，选错了直接改 routing.md；</li>
 *   <li>无额外依赖，不需要引入向量库。</li>
 * </ul>
 * 代价是每次分析多一次小模型调用，成本可忽略。
 */
@Component
public class SkillRoutingService
{
    private static final Logger log = LoggerFactory.getLogger(SkillRoutingService.class);

    private final SkillResourceLoader loader;

    private final LlmClient llmClient;

    public SkillRoutingService(SkillResourceLoader loader, LlmClient llmClient)
    {
        this.loader = loader;
        this.llmClient = llmClient;
    }

    /**
     * 选择本次要加载的参考文档。
     *
     * <p>路由失败<b>绝不阻断</b>主分析：退回只加载 SKILL.md 本身，
     * 因为"没有参考资料"远好于"整个分析失败"。
     */
    public List<SkillReference> route(String userGoal, String statsBlock)
    {
        List<SkillReference> all = loader.allReferences();
        if (all.isEmpty())
        {
            log.warn("知识库为空，跳过路由（请把 references/ 放入 resources/skill/ 目录）");
            return List.of();
        }

        try
        {
            String catalog = renderCatalog();
            String system = SkillPromptBuilder.buildRoutingSystemPrompt();
            String user = SkillPromptBuilder.buildRoutingUserPrompt(userGoal, statsBlock,
                    loader.getRoutingTable(), catalog);

            LlmResult result = llmClient.chat(List.of(LlmMessage.system(system), LlmMessage.user(user)));
            List<SkillReference> selected = resolveRefs(result.content());

            log.info("知识库路由完成：从 {} 篇中选中 {} 篇（{} tokens）",
                    all.size(), selected.size(), result.totalTokens());
            return selected;
        }
        catch (LlmException e)
        {
            log.warn("知识库路由失败，退回仅使用 SKILL.md 行为内核：{}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 解析模型返回的路径列表，并<b>用白名单过滤</b>。
     *
     * <p>过滤是必需的：模型可能编造路径或改写文件名，
     * 不过滤就会出现"注入了一篇不存在的资料"这种难以排查的问题。
     */
    private List<SkillReference> resolveRefs(String content)
    {
        if (content == null || content.isBlank())
        {
            return List.of();
        }
        String json = extractJson(content);
        try
        {
            JSONObject obj = JSON.parseObject(json);
            JSONArray refs = obj.getJSONArray("refs");
            if (refs == null)
            {
                return List.of();
            }
            List<SkillReference> result = new java.util.ArrayList<>();
            for (int i = 0; i < refs.size(); i++)
            {
                String path = refs.getString(i);
                SkillReference ref = loader.getReference(path);
                if (ref == null)
                {
                    log.warn("路由返回了目录中不存在的资料，已忽略：{}", path);
                    continue;
                }
                if (!result.contains(ref))
                {
                    result.add(ref);
                }
            }
            return result;
        }
        catch (Exception e)
        {
            log.warn("无法解析路由结果，退回不使用参考资料。原始输出：{}", truncate(content, 200));
            return List.of();
        }
    }

    private String renderCatalog()
    {
        StringBuilder sb = new StringBuilder();
        loader.describeCatalog().forEach((path, name) -> sb.append("- ").append(path).append('\n'));
        return sb.toString();
    }

    private String extractJson(String content)
    {
        String trimmed = content.trim();
        if (trimmed.startsWith("```"))
        {
            // 容错：模型有时会包一层代码块，尽管 prompt 里已明确禁止
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

    private String truncate(String s, int max)
    {
        return s == null ? "" : (s.length() <= max ? s : s.substring(0, max) + "...");
    }
}
