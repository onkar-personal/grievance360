package com.grievance360.complaint;

import com.grievance360.IntegrationTestBase;
import com.grievance360.department.ComplaintCategory;
import com.grievance360.department.Department;
import com.grievance360.user.Role;
import com.grievance360.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ComplaintWorkflowIntegrationTest extends IntegrationTestBase {

    @Autowired StatusHistoryRepository historyRepo;

    Department dept;
    Department otherDept;
    ComplaintCategory category;
    User citizen;
    User otherCitizen;
    User officer;
    User otherDeptOfficer;
    User admin;

    @BeforeEach
    void setUp() {
        dept = department();
        otherDept = department();
        category = category(dept);
        citizen = user(Role.CITIZEN, null);
        otherCitizen = user(Role.CITIZEN, null);
        officer = user(Role.OFFICER, dept);
        otherDeptOfficer = user(Role.OFFICER, otherDept);
        admin = user(Role.ADMIN, null);
    }

    private long submit(User who) throws Exception {
        var req = Map.of("title", "Streetlight broken", "description", "The streetlight outside house 12 has been off for a week.",
                "categoryId", category.getId(), "locationCity", "Pune", "locationPincode", "411001");
        var res = mvc.perform(postJson("/api/v1/complaints", req, who))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.departmentId").value(dept.getId()))
                .andReturn();
        return body(res).get("id").asLong();
    }

    private Map<String, Object> statusReq(String s, String remark, String notes) {
        var m = new HashMap<String, Object>();
        m.put("status", s);
        if (remark != null) m.put("remark", remark);
        if (notes != null) m.put("resolutionNotes", notes);
        return m;
    }

    @Test
    void creationGeneratesServerReferenceAndIgnoresClientWorkflowFields() throws Exception {
        var req = new HashMap<String, Object>(Map.of("title", "Garbage pile", "description", "Garbage has not been collected for days now.",
                "categoryId", category.getId()));
        req.put("status", "RESOLVED");
        req.put("reference", "HACKED-1");
        req.put("priority", "CRITICAL");
        var res = mvc.perform(postJson("/api/v1/complaints", req, citizen))
                .andExpect(status().isCreated()).andReturn();
        var b = body(res);
        assertThat(b.get("reference").asText()).matches("G360-\\d{4}-\\d{6}");
        assertThat(b.get("status").asText()).isEqualTo("SUBMITTED");
        assertThat(b.get("priority").asText()).isEqualTo("MEDIUM");

        long id2 = submit(citizen);
        var second = body(mvc.perform(getAs("/api/v1/complaints/" + id2, citizen)).andReturn());
        assertThat(second.get("reference").asText()).isNotEqualTo(b.get("reference").asText());
    }

    @Test
    void onlyCitizensCanSubmitAndInputIsValidated() throws Exception {
        mvc.perform(postJson("/api/v1/complaints", Map.of("title", "x", "description", "short", "categoryId", category.getId()), citizen))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(postJson("/api/v1/complaints", Map.of("title", "Valid title", "description", "A sufficiently long description here.",
                        "categoryId", category.getId()), officer))
                .andExpect(status().isForbidden());
        mvc.perform(postJson("/api/v1/complaints", Map.of("title", "Valid title", "description", "A sufficiently long description here.",
                        "categoryId", 999999), citizen))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CATEGORY"));
    }

    @Test
    void citizensAndOutsideOfficersCannotSeeOthersComplaints() throws Exception {
        long id = submit(citizen);
        mvc.perform(getAs("/api/v1/complaints/" + id, citizen)).andExpect(status().isOk());
        mvc.perform(getAs("/api/v1/complaints/" + id, otherCitizen)).andExpect(status().isNotFound());
        mvc.perform(getAs("/api/v1/complaints/" + id + "/history", otherCitizen)).andExpect(status().isNotFound());
        mvc.perform(getAs("/api/v1/complaints/" + id, otherDeptOfficer)).andExpect(status().isNotFound());
        mvc.perform(getAs("/api/v1/complaints/" + id, officer)).andExpect(status().isOk());
        mvc.perform(getAs("/api/v1/complaints/" + id, admin)).andExpect(status().isOk());

        var list = body(mvc.perform(getAs("/api/v1/complaints?size=100", otherCitizen)).andReturn());
        list.get("content").forEach(c -> assertThat(c.get("id").asLong()).isNotEqualTo(id));

        mvc.perform(patchJson("/api/v1/complaints/" + id + "/status", statusReq("ACKNOWLEDGED", null, null), otherDeptOfficer))
                .andExpect(status().isNotFound());
        mvc.perform(patchJson("/api/v1/complaints/" + id + "/status", statusReq("ACKNOWLEDGED", null, null), citizen))
                .andExpect(status().isForbidden());
    }

    @Test
    void fullLifecycleWithHistoryAndValidation() throws Exception {
        long id = submit(citizen);
        String base = "/api/v1/complaints/" + id;

        mvc.perform(patchJson(base + "/status", statusReq("RESOLVED", null, "done"), officer))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));

        mvc.perform(patchJson(base + "/status", statusReq("ACKNOWLEDGED", "Received", null), officer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));

        mvc.perform(postJson(base + "/assignment", Map.of("officerId", otherDeptOfficer.getId()), admin))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("OFFICER_DEPARTMENT_MISMATCH"));
        mvc.perform(postJson(base + "/assignment", Map.of("officerId", officer.getId()), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.assignedOfficer.id").value(officer.getId()));

        mvc.perform(patchJson(base + "/status", statusReq("IN_PROGRESS", null, null), officer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        mvc.perform(patchJson(base + "/status", statusReq("RESOLVED", null, null), officer))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("RESOLUTION_REQUIRED"));
        mvc.perform(patchJson(base + "/status", statusReq("RESOLVED", "Fixed", "Replaced the faulty lamp and wiring."), officer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedAt").isNotEmpty())
                .andExpect(jsonPath("$.actions.canReopen").value(false));

        // Citizen view
        mvc.perform(getAs(base, citizen))
                .andExpect(jsonPath("$.actions.canReopen").value(true))
                .andExpect(jsonPath("$.actions.canConfirmClose").value(true))
                .andExpect(jsonPath("$.citizenEmail").doesNotExist());

        mvc.perform(postJson(base + "/reopen", Map.of("reason", "short"), citizen)).andExpect(status().isBadRequest());
        mvc.perform(postJson(base + "/reopen", Map.of("reason", "The light went off again the next day."), citizen))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.reopenCount").value(1));
        mvc.perform(postJson(base + "/reopen", Map.of("reason", "The light went off again the next day."), citizen))
                .andExpect(status().isConflict());

        mvc.perform(patchJson(base + "/status", statusReq("RESOLVED", null, "Replaced the pole junction box."), officer))
                .andExpect(status().isOk());
        mvc.perform(postJson(base + "/close", Map.of("feedback", "Works now"), citizen))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"));

        var history = historyRepo.findByComplaintIdOrderByCreatedAtAscIdAsc(id);
        assertThat(history).extracting(StatusHistory::getToStatus).containsExactly(
                ComplaintStatus.SUBMITTED, ComplaintStatus.ACKNOWLEDGED, ComplaintStatus.ASSIGNED,
                ComplaintStatus.IN_PROGRESS, ComplaintStatus.RESOLVED, ComplaintStatus.IN_PROGRESS,
                ComplaintStatus.RESOLVED, ComplaintStatus.CLOSED);
        assertThat(history).allSatisfy(h -> assertThat(h.getActorRole()).isNotBlank());

        mvc.perform(patchJson(base + "/status", statusReq("IN_PROGRESS", null, null), admin)).andExpect(status().isConflict());
    }

    @Test
    void officersOnlyProgressComplaintsAssignedToThem() throws Exception {
        long id = submit(citizen);
        String base = "/api/v1/complaints/" + id;
        User colleague = user(Role.OFFICER, dept);
        mvc.perform(postJson(base + "/assignment", Map.of("officerId", officer.getId()), colleague)).andExpect(status().isForbidden());
        mvc.perform(postJson(base + "/assignment", Map.of("officerId", colleague.getId()), colleague))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ASSIGNED"));
        mvc.perform(patchJson(base + "/status", statusReq("IN_PROGRESS", null, null), officer)).andExpect(status().isForbidden());
    }

    @Test
    void needsInformationRequiresRemarkAndCitizenReplyResumes() throws Exception {
        long id = submit(citizen);
        String base = "/api/v1/complaints/" + id;
        mvc.perform(patchJson(base + "/status", statusReq("ACKNOWLEDGED", null, null), officer)).andExpect(status().isOk());
        mvc.perform(patchJson(base + "/status", statusReq("NEEDS_INFORMATION", null, null), officer))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REMARK_REQUIRED"));
        mvc.perform(patchJson(base + "/status", statusReq("NEEDS_INFORMATION", "Please share the pole number", null), officer))
                .andExpect(status().isOk());

        mvc.perform(postJson(base + "/comments", Map.of("body", "Pole number is 44-B", "visibility", "INTERNAL"), citizen))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.visibility").value("PUBLIC"));
        mvc.perform(getAs(base, citizen)).andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));

        mvc.perform(postJson(base + "/comments", Map.of("body", "Internal: check stock", "visibility", "INTERNAL"), officer))
                .andExpect(status().isCreated());
        var citizenComments = body(mvc.perform(getAs(base + "/comments", citizen)).andReturn());
        citizenComments.forEach(c -> assertThat(c.get("visibility").asText()).isEqualTo("PUBLIC"));
        var officerComments = body(mvc.perform(getAs(base + "/comments", officer)).andReturn());
        assertThat(officerComments.size()).isEqualTo(citizenComments.size() + 1);
    }

    @Test
    void publicLookupExposesNoPersonalData() throws Exception {
        long id = submit(citizen);
        String ref = body(mvc.perform(getAs("/api/v1/complaints/" + id, citizen)).andReturn()).get("reference").asText();
        var res = mvc.perform(get("/api/v1/public/complaints/" + ref))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andReturn();
        String text = res.getResponse().getContentAsString();
        assertThat(text).doesNotContain(citizen.getEmail()).doesNotContain("Streetlight broken").doesNotContain("citizen");
        mvc.perform(get("/api/v1/public/complaints/G360-1999-000000")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/public/complaints/drop-table")).andExpect(status().isBadRequest());
    }

    @Test
    void attachmentsAreValidatedByContent() throws Exception {
        long id = submit(citizen);
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        mvc.perform(authed(multipart("/api/v1/complaints/" + id + "/attachments")
                        .file(new MockMultipartFile("file", "evidence.png", "image/png", png)), citizen))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("image/png"));
        mvc.perform(authed(multipart("/api/v1/complaints/" + id + "/attachments")
                        .file(new MockMultipartFile("file", "photo.png", "image/png", "MZ fake exe".getBytes())), citizen))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(authed(multipart("/api/v1/complaints/" + id + "/attachments")
                        .file(new MockMultipartFile("file", "evidence.png", "image/png", png)), otherCitizen))
                .andExpect(status().isNotFound());

        var detail = body(mvc.perform(getAs("/api/v1/complaints/" + id, citizen)).andReturn());
        long attId = detail.get("attachments").get(0).get("id").asLong();
        mvc.perform(getAs("/api/v1/complaints/" + id + "/attachments/" + attId, citizen)).andExpect(status().isOk());
        mvc.perform(getAs("/api/v1/complaints/" + id + "/attachments/" + attId, otherCitizen)).andExpect(status().isNotFound());
    }

    @Test
    void dashboardsReturnRealAggregates() throws Exception {
        submit(citizen);
        mvc.perform(getAs("/api/v1/dashboard/citizen", citizen))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.total").value(1))
                .andExpect(jsonPath("$.totals.open").value(1));
        mvc.perform(getAs("/api/v1/admin/dashboard", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trend.length()").value(30));
        mvc.perform(getAs("/api/v1/officer/dashboard", officer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departmentUnassigned").value(1));
        mvc.perform(getAs("/api/v1/admin/reports/sla", admin)).andExpect(status().isOk());
    }

    @Test
    void sortingIsWhitelisted() throws Exception {
        mvc.perform(getAs("/api/v1/complaints?sort=citizen.passwordHash,asc", admin))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_SORT"));
        mvc.perform(getAs("/api/v1/complaints?sort=dueAt,asc&size=500", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(100));
    }
}
