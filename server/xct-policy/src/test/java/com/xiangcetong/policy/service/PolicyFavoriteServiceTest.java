package com.xiangcetong.policy.service;

import com.xiangcetong.common.BizException;
import com.xiangcetong.common.PageResult;
import com.xiangcetong.common.UserContext;
import com.xiangcetong.policy.entity.PolicyFavorite;
import com.xiangcetong.policy.entity.SubsidyPolicy;
import com.xiangcetong.policy.mapper.PolicyFavoriteMapper;
import com.xiangcetong.policy.mapper.SubsidyPolicyMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 政策收藏服务单测（mock 两个 Mapper，不连库）。
 * 覆盖：收藏幂等/下架拦截、取消收藏、是否已收藏（含未登录/非村民）。
 */
class PolicyFavoriteServiceTest {

    private static final Long USER_ID = 1L;

    private PolicyFavoriteMapper favoriteMapper;
    private SubsidyPolicyMapper policyMapper;
    private PolicyFavoriteService service;

    @BeforeEach
    void setUp() {
        favoriteMapper = mock(PolicyFavoriteMapper.class);
        policyMapper = mock(SubsidyPolicyMapper.class);
        service = new PolicyFavoriteServiceImpl(favoriteMapper, policyMapper);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void addFavorite_nullPolicyId_throws() {
        login("VILLAGER");
        assertThrows(BizException.class, () -> service.addFavorite(null));
    }

    @Test
    void addFavorite_offlinePolicy_throws() {
        login("VILLAGER");
        SubsidyPolicy p = policy(1L);
        p.setStatus("OFFLINE");
        when(policyMapper.findById(1L)).thenReturn(p);

        assertThrows(BizException.class, () -> service.addFavorite(1L));
    }

    @Test
    void addFavorite_success_inserts() {
        login("VILLAGER");
        when(policyMapper.findById(1L)).thenReturn(policy(1L));
        when(favoriteMapper.findByUserAndPolicy(USER_ID, 1L)).thenReturn(null);

        Map<String, Object> result = service.addFavorite(1L);

        assertEquals(1L, result.get("policyId"));
        assertEquals(true, result.get("favorited"));
        verify(favoriteMapper).insert(any());
    }

    @Test
    void addFavorite_alreadyFavorited_idempotent() {
        login("VILLAGER");
        when(policyMapper.findById(1L)).thenReturn(policy(1L));
        when(favoriteMapper.findByUserAndPolicy(USER_ID, 1L)).thenReturn(new PolicyFavorite());

        Map<String, Object> result = service.addFavorite(1L);

        assertEquals(true, result.get("favorited"));
        verify(favoriteMapper, never()).insert(any());
    }

    @Test
    void removeFavorite_returnsFalse() {
        login("VILLAGER");
        Map<String, Object> result = service.removeFavorite(1L);
        assertEquals(false, result.get("favorited"));
        verify(favoriteMapper).deleteByUserAndPolicy(USER_ID, 1L);
    }

    @Test
    void listFavorites_returnsPage() {
        login("VILLAGER");
        when(favoriteMapper.countByUser(USER_ID)).thenReturn(1L);
        PolicyFavorite fav = new PolicyFavorite();
        fav.setPolicyId(9L);
        fav.setTitle("种粮补贴");
        fav.setStandard("每亩 100 元");
        fav.setStatus("ONLINE");
        when(favoriteMapper.listByUser(USER_ID, 0, 10)).thenReturn(List.of(fav));

        PageResult<Map<String, Object>> result = service.listFavorites(1, 10);

        assertEquals(1, result.getTotal());
        assertEquals(9L, result.getList().get(0).get("id"));
    }

    @Test
    void isFavorited_notLoggedIn_false() {
        assertFalse(service.isFavorited(1L));
    }

    @Test
    void isFavorited_nonVillager_false() {
        login("ADMIN");
        assertFalse(service.isFavorited(1L));
    }

    @Test
    void isFavorited_villagerFavorited_true() {
        login("VILLAGER");
        when(favoriteMapper.findByUserAndPolicy(USER_ID, 1L)).thenReturn(new PolicyFavorite());
        assertTrue(service.isFavorited(1L));
    }

    // ---------- helpers ----------

    private void login(String role) {
        UserContext.LoginUser login = new UserContext.LoginUser();
        login.setUserId(USER_ID);
        login.setRole(role);
        login.setPhone("13800000001");
        login.setName("张三");
        UserContext.set(login);
    }

    private SubsidyPolicy policy(Long id) {
        SubsidyPolicy p = new SubsidyPolicy();
        p.setId(id);
        p.setStatus("ONLINE");
        p.setTitle("种粮补贴");
        return p;
    }
}
