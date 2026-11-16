package com.example.demo;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import java.util.List;

import com.example.demo.controller.AdminController;
import com.example.demo.controller.AuthController;
import com.example.demo.controller.PlacementController;
import com.example.demo.dto.AuthFlowDtos.MessageResponse;
import com.example.demo.dto.AuthResponse;
import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.RegisterRequest;
import com.example.demo.entity.User.Role;
import com.example.demo.repository.RefreshTokenRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.JwtUtils;
import com.example.demo.service.AdminService;
import com.example.demo.service.AuthSecurityService;
import com.example.demo.service.AuthService;
import com.example.demo.service.PlacementService;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AdminController.class, AuthController.class, PlacementController.class},
        properties = {
                "app.cors.allowed-origins=http://localhost",
                "server.error.include-message=never",
                "server.error.include-stacktrace=never"
        })
@Import(com.example.demo.config.SecurityConfig.class)
class SecurityAuthorizationIntegrationTests {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    JwtUtils jwtUtils;

    @MockitoBean
    UserRepository users;

    @MockitoBean
    RefreshTokenRepository refreshTokens;

    @MockitoBean
    AdminService adminService;

    @MockitoBean
    AuthService authService;

    @MockitoBean
    AuthSecurityService authSecurityService;

    @MockitoBean
    PlacementService placementService;

    @Test
    void anonymousRequestsToProtectedEndpointsRemainDenied() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/placement/drives"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/error")).andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousErrorDispatchToProtectedApiDoesNotBypassAuthorization() throws Exception {
        mockMvc.perform(get("/api/admin/users").with(request -> {
            request.setDispatcherType(DispatcherType.ERROR);
            return request;
        })).andExpect(status().isUnauthorized());
    }

    @Test
    void genericErrorDispatchIsHandledWithoutExposingExceptionDetails() throws Exception {
        mockMvc.perform(get("/error").with(request -> {
            request.setDispatcherType(DispatcherType.ERROR);
            assertEquals(DispatcherType.ERROR, request.getDispatcherType());
            request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 400);
            request.setAttribute(RequestDispatcher.ERROR_MESSAGE, "private registration detail");
            request.setAttribute(RequestDispatcher.ERROR_EXCEPTION,
                    new IllegalStateException("private exception detail"));
            request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/api/auth/register");
            return request;
        }))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(content().string(Matchers.not(Matchers.containsString("private registration detail"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("private exception detail"))));
    }

    @Test
    void publicRegistrationAndLoginRoutesRemainAccessible() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenReturn(new MessageResponse("Account created."));
        when(authService.login(any(LoginRequest.class), anyString()))
                .thenReturn(new AuthResponse("test-access-token", "test-refresh-token",
                        "student@example.test", "STUDENT", "Bearer"));

        for (Role role : List.of(Role.STUDENT, Role.RECRUITER)) {
            RegisterRequest registration = new RegisterRequest(
                    role.name().toLowerCase() + "@example.test", "Long-Enough-Test-Password",
                    role, "Test User", "TEST-1", "Computer Science", 8.0, 0, null);
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(registration)))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("student@example.test", "Long-Enough-Test-Password"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("STUDENT"));
    }

    @Test
    void registrationFailureReturnsSafeClientError() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "private registration detail"));

        RegisterRequest registration = new RegisterRequest(
                "admin@example.test", "Long-Enough-Test-Password",
                Role.ADMIN, null, null, null, null, null, null);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registration)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(Matchers.not(Matchers.containsString("private registration detail"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("trace"))));
    }

    @Test
    @WithMockUser(username = "student@example.com", roles = "STUDENT")
    void studentCannotAccessAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/users")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "recruiter@example.com", roles = "RECRUITER")
    void recruiterCannotAccessPlacementOfficerApi() throws Exception {
        mockMvc.perform(get("/api/placement/drives")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "officer@example.com", roles = "PLACEMENT_OFFICER")
    void placementOfficerCannotAccessAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/stats")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void adminCanAccessAdministrationApi() throws Exception {
        mockMvc.perform(get("/api/admin/stats")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "officer@example.com", roles = "PLACEMENT_OFFICER")
    void placementOfficerCanAccessPlacementApi() throws Exception {
        when(placementService.drives(anyString())).thenReturn(List.of());
        mockMvc.perform(get("/api/placement/drives")).andExpect(status().isOk());
    }
}
