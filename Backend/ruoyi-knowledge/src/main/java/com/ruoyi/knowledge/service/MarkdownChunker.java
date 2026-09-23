package com.ruoyi.knowledge.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;

/**
 * 按用户分隔符切分 Markdown，并在字符维度控制块大小和重叠。
 */
@Component
public class MarkdownChunker
{
    public List<String> split(String content, String separator, Integer chunkSize, Integer chunkOverlap)
    {
        if (StringUtils.isBlank(content))
        {
            throw new ServiceException("Markdown 内容不能为空");
        }
        String actualSeparator = normalizeSeparator(separator);
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
        String[] parts = normalized.split(Pattern.quote(actualSeparator), -1);
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String part : parts)
        {
            appendPart(chunks, current, part, actualSeparator, size, overlap);
        }
        if (!current.toString().isBlank())
        {
            chunks.add(current.toString().trim());
        }
        return chunks;
    }

    private void appendPart(List<String> chunks, StringBuilder current, String part, String separator,
            int chunkSize, int overlap)
    {
        if (part.length() > chunkSize)
        {
            List<String> hardParts = hardSplit(part, chunkSize, overlap);
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

        String addition = current.isEmpty() ? part : separator + part;
        if (current.length() + addition.length() <= chunkSize)
        {
            current.append(addition);
            return;
        }

        flush(chunks, current, overlap);
        String prefix = current.toString();
        String value = prefix + separator + part;
        if (value.length() > chunkSize)
        {
            value = value.substring(value.length() - chunkSize);
        }
        current.setLength(0);
        current.append(value);
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

    private String normalizeSeparator(String separator)
    {
        if (StringUtils.isBlank(separator))
        {
            return "\n";
        }
        return switch (separator)
        {
            case "\\n" -> "\n";
            case "\\r\\n" -> "\r\n";
            case "\\t" -> "\t";
            default -> separator;
        };
    }
}
