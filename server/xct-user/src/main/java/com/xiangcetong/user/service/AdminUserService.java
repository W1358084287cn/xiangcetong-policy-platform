package com.xiangcetong.user.service;

import com.xiangcetong.common.PageResult;
import com.xiangcetong.user.dto.CreateUserRequest;

import java.util.Map;

/**
 * 系统管理员：用户列表、新增、删除。
 */
public interface AdminUserService {

    /** 分页用户列表（不含密码） */
    PageResult<Map<String, Object>> list(String role, String phone,
                                         Integer pageNum, Integer pageSize);

    /** 创建用户；村民角色顺带建空 villager_info */
    Map<String, Object> create(CreateUserRequest req);

    /** 删除用户（同时删 villainfo） */
    void delete(Long id);
}