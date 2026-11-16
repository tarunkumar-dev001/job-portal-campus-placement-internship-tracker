package com.example.demo;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.demo.dto.AuthFlowDtos.RefreshRequest;
import com.example.demo.dto.AuthFlowDtos.ResetPasswordRequest;
import com.example.demo.dto.AuthFlowDtos.TokenResponse;
import com.example.demo.dto.admin.AdminDtos.UserStatusRequest;
import com.example.demo.dto.AuthResponse;
import com.example.demo.dto.LoginRequest;
import com.example.demo.entity.SecurityToken;
import com.example.demo.entity.StudentProfile;
import com.example.demo.entity.User;
import com.example.demo.security.JwtUtils;
import com.example.demo.repository.SecurityTokenRepository;
import com.example.demo.repository.StudentProfileRepository;
import com.example.demo.repository.RefreshTokenRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.AuthSecurityService;
import com.example.demo.service.AuthTokenSink;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationSessionLifecycleIntegrationTests {
    private static final String PASSWORD = "Current-Test-Password-123";
    private static final String RESET_PASSWORD = "Replacement-Test-Password-456";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired StudentProfileRepository profiles;
    @Autowired SecurityTokenRepository securityTokens;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtUtils jwtUtils;
    @Value("${app.jwt.secret}") String jwtSecret;
    @MockitoBean AuthTokenSink tokenSink;

    @Test
    void separatelyIssuedSessionsCanRefreshIndependently() throws Exception {
        User user = createStudent();
        AuthResponse sessionA = login(user);
        AuthResponse sessionB = login(user);

        org.junit.jupiter.api.Assertions.assertNotEquals(sessionA.refreshToken(), sessionB.refreshToken());
        refresh(sessionA.refreshToken()).andExpect(status().isOk());
        refresh(sessionB.refreshToken()).andExpect(status().isOk());
        profile(sessionA.token()).andExpect(status().isOk());
        profile(sessionB.token()).andExpect(status().isOk());
    }

    @Test
    void logoutRevokesOnlyTheCurrentRefreshSession() throws Exception {
        User user = createStudent();
        AuthResponse sessionA = login(user);
        AuthResponse sessionB = login(user);

        logout(sessionA.refreshToken()).andExpect(status().isOk());
        refresh(sessionA.refreshToken()).andExpect(status().isUnauthorized());
        refresh(sessionB.refreshToken()).andExpect(status().isOk());
        profile(sessionB.token()).andExpect(status().isOk());
    }

    @Test
    void logoutInvalidatesTheCurrentSessionsExistingAccessToken() throws Exception {
        User user = createStudent();
        AuthResponse session = login(user);

        logout(session.refreshToken()).andExpect(status().isOk());

        profile(session.token()).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutAfterMultipleRotationsInvalidatesOnlyThatSessionFamily() throws Exception {
        User user = createStudent();
        AuthResponse sessionA = login(user);
        AuthResponse sessionB = login(user);
        TokenResponse rotatedA = refreshSuccessfully(sessionA.refreshToken());
        TokenResponse rotatedAgainA = refreshSuccessfully(rotatedA.refreshToken());
        TokenResponse rotatedThirdA = refreshSuccessfully(rotatedAgainA.refreshToken());

        logout(rotatedThirdA.refreshToken()).andExpect(status().isOk());

        assertAll(
                () -> profile(sessionA.token()).andExpect(status().isUnauthorized()),
                () -> profile(rotatedA.token()).andExpect(status().isUnauthorized()),
                () -> profile(rotatedAgainA.token()).andExpect(status().isUnauthorized()),
                () -> profile(rotatedThirdA.token()).andExpect(status().isUnauthorized()),
                () -> refresh(rotatedThirdA.refreshToken()).andExpect(status().isUnauthorized()),
                () -> profile(sessionB.token()).andExpect(status().isOk()),
                () -> refresh(sessionB.refreshToken()).andExpect(status().isOk()));
    }

    @Test
    void passwordResetRevokesRefreshTokensForEverySession() throws Exception {
        User user = createStudent();
        AuthResponse sessionA = login(user);
        AuthResponse sessionB = login(user);
        String resetToken = issueResetToken(user);

        resetPassword(resetToken).andExpect(status().isOk());

        assertAll(
                () -> refresh(sessionA.refreshToken()).andExpect(status().isUnauthorized()),
                () -> refresh(sessionB.refreshToken()).andExpect(status().isUnauthorized()));
    }

    @Test
    void passwordResetInvalidatesAccessTokensFromEverySession() throws Exception {
        User user = createStudent();
        AuthResponse sessionA = login(user);
        AuthResponse sessionB = login(user);
        String resetToken = issueResetToken(user);

        resetPassword(resetToken).andExpect(status().isOk());

        assertAll(
                () -> profile(sessionA.token()).andExpect(status().isUnauthorized()),
                () -> profile(sessionB.token()).andExpect(status().isUnauthorized()));
    }

    @Test
    void predecessorReplayRevokesOnlyItsRefreshTokenFamily() throws Exception {
        User user = createStudent();
        AuthResponse sessionA = login(user);
        AuthResponse sessionB = login(user);
        TokenResponse rotatedA = refreshSuccessfully(sessionA.refreshToken());
        TokenResponse rotatedAgainA = refreshSuccessfully(rotatedA.refreshToken());
        TokenResponse rotatedThirdA = refreshSuccessfully(rotatedAgainA.refreshToken());

        refresh(sessionA.refreshToken()).andExpect(status().isUnauthorized());

        assertAll(
                () -> refresh(rotatedThirdA.refreshToken()).andExpect(status().isUnauthorized()),
                () -> refresh(sessionB.refreshToken()).andExpect(status().isOk()));
    }

    @Test
    void predecessorReplayInvalidatesAllAccessTokensFromTheAffectedFamily() throws Exception {
        User user = createStudent();
        AuthResponse sessionA = login(user);
        AuthResponse sessionB = login(user);
        TokenResponse rotatedA = refreshSuccessfully(sessionA.refreshToken());
        TokenResponse rotatedAgainA = refreshSuccessfully(rotatedA.refreshToken());
        TokenResponse rotatedThirdA = refreshSuccessfully(rotatedAgainA.refreshToken());

        refresh(sessionA.refreshToken()).andExpect(status().isUnauthorized());

        assertAll(
                () -> profile(sessionA.token()).andExpect(status().isUnauthorized()),
                () -> profile(rotatedA.token()).andExpect(status().isUnauthorized()),
                () -> profile(rotatedAgainA.token()).andExpect(status().isUnauthorized()),
                () -> profile(rotatedThirdA.token()).andExpect(status().isUnauthorized()),
                () -> profile(sessionB.token()).andExpect(status().isOk()));
    }

    @Test
    void disablingAndReenablingAccountDoesNotRestoreOldSessionsButAllowsANewLogin() throws Exception {
        User user = createStudent();
        User admin = createAccount(User.Role.ADMIN);
        AuthResponse session = login(user);
        AuthResponse adminSession = login(admin);

        setAccountEnabled(adminSession.token(), user.getId(), false).andExpect(status().isOk());
        profile(session.token()).andExpect(status().isUnauthorized());
        refresh(session.refreshToken()).andExpect(status().isUnauthorized());

        setAccountEnabled(adminSession.token(), user.getId(), true).andExpect(status().isOk());

        assertAll(
                () -> profile(session.token()).andExpect(status().isUnauthorized()),
                () -> refresh(session.refreshToken()).andExpect(status().isUnauthorized()));
        AuthResponse newSession = login(user);
        profile(newSession.token()).andExpect(status().isOk());
        refresh(newSession.refreshToken()).andExpect(status().isOk());
    }

    @Test
    void existingAccessTokenUsesCurrentRoleAfterRoleChange() throws Exception {
        User user = createStudent();
        AuthResponse session = login(user);
        profile(session.token()).andExpect(status().isOk());

        user.setRole(User.Role.RECRUITER);
        users.saveAndFlush(user);

        profile(session.token()).andExpect(status().isForbidden());
    }

    @Test
    void concurrentRefreshRequestsRotateOnceAndReplayRevokesTheFamily() throws Exception {
        User user = createStudent();
        AuthResponse session = login(user);
        String body = json.writeValueAsString(new RefreshRequest(session.refreshToken()));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<MvcResult>> results = new ArrayList<>();

        try {
            for (int index = 0; index < 2; index++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Refresh test start timed out");
                    }
                    return mvc.perform(post("/api/auth/refresh")
                                    .contentType(APPLICATION_JSON)
                                    .content(body))
                            .andReturn();
                }));
            }
            org.junit.jupiter.api.Assertions.assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            List<MvcResult> responses = new ArrayList<>();
            for (Future<MvcResult> result : results) {
                responses.add(result.get(10, TimeUnit.SECONDS));
            }

            List<MvcResult> successful = responses.stream()
                    .filter(result -> result.getResponse().getStatus() == 200)
                    .toList();
            org.junit.jupiter.api.Assertions.assertEquals(1, successful.size());
            org.junit.jupiter.api.Assertions.assertEquals(1, responses.stream()
                    .filter(result -> result.getResponse().getStatus() == 401).count());
            TokenResponse rotated = json.readValue(successful.get(0).getResponse().getContentAsString(),
                    TokenResponse.class);
            refresh(rotated.refreshToken()).andExpect(status().isUnauthorized());
            profile(rotated.token()).andExpect(status().isUnauthorized());
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void expiredRefreshTokenAlsoExpiresItsAccessSession() throws Exception {
        User user = createStudent();
        AuthResponse session = login(user);
        var storedRefresh = refreshTokens.findByTokenHash(AuthSecurityService.hash(session.refreshToken()))
                .orElseThrow();
        storedRefresh.setExpiresAt(Instant.now().minusSeconds(1));
        refreshTokens.saveAndFlush(storedRefresh);

        refresh(session.refreshToken()).andExpect(status().isUnauthorized());
        profile(session.token()).andExpect(status().isUnauthorized());
    }

    @Test
    void expiredAccessJwtIsRejectedWhileItsSessionIsActive() throws Exception {
        User user = createStudent();
        AuthResponse session = login(user);
        String sessionId = jwtUtils.extractSessionId(session.token());
        String expiredAccessToken = new JwtUtils(jwtSecret, -60_000L)
                .generateToken(user.getEmail(), user.getRole().name(), sessionId);

        profile(session.token()).andExpect(status().isOk());
        profile(expiredAccessToken).andExpect(status().isUnauthorized());
    }

    @Test
    void preV6SessionlessAccessJwtIsRejected() throws Exception {
        User user = createStudent();
        AuthResponse session = login(user);

        profile(session.token()).andExpect(status().isOk());
        profile(createPreV6AccessToken(user)).andExpect(status().isUnauthorized());
    }

    private String createPreV6AccessToken(User user) {
        Date issuedAt = new Date();
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(issuedAt)
                .expiration(new Date(issuedAt.getTime() + 60_000L))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private User createStudent() {
        User user = createAccount(User.Role.STUDENT);
        profiles.save(StudentProfile.builder().user(user).fullName("Session Test Student")
                .rollNumber(UUID.randomUUID().toString()).department("Computer Science")
                .cgpa(8.5).backlogs(0).build());
        return user;
    }

    private User createAccount(User.Role role) {
        String email = UUID.randomUUID() + "@example.test";
        return users.saveAndFlush(User.builder().email(email).password(passwords.encode(PASSWORD))
                .role(role).enabled(true).emailVerified(true).build());
    }

    private AuthResponse login(User user) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(new LoginRequest(user.getEmail(), PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return json.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
    }

    private org.springframework.test.web.servlet.ResultActions refresh(String refreshToken) throws Exception {
        return mvc.perform(post("/api/auth/refresh")
                .contentType(APPLICATION_JSON)
                .content(json.writeValueAsString(new RefreshRequest(refreshToken))));
    }

    private TokenResponse refreshSuccessfully(String refreshToken) throws Exception {
        MvcResult result = refresh(refreshToken).andExpect(status().isOk()).andReturn();
        return json.readValue(result.getResponse().getContentAsString(), TokenResponse.class);
    }

    private org.springframework.test.web.servlet.ResultActions logout(String refreshToken) throws Exception {
        return mvc.perform(post("/api/auth/logout")
                .contentType(APPLICATION_JSON)
                .content(json.writeValueAsString(new RefreshRequest(refreshToken))));
    }

    private org.springframework.test.web.servlet.ResultActions resetPassword(String token) throws Exception {
        return mvc.perform(post("/api/auth/password/reset")
                .contentType(APPLICATION_JSON)
                .content(json.writeValueAsString(new ResetPasswordRequest(token, RESET_PASSWORD))));
    }

    private org.springframework.test.web.servlet.ResultActions setAccountEnabled(String adminToken,
            Long userId, boolean enabled) throws Exception {
        return mvc.perform(patch("/api/admin/users/{userId}/status", userId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(APPLICATION_JSON)
                .content(json.writeValueAsString(new UserStatusRequest(enabled))));
    }

    private String issueResetToken(User user) {
        String token = UUID.randomUUID().toString();
        securityTokens.save(SecurityToken.builder().user(user)
                .tokenHash(AuthSecurityService.hash(token))
                .type(SecurityToken.Type.PASSWORD_RESET)
                .expiresAt(Instant.now().plusSeconds(300)).build());
        return token;
    }

    private org.springframework.test.web.servlet.ResultActions profile(String accessToken) throws Exception {
        return mvc.perform(get("/api/student/profile")
                .header("Authorization", "Bearer " + accessToken));
    }
}
