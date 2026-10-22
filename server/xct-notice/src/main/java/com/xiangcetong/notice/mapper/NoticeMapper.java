package com.xiangcetong.notice.mapper;

import com.xiangcetong.notice.entity.Notice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 通知公告数据访问层。
 * 全部使用 #{} 预编译，禁止 ${} 拼接。
 */
@Mapper
public interface NoticeMapper {

    Notice findById(@Param("id") Long id);

    /** 统计全部通知（管理端分页 total） */
    long count();

    /** 管理端分页列表：按 sort 倒序、id 倒序 */
    List<Notice> list(@Param("offset") int offset, @Param("limit") int limit);

    /** H5 滚动字幕：仅 ONLINE，按 sort 倒序、id 倒序，最多 limit 条 */
    List<Notice> listActive(@Param("limit") int limit);

    int insert(Notice notice);

    int update(Notice notice);

    int deleteById(@Param("id") Long id);
}
