package com.xiangcetong.policy.mapper;

import com.xiangcetong.policy.entity.PolicyFavorite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * policy_favorite 数据访问。
 */
@Mapper
public interface PolicyFavoriteMapper {

    int insert(PolicyFavorite favorite);

    int deleteByUserAndPolicy(@Param("userId") Long userId, @Param("policyId") Long policyId);

    PolicyFavorite findByUserAndPolicy(@Param("userId") Long userId, @Param("policyId") Long policyId);

    long countByUser(@Param("userId") Long userId);

    /** 仅联查 ONLINE 政策，下架政策从收藏列表剔除 */
    List<PolicyFavorite> listByUser(@Param("userId") Long userId,
                                    @Param("offset") int offset,
                                    @Param("limit") int limit);
}
