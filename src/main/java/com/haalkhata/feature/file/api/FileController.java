package com.haalkhata.feature.file.api;

import com.haalkhata.feature.file.application.FileStorageService;
import com.haalkhata.feature.file.application.FileStorageService.UploadResult;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;

    @PostMapping
    public Map<String, String> upload(@RequestParam("file") MultipartFile file) {
        UploadResult result = fileStorageService.store(file);
        return Map.of("url", result.url(), "fileName", result.fileName());
    }
}
