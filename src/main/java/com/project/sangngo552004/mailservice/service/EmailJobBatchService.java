package com.project.sangngo552004.mailservice.service;

import com.project.sangngo552004.mailservice.entity.EmailJob;
import com.project.sangngo552004.mailservice.entity.OutboxEvent;
import com.project.sangngo552004.mailservice.entity.UploadJob;
import com.project.sangngo552004.mailservice.repository.EmailJobRepository;
import com.project.sangngo552004.mailservice.repository.OutboxEventRepository;
import com.project.sangngo552004.mailservice.repository.UploadJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailJobBatchService {

    private final EmailJobRepository emailJobRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final UploadJobRepository uploadJobRepository;

    @Transactional
    public void saveBatch(String uploadJobId, List<EmailJob> emailJobs, List<OutboxEvent> outboxEvents) {
        emailJobRepository.saveAll(emailJobs);
        outboxEventRepository.saveAll(outboxEvents);

        UploadJob job = uploadJobRepository.findById(uploadJobId)
                .orElseThrow(() -> new IllegalStateException("Upload job not found: " + uploadJobId));

        long processed = job.getProcessedRows() == null ? 0L : job.getProcessedRows();
        job.setProcessedRows(processed + emailJobs.size());
        uploadJobRepository.save(job);

        log.info("Persisted batch: uploadJobId={}, size={}", uploadJobId, emailJobs.size());
    }
}
