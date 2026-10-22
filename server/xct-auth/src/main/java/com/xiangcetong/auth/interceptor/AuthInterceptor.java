package com.xiangcetong.auth.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiangcetong.auth.util.JwtUtil;
import com.xiangcetong.common.ApiResponse;
import com.xiangcetong.common.ErrorCodes;
import com.xiangcetong.common.UserContext;
import com.xiangcetong.common.AppProperties;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 鉴权拦截器：校验 Bearer Token + Redis 会话，写入 UserContext。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    public AuthInterceptor(JwtUtil jwtUtil,
                           StringRedisTemplate redisTemplate,
                           AppProperties appProperties,
                           ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // 预检请求直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            writeUnauthorized(response, "未登录或会话已失效");
            return false;
        }
        String token = header.substring(7).trim();
        try {
            // 1) 解析 JWT 拿 jti
            Claims claims = jwtUtil.parse(token);
            String jti = claims.getId();
            // 2) Redis 必须还有会话，否则视为已登出/过期
            String key = appProperties.getRedis().getTokenPrefix() + jti;
            String json = redisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                writeUnauthorized(response, "未登录或会话已失效");
                return false;
            }
            UserContext.LoginUser user = objectMapper.readValue(json, UserContext.LoginUser.class);
            UserContext.set(user);
            return true;
        } catch (Exception e) {
            writeUnauthorized(response, "未登录或会话已失效");
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 无论成功失败都清掉 ThreadLocal，防止串用户
        UserContext.clear();
    }

    /** 直接写 401 JSON，不经过 Controller */
    private void writeUnauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(401);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(),
                ApiResponse.fail(ErrorCodes.UNAUTHORIZED, message));
    }
}