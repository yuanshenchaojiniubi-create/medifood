package com.medifoodai.chunk;

import java.util.List;

public interface TextChunker {

    List<Chunk> chunk(String text, int maxChunkSize);

    record Chunk(String content, int startIndex, int endIndex) {
        public String toDocument(String title, String type) {
            return """
                【%s】
                %s
                【内容】
                %s
                """.formatted(type, title, content);
        }
    }
}