package com.project.sangngo552004.mailservice.service;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;
import com.project.sangngo552004.mailservice.entity.EmailJob;
import com.project.sangngo552004.mailservice.entity.OutboxEvent;
import com.project.sangngo552004.mailservice.entity.UploadJob;
import com.project.sangngo552004.mailservice.repository.UploadJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CsvParserService {

    private final UploadJobRepository uploadJobRepository;
    private final EmailJobBatchService emailJobBatchService;

    @Value("${worker.csv.batch-size:500}")
    private int batchSize;

    public void parseUpload(UploadJob uploadJob) throws IOException, CsvValidationException {
        Path filePath = Paths.get(uploadJob.getFilePath());
        long totalRows = countRows(filePath);

        uploadJob.setTotalRows(totalRows);
        uploadJobRepository.save(uploadJob);

        List<EmailJob> emailJobs = new ArrayList<>(batchSize);
        List<OutboxEvent> outboxEvents = new ArrayList<>(batchSize);

        try (BufferedReader reader = Files.newBufferedReader(filePath);
             CSVReader csvReader = new CSVReaderBuilder(reader).withSkipLines(1).build()) {

            String[] row;
            while ((row = csvReader.readNext()) != null) {
                if (row.length == 0) {
                    continue;
                }

                String email = row[0] == null ? "" : row[0].trim();
                if (email.isEmpty()) {
                    continue;
                }

                EmailJob emailJob = EmailJob.builder()
                        .id(UUID.randomUUID().toString())
                        .uploadJobId(uploadJob.getId())
                        .email(email)
                        .status(EmailJob.Status.PENDING)
                        .retryCount(0)
                        .createdAt(LocalDateTime.now())
                        .build();

                OutboxEvent outboxEvent = OutboxEvent.builder()
                        .id(UUID.randomUUID().toString())
                        .eventType(OutboxEvent.EventType.EMAIL_JOB_CREATED)
                        .payload(emailJob.getId())
                        .status(OutboxEvent.Status.NEW)
                        .createdAt(LocalDateTime.now())
                        .build();

                emailJobs.add(emailJob);
                outboxEvents.add(outboxEvent);

                if (emailJobs.size() >= batchSize) {
                    emailJobBatchService.saveBatch(uploadJob.getId(), emailJobs, outboxEvents);
                    emailJobs.clear();
                    outboxEvents.clear();
                }
            }

            if (!emailJobs.isEmpty()) {
                emailJobBatchService.saveBatch(uploadJob.getId(), emailJobs, outboxEvents);
                emailJobs.clear();
                outboxEvents.clear();
            }
        }

        log.info("Completed CSV parse for uploadJobId={}, totalRows={}", uploadJob.getId(), totalRows);
    }

    private long countRows(Path filePath) throws IOException {
        long count = 0;
        try (BufferedReader reader = Files.newBufferedReader(filePath)) {
            String line;
            boolean isHeader = true;
            while ((line = reader.readLine()) != null) {
                if (isHeader) {
                    isHeader = false;
                    continue;
                }
                if (!line.trim().isEmpty()) {
                    count++;
                }
            }
        }
        return count;
    }
}
