package com.project.sangngo552004.mailservice.controller;

import com.project.sangngo552004.mailservice.service.UploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/mail")
@RequiredArgsConstructor
public class EmailController {

    private final UploadService uploadService;

    @PostMapping("/upload")
    public ResponseEntity<String> uploadEmailList(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("File is empty");
        }

        String uploadJobId = uploadService.createUploadJob(file);
        return ResponseEntity.ok(uploadJobId);
    }
}
