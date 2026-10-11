package com.medifoodai.chunk;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 文本分块器
 * <p>
 * 该工具类用于将 Markdown 格式的文本，按照其固有的语义结构（如标题、列表）
 * 切分成一系列更小、更具内聚性的文本块 (Chunks)。
 * <p>
 * 这种智能分块策略，旨在为后续的向量化和语义搜索提供高质量的输入，
 * 从而最大程度地保留原始文本的语义信息。
 */
@Component
public class MarkdownChunker implements TextChunker {

    // 正则表达式，用于匹配 Markdown 的一级和二级标题 (e.g., "# Title", "## Subtitle")
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#|##)\s+(.*)");

    /**
     * 将给定的 Markdown 文本内容切分成多个语义块。
     *
     * @param markdownContent 完整的 Markdown 文本
     * @return 一个包含多个文本块的列表。如果输入为空，则返回空列表。
     */
    public List<String> chunk(String markdownContent) {
        List<String> chunks = new ArrayList<>();
        if (markdownContent == null || markdownContent.isBlank()) {
            return chunks;
        }

        // 按行分割文本
        String[] lines = markdownContent.split("\\R");
        StringBuilder currentChunk = new StringBuilder();

        for (String line : lines) {
            Matcher matcher = HEADING_PATTERN.matcher(line);
            // 当遇到一个新的标题时，意味着一个旧的语义块结束，一个新的语义块开始
            if (matcher.find()) {
                // 如果当前的块中有内容，则将其作为一个完整的 chunk 添加到列表中
                if (!currentChunk.isEmpty()) {
                    chunks.add(currentChunk.toString().trim());
                }
                // 开始一个新的 chunk，这个 chunk 的内容从当前这行（标题行）开始
                currentChunk = new StringBuilder(line + "\n");
            } else {
                // 如果不是标题行，则继续将该行追加到当前的 chunk 中
                currentChunk.append(line).append("\n");
            }
        }

        // 将最后一个 chunk 添加到列表中
        if (!currentChunk.isEmpty()) {
            chunks.add(currentChunk.toString().trim());
        }

        return chunks;
    }

    @Override
    public List<TextChunker.Chunk> chunk(String text, int maxChunkSize) {
        List<String> markdownChunks = chunk(text);
        List<TextChunker.Chunk> chunks = new ArrayList<>();
        int cursor = 0;
        int safeMaxChunkSize = Math.max(maxChunkSize, 1);

        for (String markdownChunk : markdownChunks) {
            if (markdownChunk == null || markdownChunk.isBlank()) {
                continue;
            }
            if (markdownChunk.length() <= safeMaxChunkSize) {
                int start = cursor;
                int end = start + markdownChunk.length();
                chunks.add(new TextChunker.Chunk(markdownChunk, start, end));
                cursor = end;
                continue;
            }

            int start = 0;
            while (start < markdownChunk.length()) {
                int end = Math.min(start + safeMaxChunkSize, markdownChunk.length());
                String part = markdownChunk.substring(start, end).trim();
                if (!part.isEmpty()) {
                    chunks.add(new TextChunker.Chunk(part, cursor + start, cursor + end));
                }
                start = end;
            }
            cursor += markdownChunk.length();
        }
        return chunks;
    }
}
