package com.project.sangngo552004.mailservice.service;

import com.project.sangngo552004.mailservice.entity.UploadJob;
import com.project.sangngo552004.mailservice.repository.UploadJobRepository;
import com.project.sangngo552004.mailservice.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UploadService {

    private final UploadJobRepository uploadJobRepository;
    private final FileStorage fileStorageService;

    public String createUploadJob(MultipartFile file) {
        String filePath;
        try {
            filePath = fileStorageService.store(file);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store upload file", e);
        }

        UploadJob job = UploadJob.builder()
                .id(UUID.randomUUID().toString())
                .filePath(filePath)
                .status(UploadJob.Status.PENDING)
                .totalRows(0L)
                .processedRows(0L)
                .build();

        uploadJobRepository.save(job);
        log.info("Created upload job {}", job.getId());
        return job.getId();
    }
}
