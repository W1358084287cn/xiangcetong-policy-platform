package com.xiangcetong.auth.util;

import com.xiangcetong.common.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 工具：签发与解析（HS256）。
 * 真正是否有效还要看 Redis 里有没有对应 jti 会话。
 */
@Component
public class JwtUtil {

    private final AppProperties appProperties;
    private final SecretKey key;

    public JwtUtil(AppProperties appProperties) {
        this.appProperties = appProperties;
        // 密钥至少 256 bit，演示密钥已足够长
        this.key = Keys.hmacShaKeyFor(
                appProperties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 签发 Token，并把 jti 返回给调用方写入 Redis。
     *
     * @return [0]=token 字符串, [1]=jti
     */
    public String[] createToken(Long userId, String role) {
        String jti = UUID.randomUUID().toString().replace("-", "");
        long now = System.currentTimeMillis();
        long expireMs = appProperties.getJwt().getExpireSeconds() * 1000L;
        String token = Jwts.builder()
                .id(jti)
                .subject(String.valueOf(userId))
                .claim("role", role)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expireMs))
                .signWith(key)
                .compact();
        return new String[]{token, jti};
    }

    /** 解析并校验签名/过期；失败抛异常由上层转 401 */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}