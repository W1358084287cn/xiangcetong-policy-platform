package com.xiangcetong.policy.service;

import com.xiangcetong.common.PageResult;

import java.util.Map;

/**
 * 村民政策收藏：增删查与是否已收藏。
 */
public interface PolicyFavoriteService {

    /** 收藏政策（幂等：已收藏也返回成功） */
    Map<String, Object> addFavorite(Long policyId);

    /** 取消收藏（幂等：未收藏也返回 favorited=false） */
    Map<String, Object> removeFavorite(Long policyId);

    /** 分页列出当前村民收藏的上架政策 */
    PageResult<Map<String, Object>> listFavorites(Integer pageNum, Integer pageSize);

    /** 当前用户是否已收藏该政策（未登录或非村民返回 false） */
    boolean isFavorited(Long policyId);
}
