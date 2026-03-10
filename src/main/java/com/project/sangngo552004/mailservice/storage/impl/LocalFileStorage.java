package com.project.sangngo552004.mailservice.storage.impl;

import com.project.sangngo552004.mailservice.storage.FileStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
@Slf4j
public class LocalFileStorage implements FileStorage {

    @Value("${storage.upload-dir:uploads}")
    private String uploadDir;

    @Override
    public String store(MultipartFile file) throws IOException {
        String originalName = file.getOriginalFilename();
        String cleaned = StringUtils.hasText(originalName) ? StringUtils.cleanPath(originalName) : "upload.csv";
        String filename = UUID.randomUUID() + "-" + cleaned;

        Path dir = Paths.get(uploadDir);
        Files.createDirectories(dir);
        Path target = dir.resolve(filename).toAbsolutePath();

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        }

        log.info("Stored upload file at {}", target);
        return target.toString();
    }
}
