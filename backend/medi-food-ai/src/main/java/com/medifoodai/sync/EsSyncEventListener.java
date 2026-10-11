package com.medifoodai.sync;

import cn.hutool.core.util.StrUtil;
import com.medifood.common.sync.EsSyncEvent;
import com.medifood.common.sync.EsSyncEventPublisher;
import com.medifood.common.util.ElasticsearchUtil;
import com.medifood.common.util.IdempotencyUtil;
import com.medifoodai.chunk.MarkdownChunker;
import com.medifoodai.service.EmbeddingService;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Component
public class EsSyncEventListener {

    @Autowired
    private ElasticsearchUtil elasticsearchUtil;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private MarkdownChunker markdownChunker;

    @Autowired
    private EsSyncEventPublisher esSyncEventPublisher;

    @Autowired
    private IdempotencyUtil idempotencyUtil;

    @Value("${es.sync.retry.max:3}")
    private int maxRetry;

    private static final String KNOWLEDGE_INDEX = "knowledge_index";
    private static final String RECIPE_INDEX = "recipe_index";
    private static final String KNOWLEDGE_SEMANTIC = "knowledge_semantic";
    private static final String RECIPE_SEMANTIC = "recipe_semantic";
    private static final String QA_INDEX = "qa_index";
    private static final String QA_SEMANTIC = "qa_semantic";
    private static final String COMMUNITY_INDEX = "community_index";
    private static final String COMMUNITY_SEMANTIC = "community_semantic";

    /** 防止 Embedding 不可用日志刷屏，同类型只警告一次 */
    private volatile boolean knowledgeVectorSkipped = false;
    private volatile boolean recipeVectorSkipped = false;

    @RabbitListener(queues = "medifood.es.sync.queue", containerFactory = "esSyncListenerContainerFactory")
    public void handleEsSyncEvent(EsSyncEvent event, Message message, Channel channel) {
        if (event == null || event.getEntityId() == null) {
            log.warn("接收到无效的ES同步事件: {}", event);
            basicAck(channel, message);
            return;
        }

        if (idempotencyUtil.isConsumed(event.getMessageId())) {
            log.info("ES同步消息已消费（幂等跳过）: messageId={}", event.getMessageId());
            basicAck(channel, message);
            return;
        }

        try {
            switch (event.getEntityType()) {
                case KNOWLEDGE -> handleKnowledgeSync(event);
                case RECIPE -> handleRecipeSync(event);
                case QA -> handleQaSync(event);
                case COMMUNITY -> handleCommunitySync(event);
                default -> log.warn("未知的实体类型: {}", event.getEntityType());
            }
        } catch (Exception e) {
            log.error("ES同步处理异常: type={}, id={}, syncType={}",
                    event.getEntityType(), event.getEntityId(), event.getSyncType(), e);
            handleRetry(event);
            basicNack(channel, message);
            return;
        }
        basicAck(channel, message);
    }

    private void handleKnowledgeSync(EsSyncEvent event) {
        Long entityId = event.getEntityId();
        switch (event.getSyncType()) {
            case CREATE, UPDATE -> {
                Map<String, Object> payload = event.getPayload();
                if (payload == null || payload.isEmpty()) {
                    log.warn("知识同步事件缺少快照数据，跳过处理: id={}, syncType={}", entityId, event.getSyncType());
                    return;
                }
                if (!isPublished(payload)) {
                    log.info("知识非发布状态，删除索引: id={}, status={}", entityId, getInteger(payload, "status"));
                    deleteDocuments(KNOWLEDGE_INDEX, KNOWLEDGE_SEMANTIC, "knowledgeId", entityId);
                    return;
                }
                deleteVectorDocuments(KNOWLEDGE_SEMANTIC, "knowledgeId", entityId);
                indexKnowledge(payload, event.getVersion());
                indexKnowledgeVector(payload, event.getVersion());
            }
            case DELETE -> deleteDocuments(KNOWLEDGE_INDEX, KNOWLEDGE_SEMANTIC, "knowledgeId", entityId);
        }
    }

