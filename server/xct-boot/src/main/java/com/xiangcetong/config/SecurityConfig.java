package com.xiangcetong.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 安全相关 Bean 装配。
 * 密码加密器交给 Spring 容器管理，各模块通过构造器注入 {@link PasswordEncoder} 接口，
 * 避免在 Service 内手动 new 具体实现，降低模块间耦合、便于替换算法。
 */
@Configuration
public class SecurityConfig {

    /** BCrypt 密码加密器（登录校验 / 建号 / 改密共用同一实例） */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
