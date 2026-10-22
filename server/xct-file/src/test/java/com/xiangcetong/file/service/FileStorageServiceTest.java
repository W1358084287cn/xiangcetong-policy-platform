package com.xiangcetong.file.service;

import com.xiangcetong.common.BizException;
import com.xiangcetong.common.ErrorCodes;
import com.xiangcetong.common.UserContext;
import com.xiangcetong.common.AppProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 文件上传：登录守卫、空文件/超大小/扩展名校验、合法文件返回 URL。
 */
class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    private FileStorageService service;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        props.getUpload().setDir(tempDir.toString());
        props.getUpload().setUrlPrefix("/uploads");
        service = new FileStorageServiceImpl(props);

        UserContext.LoginUser u = new UserContext.LoginUser();
        u.setUserId(1L);
        u.setRole("VILLAGER");
        UserContext.set(u);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private static MultipartFile file(String name, long size, boolean empty) {
        MultipartFile f = mock(MultipartFile.class);
        when(f.getOriginalFilename()).thenReturn(name);
        when(f.getSize()).thenReturn(size);
        when(f.isEmpty()).thenReturn(empty);
        try {
            when(f.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));
        } catch (Exception ignored) {
        }
        return f;
    }

    @Test
    void uploadWithoutLoginThrows401() {
        UserContext.clear();
        BizException ex = assertThrows(BizException.class,
                () -> service.upload(file("a.jpg", 10, false)));
        assertEquals(ErrorCodes.UNAUTHORIZED, ex.getCode());
    }

    @Test
    void uploadEmptyFileFails() {
        BizException ex = assertThrows(BizException.class,
                () -> service.upload(file("a.jpg", 0, true)));
        assertEquals(ErrorCodes.BAD_PARAM, ex.getCode());
        assertEquals("未传文件", ex.getMessage());
    }

    @Test
    void uploadOversizedFails() {
        BizException ex = assertThrows(BizException.class,
                () -> service.upload(file("a.jpg", 5L * 1024 * 1024 + 1, false)));
        assertEquals(ErrorCodes.BAD_PARAM, ex.getCode());
        assertEquals(413, ex.getHttpStatus());
    }

    @Test
    void uploadDisallowedExtFails() {
        BizException ex = assertThrows(BizException.class,
                () -> service.upload(file("a.gif", 10, false)));
        assertEquals(ErrorCodes.BAD_PARAM, ex.getCode());
        assertEquals("仅支持 JPG/PNG 图片", ex.getMessage());
    }

    @Test
    void uploadNoExtensionFails() {
        BizException ex = assertThrows(BizException.class,
                () -> service.upload(file("noext", 10, false)));
        assertEquals(ErrorCodes.BAD_PARAM, ex.getCode());
    }

    @Test
    void uploadJpgSucceeds() {
        Map<String, Object> data = service.upload(file("photo.jpg", 100, false));
        String url = (String) data.get("url");
        assertTrue(url.startsWith("/uploads/"));
        assertTrue(url.endsWith(".jpg"));
        assertEquals("photo.jpg", data.get("originalName"));
    }

    @Test
    void uploadUppercaseExtSucceeds() {
        Map<String, Object> data = service.upload(file("photo.PNG", 100, false));
        assertTrue(((String) data.get("url")).endsWith(".png"));
    }
}