    private void handleRecipeSync(EsSyncEvent event) {
        Long entityId = event.getEntityId();
        switch (event.getSyncType()) {
            case CREATE, UPDATE -> {
                Map<String, Object> payload = event.getPayload();
                if (payload == null || payload.isEmpty()) {
                    log.warn("食谱同步事件缺少快照数据，跳过处理: id={}, syncType={}", entityId, event.getSyncType());
                    return;
                }
                if (!isPublished(payload)) {
                    log.info("食谱非发布状态，删除索引: id={}, status={}", entityId, getInteger(payload, "status"));
                    deleteDocuments(RECIPE_INDEX, RECIPE_SEMANTIC, "recipeId", entityId);
                    return;
                }
                deleteVectorDocuments(RECIPE_SEMANTIC, "recipeId", entityId);
                indexRecipe(payload, event.getVersion());
                indexRecipeVector(payload, event.getVersion());
            }
            case DELETE -> deleteDocuments(RECIPE_INDEX, RECIPE_SEMANTIC, "recipeId", entityId);
        }
    }

    private void handleQaSync(EsSyncEvent event) {
        Long entityId = event.getEntityId();
        switch (event.getSyncType()) {
            case CREATE, UPDATE -> {
                Map<String, Object> payload = event.getPayload();
                if (payload == null || payload.isEmpty()) {
                    log.warn("问答同步事件缺少快照数据，跳过: id={}", entityId);
                    return;
                }
                if (!isPublished(payload)) {
                    deleteDocuments(QA_INDEX, QA_SEMANTIC, "questionId", entityId);
                    return;
                }
                deleteVectorDocuments(QA_SEMANTIC, "questionId", entityId);
                indexQa(payload, event.getVersion());
                indexQaVector(payload, event.getVersion());
            }
            case DELETE -> deleteDocuments(QA_INDEX, QA_SEMANTIC, "questionId", entityId);
        }
    }

    private void handleCommunitySync(EsSyncEvent event) {
        Long entityId = event.getEntityId();
        switch (event.getSyncType()) {
            case CREATE, UPDATE -> {
                Map<String, Object> payload = event.getPayload();
                if (payload == null || payload.isEmpty()) {
                    log.warn("社区同步事件缺少快照数据，跳过: id={}", entityId);
                    return;
                }
                if (!isPublished(payload)) {
                    deleteDocuments(COMMUNITY_INDEX, COMMUNITY_SEMANTIC, "postId", entityId);
                    return;
                }
                deleteVectorDocuments(COMMUNITY_SEMANTIC, "postId", entityId);
                indexCommunity(payload, event.getVersion());
                indexCommunityVector(payload, event.getVersion());
            }
            case DELETE -> deleteDocuments(COMMUNITY_INDEX, COMMUNITY_SEMANTIC, "postId", entityId);
        }
    }

    private void indexQa(Map<String, Object> payload, Long version) {
        String id = getString(payload, "id");
        Map<String, Object> source = new HashMap<>();
        source.put("id", id);
        source.put("title", getString(payload, "title"));
        source.put("content", getString(payload, "content"));
        source.put("questionId", getLong(payload, "id"));
        source.put("version", version);
        elasticsearchUtil.indexDocument(QA_INDEX, id, source);
        log.info("问答索引成功: id={}", id);
    }

    private void indexQaVector(Map<String, Object> payload, Long version) {
        String questionId = getString(payload, "id");
        String title = getString(payload, "title");
        String content = safeText(getString(payload, "content"));
        if (content.isBlank() && title.isBlank()) {
            log.warn("问答内容为空，跳过向量索引: id={}", questionId);
            return;
        }
        try {
            String text = (title + " " + content).trim();
            float[] vector = embeddingService.embed(text);
            Map<String, Object> source = new HashMap<>();
            source.put("id", questionId);
            source.put("questionId", getLong(payload, "id"));
            source.put("title", title);
            source.put("content", content);
            source.put("entityType", "qa");
            source.put("version", version);
            elasticsearchUtil.indexDocumentWithVector(QA_SEMANTIC, questionId, source, vector);
            log.info("问答向量索引成功: id={}", questionId);
        } catch (Exception e) {
            log.error("索引问答向量失败: questionId={}", questionId, e);
        }
    }

