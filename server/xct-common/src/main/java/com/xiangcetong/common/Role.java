package com.xiangcetong.common;

import java.util.Set;

/**
 * 系统角色常量。
 */
public final class Role {

    /** 村民 */
    public static final String VILLAGER = "VILLAGER";
    /** 村级管理员 */
    public static final String VILLAGE_ADMIN = "VILLAGE_ADMIN";
    /** 镇级管理员 */
    public static final String TOWN_ADMIN = "TOWN_ADMIN";
    /** 系统管理员 */
    public static final String SYS_ADMIN = "SYS_ADMIN";

    /** 全部合法角色 */
    public static final Set<String> ALL = Set.of(VILLAGER, VILLAGE_ADMIN, TOWN_ADMIN, SYS_ADMIN);
    /** 管理端（PC）账号登录允许的角色 */
    public static final Set<String> ADMIN = Set.of(VILLAGE_ADMIN, TOWN_ADMIN, SYS_ADMIN);

    private Role() {
    }
}
