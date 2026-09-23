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
    void splitsByDefaultNewlineAndKeepsChunkBudget()
    {
        String content = "第一段内容。\n第二段内容。\n第三段内容。\n第四段内容。";

        List<String> chunks = chunker.split(content, "\\n", 12, 2);

        assertTrue(chunks.size() > 1);
        assertTrue(chunks.stream().allMatch(chunk -> chunk.length() <= 12));
        assertTrue(chunks.get(0).contains("第一段"));
    }

    @Test
    void hardSplitsLongParagraphWithOverlap()
    {
        String content = "abcdefghijklmnopqrstuvwxyz";

        List<String> chunks = chunker.split(content, "\n", 10, 3);

        assertEquals(4, chunks.size());
        assertEquals("hij", chunks.get(1).substring(0, 3));
        assertEquals("klmnopq", chunks.get(1).substring(3, 10));
    }

    @Test
    void rejectsOverlapNotSmallerThanChunkSize()
    {
        assertThrows(ServiceException.class, () -> chunker.split("content", "\n", 10, 10));
    }
}