    private void indexCommunity(Map<String, Object> payload, Long version) {
        String id = getString(payload, "id");
        Map<String, Object> source = new HashMap<>();
        source.put("id", id);
        source.put("title", getString(payload, "title"));
        source.put("content", getString(payload, "content"));
        source.put("postId", getLong(payload, "id"));
        source.put("version", version);
        elasticsearchUtil.indexDocument(COMMUNITY_INDEX, id, source);
        log.info("社区帖子索引成功: id={}", id);
    }

    private void indexCommunityVector(Map<String, Object> payload, Long version) {
        String postId = getString(payload, "id");
        String title = getString(payload, "title");
        String content = safeText(getString(payload, "content"));
        if (content.isBlank() && title.isBlank()) {
            log.warn("社区内容为空，跳过向量索引: id={}", postId);
            return;
        }
        try {
            String text = (title + " " + content).trim();
            float[] vector = embeddingService.embed(text);
            Map<String, Object> source = new HashMap<>();
            source.put("id", postId);
            source.put("postId", getLong(payload, "id"));
            source.put("title", title);
            source.put("content", content);
            source.put("entityType", "community");
            source.put("version", version);
            elasticsearchUtil.indexDocumentWithVector(COMMUNITY_SEMANTIC, postId, source, vector);
            log.info("社区帖子向量索引成功: id={}", postId);
        } catch (Exception e) {
            log.error("索引社区帖子向量失败: postId={}", postId, e);
        }
    }

    private void indexKnowledge(Map<String, Object> payload, Long version) {
        String id = getString(payload, "id");
        Map<String, Object> source = new HashMap<>();
        source.put("id", id);
        source.put("title", getString(payload, "title"));
        source.put("content", getString(payload, "content"));
        source.put("categoryId", getLong(payload, "categoryId"));
        source.put("ingredientNames", getStringList(payload, "ingredientNames"));
        source.put("version", version);
        elasticsearchUtil.indexDocument(KNOWLEDGE_INDEX, id, source);
        log.info("知识索引成功: id={}", id);
    }

    private void indexRecipe(Map<String, Object> payload, Long version) {
        String id = getString(payload, "id");
        Map<String, Object> source = new HashMap<>();
        source.put("id", id);
        source.put("title", getString(payload, "title"));
        source.put("description", getString(payload, "description"));
        source.put("ingredients", getString(payload, "ingredients"));
        source.put("steps", getString(payload, "steps"));
        source.put("ingredientTexts", getStringList(payload, "ingredientTexts"));
        source.put("stepTexts", getStringList(payload, "stepTexts"));
        source.put("tagNames", getStringList(payload, "tagNames"));
        source.put("suitableFor", getString(payload, "suitableFor"));
        source.put("effects", getString(payload, "effects"));
        source.put("version", version);
        elasticsearchUtil.indexDocument(RECIPE_INDEX, id, source);
        log.info("食谱索引成功: id={}", id);
    }

