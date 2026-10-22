package com.xiangcetong.ai.mapper;

import com.xiangcetong.ai.entity.AiConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 配置 Mapper（全局唯一一条记录）。
 */
@Mapper
public interface AiConfigMapper {

    /** 查询唯一配置 */
    AiConfig findFirst();

    /** 更新配置 */
    int update(AiConfig config);
}