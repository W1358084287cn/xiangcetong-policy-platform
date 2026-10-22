package com.xiangcetong.notice.service;

import com.xiangcetong.common.PageResult;
import com.xiangcetong.notice.dto.NoticeSaveRequest;

import java.util.List;
import java.util.Map;

/**
 * 通知公告业务：
 * H5 首页滚动字幕（须登录，任意角色）+ 管理端 CRUD（仅 SYS_ADMIN）。
 */
public interface NoticeService {

    /** H5：仅返回 ONLINE 通知，data 结构 [{id,title,content}] */
    List<Map<String, Object>> active();

    /** 管理端分页：仅 SYS_ADMIN */
    PageResult<Map<String, Object>> adminList(Integer page, Integer size);

    /** 新增通知：title 必填 1~128 字；status 缺省 ONLINE；sort 缺省 0 */
    Map<String, Object> create(NoticeSaveRequest req);

    /** 编辑通知：允许部分字段更新（未传字段保留原值） */
    Map<String, Object> update(Long id, NoticeSaveRequest req);

    /** 删除通知：仅 SYS_ADMIN */
    Map<String, Object> delete(Long id);
}
