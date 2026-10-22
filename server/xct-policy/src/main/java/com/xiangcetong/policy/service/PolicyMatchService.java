package com.xiangcetong.policy.service;

import com.xiangcetong.common.PageResult;
import com.xiangcetong.policy.entity.SubsidyPolicy;

import java.util.Map;

/**
 * 村民政策匹配：用 MySQL 多条件 WHERE 按村民画像筛选适合的政策（不再做加权打分）。
 */
public interface PolicyMatchService {

    /**
     * 匹配政策列表（仅村民）。
     * includeNone=true 时：并上「未命中」的上架政策，标为 NONE（仍不打分，仅标记）。
     */
    PageResult<Map<String, Object>> match(String plantTypeFilter,
                                          String category,
                                          String townNameFilter,
                                          Boolean includeNone,
                                          Integer pageNum,
                                          Integer pageSize);

    /** 当前登录村民对单条政策是否命中 WHERE 条件；非村民/资料不全返回 null */
    Map<String, Object> myMatchSummary(SubsidyPolicy policy);
}
