package com.project.sangngo552004.mailservice.worker;

import com.project.sangngo552004.mailservice.entity.OutboxEvent;
import com.project.sangngo552004.mailservice.queue.impl.RedisQueue;
import com.project.sangngo552004.mailservice.repository.OutboxEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisherWorker {

    private final OutboxEventRepository outboxEventRepository;
    private final RedisQueue redisQueue;

    @Value("${worker.outbox.batch-size:500}")
    private int batchSize;

    @Value("${worker.email-sender.queue:email_queue}")
    private String queueName;

    @Scheduled(fixedDelayString = "${worker.outbox.delay-ms:1000}")
    public void publish() {
        List<OutboxEvent> events = outboxEventRepository.findByStatusOrderByCreatedAt(
                OutboxEvent.Status.NEW,
                PageRequest.of(0, batchSize)
        );

        if (events.isEmpty()) {
            return;
        }

        for (OutboxEvent event : events) {
            redisQueue.publish(queueName, event.getPayload());
            event.setStatus(OutboxEvent.Status.SENT);
        }

        outboxEventRepository.saveAll(events);
        log.info("Published {} outbox events", events.size());
    }
}
