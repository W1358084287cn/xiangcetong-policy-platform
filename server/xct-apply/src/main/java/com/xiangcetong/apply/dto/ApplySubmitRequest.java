package com.xiangcetong.apply.dto;

import java.util.List;
import java.util.Map;

/**
 * 村民提交申请（兼容直提；可带结构化 materialData）。
 */
public class ApplySubmitRequest {

    private Long policyId;
    private List<String> materialUrls;
    private Map<String, Object> materialData;

    public Long getPolicyId() {
        return policyId;
    }

    public void setPolicyId(Long policyId) {
        this.policyId = policyId;
    }

    public List<String> getMaterialUrls() {
        return materialUrls;
    }

    public void setMaterialUrls(List<String> materialUrls) {
        this.materialUrls = materialUrls;
    }

    public Map<String, Object> getMaterialData() {
        return materialData;
    }

    public void setMaterialData(Map<String, Object> materialData) {
        this.materialData = materialData;
    }
}