    private void indexKnowledgeVector(Map<String, Object> payload, Long version) {
        String knowledgeId = getString(payload, "id");
        String title = getString(payload, "title");
        String content = safeText(getString(payload, "content"));
        List<SemanticChunk> chunks = buildKnowledgeChunks(payload);
        if (chunks.isEmpty()) {
            log.warn("知识内容为空或无法分块，跳过向量索引: id={}", knowledgeId);
            return;
        }

        log.info("知识 id={} 被分成了 {} 个块，准备进行向量化...", knowledgeId, chunks.size());

        for (int i = 0; i < chunks.size(); i++) {
            SemanticChunk chunk = chunks.get(i);
            try {
                float[] vector = embeddingService.embed(chunk.text());
                // 零向量表示 Embedding 服务不可用，跳过索引
                if (isZeroVector(vector)) {
                    if (!knowledgeVectorSkipped) {
                        knowledgeVectorSkipped = true;
                        log.warn("Embedding 服务不可用，知识向量索引已跳过（后续同类消息不再提示）");
                    }
                    return;
                }
                Map<String, Object> source = new HashMap<>();
                source.put("id", knowledgeId);
                source.put("knowledgeId", getLong(payload, "id"));
                source.put("title", title);
                source.put("content", content);
                source.put("ingredientNames", getStringList(payload, "ingredientNames"));
                source.put("chunk", chunk.text());
                source.put("sectionType", chunk.sectionType());
                source.put("chunkNo", i);
                source.put("entityType", "knowledge");
                source.put("version", version);
                String docId = knowledgeId + "_" + i;
                elasticsearchUtil.indexDocumentWithVector(KNOWLEDGE_SEMANTIC, docId, source, vector);
            } catch (Exception e) {
                log.error("索引知识向量块失败: knowledgeId={}, chunkNo={}", knowledgeId, i, e);
            }
        }
        log.info("知识 id={} 的所有向量块索引完成。", knowledgeId);
    }

    private void indexRecipeVector(Map<String, Object> payload, Long version) {
        String recipeId = getString(payload, "id");
        String fullContent = buildRecipeContent(payload);
        List<SemanticChunk> chunks = buildRecipeChunks(payload);
        if (chunks.isEmpty()) {
            log.warn("食谱内容为空或无法分块，跳过向量索引: id={}", recipeId);
            return;
        }

        log.info("食谱 id={} 被分成了 {} 个块，准备进行向量化...", recipeId, chunks.size());

        for (int i = 0; i < chunks.size(); i++) {
            SemanticChunk chunk = chunks.get(i);
            try {
                float[] vector = embeddingService.embed(chunk.text());
                // 零向量表示 Embedding 服务不可用，跳过索引
                if (isZeroVector(vector)) {
                    if (!recipeVectorSkipped) {
                        recipeVectorSkipped = true;
                        log.warn("Embedding 服务不可用，食谱向量索引已跳过（后续同类消息不再提示）");
                    }
                    return;
                }
                Map<String, Object> source = new HashMap<>();
                source.put("id", recipeId);
                source.put("recipeId", getLong(payload, "id"));
                source.put("title", getString(payload, "title"));
                source.put("content", fullContent);
                source.put("description", getString(payload, "description"));
                source.put("ingredients", getString(payload, "ingredients"));
                source.put("ingredientTexts", getStringList(payload, "ingredientTexts"));
                source.put("steps", getString(payload, "steps"));
                source.put("stepTexts", getStringList(payload, "stepTexts"));
                source.put("tagNames", getStringList(payload, "tagNames"));
                source.put("suitableFor", getString(payload, "suitableFor"));
                source.put("effects", getString(payload, "effects"));
                source.put("chunk", chunk.text());
                source.put("sectionType", chunk.sectionType());
                source.put("chunkNo", i);
                source.put("entityType", "recipe");
                source.put("version", version);
                String docId = recipeId + "_" + i;
                elasticsearchUtil.indexDocumentWithVector(RECIPE_SEMANTIC, docId, source, vector);
            } catch (Exception e) {
                log.error("索引食谱向量块失败: recipeId={}, chunkNo={}", recipeId, i, e);
            }
        }
        log.info("食谱 id={} 的所有向量块索引完成。", recipeId);
    }

    private void deleteDocuments(String docIndex, String vectorIndex, String idFieldName, Long entityId) {
        try {
            // 1. 删除文档索引中的主文档
            elasticsearchUtil.deleteDocument(docIndex, entityId.toString());
            log.info("删除文档索引成功: index={}, id={}", docIndex, entityId);
        } catch (Exception e) {
            log.warn("删除文档索引失败 (可能不存在): index={}, id={}", docIndex, entityId, e);
        }

        deleteVectorDocuments(vectorIndex, idFieldName, entityId);
    }

