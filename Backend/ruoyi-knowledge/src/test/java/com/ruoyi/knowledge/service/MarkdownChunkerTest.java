package com.ruoyi.knowledge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.ruoyi.common.exception.ServiceException;

class MarkdownChunkerTest
{
    private final MarkdownChunker chunker = new MarkdownChunker();

    @Test
    void keepsMarkdownHeadingHierarchyAsChunkContext()
    {
        String content = "# 一级标题\n\n"
                + "一级介绍\n\n"
                + "## 二级标题\n\n"
                + "二级内容\n\n"
                + "### 三级标题\n\n"
                + "三级内容";

        List<String> chunks = chunker.split(content, 128, 0);

        assertEquals(List.of(
                "# 一级标题\n\n一级介绍",
                "# 一级标题\n\n## 二级标题\n\n二级内容",
                "# 一级标题\n\n## 二级标题\n\n### 三级标题\n\n三级内容"), chunks);
    }

    @Test
    void keepsFencedCodeBlockAndListAsAtomicStructures()
    {
        String code = "```java\nline 1\n\nline 2\n```";
        String list = "- 第一项\n  \n  续行说明\n- 第二项";
        String content = "前言\n\n" + code + "\n\n" + list + "\n\n后记";

        List<String> chunks = chunker.split(content, code.length(), 0);

        assertTrue(chunks.contains(code));
        assertTrue(chunks.stream().anyMatch(chunk -> chunk.contains(list)));
        assertTrue(chunks.stream().allMatch(chunk -> chunk.length() <= code.length()));
    }

    @Test
    void keepsMarkdownTableAsOneStructure()
    {
        String table = "| 名称 | 数量 |\n| --- | --- |\n| A | 1 |\n| B | 2 |";
        String content = "说明\n\n" + table + "\n\n结束";

        List<String> chunks = chunker.split(content, table.length(), 0);

        assertEquals(List.of("说明", table, "结束"), chunks);
    }

    @Test
    void repeatsHeadingContextWhenSectionExceedsChunkSize()
    {
        String content = "# 标题\n\nabcdefghijklmnopqrstuvwxyz";

        List<String> chunks = chunker.split(content, 20, 2);

        assertEquals(List.of(
                "# 标题\n\nabcdefghijklmn",
                "# 标题\n\nmnopqrstuvwxyz"), chunks);
        assertTrue(chunks.stream().allMatch(chunk -> chunk.length() <= 20));
    }

    @Test
    void hardSplitsLongParagraphWithOverlap()
    {
        String content = "abcdefghijklmnopqrstuvwxyz";

        List<String> chunks = chunker.split(content, 10, 3);

        assertEquals(4, chunks.size());
        assertEquals("hij", chunks.get(1).substring(0, 3));
        assertEquals("klmnopq", chunks.get(1).substring(3, 10));
    }

    @Test
    void rejectsOverlapNotSmallerThanChunkSize()
    {
        assertThrows(ServiceException.class, () -> chunker.split("content", 10, 10));
    }
}
