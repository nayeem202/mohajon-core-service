package com.haalkhata.feature.file.application;

import com.haalkhata.core.config.AppProperties;
import com.haalkhata.core.exception.BusinessException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private static final long MAX_UPLOAD_BYTES = 8L * 1024 * 1024;

    private final AppProperties props;

    public record UploadResult(String url, String fileName) {
    }

    public UploadResult store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("No file provided");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw BusinessException.badRequest("File size must not exceed 8 MB");
        }
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String ext = "";
        int dot = original.lastIndexOf('.');
        if (dot >= 0) {
            ext = original.substring(dot).toLowerCase();
        }
        if (!List.of(".jpg", ".jpeg", ".png", ".webp", ".gif", ".pdf").contains(ext)) {
            throw BusinessException.badRequest("Unsupported file type: " + ext);
        }
        String fileName = UUID.randomUUID() + ext;
        Path dir = Path.of(props.getStorageDir());
        try {
            Files.createDirectories(dir);
            Files.copy(file.getInputStream(), dir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            log.error("FILE_UPLOAD_FAILED original={}", original, ex);
            throw new BusinessException("STORAGE_ERROR", "Could not store the file");
        }
        String url = "/api/v1/files/" + fileName;
        log.info("FILE_UPLOADED name={} size={} url={}", original, file.getSize(), url);
        return new UploadResult(url, fileName);
    }
}
