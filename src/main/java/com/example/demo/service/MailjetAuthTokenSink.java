package com.example.demo.service;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import com.example.demo.entity.SecurityToken;

public final class MailjetAuthTokenSink implements AuthTokenSink {
    private static final String SEND_ENDPOINT = "https://api.mailjet.com/v3.1/send";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final RestClient restClient;
    private final String senderAddress;
    private final String senderName;
    private final String verificationUrl;
    private final String resetUrl;

    public MailjetAuthTokenSink(String apiKey, String apiSecret, String senderAddress, String senderName,
            String verificationUrl, String resetUrl) {
        this(apiKey, apiSecret, senderAddress, senderName, verificationUrl, resetUrl, createRestClientBuilder());
    }

    MailjetAuthTokenSink(String apiKey, String apiSecret, String senderAddress, String senderName,
            String verificationUrl, String resetUrl, RestClient.Builder builder) {
        this.restClient = Objects.requireNonNull(builder)
                .baseUrl(SEND_ENDPOINT)
                .defaultHeaders(headers -> headers.setBasicAuth(apiKey, apiSecret, StandardCharsets.UTF_8))
                .build();
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
                .queryParam("token", java.net.URLEncoder.encode(rawToken, StandardCharsets.UTF_8)
                        .replace("+", "%20"))
                .build(true)
                .toUriString();
        String text = "Use the following link to " + action + ":\n\n" + link
                + "\n\nIf you did not request this, you can ignore this message.";
        SendRequest request = new SendRequest(List.of(new MailjetMessage(
                new Mailbox(senderAddress, senderName),
                List.of(new Mailbox(email, null)),
                subject,
                text)));

        try {
            MailjetResponse response = restClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(MailjetResponse.class);
            if (response == null || response.messages() == null || response.messages().size() != 1
                    || !"success".equalsIgnoreCase(response.messages().get(0).status())) {
                throw new EmailDeliveryException();
            }
        } catch (RestClientException exception) {
            throw new EmailDeliveryException();
        }
    }

    private static RestClient.Builder createRestClientBuilder() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(REQUEST_TIMEOUT);
        return RestClient.builder().requestFactory(requestFactory);
    }

    private record SendRequest(@JsonProperty("Messages") List<MailjetMessage> messages) {}

    private record MailjetMessage(
            @JsonProperty("From") Mailbox from,
            @JsonProperty("To") List<Mailbox> to,
            @JsonProperty("Subject") String subject,
            @JsonProperty("TextPart") String textPart) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record Mailbox(
            @JsonProperty("Email") String email,
            @JsonProperty("Name") String name) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MailjetResponse(@JsonProperty("Messages") List<MailjetMessageResult> messages) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MailjetMessageResult(@JsonProperty("Status") String status) {}
}
