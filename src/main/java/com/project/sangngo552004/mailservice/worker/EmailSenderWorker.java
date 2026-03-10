package com.project.sangngo552004.mailservice.worker;

import com.project.sangngo552004.mailservice.entity.EmailJob;
import com.project.sangngo552004.mailservice.provider.EmailProvider;
import com.project.sangngo552004.mailservice.queue.impl.RedisQueue;
import com.project.sangngo552004.mailservice.repository.EmailJobRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class EmailSenderWorker {

    private final RedisQueue redisQueue;
    private final EmailJobRepository emailJobRepository;
    private final EmailProvider emailProvider;
    private final TaskExecutor emailSenderExecutor;

    @Value("${worker.email-sender.threads:10}")
    private int threads;

    @Value("${worker.email-sender.queue:email_queue}")
    private String queueName;

    @Value("${email.default-subject:Hello}")
    private String defaultSubject;

    @Value("${email.default-body:}")
    private String defaultBody;

    @Value("${email.retry.max:3}")
    private int maxRetry;

    public EmailSenderWorker(RedisQueue redisQueue,
                             EmailJobRepository emailJobRepository,
                             EmailProvider emailProvider,
                             @Qualifier("emailSenderExecutor") TaskExecutor emailSenderExecutor) {
        this.redisQueue = redisQueue;
        this.emailJobRepository = emailJobRepository;
        this.emailProvider = emailProvider;
        this.emailSenderExecutor = emailSenderExecutor;
    }

    @PostConstruct
    public void start() {
        for (int i = 0; i < threads; i++) {
            emailSenderExecutor.execute(this::runLoop);
        }
        log.info("Started {} email sender workers", threads);
    }

    private void runLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                redisQueue.subscribe(queueName, this::process);

            } catch (Exception e) {
                log.error("Email sender loop error", e);
            }
        }
    }

    private void process(String jobId) {
        emailJobRepository.findById(jobId).ifPresent(job -> {
            if (job.getStatus() != EmailJob.Status.PENDING) {
                log.debug("Skipping job {} with status {}", jobId, job.getStatus());
                return;
            }

            try {
                emailProvider.sendEmail(job.getEmail(), defaultSubject, defaultBody);
                job.setStatus(EmailJob.Status.SUCCESS);
                job.setErrorMessage(null);
                log.info("Sent email for job {}", jobId);
            } catch (Exception e) {
                int retry = job.getRetryCount() == null ? 0 : job.getRetryCount();
                retry++;
                job.setRetryCount(retry);

                if (retry >= maxRetry) {
                    job.setStatus(EmailJob.Status.FAILED);
                    job.setErrorMessage(e.getMessage());
                    log.error("Email job {} failed after {} retries", jobId, retry);
                } else {
                    job.setStatus(EmailJob.Status.PENDING);
                    log.warn("Email job {} retry attempt {}", jobId, retry);
                    redisQueue.publish(queueName, jobId);
                }
            } finally {
                emailJobRepository.save(job);
            }
        });
    }
}
