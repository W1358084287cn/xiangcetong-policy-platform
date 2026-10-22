package com.xiangcetong.user.service;

import com.xiangcetong.user.dto.ProfileUpdateRequest;

import java.util.Map;

/**
 * 村民个人信息读写（含精细画像字段；PUT 增量更新，Integer 的 0 也写入）。
 */
public interface VillagerService {

    /** 查询当前村民资料 */
    Map<String, Object> getProfile();

    /** 更新资料；村名变更时同步 sys_user.village_code */
    Map<String, Object> updateProfile(ProfileUpdateRequest req);
}
