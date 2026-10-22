package com.xiangcetong.user.mapper;

import com.xiangcetong.user.entity.VillagerInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * villager_info 数据访问。
 */
@Mapper
public interface VillagerInfoMapper {

    VillagerInfo findByUserId(@Param("userId") Long userId);

    int insert(VillagerInfo info);

    int update(VillagerInfo info);
}
