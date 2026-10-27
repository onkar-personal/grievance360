package com.grievance360.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Development-only demo data. Active only with the "demo" Spring profile, refuses to run alongside "prod",
 * and does nothing if departments already exist. All demo accounts share the password in DEMO_PASSWORD.
 */
@Slf4j
@Component
@Profile("demo")
@Order(2)
@RequiredArgsConstructor
public class DemoDataSeeder implements ApplicationRunner {
    public static final String DEMO_PASSWORD = "Demo@12345";

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final Environment env;

    private record Dept(long id, String code) {}

    private record Cat(long id, long deptId, String name, int slaHours, String priority, String[] titles) {}

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (Arrays.asList(env.getActiveProfiles()).contains("prod")) {
            log.error("Demo seeding refused: the 'demo' profile must never be combined with 'prod'.");
            return;
        }
        Long existing = jdbc.queryForObject("SELECT count(*) FROM departments", Long.class);
        if (existing != null && existing > 0) {
            log.info("Demo seed skipped: data already present.");
            return;
        }
        String hash = encoder.encode(DEMO_PASSWORD);
        Random rnd = new Random(26038);
        Instant now = Instant.now();

        Dept water = dept("WSD", "Water Supply & Sewerage", "Drinking water supply, pipelines, drainage and sewerage.", "water@demo.grievance360.local");
        Dept roads = dept("PWD", "Roads & Public Works", "Road surfaces, footpaths, bridges and public buildings.", "roads@demo.grievance360.local");
        Dept sanit = dept("SWM", "Sanitation & Solid Waste", "Garbage collection, street cleaning and public toilets.", "sanitation@demo.grievance360.local");
        Dept power = dept("ELE", "Electricity & Street Lighting", "Street lights, exposed wiring and supply interruptions.", "power@demo.grievance360.local");
        Dept health = dept("PHD", "Public Health", "Mosquito control, food safety and stray animal concerns.", "health@demo.grievance360.local");

        List<Cat> cats = List.of(
                cat(water, "Water supply disruption", 48, "HIGH", "No water supply since two days in our lane", "Very low water pressure in the morning hours"),
                cat(water, "Pipeline leakage", 72, "MEDIUM", "Main pipeline leaking near the bus stop", "Leaking valve wasting water on the footpath"),
                cat(water, "Drainage overflow", 48, "HIGH", "Sewage overflowing onto the road", "Blocked drain causing water logging"),
                cat(roads, "Potholes", 120, "MEDIUM", "Large potholes on the main road near the school", "Road surface damaged after rains"),
                cat(roads, "Damaged footpath", 168, "LOW", "Broken footpath tiles near the market", "Footpath encroached and unsafe for walking"),
                cat(sanit, "Garbage not collected", 24, "HIGH", "Garbage not collected for four days", "Overflowing community bin attracting animals"),
                cat(sanit, "Public toilet maintenance", 72, "MEDIUM", "Public toilet near the park is not cleaned", "No water in the public toilet block"),
                cat(power, "Street light not working", 72, "MEDIUM", "Street lights off along the entire stretch", "Flickering street light at the junction"),
                cat(power, "Exposed electrical wiring", 12, "CRITICAL", "Exposed live wires hanging from the pole", "Open junction box at children's play area"),
                cat(health, "Mosquito breeding", 96, "MEDIUM", "Stagnant water breeding mosquitoes in the vacant plot", "Fogging required in the colony"),
                cat(health, "Stray animal concern", 96, "LOW", "Stray cattle blocking traffic at the crossing", "Aggressive stray dogs near the school gate"));

        long admin = user("Aarti Kulkarni", "admin@demo.grievance360.local", "ADMIN", null, hash);
        List<Long> officers = new ArrayList<>();
        long[][] deptOfficers = new long[5][];
        Dept[] depts = {water, roads, sanit, power, health};
        String[][] officerNames = {
                {"Rahul Deshmukh", "Sneha Patil"}, {"Vikram Rao", "Meera Iyer"}, {"Imran Shaikh", "Pooja Nair"},
                {"Arjun Mehta", "Kavita Joshi"}, {"Farah Khan", "Suresh Pillai"}};
        for (int i = 0; i < depts.length; i++) {
            deptOfficers[i] = new long[2];
            for (int j = 0; j < 2; j++) {
                String email = officerNames[i][j].toLowerCase().replace(' ', '.') + "@demo.grievance360.local";
                long id = user(officerNames[i][j], email, "OFFICER", depts[i].id(), hash);
                deptOfficers[i][j] = id;
                officers.add(id);
            }
        }
        long[] citizens = {
                user("Priya Sharma", "citizen@demo.grievance360.local", "CITIZEN", null, hash),
                user("Anil Verma", "anil.verma@demo.grievance360.local", "CITIZEN", null, hash),
                user("Neha Gupta", "neha.gupta@demo.grievance360.local", "CITIZEN", null, hash),
                user("Rohan Das", "rohan.das@demo.grievance360.local", "CITIZEN", null, hash)};

