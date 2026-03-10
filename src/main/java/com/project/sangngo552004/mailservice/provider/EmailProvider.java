package com.project.sangngo552004.mailservice.provider;

public interface EmailProvider {
    void sendEmail(String to, String subject, String body) throws Exception;
    String getProviderName();
}
