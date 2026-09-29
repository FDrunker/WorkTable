package com.ruoyi.knowledge.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;

/**
 * 按 Markdown 结构切分文档，并在字符维度限制切片大小。
 */
@Component
public class MarkdownChunker
{
    private static final String BLOCK_SEPARATOR = "\n\n";

    private static final Pattern FENCE_PATTERN = Pattern.compile("^ {0,3}(`{3,}|~{3,})(.*)$");

    private static final Pattern ATX_HEADING_PATTERN = Pattern.compile("^ {0,3}(#{1,6})(?:\\s+|$).*$");

    private static final Pattern SETEXT_HEADING_PATTERN = Pattern.compile("^ {0,3}(=+|-+)\\s*$");

    private static final Pattern LIST_ITEM_PATTERN = Pattern.compile("^ {0,3}(?:[-+*]|\\d{1,9}[.)])\\s+\\S.*$");

    private static final Pattern THEMATIC_BREAK_PATTERN =
            Pattern.compile("^ {0,3}(?:(?:\\*\\s*){3,}|(?:-\\s*){3,}|(?:_\\s*){3,})$");

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
        List<MarkdownSection> sections = buildSections(parseBlocks(normalized));
        List<String> chunks = new ArrayList<>();
        for (MarkdownSection section : sections)
        {
            splitSection(section, size, overlap, chunks);
        }
        return chunks;
    }

    private List<MarkdownBlock> parseBlocks(String content)
    {
        List<MarkdownBlock> blocks = new ArrayList<>();
        String[] lines = content.split("\n", -1);
        int index = 0;
        while (index < lines.length)
        {
            if (lines[index].isBlank())
            {
                index++;
                continue;
            }
            String fenceMarker = openingFenceMarker(lines[index]);
            if (fenceMarker != null)
            {
                int start = index;
                index++;
                while (index < lines.length)
                {
                    String line = lines[index++];
                    if (isClosingFence(line, fenceMarker))
                    {
                        break;
                    }
                }
                blocks.add(new MarkdownBlock(0, joinLines(lines, start, index)));
                continue;
            }

            int headingLevel = headingLevel(lines[index]);
            if (headingLevel > 0)
            {
                blocks.add(new MarkdownBlock(headingLevel, lines[index].strip()));
                index++;
                continue;
            }

            int setextLevel = setextHeadingLevel(lines, index);
            if (setextLevel > 0)
            {
                blocks.add(new MarkdownBlock(setextLevel,
                        lines[index].strip() + "\n" + lines[index + 1].strip()));
                index += 2;
                continue;
            }

            if (isTableStart(lines, index))
            {
                int start = index;
                index += 2;
                while (index < lines.length && !lines[index].isBlank() && lines[index].contains("|"))
                {
                    index++;
                }
                blocks.add(new MarkdownBlock(0, joinLines(lines, start, index)));
                continue;
            }

            if (isBlockquote(lines[index]))
            {
                int start = index;
                index++;
                while (index < lines.length)
                {
                    if (isBlockquote(lines[index]))
                    {
                        index++;
                        continue;
                    }
                    if (lines[index].isBlank() && index + 1 < lines.length && isBlockquote(lines[index + 1]))
                    {
                        index++;
                        continue;
                    }
                    break;
                }
                blocks.add(new MarkdownBlock(0, joinLines(lines, start, index)));
                continue;
            }

            if (isListItem(lines[index]))
            {
                int start = index;
                index++;
                while (index < lines.length)
                {
                    if (isListContinuation(lines[index]))
                    {
                        index++;
                        continue;
                    }
                    if (lines[index].isBlank())
                    {
                        int next = nextNonBlankLine(lines, index + 1);
                        if (next < lines.length && isListContinuation(lines[next]))
                        {
                            index++;
                            continue;
                        }
                    }
                    break;
                }
                blocks.add(new MarkdownBlock(0, joinLines(lines, start, index)));
                continue;
            }

            int start = index;
            index++;
            while (index < lines.length && !lines[index].isBlank() && !startsBlock(lines, index))
            {
                index++;
            }
            blocks.add(new MarkdownBlock(0, joinLines(lines, start, index)));
        }
        return blocks;
    }

    private List<MarkdownSection> buildSections(List<MarkdownBlock> blocks)
    {
        List<MarkdownSection> sections = new ArrayList<>();
        List<MarkdownHeading> headingPath = new ArrayList<>();
        MarkdownSection current = new MarkdownSection(List.of(), new ArrayList<>());
        sections.add(current);

        for (MarkdownBlock block : blocks)
        {
            if (block.headingLevel() > 0)
            {
                while (!headingPath.isEmpty()
                        && headingPath.get(headingPath.size() - 1).level() >= block.headingLevel())
                {
                    headingPath.remove(headingPath.size() - 1);
                }
                headingPath.add(new MarkdownHeading(block.headingLevel(), block.content()));
                current = new MarkdownSection(List.copyOf(headingPath), new ArrayList<>());
                sections.add(current);
            }
            else
            {
                current.blocks().add(block);
            }
        }
        return sections.stream()
                .filter(section -> !section.blocks().isEmpty())
                .toList();
    }

    private void splitSection(MarkdownSection section, int chunkSize, int overlap, List<String> chunks)
    {
        String headingContext = section.headingPath().stream()
                .map(MarkdownHeading::content)
                .reduce((left, right) -> left + BLOCK_SEPARATOR + right)
                .orElse("");
        String separator = headingContext.isEmpty() ? "" : BLOCK_SEPARATOR;
        int bodyBudget = chunkSize - headingContext.length() - separator.length();
        if (bodyBudget < 1)
        {
            String fullText = headingContext + separator + joinBlocks(section.blocks());
            chunks.addAll(hardSplit(fullText, chunkSize, overlap));
            return;
        }

        StringBuilder current = new StringBuilder();
        for (MarkdownBlock block : section.blocks())
        {
            appendStructuredBlock(chunks, current, block.content(), headingContext, separator,
                    bodyBudget, overlap);
        }
        flushStructuredChunk(chunks, current, headingContext, separator);
    }

    private void appendStructuredBlock(List<String> chunks, StringBuilder current, String block,
            String headingContext, String headingSeparator, int bodyBudget, int overlap)
    {
        if (block.length() > bodyBudget)
        {
            flushStructuredChunk(chunks, current, headingContext, headingSeparator);
            int hardOverlap = Math.min(overlap, bodyBudget - 1);
            for (String part : hardSplit(block, bodyBudget, hardOverlap))
            {
                chunks.add(combine(headingContext, headingSeparator, part));
            }
            return;
        }

        if (current.isEmpty())
        {
            current.append(block);
            return;
        }
        if (current.length() + BLOCK_SEPARATOR.length() + block.length() <= bodyBudget)
        {
            current.append(BLOCK_SEPARATOR).append(block);
            return;
        }

        String previousBody = current.toString();
        flushStructuredChunk(chunks, current, headingContext, headingSeparator);
        int maxOverlap = Math.max(0, bodyBudget - BLOCK_SEPARATOR.length() - block.length());
        if (overlap > 0 && maxOverlap > 0)
        {
            current.append(tail(previousBody, Math.min(overlap, maxOverlap)));
        }
        if (!current.isEmpty())
        {
            current.append(BLOCK_SEPARATOR);
        }
        current.append(block);
    }

    private void flushStructuredChunk(List<String> chunks, StringBuilder current, String headingContext,
            String headingSeparator)
    {
        if (!current.isEmpty())
        {
            chunks.add(combine(headingContext, headingSeparator, current.toString()));
            current.setLength(0);
        }
    }

    private String combine(String headingContext, String headingSeparator, String body)
    {
        if (headingContext.isEmpty())
        {
            return body.strip();
        }
        if (body.isBlank())
        {
            return headingContext;
        }
        return headingContext + headingSeparator + body.strip();
    }

    private String joinBlocks(List<MarkdownBlock> blocks)
    {
        StringBuilder result = new StringBuilder();
        for (MarkdownBlock block : blocks)
        {
            if (!result.isEmpty())
            {
                result.append(BLOCK_SEPARATOR);
            }
            result.append(block.content());
        }
        return result.toString();
    }

    private int headingLevel(String line)
    {
        Matcher matcher = ATX_HEADING_PATTERN.matcher(line);
        return matcher.matches() ? matcher.group(1).length() : 0;
    }

    private int setextHeadingLevel(String[] lines, int index)
    {
        if (index + 1 >= lines.length || lines[index].isBlank())
        {
            return 0;
        }
        Matcher matcher = SETEXT_HEADING_PATTERN.matcher(lines[index + 1]);
        if (!matcher.matches())
        {
            return 0;
        }
        return matcher.group(1).charAt(0) == '=' ? 1 : 2;
    }

    private boolean isTableStart(String[] lines, int index)
    {
        return index + 1 < lines.length
                && lines[index].contains("|")
                && isTableDelimiter(lines[index + 1]);
    }

    private boolean isTableDelimiter(String line)
    {
        String value = line.strip();
        if (value.startsWith("|"))
        {
            value = value.substring(1);
        }
        if (value.endsWith("|"))
        {
            value = value.substring(0, value.length() - 1);
        }
        String[] cells = value.split("\\|");
        if (cells.length == 0)
        {
            return false;
        }
        for (String cell : cells)
        {
            if (!cell.strip().matches(":?-{3,}:?"))
            {
                return false;
            }
        }
        return true;
    }

    private boolean isBlockquote(String line)
    {
        return line.stripLeading().startsWith(">");
    }

    private boolean isListItem(String line)
    {
        return LIST_ITEM_PATTERN.matcher(line).matches() && !isThematicBreak(line);
    }

    private boolean isListContinuation(String line)
    {
        return isListItem(line) || line.startsWith("\t") || line.startsWith("  ");
    }

    private boolean isThematicBreak(String line)
    {
        return THEMATIC_BREAK_PATTERN.matcher(line).matches();
    }

    private boolean startsBlock(String[] lines, int index)
    {
        String line = lines[index];
        return openingFenceMarker(line) != null
                || headingLevel(line) > 0
                || isTableStart(lines, index)
                || isBlockquote(line)
                || isListItem(line)
                || isThematicBreak(line);
    }

    private String openingFenceMarker(String line)
    {
        Matcher matcher = FENCE_PATTERN.matcher(line);
        return matcher.matches() ? matcher.group(1) : null;
    }

    private boolean isClosingFence(String line, String openingMarker)
    {
        Matcher matcher = FENCE_PATTERN.matcher(line);
        if (!matcher.matches())
        {
            return false;
        }
        String closingMarker = matcher.group(1);
        return closingMarker.charAt(0) == openingMarker.charAt(0)
                && closingMarker.length() >= openingMarker.length()
                && matcher.group(2).isBlank();
    }

    private String joinLines(String[] lines, int start, int end)
    {
        return String.join("\n", List.of(lines).subList(start, end)).strip();
    }

    private int nextNonBlankLine(String[] lines, int start)
    {
        int index = start;
        while (index < lines.length && lines[index].isBlank())
        {
            index++;
        }
        return index;
    }

    private List<String> hardSplit(String text, int chunkSize, int overlap)
    {
        List<String> result = new ArrayList<>();
        int start = 0;
        while (start < text.length())
        {
            int end = Math.min(start + chunkSize, text.length());
            result.add(text.substring(start, end).strip());
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

    private record MarkdownBlock(int headingLevel, String content)
    {
    }

    private record MarkdownHeading(int level, String content)
    {
    }

    private record MarkdownSection(List<MarkdownHeading> headingPath, List<MarkdownBlock> blocks)
    {
    }
}
