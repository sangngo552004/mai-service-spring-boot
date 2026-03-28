package com.project.sangngo552004.mailservice;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncPipelineBenchmarkTest {

    @Test
    void asyncPipelineShouldBeatSequentialFlow() throws InterruptedException {
        BenchmarkInput input = new BenchmarkInput(240, 8, 15, 50);

        BenchmarkResult sequential = runSequential(input);
        BenchmarkResult async = runAsyncPipeline(input);

        printResult("SEQUENTIAL", sequential);
        printResult("ASYNC_PIPELINE", async);

        double speedup = (double) sequential.durationMs / async.durationMs;
        System.out.printf("speedup=%.2fx%n", speedup);

        assertTrue(async.durationMs < sequential.durationMs,
                "Async pipeline should finish faster than sequential flow");
        assertTrue(speedup > 2.0,
                "Async pipeline should provide meaningful speedup for IO-bound email sending");
        int expectedBatchWrites = (input.emailCount + input.batchSize - 1) / input.batchSize;
        assertTrue(async.batchWrites == expectedBatchWrites,
                "Batch write count should match expected number of CSV batches");
        assertTrue(async.queuePublishes == input.emailCount,
                "Each email job should publish exactly one outbox event in this benchmark");
    }

    private BenchmarkResult runSequential(BenchmarkInput input) throws InterruptedException {
        long start = System.nanoTime();
        int sent = 0;

        for (int i = 0; i < input.emailCount; i++) {
            simulateEmailSend(input.sendDelayMs);
            sent++;
        }

        return new BenchmarkResult(
                Duration.ofNanos(System.nanoTime() - start).toMillis(),
                sent,
                input.emailCount,
                0
        );
    }

    private BenchmarkResult runAsyncPipeline(BenchmarkInput input) throws InterruptedException {
        long start = System.nanoTime();

        BlockingQueue<String> queue = new LinkedBlockingQueue<>();
        AtomicInteger sent = new AtomicInteger();
        AtomicInteger batchWrites = new AtomicInteger();
        AtomicInteger queuePublishes = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(input.emailCount);
        List<Thread> workers = new ArrayList<>();

        for (int i = 0; i < input.workerCount; i++) {
            Thread worker = new Thread(() -> {
                try {
                    while (done.getCount() > 0) {
                        String email = queue.poll(100, TimeUnit.MILLISECONDS);
                        if (email == null) {
                            continue;
                        }
                        simulateEmailSend(input.sendDelayMs);
                        sent.incrementAndGet();
                        done.countDown();
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }, "benchmark-worker-" + i);
            worker.start();
            workers.add(worker);
        }

        List<String> batch = new ArrayList<>(input.batchSize);
        for (int i = 0; i < input.emailCount; i++) {
            batch.add("user" + i + "@example.com");
            if (batch.size() >= input.batchSize) {
                persistAndPublishBatch(batch, queue, batchWrites, queuePublishes);
                batch = new ArrayList<>(input.batchSize);
            }
        }
        if (!batch.isEmpty()) {
            persistAndPublishBatch(batch, queue, batchWrites, queuePublishes);
        }

        boolean completed = done.await(30, TimeUnit.SECONDS);
        for (Thread worker : workers) {
            worker.interrupt();
            worker.join(1000);
        }

        assertTrue(completed, "Async benchmark did not finish in time");

        return new BenchmarkResult(
                Duration.ofNanos(System.nanoTime() - start).toMillis(),
                sent.get(),
                queuePublishes.get(),
                batchWrites.get()
        );
    }

    private void persistAndPublishBatch(List<String> batch,
                                        BlockingQueue<String> queue,
                                        AtomicInteger batchWrites,
                                        AtomicInteger queuePublishes) {
        batchWrites.incrementAndGet();
        for (String email : batch) {
            queue.offer(email);
            queuePublishes.incrementAndGet();
        }
    }

    private void simulateEmailSend(long sendDelayMs) throws InterruptedException {
        Thread.sleep(sendDelayMs);
    }

    private void printResult(String label, BenchmarkResult result) {
        double throughput = result.sentCount == 0 || result.durationMs == 0
                ? 0
                : (result.sentCount * 1000.0) / result.durationMs;

        System.out.printf(
                "%s durationMs=%d sent=%d throughput=%.2f emails/s queuePublishes=%d batchWrites=%d%n",
                label,
                result.durationMs,
                result.sentCount,
                throughput,
                result.queuePublishes,
                result.batchWrites
        );
    }

    private record BenchmarkInput(int emailCount, int workerCount, long sendDelayMs, int batchSize) {
    }

    private record BenchmarkResult(long durationMs, int sentCount, int queuePublishes, int batchWrites) {
    }
}
