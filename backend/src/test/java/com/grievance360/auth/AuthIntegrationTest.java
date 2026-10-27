package com.grievance360.auth;

import com.grievance360.IntegrationTestBase;
import com.grievance360.user.Role;
import com.grievance360.user.User;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthIntegrationTest extends IntegrationTestBase {

    @Test
    void registerCreatesCitizenAndIgnoresRequestedRole() throws Exception {
        String email = unique("reg") + "@example.com";
        var body = Map.of("fullName", "Asha Rao", "email", email, "password", "Secure123", "role", "ADMIN");
        var res = mvc.perform(postJson("/api/v1/auth/register", body, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.role").value("CITIZEN"))
                .andReturn();
        assertThat(res.getResponse().getContentAsString()).doesNotContain("passwordHash").doesNotContain("Secure123");
        User saved = users.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(saved.getRole()).isEqualTo(Role.CITIZEN);
        assertThat(saved.getPasswordHash()).startsWith("$2").isNotEqualTo("Secure123");
    }

    @Test
    void duplicateEmailIsConflict() throws Exception {
        String email = unique("dup") + "@example.com";
        var body = Map.of("fullName", "Asha Rao", "email", email, "password", "Secure123");
        mvc.perform(postJson("/api/v1/auth/register", body, null)).andExpect(status().isCreated());
        mvc.perform(postJson("/api/v1/auth/register", Map.of("fullName", "Other", "email", email.toUpperCase(), "password", "Secure123"), null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_IN_USE"));
    }

    @Test
    void invalidRegistrationReturnsFieldErrors() throws Exception {
        mvc.perform(postJson("/api/v1/auth/register", Map.of("fullName", "", "email", "not-an-email", "password", "short"), null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'email')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'password')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'fullName')]").exists());
    }

    @Test
    void loginSucceedsWithCorrectPasswordOnly() throws Exception {
        User u = user(Role.CITIZEN, null);
        mvc.perform(postJson("/api/v1/auth/login", Map.of("email", u.getEmail(), "password", "Password123"), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value(u.getEmail()));
        mvc.perform(postJson("/api/v1/auth/login", Map.of("email", u.getEmail(), "password", "Wrong12345"), null))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        mvc.perform(postJson("/api/v1/auth/login", Map.of("email", "nobody@example.com", "password", "Wrong12345"), null))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deactivatedUsersCannotLoginOrUseTokens() throws Exception {
        User u = user(Role.CITIZEN, null);
        String token = token(u);
        u.setActive(false);
        users.save(u);
        mvc.perform(postJson("/api/v1/auth/login", Map.of("email", u.getEmail(), "password", "Password123"), null))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", token)).andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointsReturnJson401And403() throws Exception {
        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer garbage.token.value"))
                .andExpect(status().isUnauthorized());
        User citizen = user(Role.CITIZEN, null);
        mvc.perform(getAs("/api/v1/admin/users", citizen))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(getAs("/api/v1/officer/dashboard", citizen)).andExpect(status().isForbidden());
    }

    @Test
    void corsPreflightAllowsConfiguredOriginOnly() throws Exception {
        mvc.perform(options("/api/v1/auth/login")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mvc.perform(options("/api/v1/auth/login")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }
}
