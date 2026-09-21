package com.ruoyi.wechat.skill;

import java.util.List;

/**
 * 军师 skill 在 Java 侧的表示。
 *
 * <p>需要明确一点：goutoujunshi <b>没有运行时</b>。它是一个 Markdown 知识库
 * （SKILL.md 作为行为与路由内核 + references/ 下的知识文档），
 * 原本依赖会推理的 Agent 自行"按需加载"。在 Java 里我们把这件事拆成两步：
 * 先用一次小模型调用决定加载哪几篇（见 {@code SkillRoutingService}），
 * 再把选中的文档注入主调用的 system prompt。
 *
 * @param skillMd      SKILL.md 正文（行为内核）
 * @param references   本次选中的参考文档
 * @param routingTable 路由表原文，供路由阶段使用
 */
public record SkillBundle(String skillMd, List<SkillReference> references, String routingTable)
{
    public boolean hasReferences()
    {
        return references != null && !references.isEmpty();
    }

    /**
     * 拼接成注入 system prompt 的知识块。
     */
    public String render()
    {
        StringBuilder sb = new StringBuilder();
        if (skillMd != null && !skillMd.isBlank())
        {
            sb.append(skillMd).append("\n\n");
        }
        if (hasReferences())
        {
            sb.append("\n\n===== 以下为本次按需加载的参考资料 =====\n");
            for (SkillReference ref : references)
            {
                sb.append("\n----- 资料：").append(ref.displayName()).append(" -----\n");
                sb.append(ref.content()).append('\n');
            }
        }
        return sb.toString();
    }
}