    private void deleteVectorDocuments(String vectorIndex, String idFieldName, Long entityId) {
        try {
            elasticsearchUtil.deleteByQuery(vectorIndex, idFieldName, entityId);
            log.info("删除向量索引成功: index={}, query={}={}", vectorIndex, idFieldName, entityId);
        } catch (Exception e) {
            log.warn("删除向量索引失败: index={}, id={}", vectorIndex, entityId, e);
        }
    }

    private void handleRetry(EsSyncEvent event) {
        if (event.getRetryCount() < maxRetry) {
            event.incrementRetry();
            try {
                Thread.sleep(1000L * event.getRetryCount());
                esSyncEventPublisher.publish(event);
                log.info("ES同步重试: type={}, id={}, retryCount={}",
                        event.getEntityType(), event.getEntityId(), event.getRetryCount());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("重试等待被中断", e);
            }
        } else {
            log.error("ES同步重试次数已达上限({}), 放弃处理: type={}, id={}",
                    maxRetry, event.getEntityType(), event.getEntityId());
        }
    }

    private void basicAck(Channel channel, Message message) {
        try {
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (Exception e) {
            log.error("ES同步消息确认失败", e);
        }
    }

    private void basicNack(Channel channel, Message message) {
        try {
            channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, false);
        } catch (Exception e) {
            log.error("ES同步消息拒绝失败", e);
        }
    }

    private String buildRecipeContent(Map<String, Object> payload) {
        return String.join("\n",
                safeText(getString(payload, "description")),
                String.join("\n", getStringList(payload, "ingredientTexts")),
                String.join("\n", getStringList(payload, "stepTexts")),
                safeText(getString(payload, "effects")),
                safeText(getString(payload, "suitableFor")),
                String.join(" ", getStringList(payload, "tagNames"))
        ).trim();
    }

    private String safeText(String text) {
        return text == null ? "" : text;
    }

    private boolean isPublished(Map<String, Object> payload) {
        return Objects.equals(getInteger(payload, "status"), 1);
    }

    private List<SemanticChunk> buildKnowledgeChunks(Map<String, Object> payload) {
        String title = getString(payload, "title");
        String content = safeText(getString(payload, "content"));
        List<String> ingredientNames = getStringList(payload, "ingredientNames");
        String prefix = buildPrefix("知识标题", title, ingredientNames.isEmpty() ? null : "相关食材：" + String.join("、", ingredientNames));
        List<String> sections = markdownChunker.chunk(content);
        if (sections.isEmpty() && !content.isBlank()) {
            sections = List.of(content);
        }

        List<SemanticChunk> result = new java.util.ArrayList<>();
        for (String section : sections) {
            result.addAll(splitSemanticText("content", prefix, section, 600));
        }
        if (result.isEmpty() && StrUtil.isNotBlank(title)) {
            result.add(new SemanticChunk("title", "知识标题：" + title));
        }
        return result;
    }

    private List<SemanticChunk> buildRecipeChunks(Map<String, Object> payload) {
        String title = getString(payload, "title");
        String prefix = buildPrefix("食谱标题", title, null);
        List<SemanticChunk> chunks = new java.util.ArrayList<>();

        appendSingleSectionChunk(chunks, "description", prefix, "简介", getString(payload, "description"), 500);
        appendSingleSectionChunk(chunks, "effects", prefix, "功效", getString(payload, "effects"), 400);
        appendSingleSectionChunk(chunks, "suitableFor", prefix, "适宜人群", getString(payload, "suitableFor"), 400);

        List<String> tagNames = getStringList(payload, "tagNames");
        if (!tagNames.isEmpty()) {
            appendSingleSectionChunk(chunks, "tags", prefix, "标签", String.join("、", tagNames), 300);
        }

        appendGroupedChunks(chunks, "ingredients", prefix, "食材", getStringList(payload, "ingredientTexts"), 4);
        appendGroupedChunks(chunks, "steps", prefix, "制作步骤", addStepNumbers(getStringList(payload, "stepTexts")), 2);
        return chunks;
    }

