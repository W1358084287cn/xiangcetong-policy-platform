package com.xiangcetong.notice.service;

import com.xiangcetong.common.BizException;
import com.xiangcetong.common.UserContext;
import com.xiangcetong.notice.dto.NoticeSaveRequest;
import com.xiangcetong.notice.entity.Notice;
import com.xiangcetong.notice.mapper.NoticeMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 通知公告 Service 单测（mock NoticeMapper，不连库）。
 * 覆盖：active 仅返回 ONLINE 且按 sort 倒序、新增/编辑/下线/删除流程、title 为空报错、角色限制。
 */
class NoticeServiceTest {

    private NoticeMapper noticeMapper;
    private NoticeService service;

    @BeforeEach
    void setUp() {
        noticeMapper = mock(NoticeMapper.class);
        service = new NoticeServiceImpl(noticeMapper);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private void loginAs(String role, Long userId) {
        UserContext.LoginUser u = new UserContext.LoginUser();
        u.setUserId(userId);
        u.setRole(role);
        UserContext.set(u);
    }

    private Notice notice(long id, String title, String status, int sort) {
        Notice n = new Notice();
        n.setId(id);
        n.setTitle(title);
        n.setStatus(status);
        n.setSort(sort);
        n.setContent("正文" + id);
        return n;
    }

    // ---------- active：H5 滚动字幕 ----------

    @Test
    void activeRequiresLogin() {
        // 未登录 → 401 业务异常，且不查库
        assertThrows(BizException.class, () -> service.active());
        verify(noticeMapper, never()).listActive(anyInt());
    }

    @Test
    void activeReturnsOnlyOnlineAndPreservesSortOrder() {
        loginAs("VILLAGER", 1L);
        // listActive 已在 SQL 层过滤 status='ONLINE' 并 ORDER BY sort DESC, id DESC
        List<Notice> rows = List.of(
                notice(2L, "高优先级通知", "ONLINE", 100),
                notice(1L, "普通通知", "ONLINE", 50)
        );
        when(noticeMapper.listActive(10)).thenReturn(rows);

        List<Map<String, Object>> result = service.active();

        assertEquals(2, result.size());
        // 保持 sort 倒序返回的顺序
        assertEquals(2L, result.get(0).get("id"));
        assertEquals(1L, result.get(1).get("id"));
        // H5 只暴露 id/title/content，不泄露 status/sort 等管理字段
        assertEquals(Set.of("id", "title", "content"), result.get(0).keySet());
        // 走的是 listActive（仅 ONLINE），而非全量 list
        verify(noticeMapper).listActive(10);
        verify(noticeMapper, never()).list(anyInt(), anyInt());
    }

    // ---------- 角色限制 ----------

    @Test
    void adminOpsRequireSysAdmin() {
        loginAs("VILLAGER", 1L);

        NoticeSaveRequest req = new NoticeSaveRequest();
        req.setTitle("标题");

        assertThrows(BizException.class, () -> service.adminList(1, 10));
        assertThrows(BizException.class, () -> service.create(req));
        assertThrows(BizException.class, () -> service.update(1L, req));
        assertThrows(BizException.class, () -> service.delete(1L));
    }

    // ---------- 新增 ----------

    @Test
    void createBlankTitleFails() {
        loginAs("SYS_ADMIN", 99L);
        NoticeSaveRequest req = new NoticeSaveRequest();
        req.setTitle("   ");

        assertThrows(BizException.class, () -> service.create(req));
        verify(noticeMapper, never()).insert(any());
    }

    @Test
    void createNullTitleFails() {
        loginAs("SYS_ADMIN", 99L);
        NoticeSaveRequest req = new NoticeSaveRequest();

        assertThrows(BizException.class, () -> service.create(req));
        verify(noticeMapper, never()).insert(any());
    }

    @Test
    void createTitleTooLongFails() {
        loginAs("SYS_ADMIN", 99L);
        NoticeSaveRequest req = new NoticeSaveRequest();
        req.setTitle("a".repeat(129));

        assertThrows(BizException.class, () -> service.create(req));
        verify(noticeMapper, never()).insert(any());
    }

    @Test
    void createDefaultsStatusOnlineAndSortZero() {
        loginAs("SYS_ADMIN", 99L);
        NoticeSaveRequest req = new NoticeSaveRequest();
        req.setTitle("新通知");
        req.setContent("通知正文");
        // 模拟 MyBatis useGeneratedKeys 回填 id
        doAnswer(inv -> {
            Notice n = inv.getArgument(0);
            n.setId(100L);
            return 1;
        }).when(noticeMapper).insert(any());
        when(noticeMapper.findById(100L)).thenReturn(notice(100L, "新通知", "ONLINE", 0));

        Map<String, Object> result = service.create(req);

        ArgumentCaptor<Notice> captor = ArgumentCaptor.forClass(Notice.class);
        verify(noticeMapper).insert(captor.capture());
        Notice saved = captor.getValue();
        assertEquals("新通知", saved.getTitle());
        assertEquals("ONLINE", saved.getStatus());
        assertEquals(0, saved.getSort().intValue());
        assertEquals(99L, saved.getCreatedBy().longValue());
        assertEquals("新通知", result.get("title"));
    }

    // ---------- 编辑 / 下线 ----------

    @Test
    void updateTakesOffline() {
        loginAs("SYS_ADMIN", 99L);
        Notice online = notice(1L, "旧标题", "ONLINE", 5);
        Notice offline = notice(1L, "旧标题", "OFFLINE", 5);
        // 第一次 findById 取原值，第二次取更新后的值
        when(noticeMapper.findById(1L)).thenReturn(online, offline);

        NoticeSaveRequest req = new NoticeSaveRequest();
        req.setStatus("OFFLINE");

        Map<String, Object> result = service.update(1L, req);

        verify(noticeMapper).update(argThat(n ->
                "OFFLINE".equals(n.getStatus()) && "旧标题".equals(n.getTitle()) && n.getSort() == 5));
        assertEquals("OFFLINE", result.get("status"));
    }

    @Test
    void updatePartialKeepsExistingFields() {
        loginAs("SYS_ADMIN", 99L);
        when(noticeMapper.findById(1L)).thenReturn(notice(1L, "旧标题", "ONLINE", 5));

        NoticeSaveRequest req = new NoticeSaveRequest();
        req.setSort(8);

        service.update(1L, req);

        verify(noticeMapper).update(argThat(n ->
                "旧标题".equals(n.getTitle()) && "ONLINE".equals(n.getStatus()) && n.getSort() == 8));
    }

    @Test
    void updateNotFoundFails() {
        loginAs("SYS_ADMIN", 99L);
        when(noticeMapper.findById(1L)).thenReturn(null);

        NoticeSaveRequest req = new NoticeSaveRequest();
        req.setTitle("新");

        assertThrows(BizException.class, () -> service.update(1L, req));
        verify(noticeMapper, never()).update(any());
    }

    // ---------- 删除 ----------

    @Test
    void deleteCallsMapperAndReturnsId() {
        loginAs("SYS_ADMIN", 99L);
        when(noticeMapper.findById(1L)).thenReturn(notice(1L, "待删除", "ONLINE", 0));

        Map<String, Object> result = service.delete(1L);

        verify(noticeMapper).deleteById(1L);
        assertEquals(1L, result.get("id"));
    }

    @Test
    void deleteNotFoundFails() {
        loginAs("SYS_ADMIN", 99L);
        when(noticeMapper.findById(1L)).thenReturn(null);

        assertThrows(BizException.class, () -> service.delete(1L));
        verify(noticeMapper, never()).deleteById(anyLong());
    }
}
