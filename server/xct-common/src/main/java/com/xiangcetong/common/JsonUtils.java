package com.xiangcetong.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 申请相关 JSON 字符串与对象互转、解析为 Map/List 的公共工具。
 * 静态无状态、线程安全（ObjectMapper 仅处理 String/List/Map，不含时间类型）。
 */
public final class JsonUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonUtils() {
    }

    /** JSON 文本转任意对象（Object）；空串/非法 → null */
    public static Object parseJsonObject(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, Object.class);
        } catch (Exception e) {
            return null;
        }
    }

    /** JSON 文本转 Map；空串/非法 → null */
    public static Map<String, Object> parseMaterialData(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return null;
        }
    }

    /** 材料 URL JSON 数组转 List；空返回空列表，非法回退 singletonList(原串) */
    public static List<String> parseUrls(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return Collections.singletonList(json);
        }
    }

    /** Map 序列化为 JSON 字符串；失败抛服务端错误 */
    public static String toJsonObject(Map<String, Object> data) {
        try {
            return MAPPER.writeValueAsString(data);
        } catch (Exception e) {
            throw new BizException(ErrorCodes.SERVER_ERROR, "材料数据序列化失败", 500);
        }
    }

    /** List&lt;String&gt; 序列化为 JSON；失败抛服务端错误 */
    public static String toJson(List<String> urls) {
        try {
            return MAPPER.writeValueAsString(urls);
        } catch (Exception e) {
            throw new BizException(ErrorCodes.SERVER_ERROR, "材料序列化失败", 500);
        }
    }

    /** JSON 文本解析为 JsonNode（供材料 Schema 校验使用）；非法 JSON 抛异常由调用方兜底 */
    public static JsonNode readTree(String json) throws JsonProcessingException {
        return MAPPER.readTree(json);
    }
}
