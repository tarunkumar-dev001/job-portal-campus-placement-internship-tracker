package com.example.demo.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.example.demo.entity.SecurityToken;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

class MailjetAuthTokenSinkTests {
    private static final String ENDPOINT = "https://api.mailjet.com/v3.1/send";
    private static final String API_KEY = "test-api-key";
    private static final String API_SECRET = "test-api-secret";

    @Test
    void sendsVerificationAndResetMessagesWithBasicAuthenticationAndExistingContent() {
        Fixture fixture = new Fixture();
        String authorization = "Basic " + Base64.getEncoder().encodeToString(
                (API_KEY + ":" + API_SECRET).getBytes(StandardCharsets.UTF_8));

        fixture.server.expect(requestTo(ENDPOINT))
                .andExpect(method(POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.Messages[0].From.Email").value("no-reply@example.test"))
                .andExpect(jsonPath("$.Messages[0].From.Name").value("CareerConnect"))
                .andExpect(jsonPath("$.Messages[0].To[0].Email").value("student@example.test"))
                .andExpect(jsonPath("$.Messages[0].To[0].Name").doesNotExist())
                .andExpect(jsonPath("$.Messages[0].Subject").value("Verify your CareerConnect account"))
                .andExpect(jsonPath("$.Messages[0].TextPart").value(containsString(
                        "https://portal.example.test/verify-email?source=mail&token=raw%2Btoken%2Fvalue")))
                .andRespond(withSuccess("""
                        {"Messages":[{"Status":"success"}]}
                        """, MediaType.APPLICATION_JSON));
        fixture.server.expect(requestTo(ENDPOINT))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.Messages[0].Subject").value("Reset your CareerConnect password"))
                .andExpect(jsonPath("$.Messages[0].TextPart").value(containsString(
                        "https://portal.example.test/reset-password?token=reset-token")))
                .andRespond(withSuccess("""
                        {"Messages":[{"Status":"success"}]}
                        """, MediaType.APPLICATION_JSON));

        fixture.sink.deliver("student@example.test", SecurityToken.Type.EMAIL_VERIFICATION, "raw+token/value");
        fixture.sink.deliver("student@example.test", SecurityToken.Type.PASSWORD_RESET, "reset-token");

        fixture.server.verify();
    }

    @Test
    void rejectsUnsuccessfulHttpResponsesWithoutExposingProviderDetails() {
        Fixture fixture = new Fixture();
        fixture.server.expect(requestTo(ENDPOINT))
                .andRespond(withStatus(BAD_REQUEST).body("sensitive provider response").contentType(
                        MediaType.APPLICATION_JSON));

        EmailDeliveryException failure = assertThrows(EmailDeliveryException.class,
                () -> fixture.sink.deliver("student@example.test", SecurityToken.Type.EMAIL_VERIFICATION,
                        "raw-sensitive-token"));

        assertSanitized(failure, "sensitive provider response", "raw-sensitive-token", API_KEY, API_SECRET);
        fixture.server.verify();
    }

    @Test
    void rejectsUnsuccessfulMailjetMessageStatus() {
        Fixture fixture = new Fixture();
        fixture.server.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess("""
                        {"Messages":[{"Status":"error","Errors":[{"ErrorMessage":"sensitive provider detail"}]}]}
                        """, MediaType.APPLICATION_JSON));

        EmailDeliveryException failure = assertThrows(EmailDeliveryException.class,
                () -> fixture.sink.deliver("student@example.test", SecurityToken.Type.PASSWORD_RESET, "raw-token"));

        assertSanitized(failure, "sensitive provider detail", "raw-token", API_KEY, API_SECRET);
        fixture.server.verify();
    }

    @Test
    void rejectsTransportFailuresWithoutExposingExceptionDetails() {
        Fixture fixture = new Fixture();
        fixture.server.expect(requestTo(ENDPOINT))
                .andRespond(withException(new IOException("transport detail with " + API_SECRET)));

        EmailDeliveryException failure = assertThrows(EmailDeliveryException.class,
                () -> fixture.sink.deliver("student@example.test", SecurityToken.Type.EMAIL_VERIFICATION,
                        "raw-sensitive-token"));

        assertSanitized(failure, "transport detail", "raw-sensitive-token", API_KEY, API_SECRET);
        fixture.server.verify();
    }

    private static void assertSanitized(EmailDeliveryException failure, String... sensitiveValues) {
        assertNull(failure.getCause());
        for (String value : sensitiveValues) {
            assertFalse(failure.getMessage().contains(value));
        }
    }

    private static final class Fixture {
        private final RestClient.Builder builder = RestClient.builder();
        private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        private final MailjetAuthTokenSink sink = new MailjetAuthTokenSink(API_KEY, API_SECRET,
                "no-reply@example.test", "CareerConnect",
                "https://portal.example.test/verify-email?source=mail",
                "https://portal.example.test/reset-password", builder);
    }
}
