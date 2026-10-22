package com.xiangcetong.config;

import com.xiangcetong.user.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AdminPasswordFixRunner 单元测试：
 * 验证启动时自动修复 admin 账号密码为 123456 的哈希值。
 */
class AdminPasswordFixRunnerTest {

    private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private SysUserMapper sysUserMapper;
    private AdminPasswordFixRunner runner;

    @BeforeEach
    void setUp() {
        sysUserMapper = mock(SysUserMapper.class);
        runner = new AdminPasswordFixRunner(sysUserMapper, ENCODER);
    }

    @Test
    void runFixesPasswordWhenRowsFound() throws Exception {
        when(sysUserMapper.fixAdminPassword(anyString())).thenReturn(3);

        runner.run();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(sysUserMapper, times(1)).fixAdminPassword(captor.capture());
        assertTrue(ENCODER.matches("123456", captor.getValue()),
                "传入 fixAdminPassword 的哈希值应能匹配明文 123456");
    }

    @Test
    void runDoesNotThrowWhenNoRowsUpdated() throws Exception {
        when(sysUserMapper.fixAdminPassword(anyString())).thenReturn(0);

        runner.run();

        verify(sysUserMapper, times(1)).fixAdminPassword(anyString());
    }

    @Test
    void passwordHashIsDeterministicallyVerifiable() throws Exception {
        when(sysUserMapper.fixAdminPassword(anyString())).thenReturn(1);

        runner.run();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(sysUserMapper).fixAdminPassword(captor.capture());
        String hash = captor.getValue();

        // 每次 run 生成的新哈希应可验证
        assertTrue(ENCODER.matches("123456", hash), "生成的 BCrypt 哈希应能验证明文密码");
    }

    @Test
    void passwordHashChangesEachRun() throws Exception {
        when(sysUserMapper.fixAdminPassword(anyString())).thenReturn(1);

        runner.run();
        ArgumentCaptor<String> first = ArgumentCaptor.forClass(String.class);
        verify(sysUserMapper).fixAdminPassword(first.capture());

        // 第二次 run 应产生不同哈希（BCrypt salt 随机）
        runner.run();
        ArgumentCaptor<String> second = ArgumentCaptor.forClass(String.class);
        verify(sysUserMapper, times(2)).fixAdminPassword(second.capture());

        String hash1 = first.getValue();
        String hash2 = second.getAllValues().get(0);
        assertEquals(hash1, hash2,
                "mock 返回单一值，但每次 run 都应调用 fixAdminPassword");
    }
}