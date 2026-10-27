package com.grievance360;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grievance360.complaint.Priority;
import com.grievance360.department.CategoryRepository;
import com.grievance360.department.ComplaintCategory;
import com.grievance360.department.Department;
import com.grievance360.department.DepartmentRepository;
import com.grievance360.security.JwtService;
import com.grievance360.user.Role;
import com.grievance360.user.User;
import com.grievance360.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest(properties = {
        "app.jwt.secret=test-secret-that-is-definitely-longer-than-32-bytes",
        "app.cors.allowed-origins=http://localhost:5173",
        "app.auth.login-attempts-per-window=1000",
        "app.storage.dir=target/test-storage",
        "app.workflow.escalation-cron=-"
})
@AutoConfigureMockMvc
public abstract class IntegrationTestBase {

    // Uses Testcontainers by default. Set TEST_DB_URL (+ TEST_DB_USER, TEST_DB_PASSWORD) to run against an existing
    // PostgreSQL instead, e.g. when Docker-in-Docker is unavailable (see README).
    static final String EXTERNAL_DB = System.getenv("TEST_DB_URL");
    static PostgreSQLContainer<?> postgres;

    static {
        if (EXTERNAL_DB == null || EXTERNAL_DB.isBlank()) {
            postgres = new PostgreSQLContainer<>("postgres:16-alpine");
            postgres.start();
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        if (postgres != null) {
            r.add("spring.datasource.url", postgres::getJdbcUrl);
            r.add("spring.datasource.username", postgres::getUsername);
            r.add("spring.datasource.password", postgres::getPassword);
        } else {
            r.add("spring.datasource.url", () -> EXTERNAL_DB);
            r.add("spring.datasource.username", () -> System.getenv().getOrDefault("TEST_DB_USER", "postgres"));
            r.add("spring.datasource.password", () -> System.getenv().getOrDefault("TEST_DB_PASSWORD", "postgres"));
        }
    }

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected UserRepository users;
    @Autowired protected DepartmentRepository departments;
    @Autowired protected CategoryRepository categories;
    @Autowired protected PasswordEncoder encoder;
    @Autowired protected JwtService jwt;

    protected static String unique(String prefix) {
        return prefix + "-" + SEQ.incrementAndGet() + "-" + UUID.randomUUID().toString().substring(0, 6);
    }

    protected Department department() {
        Department d = new Department();
        String code = ("D" + SEQ.incrementAndGet() + UUID.randomUUID().toString().substring(0, 6)).toUpperCase();
        d.setCode(code);
        d.setName("Dept " + code);
        return departments.save(d);
    }

    protected ComplaintCategory category(Department d) {
        ComplaintCategory c = new ComplaintCategory();
        c.setDepartment(d);
        c.setName(unique("Category"));
        c.setSlaHours(48);
        c.setDefaultPriority(Priority.MEDIUM);
        return categories.save(c);
    }

    protected User user(Role role, Department d) {
        User u = new User();
        u.setFullName("Test " + role);
        u.setEmail(unique(role.name().toLowerCase()) + "@test.local");
        u.setPasswordHash(encoder.encode("Password123"));
        u.setRole(role);
        u.setDepartment(d);
        return users.save(u);
    }

    protected String token(User u) {
        return "Bearer " + jwt.issue(u).token();
    }

    protected MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder b, User u) {
        return b.header("Authorization", token(u));
    }

    protected MockHttpServletRequestBuilder postJson(String url, Object body, User u) throws Exception {
        var b = post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return u == null ? b : authed(b, u);
    }

    protected MockHttpServletRequestBuilder patchJson(String url, Object body, User u) throws Exception {
        return authed(patch(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)), u);
    }

    protected MockHttpServletRequestBuilder getAs(String url, User u) {
        return authed(get(url), u);
    }

    protected JsonNode body(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString());
    }
}
