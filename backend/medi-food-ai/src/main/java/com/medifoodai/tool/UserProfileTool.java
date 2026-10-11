package com.medifoodai.tool;

import com.medifood.common.result.Result;
import com.medifoodai.client.UserServiceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户画像工具
 * <p>
 * 获取当前用户的体质、性别、健康目标等个性化信息，
 * 帮助 AI 给出千人千面的饮食建议。
 * 注意：此工具不接收 LLM 参数，userId 由 ReActAgentService 直接注入。
 * </p>
 *
 * @author MediFood Team
 */
@Slf4j
@Component
public class UserProfileTool {

    @Autowired
    private UserServiceClient userServiceClient;

    /**
     * 获取指定用户的画像信息
     *
     * @param userId 用户 ID（由 ReActAgentService 注入，非 LLM 参数）
     * @return 包含昵称、性别、体质等信息的 Map
     */
    @Tool(name = "user_profile", description = "获取当前用户的身体状况、体质倾向、健康目标等信息。用于个性化推荐和回答。无需参数，AI直接调用即可。")
    public Map<String, Object> getUserProfile(Long userId) {
        Map<String, Object> result = new HashMap<>();

        if (userId == null) {
            result.put("status", "guest");
            result.put("message", "当前为匿名用户，无法获取个性化画像");
            return result;
        }

        try {
            Result<Map<String, Object>> userResult = userServiceClient.getUserInfoById(userId);
            if (userResult == null || userResult.getData() == null) {
                result.put("status", "not_found");
                result.put("message", "用户信息未找到");
                return result;
            }

            Map<String, Object> userInfo = userResult.getData();
            result.put("status", "ok");
            result.put("nickname", userInfo.getOrDefault("nickname", ""));
            result.put("gender", parseGender(userInfo.get("gender")));
            result.put("constitution", userInfo.getOrDefault("constitution", ""));
            result.put("healthGoal", userInfo.getOrDefault("healthGoal", ""));
            result.put("age", userInfo.getOrDefault("age", ""));
            result.put("height", userInfo.getOrDefault("height", ""));
            result.put("weight", userInfo.getOrDefault("weight", ""));

            log.debug("获取用户画像成功: userId={}, nickname={}", userId, result.get("nickname"));
        } catch (Exception e) {
            log.warn("获取用户画像失败: userId={}, error={}", userId, e.getMessage());
            result.put("status", "error");
            result.put("message", "获取用户画像失败：" + e.getMessage());
        }

        return result;
    }

    private String parseGender(Object genderObj) {
        if (genderObj == null) return "未知";
        String gender = genderObj.toString();
        return switch (gender) {
            case "1", "男" -> "男";
            case "2", "女" -> "女";
            default -> "未知";
        };
    }
}
