package com.project.sangngo552004.mailservice.queue;

public interface Queue {

    void publish(String topic, String payload);

    void subscribe(String topic, MessageHandler handler);
}
