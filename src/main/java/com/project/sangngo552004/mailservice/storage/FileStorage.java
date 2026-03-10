package com.project.sangngo552004.mailservice.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface FileStorage {
    String store(MultipartFile file) throws IOException;
}
