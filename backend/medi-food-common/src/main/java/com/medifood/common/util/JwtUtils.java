package com.medifood.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

@Data
@Component
public class JwtUtils {

   /* @Value("mySuperSecretKeyWhichIsVeryLongToMakeItSecure1234567890")
    private String secret;

    @Value("7200") // Access Token 默认 120分钟（单位：秒）
    private Long accessExpiration;

    @Value("604800") // Refresh Token 默认 7 天（单位：秒）
    private Long refreshExpiration;*/

    /**
     * JWT 签名密钥。
     * <p>安全说明：严禁把真实 JWT 密钥写进代码/仓库。生产部署必须通过
     * 环境变量 JWT_SECRET（jwt.secret 配置）提供独立的强随机密钥。</p>
     */
    @Value("${jwt.secret:}")
    private String secret;

    @Value("${jwt.access.expiration:7200}")
    private Long accessExpiration;

    @Value("${jwt.refresh.expiration:604800}")
    private Long refreshExpiration;

     private Key cachedSecretKey;

    // JWT Claims 中的字段名
    public static final String CLAIM_USER_ID = "userId";
    public static final String CLAIM_USERNAME = "username";

    /**
     * 生成 Access Token（用于访问接口，有效期短）
     *
     * @param userId   用户唯一标识，如用户ID
     * @param username 用户名（可为空）
     * @return JWT Token 字符串
     */
    public String generateAccessToken(String userId, String username) {
        return generateToken(userId, username, accessExpiration);
    }

    /**
     * 生成 Refresh Token（用于刷新 Access Token，有效期长）
     *
     * @param userId   用户唯一标识
     * @param username 用户名（可为空）
     * @return JWT Token 字符串
     */
    public String generateRefreshToken(String userId, String username) {
        return generateToken(userId, username, refreshExpiration);
    }

    /**
     * 通用 JWT 生成方法
     */
    private String generateToken(String userId, String username, Long expirationSeconds)  {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationSeconds * 1000L);
        return Jwts.builder()
                .setSubject(userId) // 通常是用户ID
                .claim(CLAIM_USERNAME, username) // 用户名，可选
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 解析 JWT Token，返回 Claims
     */
    public Claims parseToken(String token)  {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 校验 Token 是否合法（未过期、签名正确）
     */
    public boolean isTokenValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 判断 Token 是否过期（仅判断过期）
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.getExpiration().before(new Date());
        } catch (JwtException  e) {
            return true;
        }
    }

    /**
     * 从 Token 中获取用户ID（Subject）
     */
    public String getUserIdFromToken(String token)  {
        Claims claims = parseToken(token);
        return claims.getSubject();
    }



    /**
     * 从 Token 中获取用户名
     */
    public String getUsernameFromToken(String token)  {
        Claims claims = parseToken(token);
        return claims.get(CLAIM_USERNAME, String.class);
    }

    /**
     * 获取 JWT 签名密钥，校验密钥长度
     */
    // 推荐方式：生成符合 HS512 要求的安全密钥
/* private SecretKey getSigningKey() {
        // 直接生成适用于 HS512 的安全密钥
     System.out.println(SignatureAlgorithm.HS512);
     System.out.println(Keys.secretKeyFor(SignatureAlgorithm.HS512));
        return Keys.secretKeyFor(SignatureAlgorithm.HS512);
    }*/
 /*   private SecretKey getSigningKey() {
        // 使用足够长的字符串作为密钥源
        //String secret = "your-very-long-secret-key-string-at-least-64-characters-long-for-hs512-algorithm"; // 至少64个字符
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
*/
    private SecretKey getSigningKey() {
        if (cachedSecretKey == null) {
            // 使用配置的secret生成固定密钥
            cachedSecretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        }
        return (SecretKey) cachedSecretKey;
    }





}