package com.project.sangngo552004.mailservice.provider.impl;

import com.project.sangngo552004.mailservice.provider.EmailProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class MockEmailProvider implements EmailProvider {

    @Override
    public void sendEmail(String to, String subject, String body) throws Exception {

        Thread.sleep(1000);
        System.out.println("[MOCK] Sending email to: " + to + " via MockProvider");
        if (Math.random() < 0.1) {
            throw new RuntimeException("Simulated Network Error");
        }
    }

    @Override
    public String getProviderName() {
        return "MOCK";
    }

}
