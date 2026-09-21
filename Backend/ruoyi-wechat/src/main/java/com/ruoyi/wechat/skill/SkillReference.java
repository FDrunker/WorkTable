package com.ruoyi.wechat.skill;

/**
 * 一篇参考文档。
 *
 * @param path        相对 skill 根目录的路径，例如 {@code references/knowledge/03-依恋理论与情绪调节.md}
 * @param displayName 展示名（文件名去扩展名）
 * @param content     正文
 * @param contentHash 内容校验值，用于判断仓库更新后是否需要重建索引
 */
public record SkillReference(String path, String displayName, String content, String contentHash)
{
    public int charCount()
    {
        return content == null ? 0 : content.length();
    }
}
