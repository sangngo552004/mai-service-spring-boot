package com.project.sangngo552004.mailservice;

import com.project.sangngo552004.mailservice.entity.EmailJob;
import com.project.sangngo552004.mailservice.entity.OutboxEvent;
import com.project.sangngo552004.mailservice.provider.EmailProvider;
import com.project.sangngo552004.mailservice.repository.EmailJobRepository;
import com.project.sangngo552004.mailservice.repository.OutboxEventRepository;
import com.project.sangngo552004.mailservice.repository.UploadJobRepository;
import com.project.sangngo552004.mailservice.service.UploadService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.main.allow-bean-definition-overriding=true",
        "worker.upload-job.delay-ms=100",
        "worker.outbox.delay-ms=100",
        "worker.csv.batch-size=25",
        "worker.outbox.batch-size=25",
        "worker.email-sender.threads=8",
        "email.retry.max=3",
        "spring.jpa.show-sql=false"
})
@EnabledIfSystemProperty(named = "run.local.infra.tests", matches = "true")
class LocalInfraPerformanceTest {

    @Autowired
    private UploadService uploadService;

    @Autowired
    private EmailJobRepository emailJobRepository;

    @Autowired
    private UploadJobRepository uploadJobRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private DeterministicEmailProvider emailProvider;

    @Value("${worker.email-sender.queue:email_queue}")
    private String queueName;

    @Value("${email.retry.max:3}")
    private int maxRetry;

    @AfterEach
    void tearDown() {
        cleanupState();
        emailProvider.reset();
    }

    @Test
    void shouldMeasurePipelineWithLocalMysqlAndRedis() throws Exception {
        BenchmarkInput input = new BenchmarkInput(240, 18);

        cleanupState();
        emailProvider.reset();
        BenchmarkResult sequential = runSequentialBaseline(input);

        cleanupState();
        emailProvider.reset();
        BenchmarkResult async = runAsyncPipeline(input);

        printResult("LOCAL_SEQUENTIAL", sequential);
        printResult("LOCAL_ASYNC_PIPELINE", async);

        double speedup = (double) sequential.durationMs / async.durationMs;
        System.out.printf("local_speedup=%.2fx%n", speedup);

        assertEquals(sequential.successCount, async.successCount,
                "Retry policy should produce the same final success count");
        assertEquals(sequential.failureCount, async.failureCount,
                "Retry policy should produce the same final failure count");
        assertTrue(async.durationMs < sequential.durationMs,
                "Async pipeline should beat sequential processing on local infra");
    }

    private BenchmarkResult runSequentialBaseline(BenchmarkInput input) throws Exception {
        List<String> emails = buildEmails(input.emailCount);
        emailProvider.setSendDelayMs(input.sendDelayMs);
        long start = System.nanoTime();
        int success = 0;
        int failed = 0;
        int retried = 0;

        for (String email : emails) {
            int attempts = 0;
            boolean delivered = false;

            while (attempts < maxRetry && !delivered) {
                attempts++;
                try {
                    emailProvider.sendEmail(email, "Benchmark", "Sequential baseline");
                    delivered = true;
                } catch (Exception ignored) {
                    if (attempts >= maxRetry) {
                        break;
                    }
                }
            }

            if (attempts > 1) {
                retried++;
            }

            if (delivered) {
                success++;
            } else {
                failed++;
            }
        }

        return new BenchmarkResult(
                Duration.ofNanos(System.nanoTime() - start).toMillis(),
                input.emailCount,
                success,
                failed,
                retried,
                0,
                0
        );
    }

    private BenchmarkResult runAsyncPipeline(BenchmarkInput input) throws Exception {
        List<String> emails = buildEmails(input.emailCount);
        emailProvider.setSendDelayMs(input.sendDelayMs);
        MockMultipartFile file = createCsv(emails);

        long start = System.nanoTime();
        String uploadJobId = uploadService.createUploadJob(file);
        assertNotNull(uploadJobId);

        long timeoutAt = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
        while (System.nanoTime() < timeoutAt) {
            long total = emailJobRepository.countByUploadJobId(uploadJobId);
            long pending = emailJobRepository.countByUploadJobIdAndStatus(uploadJobId, EmailJob.Status.PENDING);
            boolean uploadCompleted = uploadJobRepository.findById(uploadJobId)
                    .map(job -> job.getStatus() != null && job.getStatus().name().matches("COMPLETED|FAILED"))
                    .orElse(false);

            if (uploadCompleted && total == input.emailCount && pending == 0) {
                break;
            }

            Thread.sleep(200);
        }

        List<EmailJob> jobs = emailJobRepository.findAllByUploadJobId(uploadJobId);
        long durationMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
        long success = jobs.stream().filter(job -> job.getStatus() == EmailJob.Status.SUCCESS).count();
        long failed = jobs.stream().filter(job -> job.getStatus() == EmailJob.Status.FAILED).count();
        long retried = jobs.stream().filter(job -> job.getRetryCount() != null && job.getRetryCount() > 1).count();
        long outboxLeft = outboxEventRepository.countByStatus(OutboxEvent.Status.NEW);

        assertEquals(input.emailCount, jobs.size(), "All email jobs should be created");
        assertEquals(0L, jobs.stream().filter(job -> job.getStatus() == EmailJob.Status.PENDING).count(),
                "All email jobs should finish processing");

        return new BenchmarkResult(
                durationMs,
                input.emailCount,
                (int) success,
                (int) failed,
                (int) retried,
                emailProvider.totalAttempts(),
                outboxLeft
        );
    }

