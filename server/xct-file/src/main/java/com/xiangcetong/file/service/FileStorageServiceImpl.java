package com.xiangcetong.file.service;

import com.xiangcetong.common.BizException;
import com.xiangcetong.common.ErrorCodes;
import com.xiangcetong.common.UserContext;
import com.xiangcetong.common.AppProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 本地文件上传实现：JPG/PNG，单张 ≤5MB，落到 uploads/年/月/。
 */
@Service
public class FileStorageServiceImpl implements FileStorageService {

    private static final Set<String> ALLOWED = Set.of("jpg", "jpeg", "png");
    private static final long MAX_SIZE = 5L * 1024 * 1024;

    private final AppProperties appProperties;

    public FileStorageServiceImpl(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    public Map<String, Object> upload(MultipartFile file) {
        // 登录即可上传（村民与管理员）
        UserContext.require();
        if (file == null || file.isEmpty()) {
            throw BizException.badParam("未传文件");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BizException(ErrorCodes.BAD_PARAM, "上传文件超过 5MB 限制", 413);
        }
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String ext = extension(original);
        if (!ALLOWED.contains(ext)) {
            throw BizException.badParam("仅支持 JPG/PNG 图片");
        }

        LocalDate now = LocalDate.now();
        String relative = String.format("%04d/%02d/%s.%s",
                now.getYear(), now.getMonthValue(),
                UUID.randomUUID().toString().replace("-", ""), ext);
        Path root = Paths.get(appProperties.getUpload().getDir()).toAbsolutePath().normalize();
        Path target = root.resolve(relative).normalize();
        // 防路径穿越
        if (!target.startsWith(root)) {
            throw BizException.badParam("非法文件路径");
        }
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException e) {
            throw new BizException(ErrorCodes.SERVER_ERROR, "文件保存失败", 500);
        }

        String url = appProperties.getUpload().getUrlPrefix() + "/" + relative.replace('\\', '/');
        Map<String, Object> data = new HashMap<>();
        data.put("url", url);
        data.put("originalName", original);
        data.put("size", file.getSize());
        return data;
    }

    private String extension(String name) {
        int i = name.lastIndexOf('.');
        if (i < 0) {
            return "";
        }
        return name.substring(i + 1).toLowerCase(Locale.ROOT);
    }
}
