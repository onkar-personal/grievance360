package com.grievance360.user;

import com.grievance360.IntegrationTestBase;
import com.grievance360.audit.AuditLogRepository;
import com.grievance360.department.Department;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminUserIntegrationTest extends IntegrationTestBase {
    @Autowired AuditLogRepository audit;

    @Test
    void adminChangesRoleWithAuditTrailAndCannotLockThemselvesOut() throws Exception {
        User admin = user(Role.ADMIN, null);
        User target = user(Role.CITIZEN, null);
        Department d = department();

        var update = new HashMap<String, Object>(Map.of("fullName", "Promoted Officer", "role", "OFFICER", "active", true));
        mvc.perform(authed(put("/api/v1/admin/users/" + target.getId()), admin)
                        .contentType("application/json").content(json.writeValueAsString(update)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DEPARTMENT_REQUIRED"));

        update.put("departmentId", d.getId());
        mvc.perform(authed(put("/api/v1/admin/users/" + target.getId()), admin)
                        .contentType("application/json").content(json.writeValueAsString(update)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("OFFICER"));
        assertThat(audit.findAll()).anyMatch(a -> a.getAction().equals("USER_ROLE_CHANGED")
                && a.getEntityId().equals(String.valueOf(target.getId())) && a.getActorId().equals(admin.getId()));

        var self = Map.of("fullName", "Me", "role", "CITIZEN", "active", true);
        mvc.perform(authed(put("/api/v1/admin/users/" + admin.getId()), admin)
                        .contentType("application/json").content(json.writeValueAsString(self)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SELF_LOCKOUT"));
    }

    @Test
    void profileUpdateCannotChangeRole() throws Exception {
        User citizen = user(Role.CITIZEN, null);
        var body = Map.of("fullName", "New Name", "phone", "9876543210", "role", "ADMIN");
        mvc.perform(authed(put("/api/v1/users/me"), citizen)
                        .contentType("application/json").content(json.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("New Name"))
                .andExpect(jsonPath("$.role").value("CITIZEN"));
    }
}
