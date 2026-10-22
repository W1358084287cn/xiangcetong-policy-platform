package com.xiangcetong.apply.support;

import com.xiangcetong.common.BizException;
import com.xiangcetong.common.ErrorCodes;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 材料 Schema 校验：materials[] 嵌套 / fields[] 扁平 / 无 schema 兜底 / 必填与照片收集。
 */
class MaterialSchemaValidatorTest {

    private final MaterialSchemaValidator validator = new MaterialSchemaValidator();

    @Test
    void noSchemaCollectsPhotoUrls() {
        Map<String, Object> data = new HashMap<>();
        data.put("photo", "/uploads/a.jpg");
        List<String> photos = validator.validateAndCollectPhotos(null, data);
        assertEquals(List.of("/uploads/a.jpg"), photos);
    }

    @Test
    void noSchemaAndNoPhotoFails() {
        BizException ex = assertThrows(BizException.class,
                () -> validator.validateAndCollectPhotos("", Map.of("name", "张三")));
        assertEquals(ErrorCodes.BIZ_RULE, ex.getCode());
    }

    @Test
    void nestedMaterialsCompleteCollectsPhotos() {
        String schema = "{\"materials\":[{\"code\":\"id_copy\",\"fields\":["
                + "{\"key\":\"front\",\"type\":\"photo\",\"label\":\"正面\"},"
                + "{\"key\":\"back\",\"type\":\"photo\",\"label\":\"反面\"}]}]}";
        Map<String, Object> data = Map.of("id_copy",
                Map.of("front", "/uploads/front.jpg", "back", "/uploads/back.jpg"));
        List<String> photos = validator.validateAndCollectPhotos(schema, data);
        assertEquals(List.of("/uploads/front.jpg", "/uploads/back.jpg"), photos);
    }

    @Test
    void nestedMaterialsMissingRequiredFails() {
        String schema = "{\"materials\":[{\"code\":\"id_copy\",\"fields\":["
                + "{\"key\":\"front\",\"type\":\"photo\",\"label\":\"正面\"}]}]}";
        BizException ex = assertThrows(BizException.class,
                () -> validator.validateAndCollectPhotos(schema, Map.of("id_copy", Map.of())));
        assertEquals(ErrorCodes.BIZ_RULE, ex.getCode());
        assertEquals("材料未齐：id_copy.正面", ex.getMessage());
    }

    @Test
    void flatFieldsCollectsPhotos() {
        String schema = "{\"fields\":[{\"key\":\"photo\",\"type\":\"photo\",\"label\":\"照片\"}]}";
        List<String> photos = validator.validateAndCollectPhotos(schema,
                Map.of("photo", "/uploads/c.jpg"));
        assertEquals(List.of("/uploads/c.jpg"), photos);
    }

    @Test
    void optionalFieldMissingIsAllowed() {
        String schema = "{\"fields\":[{\"key\":\"remark\",\"type\":\"text\",\"required\":false}]}";
        List<String> photos = validator.validateAndCollectPhotos(schema, Map.of());
        assertEquals(List.of(), photos);
    }

    @Test
    void textFieldNotCollectedAsPhoto() {
        String schema = "{\"fields\":[{\"key\":\"name\",\"type\":\"text\",\"label\":\"户名\"}]}";
        List<String> photos = validator.validateAndCollectPhotos(schema, Map.of("name", "张三"));
        assertEquals(List.of(), photos);
    }

    @Test
    void invalidSchemaFails() {
        BizException ex = assertThrows(BizException.class,
                () -> validator.validateAndCollectPhotos("{not-json", Map.of("a", "b")));
        assertEquals(ErrorCodes.BIZ_RULE, ex.getCode());
    }
}