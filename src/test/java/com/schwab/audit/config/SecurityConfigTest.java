package com.schwab.audit.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unauthenticatedRequestToAuditEndpointReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/audit/events"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidCredentialsReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/audit/events")
                .with(httpBasic("admin", "wrong-password")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validCredentialsPassesAuthentication() throws Exception {
        mockMvc.perform(get("/api/audit/events")
                .with(httpBasic("admin", "secret-audit-key")))
                .andExpect(status().isOk());
    }
}
