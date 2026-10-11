package com.medifoodai.controller;

import com.medifood.common.annotation.CurrentUser;
import com.medifood.common.result.Result;
import com.medifoodai.entity.AiFeedback;
import com.medifoodai.mapper.AiFeedbackMapper;
import com.medifoodai.mapper.AiMessageMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 反馈控制器
 * <p>
 * 提供用户对 AI 回答的 👍/👎 评价功能
 * </p>
 *
 * @author MediFood Team
 */
@Slf4j
@RestController
@RequestMapping("/ai")
public class FeedbackController {

    @Autowired
    private AiFeedbackMapper feedbackMapper;

    @Autowired
    private AiMessageMapper messageMapper;

    /**
     * 提交反馈
     *
     * @param messageId 被评价的消息 ID（ai_message 表的 id）
     * @param body      反馈内容：{"type": "positive"/"negative", "reason": "可选原因"}
     */
    @PostMapping("/feedback/{messageId}")
    public Result<Map<String, Object>> submitFeedback(@PathVariable Long messageId,
                                          @RequestBody Map<String, Object> body,
                                          @CurrentUser Long userId) {
        try {
            String type = (String) body.getOrDefault("type", "positive");
            String reason = (String) body.getOrDefault("reason", "");

            // 检查是否已反馈（同一用户对同一消息只能反馈一次）
            var existing = feedbackMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiFeedback>()
                            .eq(AiFeedback::getMessageId, messageId)
                            .eq(AiFeedback::getUserId, userId)
            );
            if (existing != null && !existing.isEmpty()) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("success", true);
                result.put("message", "已反馈，无需重复提交");
                return Result.success(result);
            }

            AiFeedback feedback = new AiFeedback();
            feedback.setMessageId(messageId);
            feedback.setUserId(userId);
            feedback.setFeedbackType(type);
            feedback.setReason(reason);
            feedback.setCreatedAt(LocalDateTime.now());

            feedbackMapper.insert(feedback);
            log.info("用户反馈已记录: messageId={}, type={}, userId={}", messageId, type, userId);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("messageId", messageId);
            return Result.success(result);
        } catch (Exception e) {
            log.error("提交反馈失败: messageId={}, userId={}", messageId, userId, e);
            return Result.fail("提交反馈失败: " + e.getMessage());
        }
    }

    /**
     * 获取反馈统计（管理员）
     * <p>
     * 返回正面/负面反馈数量和满意度
     * </p>
     */
    @GetMapping("/feedback/stats")
    public Result<Map<String, Object>> getFeedbackStats() {
        Map<String, Object> stats = new LinkedHashMap<>();

        try {
            // 总反馈数
            Long total = feedbackMapper.selectCount(null);
            stats.put("total", total);

            // 正面反馈数
            var positiveWrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiFeedback>()
                    .eq(AiFeedback::getFeedbackType, "positive");
            Long positive = feedbackMapper.selectCount(positiveWrapper);
            stats.put("positive", positive);

            // 负面反馈数
            var negativeWrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiFeedback>()
                    .eq(AiFeedback::getFeedbackType, "negative");
            Long negative = feedbackMapper.selectCount(negativeWrapper);
            stats.put("negative", negative);

            // 满意度
            double satisfaction = total > 0 ? (double) positive / total * 100 : 0;
            stats.put("satisfaction", String.format("%.1f%%", satisfaction));

            // 总消息数
            Long totalMessages = messageMapper.selectCount(null);
            stats.put("totalMessages", totalMessages);

            // 反馈率
            double feedbackRate = totalMessages > 0 ? (double) total / totalMessages * 100 : 0;
            stats.put("feedbackRate", String.format("%.1f%%", feedbackRate));

        } catch (Exception e) {
            log.error("获取反馈统计失败: {}", e.getMessage());
            stats.put("error", "获取数据失败");
        }

        return Result.success(stats);
    }
}