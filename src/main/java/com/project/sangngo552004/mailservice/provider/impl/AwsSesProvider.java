package com.project.sangngo552004.mailservice.provider.impl;

import com.project.sangngo552004.mailservice.provider.EmailProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;

@Component
public class AwsSesProvider implements EmailProvider {

    private final SesClient sesClient;

    @Value("${aws.ses.source:your-verified-email@domain.com}")
    private String sourceEmail;

    public AwsSesProvider(SesClient sesClient) {
        this.sesClient = sesClient;
    }

    @Override
    public void sendEmail(String to, String subject, String body) throws Exception {
        SendEmailRequest request = SendEmailRequest.builder()
                .destination(d -> d.toAddresses(to))
                .message(m -> m.subject(s -> s.data(subject))
                        .body(b -> b.text(t -> t.data(body))))
                .source(sourceEmail)
                .build();

        sesClient.sendEmail(request);
    }

    @Override
    public String getProviderName() {
        return "AWS_SES";
    }
}
