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
    void splitsByMarkdownParagraphsAndKeepsChunkBudget()
    {
        String firstParagraph = "第一段内容。\n同一段续行。";
        String content = firstParagraph + "\n\n第二段内容。\n\n第三段内容。";

        List<String> chunks = chunker.split(content, 18, 2);

        assertTrue(chunks.size() > 1);
        assertTrue(chunks.stream().allMatch(chunk -> chunk.length() <= 18));
        assertEquals(firstParagraph, chunks.get(0));
        assertTrue(chunks.get(1).contains("第二段内容。"));
        assertTrue(chunks.get(1).contains("第三段内容。"));
    }

    @Test
    void keepsFencedCodeBlockAsOneMarkdownParagraph()
    {
        String code = "```java\nline 1\n\nline 2\n```";
        String content = "前言\n\n" + code + "\n\n后记";

        List<String> chunks = chunker.split(content, code.length(), 0);

        assertEquals(List.of("前言", code, "后记"), chunks);
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
