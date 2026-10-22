package com.xiangcetong.notice.service;

import com.xiangcetong.common.BizException;
import com.xiangcetong.common.ErrorCodes;
import com.xiangcetong.common.PageResult;
import com.xiangcetong.common.UserContext;
import com.xiangcetong.common.PublishStatus;
import com.xiangcetong.common.Role;
import com.xiangcetong.notice.dto.NoticeSaveRequest;
import com.xiangcetong.notice.entity.Notice;
import com.xiangcetong.notice.mapper.NoticeMapper;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 通知公告业务实现：
 * H5 首页滚动字幕（须登录，任意角色）+ 管理端 CRUD（仅 SYS_ADMIN）。
 */
@Service
public class NoticeServiceImpl implements NoticeService {

    /** H5 滚动字幕最多展示条数 */
    private static final int ACTIVE_LIMIT = 10;
    private static final int TITLE_MAX = 128;
    private static final int CONTENT_MAX = 2048;

    private final NoticeMapper noticeMapper;

    public NoticeServiceImpl(NoticeMapper noticeMapper) {
        this.noticeMapper = noticeMapper;
    }

    @Override
    public List<Map<String, Object>> active() {
        UserContext.require();
        List<Notice> rows = noticeMapper.listActive(ACTIVE_LIMIT);
        return rows.stream().map(this::toActiveVo).collect(Collectors.toList());
    }

    /** 管理端分页：仅 SYS_ADMIN */
    @Override
    public PageResult<Map<String, Object>> adminList(Integer page, Integer size) {
        UserContext.requireRole(Role.SYS_ADMIN);
        int pn = PageResult.normalizePageNum(page);
        int ps = PageResult.normalizePageSize(size);
        long total = noticeMapper.count();
        List<Notice> rows = noticeMapper.list(PageResult.offset(pn, ps), ps);
        List<Map<String, Object>> list = rows.stream().map(this::toVo).collect(Collectors.toList());
        return new PageResult<>(list, total, pn, ps);
    }

    /** 新增通知：title 必填 1~128 字；status 缺省 ONLINE；sort 缺省 0 */
    @Override
    public Map<String, Object> create(NoticeSaveRequest req) {
        UserContext.LoginUser admin = UserContext.requireRole(Role.SYS_ADMIN);
        if (req == null) {
            throw BizException.badParam("请求体不能为空");
        }
        validateTitle(req.getTitle());
        validateContent(req.getContent());

        Notice n = new Notice();
        n.setTitle(req.getTitle().trim());
        n.setContent(blankToNull(req.getContent()));
        n.setStatus(normalizeStatus(req.getStatus()));
        n.setSort(req.getSort() == null ? 0 : req.getSort());
        n.setCreatedBy(admin.getUserId());
        noticeMapper.insert(n);
        return toVo(noticeMapper.findById(n.getId()));
    }

    /** 编辑通知：允许部分字段更新（未传字段保留原值） */
    @Override
    public Map<String, Object> update(Long id, NoticeSaveRequest req) {
        UserContext.requireRole(Role.SYS_ADMIN);
        Notice exist = requireNotice(id);
        if (req == null) {
            throw BizException.badParam("请求体不能为空");
        }
        if (req.getTitle() != null) {
            validateTitle(req.getTitle());
        }
        if (req.getContent() != null) {
            validateContent(req.getContent());
        }
        if (req.getStatus() != null) {
            normalizeStatus(req.getStatus());
        }

        Notice n = new Notice();
        n.setId(id);
        n.setTitle(req.getTitle() != null ? req.getTitle().trim() : exist.getTitle());
        n.setContent(req.getContent() != null ? blankToNull(req.getContent()) : exist.getContent());
        n.setStatus(req.getStatus() != null ? normalizeStatus(req.getStatus()) : exist.getStatus());
        n.setSort(req.getSort() != null ? req.getSort() : exist.getSort());
        noticeMapper.update(n);
        return toVo(noticeMapper.findById(id));
    }

    /** 删除通知：仅 SYS_ADMIN */
    @Override
    public Map<String, Object> delete(Long id) {
        UserContext.requireRole(Role.SYS_ADMIN);
        requireNotice(id);
        noticeMapper.deleteById(id);
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        return m;
    }

    /** 标题必填且不超过 128 字 */
    private void validateTitle(String title) {
        if (title == null || title.isBlank()) {
            throw BizException.badParam("通知标题不能为空");
        }
        if (title.trim().length() > TITLE_MAX) {
            throw BizException.badParam("通知标题不能超过 128 字");
        }
    }

    /** 正文不超过 2048 字（与列宽对齐） */
    private void validateContent(String content) {
        if (content != null && content.length() > CONTENT_MAX) {
            throw BizException.badParam("通知正文不能超过 2048 字");
        }
    }

    /** status 仅 ONLINE/OFFLINE；空值默认 ONLINE */
    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return PublishStatus.ONLINE;
        }
        if (!PublishStatus.ONLINE.equals(status) && !PublishStatus.OFFLINE.equals(status)) {
            throw BizException.badParam("非法 status");
        }
        return status;
    }

    private Notice requireNotice(Long id) {
        Notice n = noticeMapper.findById(id);
        if (n == null) {
            throw new BizException(ErrorCodes.NOT_FOUND, "通知不存在", 404);
        }
        return n;
    }

    private String blankToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }

    /** H5：只暴露 id/title/content */
    private Map<String, Object> toActiveVo(Notice n) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", n.getId());
        m.put("title", n.getTitle());
        m.put("content", n.getContent());
        return m;
    }

    /** 管理端：完整字段 */
    private Map<String, Object> toVo(Notice n) {
        Map<String, Object> m = toActiveVo(n);
        m.put("status", n.getStatus());
        m.put("sort", n.getSort());
        m.put("createdBy", n.getCreatedBy());
        m.put("createdAt", n.getCreatedAt());
        m.put("updatedAt", n.getUpdatedAt());
        return m;
    }
}