        String[] wards = {"Ward 4", "Ward 7", "Ward 11", "Ward 15", "Ward 22"};
        String[] areas = {"Shivaji Nagar", "Kothrud", "Hadapsar", "Aundh", "Baner", "Viman Nagar"};

        int count = 48;
        for (int i = 0; i < count; i++) {
            Cat cat = cats.get(rnd.nextInt(cats.size()));
            int deptIdx = indexOf(depts, cat.deptId());
            long citizen = citizens[i % 5 == 0 ? 0 : rnd.nextInt(citizens.length)];
            Instant created = now.minus(Duration.ofHours(2 + rnd.nextInt(60 * 24)));
            Instant due = created.plus(Duration.ofHours(slaFor(cat)));
            String title = cat.titles()[rnd.nextInt(cat.titles().length)];
            String area = areas[rnd.nextInt(areas.length)];

            // Decide how far along the workflow this complaint is, keeping all timestamps in the past.
            int stage = rnd.nextInt(100);
            String target = stage < 10 ? "SUBMITTED" : stage < 18 ? "ACKNOWLEDGED" : stage < 30 ? "ASSIGNED"
                    : stage < 45 ? "IN_PROGRESS" : stage < 50 ? "NEEDS_INFORMATION" : stage < 65 ? "RESOLVED"
                    : stage < 94 ? "CLOSED" : "REJECTED";

            long officer = deptOfficers[deptIdx][rnd.nextInt(2)];
            Instant ack = created.plus(Duration.ofMinutes(30 + rnd.nextInt(300)));
            Instant assigned = ack.plus(Duration.ofMinutes(30 + rnd.nextInt(600)));
            Instant started = assigned.plus(Duration.ofHours(1 + rnd.nextInt(20)));
            double factor = 0.3 + rnd.nextDouble() * 1.2; // some resolutions breach SLA
            Instant resolved = created.plus(Duration.ofMinutes((long) (slaFor(cat) * 60 * factor)));
            if (resolved.isBefore(started)) resolved = started.plus(Duration.ofHours(2));
            Instant closed = resolved.plus(Duration.ofHours(6 + rnd.nextInt(60)));

            target = clamp(target, now, ack, assigned, started, resolved, closed);

            String ref = "G360-%d-%06d".formatted(created.atZone(ZoneOffset.UTC).getYear(),
                    jdbc.queryForObject("SELECT nextval('complaint_ref_seq')", Long.class));
            boolean hasOfficer = List.of("ASSIGNED", "IN_PROGRESS", "RESOLVED", "CLOSED").contains(target)
                    || ("NEEDS_INFORMATION".equals(target) && rnd.nextBoolean());
            Instant updated = switch (target) {
                case "SUBMITTED" -> created;
                case "ACKNOWLEDGED", "REJECTED" -> ack;
                case "ASSIGNED" -> assigned;
                case "IN_PROGRESS" -> started;
                case "NEEDS_INFORMATION" -> hasOfficer ? started : ack.plus(Duration.ofMinutes(20));
                case "RESOLVED" -> resolved;
                default -> closed;
            };
            boolean isResolved = "RESOLVED".equals(target) || "CLOSED".equals(target);
            Long cid = jdbc.queryForObject("""
                    INSERT INTO complaints (reference, title, description, category_id, department_id, citizen_id,
                        assigned_officer_id, status, priority, location_address, location_ward, location_city, location_pincode,
                        resolution_notes, due_at, assigned_at, resolved_at, closed_at, created_at, updated_at)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) RETURNING id
                    """, Long.class,
                    ref, title, title + ". Residents of " + area + " have been facing this issue and request prompt action from the department.",
                    cat.id(), cat.deptId(), citizen, hasOfficer ? officer : null, target, cat.priority(),
                    (10 + rnd.nextInt(180)) + ", " + area + " Road", wards[rnd.nextInt(wards.length)], "Pune",
                    "4110" + (10 + rnd.nextInt(60)),
                    isResolved ? "Field team attended the site and completed the required work. Verified on inspection." : null,
                    ts(due), hasOfficer ? ts(assigned) : null, isResolved ? ts(resolved) : null,
                    "CLOSED".equals(target) ? ts(closed) : null, ts(created), ts(updated));

            history(cid, null, "SUBMITTED", "Complaint submitted", citizen, "CITIZEN", created);
            if ("SUBMITTED".equals(target)) continue;
            if ("REJECTED".equals(target)) {
                history(cid, "SUBMITTED", "REJECTED", "Issue falls outside municipal jurisdiction; forwarded details to the citizen.", officer, "OFFICER", ack);
                continue;
            }
            history(cid, "SUBMITTED", "ACKNOWLEDGED", "Complaint received by the department.", officer, "OFFICER", ack);
            if ("ACKNOWLEDGED".equals(target)) continue;
            if ("NEEDS_INFORMATION".equals(target) && !hasOfficer) {
                history(cid, "ACKNOWLEDGED", "NEEDS_INFORMATION", "Please share a photo and the nearest landmark.", officer, "OFFICER", ack.plus(Duration.ofMinutes(20)));
                continue;
            }
            history(cid, "ACKNOWLEDGED", "ASSIGNED", "Assigned to field officer", admin, "ADMIN", assigned);
            if ("ASSIGNED".equals(target)) continue;
            history(cid, "ASSIGNED", "IN_PROGRESS", "Site inspection scheduled.", officer, "OFFICER", started);
            if ("IN_PROGRESS".equals(target)) {
                comment(cid, officer, "Crew allocated; work expected to finish within the SLA window.", "PUBLIC", started.plus(Duration.ofMinutes(10)));
                continue;
            }
            if ("NEEDS_INFORMATION".equals(target)) {
                history(cid, "IN_PROGRESS", "NEEDS_INFORMATION", "Please confirm the exact location of the issue.", officer, "OFFICER", started.plus(Duration.ofMinutes(5)));
                continue;
            }
            comment(cid, officer, "Material requested from store.", "INTERNAL", started.plus(Duration.ofMinutes(30)));
            history(cid, "IN_PROGRESS", "RESOLVED", "Work completed at site.", officer, "OFFICER", resolved);
            if ("CLOSED".equals(target)) {
                history(cid, "RESOLVED", "CLOSED", "Citizen confirmed the resolution", citizen, "CITIZEN", closed);
            }
        }

