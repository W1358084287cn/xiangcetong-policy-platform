package com.xiangcetong.config;

import com.xiangcetong.user.mapper.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 启动时自动修复 admin 账号密码为 123456。
 * 解决 seed/patch SQL 中 BCrypt 哈希值不匹配导致登录失败的问题。
 */
@Component
public class AdminPasswordFixRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminPasswordFixRunner.class);

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;

    public AdminPasswordFixRunner(SysUserMapper sysUserMapper, PasswordEncoder passwordEncoder) {
        this.sysUserMapper = sysUserMapper;
        this.passwordEncoder = ***;
    }

    @Override
    public void run(String... args) {
        String correctHash = passwordEncoder.encode("123456");
        int updated = sysUserMapper.fixAdminPassword(correctHash);
        if (updated > 0) {
            log.info("已修复 {} 个 admin 账号密码为 123456", updated);
        }
    }
}