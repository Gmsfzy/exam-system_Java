package com.exam.backend.service;

import com.exam.backend.common.exception.BusinessException;
import com.exam.backend.common.exception.ErrorCode;
import com.exam.backend.dto.UploadDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class UploadService {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    private static final Set<String> ALLOWED_EXT = Set.of(
            ".png", ".jpg", ".jpeg", ".gif", ".webp",
            ".mp4", ".webm", ".pdf", ".xlsx", ".xls", ".csv", ".docx");

    public UploadDto.UploadResponse upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "文件为空");
        }
        String original = file.getOriginalFilename();
        if (original == null) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "文件名缺失");
        String ext = "";
        int dot = original.lastIndexOf('.');
        if (dot >= 0) ext = original.substring(dot).toLowerCase();
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的文件类型: " + ext);
        }
        try {
            Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
            if (!dir.startsWith(Paths.get(uploadDir).toAbsolutePath())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "上传路径不合法");
            }
            Files.createDirectories(dir);
            String saved = UUID.randomUUID() + ext;
            Path target = dir.resolve(saved).normalize();
            if (!target.startsWith(dir)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "文件路径越界");
            }
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            String url = "/uploads/" + saved;
            return new UploadDto.UploadResponse(url, original, file.getSize());
        } catch (IOException e) {
            log.error("Upload failed", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件保存失败");
        }
    }
}