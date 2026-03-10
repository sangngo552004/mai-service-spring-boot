package com.project.sangngo552004.mailservice.worker;

import com.project.sangngo552004.mailservice.entity.UploadJob;
import com.project.sangngo552004.mailservice.repository.UploadJobRepository;
import com.project.sangngo552004.mailservice.service.CsvParserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class UploadJobWorker {

    private final UploadJobRepository uploadJobRepository;
    private final CsvParserService csvParserService;

    @Scheduled(fixedDelayString = "${worker.upload-job.delay-ms:2000}")
    public void pollPending() {
        List<UploadJob> jobs = uploadJobRepository.findByStatusOrderByCreatedAt(
                UploadJob.Status.PENDING,
                PageRequest.of(0, 1)
        );

        for (UploadJob job : jobs) {
            process(job);
        }
    }

    private void process(UploadJob job) {
        log.info("Starting upload job {}", job.getId());
        job.setStatus(UploadJob.Status.PROCESSING);
        uploadJobRepository.save(job);

        try {
            csvParserService.parseUpload(job);
            job.setStatus(UploadJob.Status.COMPLETED);
            job.setErrorMessage(null);
            log.info("Completed upload job {}", job.getId());
        } catch (Exception e) {
            job.setStatus(UploadJob.Status.FAILED);
            job.setErrorMessage(e.getMessage());
            log.error("Upload job {} failed", job.getId(), e);
        }

        uploadJobRepository.save(job);
    }
}
