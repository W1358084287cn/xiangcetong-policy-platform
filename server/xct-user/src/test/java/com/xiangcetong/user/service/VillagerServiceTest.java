package com.xiangcetong.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiangcetong.common.BizException;
import com.xiangcetong.common.UserContext;
import com.xiangcetong.common.AppProperties;
import com.xiangcetong.user.dto.ProfileUpdateRequest;
import com.xiangcetong.user.entity.SysUser;
import com.xiangcetong.user.entity.VillagerInfo;
import com.xiangcetong.user.mapper.SysUserMapper;
import com.xiangcetong.user.mapper.VillagerInfoMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 村民资料更新的姓名/手机号校验（mock mapper 与 Redis，不连真实库）。
 * 覆盖：正常改名改号、手机号格式、手机号被他人占用、空姓名、未传两者时不触发。
 */
class VillagerServiceTest {

    private static final Long USER_ID = 1L;

    private SysUserMapper sysUserMapper;
    private VillagerInfoMapper villagerInfoMapper;
    private StringRedisTemplate redisTemplate;
    private VillagerService service;

    @BeforeEach
    void setUp() {
        sysUserMapper = mock(SysUserMapper.class);
        villagerInfoMapper = mock(VillagerInfoMapper.class);
        // Mockito 在 Java 25 下无法 mock 具体类 StringRedisTemplate，用子类替身：
        // getExpire 恒返回 null → 会话同步分支安全跳过，等价于"无会话快照"。
        redisTemplate = new StringRedisTemplate() {
            @Override
            public Long getExpire(String key, TimeUnit unit) {
                return null;
            }
        };
        service = new VillagerServiceImpl(sysUserMapper, villagerInfoMapper, redisTemplate,
                new AppProperties(), new ObjectMapper());

        // 登录上下文：updateProfile 依赖 UserContext 取角色与 userId
        UserContext.LoginUser login = new UserContext.LoginUser();
        login.setUserId(USER_ID);
        login.setRole("VILLAGER");
        login.setPhone("13800000001");
        login.setName("张三");
        login.setJti("jti-test");
        UserContext.set(login);

        // 已存在画像 → 走增量更新分支；buildProfile 需要 user + info
        lenient().when(villagerInfoMapper.findByUserId(USER_ID)).thenReturn(new VillagerInfo());
        SysUser user = new SysUser();
        user.setId(USER_ID);
        user.setName("张三");
        user.setPhone("13800000001");
        lenient().when(sysUserMapper.findById(USER_ID)).thenReturn(user);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void updatesNameAndPhoneAndWritesSession() {
        ProfileUpdateRequest req = new ProfileUpdateRequest();
        req.setName("张三三");
        req.setPhone("13912345678");

        Map<String, Object> result = service.updateProfile(req);

        verify(sysUserMapper).updateNamePhone(USER_ID, "张三三", "13912345678");
        assertEquals("张三", result.get("name")); // 返回的是 buildProfile 里 mock 的库值
    }

    @Test
    void rejectsBadPhoneFormat() {
        ProfileUpdateRequest req = new ProfileUpdateRequest();
        req.setPhone("12345");

        assertThrows(BizException.class, () -> service.updateProfile(req));
        verify(sysUserMapper, never()).updateNamePhone(anyLong(), anyString(), anyString());
    }

    @Test
    void rejectsPhoneTakenByAnotherUser() {
        SysUser other = new SysUser();
        other.setId(2L);
        other.setPhone("13912345678");
        when(sysUserMapper.findByPhone("13912345678")).thenReturn(other);

        ProfileUpdateRequest req = new ProfileUpdateRequest();
        req.setPhone("13912345678");

        assertThrows(BizException.class, () -> service.updateProfile(req));
        verify(sysUserMapper, never()).updateNamePhone(anyLong(), anyString(), anyString());
    }

    @Test
    void allowsPhoneUnchangedBySelf() {
        // 库里查到的就是本人 → 不算冲突
        SysUser self = new SysUser();
        self.setId(USER_ID);
        self.setPhone("13800000001");
        when(sysUserMapper.findByPhone("13800000001")).thenReturn(self);

        ProfileUpdateRequest req = new ProfileUpdateRequest();
        req.setPhone("13800000001");

        service.updateProfile(req);
        verify(sysUserMapper).updateNamePhone(USER_ID, null, "13800000001");
    }

    @Test
    void rejectsBlankName() {
        ProfileUpdateRequest req = new ProfileUpdateRequest();
        req.setName("   ");

        assertThrows(BizException.class, () -> service.updateProfile(req));
        verify(sysUserMapper, never()).updateNamePhone(anyLong(), anyString(), anyString());
    }

    @Test
    void skipsWhenNamePhoneAbsent() {
        ProfileUpdateRequest req = new ProfileUpdateRequest();
        req.setCountyName("正定县");

        service.updateProfile(req);
        verify(sysUserMapper, never()).updateNamePhone(anyLong(), anyString(), anyString());
    }

    @Test
    void updatesIdNumber() {
        ProfileUpdateRequest req = new ProfileUpdateRequest();
        req.setIdNumber("110101199001011234");

        Map<String, Object> result = service.updateProfile(req);

        verify(villagerInfoMapper).update(argThat(info ->
                "110101199001011234".equals(info.getIdNumber())));
        assertEquals("110101199001011234", result.get("idNumber"));
    }

    @Test
    void updatesIdFront() {
        ProfileUpdateRequest req = new ProfileUpdateRequest();
        req.setIdFront("/uploads/id_front.jpg");

        Map<String, Object> result = service.updateProfile(req);

        verify(villagerInfoMapper).update(argThat(info ->
                "/uploads/id_front.jpg".equals(info.getIdFront())));
        assertEquals("/uploads/id_front.jpg", result.get("idFront"));
    }

    @Test
    void updatesIdBack() {
        ProfileUpdateRequest req = new ProfileUpdateRequest();
        req.setIdBack("/uploads/id_back.jpg");

        Map<String, Object> result = service.updateProfile(req);

        verify(villagerInfoMapper).update(argThat(info ->
                "/uploads/id_back.jpg".equals(info.getIdBack())));
        assertEquals("/uploads/id_back.jpg", result.get("idBack"));
    }

    @Test
    void updatesAllIdFieldsTogether() {
        ProfileUpdateRequest req = new ProfileUpdateRequest();
        req.setIdNumber("220101198005056789");
        req.setIdFront("/uploads/f.jpg");
        req.setIdBack("/uploads/b.jpg");

        Map<String, Object> result = service.updateProfile(req);

        verify(villagerInfoMapper).update(argThat(info ->
                "220101198005056789".equals(info.getIdNumber())
                        && "/uploads/f.jpg".equals(info.getIdFront())
                        && "/uploads/b.jpg".equals(info.getIdBack())));
        assertEquals("220101198005056789", result.get("idNumber"));
        assertEquals("/uploads/f.jpg", result.get("idFront"));
        assertEquals("/uploads/b.jpg", result.get("idBack"));
    }

    @Test
    void idNumberNullWhenVillagerInfoMissing() {
        when(villagerInfoMapper.findByUserId(USER_ID)).thenReturn(null);

        Map<String, Object> result = service.updateProfile(new ProfileUpdateRequest() {{
            setIdNumber("110101199001011234");
        }});

        assertNull(result.get("idNumber"));
        assertNull(result.get("idFront"));
        assertNull(result.get("idBack"));
    }

    @Test
    void buildProfileReturnsIdFieldsFromDb() {
        VillagerInfo info = new VillagerInfo();
        info.setIdNumber("330101198812121212");
        info.setIdFront("/uploads/front.png");
        info.setIdBack("/uploads/back.png");
        when(villagerInfoMapper.findByUserId(USER_ID)).thenReturn(info);

        ProfileUpdateRequest req = new ProfileUpdateRequest();
        req.setCountyName("测试县");

        Map<String, Object> result = service.updateProfile(req);

        assertEquals("330101198812121212", result.get("idNumber"));
        assertEquals("/uploads/front.png", result.get("idFront"));
        assertEquals("/uploads/back.png", result.get("idBack"));
    }
}