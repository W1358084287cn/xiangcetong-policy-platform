package com.xiangcetong.policy.service;

import com.xiangcetong.common.BizException;
import com.xiangcetong.common.PageResult;
import com.xiangcetong.common.UserContext;
import com.xiangcetong.common.PublishStatus;
import com.xiangcetong.common.Role;
import com.xiangcetong.policy.entity.PolicyFavorite;
import com.xiangcetong.policy.entity.SubsidyPolicy;
import com.xiangcetong.policy.mapper.PolicyFavoriteMapper;
import com.xiangcetong.policy.mapper.SubsidyPolicyMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 村民政策收藏实现：增删查与是否已收藏。
 */
@Service
public class PolicyFavoriteServiceImpl implements PolicyFavoriteService {

    private final PolicyFavoriteMapper favoriteMapper;
    private final SubsidyPolicyMapper policyMapper;

    public PolicyFavoriteServiceImpl(PolicyFavoriteMapper favoriteMapper,
                                     SubsidyPolicyMapper policyMapper) {
        this.favoriteMapper = favoriteMapper;
        this.policyMapper = policyMapper;
    }

    /**
     * 收藏政策（幂等：已收藏也返回成功）。
     */
    @Override
    public Map<String, Object> addFavorite(Long policyId) {
        UserContext.LoginUser login = UserContext.requireRole(Role.VILLAGER);
        if (policyId == null) {
            throw BizException.badParam("政策 ID 不能为空");
        }
        SubsidyPolicy policy = policyMapper.findById(policyId);
        // 不存在或非上架：统一 404，避免泄露下架政策
        if (policy == null || !PublishStatus.ONLINE.equals(policy.getStatus())) {
            throw BizException.notFound("政策不存在");
        }
        // 已收藏则直接成功，不再插入
        if (favoriteMapper.findByUserAndPolicy(login.getUserId(), policyId) == null) {
            PolicyFavorite fav = new PolicyFavorite();
            fav.setUserId(login.getUserId());
            fav.setPolicyId(policyId);
            try {
                favoriteMapper.insert(fav);
            } catch (DuplicateKeyException e) {
                // 并发下唯一键冲突视为幂等成功
            }
        }
        Map<String, Object> m = new HashMap<>();
        m.put("policyId", policyId);
        m.put("favorited", true);
        return m;
    }

    /**
     * 取消收藏（幂等：未收藏也返回 favorited=false）。
     */
    @Override
    public Map<String, Object> removeFavorite(Long policyId) {
        UserContext.LoginUser login = UserContext.requireRole(Role.VILLAGER);
        if (policyId == null) {
            throw BizException.badParam("政策 ID 不能为空");
        }
        favoriteMapper.deleteByUserAndPolicy(login.getUserId(), policyId);
        Map<String, Object> m = new HashMap<>();
        m.put("policyId", policyId);
        m.put("favorited", false);
        return m;
    }

    /** 分页列出当前村民收藏的上架政策 */
    @Override
    public PageResult<Map<String, Object>> listFavorites(Integer pageNum, Integer pageSize) {
        UserContext.LoginUser login = UserContext.requireRole(Role.VILLAGER);
        int pn = PageResult.normalizePageNum(pageNum);
        int ps = PageResult.normalizePageSize(pageSize);
        long total = favoriteMapper.countByUser(login.getUserId());
        List<PolicyFavorite> rows = favoriteMapper.listByUser(
                login.getUserId(), PageResult.offset(pn, ps), ps);
        List<Map<String, Object>> list = rows.stream().map(f -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", f.getPolicyId());
            item.put("title", f.getTitle());
            item.put("standard", f.getStandard());
            item.put("status", f.getStatus());
            item.put("favoritedAt", f.getCreatedAt());
            return item;
        }).collect(Collectors.toList());
        return new PageResult<>(list, total, pn, ps);
    }

    /**
     * 当前用户是否已收藏该政策。
     * 未登录或非村民时返回 false。
     */
    @Override
    public boolean isFavorited(Long policyId) {
        if (policyId == null) {
            return false;
        }
        UserContext.LoginUser login = UserContext.get();
        if (login == null || !Role.VILLAGER.equals(login.getRole())) {
            return false;
        }
        return favoriteMapper.findByUserAndPolicy(login.getUserId(), policyId) != null;
    }
}
