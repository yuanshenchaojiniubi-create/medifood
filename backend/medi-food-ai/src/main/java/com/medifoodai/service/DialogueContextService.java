package com.medifoodai.service;

import com.medifoodai.entity.AiConversation;
import com.medifoodai.entity.AiMessage;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 对话上下文服务
 * <p>
 * 实现滑动窗口 + 摘要压缩策略：
 * - 保留最近 3 轮（6 条消息）完整对话原文
 * - 3 轮之前的消息压缩为摘要（由 LLM 生成，存入 conversation.summary）
 * - 每 5 轮触发一次摘要更新
 * - 估算总 token 数，防止超出 LLM 上下文窗口
 * </p>
 *
 * @author MediFood Team
 */
@Slf4j
@Service
public class DialogueContextService {

    @Autowired
    private ConversationService conversationService;

    /**
     * 保留的最近完整轮数（1 轮 = 1 条用户消息 + 1 条 AI 回复）
     */
    private static final int RECENT_ROUNDS = 3;

    /**
     * 每 N 轮触发一次摘要更新
     */
    private static final int SUMMARY_UPDATE_INTERVAL = 5;

    /**
     * 对话上下文结果
     */
    @Data
    public static class DialogueContext {
        /** 历史摘要（3 轮之前的内容压缩） */
        private String summary;
        /** 最近 3 轮完整消息 */
        private List<MessagePair> recentMessages;
        /** 估算的总 token 数 */
        private int estimatedTokens;
        /** 是否需要更新摘要 */
        private boolean needsSummaryUpdate;

        @Data
        public static class MessagePair {
            private String role;
            private String content;
        }
    }

    /**
     * 构建对话上下文
     *
     * @param conversationId 对话 ID（null 表示新对话）
     * @param currentQuery   当前用户提问
     * @return 对话上下文
     */
    public DialogueContext buildContext(Long conversationId, String currentQuery) {
        DialogueContext ctx = new DialogueContext();
        ctx.setRecentMessages(new ArrayList<>());
        ctx.setSummary("");
        ctx.setEstimatedTokens(estimateTokens(currentQuery));
        ctx.setNeedsSummaryUpdate(false);

        if (conversationId == null) {
            return ctx;
        }

        try {
            AiConversation conv = conversationService.getConversation(conversationId);
            if (conv == null) {
                return ctx;
            }

            List<AiMessage> allMessages = conversationService.getMessageEntities(conversationId);
            if (allMessages.isEmpty()) {
                return ctx;
            }

            int totalPairs = (int) Math.ceil(allMessages.size() / 2.0);

            // 保留最近 RECENT_ROUNDS 轮完整对话
            int recentStart = Math.max(0, allMessages.size() - RECENT_ROUNDS * 2);
            List<DialogueContext.MessagePair> recentPairs = new ArrayList<>();
            for (int i = recentStart; i < allMessages.size(); i++) {
                AiMessage msg = allMessages.get(i);
                DialogueContext.MessagePair pair = new DialogueContext.MessagePair();
                pair.setRole(msg.getRole());
                pair.setContent(msg.getContent());
                recentPairs.add(pair);
                ctx.setEstimatedTokens(ctx.getEstimatedTokens() + estimateTokens(msg.getContent()));
            }
            ctx.setRecentMessages(recentPairs);

            // 检查是否需要更新摘要：每 SUMMARY_UPDATE_INTERVAL 轮触发
            if (totalPairs >= SUMMARY_UPDATE_INTERVAL && totalPairs % SUMMARY_UPDATE_INTERVAL == 0) {
                ctx.setNeedsSummaryUpdate(true);
            }

            // 使用已有摘要或构建新摘要
            if (conv.getSummary() != null && !conv.getSummary().isEmpty()) {
                ctx.setSummary(conv.getSummary());
                ctx.setEstimatedTokens(ctx.getEstimatedTokens() + estimateTokens(conv.getSummary()));
            } else if (allMessages.size() > RECENT_ROUNDS * 2) {
                // 无缓存摘要但有历史消息，构建简单摘要
                String simpleSummary = buildSimpleSummary(allMessages, RECENT_ROUNDS);
                ctx.setSummary(simpleSummary);
                ctx.setEstimatedTokens(ctx.getEstimatedTokens() + estimateTokens(simpleSummary));
            }

            log.debug("对话上下文构建完成: conversationId={}, 总消息数={}, 摘要长度={}, 估算Token={}",
                    conversationId, allMessages.size(),
                    ctx.getSummary() != null ? ctx.getSummary().length() : 0,
                    ctx.getEstimatedTokens());

        } catch (Exception e) {
            log.error("构建对话上下文失败: conversationId={}", conversationId, e);
        }

        return ctx;
    }

    /**
     * 构建简单摘要（在前 N 轮消息中提取关键信息）
     */
    private String buildSimpleSummary(List<AiMessage> allMessages, int recentRounds) {
        int endIndex = Math.max(0, allMessages.size() - recentRounds * 2);
        StringBuilder sb = new StringBuilder();
        sb.append("用户此前讨论了以下话题：");
        for (int i = 0; i < endIndex && i < 6; i++) {
            AiMessage msg = allMessages.get(i);
            if ("user".equals(msg.getRole())) {
                String snippet = msg.getContent();
                if (snippet != null) {
                    if (snippet.length() > 50) {
                        snippet = snippet.substring(0, 50) + "...";
                    }
                    sb.append("\n- ").append(snippet);
                }
            }
        }
        return sb.toString();
    }

    /**
     * 估算文本的 token 数（粗略估算：中文约 1.5 字/token，英文约 4 字符/token）
     */
    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        // 粗略估算：混合中英文，平均 2 字符 ≈ 1 token
        return text.length() / 2;
    }
}