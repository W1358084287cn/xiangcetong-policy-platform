package com.xiangcetong.auth.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiangcetong.auth.util.JwtUtil;
import com.xiangcetong.common.UserContext;
import com.xiangcetong.common.AppProperties;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 鉴权拦截器：OPTIONS 放行、Bearer 校验、Redis 会话命中、失败写 401。
 * Java 25 下 Claims 接口无法被 Mockito inline mock，改用真实 JwtUtil 签发。
 */
class AuthInterceptorTest {

    private AppProperties props;
    private JwtUtil jwtUtil;
    private ValueOperations<String, String> valueOps;
    private HttpServletResponse response;
    private StringWriter responseBody;

    @BeforeEach
    void setUp() throws Exception {
        props = new AppProperties();
        props.getJwt().setSecret("0123456789abcdef0123456789abcdef");
        props.getJwt().setExpireSeconds(3600);
        jwtUtil = new JwtUtil(props);

        valueOps = mock(ValueOperations.class);
        response = mock(HttpServletResponse.class);
        responseBody = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseBody));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    /** 用真实 JwtUtil 解析得到 Claims，避免 mock Claims 接口（Java 25 不兼容） */
    private Claims realClaims() {
        String[] tokenAndJti = jwtUtil.createToken(1L, "VILLAGER");
        return jwtUtil.parse(tokenAndJti[0]);
    }

    private AuthInterceptor interceptor(Claims parsed) {
        JwtUtil util = new JwtUtil(props) {
            @Override
            public Claims parse(String token) {
                return parsed;
            }
        };
        StringRedisTemplate redis = new StringRedisTemplate() {
            @Override
            public ValueOperations<String, String> opsForValue() {
                return valueOps;
            }
        };
        return new AuthInterceptor(util, redis, props, new ObjectMapper());
    }

    private HttpServletRequest request(String method, String authHeader) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getMethod()).thenReturn(method);
        when(req.getHeader("Authorization")).thenReturn(authHeader);
        return req;
    }

    @Test
    void optionsRequestPassesWithoutAuth() throws Exception {
        boolean ok = interceptor(realClaims()).preHandle(request("OPTIONS", null), response, null);
        assertTrue(ok);
    }

    @Test
    void missingHeaderRejectsWith401() throws Exception {
        boolean ok = interceptor(realClaims()).preHandle(request("GET", null), response, null);
        assertFalse(ok);
        verify(response).setStatus(401);
    }

    @Test
    void nonBearerHeaderRejects() throws Exception {
        boolean ok = interceptor(realClaims()).preHandle(request("GET", "Basic abc"), response, null);
        assertFalse(ok);
        verify(response).setStatus(401);
    }

    @Test
    void validTokenWithSessionPasses() throws Exception {
        Claims claims = realClaims();
        when(valueOps.get("xiangcetong:token:" + claims.getId()))
                .thenReturn("{\"userId\":1,\"role\":\"VILLAGER\",\"name\":\"张三\"}");

        boolean ok = interceptor(claims).preHandle(request("GET", "Bearer valid-token"), response, null);

        assertTrue(ok);
        assertNotNull(UserContext.get());
        assertTrue("VILLAGER".equals(UserContext.get().getRole()));
    }

    @Test
    void missingRedisSessionRejects() throws Exception {
        Claims claims = realClaims();
        when(valueOps.get("xiangcetong:token:" + claims.getId())).thenReturn(null);
        boolean ok = interceptor(claims).preHandle(request("GET", "Bearer valid-token"), response, null);
        assertFalse(ok);
        verify(response).setStatus(401);
    }

    @Test
    void parseFailureRejects() throws Exception {
        JwtUtil util = new JwtUtil(props) {
            @Override
            public Claims parse(String token) {
                throw new RuntimeException("bad token");
            }
        };
        StringRedisTemplate redis = new StringRedisTemplate() {
            @Override
            public ValueOperations<String, String> opsForValue() {
                return valueOps;
            }
        };
        AuthInterceptor it = new AuthInterceptor(util, redis, props, new ObjectMapper());

        boolean ok = it.preHandle(request("GET", "Bearer broken"), response, null);
        assertFalse(ok);
        verify(response).setStatus(401);
    }
}