package com.medifoodai.service;

import com.medifood.common.result.Result;
import com.medifoodai.client.UserServiceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 用户画像服务
 * <p>
 * 通过 Feign 调用 User 服务获取用户信息，构建画像摘要注入 AI System Prompt。
 * Redis 缓存 30 分钟，减少跨服务调用。
 * </p>
 *
 * @author MediFood Team
 */
@Slf4j
@Service
public class UserProfileService {

    @Autowired
    private UserServiceClient userServiceClient;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String CACHE_PREFIX = "ai:profile:";
    private static final long CACHE_TTL = 30;

    /**
     * 构建用户画像摘要
     * <p>
     * 示例："用户昵称：张三，性别：男，体质倾向：湿热质"
     * </p>
     *
     * @param userId 用户 ID
     * @return 画像摘要字符串，失败时返回空字符串
     */
    public String buildProfileSummary(Long userId) {
        if (userId == null) return "";

        // 1. 尝试从 Redis 缓存读取
        String cacheKey = CACHE_PREFIX + userId;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached.toString();
        }

        // 2. 通过 Feign 调用 User 服务
        try {
            Result<Map<String, Object>> result = userServiceClient.getUserInfoById(userId);
            if (result == null || result.getData() == null) {
                return "";
            }

            Map<String, Object> userInfo = result.getData();
            StringBuilder sb = new StringBuilder();

            // 昵称
            String nickname = getString(userInfo, "nickname");
            if (!nickname.isEmpty()) {
                sb.append("用户昵称：").append(nickname);
            }

            // 性别
            Object genderObj = userInfo.get("gender");
            if (genderObj != null) {
                String gender = genderObj.toString();
                if ("1".equals(gender) || "男".equals(gender)) {
                    sb.append("，性别：男");
                } else if ("2".equals(gender) || "女".equals(gender)) {
                    sb.append("，性别：女");
                }
            }

            // 体质倾向（从用户标签或体质字段获取）
            String constitution = getConstitution(userInfo);
            if (!constitution.isEmpty()) {
                sb.append("，体质倾向：").append(constitution);
            }

            String summary = sb.toString();
            if (!summary.isEmpty()) {
                // 3. 存入 Redis 缓存
                redisTemplate.opsForValue().set(cacheKey, summary, CACHE_TTL, TimeUnit.MINUTES);
            }

            return summary;
        } catch (Exception e) {
            log.warn("获取用户画像失败: userId={}, error={}", userId, e.getMessage());
            return "";
        }
    }

    /**
     * 从用户信息中提取体质倾向
     */
    private String getConstitution(Map<String, Object> userInfo) {
        // 优先从体质字段获取
        String constitution = getString(userInfo, "constitution");
        if (!constitution.isEmpty()) return constitution;

        // 尝试从其他字段推断
        String healthGoal = getString(userInfo, "healthGoal");
        if (!healthGoal.isEmpty()) return healthGoal;

        return "";
    }

    private String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString().trim() : "";
    }
}