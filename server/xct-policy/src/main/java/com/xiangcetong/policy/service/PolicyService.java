package com.xiangcetong.policy.service;

import com.xiangcetong.common.PageResult;
import com.xiangcetong.policy.dto.PolicySaveRequest;

import java.util.Map;

/**
 * 政策：公开只读 + 管理端 CRUD（含结构化匹配字段与材料扩展展示）。
 */
public interface PolicyService {

    /** H5/公开：默认只查 ONLINE */
    PageResult<Map<String, Object>> publicList(String status, Integer pageNum, Integer pageSize);

    /** 公开详情：OFFLINE 当不存在处理；登录村民附加 myMatch、favorited */
    Map<String, Object> publicDetail(Long id);

    /** 管理端列表：仅 SYS_ADMIN */
    PageResult<Map<String, Object>> adminList(String status, Integer pageNum, Integer pageSize);

    /** 管理端详情：仅 SYS_ADMIN */
    Map<String, Object> adminDetail(Long id);

    /** 新增政策：仅 SYS_ADMIN */
    Map<String, Object> create(PolicySaveRequest req);

    /** 编辑政策：仅 SYS_ADMIN */
    Map<String, Object> update(Long id, PolicySaveRequest req);

    /** 上下架：仅 SYS_ADMIN */
    Map<String, Object> updateStatus(Long id, String status);

    /** 模糊搜索：关键词匹配标题或正文，仅上架政策 */
    PageResult<Map<String, Object>> search(String keyword, Integer pageNum, Integer pageSize);

    /** 从政策表全量重建 AI 知识库：仅 SYS_ADMIN */
    Map<String, Object> rebuildKnowledge();
}