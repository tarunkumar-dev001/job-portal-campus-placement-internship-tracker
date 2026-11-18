package com.example.demo;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.example.demo.dto.AuthFlowDtos.EmailRequest;
import com.example.demo.dto.LoginRequest;
import com.example.demo.service.AuthTokenSink;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.security.rate-limit.max-entries=30",
        "app.security.rate-limit.email.max-attempts=5",
        "app.security.rate-limit.email.window-seconds=60",
        "app.security.rate-limit.login.account.max-attempts=1000",
        "app.security.rate-limit.login.account.window-seconds=900",
        "app.security.rate-limit.login.ip.max-attempts=1000",
        "app.security.rate-limit.login.ip.window-seconds=60"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AuthLimiterCapacityIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean AuthTokenSink tokenSink;

    @Test
    void emailCapacityExhaustionDoesNotBlockLoginAttempts() throws Exception {
        for (int attempt = 0; attempt < 6; attempt++) {
            mvc.perform(emailRequest(uniqueEmail()))
                    .andExpect(status().isOk());
        }
        mvc.perform(emailRequest(uniqueEmail()))
                .andExpect(status().isTooManyRequests());

        mvc.perform(login(uniqueEmail(), "wrong-password", "198.51.100.70"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ipCapacityExhaustionDoesNotBlockVerificationRequests() throws Exception {
        for (int attempt = 0; attempt < 6; attempt++) {
            mvc.perform(login(uniqueEmail(), "wrong-password", "203.0.113." + attempt))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(login(uniqueEmail(), "wrong-password", "203.0.113.6"))
                .andExpect(status().isTooManyRequests());

        mvc.perform(emailRequest(uniqueEmail()))
                .andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder emailRequest(String email) throws Exception {
        return post("/api/auth/verification/request")
                .contentType(APPLICATION_JSON)
                .content(json.writeValueAsString(new EmailRequest(email)));
    }

    private MockHttpServletRequestBuilder login(String email, String password, String remoteAddress)
            throws Exception {
        return post("/api/auth/login")
                .with(request -> {
                    request.setRemoteAddr(remoteAddress);
                    return request;
                })
                .contentType(APPLICATION_JSON)
                .content(json.writeValueAsString(new LoginRequest(email, password)));
    }

    private String uniqueEmail() {
        return UUID.randomUUID() + "@example.test";
    }
}
