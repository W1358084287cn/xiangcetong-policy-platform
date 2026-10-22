package com.xiangcetong.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiangcetong.auth.util.JwtUtil;
import com.xiangcetong.auth.dto.ChangePasswordRequest;
import com.xiangcetong.auth.dto.LoginRequest;
import com.xiangcetong.common.BizException;
import com.xiangcetong.common.ErrorCodes;
import com.xiangcetong.common.UserContext;
import com.xiangcetong.common.Role;
import com.xiangcetong.common.AppProperties;
import com.xiangcetong.log.service.OperationLogService;
import com.xiangcetong.user.entity.SysUser;
import com.xiangcetong.user.mapper.SysUserMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 认证实现：登录（手机号 / 管理端账号+角色）、修改密码。
 * 登录流程：校验密码 → 签发 JWT → 写 Redis 会话 → 记操作日志。
 */
@Service
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper sysUserMapper;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final OperationLogService operationLogService;
    private final PasswordEncoder passwordEncoder;

    public AuthServiceImpl(SysUserMapper sysUserMapper,
                           JwtUtil jwtUtil,
                           StringRedisTemplate redisTemplate,
                           AppProperties appProperties,
                           ObjectMapper objectMapper,
                           OperationLogService operationLogService,
                           PasswordEncoder passwordEncoder) {
        this.sysUserMapper = sysUserMapper;
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
        this.operationLogService = operationLogService;
        this.passwordEncoder = ***;
    }

    /** 登录：手机号密码（旧）或 账号+角色+密码（管理端新），返回 token + 用户摘要 */
    @Override
    public Map<String, Object> login(LoginRequest request) {
        boolean byUsername = request.getUsername() != null && !request.getUsername().isBlank();
        SysUser user = byUsername ? findByUsernameAndRole(request) : findByPhone(request);

        // 账号/用户名、角色、密码任一不符，统一提示，不暴露具体是哪个错
        String failMessage = byUsername ? "账号或密码错误" : "手机号或密码错误";
        if (user == null || user.getPasswordHash() == null
                || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BizException(ErrorCodes.UNAUTHORIZED, failMessage, 401);
        }

        String[] tokenAndJti = jwtUtil.createToken(user.getId(), user.getRole());
        String token = tokenAndJti[0];
        String jti = tokenAndJti[1];

        // 组装会话对象写入 Redis
        UserContext.LoginUser session = new UserContext.LoginUser();
        session.setUserId(user.getId());
        session.setPhone(user.getPhone());
        session.setName(user.getName());
        session.setRole(user.getRole());
        session.setCountyCode(nullToEmpty(user.getCountyCode()));
        session.setTownCode(nullToEmpty(user.getTownCode()));
        session.setVillageCode(nullToEmpty(user.getVillageCode()));
        session.setJti(jti);

        try {
            String key = appProperties.getRedis().getTokenPrefix() + jti;
            String json = objectMapper.writeValueAsString(session);
            redisTemplate.opsForValue().set(key, json,
                    appProperties.getJwt().getExpireSeconds(), TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new BizException(ErrorCodes.SERVER_ERROR, "会话写入失败", 500);
        }

        operationLogService.record(user.getId(), "LOGIN",
                "sys_user:" + user.getId(), "用户登录");

        Map<String, Object> userVo = new HashMap<>();
        userVo.put("id", user.getId());
        userVo.put("name", user.getName());
        userVo.put("role", user.getRole());
        userVo.put("villageCode", nullToEmpty(user.getVillageCode()));
        userVo.put("townCode", nullToEmpty(user.getTownCode()));

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("user", userVo);
        return data;
    }

    /** 修改当前登录用户自己的密码（须登录，任意角色） */
    @Override
    public void changePassword(ChangePasswordRequest request) {
        UserContext.LoginUser login = UserContext.require();
        Long userId = login.getUserId();

        SysUser user = sysUserMapper.findById(userId);
        // 旧密码不对（或用户不存在），统一业务错误
        if (user == null || user.getPasswordHash() == null
                || !passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new BizException(ErrorCodes.BAD_PARAM, "旧密码错误");
        }

        String newPassword = request.getNewPassword();
        if (newPassword == null || newPassword.length() < 6 || newPassword.length() > 32) {
            throw new BizException(ErrorCodes.BAD_PARAM, "新密码长度须为 6~32 位");
        }

        sysUserMapper.updatePassword(userId, passwordEncoder.encode(newPassword));
    }

    /** 旧版：手机号登录，手机号非空与格式在此校验（与原 @Valid 行为一致） */
    private SysUser findByPhone(LoginRequest request) {
        String phone = request.getPhone();
        if (phone == null || phone.isBlank()) {
            throw new BizException(ErrorCodes.BAD_PARAM, "手机号不能为空");
        }
        if (!phone.matches("^1\\d{10}$")) {
            throw new BizException(ErrorCodes.BAD_PARAM, "手机号格式不正确");
        }
        return sysUserMapper.findByPhone(phone);
    }

    /** 管理端：账号+角色登录，角色非法返回 null 交由上层统一报错 */
    private SysUser findByUsernameAndRole(LoginRequest request) {
        String role = request.getRole();
        if (role == null || !isAdminRole(role)) {
            return null;
        }
        return sysUserMapper.findByUsernameAndRole(request.getUsername(), role);
    }

    private static boolean isAdminRole(String role) {
        return Role.ADMIN.contains(role);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}