        jdbc.update("""
                INSERT INTO notifications (user_id, type, title, message, created_at)
                VALUES (?, 'INFO_REQUESTED', 'Welcome to Grievance360', 'This is demo data. Explore your complaints from the sidebar.', now())
                """, citizens[0]);
        log.warn("Demo data loaded: {} complaints, {} officers. Demo password for all demo accounts: see README.", count, officers.size());
    }

    private static String clamp(String target, Instant now, Instant ack, Instant assigned, Instant started, Instant resolved, Instant closed) {
        if (("CLOSED".equals(target)) && closed.isAfter(now)) target = "RESOLVED";
        if (("RESOLVED".equals(target)) && resolved.isAfter(now)) target = "IN_PROGRESS";
        if (("IN_PROGRESS".equals(target) || "NEEDS_INFORMATION".equals(target)) && started.isAfter(now)) target = "ASSIGNED";
        if ("ASSIGNED".equals(target) && assigned.isAfter(now)) target = "ACKNOWLEDGED";
        if (("ACKNOWLEDGED".equals(target) || "REJECTED".equals(target)) && ack.isAfter(now)) target = "SUBMITTED";
        return target;
    }

    private static int slaFor(Cat c) {
        int pct = switch (c.priority()) {
            case "CRITICAL" -> 50;
            case "HIGH" -> 75;
            default -> 100;
        };
        return Math.max(1, c.slaHours() * pct / 100);
    }

    private static int indexOf(Dept[] depts, long id) {
        for (int i = 0; i < depts.length; i++) if (depts[i].id() == id) return i;
        throw new IllegalStateException();
    }

    private Dept dept(String code, String name, String desc, String email) {
        Long id = jdbc.queryForObject("INSERT INTO departments (code, name, description, contact_email) VALUES (?,?,?,?) RETURNING id",
                Long.class, code, name, desc, email);
        return new Dept(id, code);
    }

    private Cat cat(Dept d, String name, int sla, String priority, String... titles) {
        Long id = jdbc.queryForObject("""
                INSERT INTO complaint_categories (department_id, name, description, sla_hours, default_priority)
                VALUES (?,?,?,?,?) RETURNING id""", Long.class, d.id(), name, null, sla, priority);
        return new Cat(id, d.id(), name, sla, priority, titles);
    }

    private long user(String name, String email, String role, Long deptId, String hash) {
        return jdbc.queryForObject("""
                INSERT INTO users (full_name, email, phone, password_hash, role, department_id)
                VALUES (?,?,?,?,?,?) RETURNING id""", Long.class, name, email, "98" + Math.abs(email.hashCode() % 100000000), hash, role, deptId);
    }

    private void history(long cid, String from, String to, String remark, long actor, String role, Instant at) {
        jdbc.update("""
                INSERT INTO complaint_status_history (complaint_id, from_status, to_status, remark, actor_id, actor_role, created_at)
                VALUES (?,?,?,?,?,?,?)""", cid, from, to, remark, actor, role, ts(at));
    }

    private void comment(long cid, long author, String body, String visibility, Instant at) {
        jdbc.update("INSERT INTO complaint_comments (complaint_id, author_id, body, visibility, created_at) VALUES (?,?,?,?,?)",
                cid, author, body, visibility, ts(at));
    }

    private static Timestamp ts(Instant i) {
        return Timestamp.from(i);
    }
}
