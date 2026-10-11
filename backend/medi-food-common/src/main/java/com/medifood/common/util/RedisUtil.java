package com.medifood.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redis 工具类
 * 
 * @author MediFood Team
 */
@Slf4j
@Component
public class RedisUtil {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    /**
     * 设置缓存
     * 
     * @param key 键
     * @param value 值
     */
    public void set(String key, Object value) {
        try {
            redisTemplate.opsForValue().set(key, value);
        } catch (Exception e) {
            log.error("Redis 设置缓存失败，key: {}", key, e);
        }
    }
    
    /**
     * 设置缓存（带过期时间）
     * 
     * @param key 键
     * @param value 值
     * @param timeout 过期时间（秒）
     */
    public void set(String key, Object value, long timeout) {
        try {
            redisTemplate.opsForValue().set(key, value, timeout, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Redis 设置缓存失败，key: {}", key, e);
        }
    }
    
    /**
     * 获取缓存
     * 
     * @param key 键
     * @return 值
     */
    public Object get(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.error("Redis 获取缓存失败，key: {}", key, e);
            return null;
        }
    }
    
    /**
     * 删除缓存
     * 
     * @param key 键
     */
    public void delete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.error("Redis 删除缓存失败，key: {}", key, e);
        }
    }
    
    /**
     * 判断键是否存在
     * 
     * @param key 键
     * @return 是否存在
     */
    public boolean hasKey(String key) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.error("Redis 判断键是否存在失败，key: {}", key, e);
            return false;
        }
    }
    
    /**
     * 设置过期时间
     * 
     * @param key 键
     * @param timeout 过期时间（秒）
     */
    public void expire(String key, long timeout) {
        try {
            redisTemplate.expire(key, timeout, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Redis 设置过期时间失败，key: {}", key, e);
        }
    }
    
    /**
     * 递增
     * 
     * @param key 键
     * @param delta 增量
     * @return 递增后的值
     */
    public long increment(String key, long delta) {
        try {
            return redisTemplate.opsForValue().increment(key, delta);
        } catch (Exception e) {
            log.error("Redis 递增失败，key: {}", key, e);
            return 0;
        }
    }
    
    /**
     * 递减
     * 
     * @param key 键
     * @param delta 减量
     * @return 递减后的值
     */
    public long decrement(String key, long delta) {
        try {
            return redisTemplate.opsForValue().decrement(key, delta);
        } catch (Exception e) {
            log.error("Redis 递减失败，key: {}", key, e);
            return 0;
        }
    }
}

