package com.xiangcetong.apply.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.xiangcetong.common.BizException;
import com.xiangcetong.common.ErrorCodes;
import com.xiangcetong.common.JsonUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 按政策的 materialSchema 校验村民填写的申报材料是否齐全，并收集其中的照片 URL。
 * 支持 materials[] 嵌套与扁平 fields[] 两种 schema；无构造依赖，便于单测直接 new。
 */
@Component
public class MaterialSchemaValidator {

    /**
     * 校验 materialSchema 并收集 photo URL。
     * schema 为空时走「任意非空材料」兜底。
     */
    @SuppressWarnings("unchecked")
    public List<String> validateAndCollectPhotos(String schemaJson, Map<String, Object> data) {
        List<String> photos = new ArrayList<>();
        if (schemaJson == null || schemaJson.isBlank()) {
            flattenPhotoUrls(data, photos);
            if (photos.isEmpty()) {
                throw new BizException(ErrorCodes.BIZ_RULE, "请填写完整申报材料");
            }
            return photos;
        }
        try {
            JsonNode root = JsonUtils.readTree(schemaJson);
            if (root.has("materials") && root.get("materials").isArray()) {
                for (JsonNode mat : root.get("materials")) {
                    String code = mat.path("code").asText();
                    Object block = data.get(code);
                    Map<String, Object> blockMap = block instanceof Map
                            ? (Map<String, Object>) block : Map.of();
                    for (JsonNode field : mat.path("fields")) {
                        checkField(field, blockMap, photos, code);
                    }
                }
            } else if (root.has("fields") && root.get("fields").isArray()) {
                for (JsonNode field : root.get("fields")) {
                    checkField(field, data, photos, null);
                }
            } else {
                flattenPhotoUrls(data, photos);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCodes.BIZ_RULE, "材料校验失败：" + e.getMessage());
        }
        return photos;
    }

    private void checkField(JsonNode field, Map<String, Object> data, List<String> photos, String matCode) {
        String key = field.path("key").asText();
        String type = field.path("type").asText("text");
        boolean required = !field.has("required") || field.path("required").asBoolean(true);
        Object val = data.get(key);
        boolean empty = val == null || String.valueOf(val).isBlank();
        if (required && empty) {
            String label = field.path("label").asText(key);
            String tip = matCode != null ? matCode + "." + label : label;
            throw new BizException(ErrorCodes.BIZ_RULE, "材料未齐：" + tip);
        }
        if ("photo".equals(type) && !empty) {
            photos.add(String.valueOf(val));
        }
    }

    @SuppressWarnings("unchecked")
    private void flattenPhotoUrls(Map<String, Object> data, List<String> photos) {
        if (data == null) {
            return;
        }
        for (Object v : data.values()) {
            if (v instanceof String s && (s.startsWith("/uploads") || s.startsWith("http"))) {
                photos.add(s);
            } else if (v instanceof Map<?, ?> nested) {
                flattenPhotoUrls((Map<String, Object>) nested, photos);
            }
        }
    }
}