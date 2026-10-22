package com.xiangcetong.common;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JSON 互转工具：解析对象 / Map / URL 数组、序列化、非法输入兜底。
 */
class JsonUtilsTest {

    @Test
    void parseJsonObjectNullAndBlankReturnNull() {
        assertNull(JsonUtils.parseJsonObject(null));
        assertNull(JsonUtils.parseJsonObject("   "));
    }

    @Test
    void parseJsonObjectInvalidReturnNull() {
        assertNull(JsonUtils.parseJsonObject("{not-json"));
    }

    @Test
    void parseJsonObjectValidReturnsObject() {
        Object obj = JsonUtils.parseJsonObject("{\"a\":1}");
        assertTrue(obj instanceof Map);
        assertEquals(1, ((Map<?, ?>) obj).get("a"));
    }

    @Test
    void parseMaterialDataValidAndInvalid() {
        Map<String, Object> m = JsonUtils.parseMaterialData("{\"name\":\"张三\"}");
        assertEquals("张三", m.get("name"));
        assertNull(JsonUtils.parseMaterialData("not-json"));
        assertNull(JsonUtils.parseMaterialData(null));
    }

    @Test
    void parseUrlsValidArray() {
        List<String> urls = JsonUtils.parseUrls("[\"/uploads/a.jpg\",\"/uploads/b.jpg\"]");
        assertEquals(2, urls.size());
        assertEquals("/uploads/a.jpg", urls.get(0));
    }

    @Test
    void parseUrlsEmptyAndInvalid() {
        assertTrue(JsonUtils.parseUrls(null).isEmpty());
        assertTrue(JsonUtils.parseUrls("").isEmpty());
        // 非法 JSON 回退为单元素列表（兼容历史把裸 URL 直接存库的场景）
        List<String> fallback = JsonUtils.parseUrls("/uploads/plain.jpg");
        assertEquals(1, fallback.size());
        assertEquals("/uploads/plain.jpg", fallback.get(0));
    }

    @Test
    void toJsonObjectRoundTrip() {
        String json = JsonUtils.toJsonObject(Map.of("key", "value"));
        assertEquals("{\"key\":\"value\"}", json);
    }

    @Test
    void toJsonListRoundTrip() {
        assertEquals("[\"a\",\"b\"]", JsonUtils.toJson(List.of("a", "b")));
    }

    @Test
    void readTreeInvalidThrows() {
        assertThrows(Exception.class, () -> JsonUtils.readTree("{bad"));
    }

    @Test
    void readTreeValid() throws Exception {
        JsonNode node = JsonUtils.readTree("{\"x\":true}");
        assertTrue(node.get("x").asBoolean());
    }
}
