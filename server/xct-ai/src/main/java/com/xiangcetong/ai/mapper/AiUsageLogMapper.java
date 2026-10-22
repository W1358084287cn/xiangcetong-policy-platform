package com.xiangcetong.ai.mapper;

import com.xiangcetong.ai.entity.AiUsageLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

/**
 * AI 用量日志 Mapper。
 */
@Mapper
public interface AiUsageLogMapper {

    int insert(AiUsageLog log);

    /** 查询今日消耗汇总 */
    Map<String, Object> sumToday();
}