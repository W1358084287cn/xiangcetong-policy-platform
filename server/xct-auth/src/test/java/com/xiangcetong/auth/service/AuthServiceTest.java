package com.xiangcetong.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiangcetong.auth.util.JwtUtil;
import com.xiangcetong.auth.dto.ChangePasswordRequest;
import com.xiangcetong.auth.dto.LoginRequest;
import com.xiangcetong.common.BizException;
import com.xiangcetong.common.ErrorCodes;
import com.xiangcetong.common.UserContext;
import com.xiangcetong.common.AppProperties;
import com.xiangcetong.log.service.OperationLogService;
import com.xiangcetong.log.service.OperationLogServiceImpl;
import com.xiangcetong.user.entity.SysUser;
import com.xiangcetong.user.mapper.SysUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登录（手机号 / 管理端账号+角色）与修改密码逻辑。
 * Java 25 下 Mockito 无法 mock 具体类，故 JwtUtil / StringRedisTemplate / OperationLogService
 * 一律用匿名子类替身；SysUserMapper 为接口可正常 mock。
 */
class AuthServiceTest {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private SysUserMapper sysUserMapper;
    private StringRedisTemplate redisTemplate;
    private AuthService service;

    @BeforeEach
    void setUp() {
        sysUserMapper = mock(SysUserMapper.class);

        // JwtUtil 为具体类：匿名子类替身，仅覆盖 createToken
        AppProperties props = new AppProperties();
        props.getJwt().setSecret("xiangcetong-demo-jwt-secret-key-2026-change-me");
        JwtUtil jwtUtil = new JwtUtil(props) {
            @Override
            public String[] createToken(Long userId, String role) {
                return new String[]{"test-token", "test-jti"};
            }
        };

        // StringRedisTemplate 为具体类：匿名子类替身，opsForValue 返回 mock 的 ValueOperations
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        redisTemplate = new StringRedisTemplate() {
            @Override
            public ValueOperations<String, String> opsForValue() {
                return valueOps;
            }
        };

        // OperationLogService 为具体类：匿名子类替身，登录成功时跳过写日志
        OperationLogService operationLogService = new OperationLogServiceImpl(null) {
            @Override
            public void record(Long userId, String action, String target, String detail) {
                // no-op
            }
        };

        service = new AuthServiceImpl(sysUserMapper, jwtUtil, redisTemplate,
                props, new ObjectMapper(), operationLogService, ENCODER);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void loginByUsernameAndRoleSucceeds() {
        SysUser user = adminUser(10L, "admin", "VILLAGE_ADMIN", "123456");
        when(sysUserMapper.findByUsernameAndRole("admin", "VILLAGE_ADMIN")).thenReturn(user);

        LoginRequest req = new LoginRequest();
        req.setUsername("admin");
        req.setRole("VILLAGE_ADMIN");
        req.setPassword("123456");

        Map<String, Object> result = service.login(req);

        assertEquals("test-token", result.get("token"));
        Map<?, ?> userVo = (Map<?, ?>) result.get("user");
        assertEquals(10L, userVo.get("id"));
        assertEquals("VILLAGE_ADMIN", userVo.get("role"));
        verify(sysUserMapper).findByUsernameAndRole("admin", "VILLAGE_ADMIN");
    }

    @Test
    void loginByUsernameRoleMismatchFails() {
        // 账号 admin 实为村管，但前端带镇管角色 → 查无此人
        when(sysUserMapper.findByUsernameAndRole("admin", "TOWN_ADMIN")).thenReturn(null);

        LoginRequest req = new LoginRequest();
        req.setUsername("admin");
        req.setRole("TOWN_ADMIN");
        req.setPassword("123456");

        BizException ex = assertThrows(BizException.class, () -> service.login(req));
        assertEquals(ErrorCodes.UNAUTHORIZED, ex.getCode());
    }

    @Test
    void loginByUsernameWrongPasswordFails() {
        SysUser user = adminUser(11L, "admin", "SYS_ADMIN", "123456");
        when(sysUserMapper.findByUsernameAndRole("admin", "SYS_ADMIN")).thenReturn(user);

        LoginRequest req = new LoginRequest();
        req.setUsername("admin");
        req.setRole("SYS_ADMIN");
        req.setPassword("wrong-password");

        BizException ex = assertThrows(BizException.class, () -> service.login(req));
        assertEquals(ErrorCodes.UNAUTHORIZED, ex.getCode());
    }

    @Test
    void loginByUsernameAsTownAdminSucceeds() {
        SysUser user = adminUser(12L, "admin", "TOWN_ADMIN", "123456");
        when(sysUserMapper.findByUsernameAndRole("admin", "TOWN_ADMIN")).thenReturn(user);

        LoginRequest req = new LoginRequest();
        req.setUsername("admin");
        req.setRole("TOWN_ADMIN");
        req.setPassword("123456");

        Map<String, Object> result = service.login(req);

        assertEquals("test-token", result.get("token"));
        Map<?, ?> userVo = (Map<?, ?>) result.get("user");
        assertEquals("TOWN_ADMIN", userVo.get("role"));
    }

    @Test
    void loginByUsernameAsSysAdminSucceeds() {
        SysUser user = adminUser(13L, "admin", "SYS_ADMIN", "123456");
        when(sysUserMapper.findByUsernameAndRole("admin", "SYS_ADMIN")).thenReturn(user);

        LoginRequest req = new LoginRequest();
        req.setUsername("admin");
        req.setRole("SYS_ADMIN");
        req.setPassword("123456");

        Map<String, Object> result = service.login(req);

        assertEquals("test-token", result.get("token"));
        Map<?, ?> userVo = (Map<?, ?>) result.get("user");
        assertEquals("SYS_ADMIN", userVo.get("role"));
    }

    @Test
    void loginByUsernameNullPasswordHashFails() {
        SysUser user = adminUser(14L, "admin", "VILLAGE_ADMIN", "123456");
        user.setPasswordHash(null);
        when(sysUserMapper.findByUsernameAndRole("admin", "VILLAGE_ADMIN")).thenReturn(user);

        LoginRequest req = new LoginRequest();
        req.setUsername("admin");
        req.setRole("VILLAGE_ADMIN");
        req.setPassword("123456");

        BizException ex = assertThrows(BizException.class, () -> service.login(req));
        assertEquals(ErrorCodes.UNAUTHORIZED, ex.getCode());
    }

    @Test
    void loginByUsernameUserNotFoundReturnsUnauthorized() {
        when(sysUserMapper.findByUsernameAndRole("admin", "VILLAGE_ADMIN")).thenReturn(null);

        LoginRequest req = new LoginRequest();
        req.setUsername("admin");
        req.setRole("VILLAGE_ADMIN");
        req.setPassword("123456");

        BizException ex = assertThrows(BizException.class, () -> service.login(req));
        assertEquals(ErrorCodes.UNAUTHORIZED, ex.getCode());
    }

    @Test
    void changePasswordSucceeds() {
        setLogin(7L, "SYS_ADMIN");
        SysUser user = adminUser(7L, "admin", "SYS_ADMIN", "oldPass123");
        when(sysUserMapper.findById(7L)).thenReturn(user);

        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setOldPassword("oldPass123");
        req.setNewPassword("newPass456");

        service.changePassword(req);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(sysUserMapper).updatePassword(eq(7L), captor.capture());
        assertTrue(ENCODER.matches("newPass456", captor.getValue()));
    }

    @Test
    void changePasswordWrongOldPasswordFails() {
        setLogin(8L, "SYS_ADMIN");
        SysUser user = adminUser(8L, "admin", "SYS_ADMIN", "realOldPass");
        when(sysUserMapper.findById(8L)).thenReturn(user);

        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setOldPassword("wrongOldPass");
        req.setNewPassword("newPass456");

        BizException ex = assertThrows(BizException.class, () -> service.changePassword(req));
        assertEquals(ErrorCodes.BAD_PARAM, ex.getCode());
        verify(sysUserMapper, never()).updatePassword(anyLong(), anyString());
    }

    @Test
    void changePasswordNewPasswordTooShortFails() {
        setLogin(9L, "SYS_ADMIN");
        SysUser user = adminUser(9L, "admin", "SYS_ADMIN", "oldPass123");
        when(sysUserMapper.findById(9L)).thenReturn(user);

        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setOldPassword("oldPass123");
        req.setNewPassword("123");

        BizException ex = assertThrows(BizException.class, () -> service.changePassword(req));
        assertEquals(ErrorCodes.BAD_PARAM, ex.getCode());
        verify(sysUserMapper, never()).updatePassword(anyLong(), anyString());
    }

    /** 造一个密码哈希与 plainPassword 匹配的管理用户 */
    private static SysUser adminUser(Long id, String username, String role, String plainPassword) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setRole(role);
        user.setName("演示管理员");
        user.setPhone("13800000000");
        user.setPasswordHash(ENCODER.encode(plainPassword));
        return user;
    }

    /** 写入当前登录上下文 */
    private static void setLogin(Long userId, String role) {
        UserContext.LoginUser login = new UserContext.LoginUser();
        login.setUserId(userId);
        login.setRole(role);
        login.setPhone("13800000000");
        login.setName("演示管理员");
        login.setJti("test-jti");
        UserContext.set(login);
    }
}