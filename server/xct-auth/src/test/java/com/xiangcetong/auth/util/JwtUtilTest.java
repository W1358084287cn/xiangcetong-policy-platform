package com.xiangcetong.auth.util;

import com.xiangcetong.common.AppProperties;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JWT 签发/解析单测（HS256）。
 * 覆盖：签发返回 token+jti、解析往返、过期时间、篡改 token 解析失败。
 */
class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        // 演示密钥默认过短（72bit），必须换成 >=256bit 才能构造 HS256 key
        props.getJwt().setSecret("0123456789abcdef0123456789abcdef");
        props.getJwt().setExpireSeconds(3600);
        jwtUtil = new JwtUtil(props);
    }

    @Test
    void createToken_returnsTokenAndJti() {
        String[] result = jwtUtil.createToken(1L, "VILLAGER");
        assertEquals(2, result.length);
        assertFalse(result[0].isBlank());
        assertFalse(result[1].isBlank());
        // jti 为去除连字符的 UUID，长度 32
        assertEquals(32, result[1].length());
    }

    @Test
    void parse_roundTrip_claimsMatch() {
        String[] result = jwtUtil.createToken(42L, "VILLAGER");
        Claims claims = jwtUtil.parse(result[0]);

        assertEquals("42", claims.getSubject());
        assertEquals("VILLAGER", claims.get("role"));
        assertEquals(result[1], claims.getId());
    }

    @Test
    void createToken_expirationInFuture() {
        String[] result = jwtUtil.createToken(1L, "VILLAGER");
        Claims claims = jwtUtil.parse(result[0]);
        assertNotNull(claims.getExpiration());
        assertTrue(claims.getExpiration().after(new Date()));
    }

    @Test
    void parse_tamperedToken_throws() {
        String[] result = jwtUtil.createToken(1L, "VILLAGER");
        String tampered = result[0].substring(0, result[0].length() - 4) + "XXXX";
        assertThrows(Exception.class, () -> jwtUtil.parse(tampered));
    }

    @Test
    void parse_garbageToken_throws() {
        assertThrows(Exception.class, () -> jwtUtil.parse("not-a-jwt"));
    }
}