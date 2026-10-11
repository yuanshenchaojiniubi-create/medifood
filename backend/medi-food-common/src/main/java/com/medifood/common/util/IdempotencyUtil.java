package com.medifood.common.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class IdempotencyUtil {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final String KEY_PREFIX = "msg:consumed:";
    private static final Duration TTL = Duration.ofHours(24);

    public boolean isConsumed(String messageId) {
        if (messageId == null || messageId.isEmpty()) {
            return false;
        }
        String key = KEY_PREFIX + messageId;
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", TTL);
        return Boolean.TRUE.equals(success) == false;
    }
}