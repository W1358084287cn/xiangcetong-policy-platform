package com.xiangcetong.file.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 本地文件上传：JPG/PNG，单张 ≤5MB，落到 uploads/年/月/。
 */
public interface FileStorageService {

    /** 上传图片并返回可访问 URL */
    Map<String, Object> upload(MultipartFile file);
}
