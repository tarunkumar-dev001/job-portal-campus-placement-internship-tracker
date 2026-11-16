package com.example.demo;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.example.demo.dto.AuthFlowDtos.*;
import com.example.demo.dto.AuthFlowDtos.TokenResponse;
import com.example.demo.dto.AuthResponse;
import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.RegisterRequest;
import com.example.demo.entity.*;
import com.example.demo.repository.*;
import com.example.demo.service.AuthService;
import com.example.demo.service.AuthSecurityService;
import com.example.demo.service.AuthTokenSink;
import com.example.demo.service.EmailDeliveryException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.reset;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.mockito.ArgumentCaptor;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthenticationSecurityTokenIntegrationTests {
    @Autowired UserRepository users;
    @Autowired SecurityTokenRepository tokens;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired AuthSecurityService auth;
    @Autowired AuthService authService;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired StudentProfileRepository profiles;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean AuthTokenSink tokenSink;

    private User user(String email) {
        return users.save(User.builder().email(email).password("encoded").role(User.Role.STUDENT)
                .enabled(true).emailVerified(true).build());
    }

    @Test void newlyRegisteredAccountIsEnabledAndCanLogIn() {
        RegisterRequest request = new RegisterRequest("new-recruiter@example.com", "Long-Enough-Test-Password",
                User.Role.RECRUITER, null, null, null, null, null, null);
        var response = authService.register(request);
        ArgumentCaptor<String> deliveredToken = ArgumentCaptor.forClass(String.class);
        verify(tokenSink).deliver(eq(request.email()), eq(SecurityToken.Type.EMAIL_VERIFICATION), deliveredToken.capture());
        assertFalse(response.message().contains(deliveredToken.getValue()));

        User registered = users.findByEmail(request.email()).orElseThrow();
        assertTrue(registered.isEnabled());
        assertFalse(registered.isEmailVerified());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> authService.login(new LoginRequest(request.email(), request.password()))).getStatusCode());
        SecurityToken storedToken = tokens.findByTokenHashAndType(AuthSecurityService.hash(deliveredToken.getValue()),
                SecurityToken.Type.EMAIL_VERIFICATION).orElseThrow();
        assertNotEquals(deliveredToken.getValue(), storedToken.getTokenHash());
        auth.verifyEmail(new TokenRequest(deliveredToken.getValue()));
        AuthResponse session = authService.login(new LoginRequest(request.email(), request.password()));
        assertNotNull(session.token());
    }

    @Test
    void publicRegistrationRejectsPrivilegedRoles() throws Exception {
        for (User.Role role : List.of(User.Role.PLACEMENT_OFFICER, User.Role.ADMIN)) {
            String email = role.name().toLowerCase() + "-public@example.com";
            RegisterRequest request = new RegisterRequest(email, "Long-Enough-Test-Password",
                    role, null, null, null, null, null, null);

            mvc.perform(post("/api/auth/register")
                            .contentType(APPLICATION_JSON)
                            .content(json.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            assertTrue(users.findByEmail(email).isEmpty());
        }
    }

    @Test
    void publicRegistrationContinuesToAllowStudentAndRecruiter() throws Exception {
        RegisterRequest student = new RegisterRequest("public-student@example.com", "Long-Enough-Test-Password",
                User.Role.STUDENT, "Public Student", "PUBLIC-1", "Computer Science", 8.5, 0, null);
        RegisterRequest recruiter = new RegisterRequest("public-recruiter@example.com", "Long-Enough-Test-Password",
                User.Role.RECRUITER, null, null, null, null, null, null);

        mvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(student)))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(recruiter)))
                .andExpect(status().isCreated());

        assertEquals(User.Role.STUDENT, users.findByEmail(student.email()).orElseThrow().getRole());
        assertTrue(profiles.findByUserId(users.findByEmail(student.email()).orElseThrow().getId()).isPresent());
        assertEquals(User.Role.RECRUITER, users.findByEmail(recruiter.email()).orElseThrow().getRole());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void registrationRollsBackWhenEmailDeliveryFails() {
        reset(tokenSink);
        doThrow(new EmailDeliveryException()).when(tokenSink)
                .deliver(eq("delivery-failure@example.com"), eq(SecurityToken.Type.EMAIL_VERIFICATION),
                        org.mockito.ArgumentMatchers.anyString());
        RegisterRequest request = new RegisterRequest("delivery-failure@example.com", "Long-Enough-Test-Password",
                User.Role.RECRUITER, null, null, null, null, null, null);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows(ResponseStatusException.class,
                () -> authService.register(request)).getStatusCode());
        assertTrue(users.findByEmail(request.email()).isEmpty());
    }

    @Test void passwordResetConsumesTokenAndRevokesAllRefreshSessions() {
        User user = users.save(User.builder().email("reset-success@example.com")
                .password(passwordEncoder.encode("Old-Test-Password-123"))
                .role(User.Role.STUDENT).enabled(true).emailVerified(true).build());
        var session = auth.issueSession(user);
        String rawToken = "successful-password-reset";
        SecurityToken token = tokens.save(SecurityToken.builder().user(user)
                .tokenHash(AuthSecurityService.hash(rawToken)).type(SecurityToken.Type.PASSWORD_RESET)
                .expiresAt(Instant.now().plusSeconds(300)).build());

        auth.resetPassword(new ResetPasswordRequest(rawToken, "New-Test-Password-456"));

        assertNotNull(tokens.findById(token.getId()).orElseThrow().getUsedAt());
        assertNotNull(refreshTokens.findByTokenHash(AuthSecurityService.hash(session.refreshToken()))
                .orElseThrow().getRevokedAt());
        assertTrue(passwordEncoder.matches("New-Test-Password-456", users.findById(user.getId()).orElseThrow().getPassword()));
        assertThrows(ResponseStatusException.class, () -> auth.refresh(new RefreshRequest(session.refreshToken())));
    }

    @Test void expiredPasswordResetTokenIsRejected() {
        User user = user("expired-reset@example.com");
        tokens.save(SecurityToken.builder().user(user).tokenHash(AuthSecurityService.hash("reset-expired"))
                .type(SecurityToken.Type.PASSWORD_RESET).expiresAt(Instant.now().minusSeconds(60)).build());
        assertThrows(ResponseStatusException.class, () -> auth.resetPassword(new ResetPasswordRequest("reset-expired", "new-password")));
    }

    @Test void passwordResetTokenCannotBeReused() {
        User user = user("reused-reset@example.com");
        tokens.save(SecurityToken.builder().user(user).tokenHash(AuthSecurityService.hash("reset-used"))
                .type(SecurityToken.Type.PASSWORD_RESET).expiresAt(Instant.now().plusSeconds(300)).usedAt(Instant.now()).build());
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> auth.resetPassword(new ResetPasswordRequest("reset-used", "new-password")));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
    }

    @Test void expiredEmailVerificationTokenIsRejected() {
        User user = user("expired-email@example.com");
        tokens.save(SecurityToken.builder().user(user).tokenHash(AuthSecurityService.hash("email-expired"))
                .type(SecurityToken.Type.EMAIL_VERIFICATION).expiresAt(Instant.now().minusSeconds(60)).build());
        assertThrows(ResponseStatusException.class, () -> auth.verifyEmail(new TokenRequest("email-expired")));
    }

    @Test void verificationTokenCannotBeReused() {
        User user = user("reused-email@example.com");
        tokens.save(SecurityToken.builder().user(user).tokenHash(AuthSecurityService.hash("email-used"))
                .type(SecurityToken.Type.EMAIL_VERIFICATION).expiresAt(Instant.now().plusSeconds(300)).usedAt(Instant.now()).build());
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> auth.verifyEmail(new TokenRequest("email-used")));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
    }

    @Test void refreshTokenRotatesAndOldTokenCannotBeReused() {
        User user = user("rotate@example.com");
        TokenResponse issued = auth.issueSession(user);
        TokenResponse rotated = auth.refresh(new RefreshRequest(issued.refreshToken()));
        assertNotEquals(issued.refreshToken(), rotated.refreshToken());
        assertThrows(ResponseStatusException.class, () -> auth.refresh(new RefreshRequest(issued.refreshToken())));
    }

    @Test void logoutRevokesRefreshToken() {
        User user = user("logout@example.com");
        TokenResponse issued = auth.issueSession(user);
        TokenResponse rotated = auth.refresh(new RefreshRequest(issued.refreshToken()));
        auth.logout(new RefreshRequest(issued.refreshToken()));
        assertThrows(ResponseStatusException.class, () -> auth.refresh(new RefreshRequest(issued.refreshToken())));
        assertThrows(ResponseStatusException.class, () -> auth.refresh(new RefreshRequest(rotated.refreshToken())));
    }

    @Test void deactivatedAccountRefreshIsRejected() {
        User user = user("disabled-refresh@example.com");
        TokenResponse issued = auth.issueSession(user);
        user.setEnabled(false);
        users.save(user);
        assertThrows(ResponseStatusException.class, () -> auth.refresh(new RefreshRequest(issued.refreshToken())));
    }

    @Test void unverifiedAccountCannotRefreshSession() {
        User user = user("unverified-refresh@example.com");
        user.setEmailVerified(false);
        users.save(user);
        refreshTokens.save(RefreshToken.builder().user(user)
                .tokenHash(AuthSecurityService.hash("unverified-refresh-token"))
                .expiresAt(Instant.now().plusSeconds(3600)).build());
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class,
                () -> auth.refresh(new RefreshRequest("unverified-refresh-token"))).getStatusCode());
    }
}
