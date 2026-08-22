package br.com.calendar.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

// Generic email-sending service used by every flow that needs to email a
// user (signup confirmation, forgot-password, ...) instead of each flow
// talking to JavaMailSender directly.
@Slf4j
@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public EmailService(JavaMailSender mailSender, @Value("${spring.mail.username}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    public void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);

        // DEBUG only: lets a developer without real SMTP credentials
        // configured still see the content (e.g. a confirmation link) locally.
        log.debug("Sending email to {} - Subject: {} - Body: {}", to, subject, body);

        try {
            mailSender.send(message);
        } catch (MailException e) {
            // A mail-server hiccup shouldn't fail the caller's flow (signup,
            // forgot-password, ...) — log it and move on.
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }
}
