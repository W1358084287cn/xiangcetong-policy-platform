package com.xiangcetong.common;

import java.util.Collections;
import java.util.List;

/**
 * 分页结果：list + total + pageNum + pageSize。
 */
public class PageResult<T> {

    private List<T> list;
    private long total;
    private int pageNum;
    private int pageSize;

    public PageResult() {
        this.list = Collections.emptyList();
    }

    public PageResult(List<T> list, long total, int pageNum, int pageSize) {
        this.list = list == null ? Collections.emptyList() : list;
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
    }

    /** 规范化页码：至少为 1 */
    public static int normalizePageNum(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    /** 规范化每页条数：默认 10，最大 50 */
    public static int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 10;
        }
        return Math.min(pageSize, 50);
    }

    /** 计算 SQL LIMIT 的 offset */
    public static int offset(int pageNum, int pageSize) {
        return (pageNum - 1) * pageSize;
    }

    public List<T> getList() {
        return list;
    }

    public void setList(List<T> list) {
        this.list = list;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public int getPageNum() {
        return pageNum;
    }

    public void setPageNum(int pageNum) {
        this.pageNum = pageNum;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
}
