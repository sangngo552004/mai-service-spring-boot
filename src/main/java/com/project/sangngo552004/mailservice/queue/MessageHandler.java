package com.project.sangngo552004.mailservice.queue;

public interface MessageHandler {

    void handle(String payload);
}
