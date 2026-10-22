package com.xiangcetong.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 修改密码请求：旧密码 + 新密码。
 * 新密码长度 6~32 位在 AuthService 内校验。
 */
public class ChangePasswordRequest {

    @NotBlank(message = "旧密码不能为空")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    private String newPassword;

    public String getOldPassword() {
        return oldPassword;
    }

    public void setOldPassword(String oldPassword) {
        this.oldPassword = ***;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = ***;
    }
}
