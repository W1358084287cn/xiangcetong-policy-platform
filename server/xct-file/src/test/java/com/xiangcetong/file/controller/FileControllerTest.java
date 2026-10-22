package com.xiangcetong.file.controller;

import com.xiangcetong.file.service.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FileController 单测（MockMvc standalone + mock Service，不落盘）。
 * 覆盖：multipart 文件参数名绑定与上传结果透传。
 */
class FileControllerTest {

    private FileStorageService fileStorageService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        fileStorageService = mock(FileStorageService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new FileController(fileStorageService)).build();
    }

    @Test
    void uploadBindsMultipartFileAndReturnsUrl() throws Exception {
        byte[] content = new byte[]{1, 2, 3, 4, 5};
        when(fileStorageService.upload(any())).thenReturn(Map.of(
                "url", "/uploads/2026/10/abc.jpg",
                "fileName", "abc.jpg"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "照片.jpg", "image/jpeg", content);

        mockMvc.perform(multipart("/api/file/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.url").value("/uploads/2026/10/abc.jpg"))
                .andExpect(jsonPath("$.data.fileName").value("abc.jpg"));

        // 校验 multipart 字节流完整传给 Service
        ArgumentCaptor<org.springframework.web.multipart.MultipartFile> captor =
                ArgumentCaptor.forClass(org.springframework.web.multipart.MultipartFile.class);
        verify(fileStorageService).upload(captor.capture());
        assertArrayEquals(content, captor.getValue().getBytes());
        assertEquals("照片.jpg", captor.getValue().getOriginalFilename());
    }
}
