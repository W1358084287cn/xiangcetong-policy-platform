package com.xiangcetong.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 登录上下文（ThreadLocal）：绑定 / 读取 / 角色校验 / 清理。
 */
class UserContextTest {

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private static UserContext.LoginUser loginUser(String role) {
        UserContext.LoginUser u = new UserContext.LoginUser();
        u.setUserId(1L);
        u.setRole(role);
        return u;
    }

    @Test
    void setThenGetReturnsSame() {
        UserContext.LoginUser u = loginUser("VILLAGER");
        UserContext.set(u);
        assertSame(u, UserContext.get());
    }

    @Test
    void getWithoutLoginReturnsNull() {
        assertNull(UserContext.get());
    }

    @Test
    void requireWithoutLoginThrows401() {
        BizException ex = assertThrows(BizException.class, UserContext::require);
        assertEquals(ErrorCodes.UNAUTHORIZED, ex.getCode());
    }

    @Test
    void requireWithLoginReturnsUser() {
        UserContext.LoginUser u = loginUser("VILLAGER");
        UserContext.set(u);
        assertSame(u, UserContext.require());
    }

    @Test
    void requireRoleMatchingReturnsUser() {
        UserContext.set(loginUser("SYS_ADMIN"));
        assertEquals("SYS_ADMIN", UserContext.requireRole("SYS_ADMIN", "TOWN_ADMIN").getRole());
    }

    @Test
    void requireRoleMismatchThrows403() {
        UserContext.set(loginUser("VILLAGER"));
        BizException ex = assertThrows(BizException.class,
                () -> UserContext.requireRole("SYS_ADMIN"));
        assertEquals(ErrorCodes.FORBIDDEN, ex.getCode());
    }

    @Test
    void clearRemovesBinding() {
        UserContext.set(loginUser("VILLAGER"));
        UserContext.clear();
        assertNull(UserContext.get());
    }
}
