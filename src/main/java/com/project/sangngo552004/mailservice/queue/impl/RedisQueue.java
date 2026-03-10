package com.project.sangngo552004.mailservice.queue.impl;

import com.project.sangngo552004.mailservice.queue.MessageHandler;
import com.project.sangngo552004.mailservice.queue.Queue;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RedisQueue implements Queue {

    private final StringRedisTemplate redisTemplate;

    @Override
    public void publish(String queue, String payload) {
        redisTemplate.opsForList().rightPush(queue, payload);
    }

    @Override
    public void subscribe(String queue, MessageHandler handler) {

        String message = redisTemplate.opsForList()
                .leftPop(queue, Duration.ofSeconds(5));

        if (message != null) {
            handler.handle(message);
        }
    }
}