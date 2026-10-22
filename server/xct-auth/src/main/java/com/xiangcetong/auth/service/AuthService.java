package com.xiangcetong.auth.service;

import com.xiangcetong.auth.dto.ChangePasswordRequest;
import com.xiangcetong.auth.dto.LoginRequest;

import java.util.Map;

/**
 * 认证：登录（手机号 / 管理端账号+角色）、修改密码。
 */
public interface AuthService {

    /** 登录：手机号密码（旧）或 账号+角色+密码（管理端新），返回 token + 用户摘要 */
    Map<String, Object> login(LoginRequest request);

    /** 修改当前登录用户自己的密码（须登录，任意角色） */
    void changePassword(ChangePasswordRequest request);
}
