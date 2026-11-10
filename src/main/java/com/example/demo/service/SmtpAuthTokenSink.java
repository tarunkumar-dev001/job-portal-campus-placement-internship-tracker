package com.example.demo.service;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Properties;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.web.util.UriComponentsBuilder;

import com.example.demo.entity.SecurityToken;

public final class SmtpAuthTokenSink implements AuthTokenSink {
    private final JavaMailSender mailSender;
    private final String senderAddress;
    private final String senderName;
    private final String verificationUrl;
    private final String resetUrl;

    public SmtpAuthTokenSink(String host, int port, String username, String password,
            String senderAddress, String senderName, String verificationUrl, String resetUrl) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setUsername(username);
        sender.setPassword(password);
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        Properties mailProperties = new Properties();
        mailProperties.put("mail.smtp.auth", "true");
        mailProperties.put("mail.smtp.starttls.enable", "true");
        mailProperties.put("mail.smtp.connectiontimeout", "5000");
        mailProperties.put("mail.smtp.timeout", "5000");
        mailProperties.put("mail.smtp.writetimeout", "5000");
        sender.setJavaMailProperties(mailProperties);
        this.mailSender = sender;
        this.senderAddress = senderAddress;
        this.senderName = senderName;
        this.verificationUrl = verificationUrl;
        this.resetUrl = resetUrl;
    }

    public SmtpAuthTokenSink(JavaMailSender mailSender, String senderAddress, String senderName,
            String verificationUrl, String resetUrl) {
        this.mailSender = Objects.requireNonNull(mailSender);
        this.senderAddress = senderAddress;
        this.senderName = senderName;
        this.verificationUrl = verificationUrl;
        this.resetUrl = resetUrl;
    }

    @Override
    public void deliver(String email, SecurityToken.Type type, String rawToken) {
        String url;
        String subject;
        String action;
        switch (type) {
            case EMAIL_VERIFICATION -> {
                url = verificationUrl;
                subject = "Verify your CareerConnect account";
                action = "verify your email address";
            }
            case PASSWORD_RESET -> {
                url = resetUrl;
                subject = "Reset your CareerConnect password";
                action = "reset your password";
            }
            default -> throw new IllegalArgumentException("Unsupported email token type");
        }

        String link = UriComponentsBuilder.fromUriString(url)
                .queryParam("token", URLEncoder.encode(rawToken, StandardCharsets.UTF_8).replace("+", "%20"))
                .build(true)
                .toUriString();
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(senderAddress, senderName, StandardCharsets.UTF_8.name()));
            helper.setTo(email);
            helper.setSubject(subject);
            helper.setText("Use the following link to " + action + ":\n\n" + link
                    + "\n\nIf you did not request this, you can ignore this message.");
            mailSender.send(message);
        } catch (MessagingException | UnsupportedEncodingException | MailException exception) {
            throw new EmailDeliveryException();
        }
    }
}
