package ru.hvostid.auth.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import ru.hvostid.auth.config.AccountMailProperties;
import ru.hvostid.auth.exception.MailUnavailableException;

@Component
public class AccountMailSender {
    private final AccountMailProperties properties;
    private final ObjectProvider<JavaMailSender> mailSender;

    public AccountMailSender(AccountMailProperties properties, ObjectProvider<JavaMailSender> mailSender) {
        this.properties = properties;
        this.mailSender = mailSender;
    }

    public void requireAvailable() {
        if (!properties.enabled() || mailSender.getIfAvailable() == null) throw new MailUnavailableException();
    }

    public void send(String email, String path, String token, String subject) {
        requireAvailable();
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.from());
        message.setTo(email);
        message.setSubject(subject);
        message.setText(
                subject + "\n\n" + properties.publicBaseUrl() + path + "?token=" + token
                        + "\n\nThis link expires in 30 minutes and can be used once. Ignore this email if you did not request it.");
        try {
            mailSender.getObject().send(message);
        } catch (MailException ex) {
            throw new MailUnavailableException();
        }
    }
}
