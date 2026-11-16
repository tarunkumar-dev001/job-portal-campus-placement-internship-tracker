package com.example.demo;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.dto.AuthFlowDtos.EmailRequest;
import com.example.demo.dto.LoginRequest;
import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.AuthTokenSink;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "app.security.rate-limit.login.account.max-attempts=3",
        "app.security.rate-limit.login.account.window-seconds=900",
        "app.security.rate-limit.login.ip.max-attempts=3",
        "app.security.rate-limit.login.ip.window-seconds=60"
})
@AutoConfigureMockMvc
@Transactional
class AuthAbuseProtectionIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @MockitoBean AuthTokenSink tokenSink;

    @Test
    void repeatedFailedLoginsEventuallyReturnTooManyRequests() throws Exception {
        String email = uniqueEmail();
        for (int attempt = 0; attempt < 3; attempt++) {
            mvc.perform(login(email, "wrong-password", "198.51.100.10", null))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(login(email, "wrong-password", "198.51.100.10", null))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void nonexistentAndExistingAccountsHaveTheSameInvalidCredentialResponse() throws Exception {
        String existingEmail = uniqueEmail();
        user(existingEmail, "Correct-Password-123");

        String nonexistentResponse = mvc.perform(login(uniqueEmail(), "wrong-password",
                        "198.51.100.20", null))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String existingResponse = mvc.perform(login(existingEmail, "wrong-password",
                        "198.51.100.21", null))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertEquals(nonexistentResponse, existingResponse);
    }

    @Test
    void successfulLoginWorksBelowTheLimitAndDoesNotResetAttemptProtection() throws Exception {
        String email = uniqueEmail();
        user(email, "Correct-Password-123");
        mvc.perform(login(email, "wrong-password", "198.51.100.30", null))
                .andExpect(status().isUnauthorized());
        mvc.perform(login(email.toUpperCase(java.util.Locale.ROOT), "wrong-password",
                        "198.51.100.30", null))
                .andExpect(status().isUnauthorized());

        mvc.perform(login(email, "Correct-Password-123", "198.51.100.30", null))
                .andExpect(status().isOk());
        mvc.perform(login(email, "wrong-password", "198.51.100.30", null))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void forwardedHeadersCannotSpoofTheSourceIp() throws Exception {
        for (int attempt = 0; attempt < 3; attempt++) {
            mvc.perform(login(uniqueEmail(), "wrong-password", "198.51.100.40",
                            "203.0.113." + (attempt + 1)))
                    .andExpect(status().isUnauthorized());
        }

        mvc.perform(login(uniqueEmail(), "wrong-password", "198.51.100.40", "192.0.2.200"))
                .andExpect(status().isTooManyRequests());
        mvc.perform(login(uniqueEmail(), "wrong-password", "198.51.100.41", "192.0.2.200"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void verificationAndPasswordResetRequestsShareTheirGenericRateLimit() throws Exception {
        String email = uniqueEmail();
        EmailRequest request = new EmailRequest(email);

        for (int attempt = 0; attempt < 5; attempt++) {
            mvc.perform(emailRequest("/api/auth/verification/request", request))
                    .andExpect(status().isOk());
        }

        mvc.perform(emailRequest("/api/auth/password/forgot", request))
                .andExpect(status().isTooManyRequests());
    }

    private MockHttpServletRequestBuilder login(String email, String password, String remoteAddress,
            String forwardedFor) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/auth/login")
                .with(servletRequest -> {
                    servletRequest.setRemoteAddr(remoteAddress);
                    return servletRequest;
                })
                .contentType(APPLICATION_JSON)
                .content(json.writeValueAsString(new LoginRequest(email, password)));
        if (forwardedFor != null) {
            request.header("X-Forwarded-For", forwardedFor);
        }
        return request;
    }

    private MockHttpServletRequestBuilder emailRequest(String path, EmailRequest request) throws Exception {
        return post(path).contentType(APPLICATION_JSON).content(json.writeValueAsString(request));
    }

    private User user(String email, String password) {
        return users.save(User.builder().email(email).password(passwords.encode(password))
                .role(User.Role.STUDENT).enabled(true).emailVerified(true).build());
    }

    private String uniqueEmail() {
        return UUID.randomUUID() + "@example.test";
    }
}
