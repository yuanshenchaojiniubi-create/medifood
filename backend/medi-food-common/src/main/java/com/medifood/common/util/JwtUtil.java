package com.medifood.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * JWT 工具类
 * 
 * @author MediFood Team
 */
@Slf4j
public class JwtUtil {
    
    /**
     * JWT 密钥（实际项目中应从配置文件读取）
     */
    private static final String SECRET = "MediFoodCircleSecretKeyForJWTTokenGeneration2024";
    
    /**
     * JWT 过期时间（7天）
     */
    private static final long EXPIRE_TIME = 7 * 24 * 60 * 60 * 1000;
    
    /**
     * 生成密钥
     */
    private static SecretKey getSecretKey() {
        return Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }
    
    /**
     * 生成 JWT Token
     * 
     * @param userId 用户ID
     * @param claims 自定义声明
     * @return Token
     */
    public static String generateToken(Long userId, Map<String, Object> claims) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + EXPIRE_TIME);
        
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(String.valueOf(userId))
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSecretKey(), SignatureAlgorithm.HS256)
                .compact();
    }
    
    /**
     * 生成 JWT Token（仅用户ID）
     * 
     * @param userId 用户ID
     * @return Token
     */
    public static String generateToken(Long userId) {
        return generateToken(userId, null);
    }
    
    /**
     * 从 Token 中获取 Claims
     * 
     * @param token JWT Token
     * @return Claims
     */
    public static Claims getClaimsFromToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(getSecretKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception e) {
            log.error("解析 JWT Token 失败: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 从 Token 中获取用户ID
     * 
     * @param token JWT Token
     * @return 用户ID
     */
    public static Long getUserIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        if (claims != null) {
            return Long.parseLong(claims.getSubject());
        }
        return null;
    }
    
    /**
     * 验证 Token 是否有效
     * 
     * @param token JWT Token
     * @return 是否有效
     */
    public static boolean validateToken(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            return claims != null && !isTokenExpired(claims);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 判断 Token 是否过期
     * 
     * @param claims Claims
     * @return 是否过期
     */
    private static boolean isTokenExpired(Claims claims) {
        Date expiration = claims.getExpiration();
        return expiration.before(new Date());
    }
}

