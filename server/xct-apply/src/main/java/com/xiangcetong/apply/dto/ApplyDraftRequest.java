package com.xiangcetong.apply.dto;

import java.util.Map;

/**
 * 保存/更新申报草稿。
 */
public class ApplyDraftRequest {

    private Long policyId;
    private Map<String, Object> materialData;

    public Long getPolicyId() {
        return policyId;
    }

    public void setPolicyId(Long policyId) {
        this.policyId = policyId;
    }

    public Map<String, Object> getMaterialData() {
        return materialData;
    }

    public void setMaterialData(Map<String, Object> materialData) {
        this.materialData = materialData;
    }
}