    private MockMultipartFile createCsv(List<String> emails) {
        StringBuilder content = new StringBuilder("email\n");
        for (String email : emails) {
            content.append(email).append('\n');
        }

        byte[] bytes = content.toString().getBytes(StandardCharsets.UTF_8);
        return new MockMultipartFile(
                "file",
                "benchmark.csv",
                "text/csv",
                bytes
        );
    }

    private List<String> buildEmails(int emailCount) {
        List<String> emails = new ArrayList<>(emailCount);
        for (int i = 1; i <= emailCount; i++) {
            if (i % 20 == 0) {
                emails.add("fail-" + i + "@example.com");
            } else if (i % 10 == 0) {
                emails.add("retry-" + i + "@example.com");
            } else {
                emails.add("user-" + i + "@example.com");
            }
        }
        return emails;
    }

    private void cleanupState() {
        redisTemplate.delete(queueName);
        outboxEventRepository.deleteAllInBatch();
        emailJobRepository.deleteAllInBatch();
        uploadJobRepository.deleteAllInBatch();
    }

    private void printResult(String label, BenchmarkResult result) {
        double throughput = result.durationMs == 0 ? 0 : (result.totalCount * 1000.0) / result.durationMs;
        double successRate = result.totalCount == 0 ? 0 : (result.successCount * 100.0) / result.totalCount;
        double failureRate = result.totalCount == 0 ? 0 : (result.failureCount * 100.0) / result.totalCount;
        double retryRate = result.totalCount == 0 ? 0 : (result.retriedCount * 100.0) / result.totalCount;

        System.out.printf(
                "%s durationMs=%d total=%d success=%d failed=%d retried=%d successRate=%.2f%% failureRate=%.2f%% retryRate=%.2f%% throughput=%.2f emails/s attempts=%d pendingOutbox=%d%n",
                label,
                result.durationMs,
                result.totalCount,
                result.successCount,
                result.failureCount,
                result.retriedCount,
                successRate,
                failureRate,
                retryRate,
                throughput,
                result.totalAttempts,
                result.pendingOutbox
        );
    }

    private record BenchmarkInput(int emailCount, long sendDelayMs) {
    }

    private record BenchmarkResult(long durationMs,
                                   int totalCount,
                                   int successCount,
                                   int failureCount,
                                   int retriedCount,
                                   int totalAttempts,
                                   long pendingOutbox) {
    }

    @TestConfiguration
    static class LocalInfraTestConfig {

        @Bean
        @Primary
        public DeterministicEmailProvider mockEmailProvider() {
            return new DeterministicEmailProvider();
        }

        @Bean
        DeterministicEmailProvider deterministicEmailProvider() {
            return mockEmailProvider();
        }
    }

    static class DeterministicEmailProvider implements EmailProvider {

        private final Map<String, AtomicInteger> attempts = new ConcurrentHashMap<>();
        private volatile long sendDelayMs = 18L;

        @Override
        public void sendEmail(String to, String subject, String body) throws Exception {
            Thread.sleep(sendDelayMs);
            int attempt = attempts.computeIfAbsent(to, ignored -> new AtomicInteger()).incrementAndGet();

            if (to.startsWith("fail-")) {
                throw new IllegalStateException("Permanent failure for " + to);
            }

            if (to.startsWith("retry-") && attempt == 1) {
                throw new IllegalStateException("Transient failure for " + to);
            }
        }

        @Override
        public String getProviderName() {
            return "DETERMINISTIC_TEST_PROVIDER";
        }

        int totalAttempts() {
            return attempts.values().stream().mapToInt(AtomicInteger::get).sum();
        }

        void reset() {
            attempts.clear();
        }

        void setSendDelayMs(long sendDelayMs) {
            this.sendDelayMs = sendDelayMs;
        }
    }
}
