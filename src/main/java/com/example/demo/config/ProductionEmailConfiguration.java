package com.example.demo.config;

import java.net.URI;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.example.demo.service.AuthTokenSink;
import com.example.demo.service.MailjetAuthTokenSink;

@Configuration
@Profile("prod")
public class ProductionEmailConfiguration {

    @Bean
    AuthTokenSink mailjetAuthTokenSink(
            @Value("${app.mailjet.api-key:}") String apiKey,
            @Value("${app.mailjet.api-secret:}") String apiSecret,
            @Value("${app.mail.sender.address:}") String senderAddress,
            @Value("${app.mail.sender.name:}") String senderName,
            @Value("${app.mail.frontend.verification-url:}") String verificationUrl,
            @Value("${app.mail.frontend.reset-url:}") String resetUrl) {
        requireConfigured(apiKey, apiSecret, senderAddress, senderName, verificationUrl, resetUrl);
        return new MailjetAuthTokenSink(apiKey, apiSecret, senderAddress, senderName,
                verificationUrl, resetUrl);
    }

    private void requireConfigured(String apiKey, String apiSecret, String senderAddress,
            String senderName, String verificationUrl, String resetUrl) {
        if (apiKey.isBlank() || apiSecret.isBlank()
                || !isValidSender(senderAddress, senderName)
                || !isSecureFrontendUrl(verificationUrl) || !isSecureFrontendUrl(resetUrl)) {
            throw new IllegalStateException(
                    "Production email requires Mailjet API credentials, sender address/name, "
                            + "and HTTPS verification/reset URLs.");
        }
    }

    private boolean isValidSender(String address, String name) {
        if (address.isBlank() || name.isBlank() || name.contains("\r") || name.contains("\n")) {
            return false;
        }
        try {
            InternetAddress sender = new InternetAddress(address, true);
            sender.validate();
            return address.equals(sender.getAddress());
        } catch (AddressException exception) {
            return false;
        }
    }

    private boolean isSecureFrontendUrl(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && uri.getUserInfo() == null && uri.getFragment() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
