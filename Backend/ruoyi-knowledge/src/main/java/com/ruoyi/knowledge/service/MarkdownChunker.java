package com.ruoyi.knowledge.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;

/**
 * 按 Markdown 段落切分文档，并在字符维度控制块大小和重叠。
 */
@Component
public class MarkdownChunker
{
    private static final String PARAGRAPH_SEPARATOR = "\n\n";

    private static final Pattern FENCE_PATTERN = Pattern.compile("^ {0,3}(`{3,}|~{3,})(.*)$");

    public List<String> split(String content, Integer chunkSize, Integer chunkOverlap)
    {
        if (StringUtils.isBlank(content))
        {
            throw new ServiceException("Markdown 内容不能为空");
        }
        int size = chunkSize == null ? 1024 : chunkSize;
        int overlap = chunkOverlap == null ? 100 : chunkOverlap;
        if (size < 1)
        {
            throw new ServiceException("块大小必须大于 0");
        }
        if (overlap < 0 || overlap >= size)
        {
            throw new ServiceException("重叠长度必须大于等于 0 且小于块大小");
        }

        String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
        List<String> paragraphs = splitMarkdownParagraphs(normalized);
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : paragraphs)
        {
            appendParagraph(chunks, current, paragraph, size, overlap);
        }
        if (!current.toString().isBlank())
        {
            chunks.add(current.toString().trim());
        }
        return chunks;
    }

    private List<String> splitMarkdownParagraphs(String content)
    {
        List<String> paragraphs = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String fenceMarker = null;
        for (String line : content.split("\n", -1))
        {
            if (fenceMarker == null)
            {
                String marker = openingFenceMarker(line);
                if (marker != null)
                {
                    flushParagraph(paragraphs, current);
                    current.append(line);
                    fenceMarker = marker;
                    continue;
                }
                if (line.isBlank())
                {
                    flushParagraph(paragraphs, current);
                    continue;
                }
            }
            else
            {
                appendLine(current, line);
                if (isClosingFence(line, fenceMarker))
                {
                    flushParagraph(paragraphs, current);
                    fenceMarker = null;
                }
                continue;
            }
            appendLine(current, line);
        }
        flushParagraph(paragraphs, current);
        return paragraphs;
    }

    private void appendParagraph(List<String> chunks, StringBuilder current, String paragraph,
            int chunkSize, int overlap)
    {
        if (paragraph.length() > chunkSize)
        {
            List<String> hardParts = hardSplit(paragraph, chunkSize, overlap);
            if (!current.isEmpty())
            {
                flush(chunks, current, 0);
            }
            for (String hardPart : hardParts)
            {
                chunks.add(hardPart.trim());
            }
            current.setLength(0);
            return;
        }

        if (!current.isEmpty() && current.length() + PARAGRAPH_SEPARATOR.length() + paragraph.length() > chunkSize)
        {
            flush(chunks, current, overlap);
            int maxOverlap = Math.max(0,
                    chunkSize - PARAGRAPH_SEPARATOR.length() - paragraph.length());
            if (current.length() > maxOverlap)
            {
                String overlapText = tail(current.toString(), maxOverlap);
                current.setLength(0);
                current.append(overlapText);
            }
        }
        if (!current.isEmpty())
        {
            current.append(PARAGRAPH_SEPARATOR);
        }
        current.append(paragraph);
    }

    private void flush(List<String> chunks, StringBuilder current, int overlap)
    {
        String value = current.toString().trim();
        if (!value.isBlank())
        {
            chunks.add(value);
        }
        current.setLength(0);
        if (overlap > 0 && !value.isBlank())
        {
            current.append(tail(value, overlap));
        }
    }

    private void flushParagraph(List<String> paragraphs, StringBuilder current)
    {
        String value = current.toString().trim();
        if (!value.isBlank())
        {
            paragraphs.add(value);
        }
        current.setLength(0);
    }

    private void appendLine(StringBuilder value, String line)
    {
        if (!value.isEmpty())
        {
            value.append('\n');
        }
        value.append(line);
    }

    private String openingFenceMarker(String line)
    {
        var matcher = FENCE_PATTERN.matcher(line);
        if (!matcher.matches())
        {
            return null;
        }
        return matcher.group(1);
    }

    private boolean isClosingFence(String line, String openingMarker)
    {
        var matcher = FENCE_PATTERN.matcher(line);
        if (!matcher.matches())
        {
            return false;
        }
        String closingMarker = matcher.group(1);
        return closingMarker.charAt(0) == openingMarker.charAt(0)
                && closingMarker.length() >= openingMarker.length()
                && matcher.group(2).isBlank();
    }

    private List<String> hardSplit(String text, int chunkSize, int overlap)
    {
        List<String> result = new ArrayList<>();
        int start = 0;
        while (start < text.length())
        {
            int end = Math.min(start + chunkSize, text.length());
            result.add(text.substring(start, end));
            if (end == text.length())
            {
                break;
            }
            start = Math.max(end - overlap, start + 1);
        }
        return result;
    }

    private String tail(String text, int length)
    {
        return text.length() <= length ? text : text.substring(text.length() - length);
    }
}