    private void appendSingleSectionChunk(List<SemanticChunk> chunks, String sectionType, String prefix,
                                          String sectionTitle, String text, int maxLength) {
        if (StrUtil.isBlank(text)) {
            return;
        }
        String section = sectionTitle + "：\n" + text;
        chunks.addAll(splitSemanticText(sectionType, prefix, section, maxLength));
    }

    private void appendGroupedChunks(List<SemanticChunk> chunks, String sectionType, String prefix,
                                     String sectionTitle, List<String> lines, int groupSize) {
        if (lines.isEmpty()) {
            return;
        }
        List<String> buffer = new java.util.ArrayList<>();
        for (String line : lines) {
            if (StrUtil.isBlank(line)) {
                continue;
            }
            buffer.add(line);
            if (buffer.size() == groupSize) {
                chunks.add(new SemanticChunk(sectionType, prefix + "\n" + sectionTitle + "：\n" + String.join("\n", buffer)));
                buffer.clear();
            }
        }
        if (!buffer.isEmpty()) {
            chunks.add(new SemanticChunk(sectionType, prefix + "\n" + sectionTitle + "：\n" + String.join("\n", buffer)));
        }
    }

    private List<String> addStepNumbers(List<String> stepTexts) {
        List<String> numbered = new java.util.ArrayList<>();
        for (int i = 0; i < stepTexts.size(); i++) {
            String text = stepTexts.get(i);
            if (StrUtil.isNotBlank(text)) {
                numbered.add("步骤" + (i + 1) + "：" + text);
            }
        }
        return numbered;
    }

    private List<SemanticChunk> splitSemanticText(String sectionType, String prefix, String section, int maxLength) {
        String normalized = safeText(section).trim();
        if (normalized.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        List<SemanticChunk> chunks = new java.util.ArrayList<>();
        List<String> paragraphs = java.util.Arrays.stream(normalized.split("\\n\\s*\\n"))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toList());
        if (paragraphs.isEmpty()) {
            paragraphs = List.of(normalized);
        }

        StringBuilder current = new StringBuilder(prefix);
        for (String paragraph : paragraphs) {
            if (current.length() > prefix.length() && current.length() + paragraph.length() + 2 > maxLength) {
                chunks.add(new SemanticChunk(sectionType, current.toString().trim()));
                current = new StringBuilder(prefix);
            }
            if (current.length() > prefix.length()) {
                current.append("\n\n");
            } else if (!prefix.isBlank()) {
                current.append("\n");
            }
            current.append(paragraph);
        }
        if (!current.isEmpty()) {
            chunks.add(new SemanticChunk(sectionType, current.toString().trim()));
        }
        return chunks;
    }

    private String buildPrefix(String label, String title, String extra) {
        StringBuilder prefix = new StringBuilder();
        if (StrUtil.isNotBlank(title)) {
            prefix.append(label).append("：").append(title);
        }
        if (StrUtil.isNotBlank(extra)) {
            if (!prefix.isEmpty()) {
                prefix.append("\n");
            }
            prefix.append(extra);
        }
        return prefix.toString();
    }

    private String getString(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private Long getLong(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return Long.parseLong(String.valueOf(value));
    }

    private Integer getInteger(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return Integer.parseInt(String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    private List<String> getStringList(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (!(value instanceof List<?> list)) {
            return java.util.Collections.emptyList();
        }
        return list.stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toList());
    }

    /**
     * 判断向量是否为零向量（全零 = Embedding 服务不可用）
     */
    private boolean isZeroVector(float[] vector) {
        if (vector == null) return true;
        for (float v : vector) {
            if (v != 0.0f) return false;
        }
        return true;
    }

    private record SemanticChunk(String sectionType, String text) {
    }
}
