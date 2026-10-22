package com.xiangcetong.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求，兼容两种入参：
 * 1. 旧版：手机号 + 密码（phone + password），村民端沿用；
 * 2. 管理端新版：账号 + 角色 + 密码（username + role + password）。
 * 手机号格式校验移到 AuthService 里按旧版路径单独做，避免管理端不传手机号时被 @Valid 误拦。
 */
public class LoginRequest {

    /** 手机号（旧版登录用） */
    private String phone;

    /** 账号（管理端登录用，统一为 admin） */
    private String username;

    /** 管理端角色：VILLAGE_ADMIN / TOWN_ADMIN / SYS_ADMIN */
    private String role;

    @NotBlank(message = "密码不能为空")
    private String password;

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = ***;
    }
}
