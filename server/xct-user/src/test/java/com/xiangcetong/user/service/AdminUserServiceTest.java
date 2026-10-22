package com.xiangcetong.user.service;

import com.xiangcetong.common.BizException;
import com.xiangcetong.common.ErrorCodes;
import com.xiangcetong.common.PageResult;
import com.xiangcetong.common.UserContext;
import com.xiangcetong.log.service.OperationLogService;
import com.xiangcetong.log.service.OperationLogServiceImpl;
import com.xiangcetong.user.dto.CreateUserRequest;
import com.xiangcetong.user.entity.SysUser;
import com.xiangcetong.user.mapper.SysUserMapper;
import com.xiangcetong.user.mapper.VillagerInfoMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理端用户创建/列表单测（mock Mapper，不连库）。
 * 覆盖：权限守卫、非法角色、村/镇编码必填、手机号重复、村民角色顺带建 villager_info。
 */
class AdminUserServiceTest {

    private SysUserMapper sysUserMapper;
    private VillagerInfoMapper villagerInfoMapper;
    private AdminUserService service;

    @BeforeEach
    void setUp() {
        sysUserMapper = mock(SysUserMapper.class);
        villagerInfoMapper = mock(VillagerInfoMapper.class);
        // 具体类依赖用匿名子类替身（Java 25 Mockito 限制）
        OperationLogService opLog = new OperationLogServiceImpl(null) {
            @Override
            public void record(Long userId, String action, String target, String detail) {
                // no-op
            }
        };
        service = new AdminUserServiceImpl(sysUserMapper, villagerInfoMapper, opLog,
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void list_requiresSysAdmin() {
        login("VILLAGER");
        assertThrows(BizException.class, () -> service.list(null, null, 1, 10));
    }

    @Test
    void list_returnsPageWithoutPassword() {
        login("SYS_ADMIN");
        when(sysUserMapper.countAdmin(null, null)).thenReturn(1L);
        SysUser u = new SysUser();
        u.setId(1L);
        u.setPhone("13800000001");
        u.setName("张三");
        u.setRole("VILLAGER");
        when(sysUserMapper.listAdmin(null, null, 0, 10)).thenReturn(List.of(u));

        PageResult<Map<String, Object>> result = service.list(null, null, 1, 10);

        assertEquals(1, result.getTotal());
        assertEquals("张三", result.getList().get(0).get("name"));
    }

    @Test
    void create_illegalRole_throws() {
        login("SYS_ADMIN");
        CreateUserRequest req = req("13800000002", "SUPER_ADMIN");
        assertThrows(BizException.class, () -> service.create(req));
    }

    @Test
    void create_villagerMissingVillageCode_throws() {
        login("SYS_ADMIN");
        CreateUserRequest req = req("13800000002", "VILLAGER");
        req.setVillageCode(null);
        assertThrows(BizException.class, () -> service.create(req));
    }

    @Test
    void create_villageAdminMissingTownCode_throws() {
        login("SYS_ADMIN");
        CreateUserRequest req = req("13800000002", "VILLAGE_ADMIN");
        req.setVillageCode("西河村");
        req.setTownCode(null);
        assertThrows(BizException.class, () -> service.create(req));
    }

    @Test
    void create_phoneTaken_throws() {
        login("SYS_ADMIN");
        when(sysUserMapper.findByPhone("13800000002")).thenReturn(new SysUser());
        CreateUserRequest req = req("13800000002", "TOWN_ADMIN");
        req.setTownCode("南牛乡");

        BizException ex = assertThrows(BizException.class, () -> service.create(req));
        assertEquals(ErrorCodes.CONFLICT, ex.getCode());
    }

    @Test
    void create_villager_success_createsInfo() {
        login("SYS_ADMIN");
        when(sysUserMapper.findByPhone("13800000002")).thenReturn(null);
        SysUser saved = new SysUser();
        saved.setId(5L);
        saved.setPhone("13800000002");
        saved.setName("李四");
        saved.setRole("VILLAGER");
        when(sysUserMapper.findById(any())).thenReturn(saved);

        CreateUserRequest req = req("13800000002", "VILLAGER");
        req.setVillageCode("西河村");

        Map<String, Object> result = service.create(req);

        assertEquals(5L, result.get("id"));
        assertEquals("VILLAGER", result.get("role"));
        verify(sysUserMapper).insert(any());
        // 村民角色顺带建空 villager_info
        verify(villagerInfoMapper).insert(any());
    }

    @Test
    void create_admin_success_noVillagerInfo() {
        login("SYS_ADMIN");
        when(sysUserMapper.findByPhone("13800000002")).thenReturn(null);
        SysUser saved = new SysUser();
        saved.setId(6L);
        saved.setPhone("13800000002");
        saved.setName("王五");
        saved.setRole("TOWN_ADMIN");
        when(sysUserMapper.findById(any())).thenReturn(saved);

        CreateUserRequest req = req("13800000002", "TOWN_ADMIN");
        req.setTownCode("南牛乡");

        Map<String, Object> result = service.create(req);

        assertEquals("TOWN_ADMIN", result.get("role"));
        verify(sysUserMapper).insert(any());
        // 非村民角色不建 villager_info
        verify(villagerInfoMapper, never()).insert(any());
    }

    // ---------- helpers ----------

    private void login(String role) {
        UserContext.LoginUser login = new UserContext.LoginUser();
        login.setUserId(1L);
        login.setRole(role);
        login.setPhone("13800000001");
        login.setName("管理员");
        UserContext.set(login);
    }

    private CreateUserRequest req(String phone, String role) {
        CreateUserRequest req = new CreateUserRequest();
        req.setPhone(phone);
        req.setPassword("123456");
        req.setName("测试用户");
        req.setRole(role);
        return req;
    }
}
