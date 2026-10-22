package com.xiangcetong.user.mapper;

import com.xiangcetong.user.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * sys_user 数据访问。
 */
@Mapper
public interface SysUserMapper {

    SysUser findByPhone(@Param("phone") String phone);

    SysUser findById(@Param("id") Long id);

    /** 管理端按账号 + 角色定位用户 */
    SysUser findByUsernameAndRole(@Param("username") String username, @Param("role") String role);

    /** 修改密码 */
    int updatePassword(@Param("id") Long id, @Param("passwordHash") String passwordHash);

    int insert(SysUser user);

    int updateVillageCode(@Param("id") Long id, @Param("villageCode") String villageCode);

    /** 更新姓名/手机号（仅非 null 字段） */
    int updateNamePhone(@Param("id") Long id,
                        @Param("name") String name,
                        @Param("phone") String phone);

    long countAdmin(@Param("role") String role, @Param("phone") String phone);

    List<SysUser> listAdmin(@Param("role") String role,
                            @Param("phone") String phone,
                            @Param("offset") int offset,
                            @Param("limit") int limit);

    /** 删除用户（根据 id） */
    int deleteById(@Param("id") Long id);

    /** 修复 admin 账号密码（启动时自动执行） */
    int fixAdminPassword(@Param("passwordHash") String passwordHash);
}