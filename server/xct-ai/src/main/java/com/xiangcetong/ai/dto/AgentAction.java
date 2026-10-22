package com.xiangcetong.ai.dto;

/**
 * Agent 执行动作类型枚举。
 */
public enum AgentAction {

    /** 查看个人资料 */
    VIEW_PROFILE,
    /** 匹配适合的政策 */
    MATCH_POLICY,
    /** 查看某政策详情 */
    POLICY_DETAIL,
    /** 开始申报流程 */
    START_APPLY,
    /** 填写申报字段 */
    FILL_FIELD,
    /** 修改申报字段 */
    MODIFY_FIELD,
    /** 提交申报 */
    SUBMIT_APPLY,
    /** 查看我的申报列表 */
    MY_APPLIES,
    /** 查看申报进度详情 */
    APPLY_STATUS,
    /** 搜索知识库 */
    SEARCH_KB,
    /** 取消当前操作 */
    CANCEL_ACTION,
    /** 自由对话（AI 兜底） */
    FREE_CHAT
}