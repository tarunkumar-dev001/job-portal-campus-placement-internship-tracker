package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityAuthorizationIntegrationTests {
    @Autowired
    MockMvc mockMvc;

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
}
