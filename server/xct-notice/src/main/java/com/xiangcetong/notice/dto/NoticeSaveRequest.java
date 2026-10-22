package com.xiangcetong.notice.dto;

/**
 * 通知公告新增/编辑请求体。
 * title 必填（1~128 字）；content 可选；status 可选（缺省 ONLINE）；sort 可选（缺省 0）。
 */
public class NoticeSaveRequest {

    private String title;
    private String content;
    private String status;
    private Integer sort;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }
}
